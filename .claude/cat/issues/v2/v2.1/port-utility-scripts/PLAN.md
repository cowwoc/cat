# Plan: port-utility-scripts

## Current State
Utility scripts handle licensing, feature gating, validation, monitoring, rendering, and analysis
via a mix of bash and Python in `plugin/scripts/`.

## Target State
All utility scripts ported to Java classes in the hooks module.

## Satisfies
None - infrastructure/tech debt

## Risk Assessment
- **Risk Level:** MEDIUM
- **Breaking Changes:** Utility scripts are mostly independent; low cascade risk
- **Mitigation:** Port independently; verify output parity for each

## Scripts to Port

### Licensing and Feature Gating
- `feature-gate.sh` (2KB) - Feature tier checking (currently 481ms due to subprocess chaining)
- `entitlements.sh` (2KB) - Tier-to-feature mapping
- `validate-license.py` (6KB) - License validation

### Validation and Analysis
- `validate-status-alignment.sh` (3KB) - STATUS.md consistency checks
- `analyze-session.py` (15KB) - Session efficiency analysis
- `migrate-retrospectives.py` (8KB) - Retrospective data migration

### Rendering and Display
- `render-diff.py` (27KB) - Git diff to 4-column table rendering
- `wrap-markdown.py` (10KB) - Markdown line wrapping at 120 chars
- `get-render-diff.sh` (4KB) - Wrapper for render-diff invocation
- `get-config-display.sh` (4KB) - Config display rendering
- `get-cleanup-survey.sh` (2KB) - Cleanup survey display

### Monitoring
- `monitor-subagents.sh` (5KB) - Subagent status monitoring
- `batch-read.sh` (7KB) - Batch file reading optimization

### Other
- `measure-emoji-widths.sh` (13KB) - Terminal emoji width measurement
- `register-hook.sh` (7KB) - Hook registration wizard

## Files to Modify
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/util/` - Licensing, validation utilities
- `hooks/src/main/java/io/github/cowwoc/cat/hooks/skills/` - Rendering classes
- `plugin/scripts/` - Remove ported scripts
- `plugin/skills/*/SKILL.md` - Update script invocation paths

## Execution Steps
1. **Port licensing scripts:** feature-gate, entitlements, validate-license as unified Java class
2. **Port render-diff.py:** Largest script - git diff table rendering
3. **Port wrap-markdown.py:** Markdown formatting utility
4. **Port validation scripts:** validate-status-alignment, analyze-session
5. **Port monitoring scripts:** monitor-subagents, batch-read
6. **Port remaining utilities:** measure-emoji-widths, register-hook, config/cleanup display
7. **Port migration script:** migrate-retrospectives
8. **Verify output parity:** Compare old vs new output for each script
9. **Run tests:** Execute `mvn -f hooks/pom.xml test`

## Success Criteria
- [ ] All 15 utility scripts have Java equivalents
- [ ] feature-gate latency reduced from ~481ms to <10ms (no subprocess chaining)
- [ ] render-diff output identical to Python version
- [ ] All tests pass (`mvn -f hooks/pom.xml test`)
- [ ] No Python/bash subprocess spawning for utility operations