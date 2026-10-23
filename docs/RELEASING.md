# Branches & releases

| Branch | Purpose |
|---|---|
| `dev` | **Default branch** and integration branch. All development lands here through short-lived branches merged with `--no-ff`: `feat/*` for features, `fix/*` for fixes, `docs/*` for documentation. |
| `main` | Releases only. It changes **only** through a pull request from `dev`, and every merge to `main` is a tagged release. |

Commits are small and focused, with no co-author or AI signature lines.

## Cutting a release

1. On the work branch (e.g. `fix/bezel-sensitivity`), bump the version in both modules and commit it as `vX.Y.Z`:
   - `android/app/build.gradle.kts`: `versionName` and `versionCode` (+1)
   - `android/watchface/build.gradle.kts`: `versionName` and `versionCode` (+1)

   Keep both modules on the same version, and never lower a `versionCode`: the watch refuses downgrades.
2. Add a section to `CHANGELOG.md` (`## vX.Y.Z (YYYY-MM-DD)`) and commit it as `Changelog: vX.Y.Z`.
3. Merge into `dev` and push:
   ```sh
   git checkout dev && git merge --no-ff <branch> -m "Merge <branch>: vX.Y.Z" && git push origin dev
   ```
4. Open the release PR from `dev` to `main`:
   ```sh
   gh pr create --base main --head dev --title "Release vX.Y.Z" --body-file <(sed -n '/^## vX.Y.Z/,/^## v/p' CHANGELOG.md | sed '$d')
   ```
5. Merge it with a merge commit, so `main` keeps a clear release boundary:
   ```sh
   gh pr merge <number> --merge --subject "Release vX.Y.Z"
   ```
6. Tag `main` and publish the GitHub release from the changelog section:
   ```sh
   git checkout main && git pull
   git tag -a vX.Y.Z -m "vX.Y.Z" && git push origin vX.Y.Z
   gh release create vX.Y.Z --title "vX.Y.Z" --notes-file <(sed -n '/^## vX.Y.Z/,/^## v/p' CHANGELOG.md | sed '1d;$d')
   git checkout dev
   ```

Versions follow semver: `0.x` while features are still moving. Small fixes and doc-only changes ship as patch releases.

Before releasing app changes, install with `tools/install_watch.sh` and check them on the watch. Doc-only releases skip step 1 (the apps are unchanged, so their versions stay put) and need no rebuild.

### Release artifacts

No APKs are attached to releases. A complete build bundles personally fetched, copyrighted alien artwork, which must not be redistributed. Build and install from source with `tools/install_watch.sh` (see the README).
