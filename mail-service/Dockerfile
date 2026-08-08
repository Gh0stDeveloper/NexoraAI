FROM debian:13-slim

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && echo "postfix postfix/mailname string localhost" | debconf-set-selections \
    && echo "postfix postfix/main_mailer_type select Internet Site" | debconf-set-selections \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        openssl \
        postfix \
        opendkim \
        opendkim-tools \
        python3 \
        tini \
    && rm -rf /var/lib/apt/lists/*

COPY gateway.py /opt/nexora-mail/gateway.py
COPY entrypoint.sh /usr/local/bin/nexora-mail-entrypoint

RUN chmod 0755 /usr/local/bin/nexora-mail-entrypoint /opt/nexora-mail/gateway.py \
    && mkdir -p /var/lib/nexora-mail /run/opendkim

EXPOSE 8025

HEALTHCHECK --interval=10s --timeout=5s --retries=6 --start-period=15s \
  CMD python3 -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8025/health', timeout=3).read()" || exit 1

ENTRYPOINT ["/usr/bin/tini", "--", "/usr/local/bin/nexora-mail-entrypoint"]