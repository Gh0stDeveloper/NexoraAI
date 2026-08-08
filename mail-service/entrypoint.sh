#!/usr/bin/env bash
set -euo pipefail

MAIL_DOMAIN="${MAIL_DOMAIN:-}"
MAIL_FROM="${MAIL_FROM:-}"
MAIL_DKIM_SELECTOR="${MAIL_DKIM_SELECTOR:-nexora}"
MAIL_HOSTNAME="${MAIL_HOSTNAME:-mail.${MAIL_DOMAIN}}"
KEY_DIR="/var/lib/nexora-mail"

if [[ -z "$MAIL_DOMAIN" || -z "$MAIL_FROM" || -z "${AUTH_EMAIL_WEBHOOK_SECRET:-}" ]]; then
  printf 'ERROR: MAIL_DOMAIN, MAIL_FROM y AUTH_EMAIL_WEBHOOK_SECRET son obligatorios.\n' >&2
  exit 2
fi
if [[ ! "$MAIL_DOMAIN" =~ ^[A-Za-z0-9.-]+$ ]]; then
  printf 'ERROR: MAIL_DOMAIN no es válido.\n' >&2
  exit 2
fi
if [[ ! "$MAIL_DKIM_SELECTOR" =~ ^[A-Za-z0-9_-]+$ ]]; then
  printf 'ERROR: MAIL_DKIM_SELECTOR no es válido.\n' >&2
  exit 2
fi
if [[ "$MAIL_FROM" != *@* ]]; then
  printf 'ERROR: MAIL_FROM no es válido.\n' >&2
  exit 2
fi

mkdir -p "$KEY_DIR" /run/opendkim /etc/opendkim
chmod 700 "$KEY_DIR"

private_key="$KEY_DIR/${MAIL_DKIM_SELECTOR}.private"
public_record="$KEY_DIR/${MAIL_DKIM_SELECTOR}.txt"
if [[ ! -s "$private_key" || ! -s "$public_record" ]]; then
  rm -f -- "$private_key" "$public_record"
  opendkim-genkey \
    --bits=2048 \
    --domain="$MAIL_DOMAIN" \
    --directory="$KEY_DIR" \
    --selector="$MAIL_DKIM_SELECTOR"
fi
chown opendkim:opendkim "$private_key"
chmod 600 "$private_key"
chmod 644 "$public_record"

cat > /etc/opendkim.conf <<EOF
Syslog                  yes
SyslogSuccess           yes
LogWhy                   no
Canonicalization        relaxed/simple
Mode                    s
SubDomains              no
OversignHeaders         From
UserID                  opendkim
UMask                   007
Socket                  inet:8891@127.0.0.1
PidFile                 /run/opendkim/opendkim.pid
KeyTable                refile:/etc/opendkim/KeyTable
SigningTable            refile:/etc/opendkim/SigningTable
ExternalIgnoreList      refile:/etc/opendkim/TrustedHosts
InternalHosts           refile:/etc/opendkim/TrustedHosts
EOF

cat > /etc/opendkim/KeyTable <<EOF
${MAIL_DKIM_SELECTOR}._domainkey.${MAIL_DOMAIN} ${MAIL_DOMAIN}:${MAIL_DKIM_SELECTOR}:${private_key}
EOF
cat > /etc/opendkim/SigningTable <<EOF
*@${MAIL_DOMAIN} ${MAIL_DKIM_SELECTOR}._domainkey.${MAIL_DOMAIN}
EOF
cat > /etc/opendkim/TrustedHosts <<'EOF'
127.0.0.1
localhost
EOF

postconf -e "compatibility_level = 3.6"
postconf -e "myhostname = ${MAIL_HOSTNAME}"
postconf -e "mydomain = ${MAIL_DOMAIN}"
postconf -e "myorigin = ${MAIL_DOMAIN}"
postconf -e "mydestination = localhost"
postconf -e "inet_interfaces = loopback-only"
postconf -e "inet_protocols = ipv4"
postconf -e "mynetworks = 127.0.0.0/8"
postconf -e "relay_domains ="
postconf -e "smtp_helo_name = ${MAIL_HOSTNAME}"
postconf -e "smtp_tls_security_level = may"
postconf -e "smtp_tls_CAfile = /etc/ssl/certs/ca-certificates.crt"
postconf -e "smtp_tls_loglevel = 1"
postconf -e "milter_default_action = accept"
postconf -e "milter_protocol = 6"
postconf -e "smtpd_milters = inet:127.0.0.1:8891"
postconf -e "non_smtpd_milters = inet:127.0.0.1:8891"
postconf -e "message_size_limit = 10485760"
postconf -e "maillog_file = /dev/stdout"

/usr/sbin/opendkim -x /etc/opendkim.conf
postfix start

for _ in $(seq 1 30); do
  if python3 - <<'PY'
import smtplib
try:
    with smtplib.SMTP('127.0.0.1', 25, timeout=1) as smtp:
        code, _ = smtp.noop()
        raise SystemExit(0 if 200 <= code < 400 else 1)
except Exception:
    raise SystemExit(1)
PY
  then
    exec python3 /opt/nexora-mail/gateway.py
  fi
  sleep 1
done

printf 'ERROR: Postfix no quedó disponible dentro de Nexora Mail.\n' >&2
exit 1