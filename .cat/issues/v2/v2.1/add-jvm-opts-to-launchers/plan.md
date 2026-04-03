# Plan

## Goal

Add -ea (enable assertions) to dev builds and CAT_JVM_OPTS support to all launcher scripts. build-jlink.sh should
accept an --enable-assertions flag that bakes -ea into generated launchers. All launchers should also expand
${CAT_JVM_OPTS:-} so users can inject JVM flags at runtime. The /cat-update-client skill should pass
--enable-assertions when invoking the build.

## Pre-conditions

(none)

## Post-conditions

- [ ] `build-jlink.sh --enable-assertions` generates launchers with `-ea` flag
- [ ] `build-jlink.sh` without `--enable-assertions` generates launchers without `-ea`
- [ ] All generated launchers include `${CAT_JVM_OPTS:-}` expansion for runtime JVM flag injection
- [ ] The `/cat-update-client` skill passes `--enable-assertions` to the build
- [ ] Tests pass and no regressions introduced
- [ ] E2E: Build the client with `--enable-assertions`, verify a launcher script contains both `-ea` and `CAT_JVM_OPTS`
