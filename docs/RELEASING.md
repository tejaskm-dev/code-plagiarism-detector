# Releasing

A release is one tag push:

```sh
git tag v0.1.0
git push origin v0.1.0
```

The [release workflow](../.github/workflows/release.yml) then:

1. Builds everything and runs the tests, with the version set from the tag.
2. Creates a GitHub release. It attaches the runnable `integrity-engine-<version>.jar` and a `SHA256SUMS` file.
3. Publishes the library, `io.github.tejaskm-dev:integrity-engine-core`, to Maven Central, but only if the Central secrets below are set. Without them, this step is skipped.

A tag with a suffix, such as `v0.2.0-rc1`, becomes a pre-release.

Maven Central does not allow a version to be replaced or deleted once published, so check
that CI is green on the commit before you tag it.

## Before the first Maven Central release

### 1. Add a license

Central rejects any POM without a `<licenses>` section. Choose a license, commit it as
`LICENSE`, and add the matching block to the root `pom.xml`. For example, for MIT:

```xml
<licenses>
  <license>
    <name>MIT License</name>
    <url>https://opensource.org/license/mit</url>
  </license>
</licenses>
```

### 2. Claim the namespace

1. Sign in to [central.sonatype.com](https://central.sonatype.com) with the **tejaskm-dev** GitHub account.
   Signing in with GitHub verifies the `io.github.tejaskm-dev` namespace. Check that it appears under **Namespaces**.
2. Open your account, choose **Generate User Token**, and copy the username and password it shows.

### 3. Create a signing key

Central requires every file to be signed with a GPG key whose public half is on a public keyserver.

```sh
gpg --full-generate-key                                   # RSA, 4096 bits; set a passphrase
gpg --list-secret-keys --keyid-format long                # note the key id after "rsa4096/"
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
gpg --armor --export-secret-keys <KEY_ID>                 # copy the whole block, including the BEGIN/END lines
```

### 4. Add repository secrets

In GitHub, go to **Settings → Secrets and variables → Actions** and add:

| Secret | Value |
| :--- | :--- |
| `CENTRAL_USERNAME` | Token username from step 2 |
| `CENTRAL_PASSWORD` | Token password from step 2 |
| `GPG_PRIVATE_KEY` | Armored private key from step 3 |
| `GPG_PASSPHRASE` | The key's passphrase |

The next tag pushed publishes to Central. A new version usually becomes resolvable within
about 30 minutes, although search results on central.sonatype.com can take longer to update.

## JitPack

[`jitpack.yml`](../jitpack.yml) lets JitPack build any tag on request, with no account or
secrets. That makes it a stopgap until Central is set up:

```xml
<repositories>
  <repository><id>jitpack.io</id><url>https://jitpack.io</url></repository>
</repositories>

<dependency>
  <groupId>com.github.tejaskm-dev.code-plagiarism-detector</groupId>
  <artifactId>integrity-engine-core</artifactId>
  <version>v0.1.0</version>
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
    -Dmaven.install.skip=true -Drevision=0.1.0 -DcentralBaseUrl=http://127.0.0.1:9
unzip -l target/central-publishing/central-bundle.zip
```

The bundle should contain the parent POM and core's jar, sources jar, javadoc jar and POM,
each with checksums. It should contain nothing from `integrity-engine-app`.
