# Plan: enforce-bash-command-chaining

## Problem

Despite existing conventions in CLAUDE.md and MEMORY.md ("Chain independent git/bash commands with
`&&`"), the agent frequently issues 3-13 consecutive Bash calls for independent diagnostic commands
that could be chained. In one session, a sequence of 13 consecutive Bash calls was detected, many of
which were independent `git log`, `git status`, `git diff --stat`, and `ls` commands.

## Satisfies

None — efficiency enforcement for internal tooling

## Reproduction Code

```
# Agent issues these as 5 separate Bash calls:
Bash("git log --oneline -1")
Bash("git status --porcelain")
Bash("git diff --stat")
Bash("git branch --show-current")
Bash("pwd")

# Should chain as 1 call:
Bash("git log --oneline -1 && git status --porcelain && git diff --stat && git branch --show-current && pwd")
```

## Expected vs Actual

- **Expected:** Independent diagnostic commands are chained with `&&` in one Bash call
- **Actual:** Commands issued as 3-13 separate Bash calls despite existing conventions

## Root Cause

The existing convention is documented but not reinforced at the point where agents actually make
decisions about Bash calls. Subagents in particular may not receive this convention, or context
compaction may lose it.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Over-chaining dependent commands could mask failures
- **Mitigation:** Only chain commands that are truly independent (read-only diagnostics)

## Files to Modify

- Determine if reinforcement belongs in session instructions, subagent prompts, or a PreToolUse
  hook that detects sequential independent Bash calls
- Consider a PostToolUse hook that warns when 3+ consecutive Bash calls could have been chained

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

1. **Analyze where the convention is lost**
   - Check if subagent prompts include the chaining convention
   - Check if compaction summary preserves it
   - Determine if a hook-based approach would be more reliable than documentation

2. **Implement reinforcement at the identified injection point**
   - Add the convention where it will be most effective

3. **Add chaining constraint to work-verify agent** (fixes Criterion 2 gap)
   - Add a "Chain independent Bash commands" key constraint to `plugin/agents/work-verify.md`
   - Mirror the wording from `work-execute.md` lines 25-28: combine independent commands with `&&`,
     do not chain commands where a later command depends on the exit code or output of an earlier one

4. **Add chaining constraint to work-squash agent** (fixes Criterion 2 gap)
   - Add a "Chain independent Bash commands" key constraint to `plugin/agents/work-squash.md`
   - Mirror the wording from `work-execute.md` lines 25-28

5. **Add chaining constraint to work-merge agent** (fixes Criterion 2 gap)
   - Add a "Chain independent Bash commands" key constraint to `plugin/agents/work-merge.md`
   - Mirror the wording from `work-execute.md` lines 25-28

## Post-conditions

- [ ] Consecutive independent Bash calls are reduced by at least 50% in a typical session
- [ ] The convention is present in all relevant instruction sources (main agent and subagents)
