# GPlay Promo Code Provider

A self-hosted web platform for handing out **Google Play promo codes** to users, especially beta testers,
without sending them one by one over chat or email.

Admins upload batches of promo codes for an app. Users open the platform, pick the app and request a code.
The platform hands out an unused code and marks it as consumed. It works for **any** app: nothing is tied
to a specific product.

> **Status:** Early stage. This README describes the target specification; the implementation is being
> built step by step. The project is also a showcase for AI-assisted development (see [CLAUDE.md](./CLAUDE.md)).

---

## Features

### For users
- See the apps that currently have promo codes available.
- Request a promo code for an app with one click.
- Request more than one code (e.g. for several devices or accounts).
- Copy the code, or open Google Play's redeem page directly
  (`https://play.google.com/redeem?code=<CODE>`).

### For admins
- Manage apps (name, optional Google Play package name, optional description).
- Upload a **batch** of promo codes with:
  - the app the codes belong to,
  - an optional description (e.g. "Beta wave 2", "Black Friday"),
  - the expiration date of the codes,
  - the list of codes.
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
- Expired codes are never handed out.

---

## Architecture

| Part | Technology | Module |
|------|------------|--------|
| Web UI | [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/) for Web (Wasm, with a JS fallback) | `webApp`, `shared` |
| Server / REST API | [Ktor](https://ktor.io/) (Kotlin/JVM) | `server` *(planned)* |
| Shared models | Kotlin Multiplatform (`commonMain`), `kotlinx.serialization` DTOs used by both client and server | `shared` |
| Persistence | Relational database, accessed by the server only | *(to be decided)* |

### Domain model (draft)

- **App**: `id`, `name`, `packageName?`, `description?`
- **CodeBatch**: `id`, `appId`, `description?`, `expiresAt`, `createdAt`
- **PromoCode**: `id`, `batchId`, `code` (unique), `status` (`AVAILABLE` / `CONSUMED`), `consumedAt?`

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

---

## Roadmap

1. ✅ Specification: README and CLAUDE.md
2. Resolve the open questions below
3. Add the `server` module (Ktor) with persistence and the REST API, plus tests
4. Shared DTOs in `shared`, API client in the web app
5. User UI: app list, request a code, copy and redeem
6. Admin UI: manage apps, upload batches, statistics
7. Authentication and abuse protection
8. Packaging and deployment (e.g. Docker) and CI

## Open questions

These need a decision before or during implementation:

1. **User access:** Who may request codes? Anonymous visitors, users with a per-app invite link or
   token, or signed-in users (e.g. Google Sign-In)?
2. **Request limits:** Is "multiple codes per user" unlimited, or should there be a limit or cooldown
   per user and app?
3. **Admin authentication:** One admin configured through the environment, or several admin accounts?
4. **Database:** SQLite (simple, one file) or PostgreSQL (more robust for hosting)?
5. **Code upload format:** Paste codes one per line, upload the CSV exported from the Play Console, or both?
6. **Handout order:** Hand out the codes that expire soonest first?
7. **Deployment:** Should the Ktor server also serve the compiled web app (one deployable), and where
   will it be hosted?
8. **Language:** English only, or also German (i18n)?

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/),
[Kotlin/Wasm](https://kotl.in/wasm/) and [Ktor](https://ktor.io/).
