# CLAUDE.md

Guidance for Claude Code (and other AI assistants) working in this repository.

## Project purpose

GPlay Promo Code Provider is a web platform for handing out Google Play promo codes to users, mainly
beta testers. It works for any app.

- **Admins** (one account, configured through environment variables) upload batches of promo codes.
  Each batch has an app, an optional description, an expiration date and the list of codes (pasted
  into a text field, one per line).
- **Users** open an app's secret invite link, see that app's batches (app, description, expiry date),
  pick one and request a code. There are no user accounts and no per-user limits; a user may request
  as many codes as they like.
- Each handed-out code is marked **consumed** right away. Whether it is actually redeemed on Google
  Play is out of scope.

The full specification, decisions and roadmap are in [README.md](./README.md). Keep the README in
sync when a requirement changes or a new decision is made.

The project is built step by step and also serves as a showcase for AI-assisted development. Work in
small, reviewable increments, and point out possible improvements to the owner instead of silently
deciding them.

## Tech stack

- **Kotlin Multiplatform**, Kotlin version in `gradle/libs.versions.toml`
- **Compose Multiplatform for Web**: Wasm target (primary) and JS target (fallback)
- **Ktor** for the server (Kotlin/JVM), planned as module `server`. It also serves the compiled web app.
- **SQLite** for persistence
- **Docker** image as the single deployable, deployed to **Fly.io** (SQLite file on a Fly volume)
- UI language: English only (no i18n needed)
- Gradle with Kotlin DSL and a version catalog (`gradle/libs.versions.toml`)
- Package: `at.techbee.gplaypromocodeprovider`

## Modules

| Module | Purpose |
|--------|---------|
| `shared` | Compose UI, view models, and later the `@Serializable` DTOs shared with the server. Targets: `js`, `wasmJs` (a `jvm` target is added when the server needs the DTOs). |
| `webApp` | Web entry point (`main.kt` → `ComposeViewport { App() }`), `index.html`, CSS. |
| `server` | *(planned)* Ktor REST API, SQLite persistence, admin authentication, serving the web app. |

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
5. User endpoints are scoped by the app's invite token. An unknown token reveals nothing (not even
   whether an app exists), and there is no public list of apps.
6. Admin endpoints require authentication. Admin credentials come from environment variables, never
   from the repository. Promo codes and invite tokens are secrets: never log them in full.
7. Uploaded codes are parsed one per line; whitespace is trimmed and blank lines are ignored.

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

## Workflow

The project is built along the roadmap in the README. Each roadmap step has a GitHub issue.

- **One step = one issue = one conversation = one pull request.** A session typically starts with
  "Work on issue #N". Read the issue, README and this file first; stay within the issue's scope.
- Reference the issue in the PR description (`Closes #N`).
- Before opening or updating the PR, run the build and tests, and review your own diff.
- At the end of a step:
  - tick it off in the README roadmap (✅),
  - update README and CLAUDE.md for any new decisions, commands or modules,
  - record follow-ups and open questions in the issue, a new issue, or the README.
- Never leave decisions or next steps only in the chat. The next conversation starts from the
  default branch and sees only what is committed and in GitHub.

## Working with the owner

- When something in the spec is ambiguous, ask instead of assuming. Record decisions in the README.
- Suggest improvements (architecture, security, UX, tooling) when you see them; the owner wants to
  learn from this project.
- The owner is new to Docker and Fly.io. When working on packaging and deployment, explain each
  step (Dockerfile stages, image contents, `fly.toml`, volumes, secrets) rather than only producing files.
