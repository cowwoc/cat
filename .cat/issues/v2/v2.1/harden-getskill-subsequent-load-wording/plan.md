# Plan

## Goal

Harden GetSkill.buildSubsequentLoadResponse wording so repeated skill invocations clearly distinguish previously loaded full instructions from fresh directive output, prioritizing effectiveness first with compaction as a close second.

## Pre-conditions

(none)

## Post-conditions

- [ ] Subsequent-load wording explicitly states previously loaded full skill body remains authoritative
- [ ] Subsequent-load wording explicitly states appended directive output is fresh runtime output only
- [ ] Wording reduces the risk of interpreting wrapper output as the full instruction set
