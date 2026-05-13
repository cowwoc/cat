# Plan

## Goal

Add Codex slash command wrappers for common CAT workflows while keeping Claude command-free because Claude commands are deprecated. The wrappers should expose init, uninstall, cleanup, config, feedback, help, learn, optimize-execution, research, retrospective, status, and work through Codex autocomplete without duplicating the canonical CAT skill logic.

## Pre-conditions

- [ ] All dependent issues are closed

## Post-conditions

- [ ] Codex plugin artifacts include command files for init, uninstall, cleanup, config, feedback, help, learn, optimize-execution, research, retrospective, status, and work.
- [ ] Each Codex command wrapper routes to the matching `cat:*` skill and passes `$ARGUMENTS` through unchanged.
- [ ] Claude plugin artifacts do not include these command files and the existing Claude skill behavior remains unchanged.
- [ ] Tests or build verification cover the Codex command files and the absence of Claude command files.
- [ ] `mvn -f client/pom.xml verify -e` passes.
