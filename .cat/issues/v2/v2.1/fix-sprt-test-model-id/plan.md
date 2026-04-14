# Issue: fix-sprt-test-model-id

## Type

bugfix

## Goal

Fix incorrect Sonnet 4.5 model ID in SPRT test file. The instruction-builder-regression.md test references claude-sonnet-4-5-20251001 which doesn't exist. Should be claude-sonnet-4-5-20250929.

## Post-conditions

- [ ] Bug fixed: model ID corrected to claude-sonnet-4-5-20250929 in test file
- [ ] Test file updated: both command invocation (line 12) and assertion (line 32) reference correct model ID
- [ ] No new issues introduced
- [ ] E2E verification: test runs successfully with corrected model ID

## Jobs

### Job 1: Fix model ID in test file

1. Update line 12 in plugin/tests/skills/sprt-runner-agent/first-use/instruction-builder-regression.md:
   - Change `"claude-sonnet-4-5-20251001"` to `"claude-sonnet-4-5-20250929"`

2. Update line 32 (assertion) to match:
   - Change `claude-sonnet-4-5-20251001` to `claude-sonnet-4-5-20250929`

3. Commit with type `test:` (test file modification)
