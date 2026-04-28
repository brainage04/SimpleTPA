# Modrinth publishing

This template includes optional Modrinth publishing as part of `.github/workflows/release.yml`.

After the GitHub release is created, a second job in the same workflow does two things:

1. Creates the Modrinth project if a project with slug `mod_id` does not already exist.
2. Uploads the built release jar as a Modrinth version if that `mod_version` has not already been uploaded.

The Modrinth job is skipped unless the repository has a `MODRINTH_TOKEN` secret configured.

## Required secret

Create a Modrinth personal access token and add it as a repository secret to the GitHub repository as `MODRINTH_TOKEN`.

Minimum useful scopes:

- `PROJECT_CREATE`
- `PROJECT_WRITE`
- `VERSION_CREATE`

The release workflow uses the Modrinth API directly:

- Project creation: `POST /project`
- Version upload: `POST /version`

## Project metadata

The workflow reads:

- `src/main/resources/fabric.mod.json` for the project slug, title, description, contact links, licence, and side support inference
- `README.md` for the long project description
- `.modrinth/project.json` for optional Modrinth-specific overrides
- `gradle.properties` for `mod_version` and `minecraft_version`

Defaults:

- The Modrinth slug is `fabric.mod.json.id`
- The project is created as `draft`
- The GitHub repo URL is used when `fabric.mod.json.contact.sources` is absent
- The GitHub issues URL is used when `fabric.mod.json.contact.issues` is absent
- The GitHub wiki URL is used when `fabric.mod.json.contact.wiki` is absent
- The licence link points at `LICENSE` by default
- `fabric` is used as the default loader for versions when no override is supplied
- `utility` is used as the default project category when no override is supplied
- `discord_url` is always set to `https://discord.gg/N4zfhBx8Fm`
- The workflow syncs `issues_url`, `source_url`, `wiki_url`, and `license_url` on every release so existing Modrinth projects stay aligned with the repository

In practice, `.modrinth/project.json` can be kept very small. The template only needs it when you want to override defaults such as:

- `slug`
- `categories`
- `additional_categories`
- `wiki_url`
- `license_url`
- `dependency_overrides`

Valid values for `categories` and `additional_categories` are as follows:

- `adventure`
- `cursed`
- `decoration`
- `economy`
- `equipment`
- `food`
- `game-mechanics`
- `library`
- `magic`
- `management`
- `minigame`
- `mobs`
- `optimization`
- `social`
- `storage`
- `technology`
- `transportation`
- `utility`
- `worldgen`

`additional_categories` uses the same values as `categories`; the difference is that they are searchable but not shown as primary display categories.

If you do not need any overrides, you can remove `.modrinth/project.json` entirely and the workflow will fall back to defaults.

Modrinth categories are separate from loaders. Do not use `fabric` in `categories` or `additional_categories`; keep Fabric in `version.loaders` if you need to override loaders.

## Version dependencies

Version dependencies are inferred from `src/main/resources/fabric.mod.json`:

- `depends` becomes Modrinth `required`
- `recommends` and `suggests` become Modrinth `optional`
- `conflicts` and `breaks` become Modrinth `incompatible`
- `minecraft`, `java`, and `fabricloader` are ignored

The workflow first tries the Fabric mod ID as a Modrinth slug, then simple normalisations such as replacing `_` with `-`.

If a dependency uses a different Modrinth slug, add an override in `.modrinth/project.json`:

```json
{
  "dependency_overrides": {
    "fzzy_config": {
      "project_slug": "fzzy-config"
    }
  }
}
```

You can also override the dependency type, provide a project ID directly, or skip a dependency entirely:

```json
{
  "dependency_overrides": {
    "some_mod": {
      "project_id": "AABBCCDD",
      "dependency_type": "optional"
    },
    "local_only_mod": {
      "skip": true
    }
  }
}
```

Manual extra version dependencies are still supported with `version.dependencies` if you need to append entries that do not come from `fabric.mod.json`.

## Side support defaults

Side support is inferred from `fabric.mod.json`:

- `environment=client`: `client_side=required`, `server_side=unsupported`
- `environment=server`: `client_side=unsupported`, `server_side=required`
- `environment=*` with a client entrypoint: `client_side=required`, `server_side=required`
- `environment=*` without a client entrypoint: `client_side=unsupported`, `server_side=required`

You can still override the inferred values in `.modrinth/project.json` if needed.

## Release notes

The Modrinth changelog is taken from the same annotated tag notes that are used for the GitHub release body. See [RELEASE.md](RELEASE.md) for more info.

The release workflow fetches the remote tag object before reading notes so annotated tag messages are preserved in GitHub Actions checkouts.

## Notes

- The workflow uploads the main release jar from `build/libs` and ignores `*-dev.jar` and `*-sources.jar`.
- If the Modrinth project already exists, it is reused instead of recreated.
- If the Modrinth version already exists for the current `mod_version`, publishing is skipped.
- The Modrinth scripts can also be run locally for validation. When they run outside GitHub Actions, `GITHUB_ENV` is optional and no step output file is written.
