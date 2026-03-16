# Plan: fix-persisted-skill-output-read-limit

## Problem
`plugin/rules/persisted-skill-output.md` instructs agents to read persisted skill output files using `limit=2000`.
This value is too large: for base64-dense content, 2000 lines can exceed 25,000 tokens (the Read tool hard limit),
causing the read to trigger another persisted-output condition and creating an infinite loop.

Empirical testing confirmed:
- ~20KB of base64 content (~361 lines, ~76 chars/line) ≈ 24,500 tokens (safe)
- ~21KB of base64 content ≈ 25,247 tokens (exceeds 25,000 limit)
- At typical skill line lengths, 400 lines ≈ 20KB — the safe boundary

The add-agent skill output (~80KB) was the specific trigger: `limit=2000` attempted to read the entire file at
once, hitting the token limit.

## Parent Requirements
None

## Root Cause
The `limit=2000` value was chosen without considering the token limit of the Read tool. Base64 or other
token-dense content at 2000 lines can easily exceed 25,000 tokens.

## Files to Modify
- `plugin/rules/persisted-skill-output.md` — change all occurrences of `2000` to `400` and update chunk
  calculation examples to reflect the new limit

## Specific Changes

In `plugin/rules/persisted-skill-output.md`:

1. Change "up to 2000 lines at a time" → "up to 400 lines at a time"
2. Change `ceil(total_lines / 2000)` → `ceil(total_lines / 400)`
3. Change `Read calls with \`limit=2000\` each` → `Read calls with \`limit=400\` each`
4. Update the examples section:
   - The 30.3KB example (~674 lines): `ceil(674/400) = 2` chunks:
     - Read offset=1 limit=400
     - Read offset=401 limit=400
   - The 150KB example (~3334 lines): `ceil(3334/400) = 9` chunks:
     - Read offset=1 limit=400
     - Read offset=401 limit=400
     - Read offset=801 limit=400
     - ... (continue for remaining chunks)

## Pre-conditions
- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1
- Update `plugin/rules/persisted-skill-output.md` replacing all `limit=2000` references with `limit=400` and
  updating the chunk calculation examples accordingly
  - Files: `plugin/rules/persisted-skill-output.md`
- Update STATE.md to closed with 100% progress
