# syntax=docker/dockerfile:1
# The web app: built by Vite, served as static files by nginx running as an unprivileged user.
#   docker build -t request-portal/web frontend
FROM node:22.23.4-alpine3.23 AS build
WORKDIR /app
RUN corepack enable
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM nginxinc/nginx-unprivileged:1.30.6-alpine3.24
LABEL org.opencontainers.image.source="https://github.com/Kazt2407/request-portal" \
      org.opencontainers.image.title="request-portal web"
# Turned into /etc/nginx/conf.d/*.conf at startup with the environment variables below filled in, so the same
# image serves any environment.
COPY nginx/ /etc/nginx/templates/
COPY --from=build /app/dist /usr/share/nginx/html
ENV KEYCLOAK_URL=http://localhost:8180 \
    KEYCLOAK_REALM=request-portal \
    KEYCLOAK_CLIENT_ID=portal-spa \
    STORAGE_URL=http://localhost:9010
EXPOSE 8080
