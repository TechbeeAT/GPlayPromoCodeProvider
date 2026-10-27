#!/bin/bash
# SessionStart hook: prepares a Claude Code cloud session so the project can be built and tested.
# Locally it does nothing. See CLAUDE.md ("Cloud session setup") for details.
set -euo pipefail

# Stdout of a SessionStart hook is added to the session context, so progress goes to stderr
# and only a short summary goes to stdout.

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

cd "${CLAUDE_PROJECT_DIR:-$(dirname "$0")/../..}"

# 1. JDK 21 (runs the Gradle daemon, see gradle/gradle-daemon-jvm.properties).
java_major() {
  java -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -n 1
}
if ! command -v java >/dev/null 2>&1 || [ "$(java_major)" != "21" ]; then
  echo "Installing OpenJDK 21..." >&2
  apt-get update -qq >&2
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-21-jdk-headless >&2
fi

# 2. GitHub-hosted npm packages. Kotlin/JS pins a fork of karma (the browser test runner) as a
# GitHub tarball. The cloud network policy blocks codeload.github.com, but git access to github.com
# works. So we build those tarballs with git and put them in a Yarn offline mirror, where Yarn
# looks before downloading. The lockfile has no integrity hash for these entries.
mirror_dir="$HOME/.cache/yarn-offline-mirror"
mkdir -p "$mirror_dir"
if ! grep -qs "yarn-offline-mirror" "$HOME/.yarnrc"; then
  echo "yarn-offline-mirror \"$mirror_dir\"" >> "$HOME/.yarnrc"
fi
grep -ho 'https://codeload.github.com/[^"]*/tar.gz/[0-9a-f]*' kotlin-js-store/yarn.lock kotlin-js-store/*/yarn.lock 2>/dev/null |
  sort -u |
  while IFS=/ read -r _ _ _ owner repo _ sha; do
    [ -s "$mirror_dir/$sha" ] && continue
    echo "Packing $owner/$repo@$sha for the Yarn offline mirror..." >&2
    tmp="$(mktemp -d)"
    git -C "$tmp" init -q
    git -C "$tmp" fetch -q --depth 1 "https://github.com/$owner/$repo.git" "$sha"
    git -C "$tmp" archive --format=tar.gz --prefix="$repo-$sha/" -o "$mirror_dir/$sha.tmp" FETCH_HEAD
    mv "$mirror_dir/$sha.tmp" "$mirror_dir/$sha"
    rm -rf "$tmp"
  done

# 3. Headless Chrome for the browser tests (Karma reads CHROME_BIN). The cloud image ships
# Playwright's Chromium. Sessions run as root, where Chrome only starts with --no-sandbox,
# so CHROME_BIN points to a small wrapper that adds the flag.
chromium="${PLAYWRIGHT_CHROMIUM:-/opt/pw-browsers/chromium}"
chrome_wrapper="$HOME/.local/bin/chrome-no-sandbox"
if [ -x "$chromium" ]; then
  mkdir -p "$(dirname "$chrome_wrapper")"
  printf '#!/bin/sh\nexec "%s" --no-sandbox "$@"\n' "$chromium" > "$chrome_wrapper"
  chmod +x "$chrome_wrapper"
  export CHROME_BIN="$chrome_wrapper"
  if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
    echo "export CHROME_BIN=\"$chrome_wrapper\"" >> "$CLAUDE_ENV_FILE"
  fi
else
  echo "Warning: no Chromium at $chromium, browser tests will fail." >&2
fi

# 4. Gradle distribution, dependencies, Node/Yarn packages and a first build.
# Running the tests once downloads everything they need; afterwards Gradle's up-to-date
# checks make this step fast. Maven Central often answers 429 (rate limit) to a fresh
# container, so network errors are retried. Other failures (e.g. a failing test) are not.
gradle_args=(
  --console=plain
  -Dorg.gradle.internal.repository.max.retries=8
  -Dorg.gradle.internal.repository.initial.backoff=1000
  :shared:jsTest :shared:wasmJsTest
)
log="$(mktemp)"
trap 'rm -f "$log"' EXIT
for attempt in 1 2 3 4 5 6; do
  if ./gradlew "${gradle_args[@]}" 2>&1 | tee "$log" >&2; then
    echo "Cloud session setup complete: JDK 21, Gradle and npm dependencies, CHROME_BIN; :shared:jsTest and :shared:wasmJsTest pass."
    exit 0
  fi
  if ! grep -qE 'status code (429|5[0-9][0-9])|Could not (GET|HEAD|resolve)|Connection (reset|refused)|timed out' "$log"; then
    echo "Gradle warm-up failed (not a network error, not retrying)." >&2
    exit 1
  fi
  echo "Gradle warm-up hit a network error (attempt $attempt), retrying in $((attempt * 15))s..." >&2
  sleep $((attempt * 15))
done

echo "Gradle warm-up failed after several retries. Check the output above (network policy, Maven Central rate limits)." >&2
exit 1
