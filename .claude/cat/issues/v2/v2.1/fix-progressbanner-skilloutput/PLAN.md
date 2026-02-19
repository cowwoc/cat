# Plan: fix-progressbanner-skilloutput

## Problem

`/cat:work` fails with: `Class io.github.cowwoc.cat.hooks.skills.ProgressBanner does not implement SkillOutput`

The skill loader expects all registered skill classes to implement the `SkillOutput` interface, but `ProgressBanner`
does not implement it. `ProgressBanner` generates progress banners (workflow phase indicators) and has a different API
(`generate()` method) than the `SkillOutput.getOutput(String[])` contract.

## Root Cause

The skill loader resolves classes by name and casts to `SkillOutput`. `ProgressBanner` was registered as a skill
handler but its class doesn't implement the required interface.

## Files to Modify

- Identify where `ProgressBanner` is registered as a skill handler and either:
  - Make `ProgressBanner` implement `SkillOutput`, or
  - Fix the registration to not treat it as a `SkillOutput`

## Acceptance Criteria

- [ ] `/cat:work` no longer throws `ProgressBanner does not implement SkillOutput`
- [ ] Progress banners still render correctly
- [ ] Tests pass
