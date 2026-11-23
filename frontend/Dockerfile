# syntax=docker/dockerfile:1
# 환경과 무관한 단일 실행 이미지를 빌드한다. 런타임 환경 변수는 컨테이너 기동 시(ConfigMap 등) 주입한다.
FROM node:22-slim AS base
ENV NEXT_TELEMETRY_DISABLED=1

FROM base AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM base AS builder
WORKDIR /app
ENV CI=true
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

FROM base AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"

RUN groupadd --gid 1001 nodejs \
    && useradd --uid 1001 --gid nodejs --no-create-home --home-dir /app --shell /usr/sbin/nologin nextjs

COPY --from=builder --chown=1001:1001 /app/public ./public
COPY --from=builder --chown=1001:1001 /app/.next/standalone ./
COPY --from=builder --chown=1001:1001 /app/.next/static ./.next/static
RUN mkdir -p /app/.next/cache && chown -R 1001:1001 /app/.next

USER 1001:1001
EXPOSE 3000
CMD ["node", "server.js"]
