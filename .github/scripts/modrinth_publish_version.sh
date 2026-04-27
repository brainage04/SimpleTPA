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

config_file="$MODRINTH_PROJECT_CONFIG"

if [ ! -f "$config_file" ]; then
  config_file="$(mktemp)"
  printf '{}\n' >"$config_file"
fi

mod_id="$(gradle_property mod_id)"
mod_name="$(gradle_property mod_name)"
mod_version="$(gradle_property mod_version)"
minecraft_version="$(gradle_property minecraft_version)"
project_slug="$(jq -r --arg mod_id "$mod_id" '.slug // $mod_id' "$config_file")"
release_tag="${RELEASE_TAG:-$(release_field '.release.tag_name')}"
release_body="${RELEASE_BODY:-$(release_field '.release.body')}"
release_prerelease="${RELEASE_PRERELEASE:-$(release_field '.release.prerelease')}"
project_id="${MODRINTH_PROJECT_ID:-}"

if [ -z "$project_id" ]; then
  project_id="$(resolve_project_id "$project_slug")"
fi

if [ "${release_tag}" != "v${mod_version}" ]; then
  echo "Expected release tag v${mod_version}, got ${release_tag}" >&2
  exit 1
fi

response_file="$(mktemp)"
status="$(modrinth_request GET "/project/${project_slug}/version?include_changelog=false" "$response_file")"

if [ "$status" != "200" ]; then
  echo "Failed to list Modrinth versions for ${project_slug}: HTTP ${status}" >&2
  cat "$response_file" >&2
  exit 1
fi

if jq -e --arg version_number "$mod_version" '.[] | select(.version_number == $version_number)' "$response_file" >/dev/null; then
  echo "Modrinth version ${mod_version} already exists for ${project_slug}; skipping publish."
  exit 0
fi

mapfile -t release_jars < <(find "$MODRINTH_RELEASE_JAR_DIR" -maxdepth 1 -type f -name '*.jar' ! -name '*-dev.jar' ! -name '*-sources.jar' | sort)

if [ "${#release_jars[@]}" -eq 0 ]; then
  echo "No release jar found in ${MODRINTH_RELEASE_JAR_DIR}" >&2
  exit 1
fi

if [ "${#release_jars[@]}" -ne 1 ]; then
  printf 'Expected exactly one release jar, found %s:\n' "${#release_jars[@]}" >&2
  printf '  %s\n' "${release_jars[@]}" >&2
  exit 1
fi

version_type="release"

case "${release_tag,,}" in
  *alpha*)
    version_type="alpha"
    ;;
  *beta* | *rc* | *pre*)
    version_type="beta"
    ;;
  *)
    if [ "$release_prerelease" = "true" ]; then
      version_type="beta"
    fi
    ;;
esac

dependency_entries_file="$(mktemp)"

jq -nc \
  --slurpfile config "$config_file" \
  --slurpfile mod "$MODRINTH_FABRIC_MOD_JSON" \
  '
    ($config[0]) as $config |
    ($mod[0]) as $mod |
    def inferred_dependencies($field; $dependency_type):
      (($mod[$field] // {}) | to_entries | map({
        mod_id: .key,
        dependency_type: $dependency_type,
        override: ($config.dependency_overrides[.key] // {})
      }));
    (
      inferred_dependencies("depends"; "required") +
      inferred_dependencies("recommends"; "optional") +
      inferred_dependencies("suggests"; "optional") +
      inferred_dependencies("conflicts"; "incompatible") +
      inferred_dependencies("breaks"; "incompatible")
    )
    | map(. as $dependency | select((["minecraft", "java", "fabricloader"] | index($dependency.mod_id)) | not))
    | unique_by([.mod_id, (.override.dependency_type // .dependency_type)])
    | .[]
  ' >"$dependency_entries_file"

inferred_dependencies_file="$(mktemp)"

while IFS= read -r dependency_entry; do
  mod_dependency_id="$(jq -r '.mod_id' <<<"$dependency_entry")"
  dependency_type="$(jq -r '.override.dependency_type // .dependency_type' <<<"$dependency_entry")"
  project_id_override="$(jq -r '.override.project_id // empty' <<<"$dependency_entry")"
  project_slug_override="$(jq -r '.override.project_slug // empty' <<<"$dependency_entry")"
  skip_dependency="$(jq -r '.override.skip // false' <<<"$dependency_entry")"

  if [ "$skip_dependency" = "true" ]; then
    continue
  fi

  if [ -n "$project_id_override" ]; then
    resolved_dependency_project_id="$project_id_override"
  else
    candidate_slug_hyphenated="${mod_dependency_id//_/-}"
    candidate_slug_normalized="${candidate_slug_hyphenated//./-}"

    if ! resolved_dependency_project_id="$(
      resolve_project_id_candidates \
        "$project_slug_override" \
        "$mod_dependency_id" \
        "$candidate_slug_hyphenated" \
        "$candidate_slug_normalized"
    )"; then
      echo "Could not resolve Modrinth project for dependency ${mod_dependency_id}." >&2
      echo "Add a dependency_overrides entry in ${MODRINTH_PROJECT_CONFIG}, for example:" >&2
      echo "  \"${mod_dependency_id}\": { \"project_slug\": \"modrinth-slug\" }" >&2
      exit 1
    fi
  fi

  jq -nc \
    --arg project_id "$resolved_dependency_project_id" \
    --arg dependency_type "$dependency_type" \
    '{project_id: $project_id, dependency_type: $dependency_type}' >>"$inferred_dependencies_file"
done <"$dependency_entries_file"

if [ -s "$inferred_dependencies_file" ]; then
  inferred_dependencies="$(jq -s 'unique_by([.project_id, .dependency_type])' "$inferred_dependencies_file")"
else
  inferred_dependencies='[]'
fi

version_payload="$(
  jq -n \
    --arg project_id "$project_id" \
    --arg name "${mod_name} ${mod_version}" \
    --arg version_number "$mod_version" \
    --arg changelog "$release_body" \
    --arg minecraft_version "$minecraft_version" \
    --arg version_type "$version_type" \
    --argjson inferred_dependencies "$inferred_dependencies" \
    --slurpfile config "$config_file" \
    '
      ($config[0]) as $config |
      {
        project_id: $project_id,
        name: $name,
        version_number: $version_number,
        changelog: (if $changelog == "" then null else $changelog end),
        dependencies: (($inferred_dependencies + ($config.version.dependencies // [])) | unique_by([.project_id, .version_id, .file_name, .dependency_type])),
        game_versions: (if ($config.version.game_versions // [] | length) > 0 then $config.version.game_versions else [$minecraft_version] end),
        version_type: $version_type,
        loaders: (if ($config.version.loaders // [] | length) > 0 then $config.version.loaders else ["fabric"] end),
        featured: ($config.version.featured // true),
        file_parts: ["primary"],
        primary_file: "primary",
        status: ($config.version.status // "listed")
      }
    '
)"

response_file="$(mktemp)"
status="$(modrinth_request POST "/version" "$response_file" \
  -F "data=${version_payload};type=application/json" \
  -F "primary=@${release_jars[0]}")"

if [ "$status" != "200" ]; then
  echo "Failed to publish Modrinth version ${mod_version}: HTTP ${status}" >&2
  cat "$response_file" >&2
  exit 1
fi

echo "Published Modrinth version ${mod_version} for project ${project_id}"
