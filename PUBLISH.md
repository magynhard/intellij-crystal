# Publishing to JetBrains Marketplace

## Prerequisites

- JetBrains account (same as your IDE license)
- Marketplace token from [Profile → Tokens](https://plugins.jetbrains.com/author/me/tokens)
- Signing certificate (required since 2024)

## One-Time Setup: Signing Certificate

### 1. Generate a private key

```bash
openssl genpkey -algorithm RSA -out private.pem -pkeyopt rsa_keygen_bits:4096
```

### 2. Generate a certificate signing request (CSR)

```bash
openssl req -new -key private.pem -out request.csr -subj "/CN=magynhard"
```

### 3. Upload CSR to JetBrains

Go to [plugins.jetbrains.com/author/me/certificates](https://plugins.jetbrains.com/author/me/certificates) and upload `request.csr`. You will receive a `chain.crt` file back.

### 4. Store credentials securely

Keep `private.pem` and `chain.crt` in a safe location outside the repository. Never commit them.

## Environment Variables

Set these before running the publish tasks:

```bash
export CERTIFICATE_CHAIN=$(cat /path/to/chain.crt)
export PRIVATE_KEY=$(cat /path/to/private.pem)
export PRIVATE_KEY_PASSWORD=""        # empty if no passphrase was set
export PUBLISH_TOKEN="perm:your-marketplace-token"
```

## Publishing Workflow

### 1. Update the version

Edit `gradle.properties`:

```properties
version = 0.2.0
```

### 2. Update change notes

Edit `src/main/resources/META-INF/plugin.xml` and add a new section to `<change-notes>`:

```xml
<h3>0.2.0</h3>
<ul>
  <li>New feature X</li>
  <li>Fixed bug Y</li>
</ul>
```

### 3. Build

```bash
./gradlew clean build
```

The plugin ZIP is generated at `build/distributions/intellij-crystal-<version>.zip`.

### 4. Sign

```bash
./gradlew signPlugin
```

The signed ZIP is at `build/distributions/intellij-crystal-<version>-signed.zip`.

### 5. Publish

```bash
./gradlew publishPlugin
```

This uploads the signed plugin directly to the JetBrains Marketplace.

### 6. Wait for review

- First upload: 1–3 business days for manual review
- Subsequent updates: usually approved within hours

## Manual Upload (Alternative)

If you prefer not to use the Gradle task:

1. Run `./gradlew signPlugin`
2. Go to [plugins.jetbrains.com/plugin/add](https://plugins.jetbrains.com/plugin/add)
3. Upload the signed ZIP from `build/distributions/`
4. Fill in the required metadata and submit

## Version Scheme

Follow [Semantic Versioning](https://semver.org/):

- `0.x.y` — pre-1.0 development (breaking changes allowed)
- `1.0.0` — first stable release
- Bump `patch` for bug fixes, `minor` for new features, `major` for breaking changes

## Checklist Before Publishing

- [ ] Version bumped in `gradle.properties`
- [ ] Change notes updated in `plugin.xml`
- [ ] `./gradlew build` succeeds
- [ ] Plugin tested locally (`./gradlew runIde`)
- [ ] All environment variables set
- [ ] `./gradlew signPlugin` succeeds
- [ ] Git tagged: `git tag v0.x.y && git push --tags`
