import assert from "node:assert/strict";
import fs from "node:fs";
import { test } from "node:test";

const read = (file) => fs.readFileSync(file, "utf8");
const compose = read("docker-compose.vps.yml");
const dockerfile = read("mail-service/Dockerfile");
const gateway = read("mail-service/gateway.py");
const entrypoint = read("mail-service/entrypoint.sh");
const cli = read("deploy/scripts/nexora-vps.sh");
const verify = read("deploy/scripts/verify-vps.sh");

test("Nexora Mail is private to Docker and persistent only where required", () => {
  assert.match(compose, /mailer:/);
  assert.match(compose, /expose: \["8025"\]/);
  assert.doesNotMatch(compose, /(?:25|465|587|993|995):(?:25|465|587|993|995)/);
  assert.doesNotMatch(compose, /8025:8025/);
  assert.match(compose, /mailer-keys:\/var\/lib\/nexora-mail/);
  assert.match(compose, /http:\/\/mailer:8025\/send/);
  assert.match(compose, /condition: service_healthy/);
});

test("mailer signs outbound mail and does not become an inbound relay", () => {
  assert.match(dockerfile, /postfix/);
  assert.match(dockerfile, /opendkim/);
  assert.match(entrypoint, /opendkim-genkey/);
  assert.match(entrypoint, /inet_interfaces = loopback-only/);
  assert.match(entrypoint, /mynetworks = 127\.0\.0\.0\/8/);
  assert.match(entrypoint, /relay_domains =/);
  assert.match(entrypoint, /smtpd_milters = inet:127\.0\.0\.1:8891/);
  assert.match(gateway, /hmac\.compare_digest/);
  assert.match(gateway, /MAX_BODY_BYTES/);
  assert.match(gateway, /smtplib\.SMTP\(SMTP_HOST, SMTP_PORT/);
});

test("existing VPS configs can upgrade without new required secrets", () => {
  assert.match(gateway, /POSTGRES_PASSWORD/);
  assert.match(gateway, /nexora-mail:/);
  assert.match(entrypoint, /PUBLIC_DOMAIN:-ghostnexoraai\.duckdns\.org/);
  assert.match(cli, /compose_has_service mailer/);
  assert.match(cli, /deploy_revision/);
  assert.match(cli, /rollback automático/);
});

test("operator can verify mail health, DNS guidance and test delivery", () => {
  assert.match(verify, /Nexora Mail/);
  assert.match(verify, /http:\/\/127\.0\.0\.1:8025\/health/);
  assert.match(cli, /mail_dns\(\)/);
  assert.match(cli, /mail_test\(\)/);
  assert.match(cli, /SPF/);
  assert.match(cli, /DKIM/);
  assert.match(cli, /DMARC/);
  assert.match(cli, /PTR\/rDNS/);
});
