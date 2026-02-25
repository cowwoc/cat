# Plan: batch-write-skill

## Goal
Create a `batch-write` skill that instructs agents to issue multiple Write/Edit tool calls in a single response when
modifying independent files, eliminating sequential round-trips (50-70% faster for 3+ file writes).

## Satisfies
None (infrastructure/optimization issue)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Agents may not reliably follow the instruction to parallelize; Write/Edit failures on one file could
  affect others in the same batch
- **Mitigation:** Skill provides clear patterns and failure-handling guidance; batching is advisory, not enforced

## Files to Modify
- `plugin/skills/batch-write/SKILL.md` - Skill frontmatter with preprocessor directive (new)
- `plugin/skills/batch-write/first-use.md` - Full skill content with patterns and examples (new)

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Steps
1. **Create skill directory** `plugin/skills/batch-write/`
2. **Create SKILL.md** with frontmatter (user-invocable: false, allowed-tools: Write, Edit, Bash) and preprocessor
   directive matching the batch-read pattern
3. **Create first-use.md** with:
   - Purpose: parallel Write/Edit calls for independent files in a single response
   - When to use vs when not to use (mirrors batch-read structure)
   - Performance comparison: sequential vs batched (N round-trips vs 1)
   - Usage patterns:
     - Multiple Write calls in one response (new files)
     - Multiple Edit calls in one response (existing files)
     - Mixed Write + Edit in one response
     - Bash heredoc approach for simple file creation (multiple files via single Bash call)
   - Error handling: if one file fails, others in the batch still succeed (tool calls are independent)
   - Limitations: files with dependencies (file B imports from file A) should NOT be batched
   - Performance characteristics table matching batch-read format

## Post-conditions
- [ ] `plugin/skills/batch-write/SKILL.md` exists with correct frontmatter and preprocessor directive
- [ ] `plugin/skills/batch-write/first-use.md` exists with comprehensive usage guidance
- [ ] Skill is automatically discoverable as `cat:batch-write`
- [ ] License headers follow exemption rules (SKILL.md files are exempt)
- [ ] E2E: Invoking `cat:batch-write` via the Skill tool returns the first-use.md content
