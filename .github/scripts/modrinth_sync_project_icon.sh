#!/usr/bin/env bash

set -euo pipefail

# shellcheck source=.github/scripts/modrinth_common.sh
. "$(dirname "$0")/modrinth_common.sh"

require_command curl
require_command jq
require_env GITHUB_REPOSITORY MODRINTH_TOKEN

config_file="$MODRINTH_PROJECT_CONFIG"

if [ ! -f "$config_file" ]; then
  config_file="$(mktemp)"
  printf '{}\n' >"$config_file"
fi

if [ ! -f "$MODRINTH_FABRIC_MOD_JSON" ]; then
  echo "Missing Fabric mod metadata: $MODRINTH_FABRIC_MOD_JSON" >&2
  exit 1
fi

mod_id="$(jq -r '.id' "$MODRINTH_FABRIC_MOD_JSON")"
project_slug="$(jq -r --arg mod_id "$mod_id" '.slug // $mod_id' "$config_file")"
icon_rel_path="$(jq -r '.icon // empty' "$MODRINTH_FABRIC_MOD_JSON")"

if [ -z "$icon_rel_path" ]; then
  echo "No icon declared in $MODRINTH_FABRIC_MOD_JSON; skipping Modrinth project icon sync."
  exit 0
fi

icon_path=""

if [ -n "${MODRINTH_ICON_SOURCE:-}" ]; then
  icon_path="$MODRINTH_ICON_SOURCE"
elif [ -f "src/main/resources/${icon_rel_path}" ]; then
  icon_path="src/main/resources/${icon_rel_path}"
elif [ -f "build/resources/main/${icon_rel_path}" ]; then
  icon_path="build/resources/main/${icon_rel_path}"
fi

if [ -z "$icon_path" ] || [ ! -f "$icon_path" ]; then
  echo "Could not find icon declared by $MODRINTH_FABRIC_MOD_JSON: $icon_rel_path" >&2
  exit 1
fi

icon_ext="${icon_path##*.}"
icon_ext="${icon_ext,,}"
upload_icon_path="$icon_path"
content_type="image/${icon_ext}"
max_icon_bytes=262144

case "$icon_ext" in
  jpg)
    content_type="image/jpeg"
    ;;
  svgz)
    content_type="image/svgz"
    ;;
  png | jpeg | bmp | gif | webp | svg | rgb) ;;
  *)
    echo "Unsupported Modrinth icon extension: $icon_ext" >&2
    exit 1
    ;;
esac

icon_size="$(wc -c <"$upload_icon_path")"

if [ "$icon_ext" = "png" ] && [ "$icon_size" -gt "$max_icon_bytes" ]; then
  require_command pngquant

  optimized_icon_path="$(mktemp --suffix=.png)"
  pngquant --force --output "$optimized_icon_path" --speed 1 256 "$upload_icon_path"
  upload_icon_path="$optimized_icon_path"
  icon_size="$(wc -c <"$upload_icon_path")"
fi

if [ "$icon_size" -gt "$max_icon_bytes" ]; then
  echo "Modrinth project icon is too large after optimization: ${icon_size} bytes, max ${max_icon_bytes} bytes." >&2
  exit 1
fi

if ! project_id="$(resolve_project_id "$project_slug")"; then
  echo "Could not resolve Modrinth project for slug ${project_slug}." >&2
  exit 1
fi

response_file="$(mktemp)"
status="$(modrinth_request PATCH "/project/${project_id}/icon?ext=${icon_ext}" "$response_file" \
  -H "Content-Type: ${content_type}" \
  --data-binary "@${upload_icon_path}")"

if [ "$status" != "204" ]; then
  echo "Failed to sync Modrinth project icon for ${project_slug}: HTTP ${status}" >&2
  cat "$response_file" >&2
  exit 1
fi

echo "Synced Modrinth project icon for ${project_slug} from ${icon_path}"
