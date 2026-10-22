# Branches & releases

| Branch | Purpose |
|---|---|
| `dev` | Integration branch. All development lands here, directly in small commits or via short-lived `feature/*` branches merged into `dev`. |
| `main` | Releases only. It changes **only** through a pull request from `dev`, and every merge to `main` is a tagged release. |

## Cutting a release

1. On `dev`, bump the version in both modules:
   - `android/app/build.gradle.kts`: `versionName` and `versionCode`
   - `android/watchface/build.gradle.kts`: `versionName` and `versionCode`
2. Add a section to `CHANGELOG.md` (`## vX.Y.Z (YYYY-MM-DD)`) and commit both on `dev`.
3. Open the release PR:
   ```sh
   gh pr create --base main --head dev --title "Release vX.Y.Z" --body-file <(sed -n '/^## vX.Y.Z/,/^## v/p' CHANGELOG.md | sed '$d')
   ```
4. Merge it with a merge commit, so `main` keeps a clear release boundary:
   ```sh
   gh pr merge <number> --merge
   ```
5. Tag `main` and publish the GitHub release from the changelog section:
   ```sh
   git checkout main && git pull
   git tag -a vX.Y.Z -m "vX.Y.Z" && git push origin vX.Y.Z
   gh release create vX.Y.Z --title "vX.Y.Z" --notes-file <(sed -n '/^## vX.Y.Z/,/^## v/p' CHANGELOG.md | sed '1d;$d')
   git checkout dev
   ```

Versions follow semver: `0.x` while features are still moving.

### Release artifacts

No APKs are attached to releases. A complete build bundles personally fetched, copyrighted alien artwork, which must not be redistributed. Build and install from source with `tools/install_watch.sh` (see the README).
