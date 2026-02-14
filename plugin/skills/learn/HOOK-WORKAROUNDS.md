# Investigate Hook Workarounds

Lazy-loaded when a mistake involves bypassing or working around a hook.

## When to Use

When a mistake involves bypassing or working around a hook, investigate WHY.

Hooks exist to enforce correct behavior. If an agent worked around a hook (intentionally or not),
there are three possible causes:

| Cause | Investigation | Fix |
|-------|---------------|-----|
| **Hook pattern gap** | Hook regex/logic didn't match the command variant used | Fix the hook pattern |
| **Guidance gap** | Hook didn't explain what the correct approach is | Add guidance to hook error message |
| **Technical impossibility** | The "right thing" doesn't work for some reason | Fix the underlying issue or update guidance |

## Investigation Checklist

```yaml
hook_workaround_analysis:
  # Step 1: Was the right thing technically possible?
  right_thing_possible:
    question: "Could the agent have done this correctly?"
    how_to_check: "Try the 'correct' approach manually - does it work?"
    if_no: "The hook is blocking something that can't be done correctly - fix the underlying issue"
    if_yes: "Continue to step 2"

  # Step 2: Did existing guidance explain the correct approach?
  guidance_exists:
    question: "Did the hook (or related docs) tell the agent what TO do?"
    check_locations:
      - Hook error message (does it show the fix?)
      - Related skill documentation
      - System prompts/instructions
    if_no: "Add guidance showing the correct approach"
    if_yes: "Continue to step 3"

  # Step 3: Why didn't the agent follow the guidance?
  why_not_followed:
    possibilities:
      - "Hook pattern didn't match, so agent never saw the guidance"
      - "Conflicting guidance elsewhere (e.g., 'avoid cd' vs 'cd before delete')"
      - "Guidance was ambiguous or incomplete"
    action: "Fix the specific gap identified"
```

## Example - M398

```yaml
# Agent used: git -C /workspace worktree remove /path --force
# Hook blocked: git worktree remove (without -C flag)
# Result: Hook didn't fire, shell cwd was deleted

investigation:
  right_thing_possible: true  # "cd /workspace && git worktree remove" works
  guidance_exists: true       # work-merge skill shows correct pattern
  why_not_followed: "Hook pattern didn't match 'git -C' variant"

fixes_needed:
  - pattern_fix: "Update hook regex to match git -C flag"
  - guidance_fix: "Clarify when cd IS appropriate despite 'avoid cd' guidance"
```

**Key insight:** If the agent worked around a hook, the hook's job is to BLOCK the wrong action
AND SHOW the right action. Both the blocking pattern AND the guidance must be correct.
