#!/usr/bin/env python3
import hashlib
import hmac
import json
import os
import re
import smtplib
import sys
from email.message import EmailMessage
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOST = "0.0.0.0"
PORT = 8025
SMTP_HOST = "127.0.0.1"
SMTP_PORT = 25
MAX_BODY_BYTES = 96 * 1024
MIN_SECRET_LENGTH = 32
EMAIL_RE = re.compile(r"^[^\s@<>]+@[^\s@<>]+\.[^\s@<>]+$")


def env(name: str, default: str = "") -> str:
    return os.environ.get(name, default).strip()


def required(name: str) -> str:
    value = env(name)
    if not value:
        raise RuntimeError(f"{name} is required")
    return value


def webhook_secret() -> str:
    explicit = env("AUTH_EMAIL_WEBHOOK_SECRET")
    if len(explicit) >= MIN_SECRET_LENGTH:
        return explicit
    database_secret = required("POSTGRES_PASSWORD")
    return hashlib.sha256(f"nexora-mail:{database_secret}".encode("utf-8")).hexdigest()


def smtp_health() -> bool:
    try:
        with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=3) as client:
            code, _ = client.noop()
            return 200 <= code < 400
    except Exception:
        return False


def send_payload(payload: dict) -> None:
    mail_from = required("MAIL_FROM")
    recipient = str(payload.get("to") or "").strip()
    subject = str(payload.get("subject") or "").strip()
    text = str(payload.get("text") or "").strip()
    html = str(payload.get("html") or "").strip()
    message_type = str(payload.get("type") or "nexora.transactional").strip()

    if not EMAIL_RE.fullmatch(recipient):
        raise ValueError("invalid recipient")
    if not subject or len(subject) > 180 or "\r" in subject or "\n" in subject:
        raise ValueError("invalid subject")
    if not text or len(text) > 24_000 or len(html) > 64_000:
        raise ValueError("invalid message content")
    if not message_type.startswith("nexora."):
        raise ValueError("invalid message type")

    message = EmailMessage()
    message["From"] = mail_from
    message["To"] = recipient
    message["Subject"] = subject
    message["Auto-Submitted"] = "auto-generated"
    message["X-Auto-Response-Suppress"] = "All"
    message["X-Nexora-Mail-Type"] = message_type[:120]
    message.set_content(text)
    if html:
        message.add_alternative(html, subtype="html")

    with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=15) as client:
        client.send_message(message, from_addr=mail_from, to_addrs=[recipient])


class Handler(BaseHTTPRequestHandler):
    server_version = "NexoraMail/1.0"

    def log_message(self, fmt: str, *args) -> None:
        sys.stdout.write("[nexora-mail] " + (fmt % args) + "\n")
        sys.stdout.flush()

    def respond(self, status: int, body: dict) -> None:
        encoded = json.dumps(body, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def do_GET(self) -> None:
        if self.path != "/health":
            self.respond(404, {"ok": False})
            return
        healthy = smtp_health()
        self.respond(200 if healthy else 503, {"ok": healthy, "service": "nexora-mail"})

    def do_POST(self) -> None:
        if self.path != "/send":
            self.respond(404, {"ok": False})
            return
        secret = webhook_secret()
        provided = self.headers.get("Authorization", "")
        expected = f"Bearer {secret}"
        if not hmac.compare_digest(provided, expected):
            self.respond(401, {"ok": False, "error": "unauthorized"})
            return

        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            length = 0
        if length <= 0 or length > MAX_BODY_BYTES:
            self.respond(413, {"ok": False, "error": "invalid_body_size"})
            return

        try:
            payload = json.loads(self.rfile.read(length))
            if not isinstance(payload, dict):
                raise ValueError("invalid payload")
            send_payload(payload)
        except ValueError as error:
            self.respond(400, {"ok": False, "error": str(error)})
            return
        except Exception as error:
            self.log_message("delivery failed: %s", error)
            self.respond(502, {"ok": False, "error": "delivery_failed"})
            return

        self.respond(202, {"ok": True, "queued": True})


if __name__ == "__main__":
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"[nexora-mail] gateway listening on {HOST}:{PORT}", flush=True)
    server.serve_forever()
