# Atheris ESG on the shared GRC VPS

This is the runbook for the VPS that also hosts compliance, control, infosec, itgov, risk and thirdLine.
That server already runs nginx (TLS for every module), PostgreSQL 17 and Ollama, so ESG uses those and adds only
what the server lacks. `deploy/docker-compose.prod.yml` is for a server of its own and is not used here.

```
browser ─TLS─▶ host nginx (esg.atherislimited.com)
                 ├─ /                    → /var/www/esg/web            (built web app)
                 ├─ /api/                → 127.0.0.1:8085 backend      (Docker, host network)
                 └─ /atheris-esg-files/  → 127.0.0.1:9010 SeaweedFS    (pre-signed downloads, GET only)
backend ─▶ host PostgreSQL 17 :5432 (db esg, roles esg_owner / esg_app, RLS)
        ─▶ ClamAV 127.0.0.1:3310 (Docker)   ─▶ SeaweedFS S3 (Docker, SSE at rest)
        ─▶ esg-ai 127.0.0.1:8090 (Docker, host network) ─▶ host Ollama 127.0.0.1:11434
```

Server at the time of writing: Ubuntu 24.04, 4 vCPU, 7.8 GB RAM (about 4.9 GB free), no swap, Docker 29, nginx 1.24,
PostgreSQL 17.12, Ollama running. Ports 8080 and 8088 are not used by ESG; 8088 belongs to another container.

ESG adds about 4 GB under load (backend up to 2 GB, ClamAV about 1.3–1.8 GB, SeaweedFS and AI about 0.5 GB). Step 1 adds
swap so a ClamAV signature reload can't get the other modules killed for lack of memory.

## First install

Run everything as root on the VPS.

### 1. Swap (once)

```bash
fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab
sysctl vm.swappiness=10 && echo 'vm.swappiness=10' > /etc/sysctl.d/99-swappiness.conf
```

### 2. Code

`/var/www/esg` is empty. Clone the release into it. The repository is private, so use a deploy key or a token
with read-only access:

```bash
cd /var/www && git clone https://github.com/Mohalidesigns/atheris-ESG.git esg && cd esg
git checkout main
```

### 3. Database on the host PostgreSQL

Generate two passwords and note them for `.env` (step 4):

```bash
openssl rand -base64 24 | tr -d '/+='; openssl rand -base64 24 | tr -d '/+='
```

```bash
sudo -u postgres psql -v ON_ERROR_STOP=1 <<'SQL'
CREATE ROLE esg_owner LOGIN PASSWORD 'OWNER_PASSWORD_HERE';
CREATE ROLE esg_app LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE PASSWORD 'APP_PASSWORD_HERE';
CREATE DATABASE esg OWNER esg_owner;
REVOKE ALL ON DATABASE esg FROM PUBLIC;
GRANT CONNECT ON DATABASE esg TO esg_app;
SQL
```

`esg_app` must not be a superuser and must not have BYPASSRLS. Otherwise Row-Level Security is skipped and tenants
can see each other's data. The backend connects over `127.0.0.1`, which the default `pg_hba.conf` allows with a
password. Flyway creates the schemas as `esg_owner` on first start.

### 4. Secrets

```bash
cp deploy/vps/.env.example deploy/vps/.env && chmod 600 deploy/vps/.env
openssl rand -base64 48                  # JWT_SECRET
openssl rand -base64 32                  # DATA_ENCRYPTION_KEY
openssl rand -hex 24                     # ESG_AI_TOKEN
openssl rand -hex 24                     # S3_SECRET_KEY
openssl rand -hex 32                     # S3_SSE_KEK
nano deploy/vps/.env
```

Also set `ESG_BOOTSTRAP_ADMIN_EMAIL` and `ESG_BOOTSTRAP_ADMIN_PASSWORD` (12+ characters) for the first start.
Keep a copy of `DATA_ENCRYPTION_KEY` and `S3_SSE_KEK` outside the server. Without them, two-factor seeds
and stored files can't be decrypted after a restore.

### 5. File storage (once)

MinIO no longer publishes community container images (D48), so the S3-compatible store is SeaweedFS. It creates
the bucket named in `S3_BUCKET` on first start. Start it, then check that it accepts an encrypted upload:

```bash
docker compose -f deploy/vps/docker-compose.vps.yml --env-file deploy/vps/.env up -d s3
K=$(grep ^S3_ACCESS_KEY= deploy/vps/.env | cut -d= -f2); S=$(grep ^S3_SECRET_KEY= deploy/vps/.env | cut -d= -f2)
B=$(grep ^S3_BUCKET= deploy/vps/.env | cut -d= -f2)
echo probe | docker run --rm -i --network host -e AWS_ACCESS_KEY_ID=$K -e AWS_SECRET_ACCESS_KEY=$S -e AWS_DEFAULT_REGION=af-south-1 \
  amazon/aws-cli:2.31.0 --endpoint-url http://127.0.0.1:9010 s3 cp - s3://$B/_probe.txt --sse AES256
docker run --rm --network host -e AWS_ACCESS_KEY_ID=$K -e AWS_SECRET_ACCESS_KEY=$S -e AWS_DEFAULT_REGION=af-south-1 \
  amazon/aws-cli:2.31.0 --endpoint-url http://127.0.0.1:9010 s3api head-object --bucket $B --key _probe.txt
```

`head-object` must show `"ServerSideEncryption": "AES256"`. Delete the probe afterwards with `s3 rm s3://$B/_probe.txt`.
The backend does not create the bucket itself (`esg.files.s3.create-bucket: false`).

### 6. nginx site and certificate

Point a DNS A record for `esg.atherislimited.com` at this server first. If you use another name, change it in
`nginx-esg.conf` and in `PUBLIC_URL`/`PUBLIC_HOST`.

```bash
cp deploy/vps/nginx-esg.conf /etc/nginx/sites-available/esg
ln -s /etc/nginx/sites-available/esg /etc/nginx/sites-enabled/esg
mkdir -p /var/www/esg/web && echo ok > /var/www/esg/web/index.html
nginx -t && systemctl reload nginx
certbot --nginx -d esg.atherislimited.com --redirect
```

The site has to be on HTTPS before the first sign-in: the refresh-token cookie is `Secure`.

### 7. Ollama model (optional)

```bash
ollama list            # already has granite4:micro? skip the pull
ollama pull granite4:micro
```

Report extraction needs it. Forecasts and anomaly detection work without it. To switch AI off completely, set
`ESG_AI_URL=` (empty) in `.env`.

### 8. Build and start

```bash
chmod +x deploy/vps/deploy.sh && ./deploy/vps/deploy.sh
```

The first build downloads Maven and npm dependencies and takes 10–15 minutes. ClamAV needs a few more minutes to
download its signatures. Until then, uploads stay "pending scan" and are rescanned every 2 minutes.

### 9. First sign-in

1. Open https://esg.atherislimited.com and sign in as the bootstrap administrator. Set up two-factor sign-in.
2. Delete `ESG_BOOTSTRAP_ADMIN_EMAIL` and `ESG_BOOTSTRAP_ADMIN_PASSWORD` from `.env` and run `./deploy/vps/deploy.sh` again.
3. Create the customer organisation under Administration › Tenants (`docs/release/PILOT_ONBOARDING.md`).

## Updates

```bash
cd /var/www/esg && ./deploy/vps/deploy.sh main      # or a release tag
```

The script builds the web app, restarts the containers (Flyway migrates the database), waits for
`/api/actuator/health`, and only then switches `/var/www/esg/web`. If the backend doesn't come up, the old web app
stays in place and the backend log is printed. The previous web app is kept in `web.old`.

## Backups (set up before go-live)

```bash
cat > /etc/cron.d/esg-backup <<'CRON'
0 */6 * * * root d=/var/backups/esg; mkdir -p $d; sudo -u postgres pg_dump -Fc esg > $d/esg-$(date +\%F-\%H).dump; docker run --rm -v atheris-esg_s3data:/data:ro -v $d:/out alpine tar czf /out/files-$(date +\%F-\%H).tgz -C /data .; find $d -mtime +90 -delete
CRON
```

That gives the BRD §7.4 schedule: every 6 hours, kept for 90 days. Also copy `/var/backups/esg` off the server.

## Operations

| What | Command |
|---|---|
| Status | `docker compose -f deploy/vps/docker-compose.vps.yml --env-file deploy/vps/.env ps` |
| Backend log | `docker compose -f deploy/vps/docker-compose.vps.yml --env-file deploy/vps/.env logs -f backend` |
| Health | `curl -s http://127.0.0.1:8085/api/actuator/health` |
| Memory | `docker stats --no-stream` |
| Roll back web app | `mv web web.bad && mv web.old web` (the backend: `./deploy/vps/deploy.sh <previous tag>`) |

Rolling back the backend doesn't undo database migrations. Take a `pg_dump` before you deploy a release that has new
migrations.
