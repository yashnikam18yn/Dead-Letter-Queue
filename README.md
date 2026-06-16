# DLQ Analyzer

**A self-hosted dashboard for inspecting, grouping, and replaying dead-letter queue messages — so failed messages stop being a black box.**

When a message lands in a dead-letter queue, the broker's native UI tells you *that* it failed, not *why* — and replaying it usually means writing a one-off script. DLQ Analyzer captures failed messages, automatically groups them by root cause, and lets you replay them back to their source with one click once the bug is fixed.

> ⚠️ **Status:** Early-stage, actively developed. RabbitMQ is fully supported; Kafka support and AI-powered explanations are on the roadmap (see below). Suitable for self-hosted/dev use today.

<!-- TODO: add a dashboard screenshot or GIF here — it's the single highest-impact thing you can add.
     ![DLQ Analyzer dashboard](docs/dashboard.png) -->

---

## What it does today

- **Monitors dead-letter queues** across RabbitMQ, with auto-discovery by name pattern (`.dlq`, `-dlq`, `.dead-letter`) or an explicit list.
- **Groups failures by root cause.** A stack-trace fingerprinting classifier collapses the same underlying failure into a single group, even when line numbers shift or framework noise differs — so 500 failed messages become a handful of actionable problems.
- **Inspects each message** — payload, error class, error message, and stack trace.
- **Replays messages** back to a target destination, individually or by group, with a dry-run mode to preview without publishing.
- **Audit log** of every replay and discard, with a shared batch ID for group operations, so actions are traceable.
- **Live updates** to the dashboard over WebSocket as new messages arrive and replays complete.

## Architecture

DLQ Analyzer uses a broker-agnostic adapter pattern: each broker implements a common interface (discover queues, poll messages, republish), so support for new brokers plugs into the same ingestion, grouping, and replay pipeline.

```
React dashboard  ──HTTP/WS──>  Spring Boot API  ──>  Broker adapter (RabbitMQ)
                                      │
                                      ├──> MySQL    (failed messages + audit log)
                                      └──> Redis    (dedup / caching)
```

**Stack:** Spring Boot 3 · RabbitMQ · MySQL · Redis · React + Vite · Docker Compose

## Quickstart

Requires Docker and Docker Compose.

```bash
# 1. Clone
git clone https://github.com/yashnikam18yn/Dead-Letter-Queue.git
cd Dead-Letter-Queue

# 2. Configure — copy the template and edit values
cp env.example .env
#   Set DB/RabbitMQ credentials and your DLQ names. Change the default
#   admin/viewer passwords before any non-local use.

# 3. Start the full stack
docker compose up
```

Then open the dashboard at **http://localhost:3000** and sign in with the credentials from your `.env`.

To point it at your own queues, set `DLQ_NAMES` (comma-separated) in `.env`, or rely on auto-discovery via `DLQ_PATTERNS`.

## Configuration

Key environment variables (see `env.example` for the full list):

| Variable | Purpose |
|---|---|
| `DLQ_NAMES` | Explicit comma-separated DLQ names to monitor (overrides auto-discovery) |
| `DLQ_PATTERNS` | Name suffixes used for auto-discovery (default `.dlq,-dlq,.dead-letter`) |
| `DLQ_POLLING_INTERVAL_MS` | How often to poll the broker (default 30000) |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | Broker connection |
| `DB_*` | MySQL connection |
| `DLQ_ADMIN_USER` / `DLQ_ADMIN_PASSWORD` | Dashboard admin login |

## Running tests

```bash
cd dlq-analyzer
./mvnw test
```

The unit tests cover the error classifier (grouping correctness) and the replay engine (replay, dry-run, failure handling, and batch tracing). They run without any live broker or database.

## Roadmap

- [ ] **AI-powered error explanations** — plain-language summaries of *why* a message failed and likely fixes (Anthropic integration; `ANTHROPIC_API_KEY` is already plumbed in config).
- [ ] **Kafka support** — adapter scaffolding is in place; ingestion and replay not yet implemented.
- [ ] Testcontainers-based integration tests for the broker adapters.
- [ ] Token-based auth (currently HTTP Basic, intended for self-hosted use).

## Scope & honesty

This is a portfolio-grade, self-hosted tool, not a hardened production service. Authentication is HTTP Basic over a self-hosted deployment; supply real secrets via environment variables at deploy time and never commit them. RabbitMQ is the only broker implemented today.

## License

MIT — see [LICENSE](LICENSE).
