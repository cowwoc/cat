# Plan

## Goal

Fix skill-triggers guard comparison that fails due to plugin prefix mismatch between marker files and skill-triggers.json. RequireSkillForCommand compares loaded skill names (prefixed with `marketplaces:` from derivePluginPrefix()) against guard skill names (hardcoded as `cat:` in skill-triggers.json). The comparison always fails because the prefixes differ. Strip prefixes from both sides using GetSkill.stripPrefix() and remove hardcoded prefixes from skill-triggers.json.

## Pre-conditions

(none)

## Post-conditions

- [ ] RequireSkillForCommand strips prefixes from both loaded skill names and guard skill names before comparing, using GetSkill.stripPrefix()
- [ ] skill-triggers.json stores bare skill names without plugin prefix (e.g., `validate-git-safety-agent` instead of `cat:validate-git-safety-agent`)
- [ ] Regression test verifies guard comparison succeeds when loaded markers use a different prefix than the guard registry
- [ ] E2E: load a guarded skill via Skill tool, then run the corresponding guarded Bash command pattern and confirm RequireSkillForCommand allows it
