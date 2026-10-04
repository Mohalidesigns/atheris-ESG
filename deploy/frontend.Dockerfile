# WP-54: the web app served by nginx with the platform's security headers (deploy/nginx.conf).
#   docker build -f deploy/frontend.Dockerfile -t atheris-esg-frontend .
FROM node:22-alpine AS build
WORKDIR /src/esg-frontend
COPY esg-frontend/package.json esg-frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY esg-frontend ./
RUN npm run build
# Precompressed copies for gzip_static (Brotli is left to the CDN/edge, D41).
RUN find dist -type f \( -name '*.js' -o -name '*.css' -o -name '*.html' -o -name '*.svg' -o -name '*.json' \) -exec gzip -9 -k {} \;

FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/esg-frontend/dist /usr/share/nginx/html
EXPOSE 8080
# The base image already runs as nginx (uid 101); stated explicitly so the image never falls back to root.
USER 101
