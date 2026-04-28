#!/usr/bin/env bash

set -euo pipefail

# shellcheck source=.github/scripts/modrinth_common.sh
. "$(dirname "$0")/modrinth_common.sh"

require_command curl
require_command jq
require_env GITHUB_REPOSITORY MODRINTH_TOKEN

if [ ! -f "$MODRINTH_FABRIC_MOD_JSON" ]; then
  echo "Missing Fabric mod metadata: $MODRINTH_FABRIC_MOD_JSON" >&2
  exit 1
fi

if [ ! -f "$MODRINTH_PROJECT_BODY" ]; then
  echo "Missing Modrinth project body: $MODRINTH_PROJECT_BODY" >&2
  exit 1
fi

config_file="$MODRINTH_PROJECT_CONFIG"

if [ ! -f "$config_file" ]; then
  config_file="$(mktemp)"
  printf '{}\n' >"$config_file"
fi

mod_id="$(jq -r '.id' "$MODRINTH_FABRIC_MOD_JSON")"
project_slug="$(jq -r --arg mod_id "$mod_id" '.slug // $mod_id' "$config_file")"
repo_url="https://github.com/${GITHUB_REPOSITORY}"
issues_url="${repo_url}/issues"
default_wiki_url="${repo_url}/wiki"
license_path="LICENSE"
icon_rel_path="$(jq -r '.icon // empty' "$MODRINTH_FABRIC_MOD_JSON")"
icon_path=""

for candidate in LICENSE LICENSE.md; do
  if [ -f "$candidate" ]; then
    license_path="$candidate"
    break
  fi
done

default_license_url="${repo_url}/blob/HEAD/${license_path}"

if [ -n "$icon_rel_path" ]; then
  icon_path="src/main/resources/${icon_rel_path}"
fi

# shellcheck disable=SC2016
inferred_support_jq='
  def inferred_support($mod):
    if ($mod.environment // "*") == "client" then
      { client_side: "required", server_side: "unsupported" }
    elif ($mod.environment // "*") == "server" then
      { client_side: "unsupported", server_side: "required" }
    elif (($mod.entrypoints.client // []) | length) > 0 then
      { client_side: "required", server_side: "required" }
    else
      { client_side: "unsupported", server_side: "required" }
    end;
'

project_metadata_payload="$(
  jq -n \
    --arg repo_url "$repo_url" \
    --arg issues_url "$issues_url" \
    --arg default_wiki_url "$default_wiki_url" \
    --arg default_license_url "$default_license_url" \
    --slurpfile config "$config_file" \
    --slurpfile mod "$MODRINTH_FABRIC_MOD_JSON" \
    "$inferred_support_jq"'
      ($config[0]) as $config |
      ($mod[0]) as $mod |
      inferred_support($mod) as $support |
      {
        issues_url: ($config.issues_url // $mod.contact.issues // $issues_url),
        source_url: ($config.source_url // $mod.contact.sources // $mod.contact.homepage // $repo_url),
        wiki_url: ($config.wiki_url // $mod.contact.wiki // $default_wiki_url),
        license_url: ($config.license_url // $default_license_url),
        client_side: ($config.client_side // $support.client_side),
        server_side: ($config.server_side // $support.server_side)
      }
      | with_entries(select(.value != null))
    '
)"

if project_id="$(resolve_project_id "$project_slug")"; then
  echo "Modrinth project already exists: ${project_id}"
else
  project_payload="$(
    jq -n \
      --arg repo_url "$repo_url" \
      --arg issues_url "$issues_url" \
      --arg default_wiki_url "$default_wiki_url" \
      --arg default_license_url "$default_license_url" \
      --arg discord_url "$MODRINTH_DISCORD_URL" \
      --rawfile body "$MODRINTH_PROJECT_BODY" \
      --slurpfile config "$config_file" \
      --slurpfile mod "$MODRINTH_FABRIC_MOD_JSON" \
      "$inferred_support_jq"'
        ($config[0]) as $config |
        ($mod[0]) as $mod |
        inferred_support($mod) as $support |
        {
          slug: ($config.slug // $mod.id),
          title: ($config.title // $mod.name),
          description: ($config.description // $mod.description),
          body: $body,
          categories: (if ($config.categories // [] | length) > 0 then $config.categories else ["utility"] end),
          client_side: ($config.client_side // $support.client_side),
          server_side: ($config.server_side // $support.server_side),
          status: ($config.status // "draft"),
          requested_status: ($config.requested_status // null),
          additional_categories: ($config.additional_categories // []),
          issues_url: ($config.issues_url // $mod.contact.issues // $issues_url),
          source_url: ($config.source_url // $mod.contact.sources // $mod.contact.homepage // $repo_url),
          wiki_url: ($config.wiki_url // $mod.contact.wiki // $default_wiki_url),
          discord_url: $discord_url,
          donation_urls: ($config.donation_urls // []),
          license_id: ($config.license_id // $mod.license),
          license_url: ($config.license_url // $default_license_url),
          project_type: ($config.project_type // "mod"),
          initial_versions: [],
          is_draft: true
        }
        | with_entries(select(.value != null))
      '
  )"

  response_file="$(mktemp)"
  curl_args=(
    -F "data=${project_payload};type=application/json"
  )

  if [ -n "$icon_path" ] && [ -f "$icon_path" ]; then
    curl_args+=(-F "icon=@${icon_path}")
  fi

  status="$(modrinth_request POST "/project" "$response_file" "${curl_args[@]}")"

  if [ "$status" != "200" ]; then
    echo "Failed to create Modrinth project for slug ${project_slug}: HTTP ${status}" >&2
    cat "$response_file" >&2
    exit 1
  fi

  project_id="$(jq -r '.id' "$response_file")"
  echo "Created Modrinth project: ${project_id}"
fi

response_file="$(mktemp)"
status="$(modrinth_request PATCH "/project/${project_slug}" "$response_file" \
  -H "Content-Type: application/json" \
  --data-binary "$project_metadata_payload")"

if [ "$status" != "204" ]; then
  echo "Failed to sync Modrinth project metadata for ${project_slug}: HTTP ${status}" >&2
  cat "$response_file" >&2
  exit 1
fi

if [ -n "${GITHUB_ENV:-}" ]; then
  echo "MODRINTH_PROJECT_ID=${project_id}" >>"$GITHUB_ENV"
fi
