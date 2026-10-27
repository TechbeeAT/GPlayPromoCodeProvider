# GPlay Promo Code Provider

A self-hosted web platform for handing out **Google Play promo codes** to users, especially beta testers,
without sending them one by one over chat or email.

Admins upload batches of promo codes for an app and share the app's invite link. Users open the link,
pick one of the available batches and request a code. The platform hands out an unused code from that
batch and marks it as consumed. It works for **any** app: nothing is tied to a specific product.

> **Status:** Early stage. This README describes the target specification; the implementation is being
> built step by step. The project is also a showcase for AI-assisted development (see [CLAUDE.md](./CLAUDE.md)).

---

## Features

### For users
- Open the **invite link** of an app (e.g. `https://<host>/invite/<token>`). There is no public list
  of apps, and users don't need an account.
- See the batches of that app that still have codes available, each with the app name, the batch
  description and the expiration date.
- Pick a batch and request a code with one click.
- Request more codes whenever needed (e.g. for several devices or accounts). There is no per-user limit.
- Copy the code, or open Google Play's redeem page directly
  (`https://play.google.com/redeem?code=<CODE>`).

### For admins
- Log in with the single admin account configured on the server.
- Manage apps (name, optional Google Play package name, optional description).
- Get each app's invite link, and regenerate it to revoke the old one.
- Upload a **batch** of promo codes with:
  - the app the codes belong to,
  - an optional description (e.g. "Beta wave 2", "Black Friday"),
  - the expiration date of the codes,
  - the codes, pasted into a text field, one code per line. Many codes can be pasted at once.
    Surrounding whitespace is trimmed and blank lines are ignored.
- See how many codes are available, consumed and expired, per app and per batch.

### Code lifecycle

```
AVAILABLE ──(user requests code)──▶ CONSUMED
    │
    └──(expiration date passes)───▶ EXPIRED (never handed out)
```

- A code is marked **consumed** as soon as it is handed out. Whether it is actually redeemed on
  Google Play is not tracked and doesn't matter.
- A code is handed out to exactly one request. Two parallel requests must never get the same code.
- Expired codes are never handed out, and batches without available codes are not shown to users.

### Decisions

| Topic | Decision |
|-------|----------|
| User access | One secret invite link per app. No user accounts. |
| Request limits | None. Users are not tracked; abuse is unlikely and its impact is low. |
| Admin authentication | One admin account, configured through environment variables. |
| Database | SQLite (one file, stored on a persistent volume in production). |
| Code upload | Text field, one code per line, multiple codes at once. CSV import may come later. |
| Which code is handed out | The user picks a batch (app, description, expiry date); any available code of that batch is handed out. |
| Deployment | One Docker image (Ktor server also serves the web app), deployable to [Fly.io](https://fly.io). |
| Language | English only. |
| Build JDK | Java 21, any vendor (Gradle daemon criteria). Not pinned to Azul so cloud AI sessions can use the preinstalled OpenJDK. |
| API models | Separate KMP module `api` (jvm, js, wasmJs), only kotlinx.serialization. The server doesn't pull in Compose. |
| Admin login | `POST /api/admin/login` with username and password. On success the server creates a session (kept in memory, 12 hours) and sets a random session id as an `HttpOnly`, `SameSite=Strict` cookie (also `Secure` unless `COOKIE_SECURE=false`). Logout ends the session. A server restart logs the admin out. |
| Database access | [Exposed](https://www.jetbrains.com/exposed/) DSL on the SQLite JDBC driver. Migrations are plain SQL versioned with `PRAGMA user_version` (no extra migration library). Timestamps are stored as UTC epoch milliseconds. |
| Atomic hand-out | One SQL statement: `UPDATE promo_code SET consumed_at = ? WHERE consumed_at IS NULL AND id = (SELECT … LIMIT 1) RETURNING code`. Transactions use `BEGIN IMMEDIATE` and a busy timeout, so concurrent writers queue up instead of failing. |
| Expiry | A batch's `expiresAt` is an exact instant (UTC). From that instant on, its remaining codes count as expired. The admin UI will turn a picked date into an instant. |
| Upload rules | Codes are unique per app (the same code may exist in two different apps). Duplicates, against existing codes of the app or within the upload, are skipped and returned to the admin. An upload with no new code, an expiry date in the past, or a code longer than 128 characters is rejected. At most 10,000 codes per upload. |

---

## Architecture

| Part | Technology | Module |
|------|------------|--------|
| Web UI | [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/) for Web (Wasm, with a JS fallback) | `webApp`, `shared` |
| Server / REST API | [Ktor](https://ktor.io/) (Kotlin/JVM), will also serve the compiled web app | `server` |
| Shared models | Kotlin Multiplatform (`commonMain`), `kotlinx.serialization` DTOs used by both client and server | `api` |
| Persistence | SQLite via Exposed, accessed by the server only | `server` |
| Packaging | Docker image, deployed to Fly.io with a volume for the SQLite file | *(planned)* |

### Domain model

- **App**: `id`, `name`, `packageName?`, `description?`, `inviteToken` (unique, 32 random bytes, URL-safe Base64, regenerable), `createdAt`
- **CodeBatch**: `id`, `appId`, `description?`, `expiresAt`, `createdAt`
- **PromoCode**: `id`, `appId`, `batchId`, `code` (unique per app), `consumedAt?` (null while available;
  this replaces the separate `status` column of the first draft). "Expired" is not stored: it follows
  from the batch's `expiresAt`.

### REST API

All bodies are JSON; the models are in [`api`](./api/src/commonMain/kotlin/at/techbee/gplaypromocodeprovider/api).
Errors are `{"error": "<code>", "message": "..."}`.

User endpoints (scoped by the invite token; an unknown token, an unknown batch and a batch of another
app all give the same `404`):

| Method and path | Response |
|-----------------|----------|
| `GET /api/invite/{token}` | `InviteResponse`: app name and batches with available, non-expired codes |
| `POST /api/invite/{token}/batches/{batchId}/code` | `PromoCodeResponse` with one code, or `409 no_code_available` |

Admin endpoints (all except login and logout need the session cookie, otherwise `401`):

| Method and path | Response |
|-----------------|----------|
| `POST /api/admin/login` | `204` and session cookie, or `401` |
| `POST /api/admin/logout` | `204` |
| `GET /api/admin/session` | `AdminSessionResponse` (to check whether the admin is logged in) |
| `GET /api/admin/apps` | `AdminApp` list, each with statistics |
| `POST /api/admin/apps` | `201`, the new `AdminApp` |
| `GET /api/admin/apps/{appId}` | `AdminApp` |
| `PUT /api/admin/apps/{appId}` | the updated `AdminApp` |
| `POST /api/admin/apps/{appId}/invite-token` | `AdminApp` with a new invite token (the old link stops working) |
| `GET /api/admin/apps/{appId}/batches` | `AdminBatch` list with statistics, newest first |
| `POST /api/admin/apps/{appId}/batches` | `201`, `BatchUploadResponse` (new batch, number of added codes, skipped duplicates) |

---

## Project structure

- [`/api`](./api/src/commonMain/kotlin): the REST API models, shared by server and web app.
- [`/shared`](./shared/src): code shared by the Compose Multiplatform apps (UI, view models).
  - [`commonMain`](./shared/src/commonMain/kotlin): code common to all targets.
  - `jsMain` / `wasmJsMain`: platform-specific code for the two web targets.
- [`/webApp`](./webApp/src): the web entry point (`main.kt`, `index.html`, styles).
- [`/server`](./server/src): the Ktor backend.
  - `db`: database connection, migrations, Exposed table mappings.
  - `domain`: business logic (code allocation, upload, statistics, validation), unit tested.
  - `routes`: the HTTP endpoints and admin authentication.

---

## Running the app

Web app:
- Wasm target (faster, modern browsers): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
- JS target (slower, supports older browsers): `./gradlew :webApp:jsBrowserDevelopmentRun`

Server (port 8080 by default):

```bash
ADMIN_USERNAME=admin ADMIN_PASSWORD=secret COOKIE_SECURE=false ./gradlew :server:run
```

| Variable | Default | Meaning |
|----------|---------|---------|
| `ADMIN_USERNAME` | *(required)* | Admin login name |
| `ADMIN_PASSWORD` | *(required)* | Admin password |
| `PORT` | `8080` | HTTP port |
| `DATABASE_PATH` | `data/gplaypromocodeprovider.db` | SQLite file, created with its directory if missing |
| `COOKIE_SECURE` | `true` | Send the session cookie over HTTPS only. Set to `false` for local development over plain HTTP. |

## Running tests

- Wasm target: `./gradlew :shared:wasmJsTest`
- JS target: `./gradlew :shared:jsTest`
- Server: `./gradlew :server:test` (JVM, no browser needed; uses temporary SQLite files)

The browser tests need Chrome or Chromium (Karma uses `CHROME_BIN` if set). Claude Code cloud sessions are
prepared automatically by a SessionStart hook, see [CLAUDE.md](./CLAUDE.md#cloud-session-setup).

## Deployment

*Coming soon.* The plan is a single Docker image containing the Ktor server and the compiled web app,
plus a step-by-step guide for deploying it to Fly.io (including a persistent volume for the SQLite
database and setting the admin credentials as secrets).

---

## Roadmap

Each step is one GitHub issue, one AI conversation and one pull request (see [CLAUDE.md](./CLAUDE.md#workflow)).

1. ✅ Specification: README and CLAUDE.md
2. ✅ Resolve the open questions
3. ✅ [#2](https://github.com/TechbeeAT/GPlayPromoCodeProvider/issues/2) Development setup: SessionStart hook so cloud AI sessions can build and test
4. ✅ [#3](https://github.com/TechbeeAT/GPlayPromoCodeProvider/issues/3) `server` module (Ktor) with SQLite persistence and the REST API, plus tests
5. [#4](https://github.com/TechbeeAT/GPlayPromoCodeProvider/issues/4) Shared API models, API client in the web app
6. [#5](https://github.com/TechbeeAT/GPlayPromoCodeProvider/issues/5) User UI: invite link page, batch list, request a code, copy and redeem
7. [#6](https://github.com/TechbeeAT/GPlayPromoCodeProvider/issues/6) Admin UI: login, manage apps and invite links, upload batches, statistics
8. [#7](https://github.com/TechbeeAT/GPlayPromoCodeProvider/issues/7) Packaging as a Docker image and deployment to Fly.io, plus CI

### Follow-ups and open points

- **Step #4**: make `shared` depend on `api`.
- **Step #7**: serve the compiled web app from the server. Behind Fly.io's proxy, TLS ends at the
  proxy, so Ktor needs `XForwardedHeaders` to see `https` (otherwise it refuses to send the `Secure`
  session cookie).
- **Suggestion**: rate-limit `POST /api/admin/login` (Ktor `RateLimit` plugin) against password guessing.
  The invite endpoints could be rate-limited too.
- **Suggestion**: deleting apps and batches is not part of the API yet.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/),
[Kotlin/Wasm](https://kotl.in/wasm/) and [Ktor](https://ktor.io/).
