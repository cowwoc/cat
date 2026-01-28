# Plan: java-jdk-infrastructure

## Metadata
- **Parent:** migrate-python-to-java
- **Sequence:** 1 of 5
- **Estimated Tokens:** 25K

## Objective
Create jlinked JDK 25 bundle with Jackson 3, and bootstrap/runner scripts.

## Scope
- JDK jlink configuration to create minimal runtime
- Jackson 3 module inclusion
- SessionStart bootstrap script (check/download JDK)
- Java hook runner intermediary script

## Dependencies
- None (first in sequence - other tasks depend on this)

## Files to Create
- `plugin/hooks/jdk/jlink-config.sh` - JDK jlink build configuration
- `plugin/hooks/jdk/session_start.sh` - SessionStart hook to bootstrap JDK
- `plugin/hooks/jdk/java_runner.sh` - Intermediary script to run Java hooks
- `plugin/hooks/jdk/README.md` - Documentation for JDK bundle
- `.github/workflows/build-jdk-bundle.yml` - CI workflow to build and release bundles

## Execution Steps
1. Research JDK 25 jlink options for minimal runtime
2. Create jlink configuration including Jackson 3 modules
3. Implement session_start.sh to detect/download JDK
4. Implement java_runner.sh to execute Java hooks
5. Create GitHub Actions workflow to build bundles for all platforms
6. Document build process for jlinked bundle

## Acceptance Criteria
- [x] jlink config creates minimal JDK with Jackson 3
- [x] session_start.sh correctly detects existing JDK or downloads bundle
- [x] java_runner.sh correctly invokes Java hooks
- [x] Scripts work on Linux (primary platform)
- [x] GitHub workflow builds bundles for linux-x64, linux-aarch64, macos-x64, macos-aarch64
- [x] Bundles uploaded to GitHub releases on version tags
