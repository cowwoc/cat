# Plan: fix-status-verbatim-output

## Problem
When `/cat:status` is invoked, haiku (and sonnet) summarize the `<output>` tag content instead of echoing it verbatim.
The user sees no status display — only a NEXT STEPS table or the agent's own summary text.

## Reproduction Code
```
# Invoke /cat:status — agent receives:
<skill>
# Status
Echo the content inside the `<output>` tag above exactly as it appears...
</skill>

<output>
╭──────────────────────────────────────────╮
│ 📊 Overall: 89% · 370/412 tasks         │
╰──────────────────────────────────────────╯
</output>

# Agent outputs its own summary instead of echoing the box content
```

## Expected vs Actual
- **Expected:** Agent copies box-drawing content verbatim, then appends NEXT STEPS table
- **Actual:** Agent summarizes ("I can see the project is at 89%...") or skips output entirely

## Root Cause
Empirical testing (5 rounds, 80+ trials across haiku and sonnet) identified two compounding issues:

1. **`<skill>` tag triggers analytical mode:** The `<skill>`/`<output>` tag structure causes both models to interpret
   content as metadata rather than copy-target. Renaming to `<verbatim-response>` + preamble achieves 100% on single
   invocation.

2. **Multi-invocation primacy bias:** On 2nd+ invocations, both models echo the FIRST invocation's content regardless
   of instructions (version attributes, replacement cues, numbered invocations all fail at 0%). This is compounded by
   `reference.md` telling the agent to "follow the original skill definition" — pointing back to the broken first
   response.

## Empirical Evidence

| Config | Single Invocation | Multi-Invocation |
|--------|-------------------|------------------|
| Original `<skill>`/`<output>` | 0% | 0% |
| Renamed `<verbatim-response>` + preamble | 100% | 0% |
| `<output>` before `<skill>` | 100% (simple) / 30% (production) | 0% |
| Code fence | 100% | 20% |
| Version counter attributes | N/A | 0% (haiku+sonnet) |

## Risk Assessment
- **Risk Level:** LOW
- **Regression Risk:** Changes affect status display only; other skills using `<output>` tag unaffected (SkillLoader
  wraps in `<output>` at line 513)
- **Mitigation:** Empirical testing validates fix before commit

## Files to Modify
- `plugin/skills/status-first-use/SKILL.md` — rename tags, add preamble explaining tag purpose
- `plugin/skills/reference.md` — replace vague re-invocation instructions with explicit verbatim echo + tag reference
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` — update tag names in output wrapping
  (line 513) and tag parsing patterns (lines 86-88)

## Test Cases
- [ ] Single invocation: agent echoes `<verbatim-response>` content verbatim (target: 90%+)
- [ ] Multi-invocation: agent echoes latest content, not stale first invocation (target: improvement over 0%)
- [ ] SkillLoader correctly parses new tag names
- [ ] Other skills using `<output>` pattern still work

## Execution Waves

### Wave 1
1. **Step 1:** Read Java conventions file, then update SkillLoader.java tag patterns and output wrapping
   - Change `OUTPUT_TAG_PATTERN` from `<output>` to `<verbatim-response>`
   - Change `SKILL_TAG_PATTERN` from `<skill>` to `<response-instructions>`
   - Update `invokeSkillOutput()` to wrap in `<verbatim-response>` tags
   - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java`

2. **Step 2:** Update status-first-use SKILL.md with renamed tags and preamble
   - Replace `<skill>` with `<response-instructions>`
   - Replace `<output>` with `<verbatim-response>`
   - Add preamble explaining tag purpose before the tags
   - Files: `plugin/skills/status-first-use/SKILL.md`

3. **Step 3:** Rewrite reference.md with explicit verbatim echo instructions referencing the tags
   - Remove "follow original skill definition" — make self-contained
   - Explicitly instruct to echo `<verbatim-response>` content verbatim
   - Reference `<response-instructions>` for post-echo formatting
   - Files: `plugin/skills/reference.md`

4. **Step 4:** Run Java tests
   - Files: `client/pom.xml`

5. **Step 5:** Build and install plugin
   - Rebuild jlink bundle and install to plugin cache

6. **Step 6:** Run empirical validation tests
   - Single invocation with production content (target: 90%+)
   - Multi-invocation with 2 priming messages (measure improvement)


## Post-conditions
- [ ] Single invocation pass rate >= 90% on haiku with production-sized content
- [ ] Multi-invocation pass rate improves over 0% baseline
- [ ] All Java tests pass
- [ ] SkillLoader correctly parses renamed tags
