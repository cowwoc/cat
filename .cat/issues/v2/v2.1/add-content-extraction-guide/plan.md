# Plan: add-content-extraction-guide

## Goal

Add a concept document (`plugin/concepts/content-extraction.md`) that provides a checklist procedure
for plan-builder-agent to follow when an issue involves extracting, moving, or splitting content between
files. The guide ensures no information is silently dropped during refactoring by requiring explicit
enumeration of source content and verification against the destination.

## Pre-conditions

- [ ] All dependent issues are closed

## Post-conditions

- [ ] `plugin/concepts/content-extraction.md` exists with extraction checklist procedure
- [ ] `plan-builder-agent` references the concept file when generating plans for extraction/split/move jobs
- [ ] Compilation passes
- [ ] Unit tests pass
