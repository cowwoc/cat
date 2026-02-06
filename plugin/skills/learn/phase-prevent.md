# Phase 3: Prevent

This phase identifies prevention strategies, evaluates quality, implements fixes, and verifies no new priming.

## Your Task

Complete the prevention phase for the learn skill. You will receive analysis results from Phase 2 as input.

Your final message must be ONLY the JSON result object with no surrounding text or explanation. The parent agent parses your response as JSON.

## Input

You will receive a JSON object with analysis results containing:
- Mistake description and impact
- Context metrics
- Root cause and RCA method
- Category and recurrence information

## Step 5: Check for Context Degradation Patterns

**CAT-specific analysis checklist:**

Reference: agent-architecture.md § Context Limit Constants

```yaml
context_degradation_analysis:
  tokens_at_error: 95000
  threshold_exceeded: true
  threshold_exceeded_by: 15000
  compaction_events: 2
  errors_after_compaction: true
  session_duration: 4.5 hours
  messages_before_error: 127
  early_session_quality: high
  late_session_quality: degraded
  quality_degradation_detected: true
  context_related: LIKELY
  confidence: 0.85
```

## Step 6: Identify Prevention Level

**Reference:** See [prevention-hierarchy.md](prevention-hierarchy.md) for detailed hierarchy and escalation rules.

**Quick Reference:**

| Level | Type | Description |
|-------|------|-------------|
| 1 | code_fix | Make incorrect behavior impossible in code |
| 2 | hook | Automated enforcement via PreToolUse/PostToolUse |
| 3 | validation | Automated checks that catch mistakes early |
| 4 | config | Configuration or threshold changes |
| 5 | skill | Update skill documentation with explicit guidance |
| 6 | process | Change workflow steps or ordering |
| 7 | documentation | Document to prevent future occurrence (weakest) |

**Key principle:** Lower level = stronger prevention. Always prefer level 1-3 over level 5-7.

## Step 7: Evaluate Prevention Quality

**BEFORE implementing, verify the prevention is robust:**

```yaml
prevention_quality_check:
  verification_type:
    positive: "Check for PRESENCE of correct behavior"  # ✅ Preferred
    negative: "Check for ABSENCE of specific failure"   # ❌ Fragile

  # Ask: Am I checking for what I WANT, or what I DON'T want?
  # Example:
  #   ❌ grep "Initial implementation -"  (catches ONE placeholder pattern)
  #   ✅ grep "^- \`[a-f0-9]{7,}\`"        (checks for correct commit format)

  generality:
    question: "If the failure mode varies slightly, will this still catch it?"
    examples:
      - "What if placeholder text changes from 'Initial' to 'First'?"
      - "What if someone uses 'TBD' or 'TODO' instead?"
      - "What if the format is subtly wrong in a different way?"
    # If answer is NO → prevention is too specific → redesign

  inversion:
    question: "Can I invert this check to verify correctness instead?"
    pattern: |
      Instead of: "Fail if BAD_PATTERN exists"
      Try:        "Fail if GOOD_PATTERN is missing"
    # Positive verification catches ALL failures, not just anticipated ones

  fragility_assessment:
    low:    "Checks for correct format/behavior (positive verification)"
    medium: "Checks for category of errors (e.g., any TODO-like text)"
    high:   "Checks for exact observed failure (specific string match)"
```

**Decision gate:** If fragility is HIGH, redesign the prevention before implementing.

## Step 7b: Replay Scenario Verification (BLOCKING GATE - M305)

**MANDATORY: Verify prevention would have prevented THIS specific problem.**

Before proceeding, mentally replay the exact scenario that caused the mistake:

```yaml
scenario_replay:
  # Step 1: Describe the exact sequence that led to the mistake
  what_happened:
    step_1: "{first action/state}"
    step_2: "{second action/state}"
    step_3: "{action where mistake occurred}"
    result: "{the bad outcome}"

  # Step 2: Insert your proposed prevention and replay
  with_prevention:
    step_1: "{same first action/state}"
    step_2: "{same second action/state}"
    prevention_activates: "{when/how does prevention trigger?}"
    step_3: "{what happens differently?}"
    result: "{the good outcome}"

  # Step 3: Verify causation
  verification:
    prevents_root_cause: true|false  # Does it fix the CAUSE or just a symptom?
    would_have_blocked: true|false   # Would this SPECIFIC scenario have been prevented?
    timing_correct: true|false       # Does prevention activate BEFORE the mistake?
```

**BLOCKING CONDITION:**

If `would_have_blocked: false` or `prevents_root_cause: false`:
- STOP - your prevention fixes a symptom, not the cause
- Return to RCA and dig deeper into WHY the mistake occurred
- Find prevention that addresses the actual failure point

**Fix Source, Not Symptoms (M355):**

When a mistake involves incorrect output from a subagent or downstream process:

| Symptom Fix (❌ WRONG) | Source Fix (✅ CORRECT) |
|------------------------|-------------------------|
| Add validation to catch bad output | Fix the prompt/input that caused bad output |
| Add check for fabricated scores | Remove priming content from delegation prompt |
| Add warning when result looks wrong | Fix the instructions that led to wrong result |
| Double-check subagent work | Fix the task description given to subagent |

**The question to ask:** "Why did the subagent produce wrong output?"
- If answer involves the PROMPT you gave it → fix the prompt
- If answer involves the DOCUMENTATION it read → fix the documentation
- Adding validation AFTER is treating the symptom, not the cause

**Example - M355 Pattern:**

```yaml
# Mistake: Subagent reported unexpected validation scores

# ❌ SYMPTOM FIX: Add validation layer to catch "wrong" results
prevention: "Run independent validation and compare scores"
prevents_root_cause: false  # Another subagent is no more independent!

# ✅ SOURCE FIX: Investigate and fix the prompt or skill
prevention: "Review delegation prompt for priming; fix skill instructions if ambiguous"
prevents_root_cause: true  # Subagent now produces correct results
```

**Note (M357):** Identical scores (e.g., all 1.0) do NOT inherently indicate fabrication. Multiple files
can legitimately achieve the same score. When results differ from expectations, investigate the prompt
or skill methodology - don't add validation layers.

**Anti-pattern:** "The subagent did X wrong, so I'll add a check for X."
**Correct approach:** "The subagent did X wrong because my prompt said Y. Fix Y."

**Example - would_have_blocked: false:**

```yaml
# Mistake: Squash captured stale file state from diverged worktree

# ❌ FAILS VERIFICATION: "Add warning when worktree diverges from base"
scenario_replay:
  what_happened:
    step_1: "Worktree created from v2.1 at commit A"
    step_2: "v2.1 advanced to commit D, worktree not updated"
    step_3: "git reset --soft v2.1 captured stale working directory"
    result: "M304 changes reverted"
  with_prevention:
    prevention_activates: "Before squash, detect and warn about divergence"
    step_3: "Warning printed, but squash proceeds anyway"
    result: "M304 changes still reverted"
  verification:
    would_have_blocked: false  # Warning doesn't prevent the failure!
    prevents_root_cause: false
    # STOP: This prevention is useless - find one that actually blocks

# ✅ PASSES VERIFICATION: "Rebase onto base before squashing"
scenario_replay:
  with_prevention:
    prevention_activates: "Before squash, rebase onto current base"
    step_3: "Working directory updated to include M304, then squash"
    result: "M304 changes preserved"
  verification:
    would_have_blocked: true   # This specific scenario prevented
    prevents_root_cause: true  # Addresses stale working directory state
```

**Why this gate exists:** M305 showed that proposed solutions may sound reasonable but fail
to actually prevent the failure. Replaying the exact scenario exposes whether prevention
changes the outcome or just adds noise (warnings, documentation).

## Step 8: Check If Prevention Already Exists (MANDATORY)

**CRITICAL: If prevention already exists, it FAILED and MUST be replaced with stronger prevention.**

Before implementing prevention, check if it already exists:

```yaml
existing_prevention_check:
  question: "Does documentation/process already cover this?"
  check_locations:
    - Workflow files (work.md, etc.)
    - CLAUDE.md / project instructions
    - Skill documentation
    - Existing hooks

  if_exists:
    conclusion: "Existing prevention FAILED - it was ineffective"
    action: "MUST escalate to higher prevention level"
    rationale: |
      If prevention exists and the mistake still occurred, that prevention
      is NOT WORKING. Pointing to it again changes nothing. The mistake
      WILL recur unless you implement STRONGER prevention.
```

**Key insight:** Existing prevention that didn't prevent the mistake is NOT prevention - it's
failed prevention. You must escalate to a level that will actually work.

**Escalation hierarchy (when current level failed):**

| Failed Level | Escalate To | Example |
|--------------|-------------|---------|
| Documentation | Hook/Validation | Add pre-commit hook that blocks incorrect behavior |
| Process | Code fix | Make incorrect path impossible in code |
| Threshold | Lower threshold + hook | Add monitoring that forces action |
| Validation | Code fix | Compile-time or runtime enforcement |

**Example - Documentation failed:**

```yaml
# Situation: Workflow says "MANDATORY: Execute different issue when locked"
# Agent ignored it and tried to delete the lock

# ❌ WRONG: Record prevention as "documentation" pointing to same workflow
prevention_type: documentation
prevention_path: "work.md"  # Already says MANDATORY - and it FAILED!

# ✅ CORRECT: Escalate to hook that enforces the behavior
prevention_type: hook
prevention_path: "${CLAUDE_PROJECT_DIR}/.claude/hooks/enforce-lock-protocol.sh"
action: |
  Create hook that detects lock investigation patterns and blocks them.
  Or: Modify issue-lock.sh to output ONLY "find another issue" guidance,
  removing any information that could be used to bypass the lock.
```

**The prevention step MUST take NEW action.** Recording a mistake without implementing NEW prevention
(beyond what already existed) is not learning - it's just logging. The same mistake WILL recur.

**BLOCKING CRITERIA (A002) - Documentation-level prevention NOT ALLOWED when:**

| Condition | Why Blocked | Required Action |
|-----------|-------------|-----------------|
| Similar documentation already exists | Documentation already failed | Escalate to hook or code_fix |
| Mistake category is `protocol_violation` | Protocol was documented but violated | Escalate to hook enforcement |
| This is a recurrence (`recurrence_of` is set) | Previous prevention failed | Escalate to stronger level |
| prevention_type would be `documentation` (level 7) | Weakest level, often ineffective | Consider hook (level 2) or validation (level 3) |

**Self-check before recording prevention_type: documentation:**

```yaml
documentation_prevention_blocked_if:
  - Similar instruction already exists in workflow/skill docs
  - The mistake was ignoring existing documentation
  - Category is protocol_violation (protocols ARE documentation)
  - This is a recurrence of a previous mistake

# If ANY of the above is true:
action: "STOP. Escalate to hook, validation, or code_fix instead."
```

**Verification questions:**
1. "Did prevention for this already exist?" → If YES, it failed and must be escalated
2. "What NEW mechanism will prevent this tomorrow?" → Must be different from what failed today
3. "Is this prevention stronger than what failed?" → Must be higher in the hierarchy
4. "Am I choosing documentation because it's easy?" → If YES, find a stronger approach (A002)

**If you cannot identify NEW prevention stronger than what already exists, you have NOT learned.**

## Step 8b: Check for Misleading Documentation (M256)

**CRITICAL: Documentation may have ACTIVELY MISLED the agent toward the wrong approach.**

If the mistake involves a **skill file**, use skill-builder's Priming Prevention Checklist to analyze it:

```
/cat:skill-builder analyze {path-to-skill}
```

The checklist covers:
- Information Ordering (teaching HOW before saying "invoke tool")
- Output Format (expected values embedded in examples)
- Cost/Efficiency Language (suggesting proper approach is expensive)
- Encapsulation (orchestrator learning to do the task directly)
- Reference Information (formatting details that should be in preprocessing)

**Quick self-check for non-skill documents:**

| Question | If YES |
|----------|--------|
| Does doc teach approach BEFORE saying not to use it? | Reorder or remove |
| Are there "for reference only" sections? | Move to preprocessing |
| Could agent do the task after reading this doc? | Too much exposed |

**Reference:** See `/cat:skill-builder` § "Priming Prevention Checklist" for detailed patterns.

## Step 9: Implement Prevention

**MANDATORY: Take concrete action. Prevention without action changes nothing.**

The prevention step must result in a modified file - code, hook, configuration, or documentation.
If you finish this step without editing a file, you have not implemented prevention.

**Escalation and Layered Prevention (M342):**

When escalating from documentation to hook/validation, **keep both layers** but align them:

| Layer | Purpose | Keep? |
|-------|---------|-------|
| **Skill/Doc** | Proactive guidance - teaches WHY, guides before action | YES |
| **Hook** | Reactive enforcement - blocks after attempt, provides fix | YES (new) |

**Why keep both:**
- Skills guide agents BEFORE they act (proactive)
- Hooks catch mistakes AFTER the attempt (reactive)
- Skills explain context and cover related rules
- Hooks can fail (bugs, edge cases, not loaded)

**When escalating, update the skill to reference the hook:**
```markdown
- NEVER do X - use Y instead
  *(Enforced by hook MXXX - blocked if condition)*
```

This creates defense-in-depth: guidance prevents most mistakes, enforcement catches the rest.

**Script vs Skill Instructions (M363):**

Before adding prevention to a skill, ask: **Does this require LLM decision-making?**

| If the check is... | Implement as... | Why |
|--------------------|-----------------|-----|
| Deterministic (fixed inputs → fixed outputs) | Script with tests | Testable, consistent, no LLM variance |
| Requires context/judgment | Skill instructions | LLM needed for interpretation |

**Examples:**

| Check | Deterministic? | Implementation |
|-------|----------------|----------------|
| "Does branch have commits ahead of base?" | Yes - `git log` | Script |
| "Is file path inside worktree?" | Yes - path comparison | Script |
| "Does commit message follow convention?" | Yes - regex match | Hook |
| "Is this change architecturally sound?" | No - requires judgment | Skill instructions |
| "Should we decompose this task?" | No - context-dependent | Skill instructions |

**When in doubt, ask:** "Could a bash script do this with no LLM?" If yes → script with tests.

### Fix Location Checklist (M419 - MANDATORY)

**BLOCKING: Complete this checklist BEFORE editing any file for prevention.**

```yaml
fix_location_checklist:
  # Step 1: Classify the rule
  rule_scope:
    question: "Does this rule apply to ONE specific skill, or to MULTIPLE skills/scenarios?"
    if_one_skill: "Edit that skill's SKILL.md"
    if_multiple: "Go to Step 2"

  # Step 2: Find the deepest applicable doc
  depth_analysis:
    question: "What is the LOWEST level document where this rule applies?"
    check_order:
      - "Concept doc? (applies to all skills referencing concept)"
      - "Workflow doc? (applies to specific workflow)"
      - "Skill doc? (applies to one skill only)"
      - "Command doc? (single entry point)"
    rule: "Choose the FIRST applicable level (deepest = most reuse)"

  # Step 3: Verify before editing
  verification:
    file_to_edit: "________________"  # Fill in BEFORE editing
    why_this_level: "________________"  # Justify the depth choice
    not_editing_because_convenient: true  # Confirm you're not just editing current file
```

**Common mistake (M419):** Editing the file you're currently working with because it's convenient,
instead of finding the appropriate depth. Example: Putting "use Tokens header for compression" in
work-with-issue.md (generic) instead of subagent-delegation.md (concept doc for all result presentation).

**Fix Location Principle: Apply to deepest document possible.**

When choosing WHERE to implement a fix, prefer the lowest-level document that addresses the issue:

| Level | Example | Benefit |
|-------|---------|---------|
| Concept doc | `concepts/subagent-delegation.md` | All skills/workflows referencing it inherit the fix |
| Skill doc | `skills/shrink-doc/SKILL.md` | All invocations of that skill get the fix |
| Workflow doc | `concepts/work.md` | Specific workflow improved |
| Command doc | `commands/work.md` | Single entry point fixed |

**Why depth matters:** A fix in a concept document (e.g., subagent-delegation.md) benefits every skill
and workflow that references it. A fix in a command document benefits only that command. Apply fixes
at the deepest level where they're relevant to maximize fix propagation.

**Example:** M277 (validation separation) belongs in `shrink-doc/SKILL.md` (skill-specific validation)
not `work.md` (generic workflow) because the per-file subagent pattern is shrink-doc-specific.

**Verification question (M297):** Before committing a fix, ask: "Is this rule specific to one skill/context,
or genuinely applies to all issues?" If specific → find the skill doc. If generic → workflow doc is correct.

**Generalize prevention to match fix location scope (M440).** When the fix location is a document that
handles multiple skills/scenarios, write the prevention in general terms — not specific to the skill that
triggered the problem. The fix should cover all similar cases.

| Fix Location | Prevention Wording |
|--------------|-------------------|
| Skill-specific doc (e.g., `shrink-doc/SKILL.md`) | May reference that skill's specific behavior |
| General doc (e.g., `work-with-issue/SKILL.md`) | Must apply to ALL skills handled by that doc |
| Concept doc (e.g., `concepts/subagent-delegation.md`) | Must apply to ALL contexts using that concept |

Example: If shrink-doc's iteration loop was bypassed, and the fix goes in work-with-issue (which handles
all skills), write "complete each skill fully before delegation" — not "complete shrink-doc's iteration
loop before delegation."

**Language requirements for documentation/prompt changes (M177):**

When prevention involves updating documentation, prompts, or instructions, use **positive actionable
language** that guides toward correct behavior rather than warning against mistakes.

| Instead of (negative) | Use (positive) |
|----------------------|----------------|
| "Do NOT use approximate content" | "Copy-paste exact content from final output" |
| "Never skip the verification step" | "Complete verification before proceeding" |
| "Don't forget to commit" | "Commit changes before requesting review" |
| "Avoid using placeholder text" | "Write final content first, then calculate" |

**Why positive framing works better:**
- Tells the agent what TO do (actionable) vs what to avoid (requires inference)
- Creates clear mental model of correct behavior
- Reduces cognitive load - no need to invert the instruction
- Section titles should name the solution, not the problem (e.g., "Copy-Paste Workflow" not "Avoiding Content Mismatch")

**Self-check before finalizing prevention text:**
1. Does each instruction describe an action to take?
2. Are section titles named after solutions, not problems?
3. Would someone know exactly what to do after reading this?

Keep negative language only when no actionable positive alternative exists (e.g., security warnings
where the "don't" is the entire point).

**Fail-Fast Error Handling (M361):**

When implementing prevention that modifies error handling, apply the fail-fast principle:

| Situation | Wrong Approach | Correct Approach |
|-----------|----------------|------------------|
| Required parameter missing | Return None (allow) | Block with error |
| Can't verify safety | Assume safe | Assume unsafe, block |
| Validation impossible | Skip validation | Fail the operation |

**The mental model:**
- "Unknown safety" = "unsafe"
- If you can't verify an operation is safe, block it
- Never allow potentially dangerous operations to proceed when validation fails

**Example (M361):**
```python
# ❌ WRONG: Allow when can't validate
cwd = context.get("cwd")
if not cwd:
    return None  # Allows dangerous command!

# ✅ CORRECT: Block when can't validate
cwd = context.get("cwd")
if not cwd:
    return {"decision": "block", "reason": "Cannot verify safety - cwd missing"}
```

**Complete Fix Requirement for Documentation Priming (M345):**

When documentation primed the agent for wrong behavior, the fix must be **complete**:

| Scenario | Incomplete Fix | Complete Fix |
|----------|----------------|--------------|
| Automation exists but broken | "NEVER manually construct" | Fix the preprocessing/handler |
| Automation doesn't exist yet | "NEVER manually construct" | ASK USER: build it or simplify? |
| Skill references non-existent feature | "Don't use feature X" | ASK USER: build it or simplify? |

**The fix must make correct behavior possible, not just prohibit wrong behavior.**

**MANDATORY: Preserve Output Format When Possible (M345):**

When a skill cannot produce its intended output due to missing automation:

1. **Do NOT unilaterally change the output format**
2. **Use AskUserQuestion to offer the choice:**

```yaml
question: "Skill '{skill}' references output that cannot be generated. How should I proceed?"
options:
  - label: "Build the missing automation"
    description: "Create the preprocessing script/handler to generate the intended output"
  - label: "Simplify the output format"
    description: "Change skill to use simpler format (e.g., markdown instead of boxes)"
```

3. Implement whichever option the user selects

**Checklist before finalizing documentation priming fix:**

```yaml
complete_fix_checklist:
  negative_guidance: "Does fix say what NOT to do?"  # Necessary but insufficient
  positive_guidance: "Does fix say what TO do?"      # Required
  ability_to_act: "Can agent actually do it?"        # Required
  format_preserved: "Is original output format retained?"  # Preferred

  # If ability_to_act is NO:
  if_automation_broken:
    action: "Fix the automation"
  if_automation_missing:
    action: "ASK USER: build automation or simplify output?"
    do_not: "Unilaterally change output format"
```

**Anti-pattern (M345):** Adding "NEVER do X" without ensuring the agent CAN do Y.
**Anti-pattern (M345):** Changing output format without user consent.

**Missing Preprocessing Output (M452):**

When a handler/preprocessing script should have provided output but didn't, fail-fast. Do NOT add
fallback behavior that teaches the LLM to run scripts directly or gather data manually.

| Prevention Pattern | Wrong | Correct |
|-------------------|-------|---------|
| Missing script output | Add script command as fallback | Skip output or error — fix the handler |
| Missing handler data | Teach LLM to read files manually | Error — fix the handler |

Adding script commands as fallbacks teaches the agent to bypass preprocessing — which is the same
problem as manual construction (PATTERN-008). Fail-fast on missing preprocessing, then fix the handler.

**For context-related mistakes:**

```yaml
prevention_action:
  if_context_related:
    # Context limits are fixed - see agent-architecture.md § Context Limit Constants
    primary:
      action: "Improve issue size estimation"
      rationale: "Better estimates prevent exceeding limits"

    secondary:
      action: "Add quality checkpoint at 50% context"
      implementation: |
        At 50% context, pause and verify:
        - Is work quality consistent with early session?
        - Are earlier decisions still being referenced?
        - Should issue be decomposed now?

    tertiary:
      action: "Enhance PLAN.md with explicit checkpoints"
      implementation: |
        Add context-aware milestones to issue plans.
        Each milestone = potential decomposition point.
```

**BLOCKING GATE (M134/A022) - Prevention File Edit Verification:**

BEFORE proceeding to next step, you MUST complete this gate:

1. **List EVERY file you edited in Step 9:**
   - File 1: _______________
   - File 2: _______________
   (Add more lines as needed)

2. **Verification check:**
   - [ ] At least ONE file path is listed above
   - [ ] Each listed file was ACTUALLY edited (not just read)
   - [ ] The edit tool was used, not just planned

3. **BLOCKING CONDITION:**
   If the file list above is BLANK or contains only placeholders:
   - **STOP IMMEDIATELY**
   - Go back to Step 9
   - Make an ACTUAL edit to implement prevention
   - Return here and fill in the file path(s)
   - Only then proceed to next step

4. **Why this gate exists (M134/M135):**
   Recording `prevention_implemented: true` without editing a file is FALSE.
   The prevention_path in the JSON entry MUST match a file listed above.
   If they don't match, the learning system is corrupted.

## Step 9b: Verify Fix Doesn't Introduce Priming (M370)

**MANDATORY: After editing documentation, read `PRIMING-VERIFICATION.md` to verify no new priming introduced.**

Quick check: Do edited files contain concrete values (1.0, SUCCESS) in output formats? Replace with placeholders.

## Step 9c: Check Related Files for Similar Mistakes (M341)

**MANDATORY: After fixing a file, read `RELATED-FILES-CHECK.md` to find and fix similar vulnerabilities.**

Skip only when: fix is unique to one file (typo) or no similar files exist (verified).

## Output Format

Your final message MUST be ONLY this JSON (no other text):

```json
{
  "phase": "prevent",
  "status": "COMPLETE",
  "prevention_type": "code_fix|hook|validation|config|skill|process|documentation",
  "prevention_level": 1,
  "prevention_quality": {
    "verification_type": "positive|negative",
    "fragility": "low|medium|high",
    "catches_variations": true
  },
  "scenario_verified": true,
  "existing_prevention_failed": false,
  "files_modified": [
    "/absolute/path/to/file1",
    "/absolute/path/to/file2"
  ],
  "prevention_description": "What was changed and why",
  "priming_verified": true,
  "related_files_checked": true
}
```
