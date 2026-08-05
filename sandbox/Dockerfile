FROM node:22-alpine

RUN apk add --no-cache docker-cli

WORKDIR /runner
COPY server.mjs ./server.mjs

ENV PORT=8787 \
    SANDBOX_TIMEOUT_MS=30000 \
    SANDBOX_MAX_OUTPUT_BYTES=12000

EXPOSE 8787
CMD ["node", "server.mjs"]
