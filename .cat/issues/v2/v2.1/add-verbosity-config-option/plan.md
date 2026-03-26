# Plan

## Goal

Add a verbosity config option with low/medium/high levels controlling how much CAT explains itself to the
user during task execution.

- **low**: Progress banners and errors only — no reasoning, no summaries beyond phase markers
- **medium**: Phase-transition summaries — what was done, key decisions (current behavior, default)
- **high**: Full reasoning — alternatives considered, tradeoffs noted, rationale for each decision

## Pre-conditions

(none)

## Post-conditions

- [ ] Config wizard presents verbosity option with three levels: Low, Medium (Default), High
- [ ] Verbosity setting stored in config.json under key "verbosity"
- [ ] `get-config-output effective` returns verbosity field with default "medium" when unset
- [ ] VerbosityLevel enum added with values LOW, MEDIUM, HIGH
- [ ] Low verbosity: only progress banners and errors appear; phase summaries suppressed
- [ ] Medium verbosity: phase-transition summaries shown (current behavior unchanged)
- [ ] High verbosity: full reasoning output including alternatives considered and tradeoffs
- [ ] Unit tests for VerbosityLevel enum and config parsing
- [ ] No regressions in existing config option handling
- [ ] E2E: run config skill, set each verbosity level and verify the output level matches its specification
