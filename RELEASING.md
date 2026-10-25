# Releasing autoFrameX

autoFrameX is consumed by other projects as Maven dependencies (`autoFrameX:autoframex-core`,
`autoframex-selenium`, and so on). There is no remote Maven repository configured in the poms
(`distributionManagement` is absent), so today a "release" means: a tagged commit whose modules every
consumer builds and installs into its own `~/.m2` (or into the team's repository once one exists).

All modules share the parent's version. Use semantic versioning: a change that makes a suite that used to
pass fail (a stricter check, a changed default) is at least a **minor** version, and goes under
*behaviour change* in [CHANGELOG.md](CHANGELOG.md).

## Before you start

- The working tree is clean and on the branch you are releasing (`git status`).
- `CHANGELOG.md`: move everything under *Unreleased* to a new `## <version> - <date>` heading, and leave an
  empty *Unreleased* above it.
- `mvn -B clean install` passes on a machine with JDK 17 or newer and Maven 3.8.8 or newer. The build
  enforces both, and fails if any plugin version is unpinned.
- Security: `mvn dependency-check:check` has no finding at CVSS 7 or above that you have not decided to
  accept (the weekly workflow only fails at 9).
- Nobody is relying on a credential that is in git history (README, *Secrets*): releasing does not make it
  safer, but a release is a reasonable moment to have rotated it.

## Cut the release

1. Set the release version in every module (parent and children together):
   ```bash
   mvn -B versions:set -DnewVersion=1.0.0 -DgenerateBackupPoms=false
   ```
   The child poms take their version from the parent; check with `git diff --stat` that only the
   `pom.xml` files changed.
2. Build and test the release version exactly as a consumer would:
   ```bash
   mvn -B clean install
   ```
3. Commit and tag:
   ```bash
   git commit -am "Release 1.0.0"
   git tag -a v1.0.0 -m "autoFrameX 1.0.0"
   ```
4. Publish. With a repository configured, `mvn -B deploy` publishes the jars and the source jars
   (`maven-source-plugin` already attaches them). Without one, consumers build the tag themselves:
   ```bash
   git clone --branch v1.0.0 https://github.com/Rajeshluffy/autoFrameX.git
   cd autoFrameX && mvn -B clean install -DskipTests
   ```
5. Move to the next development version and commit:
   ```bash
   mvn -B versions:set -DnewVersion=1.0.1-SNAPSHOT -DgenerateBackupPoms=false
   git commit -am "Start 1.0.1-SNAPSHOT"
   ```
6. Push the commits and the tag (`git push origin <branch> v1.0.0`). Pushing is a separate, deliberate step:
   do it once, after you have looked at the result of steps 1 to 5.

## Consumers

A consuming project pins the released version in its own pom, for every autoFrameX module it uses:

```xml
<dependency>
    <groupId>autoFrameX</groupId>
    <artifactId>autoframex-selenium</artifactId>
    <version>1.0.0</version>
</dependency>
```

Until the first numbered release, consumers depend on `0.0.1-SNAPSHOT` from their local `~/.m2`, and have to
re-run `mvn clean install` in this repository after every pull or the module they use is stale.
After a release, read the *behaviour change* entries in the changelog for every version between the one a
project used and the one it moves to, then run the project's own suite.

## If a release is wrong

Do not move or re-use a published tag. Fix forward with a new patch version and say what was wrong under that
version in the changelog.
