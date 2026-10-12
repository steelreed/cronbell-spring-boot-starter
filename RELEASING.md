# Releasing

Releases are cut by the maintainer by pushing a version tag. The
[Release workflow](.github/workflows/release.yml) then:

1. **Check the tag**: the tag is `vX.Y.Z` or `vX.Y.Z-alpha.N` / `-beta.N` / `-rc.N`, `pom.xml` has
   the same version, the tagged commit is on `main`, and `CHANGELOG.md` has a dated section for
   it. If any of these fails, nothing is built or uploaded.
2. **Build, sign and upload** on JDK 17: `./mvnw -Prelease deploy` runs every test, builds the
   jar, sources jar and Javadoc jar, signs them with GPG and uploads the bundle to the Central
   Portal. The job waits until Central has validated the bundle; it does not publish it.
3. **Create the GitHub Release** for the tag, with that version's `CHANGELOG.md` section as notes.
   `-alpha`, `-beta` and `-rc` versions are marked as pre-releases.

Publishing on Maven Central is the one manual step (`autoPublish` is `false`), so a bad release
can still be dropped before anyone can download it.

## One-time setup: secrets

Add these as repository secrets, or as organization secrets shared with this repository. They are
the same names as QueryFence uses, so the organization secrets can be reused.

| Secret | What it holds |
|---|---|
| `CENTRAL_USERNAME` | Central Portal user token, username part |
| `CENTRAL_PASSWORD` | Central Portal user token, password part |
| `GPG_PRIVATE_KEY` | ASCII-armoured private key that signs the artifacts |
| `GPG_PASSPHRASE` | Passphrase of that key |

The `com.steelreed` namespace is already verified on the Central Portal. The public half of the
GPG key must be on a public key server (for example `keys.openpgp.org`): Central checks the
signatures against it.

## Pre-release checklist

- [ ] CI is green on `main` for every Java and Spring Boot line of the matrix.
- [ ] `./mvnw -B verify`, `-Pboot33` and `-Pboot4` pass locally.
- [ ] `./mvnw -B -Prelease -DskipTests -Dgpg.skip package` builds the sources and Javadoc jars
      (`target/*-sources.jar`, `target/*-javadoc.jar`) without errors.
- [ ] The README's dependency snippet, compatibility table and status note match the release.
- [ ] `CHANGELOG.md` lists every change users will notice under the new version.
- [ ] No dependency or plugin is a milestone, RC or snapshot.

## Cutting a release

Example for `0.1.0`; use the same steps for a pre-release such as `0.1.0-rc.1`.

1. On a branch, set the version and date the changelog:
   ```bash
   ./mvnw -B versions:set -DnewVersion=0.1.0 -DgenerateBackupPoms=false
   ```
   In `CHANGELOG.md`, rename `## [Unreleased]` to `## [0.1.0] - YYYY-MM-DD` (the release date) and
   add a new empty `## [Unreleased]` above it. Update the version in the README snippet.
2. Commit (`git commit -s -m "chore: release 0.1.0"`), open a pull request and merge it once CI
   passes.
3. Tag the merge commit on `main` and push the tag:
   ```bash
   git switch main && git pull
   git tag -s v0.1.0 -m "Cronbell Spring Boot starter 0.1.0"   # or -a if you do not sign tags
   git push origin v0.1.0
   ```
4. Watch the run under **Actions → Release**. If **Check the tag** fails, fix `main`, delete the tag
   (`git push --delete origin v0.1.0 && git tag -d v0.1.0`) and tag again.
5. When the run is green, open <https://central.sonatype.com/publishing/deployments>, check the
   deployment (jar, `-sources.jar`, `-javadoc.jar`, `.pom`, each with an `.asc` signature) and
   click **Publish**. To abandon the release instead, click **Drop**, then delete the GitHub
   Release and the tag.
6. Verify. Publishing usually takes 10 to 30 minutes; search.maven.org can lag longer.
   - <https://repo1.maven.org/maven2/com/steelreed/cronbell-spring-boot-starter/0.1.0/> lists
     the files.
   - <https://central.sonatype.com/artifact/com.steelreed/cronbell-spring-boot-starter> shows the
     version.
   - A fresh project resolves it: add the dependency from the README and run `./mvnw verify`.
   - The GitHub Release has the right notes and pre-release flag.
7. Back on `main`, set the next development version
   (`./mvnw -B versions:set -DnewVersion=0.1.1-SNAPSHOT -DgenerateBackupPoms=false`) in a pull
   request.

## If the workflow fails after the upload

- **Upload or validation failed**: nothing is public. Drop the deployment in the Central Portal if
  one is listed, fix the cause on `main`, delete and push the tag again.
- **Only the GitHub Release job failed**: re-run that job from the Actions page. It updates the
  release if it already exists.
- A version that has been published on Maven Central cannot be changed or deleted. Release a new
  patch version instead.
