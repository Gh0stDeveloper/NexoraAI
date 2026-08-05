FROM node:22-bookworm-slim

RUN apt-get update \
  && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
    ca-certificates openjdk-17-jdk-headless unzip zip \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /worker
COPY package.json package-lock.json ./
RUN npm ci --omit=dev --no-audit --no-fund

COPY build-worker/worker.mjs ./worker.mjs
COPY build-worker/template /opt/nexora-user-template

RUN groupadd --system --gid 1001 nexora-builder \
  && useradd --system --uid 1001 --gid 1001 --home-dir /nonexistent nexora-builder

ENV NODE_ENV=production \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/var/cache/nexora-gradle \
    USER_BUILD_JOBS_PATH=/var/lib/nexora-ai/android-build-jobs \
    USER_BUILD_TEMPLATE_PATH=/opt/nexora-user-template

USER 1001:1001
CMD ["node", "worker.mjs"]
