# Releasing

The version is set in one place: the `revision` property in the root [`pom.xml`](../pom.xml).
It follows [semantic versioning](https://semver.org):

- **Patch** (1.0.1): bug fixes.
- **Minor** (1.1.0): new features that don't break existing code.
- **Major** (2.0.0): changes that break existing code using the library.

To release:

1. Set `<revision>` to the new version, e.g. `1.0.1`, and commit.
2. Tag that commit with the same version, prefixed with `v`, and push the tag:

   ```sh
   git tag v1.0.1
   git push origin v1.0.1
   ```

The [release workflow](../.github/workflows/release.yml) then:

1. Stops if the tag doesn't match the version in `pom.xml`, before anything is published.
2. Builds everything and runs the tests.
3. Creates a GitHub release. It attaches the runnable `integrity-engine-<version>.jar` and a `SHA256SUMS` file.
4. Publishes the library, `io.github.tejaskm-dev:integrity-engine-core`, to Maven Central, but only if the Central secrets below are set. Without them, this step is skipped.

A version with a suffix, such as `1.1.0-rc1`, becomes a pre-release.

Maven Central does not allow a version to be replaced or deleted once published, so check
that CI is green on the commit before you tag it.

## One-time Maven Central setup

This has already been done for `tejaskm-dev`. It is recorded here in case the account, token
or signing key ever needs replacing. The project is MIT-licensed; Central requires the
license entry that is in `pom.xml`.

### 1. Claim the namespace

1. Sign in to [central.sonatype.com](https://central.sonatype.com) with the **tejaskm-dev** GitHub account.
   Signing in with GitHub verifies the `io.github.tejaskm-dev` namespace. Check that it appears under **Namespaces**.
2. Open your account, choose **Generate User Token**, and copy the username and password it shows.

### 2. Create a signing key

Central requires every file to be signed with a GPG key whose public half is on a public keyserver.

```sh
gpg --full-generate-key                                   # RSA, 4096 bits; set a passphrase
gpg --list-secret-keys --keyid-format long                # note the key id after "rsa4096/"
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
gpg --armor --export-secret-keys <KEY_ID>                 # copy the whole block, including the BEGIN/END lines
```

### 3. Add repository secrets

In GitHub, go to **Settings → Secrets and variables → Actions** and add:

| Secret | Value |
| :--- | :--- |
| `CENTRAL_USERNAME` | Token username from step 1 |
| `CENTRAL_PASSWORD` | Token password from step 1 |
| `GPG_PRIVATE_KEY` | Armored private key from step 2 |
| `GPG_PASSPHRASE` | The key's passphrase |

Once all four are set, every release tag also publishes to Central. A new version usually becomes resolvable within
about 30 minutes, although search results on central.sonatype.com can take longer to update.

## JitPack

[`jitpack.yml`](../jitpack.yml) lets JitPack build any tag on request, with no account or
secrets. Maven Central is the main distribution channel; JitPack is an alternative, for
example for trying an untagged commit:

```xml
<repositories>
  <repository><id>jitpack.io</id><url>https://jitpack.io</url></repository>
</repositories>

<dependency>
  <groupId>com.github.tejaskm-dev.code-plagiarism-detector</groupId>
  <artifactId>integrity-engine-core</artifactId>
  <version>v1.0.0</version>
</dependency>
```

The first request for a tag triggers the build. Its log is at
`https://jitpack.io/#tejaskm-dev/code-plagiarism-detector`.

## Checking a release locally

This builds the exact bundle Central would receive, without uploading or signing anything.
`publish-dry-settings.xml` is any settings file that contains a `central` server entry; the
credentials can be dummy values. The upload is pointed at a closed local port, so it fails
after the bundle is built:

```sh
mvn -s publish-dry-settings.xml deploy -Prelease -DskipTests -Dgpg.skip=true \
    -Dmaven.install.skip=true -DcentralBaseUrl=http://127.0.0.1:9
unzip -l target/central-publishing/central-bundle.zip
```

The bundle should contain the parent POM and core's jar, sources jar, javadoc jar and POM,
each with checksums. It should contain nothing from `integrity-engine-app`.
