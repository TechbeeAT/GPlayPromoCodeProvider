# GPlay Promo Code Provider

A self-hosted web platform for handing out **Google Play promo codes** to users, especially beta testers,
without sending them one by one over chat or email.

Admins upload batches of promo codes for an app and share the app's invite link. Users open the link,
pick one of the available batches and request a code. The platform hands out an unused code from that
batch and marks it as consumed. It works for **any** app: nothing is tied to a specific product.

> **Status:** All roadmap steps are implemented; see the [follow-ups](#follow-ups-and-open-points). The project was
> built step by step and is also a showcase for AI-assisted development (see [CLAUDE.md](./CLAUDE.md)).

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
- Open `https://<host>/admin` and log in with the single admin account configured on the server.
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
| Deployment | One Docker image (Ktor server also serves the web app), deployable to [Fly.io](https://fly.io). One machine with one volume (SQLite), stopped when idle. Details in [Deployment](#deployment). |
| Web app in production | The Wasm build with the JS build as fallback (Compose's `composeCompatibilityBrowserDistribution`), served by Ktor from `WEB_DIR`: `index.html` for every path that isn't a file, gzip, `Cache-Control: no-cache`. |
| Proxy headers | Ktor trusts `X-Forwarded-*` headers (`XForwardedHeaders`), so it knows the browser used HTTPS behind Fly.io's proxy and sends the `Secure` cookie. A client could fake them when talking to the server directly; nothing relies on the client address, so that's harmless now. A future rate limit per client must use Fly.io's `Fly-Client-IP` instead. |
| CI | GitHub Actions: all tests and a Docker image build on every pull request and push to `main`. Deploying `main` to Fly.io is built in but off until enabled (repository variable `FLY_DEPLOY`). |
| Language | English only. |
| Build JDK | Java 21, any vendor (Gradle daemon criteria). Not pinned to Azul so cloud AI sessions can use the preinstalled OpenJDK. |
| API models | Separate KMP module `api` (jvm, js, wasmJs), only kotlinx.serialization. The server doesn't pull in Compose. |
| Admin login | `POST /api/admin/login` with username and password. On success the server creates a session (kept in memory, 12 hours) and sets a random session id as an `HttpOnly`, `SameSite=Strict` cookie (also `Secure` unless `COOKIE_SECURE=false`). Logout ends the session. A server restart logs the admin out. |
| Database access | [Exposed](https://www.jetbrains.com/exposed/) DSL on the SQLite JDBC driver. Migrations are plain SQL versioned with `PRAGMA user_version` (no extra migration library). Timestamps are stored as UTC epoch milliseconds. |
| Atomic hand-out | One SQL statement: `UPDATE promo_code SET consumed_at = ? WHERE consumed_at IS NULL AND id = (SELECT … LIMIT 1) RETURNING code`. Transactions use `BEGIN IMMEDIATE` and a busy timeout, so concurrent writers queue up instead of failing. |
| Expiry | A batch's `expiresAt` is an exact instant (UTC). From that instant on, its remaining codes count as expired. In the admin UI the admin picks a day; the codes are valid until the end of that day (23:59:59) in the admin's browser time zone. |
| API client | `ApiClient` in `shared` (package `client`), on the Ktor client with the browser's `fetch` for JS and Wasm. Every call returns an `ApiResult` (`Success`, `Failure` with status and `ApiError`, `NetworkError`) instead of throwing, so the UI has to handle each case. JSON is parsed leniently (`ignoreUnknownKeys`, `coerceInputValues`), so an older web app keeps working against a newer server. Path segments are encoded including `/`, so an invite token can't change the request path. `webApp/main.kt` creates one client for the page's lifetime and passes it to `App`; it is never closed. On the `jvm` target (previews, tests) there is no default engine, so one must be passed. |
| Invite page | The web app picks its screen from the URL path (`/invite/<token>`; `/admin…` see below; anything else shows a short home page). A deep link and a page reload behave the same. The page lists the app's batches, hands out a code per "Get code" tap, and shows the code with "Copy code" and "Redeem on Google Play". The codes received on the page stay listed (newest first, also if the link stops working meanwhile), but only until the page is closed or reloaded. After every request the batch list is refreshed, so a batch that just ran out disappears. A batch that ran out (`409`) or no longer exists (`404`) is removed from the list right away, with a short notice, so it isn't offered again even if the refresh fails. |
| Compose previews | `shared` has a `jvm` target only so the IDE can render `@Preview`s (and the tests also run fast on the JVM). There is no desktop app. Browser-only code (date formatting, clipboard entry) is `expect`/`actual` with a JVM version in `jvmMain`. |
| Codes left per batch | Not shown to users. The invite API sends no counts, so a link holder learns nothing about volumes. |
| Dates in the user UI | Shown in the browser's time zone as English date and 24-hour time ("31 Jan 2027, 23:59"), formatted by the browser (`Intl`), so daylight saving time is right. |
| Admin UI | `/admin` lists the apps, `/admin/apps/<id>` shows one app: details (editable), invite link (copy, or replace after a confirmation), the upload form and the batches. Statistics (available, consumed, expired) are shown per app and per batch. The admin pages switch without a page load (History API, so back and forward work); each page gets fresh view models, like a page load. There is no link to `/admin` from the user pages. |
| Admin session in the UI | On opening `/admin…` the web app asks `GET /api/admin/session`; without a session it shows the login form. When any admin call answers `401` (session expired, server restarted), the login form appears, but the page's state is kept: after logging in again, the page reloads what failed, and e.g. pasted codes are still in the form. Logout clears it. |
| Upload form | Description, an expiry day (date picker; days that have already ended can't be picked) and the codes. It counts the non-blank lines while typing. After an upload it shows the number of added codes and lists the skipped duplicates (the first 100), clears the form and reloads the statistics. |
| Upload rules | Codes are unique per app (the same code may exist in two different apps). Duplicates, against existing codes of the app or within the upload, are skipped and returned to the admin. An upload with no new code, an expiry date in the past, or a code longer than 128 characters is rejected. At most 10,000 codes per upload. |

---

## Architecture

| Part | Technology | Module |
|------|------------|--------|
| Web UI | [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/) for Web (Wasm, with a JS fallback) | `webApp`, `shared` |
| Server / REST API | [Ktor](https://ktor.io/) (Kotlin/JVM), also serves the compiled web app | `server` |
| Shared models | Kotlin Multiplatform (`commonMain`), `kotlinx.serialization` DTOs used by both client and server | `api` |
| Persistence | SQLite via Exposed, accessed by the server only | `server` |
| Packaging | Docker image, deployed to Fly.io with a volume for the SQLite file; CI on GitHub Actions | `Dockerfile`, `fly.toml`, `.github/workflows` |

### Domain model

- **App**: `id`, `name`, `packageName?`, `description?`, `inviteToken` (unique, 32 random bytes, URL-safe Base64, regenerable), `createdAt`
- **CodeBatch**: `id`, `appId`, `description?`, `expiresAt`, `createdAt`
- **PromoCode**: `id`, `appId`, `batchId`, `code` (unique per app), `consumedAt?` (null while available;
  this replaces the separate `status` column of the first draft). "Expired" is not stored: it follows
  from the batch's `expiresAt`.

### REST API

All bodies are JSON; the models are in [`api`](./api/src/commonMain/kotlin/at/techbee/gplaypromocodeprovider/api).
Errors are `{"error": "<code>", "message": "..."}`, where `error` is the `ApiError` enum
(`not_found`, `no_code_available`, `invalid_request`, `unauthorized`, `internal_error`). Clients should
parse with `coerceInputValues = true`, so a code they don't know yet becomes `unknown` instead of
failing; the web app's API client does this. If an error body isn't an `ErrorResponse` (e.g. a proxy's error
page), the client derives the code from the status (`400`, `401`, `404`, `5xx`; otherwise `unknown`). A `502`, `503` or
`504` without an `ErrorResponse` comes from a proxy whose server is down (the dev server without `:server:run`, Fly.io's
proxy), so the web app treats it as a network error: "The server can't be reached". For a code request that is not the
whole truth: a `504` can come after the server handed out the code. The invite page then says the server didn't answer
and that a code may have been handed out without reaching the user (it is lost; the user can request another one).

User endpoints (scoped by the invite token; an unknown token, an unknown batch and a batch of another
app all give the same `404`):

| Method and path | Response |
|-----------------|----------|
| `GET /api/invite/{token}` | `InviteResponse`: app name and batches with available, non-expired codes |
| `POST /api/invite/{token}/batches/{batchId}/code` | `PromoCodeResponse` with one code, or `409 no_code_available` |

`GET /api/health` answers `200 ok` (Fly.io's health check). Unknown `/api` paths get `404 not_found`.

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
  - [`commonMain`](./shared/src/commonMain/kotlin): code common to all targets: the app shell (`App.kt`,
    picks the screen by `Route`), the API client (`client`), the invite page (`invite`: screen, view model,
    UI state; its building blocks such as the batch list and the code card in `invite/components`), the admin area
    (`admin`: session and login, `apps` list, `app` page with upload) and platform helpers (`platform`: `expect`
    declarations for dates, clipboard and browser history). `PreviewTheme` wraps every `@Preview`.
  - [`webMain`](./shared/src/webMain/kotlin): code for the browser targets (JS and Wasm) only, e.g. date formatting with `Intl`
    and the History API.
  - [`jvmMain`](./shared/src/jvmMain/kotlin): the JVM versions of that code, for the IDE's Compose previews.
  - [`commonTest`](./shared/src/commonTest/kotlin): tests, run in a headless browser for both JS and Wasm.
    The API client is tested against Ktor's `MockEngine`.
- [`/webApp`](./webApp/src): the web entry point (`main.kt`, `index.html`, styles). `webpack.config.d` configures
  the development server.
- [`/server`](./server/src): the Ktor backend.
  - `db`: database connection, migrations, Exposed table mappings.
  - `domain`: business logic (code allocation, upload, statistics, validation), unit tested.
  - `routes`: the HTTP endpoints, admin authentication and serving the web app.
- [`Dockerfile`](./Dockerfile), [`docker-entrypoint.sh`](./docker-entrypoint.sh), [`.dockerignore`](./.dockerignore):
  the Docker image; [`fly.toml`](./fly.toml): the Fly.io configuration (see [Deployment](#deployment)).
- [`.github/workflows/ci.yml`](./.github/workflows/ci.yml): CI (see [CI](#ci)).

---

## Running the app

Server (port 8080 by default):

```bash
ADMIN_USERNAME=admin ADMIN_PASSWORD=secret COOKIE_SECURE=false ./gradlew :server:run
```

Web app development server on <http://localhost:8081>, forwarding `/api` to the server on port 8080
(start the server first):
- Wasm target (faster, modern browsers): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
- JS target (slower, supports older browsers): `./gradlew :webApp:jsBrowserDevelopmentRun`

Every path is answered with `index.html`, so invite links like `http://localhost:8081/invite/<token>` work, also on
reload. The admin UI is at <http://localhost:8081/admin> (log in with `admin` / `secret` from the command above): create
an app, upload codes and copy its invite link there. The same works through the admin API, e.g.:

```bash
curl -c cookies -H 'Content-Type: application/json' -d '{"username":"admin","password":"secret"}' localhost:8080/api/admin/login
curl -b cookies -H 'Content-Type: application/json' -d '{"name":"My App"}' localhost:8080/api/admin/apps
curl -b cookies -H 'Content-Type: application/json' -d '{"description":"Beta","expiresAt":"2027-01-31T23:00:00Z","codes":"CODE1\nCODE2"}' localhost:8080/api/admin/apps/1/batches
```

The second call returns the app's `inviteToken`.

| Variable | Default | Meaning |
|----------|---------|---------|
| `ADMIN_USERNAME` | *(required)* | Admin login name |
| `ADMIN_PASSWORD` | *(required)* | Admin password |
| `PORT` | `8080` | HTTP port |
| `DATABASE_PATH` | `data/gplaypromocodeprovider.db` | SQLite file, created with its directory if missing |
| `COOKIE_SECURE` | `true` | Send the session cookie over HTTPS only. Set to `false` for local development over plain HTTP. |
| `WEB_DIR` | *(none)* | Directory of the compiled web app to serve (the Docker image sets it). Without it the server only answers `/api`; in development the web app comes from the dev server. |

## Running tests

- Wasm target: `./gradlew :shared:wasmJsTest`
- JS target: `./gradlew :shared:jsTest`
- JVM: `./gradlew :shared:jvmTest` (the same tests, fast and without a browser)
- API models: `./gradlew :api:jvmTest`
- Server: `./gradlew :server:test` (JVM, no browser needed; uses temporary SQLite files)

The browser tests need Chrome or Chromium (Karma uses `CHROME_BIN` if set). They run in the time zone `America/New_York`
(`TZ`, set in `shared/build.gradle.kts`), west of UTC, so date mix-ups between UTC and local time fail instead of
passing unnoticed; `BrowserTimeZoneTest` checks that this setting reaches the browser. The JVM tests switch time zones
themselves (`DateFormatTimeZoneTest`). Claude Code cloud sessions are
prepared automatically by a SessionStart hook, see [CLAUDE.md](./CLAUDE.md#cloud-session-setup).

## Deployment

The whole platform ships as **one Docker image**: the Ktor server, which also serves the compiled web app. It runs
anywhere Docker runs; this guide deploys it to [Fly.io](https://fly.io).

### What's in the image

A Docker image is a packaged file system plus a start command. A container is a running instance of it. The
[`Dockerfile`](./Dockerfile) builds the image in two stages:

1. **Build stage** (`eclipse-temurin:21-jdk-noble`: Ubuntu with a JDK). Copies the sources and runs
   `./gradlew :server:installDist :webApp:composeCompatibilityBrowserDistribution`, which compiles the server
   (with its libraries and a start script) and the web app (Wasm, plus the JS build as fallback for browsers
   without the Wasm features it needs; a small loader picks one). Gradle downloads Node.js for the web build by itself.
   This stage is thrown away; it only provides the compiled files.
2. **Runtime stage** (`eclipse-temurin:21-jre-noble`: Ubuntu with only a Java runtime). Contains:
   - `/app/bin/server` and `/app/lib/*.jar`: the server,
   - `/app/web`: the web app's files (`index.html`, scripts, Wasm),
   - `/data`: the directory for the SQLite file, meant to be a **volume** (see below),
   - `sqlite3`, the SQLite command line tool, only for backups,
   - [`docker-entrypoint.sh`](./docker-entrypoint.sh): the start script. The server runs as the unprivileged user
     `app`, not as root. A volume can be mounted owned by root (Fly.io does that), so the script starts as root,
     hands `/data` to `app`, and then starts the server as `app`.

The tests are not run in the image build; CI runs them (see [CI](#ci)). `.dockerignore` keeps local build outputs
and databases out of the build.

**Volumes**: everything a container writes is lost when it's replaced by a new one, e.g. on every deployment. A
volume is storage that lives outside the container and is mounted into it at `/data`, so the database survives.

**Configuration** comes from environment variables (table in [Running the app](#running-the-app)). The image sets
`PORT=8080`, `DATABASE_PATH=/data/gplaypromocodeprovider.db` and `WEB_DIR=/app/web`; `ADMIN_USERNAME` and
`ADMIN_PASSWORD` are passed at start. `JAVA_OPTS` holds the JVM options (heap: 60% of the container's memory).

### Serving the web app

With `WEB_DIR` set, the server answers every path outside `/api` from that directory. Paths without a file
(`/invite/<token>`, `/admin/apps/1`) get `index.html`, so links and page reloads work. Unknown `/api` paths get the
API's JSON `404`. `index.html` has `<base href="/">`: the production build's loader and webpack chunks load further
files with relative URLs, which on `/invite/<token>` would otherwise point to `/invite/originWasmWebApp.js` and get
`index.html` instead of the script. The files are sent gzip-compressed (API responses are not: they contain secrets, and compressing
secrets together with attacker-controlled data can leak them, see BREACH) with `Cache-Control: no-cache`, so a
browser always asks whether its copy is still current (`304 Not Modified` if so) and picks up a new release right
away. `GET /api/health` answers `ok`, for Fly.io's health check.

### Running the image locally

Needs [Docker](https://docs.docker.com/get-started/get-docker/). The first build takes several minutes.

```bash
docker build -t gplay-promo-codes .
```

```bash
docker run --rm -p 8080:8080 -e ADMIN_USERNAME=admin -e ADMIN_PASSWORD=secret -e COOKIE_SECURE=false -v gplay-data:/data gplay-promo-codes
```

- `-p 8080:8080` makes port 8080 of the container reachable as <http://localhost:8080> (admin UI: `/admin`).
- `-e` sets environment variables. `COOKIE_SECURE=false` because this is plain HTTP.
- `-v gplay-data:/data` mounts a named volume `gplay-data`, so the data is still there after `docker run` again.
  `docker volume rm gplay-data` deletes it.
- `--rm` removes the container (not the volume) when it stops; stop it with Ctrl+C.

### Deploying to Fly.io

Fly.io runs Docker images on small virtual machines ("Machines") close to the users. Its proxy accepts HTTPS
(certificates included) and forwards the requests to the server's port 8080. The app is configured in
[`fly.toml`](./fly.toml):

| Setting | Meaning |
|---------|---------|
| `app`, `primary_region` | The app's unique name (address `https://<app>.fly.dev`) and where it runs (`fra`, Frankfurt) |
| `[build]` | Build from the `Dockerfile`; Fly.io builds it on its own builders, no local Docker needed |
| `[env]` | Non-secret environment variables |
| `[mounts]` | The volume `data`, mounted at `/data` |
| `[http_service]` | HTTPS forwarding to port 8080, HTTP redirected to HTTPS, health check on `/api/health`, machine stopped when idle and started on the next request |
| `[[vm]]` | Machine size: 1 shared CPU, 512 MB memory |

The app runs on **exactly one machine**: a Fly volume belongs to a single machine, and SQLite is a file, not a
database server several machines could share. Pricing changes; see [fly.io/pricing](https://fly.io/pricing) (a
512 MB machine that is stopped when idle, plus a 1 GB volume, costs a few dollars a month at most).

One-time setup:

1. Install [flyctl](https://fly.io/docs/flyctl/install/) and log in (creates an account if needed; Fly.io asks for a
   credit card):

   ```bash
   fly auth login
   ```

2. Pick a unique app name, put it into `fly.toml` (`app = "..."`), and create the app:

   ```bash
   fly apps create gplay-promo-codes
   ```

3. Create the volume for the SQLite file in the same region as in `fly.toml`. 1 GB is plenty (a million codes are a
   few dozen MB); it can be extended later with `fly volumes extend`, never shrunk.

   ```bash
   fly volumes create data --region fra --size 1
   ```

   Fly.io warns that a single volume has no redundancy: if the server's disk fails, the data since the last
   snapshot is lost. That is accepted here; see [Backups](#backups).

4. Set the admin credentials as **secrets**. Fly.io stores them encrypted and passes them to the server as
   environment variables; they never appear in `fly.toml` or the repository. Use a long random password.

   ```bash
   fly secrets set ADMIN_USERNAME=admin ADMIN_PASSWORD='<a long random password>'
   ```

5. Deploy. `--ha=false` makes sure Fly.io creates one machine, not two (its default for high availability):

   ```bash
   fly deploy --ha=false
   ```

   Fly.io builds the image, starts a machine with the volume, waits for the health check, and assigns the
   addresses. The app is now at `https://<app>.fly.dev`; the admin UI at `https://<app>.fly.dev/admin`.

Updating: run `fly deploy` again (or let CI do it, see [below](#deploying-from-ci)). The machine is replaced by one
with the new image; the volume, and so the data, stays. A deployment logs the admin out (sessions are kept in
memory). Useful commands: `fly status` (machine state), `fly logs` (server output), `fly secrets list` (names only),
`fly ssh console` (a shell in the running machine).

**Idle machine**: with `auto_stop_machines = "stop"`, the machine stops after a few minutes without requests and
starts again on the next one, which then takes a few seconds. That also logs the admin out. To keep it running,
set `auto_stop_machines = "off"` and `min_machines_running = 1` in `fly.toml`.

### Custom domain and HTTPS

`https://<app>.fly.dev` has HTTPS out of the box. For an own domain, e.g. `codes.example.com`:

1. Tell Fly.io about it; it then requests a certificate from Let's Encrypt and renews it automatically:

   ```bash
   fly certs add codes.example.com
   ```

2. At the domain's DNS provider, add the records that `fly certs add` prints. For a subdomain that is a `CNAME`
   record `codes` → `<app>.fly.dev`; for a domain without subdomain (`example.com`) an `A` and an `AAAA` record with
   the addresses from `fly ips list`.
3. Check the state with `fly certs show codes.example.com`; it usually takes a few minutes.

Invite links are built from the address the admin UI is opened with, so open the admin UI with the custom domain
before copying links.

### Backups

The database is one file, `/data/gplaypromocodeprovider.db`, on the volume. It contains all codes and invite
tokens, so treat every copy as a secret.

- **Snapshots**: Fly.io takes a snapshot of every volume daily and keeps it for 5 days by default.
  `fly volumes list` shows the volume's id, `fly volumes snapshots list <volume id>` its snapshots. To restore one,
  create a new volume from it (`fly volumes create data --snapshot-id <snapshot id> --region fra`), then destroy the
  old volume (`fly volumes destroy <old volume id>`) and run `fly deploy` again.
- **Own copies** (recommended before risky changes and regularly): make a consistent copy with `sqlite3`'s
  `.backup` (copying the file itself while the server runs can miss recent changes, which are in the
  `-wal` file next to it), download it, and delete it on the server:

  ```bash
  fly ssh console -C "sqlite3 /data/gplaypromocodeprovider.db '.backup /data/backup.db'"
  ```

  ```bash
  fly ssh sftp get /data/backup.db ./gplay-backup.db
  ```

  ```bash
  fly ssh console -C "rm /data/backup.db"
  ```

  If the machine is stopped (idle), open the app's address first or run `fly machine start`.
- **Restoring an own copy**: upload it (`fly ssh sftp shell`, then `put gplay-backup.db /data/restore.db`), copy it
  into the database, and restart the server:

  ```bash
  fly ssh console -C "sqlite3 /data/gplaypromocodeprovider.db '.restore /data/restore.db'"
  ```

  ```bash
  fly apps restart
  ```

  Then delete `/data/restore.db` like the backup above.

## CI

[GitHub Actions](https://docs.github.com/actions) runs [`.github/workflows/ci.yml`](./.github/workflows/ci.yml) on
every pull request and every push to `main`:

- **Build and test**: all test tasks (`:shared:jsTest :shared:wasmJsTest :shared:jvmTest :api:jvmTest :server:test`)
  with Java 21 and the runner's Chrome. A failing test fails the run; the test reports are then attached to it.
- **Build Docker image**: builds the image (without pushing it), so a broken `Dockerfile` shows up in the pull
  request and not only when deploying.
- **Deploy to Fly.io** (optional, off by default): after both jobs passed on `main`, runs `flyctl deploy`.

### Deploying from CI

1. Create a deploy token, which can only deploy this app:

   ```bash
   fly tokens create deploy --expiry 8760h
   ```

2. In the GitHub repository, under *Settings → Secrets and variables → Actions*: add the token as the **secret**
   `FLY_API_TOKEN`, and add the **variable** `FLY_DEPLOY` with the value `true`.

From then on every merge to `main` is deployed. The token expires after a year (`8760h`); create a new one then.
Remove the variable (or set it to anything else) to stop deploying from CI.

---

## Roadmap

Each step is one GitHub issue, one AI conversation and one pull request (see [CLAUDE.md](./CLAUDE.md#workflow)).

1. ✅ Specification: README and CLAUDE.md
2. ✅ Resolve the open questions
3. ✅ [#2](https://github.com/TechbeeAT/GPlayPromoCodeProvider/issues/2) Development setup: SessionStart hook so cloud AI sessions can build and test
4. ✅ [#3](https://github.com/TechbeeAT/GPlayPromoCodeProvider/issues/3) `server` module (Ktor) with SQLite persistence and the REST API, plus tests
5. ✅ [#4](https://github.com/TechbeeAT/GPlayPromoCodeProvider/issues/4) Shared API models, API client in the web app
6. ✅ [#5](https://github.com/TechbeeAT/GPlayPromoCodeProvider/issues/5) User UI: invite link page, batch list, request a code, copy and redeem
7. ✅ [#6](https://github.com/TechbeeAT/GPlayPromoCodeProvider/issues/6) Admin UI: login, manage apps and invite links, upload batches, statistics
8. ✅ [#7](https://github.com/TechbeeAT/GPlayPromoCodeProvider/issues/7) Packaging as a Docker image and deployment to Fly.io, plus CI

### Follow-ups and open points

- **Suggestion**: rate-limit `POST /api/admin/login` (Ktor `RateLimit` plugin) against password guessing.
  The invite endpoints could be rate-limited too.
- **Accepted for now** (decided after step #5, no action planned):
  - Received codes are lost when the user reloads or closes the invite page (the page says so). Keeping them in
    the browser's `localStorage` per invite token would make that safer.
  - Compose for Web has no monospace font built in, so codes are shown in the default font, where `O`/`0` and
    `I`/`l` can look alike. Bundling a monospace font (Compose resources) would help users typing a code by hand.
  - Compose draws onto a canvas, so the page has no DOM text. The UI was tested end to end with Playwright
    screenshots and clicks at coordinates; the view model is unit tested. Screen reader support of Compose for Web
    has not been checked.
- **Open idea** (owner may come back to it): Google Play's redeem link (`https://play.google.com/redeem?code=…`) has no
  package parameter; Play finds the app from the code. A second button "Open in Google Play"
  (`https://play.google.com/store/apps/details?id=<packageName>`) could be shown when the app has a package name.
  That needs `packageName` in `InviteResponse`, i.e. link holders would see it.
- **Suggestion**: deleting apps and batches is not part of the API yet. Closing a batch early (e.g. a wave that
  should stop) would then need the API and an admin UI button.
- **Note**: the admin login form is drawn by Compose on a canvas, so browser password managers can't fill it in.
  A plain HTML login form in `index.html` (posting to `/api/admin/login`) would fix that, if it becomes annoying.
- **Suggestion**: the admin UI has no search or paging. Fine for a handful of apps and batches; worth a look if
  the lists get long. Uploads are capped at 10,000 codes; a CSV import could come with a file picker.
- **Note**: the date picker starts weeks on Sunday (Material's default for the browser locale `en`). Showing
  Monday first would need a locale override.
- **Decision**: `server/.../db/Tables.kt` mirrors the SQL in `Migrations.kt` by hand; nothing checks
  that they match (the tests only catch mismatches in columns they use). Fine at three tables and with
  no migrations planned. If the schema starts changing, consider a schema check test (Exposed's
  `exposed-migration` module) or switching to SQLDelight, whose compiler checks queries and migrations.

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/),
[Kotlin/Wasm](https://kotl.in/wasm/) and [Ktor](https://ktor.io/).
