# Plan

## Goal

Ship separate CAT bundles for each runtime. The Codex bundle must not contain Claude-specific plugin code, generated
code, classes, launchers, hooks, rules, skills, agent definitions, secrets, or compatibility shims, including shared
classes such as `SharedSecrets`. Refactor the plugin so each runtime bundle contains only runtime-neutral shared code
plus that runtime's own code.

## Pre-conditions

- [ ] `split-runtime-rules-directories` is closed
- [ ] `split-cli-runtime-modules` is closed

## Post-conditions

- [ ] User-visible behavior is unchanged for supported Claude and Codex workflows
- [ ] The Codex bundle contains no Claude-specific plugin source, generated code, classes, launchers, hooks, rules,
  skills, agent definitions, secrets, or compatibility shims
- [ ] The Claude bundle contains no Codex-specific plugin source, generated code, classes, launchers, hooks, rules,
  skills, agent definitions, secrets, or compatibility shims
- [ ] Runtime-neutral shared code contains no Claude- or Codex-specific implementations, product API assumptions,
  synthetic environment requirements, or runtime-specific secret access for another runtime
- [ ] Build and distribution packaging emit separate Claude and Codex CAT bundles assembled from explicit shared plus
  runtime-specific inputs
- [ ] Tests or build verification cover representative bundle contents and runtime behavior for both runtimes
- [ ] Tests passing: `mvn -f client/pom.xml verify -e` exits 0
