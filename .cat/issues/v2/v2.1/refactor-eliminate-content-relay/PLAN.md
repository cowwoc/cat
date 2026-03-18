# Plan: refactor-eliminate-content-relay

## Current State

The main agent reads PLAN.md content and pastes it into subagent prompts, violating the Subagent Content Relay
Anti-Pattern documented in `optimize-execution/first-use.md` (lines 355-385). Two files have this problem:

1. **`work-with-issue-agent/first-use.md`** ��� Lines 148-154 extract `ISSUE_GOAL` and `EXECUTION_STEPS` from PLAN.md
   via sed. Lines 257-261 inline `${ISSUE_GOAL}` and the full Execution Waves section into subagent prompts. Lines
   362-363 repeat this for parallel wave prompts. The main agent only needs two lightweight Bash operations from
   PLAN.md: skill reference detection (line 157-163) and wave counting (line 191). Everything else is content relay.

2. **`delegate-agent/first-use.md`** ��� Line 238 instructs the main agent to "Read all relevant code - Complete
   exploration before delegating." This contradicts the two-stage planning subagent pattern defined in the same file
   (lines 164-194) where Stage 1 planning subagents produce approach outlines and Stage 2 produces the detailed spec.
   Planning subagents do the exploration, not the main agent.

## Target State

1. **work-with-issue-agent** passes the PLAN.md file path to subagents. Subagents read PLAN.md themselves. Main agent
   retains only the lightweight Bash grep for skill references and wave count.

2. **delegate-agent** removes "Read all relevant code" from main agent responsibilities and correctly describes
   planning subagents as responsible for code exploration in the two-stage pattern.

## Satisfies

None ��� aligns with existing optimize-execution guidance

## Risk Assessment

- **Risk Level:** MEDIUM
- **Breaking Changes:** Subagent prompt format changes; subagents must now read PLAN.md themselves
- **Mitigation:** Subagents already have Read tool access; PLAN.md path is passed as a variable they can read

## Files to Modify

- `plugin/skills/work-with-issue-agent/first-use.md` ��� replace inlined content with path references
- `plugin/skills/delegate-agent/first-use.md` ��� update Main Agent Responsibilities section

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Update `plugin/skills/work-with-issue-agent/first-use.md`:
  - Files: `plugin/skills/work-with-issue-agent/first-use.md`
  - Remove `ISSUE_GOAL` sed extraction (line 154) ��� main agent no longer needs this variable
  - Keep `EXECUTION_STEPS` sed extraction ONLY for the skill reference scan (lines 157-163). Alternatively,
    replace with a direct grep on `$PLAN_MD` for skill patterns, eliminating the sed entirely
  - Keep the wave count grep (line 191) ��� this is already lightweight
  - Replace `${ISSUE_GOAL}` in single-subagent prompt (line 258) with:
    `PLAN_MD_PATH: ${PLAN_MD}` and instruct the subagent to read the Goal section itself
  - Replace `[Include the ## Execution Waves section from PLAN.md]` (line 261) with:
    instruction for the subagent to read Execution Waves from `${PLAN_MD}` directly
  - Apply same changes to the parallel wave prompt template (lines 362-363)
  - Keep `${ISSUE_GOAL}` in approval gate questions (lines 1398, 1410) since the main agent
    needs a brief goal summary for the user ��� extract this via a single-line Bash grep, not a
    full Read into LLM context
  - Update the "Pass PLAN.md execution steps verbatim" guidance (line 205) to reflect that
    subagents now read PLAN.md themselves

- Update `plugin/skills/delegate-agent/first-use.md`:
  - Files: `plugin/skills/delegate-agent/first-use.md`
  - Remove item 1 from Main Agent Responsibilities: "Read all relevant code" (line 238)
  - Add clarification that planning subagents (Stage 1-2, lines 164-194) handle code exploration
  - Main agent responsibilities should focus on: choosing approach from Stage 1, passing selection
    to Stage 2, and handing the resulting PLAN.md path to the implementation subagent

- Commit: `refactor: eliminate content relay anti-pattern from work-with-issue and delegate skills`

## Post-conditions

- [ ] work-with-issue-agent subagent prompts receive PLAN.md path instead of inlined ISSUE_GOAL and Execution Waves
- [ ] `${ISSUE_GOAL}` variable binding removed from subagent prompt construction (kept only for approval gate display)
- [ ] Execution Waves verbatim inlining removed from subagent prompt templates
- [ ] Main agent extracts skill references and wave count via lightweight Bash grep only
- [ ] delegate-agent "Main Agent Responsibilities" no longer instructs "Read all relevant code"
- [ ] delegate-agent correctly describes planning subagents as responsible for code exploration
- [ ] No behavioral regression: issue execution workflow produces same outcomes
- [ ] E2E: Execute /cat:work on a test issue and confirm subagent receives PLAN.md path (not inlined content)
