# Plan: add-adversarial-tdd-to-skill-builder

## Goal

Add an Adversarial TDD loop to the skill-builder workflow that iteratively hardens instructions by
alternating between a red-team subagent (finds concrete loopholes) and a blue-team subagent (closes them),
using two separate subagents per round to eliminate cognitive bias. Iterate until no major loopholes remain.

## Parent Requirements

None

## Approaches

### A: Two separate subagents per round (red-team + blue-team)
- **Risk:** LOW
- **Scope:** 1 file (moderate)
- **Description:** Each iteration spawns a fresh red-team agent (no memory of previous rounds) and a fresh
  blue-team agent. Main skill orchestrates rounds. Eliminates anchoring bias — red team cannot subconsciously
  spare loopholes it invented, blue team cannot over-fit to only the attacks it was shown.

### B: Single agent playing both roles alternately
- **Risk:** MEDIUM
- **Scope:** 1 file (minimal)
- **Description:** One agent iterates, switching roles internally. Simpler but known bias risk: agent knows
  its own attack vectors and may close only exactly those, leaving adjacent loopholes open. Rejected.

> **Chosen: Approach A.** Separate subagents prevent anchoring bias and are the standard "red team / blue team"
> security practice. The added overhead (one extra Task spawn per round) is acceptable for a quality gate.

## Risk Assessment

- **Risk Level:** LOW
- **Concerns:** Loop could run indefinitely if termination criteria are too lenient; red team could be too
  creative and flag acceptable edge cases as "major" loopholes.
- **Mitigation:** Explicit termination criteria (no major loopholes = CRITICAL or HIGH severity) and maximum
  iteration cap (3 rounds). Red-team output must include concrete defeat strategies (specific text/behavior),
  not vague concerns.

## Files to Modify

- `plugin/skills/skill-builder-agent/first-use.md` — add Adversarial TDD loop as Steps N+1 through N+3
  after the existing backward-chaining steps and before the final output step

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Add Adversarial TDD loop to `plugin/skills/skill-builder-agent/first-use.md`:
  - Insert after the last existing numbered step (find by searching for the final `### Step N:` heading)
  - Add the following new steps before any final "Output" or "Verification" section:

  **Step N+1: Adversarial TDD Loop**

  ```
  ### Step N+1: Adversarial TDD Loop

  After the forward execution steps are drafted, harden the instructions using alternating red-team
  and blue-team subagents. Run until no major loopholes remain or 3 iterations complete.

  **Why two separate subagents per round:**
  A single agent playing both roles anchors on its own attack vectors — it knows exactly which
  loopholes it invented, so it subconsciously over-fits the defense to those attacks and under-defends
  against variations. Separate subagents with fresh context eliminate this bias.

  **Termination criteria:** No major loopholes = red-team finds no CRITICAL or HIGH severity issues.
  LOW/MEDIUM concerns may remain if they require disproportionate instruction complexity to close.

  **Loop (max 3 iterations):**

  **Iteration setup:** Extract the current draft instructions from the most recent step output.

  **Red-team subagent** (spawn fresh, no prior round context):
  ```
  Task tool:
    description: "Red-team: find loopholes in instructions (round N)"
    subagent_type: "general-purpose"
    prompt: |
      You are a red-team agent. Your job: find concrete ways to defeat or circumvent these instructions.

      ## Instructions to Attack
      {CURRENT_INSTRUCTIONS}

      ## Your Task
      For each loophole you find, provide:
      1. **Loophole name**: brief slug (e.g., "bash-file-write-bypass")
      2. **Severity**: CRITICAL | HIGH | MEDIUM | LOW
      3. **Attack**: exact agent reasoning or action that defeats the rule (be specific — quote
         undefined terms, name unlisted tools, describe the rationalization the agent would use)
      4. **Evidence**: why the current instructions permit this (quote the permissive text or
         identify the missing prohibition)

      Do NOT suggest fixes. Do NOT be vague. If you cannot find a concrete attack for a concern,
      do not include it.

      ## Return Format
      ```json
      {
        "loopholes": [
          {
            "name": "bash-file-write-bypass",
            "severity": "CRITICAL",
            "attack": "Agent uses Bash `sed -i` to modify file, bypassing Edit/Write prohibition",
            "evidence": "Rule says 'MUST NOT use Edit or Write tools' but does not mention Bash"
          }
        ],
        "major_loopholes_found": true
      }
      ```
      Set major_loopholes_found to true if any CRITICAL or HIGH severity loopholes exist.
  ```

  If red-team returns `major_loopholes_found: false`: STOP loop, instructions are sufficiently hardened.

  **Blue-team subagent** (spawn fresh, no prior round context):
  ```
  Task tool:
    description: "Blue-team: close loopholes in instructions (round N)"
    subagent_type: "general-purpose"
    prompt: |
      You are a blue-team agent. Your job: close specific loopholes in these instructions.

      ## Current Instructions
      {CURRENT_INSTRUCTIONS}

      ## Loopholes to Close
      {RED_TEAM_FINDINGS_JSON}

      ## Your Task
      For each loophole, revise the instructions to close it:
      - Add explicit prohibitions for unlisted tools/techniques
      - Define undefined terms that enabled rationalization
      - Convert permissive-by-omission lists to exhaustive lists with "nothing else" clauses
      - Add scope boundaries that prevent context-dependent bypass

      Rules:
      - Minimal changes: close the specific loophole, do not rewrite unrelated sections
      - Preserve all existing correct guidance
      - Do NOT add so many caveats that the instructions become unreadable

      ## Return Format
      Return the complete revised instructions as a markdown code block, followed by:
      ```json
      {
        "changes": [
          {"loophole": "bash-file-write-bypass", "fix": "Added Bash to the list of prohibited tools"}
        ]
      }
      ```
  ```

  Update CURRENT_INSTRUCTIONS with blue-team output. Increment round counter.
  If round < 3 and major_loopholes_found: continue to next iteration.
  ```

  - Update STATE.md: status closed, progress 100%
    - Files: `.claude/cat/issues/v2/v2.1/add-adversarial-tdd-to-skill-builder/STATE.md`

## Post-conditions

- [ ] `plugin/skills/skill-builder-agent/first-use.md` contains a new step titled "Adversarial TDD Loop"
      after the existing backward-chaining forward steps
- [ ] The step spawns separate red-team and blue-team subagents (not a single agent playing both roles)
- [ ] Red-team prompt requires concrete defeat strategies with severity ratings (not vague concerns)
- [ ] Blue-team prompt receives red-team findings and returns revised instructions
- [ ] Termination criteria: no CRITICAL or HIGH loopholes, or 3-iteration cap reached
- [ ] E2E: Run `/cat:skill-builder` on a sample skill; confirm the adversarial loop step executes,
      red-team returns loopholes JSON, blue-team returns revised instructions, and loop terminates
