<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Subagent Context Minimization

## Purpose

When the main agent reads files, runs commands, or expands content solely to pass it into a subagent prompt, that
content permanently inflates the main agent's context window for every subsequent turn. The goal of context
minimization is to keep the main agent's context lean by having subagents load their own content from disk rather than
having the main agent relay it. This reduces cumulative token costs and allows the main agent to sustain more turns
before context pressure forces early completion or compaction.

## When to Delegate to a Subagent

**Always delegate to subagents.** Even when the main agent's context is low, delegation preserves the main agent's
context budget for orchestration across long sessions and back-to-back issues. A main agent that stays lean can sustain
many more turns before context compaction forces early completion — and this strategic benefit applies regardless of
current context size.

The raw token savings do vary with context level (higher context = more savings per delegated phase: ~20-40% at 40-80k,
~50-70% at 80k+), but even at low context levels the compounding benefit of not loading file content into the main
agent's window outweighs the spawn overhead for most non-trivial phases.

For the full estimation formula (including inline vs. delegation cost calculation), see
[optimize-execution](../skills/optimize-execution/first-use.md) Step 4.

**Additional cost benefit:** Subagent tokens have a higher cache-read ratio (0.1x pricing) than main agent tokens at
the same context size. This makes the actual cost savings ~1.5-2x larger than raw token counts suggest — an additional
reason to prefer delegation.

## How to Pass References Instead of Content

### File References

Pass file paths (absolute, or relative to the worktree root) to the subagent rather than pasting file content into
the prompt. The subagent reads the file in its own context at a fraction of the cost.

```
✅ CORRECT: pass the path
  "Read plan.md at: /workspace/.claude/worktrees/my-issue/.cat/issues/v2/v2.1/my-issue/plan.md"

❌ WRONG: paste the content
  "Here is subagent-delegation.md: [600 lines of content pasted inline]"
```

### Git References

Pass commit SHAs or branch names rather than diff text or file snapshots. The subagent can run `git show` or
`git diff` in its own context.

```
✅ CORRECT: pass the SHA
  "Review the changes introduced by commit abc1234 against HEAD"

❌ WRONG: paste the diff
  "Here is the diff: [500-line git diff output pasted inline]"
```

### Task Descriptions

Pass a description of what to do — with well-defined inputs and expected outputs — rather than a pre-read expansion
of the files or instructions the subagent will need.

```
✅ CORRECT: pass the description with references
  "Implement the changes described in plan.md Wave 1. plan.md is at:
   /workspace/.claude/worktrees/my-issue/.cat/issues/v2/v2.1/my-issue/plan.md"

❌ WRONG: expand the plan inline
  "Here are the Wave 1 items: [plan.md Wave 1 section pasted verbatim]"
```

## Correct Patterns

### Pass File Path to Subagent

```yaml
# Main agent identifies what the subagent needs, passes the path only
Task tool:
  subagent_type: "general-purpose"
  model: "sonnet"
  prompt: |
    Implement the changes described in:
      plan.md: ${WORKTREE_PATH}/.cat/issues/v2/v2.1/my-issue/plan.md
    Read that file yourself before starting. Execute only Wave 1 items.
```

### Pass Commit SHA Instead of Diff Text

```yaml
# Main agent passes commit reference, not expanded diff
Task tool:
  subagent_type: "general-purpose"
  model: "sonnet"
  prompt: |
    Review the code changes in commit ${COMMIT_SHA}.
    Run: git show ${COMMIT_SHA}
    Check for license header compliance and correct commit type prefix.
```

### Pass Task Description with Well-Defined Inputs

```yaml
# Main agent describes the task with explicit file locations
Task tool:
  subagent_type: "general-purpose"
  model: "haiku"
  prompt: |
    Add a Related Concepts section to the file at:
      ${WORKTREE_PATH}/plugin/skills/optimize-execution/first-use.md
    Append exactly the following text at the end of the existing ## Related Concepts section:
      - **subagent-context-minimization**: When and how to pass file paths instead of
        file content to subagents
    Commit with message: "docs: add subagent-context-minimization reference"
```

## Anti-Patterns

### Main Agent Reads File Then Pastes Into Subagent Prompt

```
# Main agent reads 5 source files and pastes them all into the prompt
[Read: src/auth/AuthService.java]   ← main agent context grows by 300 lines
[Read: src/auth/TokenValidator.java] ← main agent context grows by 200 lines
Task: "Refactor AuthService. Here is the current content: [600 lines pasted] ..."

PROBLEM: Each relayed file adds its full content to the main agent's context permanently,
compounding with every subsequent main agent turn. For a 500-line file (~2k tokens),
relaying instead of letting the subagent read it costs 2k × remaining_main_turns tokens.
```

### Main Agent Runs `git diff` Then Pastes Output Into Subagent Prompt

```
# Main agent runs diff and pastes output for a review subagent
[Bash: git diff HEAD~1 HEAD]  ← main agent context grows by diff output
Task: "Review these changes: [500 lines of diff pasted] ..."

PROBLEM: The review subagent can run git diff itself. The main agent has loaded content
it does not need for its own decisions, and that content persists for all future turns.
```

### Main Agent Reads Test Output Then Passes Verbatim to Fix Subagent

```
# Main agent runs tests, pastes failure output into fix subagent prompt
[Bash: mvn test 2>&1]  ← main agent context grows by full test output
Task: "Fix the failing tests. Here is the output: [300 lines of test output] ..."

PROBLEM: The fix subagent can re-run the tests and get the output in its own context.
Passing the verbatim output wastes main agent context without benefit.
```

**Exception:** If the main agent already read the file or ran the command for its *own* decision-making (for example,
reviewing plan.md to choose which wave to execute), it MAY include that content in the subagent prompt to avoid a
redundant subagent read. The key distinction is that the main agent read it for its own purpose first, not as a relay.

## Codebase Examples

- `plugin/skills/optimize-execution/first-use.md` **Step 6, item 10** (Content Relay Detection): describes how to detect
  main agent Read/Grep/Bash calls whose output is only used to populate a subagent prompt, and recommends letting the
  subagent load its own content instead.

- `plugin/concepts/subagent-delegation.md` **Pre-Spawn Checklist item 7**: notes that if the subagent will write or
  edit files, the main agent should include relevant project conventions (e.g., line wrapping, license headers,
  language-specific style) *inline* — these are short, high-value conventions the main agent already knows, not
  large file contents the subagent could read itself.

- `plugin/skills/instruction-builder-agent/first-use.md` **Step 2** (Design Subagent Delegation): the design subagent is
  passed the existing skill content inline (already read in Step 1) and file path references for methodology and
  conventions documents it will read itself, rather than pre-expanding all supporting files into the main agent prompt.

## Related Concepts

- **subagent-delegation**: General subagent safety rules: fail-fast behavior, prompt completeness, acceptance
  criteria, and validation separation — `plugin/concepts/subagent-delegation.md`
- **optimize-execution**: Post-hoc session analysis including the full delegation cost estimation formula and
  content relay detection — `plugin/skills/optimize-execution/first-use.md`
