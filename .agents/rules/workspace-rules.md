---
description: Workspace rules for Java build environment, Spring Boot library packaging, dependency management, skill placement, and SemVer conventions
globs: ["**/*.java", "pom.xml", "**/mvnw*", ".agents/**/*"]
always_on: true
---

# Workspace Rules & Build Guardrails

## 1. Build Environment
- When running Maven commands (`mvnw` / `mvn`), ensure `JAVA_HOME` is set to the GraalVM JDK 25 installation:
  `$env:JAVA_HOME = 'C:\Users\btama\.jdks\graalvm-jdk-25'`
- On modern JDKs (JDK 21/25+), ensure `maven-surefire-plugin` includes `-XX:+EnableDynamicAgentLoading` to allow ByteBuddy/Mockito dynamic agent attachment.

## 2. Dependency Management
- **Never downgrade dependency versions** based on assumptions. Verify artifact existence against Maven Central before questioning or modifying versions.
- This project targets modern Spring Boot releases (e.g. `4.1.0+`).

## 3. Spring Boot Library Packaging
- In reusable library modules (non-application JARs), always configure `spring-boot-maven-plugin` with `<skip>true</skip>` (or omit repackaging execution) so that a consumable library artifact is published rather than an executable fat JAR.

## 4. Task Completion Workflow
- After finishing each task and verifying tests/build, always prompt the user to commit and push changes to git.

## 5. Skill Scope & Placement
- **Project-Specific Skills (`.agents/skills/`)**: Only place workflows, runbooks, and CI/CD procedures dedicated strictly to this project (e.g. `maven-central-release`) in the project repository.
- **Global Skills (`~/.gemini/config/skills/`)**: Place cross-project, universal engineering skills (e.g. `code-review`, clean code checklists) in the global user configuration to prevent cluttering the project git repository.

## 6. Pre-1.0 Semantic Versioning (SemVer)
- During initial development (`0.y.z`):
  - Breaking API modifications increment the **minor** version (e.g. `0.0.1` -> `0.1.0`).
  - Compatible features and bugfixes increment the **patch** version (e.g. `0.1.0` -> `0.1.1`).
  - Reserve `1.0.0` for the first official stable production release.
