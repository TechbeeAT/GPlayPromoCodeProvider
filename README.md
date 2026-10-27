# GPlay Promo Code Provider

A self-hosted web platform for handing out **Google Play promo codes** to users, especially beta testers,
without sending them one by one over chat or email.

Admins upload batches of promo codes for an app and share the app's invite link. Users open the link,
pick one of the available batches and request a code. The platform hands out an unused code from that
batch and marks it as consumed. It works for **any** app: nothing is tied to a specific product.

> **Status:** Early stage. This README describes the target specification; the implementation is being
> built step by step. The project is also a showcase for AI-assisted development: see [CLAUDE.md](./CLAUDE.md)
> and the reusable [AI-assisted project kickoff guide](./docs/ai-workflow.md).

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

---

## Architecture

| Part | Technology | Module |
|------|------------|--------|
| Web UI | [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/) for Web (Wasm, with a JS fallback) | `webApp`, `shared` |
| Server / REST API | [Ktor](https://ktor.io/) (Kotlin/JVM), also serves the compiled web app | `server` *(planned)* |
| Shared models | Kotlin Multiplatform (`commonMain`), `kotlinx.serialization` DTOs used by both client and server | `shared` |
| Persistence | SQLite, accessed by the server only | `server` |
| Packaging | Docker image, deployed to Fly.io with a volume for the SQLite file | *(planned)* |

### Domain model (draft)

- **App**: `id`, `name`, `packageName?`, `description?`, `inviteToken` (unique, random, regenerable)
- **CodeBatch**: `id`, `appId`, `description?`, `expiresAt`, `createdAt`
- **PromoCode**: `id`, `batchId`, `code` (unique per app), `status` (`AVAILABLE` / `CONSUMED`), `consumedAt?`

---

## Project structure

- [`/shared`](./shared/src): code shared by the Compose Multiplatform apps (UI, view models, and later the DTOs shared with the server).
  - [`commonMain`](./shared/src/commonMain/kotlin): code common to all targets.
  - `jsMain` / `wasmJsMain`: platform-specific code for the two web targets.
- [`/webApp`](./webApp/src): the web entry point (`main.kt`, `index.html`, styles).
- `/server` *(planned)*: the Ktor backend.

---

## Running the app

Web app:
- Wasm target (faster, modern browsers): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
- JS target (slower, supports older browsers): `./gradlew :webApp:jsBrowserDevelopmentRun`

Server: *coming soon.*

## Running tests

- Wasm target: `./gradlew :shared:wasmJsTest`
- JS target: `./gradlew :shared:jsTest`

## Deployment

*Coming soon.* The plan is a single Docker image containing the Ktor server and the compiled web app,
plus a step-by-step guide for deploying it to Fly.io (including a persistent volume for the SQLite
database and setting the admin credentials as secrets).

---

## Roadmap

Each step is one GitHub issue, one AI conversation and one pull request (see [CLAUDE.md](./CLAUDE.md#workflow)
and [docs/ai-workflow.md](./docs/ai-workflow.md)).

1. ✅ Specification: README and CLAUDE.md
2. ✅ Resolve the open questions
3. [#2](https://github.com/TechbeeAT/GPlayPromoCodeProvider/issues/2) Development setup: SessionStart hook so cloud AI sessions can build and test
4. [#3](https://github.com/TechbeeAT/GPlayPromoCodeProvider/issues/3) `server` module (Ktor) with SQLite persistence and the REST API, plus tests
5. [#4](https://github.com/TechbeeAT/GPlayPromoCodeProvider/issues/4) Shared API models, API client in the web app
6. [#5](https://github.com/TechbeeAT/GPlayPromoCodeProvider/issues/5) User UI: invite link page, batch list, request a code, copy and redeem
7. [#6](https://github.com/TechbeeAT/GPlayPromoCodeProvider/issues/6) Admin UI: login, manage apps and invite links, upload batches, statistics
8. [#7](https://github.com/TechbeeAT/GPlayPromoCodeProvider/issues/7) Packaging as a Docker image and deployment to Fly.io, plus CI

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/),
[Kotlin/Wasm](https://kotl.in/wasm/) and [Ktor](https://ktor.io/).
