# Create a new release

1. Update `mod_version` in `gradle.properties`.
2. Commit that change.
3. Push that commit.
4. Create a matching annotated git tag in the form `vX.Y.Z` so the tag message becomes the GitHub release notes.
5. For a short release note, run `git tag -a v1.0.1 -m "Summarise the release here"`.
6. For longer release notes, put them in a file and run `git tag -a v1.0.1 -F RELEASE_NOTES.md`.
7. Push the tag with `git push origin v1.0.1`.

The release workflow reads the annotated tag message and uses it as the GitHub release body.
If the tag has no annotation text, GitHub auto-generated release notes are used as a fallback.
GitHub Actions checks out tag pushes in a way that can obscure annotated tag contents, so the workflow fetches the remote tag object before reading the notes.

If `MODRINTH_TOKEN` is configured, the same `release.yml` workflow runs a second job after the GitHub release is created and publishes the same build to Modrinth.
That job reuses the same tag notes as the Modrinth version changelog.
