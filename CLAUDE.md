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
- **Ktor** for the server (Kotlin/JVM), module `server`. It will also serve the compiled web app.
- **SQLite** for persistence, accessed through **Exposed** (DSL, JDBC). Schema migrations are plain SQL
  in `server/.../db/Migrations.kt`, versioned with `PRAGMA user_version`.
- **Docker** image as the single deployable, deployed to **Fly.io** (SQLite file on a Fly volume)
- UI language: English only (no i18n needed)
- Gradle with Kotlin DSL and a version catalog (`gradle/libs.versions.toml`)
- Package: `at.techbee.gplaypromocodeprovider`

## Modules

| Module | Purpose |
|--------|---------|
| `api` | `@Serializable` DTOs of the REST API, shared by server and web app. Only depends on kotlinx.serialization (no Compose). Targets: `jvm`, `js`, `wasmJs`. |
| `shared` | Compose UI and view models. Targets: `js`, `wasmJs`. Will depend on `api` (step #4). |
| `webApp` | Web entry point (`main.kt` → `ComposeViewport { App() }`), `index.html`, CSS. |
| `server` | Ktor REST API (Kotlin/JVM), SQLite persistence via Exposed, admin authentication. Will also serve the web app (step #7). |

## Commands

```bash
./gradlew :webApp:wasmJsBrowserDevelopmentRun   # run the web app (Wasm)
./gradlew :webApp:jsBrowserDevelopmentRun       # run the web app (JS)
./gradlew :shared:wasmJsTest                    # tests (Wasm)
./gradlew :shared:jsTest                        # tests (JS)
./gradlew :server:test                          # server tests (JVM)
ADMIN_USERNAME=admin ADMIN_PASSWORD=secret COOKIE_SECURE=false ./gradlew :server:run   # run the server on :8080
```

Server environment variables are documented in `server/.../ServerConfig.kt` and the README.

## Cloud session setup

Claude Code cloud sessions start in a fresh container. The SessionStart hook
[`.claude/hooks/session-start.sh`](./.claude/hooks/session-start.sh) (registered in
`.claude/settings.json`) prepares it so the commands above work right away. It runs only when
`CLAUDE_CODE_REMOTE=true`; on a local machine it exits immediately. It runs synchronously, so the
session starts once it is done. The steps:

1. **JDK 21**: installs OpenJDK 21 through apt if neither `java` on PATH nor a JDK under `/usr/lib/jvm`
   is version 21. The Gradle
   daemon needs Java 21 of any vendor (`gradle/gradle-daemon-jvm.properties`). The vendor was
   previously pinned to Azul, but the cloud network policy blocks `cdn.azul.com`.
2. **Karma from GitHub**: Kotlin/JS pins a fork of karma (the browser test runner) as a GitHub
   tarball from `codeload.github.com`. Cloud sessions can only download such tarballs for the
   repositories attached to the session; for others GitHub access answers 403, even when the domain
   is in the environment's allowed domains. Cloning public repositories with git works, so the hook
   fetches that commit with git and puts a `git archive` of it into a Yarn offline mirror
   (`~/.cache/yarn-offline-mirror`, configured in `~/.yarnrc`), where Yarn looks first. Every
   `codeload.github.com` URL in `kotlin-js-store/**/yarn.lock` is handled this way. If packing
   fails, the hook only prints a warning and continues.
3. **Headless Chrome**: points `CHROME_BIN` (also exported for the session through
   `CLAUDE_ENV_FILE`) to a wrapper around the image's Playwright Chromium that adds `--no-sandbox`,
   because sessions run as root.
4. **Gradle warm-up**: runs `./gradlew :shared:jsTest :shared:wasmJsTest :server:test`. This downloads the Gradle
   distribution, Maven and npm dependencies, Node.js and Yarn, compiles and runs the tests. Maven
   Central often answers `429 Too Many Requests` to a fresh container, so network errors are retried
   (up to 6 attempts); other failures, such as a failing test, are not.

A fresh container takes about 2 to 6 minutes (mostly waiting out Maven Central rate limits). When
everything is already present, the hook takes a few seconds and is safe to run again.

To verify it, run it by hand in a cloud session and then run the tests:

```bash
CLAUDE_CODE_REMOTE=true .claude/hooks/session-start.sh
./gradlew :shared:jsTest :shared:wasmJsTest :server:test
```

When a new module or test task is added, add it to the warm-up in the hook.

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

- Put shared models and API contracts in `api/commonMain` so that client and server can't drift apart.
- Keep business logic (code allocation, validation) out of Ktor routes and Compose UI, in plain
  classes that can be unit tested (server: package `server.domain`; routes only map HTTP to it).
- Inject `kotlin.time.Clock` wherever "now" matters, so tests can control time.
- Never install Ktor's `CallLogging` or log request paths: they contain invite tokens. The
  `Exposed` logger is off because it logs statement values (codes). Don't log exception messages of
  `SQLException`s.
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
