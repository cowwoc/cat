# Plan: upgrade-compiler-to-jdk26

## Goal
Upgrade `client/pom.xml` compiler target from JDK 25 to JDK 26, and remove the redundant `<release>` element from
the `maven-compiler-plugin` `<configuration>` block. The `maven.compiler.release` property already sets the compiler
release target, making the `<release>` element inside the plugin configuration superfluous.

## Parent Requirements
None

## Approaches

### A: Update property only (Recommended)
- **Risk:** LOW
- **Scope:** 1 file (minimal)
- **Description:** Update `maven.compiler.release` from 25 to 26. Remove the `<release>26</release>` line inside the
  maven-compiler-plugin `<configuration>` block (the property already controls this). Single-file change.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** JDK 26 may have new lint warnings or deprecations that cause compilation failures with
  `-Xlint:all,-exports,-requires-automatic,-module` and `<failOnWarning>true</failOnWarning>`.
- **Mitigation:** Run `mvn -f client/pom.xml verify` after the change to catch any new warnings.

## Files to Modify
- `client/pom.xml` — change `<maven.compiler.release>` from 25 to 26; remove `<release>26</release>` from
  maven-compiler-plugin `<configuration>` block

## Pre-conditions
- [ ] All dependent issues are closed
- [ ] JDK 26 is installed and available on the PATH

## Sub-Agent Waves

### Wave 1
- Update `client/pom.xml`:
  - Change `<maven.compiler.release>25</maven.compiler.release>` to `<maven.compiler.release>26</maven.compiler.release>`
    (in `<properties>` section, line ~17)
  - Remove the `<release>25</release>` line from the `maven-compiler-plugin` `<configuration>` block
    (previously line ~131; the `maven.compiler.release` property already controls this)
  - Files: `client/pom.xml`

## Post-conditions
- [ ] `client/pom.xml` has `<maven.compiler.release>26</maven.compiler.release>` in `<properties>`
- [ ] `client/pom.xml` has NO `<release>` element inside `maven-compiler-plugin` `<configuration>`
- [ ] `mvn -f client/pom.xml test` exits 0 (all tests pass)
- [ ] E2E: `mvn -f client/pom.xml verify` completes with BUILD SUCCESS
