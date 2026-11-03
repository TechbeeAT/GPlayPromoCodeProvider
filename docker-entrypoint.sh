#!/bin/sh
# Entry point of the Docker image: starts the server as the unprivileged user `app`.
#
# A volume can be mounted over /data with an owner other than `app` (Fly.io mounts its volumes
# owned by root, so `app` couldn't create the database there). When the container starts as root
# (the default), this script first gives /data to `app`, then drops root and runs the server as
# `app`. Started as another user (`docker run --user ...`), it runs the server unchanged.
set -eu

if [ "$(id -u)" = "0" ]; then
  data_dir="$(dirname "${DATABASE_PATH:-/data/gplaypromocodeprovider.db}")"
  mkdir -p "$data_dir"
  chown -R app:app "$data_dir"
  # exec: the server replaces this script, so it receives Docker's stop signal (SIGTERM) directly.
  exec setpriv --reuid=app --regid=app --init-groups "$@"
fi

exec "$@"
