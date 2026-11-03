#!/bin/bash
# Runs ./gradlew with the given arguments and retries when the failure was a dependency download
# problem, not the build itself. Used by .github/workflows/ci.yml.
#
# Maven Central sometimes fails a request for a short time: 429 (rate limit), 5xx, or even 404 for a
# file that exists. Gradle retries 429 and 5xx itself, but a 404 means "not there" to it, so the
# build fails at once. Gradle also remembers the missing file in its cache, which CI saves and
# restores, so a retry runs with --refresh-dependencies to ask the server again.
#
# Failing tests and compile errors are never retried: they fail the build on the first attempt.
set -uo pipefail

max_attempts="${GRADLE_MAX_ATTEMPTS:-3}"
log="$(mktemp)"
trap 'rm -f "$log"' EXIT

for attempt in $(seq 1 "$max_attempts"); do
  extra=()
  [ "$attempt" -gt 1 ] && extra=(--refresh-dependencies)
  if ./gradlew "${extra[@]}" "$@" 2>&1 | tee "$log"; then
    exit 0
  fi
  if grep -qE 'There were failing tests|Compilation error|^e: ' "$log" ||
    ! grep -qE 'Could not (resolve|find|GET|HEAD|download) |Received status code (429|5[0-9][0-9])' "$log"; then
    echo "Gradle failed, not because of a download problem. Not retrying." >&2
    exit 1
  fi
  [ "$attempt" -lt "$max_attempts" ] || break
  echo "::warning::Gradle could not download a dependency (attempt $attempt of $max_attempts), retrying in $((attempt * 30))s."
  sleep $((attempt * 30))
done

echo "Gradle could not download its dependencies after $max_attempts attempts." >&2
exit 1
