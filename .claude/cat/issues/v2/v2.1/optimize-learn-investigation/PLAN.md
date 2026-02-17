# Plan: Optimize Learn Skill Investigation Phase

## Goal
Reduce learn investigation subagent duration from ~15 minutes / 48 tool calls to ~3 minutes by eliminating redundant
session log parsing and adding early termination.

## Satisfies
None (performance optimization from optimize-execution analysis)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Pre-extraction may miss edge cases the subagent would have found
- **Mitigation:** Pre-extraction uses broad patterns; subagent can still do targeted follow-up

## Optimizations

### 1. Single-pass pre-extraction script (HIGH impact)
Create a script that extracts ALL investigation-relevant data from the session JSONL in one pass. The parent agent runs
this BEFORE spawning the investigation subagent and includes the extracted data in the prompt.

**What to extract:**
- All Bash commands containing the mistake-related keywords (backup-before-squash, squash-quick, etc.)
- Tool results for those commands (stdout, stderr)
- Timestamps and CWD for each
- Agent IDs (main vs subagent context)

### 2. Early termination instruction (MEDIUM impact)
Add to the investigation phase prompt: "Stop searching after finding 3 positive matches for each type of evidence
(creation command, deletion command, failure output). Do not continue searching for additional evidence once the timeline
is established."

### 3. Parallel file reads at start (LOW impact)
Read all reference files (scripts, skills, agent definitions) in a single parallel call at the start rather than
sequentially as needed.

### 4. Eliminate timezone investigation (LOW impact)
Pre-compute and include timezone context in the prompt: "Container TZ=$TZ, git uses /etc/localtime. Timestamps in
branch names use `date +%Y%m%d-%H%M%S` which follows $TZ."

## Files to Modify
- `plugin/scripts/extract-investigation-context.sh` (or .py) - New: single-pass session log extractor
- `plugin/skills/learn/phase-investigate.md` - Add early termination instructions, reference pre-extracted data
- `plugin/skills/learn/SKILL.md` - Update Step 2 prompt to run pre-extraction and include results

## Acceptance Criteria
- [ ] Pre-extraction script extracts relevant tool calls in one pass
- [ ] Investigation subagent receives pre-extracted data instead of raw session log path
- [ ] Early termination instruction prevents redundant searching
- [ ] Investigation duration < 5 minutes for typical mistakes
- [ ] No loss of investigation quality (same root causes identified)

## Execution Steps
1. **Create pre-extraction script:** Python script that takes session JSONL path + keywords, outputs structured JSON
   with relevant tool calls, timestamps, and results
2. **Update learn SKILL.md Step 2:** Before spawning subagent, run pre-extraction with mistake keywords, include output
   in subagent prompt
3. **Update phase-investigate.md:** Add early termination rule, mention pre-extracted data format, add parallel read
   instruction for reference files
4. **Test:** Run learn on a known mistake, verify duration reduction
