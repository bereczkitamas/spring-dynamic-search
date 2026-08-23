---
name: maven-central-release
description: >-
  Step-by-step workflow and runbook for releasing and publishing spring-dynamic-search
  to Maven Central via Sonatype Central Portal and GitHub Actions.
---

# Maven Central Release Workflow for `spring-dynamic-search`

This skill defines the complete procedure for releasing new versions of `spring-dynamic-search` to Maven Central.

---

## 1. Prerequisites & Environment

- **JDK**: GraalVM JDK 25 or JDK 21+ (`$env:JAVA_HOME = 'C:\Users\btama\.jdks\graalvm-jdk-25'`).
- **GPG Signing Configuration**: GnuPG 2.4+ key configured without `use-keyboxd` in `~/.gnupg/common.conf`.
- **CI/CD Pipeline**: `.github/workflows/release.yml` triggers on any `v*` tag push and publishes to Sonatype Central Portal via `central-publishing-maven-plugin:0.11.0`.

---

## 2. Release Procedure (Step-by-Step)

### Step 1: Determine Next SemVer Version
- Follow Semantic Versioning 2.0.0:
  - **`0.Y.0`**: Breaking API changes during initial development.
  - **`0.0.Z`**: Non-breaking features and bug fixes.
  - **`1.0.0`**: First stable production release.

### Step 2: Update Version in `pom.xml`
- Update `<version>X.Y.Z</version>` in the project `pom.xml` (or root + submodule POMs in multi-module setups).

### Step 3: Run Full Clean Verification & Packaging
- Execute full test suite and verify binary, source, and Javadoc JAR generation:
  ```powershell
  $env:JAVA_HOME = 'C:\Users\btama\.jdks\graalvm-jdk-25'
  .\mvnw.cmd clean test
  .\mvnw.cmd clean package
  ```
- Ensure all tests pass with zero failures and artifacts are properly produced in `target/`.

### Step 4: Commit and Push Version Changes
- Ask user for confirmation, then commit and push to `main`:
  ```powershell
  git add pom.xml
  git commit -m "chore(release): bump version to X.Y.Z"
  git push origin main
  ```

### Step 5: Create and Push Git Release Tag
- Create annotated or standard git tag matching the version (`vX.Y.Z`):
  ```powershell
  git tag vX.Y.Z
  git push origin vX.Y.Z
  ```

### Step 6: Monitor GitHub Actions & Central Publishing
- Tag push triggers GitHub Actions workflow: `https://github.com/bereczkitamas/spring-dynamic-search/actions`
- The workflow:
  1. Checks out code with Java 25.
  2. Imports GPG private signing key from GitHub Secrets.
  3. Executes `mvn --batch-mode clean deploy -P release`.
  4. Waits for deployment publication on Sonatype Central Portal: `https://central.sonatype.com/publishing/deployments`
  5. Creates a GitHub Release with generated release notes.
