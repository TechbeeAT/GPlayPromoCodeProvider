# CLAUDE.md

Guidance for Claude Code (and other AI assistants) working in this repository.

## Project purpose

GPlay Promo Code Provider is a web platform for handing out Google Play promo codes to users, mainly
beta testers. It works for any app.

- **Admins** upload batches of promo codes. Each batch has an app, an optional description, an
  expiration date and the list of codes.
- **Users** open the platform, pick an app and request a code. A user may request several codes.
- Each handed-out code is marked **consumed** right away. Whether it is actually redeemed on Google
  Play is out of scope.

The full specification, roadmap and open questions are in [README.md](./README.md). Keep the README in
sync when a requirement changes or an open question is decided.

The project is built step by step and also serves as a showcase for AI-assisted development. Work in
small, reviewable increments, and point out possible improvements to the owner instead of silently
deciding them.

## Tech stack

- **Kotlin Multiplatform**, Kotlin version in `gradle/libs.versions.toml`
- **Compose Multiplatform for Web**: Wasm target (primary) and JS target (fallback)
- **Ktor** for the server (Kotlin/JVM), planned as module `server`
- Gradle with Kotlin DSL and a version catalog (`gradle/libs.versions.toml`)
- Package: `at.techbee.gplaypromocodeprovider`

## Modules

| Module | Purpose |
|--------|---------|
| `shared` | Compose UI, view models, and later the `@Serializable` DTOs shared with the server. Targets: `js`, `wasmJs` (a `jvm` target is added when the server needs the DTOs). |
| `webApp` | Web entry point (`main.kt` → `ComposeViewport { App() }`), `index.html`, CSS. |
| `server` | *(planned)* Ktor REST API, persistence and admin authentication. |

## Commands

```bash
./gradlew :webApp:wasmJsBrowserDevelopmentRun   # run the web app (Wasm)
./gradlew :webApp:jsBrowserDevelopmentRun       # run the web app (JS)
./gradlew :shared:wasmJsTest                    # tests (Wasm)
./gradlew :shared:jsTest                        # tests (JS)
```

Update this list when the server module and its tasks are added.

## Domain rules (must hold)

1. A promo code is handed out **at most once**. Hand-out is a single atomic database operation
   (select an available code and mark it consumed in one transaction or one conditional `UPDATE`), so
   concurrent requests can never receive the same code.
2. Codes past their batch's expiration date are never handed out.
3. Codes are unique per app. Uploading a duplicate code is rejected or skipped and reported back to
   the admin; it is never silently duplicated.
4. The client never gets bulk access to codes. A user only ever receives the single code they requested.
5. Admin endpoints require authentication. Promo codes are secrets: never log them in full.

## Conventions

- Put shared models and API contracts in `shared/commonMain` so that client and server can't drift apart.
- Keep business logic (code allocation, validation) out of Ktor routes and Compose UI, in plain
  classes that can be unit tested.
- Write tests along with new logic. Code allocation and expiry handling in particular need tests,
  including a concurrency test for rule 1.
- Use `kotlinx-datetime` / `kotlin.time.Instant` for timestamps and store them in UTC.
- Add dependencies through `gradle/libs.versions.toml`, not as inline version strings.
- Follow the official Kotlin code style (`kotlin.code.style=official`).
- Keep commits small and focused, with descriptive messages.

## Working with the owner

- When something in the spec is ambiguous, ask instead of assuming. Record decisions in the README.
- Suggest improvements (architecture, security, UX, tooling) when you see them; the owner wants to
  learn from this project.
