# Plan: status-display-redesign

## Goal
Redesign /cat:status output with ultra-compact layout supporting optional patch versions, visual separation for active
work, and blocked task visibility.

## Satisfies
None (infrastructure task)

## Design Specification

The approved design from conversation:

```
╭──────────────────────────────────────────────────────────────────────────╮
│ 📊 Overall: [██████████████████████░░░] 88% · 133/150 tasks              │
│                                                                          │
│ ╭─ 📦 v1: Multi-Agent Architecture ──────────────────────────────────╮   │
│ │ ☑️ v1.0 - v1.10 (81/81)                                            │   │
│ ╰────────────────────────────────────────────────────────────────────╯   │
│                                                                          │
│ ╭─ 📦 v2: Commercialization ─────────────────────────────────────────╮   │
│ │ v2.0: Licensing & Billing (52/66)                                  │   │
│ │    ☑️ v2.0.1: Legal & Branding (3/3)                               │   │
│ │                                                                    │   │
│ │    🔄 v2.0.2: Feature Gates (2/6)                                  │   │
│ │       ☑️ tier-feature-mapping                                      │   │
│ │       ☑️ feature-gate-middleware                                   │   │
│ │       🔳 validate-license-integration                              │   │
│ │       🚫 login-command (blocked by: validate-license)              │   │
│ │       🚫 upgrade-prompts (blocked by: validate-license)            │   │
│ │       🔳 update-check-startup                                      │   │
│ │                                                                    │   │
│ │    🔳 v2.0.3: Polish (0/3)                                         │   │
│ │    🔳 v2.0.4: Demos & Docs (0/4)                                   │   │
│ │                                                                    │   │
│ │ 🔳 v2.1: Pluggable Issue Trackers (0/3)                            │   │
│ ╰────────────────────────────────────────────────────────────────────╯   │
│                                                                          │
│ 📋 Next: /cat:work v2.0.2-validate-license-integration                   │
╰──────────────────────────────────────────────────────────────────────────╯
```

## Key Design Elements

1. **Progress bar merged with task count** - Single line for overall status
2. **Collapsed completed major versions** - e.g., "☑️ v1.0 - v1.10 (81/81)"
3. **🔄 only on active patch/minor** - Parent minor (v2.0) has no emoji
4. **Empty line before/after active patch** - Visual spotlight on current work
5. **Empty line between minor versions** - Clear separation (before v2.1)
6. **Completed tasks shown only for active version** - ☑️ prefix
7. **Blocked tasks visible** - 🚫 with dependency info
8. **Full task names** - No truncation with "..."
9. **Actionable footer** - Shows next command to run
10. **Patch/minor versions are OPTIONAL** - Display adapts when they don't exist

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Box alignment with emoji widths, backward compatibility with projects without patch versions
- **Mitigation:** Use existing build_box_lines.py for alignment, test with various project structures

## Files to Modify
- `hooks/skill_handlers/status_handler.py` - Main rendering logic

## Post-conditions
- [ ] Progress bar and task count on single line
- [ ] Completed major versions collapsed to range
- [ ] 🔄 only on active version (not parent)
- [ ] Empty lines around active patch version
- [ ] Empty line between minor versions within major
- [ ] Completed tasks visible for active version only
- [ ] Blocked tasks shown with 🚫 and dependency
- [ ] No task name truncation
- [ ] Works with projects that have no patch versions (backward compatible)
- [ ] Works with projects that have patch versions

## Sub-Agent Waves

### Wave 1
1. **Step 1:** Update status_handler.py rendering logic
   - Files: hooks/skill_handlers/status_handler.py
   - Verify: /cat:status shows new format
2. **Step 2:** Test with current project (no patch versions)
   - Verify: Display renders correctly without patch versions
3. **Step 3:** Test with patch version structure
   - Verify: Display renders correctly with patch versions
