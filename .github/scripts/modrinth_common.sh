#!/usr/bin/env bash

set -euo pipefail

MODRINTH_API="${MODRINTH_API:-https://api.modrinth.com/v2}"
MODRINTH_PROJECT_CONFIG="${MODRINTH_PROJECT_CONFIG:-.modrinth/project.json}"
MODRINTH_FABRIC_MOD_JSON="${MODRINTH_FABRIC_MOD_JSON:-build/resources/main/fabric.mod.json}"
MODRINTH_SOURCE_FABRIC_MOD_JSON="${MODRINTH_SOURCE_FABRIC_MOD_JSON:-src/main/resources/fabric.mod.json}"
MODRINTH_PROJECT_BODY="${MODRINTH_PROJECT_BODY:-README.md}"
MODRINTH_RELEASE_JAR_DIR="${MODRINTH_RELEASE_JAR_DIR:-build/libs}"
MODRINTH_DISCORD_URL="${MODRINTH_DISCORD_URL:-https://discord.gg/N4zfhBx8Fm}"
MODRINTH_USER_AGENT="${MODRINTH_USER_AGENT:-${GITHUB_REPOSITORY:-unknown}/github-actions modrinth-publisher}"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_env() {
  local name

  for name in "$@"; do
    if [ -z "${!name:-}" ]; then
      echo "Missing required environment variable: $name" >&2
      exit 1
    fi
  done
}

gradle_property() {
  local key="$1"
  awk -F= -v key="$key" '$1 == key { print substr($0, index($0, $2)); exit }' gradle.properties
}

release_field() {
  local jq_filter="$1"

  if [ -z "${GITHUB_EVENT_PATH:-}" ] || [ ! -f "${GITHUB_EVENT_PATH}" ]; then
    echo ""
    return 0
  fi

  jq -r "$jq_filter // empty" "$GITHUB_EVENT_PATH"
}

modrinth_request() {
  local method="$1"
  local path="$2"
  local output_file="$3"
  shift 3

  curl -sS -o "$output_file" -w '%{http_code}' \
    -X "$method" \
    -H "Authorization: ${MODRINTH_TOKEN}" \
    -H "User-Agent: ${MODRINTH_USER_AGENT}" \
    "$@" \
    "${MODRINTH_API}${path}"
}

resolve_project_id() {
  local slug="$1"
  local response_file status

  response_file="$(mktemp)"
  status="$(modrinth_request GET "/project/${slug}" "$response_file")"

  case "$status" in
    200)
      jq -r '.id' "$response_file"
      ;;
    404)
      return 1
      ;;
    *)
      echo "Unexpected Modrinth response while fetching project ${slug}: HTTP ${status}" >&2
      cat "$response_file" >&2
      exit 1
      ;;
  esac
}

resolve_project_id_candidates() {
  local candidate

  for candidate in "$@"; do
    if [ -n "$candidate" ] && resolve_project_id "$candidate"; then
      return 0
    fi
  done

  return 1
}
