# DLQ-Analyzer — Distribution & Setup Guide

This guide covers two roles:

- **Part A — Publisher (you, Yash):** how to build your images and push them to Docker Hub so the world can use them.
- **Part B — New user:** how someone who has never seen this project goes from nothing to a running app, using only your public images.

The whole design here is: **the new user never touches your source code and never builds anything.** They download two small text files and run one command.

---

## How the pieces fit together

```
   YOU (publisher)                         DOCKER HUB                    NEW USER
   ---------------                         ----------                    --------
   source code  ──docker build──►  image  ──docker push──►  [public repo]  ──docker pull──►  runs container
                                                              (anyone can
                                                               pull, no login)
```

Two images get published:

| Image | Docker Hub repo | What it is |
|-------|-----------------|------------|
| Backend | `yashnikam18yn/dlq-analyzer-backend:latest` | Spring Boot API |
| Frontend | `yashnikam18yn/dlq-analyzer-frontend:latest` | React UI served by nginx |

The supporting services (MySQL, Redis, RabbitMQ) are **official public images** — the new user pulls those straight from Docker Hub automatically. You don't publish those; you just reference them in `docker-compose.yml`.

---

# PART A — Publisher steps (you)

You do this once now, and again whenever you change the code.

### A1. Log in to Docker Hub (one time per machine)

```powershell
docker login
```

Enter your Docker Hub username (`yashnikam18yn`) and password/token. You stay logged in until you `docker logout`.

### A2. Make your repos public (one time, on the website)

1. Go to https://hub.docker.com → sign in.
2. For each repo (`dlq-analyzer-backend`, `dlq-analyzer-frontend`):
   - Open the repo → **Settings** → **Visibility** → set to **Public**.
   - If the repo doesn't exist yet, it gets created automatically the first time you push (step A4). You can set it public right after.

Public means: **anyone can `docker pull` without logging in.** That's what makes the new-user side login-free.

### A3. Build the images from source

Run these from the project root `C:\work_dsi\DLQ-Analyzer` (adjust paths to where each Dockerfile lives).

```powershell
# Backend
docker build -t yashnikam18yn/dlq-analyzer-backend:latest .\dlq-analyzer

# Frontend
docker build -t yashnikam18yn/dlq-analyzer-frontend:latest .\dlq-analyzer-ui
```

The `-t` flag tags the image with the exact name Docker Hub expects: `username/reponame:tag`. The `:latest` tag is the conventional default.

**Tip — version your releases.** `latest` is convenient but moves. For real releases, tag twice so users can pin a fixed version:

```powershell
docker build -t yashnikam18yn/dlq-analyzer-backend:latest -t yashnikam18yn/dlq-analyzer-backend:1.0.0 .\dlq-analyzer
```

### A4. Push to Docker Hub

```powershell
docker push yashnikam18yn/dlq-analyzer-backend:latest
docker push yashnikam18yn/dlq-analyzer-frontend:latest
```

If you also made a version tag, push it too:

```powershell
docker push yashnikam18yn/dlq-analyzer-backend:1.0.0
```

After this, your images are live at:
- https://hub.docker.com/r/yashnikam18yn/dlq-analyzer-backend
- https://hub.docker.com/r/yashnikam18yn/dlq-analyzer-frontend

### A5. Publish the two files the new user needs

The new user needs exactly two text files from you. Put them somewhere public — a GitHub repo, a Gist, or even attached to a release. They are:

1. `docker-compose.yml` (provided below in Part B)
2. `.env` (template provided below in Part B)

Plus the README (Part B itself).

That's the entire publisher job. **Re-publishing after a code change = repeat A3 + A4.**

---

# PART B — New-user steps (hand this part to anyone)

You don't need the source code. You don't need Java, Node, MySQL, or anything else installed. You need **Docker**, two text files, and one command.

### B1. Install Docker Desktop

- Download: https://www.docker.com/products/docker-desktop/
- Install it, launch it, and **wait until the whale icon says "Docker Desktop is running."**
- If you skip this, every command fails with a "cannot connect to the Docker daemon" / named-pipe error.

### B2. Create a folder with two files

Make an empty folder anywhere, e.g. `dlq-analyzer-run`. Inside it, create these two files exactly.

**File 1 — `docker-compose.yml`**

```yaml
services:
  dlq-analyzer-backend:
    image: yashnikam18yn/dlq-analyzer-backend:latest
    container_name: dlq-backend
    ports:
      - "8082:8081"          # host 8082 -> container 8081
    env_file:
      - .env
    depends_on:
      - dlq-mysql
      - dlq-redis
      - dlq-rabbitmq

  dlq-analyzer-frontend:
    image: yashnikam18yn/dlq-analyzer-frontend:latest
    container_name: dlq-frontend
    ports:
      - "3000:80"            # open http://localhost:3000
    depends_on:
      - dlq-analyzer-backend

  dlq-mysql:
    image: mysql:8.0
    container_name: dlq-mysql
    ports:
      - "3307:3306"
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
      MYSQL_DATABASE: ${DB_NAME}
    volumes:
      - dlq_mysql_data:/var/lib/mysql

  dlq-redis:
    image: redis:7
    container_name: dlq-redis
    ports:
      - "6379:6379"

  dlq-rabbitmq:
    image: rabbitmq:3-management
    container_name: dlq-rabbitmq
    ports:
      - "5672:5672"          # broker
      - "15672:15672"        # management UI

volumes:
  dlq_mysql_data:
```

**File 2 — `.env`**

```dotenv
# --- Database ---
DB_HOST=dlq-mysql
DB_PORT=3306
DB_NAME=dlqanalyzer
DB_USERNAME=root
DB_PASSWORD=change-me

# --- RabbitMQ ---
RABBITMQ_HOST=dlq-rabbitmq
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest

# --- Redis ---
REDIS_HOST=dlq-redis
REDIS_PORT=6379
```

> **The single most important rule in this whole file:** every `*_HOST` value must be the **service name** from `docker-compose.yml` (`dlq-mysql`, `dlq-rabbitmq`, `dlq-redis`) — **never `localhost`.** Inside Docker, `localhost` means "this container talking to itself," so `localhost` makes the backend look for RabbitMQ inside its own container, find nothing, and fail. Containers reach each other by service name on Docker's internal network. (This was the exact bug that cost us an hour.)

### B3. Start everything

Open a terminal **in that folder** (the folder that contains the two files) and run:

```powershell
docker compose up -d
```

What happens:
- Docker pulls your two public images + MySQL/Redis/RabbitMQ from Docker Hub (first run only; cached after).
- `-d` runs them in the background ("detached").
- 30–60 seconds later everything is up.

> **Run it from the right folder.** `docker compose` reads `docker-compose.yml` **and** `.env` from the directory you're standing in. Run it from the wrong folder and you'll get the wrong file (or none). This was another bug we hit.

### B4. Confirm it's working

- Backend health: open http://localhost:8082/actuator/health → should show `{"status":"UP"}`
- Frontend: open http://localhost:3000 → the dashboard loads.

### B5. (Optional) Stop the RabbitMQ 404 log noise

The backend polls three dead-letter queues (`orders.dlq`, `payments.dlq`, `notifications.dlq`). On a fresh broker those don't exist yet, so the logs show harmless `404 NOT_FOUND` lines. To create them:

1. Open the RabbitMQ management UI: http://localhost:15672 (login `guest` / `guest`).
2. Go to **Queues and Streams** → **Add a new queue**.
3. Add three **durable** queues named exactly `orders.dlq`, `payments.dlq`, `notifications.dlq`.

The 404s stop and you can publish a test message to watch it appear on the dashboard.

### B6. Everyday commands

```powershell
docker compose ps         # what's running
docker compose logs -f dlq-analyzer-backend   # follow backend logs
docker compose down       # stop & remove containers (keeps the MySQL volume/data)
docker compose down -v    # stop & also delete the MySQL data volume (full reset)
docker compose pull       # grab the newest :latest images you published
docker compose up -d      # re-start after a pull
```

To upgrade to a new version you published: `docker compose pull` then `docker compose up -d`. Docker recreates only the changed containers.

---

## Troubleshooting (the things that actually went wrong)

| Symptom | Cause | Fix |
|---------|-------|-----|
| "cannot connect to the Docker daemon" / named-pipe error | Docker Desktop isn't running | Launch it, wait for "running" |
| Port bind fails (e.g. `8081 already in use`) | Something else holds the port | Change the **host** side of the mapping, e.g. `"8082:8081"` |
| Backend can't reach RabbitMQ/MySQL/Redis | A `*_HOST` is set to `localhost` | Set it to the **service name** (`dlq-rabbitmq`, etc.) |
| Changed `.env` but backend ignores it | A running container keeps its original env | `docker compose up -d --force-recreate dlq-analyzer-backend` |
| Wrong/old compose file used | Ran from the wrong directory | `cd` into the folder with your `docker-compose.yml` first |
| Orphan-container warnings | Leftover containers from old runs | `docker compose up -d --remove-orphans` |
| `404 NOT_FOUND` for `*.dlq` queues | Queues don't exist on a fresh broker | Create them in the RabbitMQ UI (B5) — or ignore, it's harmless |

---

## Quick reference card

**Publisher (you), after any code change:**
```powershell
docker build -t yashnikam18yn/dlq-analyzer-backend:latest .\dlq-analyzer
docker build -t yashnikam18yn/dlq-analyzer-frontend:latest .\dlq-analyzer-ui
docker push yashnikam18yn/dlq-analyzer-backend:latest
docker push yashnikam18yn/dlq-analyzer-frontend:latest
```

**New user, first run:**
```powershell
# 1. install + start Docker Desktop
# 2. create folder with docker-compose.yml and .env
docker compose up -d
# 3. open http://localhost:3000
```

That's the whole distribution pipeline.

---

# PART C — Updating the app (publishing new features)

When you change the code — for example, adding new AI features — the images on Docker Hub do **not** change until you rebuild and push. An image is a snapshot of your code at build time. The update loop is just the publish steps again, followed by verification.

**Mental model:** local code change → rebuild image → push to Hub → verify.

### C1. Make and test your change locally first

Get the new feature working the normal dev way (run backend/frontend directly, confirm it behaves) **before** packaging it into an image. Docker build is the last step, not the debugging step — you don't want to rebuild an image ten times while fixing a bug.

### C2. Rebuild only the image(s) you changed

From project root `C:\work_dsi\DLQ-Analyzer`:

```powershell
# Backend change:
docker build -t yashnikam18yn/dlq-analyzer-backend:latest .\dlq-analyzer

# Frontend change:
docker build -t yashnikam18yn/dlq-analyzer-frontend:latest .\dlq-analyzer-ui
```

Build both only if you changed both.

### C3. (Recommended) Add a version tag

`latest` always moves to your newest build. A fixed version tag lets you roll back if a release breaks. Build with two tags at once:

```powershell
docker build -t yashnikam18yn/dlq-analyzer-backend:latest -t yashnikam18yn/dlq-analyzer-backend:1.1.0 .\dlq-analyzer
```

Bump the number each release (1.1.0, 1.2.0, ...).

### C4. Push to Docker Hub

```powershell
docker push yashnikam18yn/dlq-analyzer-backend:latest
docker push yashnikam18yn/dlq-analyzer-backend:1.1.0   # if you version-tagged
```

Push the frontend image too if you rebuilt it. You're already logged in. If you ever see `insufficient_scope` again, the CLI login dropped — re-run `docker login -u yashnikam18yn` (use a Read & Write token as the password) until you see `Login Succeeded`.

### C5. Quick verify — does the new image run?

```powershell
docker compose pull
docker compose up -d
```

Then check:
- `docker ps` → all five containers up
- http://localhost:8082/actuator/health → `{"status":"UP"}`
- http://localhost:3000 → **exercise your new feature specifically**, not just that the page loads

### C6. Clean-room verify — does a *stranger* get the new version?

This is the full proof. Deleting your local images forces a real download from the Hub, so you test exactly what a new user receives.

```powershell
docker compose down
docker rmi yashnikam18yn/dlq-analyzer-backend:latest yashnikam18yn/dlq-analyzer-frontend:latest
docker compose pull
docker compose up -d
```

Re-run the C5 checks, focusing on the new feature. If it works here, it works for anyone.

### Two things that will bite you if ignored

1. **Existing users with the old images won't auto-update.** They keep running whatever they pulled until they run `docker compose pull` themselves. Tell users to pull when you ship something important — e.g. "v1.1.0 adds X, run `docker compose pull && docker compose up -d`." This is the strongest reason to use version tags.

2. **A new config value is NOT baked into the image.** If your AI feature needs an API key, model name, or any new env var, it lives in `.env`, not the image. You must:
   - Add the variable to `application.yaml` with a sensible default: `${NEW_VAR:default}`
   - Add it to the `.env` template you give users
   - **Never bake a secret/API key into the pushed image** — anyone who pulls a public image can read what's inside it. Keys always come from `.env` at runtime.

### Update-cycle quick reference

```powershell
# 1. change + test code locally
# 2. rebuild
docker build -t yashnikam18yn/dlq-analyzer-backend:latest .\dlq-analyzer
# 3. push
docker push yashnikam18yn/dlq-analyzer-backend:latest
# 4. refresh local stack
docker compose pull
docker compose up -d
# 5. test the new feature at localhost:3000
```
