# Plan: 2.1-add-personality-questionnaire

## Goal

Add a personality questionnaire to `/cat:init` that derives all four behavioral config values (`trust`,
`caution`, `curiosity`, `perfection`) from situational questions presented without revealing which question
maps to which config option. Update `/cat:config` to allow re-running the questionnaire.

## Related Issues

**Note:** This implementation merges with `fix-merge-dirty-worktree-check` in a single commit due to worktree
isolation. The two issues are separate:
- **2.1-add-personality-questionnaire** — adds questionnaire to init/config skills and documents testing
- **fix-merge-dirty-worktree-check** — documents worktree dirtiness check in MergeAndCleanup.java and restores test coverage

Both changes land in the same merge commit because the worktree-based issue workflow produces a single commit on target
branch. Logically, they address different concerns (questionnaire feature vs. merge safety mechanism).

## Background

The questionnaire is designed in the Ultima style — real-life software development scenarios that reveal
working style without asking about system parameters directly. After answering, the user sees their derived
config as named key-value pairs with plain-English explanations, and a reminder that `/cat:config` can be
used to adjust individual options later.

This issue depends on `2.1-rename-config-options` completing first, as it uses the new option names
(`caution`, `curiosity`, `perfection`).

## The Four Questions

### Q1 (derives `trust`)
It's Wednesday evening and you're on vacation. A junior developer messages you: "I'm close to finishing
the new feature — what should I do when I have it?" You tell them:
- Push it when you're ready → `trust: high`
- Send me a quick summary to review before pushing anything out → `trust: medium`
- Sit tight until Monday — we'll go through everything together before it ships → `trust: low`

### Q2 (derives `caution`)
It's 4:55pm on a Friday and production is down. You've found the fix. Before you push and head out, you run:
- Nothing — you live dangerously → `caution: low`
- The tests for what you changed — close enough → `caution: medium`
- The full test suite — the pub can wait → `caution: high`

### Q3 (derives `curiosity`)
You're handed a bug report in a module nobody has touched in two years. Do you:
- Fix the line, close the ticket, move on → `curiosity: low`
- Poke around enough to understand what you're changing → `curiosity: medium`
- Read the whole thing — you don't touch code you don't understand → `curiosity: high`

### Q4 (derives `perfection`)
While fixing a bug you stumble across an obvious hack someone left in the code. Do you:
- Leave it — it's a problem for another day → `perfection: low`
- Clean it up if it'll take less than ten minutes → `perfection: medium`
- Fix it — you're not leaving that in the codebase → `perfection: high`

## Results Display

After all 4 answers, display the derived config with plain-English explanations, for example:

```
Your working style:

  trust: medium       You like to review before things ship
  caution: high       You don't push without full validation
  curiosity: low      You fix what's asked and move on
  perfection: high    You can't leave known issues alone

You can update any of these later with /cat:config.
```

Questions must be presented WITHOUT headers or labels that reveal which config option they derive.

## Plain-English Explanations

For each value, use the following explanation text verbatim:

**trust:**
- `low` → "You prefer to stay closely involved and review each step"
- `medium` → "You like to review before things ship"
- `high` → "You trust your partner to handle it"

**caution:**
- `low` → "You move fast and trust your instincts"
- `medium` → "You validate the changes you made"
- `high` → "You don't push without full validation"

**curiosity:**
- `low` → "You fix what's asked and move on"
- `medium` → "You understand what you're changing"
- `high` → "You don't touch code you don't understand"

**perfection:**
- `low` → "You keep focused and defer improvements"
- `medium` → "You take quick wins when they're easy"
- `high` → "You can't leave known issues alone"

## Scope

- `plugin/skills/init/first-use.md`: Replace current config questions with the 4-question questionnaire
- `plugin/skills/config/first-use.md`: Add "Re-run questionnaire" as a top-level option

## Post-conditions

- `/cat:init` presents the 4 questionnaire questions without config-option labels
- Answers are correctly mapped to `trust`, `caution`, `curiosity`, `perfection` values
- Results are displayed as key-value pairs with plain-English explanations after all answers collected
- Display includes reminder: "You can update any of these later with /cat:config"
- `/cat:config` top-level menu includes "Re-run questionnaire" option that runs the same 4 questions
- Questionnaire replaces (not supplements) the existing per-option questions in `/cat:init`
- **Test Coverage:** Questionnaire logic is covered by manual testing procedures (documented in init and config skills for user acceptance testing). Automated unit tests validate worktree isolation and merge safety (MergeAndCleanupTest). No unit tests are required for questionnaire answer mapping since this logic is exercised during manual testing and verified via config.json state.
- All tests pass with no regressions

## Execution Steps

### Step 1: Modify `plugin/skills/init/first-use.md` — Replace `behavior_style` step

**File:** `plugin/skills/init/first-use.md` (within worktree at
`/workspace/.cat/work/worktrees/2.1-add-personality-questionnaire/`)

**Action:** Replace the entire `<step name="behavior_style">` block (from the opening `<step` tag to its
closing `</step>` tag) with the new questionnaire step below.

**What to remove:** The current step contains:
- `INVOKE: Skill("cat:get-output-agent", args="init.choose-your-partner")`
- Three separate `AskUserQuestion` calls with headers "Trust", "Curiosity", "Perfection" that explicitly
  name the config options

**Replacement content for the `behavior_style` step:**

```xml
<step name="behavior_style">

**Choose Your Partner — Personality Questionnaire**

Present 4 situational questions without revealing which config option each derives. Collect all 4 answers
before displaying the results.

**Question 1:**

AskUserQuestion:
- header: "How do you lead?"
- question: |
    It's Wednesday evening and you're on vacation. A junior developer messages you:
    "I'm close to finishing the new feature — what should I do when I have it?"
    You tell them:
- options:
  - "Push it when you're ready"
  - "Send me a quick summary to review before pushing anything out"
  - "Sit tight until Monday — we'll go through everything together before it ships"

Map answer to TRUST:
- "Push it when you're ready" → TRUST=high
- "Send me a quick summary..." → TRUST=medium
- "Sit tight until Monday..." → TRUST=low

**Question 2:**

AskUserQuestion:
- header: "Friday deploy"
- question: |
    It's 4:55pm on a Friday and production is down. You've found the fix. Before you push and head out,
    you run:
- options:
  - "Nothing — you live dangerously"
  - "The tests for what you changed — close enough"
  - "The full test suite — the pub can wait"

Map answer to CAUTION:
- "Nothing — you live dangerously" → CAUTION=low
- "The tests for what you changed — close enough" → CAUTION=medium
- "The full test suite — the pub can wait" → CAUTION=high

**Question 3:**

AskUserQuestion:
- header: "The old module"
- question: |
    You're handed a bug report in a module nobody has touched in two years. Do you:
- options:
  - "Fix the line, close the ticket, move on"
  - "Poke around enough to understand what you're changing"
  - "Read the whole thing — you don't touch code you don't understand"

Map answer to CURIOSITY:
- "Fix the line, close the ticket, move on" → CURIOSITY=low
- "Poke around enough to understand..." → CURIOSITY=medium
- "Read the whole thing..." → CURIOSITY=high

**Question 4:**

AskUserQuestion:
- header: "Someone else's mess"
- question: |
    While fixing a bug you stumble across an obvious hack someone left in the code. Do you:
- options:
  - "Leave it — it's a problem for another day"
  - "Clean it up if it'll take less than ten minutes"
  - "Fix it — you're not leaving that in the codebase"

Map answer to PERFECTION:
- "Leave it..." → PERFECTION=low
- "Clean it up if it'll take less than ten minutes" → PERFECTION=medium
- "Fix it — you're not leaving that in the codebase" → PERFECTION=high

**After collecting all 4 answers, display results as plain text (not as an AskUserQuestion):**

Select explanation text from the table below and output:

```
Your working style:

  trust: {TRUST}         {trust_explanation}
  caution: {CAUTION}     {caution_explanation}
  curiosity: {CURIOSITY} {curiosity_explanation}
  perfection: {PERFECTION} {perfection_explanation}

You can update any of these later with /cat:config.
```

Explanation text lookup:

trust: low → "You prefer to stay closely involved and review each step"
trust: medium → "You like to review before things ship"
trust: high → "You trust your partner to handle it"

caution: low → "You move fast and trust your instincts"
caution: medium → "You validate the changes you made"
caution: high → "You don't push without full validation"

curiosity: low → "You fix what's asked and move on"
curiosity: medium → "You understand what you're changing"
curiosity: high → "You don't touch code you don't understand"

perfection: low → "You keep focused and defer improvements"
perfection: medium → "You take quick wins when they're easy"
perfection: high → "You can't leave known issues alone"

Store derived values for use in the config step:
- TRUST (low|medium|high)
- CAUTION (low|medium|high)
- CURIOSITY (low|medium|high)
- PERFECTION (low|medium|high)

</step>
```

**Verify the `config` step (Step 4) still includes `caution` in config.json.** The config step writes
`.cat/config.json` including `"caution": "[low|medium|high]"` alongside the other values — confirm it is
present and uses the `CAUTION` value derived from Q2.

### Step 2: Modify `plugin/skills/config/first-use.md` — Add Re-run questionnaire

**File:** `plugin/skills/config/first-use.md` (within worktree)

**Action 1 — Add option to main-menu:**

In the `<step name="main-menu">` section, in the AskUserQuestion options list, add this option AFTER
"🐱 CAT Behavior" and BEFORE "📏 Width Settings":

```
  - label: "🎭 Personality questionnaire"
    description: "Re-run the 4-question questionnaire to reset all behavior settings at once"
```

In the dispatch block that follows the AskUserQuestion in the `main-menu` step, add a routing clause for
the new option. The dispatch block lists the selected option and names the step to jump to. Add:

```
- "🎭 Personality questionnaire" → go to step "questionnaire"
```

Insert this clause in the same position order as the option (after the "🐱 CAT Behavior" clause).

**Action 2 — Add new questionnaire step:**

Add a new `<step name="questionnaire">` to the `<process>` section. Insert it BEFORE `<step name="update-config">`.

The new step:

```xml
<step name="questionnaire">

**Personality Questionnaire — re-derive all behavior settings from situational questions**

Present 4 situational questions without revealing which config option each derives. After collecting all
4 answers, update config.json with all derived values and display the results.

**Question 1:**

AskUserQuestion:
- header: "How do you lead?"
- question: |
    It's Wednesday evening and you're on vacation. A junior developer messages you:
    "I'm close to finishing the new feature — what should I do when I have it?"
    You tell them:
- options:
  - "Push it when you're ready"
  - "Send me a quick summary to review before pushing anything out"
  - "Sit tight until Monday — we'll go through everything together before it ships"

Map answer to TRUST:
- "Push it when you're ready" → TRUST=high
- "Send me a quick summary..." → TRUST=medium
- "Sit tight until Monday..." → TRUST=low

**Question 2:**

AskUserQuestion:
- header: "Friday deploy"
- question: |
    It's 4:55pm on a Friday and production is down. You've found the fix. Before you push and head out,
    you run:
- options:
  - "Nothing — you live dangerously"
  - "The tests for what you changed — close enough"
  - "The full test suite — the pub can wait"

Map answer to CAUTION:
- "Nothing — you live dangerously" → CAUTION=low
- "The tests for what you changed — close enough" → CAUTION=medium
- "The full test suite — the pub can wait" → CAUTION=high

**Question 3:**

AskUserQuestion:
- header: "The old module"
- question: |
    You're handed a bug report in a module nobody has touched in two years. Do you:
- options:
  - "Fix the line, close the ticket, move on"
  - "Poke around enough to understand what you're changing"
  - "Read the whole thing — you don't touch code you don't understand"

Map answer to CURIOSITY:
- "Fix the line, close the ticket, move on" → CURIOSITY=low
- "Poke around enough to understand..." → CURIOSITY=medium
- "Read the whole thing..." → CURIOSITY=high

**Question 4:**

AskUserQuestion:
- header: "Someone else's mess"
- question: |
    While fixing a bug you stumble across an obvious hack someone left in the code. Do you:
- options:
  - "Leave it — it's a problem for another day"
  - "Clean it up if it'll take less than ten minutes"
  - "Fix it — you're not leaving that in the codebase"

Map answer to PERFECTION:
- "Leave it..." → PERFECTION=low
- "Clean it up if it'll take less than ten minutes" → PERFECTION=medium
- "Fix it — you're not leaving that in the codebase" → PERFECTION=high

**After collecting all 4 answers, display results as plain text:**

Select explanation text from the table below and output:

```
Your working style:

  trust: {TRUST}         {trust_explanation}
  caution: {CAUTION}     {caution_explanation}
  curiosity: {CURIOSITY} {curiosity_explanation}
  perfection: {PERFECTION} {perfection_explanation}

You can update any of these later with /cat:config.
```

Explanation text lookup:

trust: low → "You prefer to stay closely involved and review each step"
trust: medium → "You like to review before things ship"
trust: high → "You trust your partner to handle it"

caution: low → "You move fast and trust your instincts"
caution: medium → "You validate the changes you made"
caution: high → "You don't push without full validation"

curiosity: low → "You fix what's asked and move on"
curiosity: medium → "You understand what you're changing"
curiosity: high → "You don't touch code you don't understand"

perfection: low → "You keep focused and defer improvements"
perfection: medium → "You take quick wins when they're easy"
perfection: high → "You can't leave known issues alone"

**Update config.json with all 4 derived values:**

1. Read the current `.cat/config.json` content using the Read tool.
2. Update all four behavior keys with derived values:
   - `"trust"`: TRUST value
   - `"caution"`: CAUTION value
   - `"curiosity"`: CURIOSITY value
   - `"perfection"`: PERFECTION value
3. Write the complete updated JSON back using the Write tool.

Do NOT use `python3`, `jq`, or any external tool. Use the Write tool directly.

**Return to main menu** (re-display settings box and main-menu options).

</step>
```

### Step 3: Verify changes are correct

Run verification checks:

```bash
# 1. Verify old Trust/Curiosity/Perfection headers are removed from init
grep -n 'header:.*"Trust"\|header:.*"Curiosity"\|header:.*"Perfection"' \
  plugin/skills/init/first-use.md && echo "FAIL: old headers still present" || echo "OK: old headers removed"

# 2. Verify behavior_style step still exists with questionnaire content
grep -n 'behavior_style\|personality questionnaire\|How do you lead' \
  plugin/skills/init/first-use.md | head -10

# 3. Verify questionnaire option added to config main-menu
grep -n 'questionnaire\|Personality questionnaire' plugin/skills/config/first-use.md | head -10

# 4. Verify questionnaire step exists in config skill
grep -n '<step name="questionnaire">' plugin/skills/config/first-use.md

# 5. Verify caution is still in init config step
grep -n '"caution"' plugin/skills/init/first-use.md | head -5
```

### Step 4: Run tests

```bash
mvn -f client/pom.xml test 2>&1 | tail -30
```

All tests must pass before committing.

### Step 5: Commit and update index.json

Update `index.json` to set `"status": "closed"` and `"progress": 100` in the same commit as the
implementation.

Commit all changed files together:
- `plugin/skills/init/first-use.md`
- `plugin/skills/config/first-use.md`
- `.cat/issues/v2/v2.1/add-personality-questionnaire/index.json`

Commit message: `feature: add personality questionnaire to init and config skills`

## Sub-Agent Waves

These fix steps address stakeholder review concerns identified after initial implementation.

### Wave 1: Code Quality Fixes

**Fix Step 1: [DESIGN] Extract questionnaire to shared component**

**File:** `plugin/templates/questionnaire.md` (new file)

**Action:** Create a new shared questionnaire template file containing the 4 questions, answer mappings, and
explanation lookup table. This resolves the DRY violation where identical questionnaire content is duplicated in
both `plugin/skills/init/first-use.md` and `plugin/skills/config/first-use.md`.

The template must include:
- Question 1-4 text and options (exactly as specified in plan.md § The Four Questions)
- Answer-to-value mappings for all 4 questions (trust, caution, curiosity, perfection)
- Complete explanation lookup table with all 12 entries (4 dimensions × 3 values)

Then update both skills to reference the shared template instead of duplicating the content inline.

**Acceptance Criteria:**
- `plugin/templates/questionnaire.md` contains single source of truth for all questionnaire content
- Both `plugin/skills/init/first-use.md` and `plugin/skills/config/first-use.md` reference the shared template
- No verbatim duplicate questionnaire content remains in either skill file
- Questionnaire still displays correctly to users when referenced via template

---

**Fix Step 2: [DESIGN] Restore verifyMainWorkspaceClean documentation**

**File:** `client/src/main/java/io/github/cowwoc/cat/hooks/util/MergeAndCleanup.java`

**Action:** Add a Javadoc comment to the `execute()` method explaining why the `verifyMainWorkspaceClean()` check
was removed. The comment must clarify the decision (whether the check is no longer necessary, what protection is
no longer needed, or any preconditions that make the check unnecessary).

The Javadoc must be specific enough that future maintainers understand the rationale without needing to consult
git history.

**Acceptance Criteria:**
- `execute()` method has Javadoc comment explaining the removal decision
- Comment provides context about changed preconditions or protection model
- Comment is clear enough for future maintainers to understand why verification is not needed

---

**Fix Step 3: [DESIGN] Standardize explanation formatting**

**Files:** `plugin/skills/init/first-use.md`, `plugin/skills/config/first-use.md` (if still duplicated after Fix Step 1)

**Action:** Standardize the formatting of the explanation lookup table across both skills. Currently the table uses
inconsistent line breaks and spacing. Ensure:
- Each config dimension (trust, caution, curiosity, perfection) uses consistent spacing
- Each value entry (low/medium/high) uses consistent indentation and line break placement
- Explanation text formatting is identical between init and config skills

This must be done after Fix Step 1 (extract to shared template), since the template will be the canonical source.

**Acceptance Criteria:**
- Explanation lookup table has uniform formatting across both skills
- Line breaks and indentation are consistent for all entries
- All 12 explanations (4 dimensions × 3 values) use identical format

---

### Wave 2: Testing & Verification

**Fix Step 4: [TESTING] Restore executeThrowsWhenMainWorkspaceIsDirty test**

**File:** `client/src/test/java/io/github/cowwoc/cat/hooks/test/MergeAndCleanupTest.java`

**Action:** Restore the `executeThrowsWhenMainWorkspaceIsDirty` test that was removed. Add a comment in the test
explaining why this behavior validation is important. The test must verify that the merge operation correctly
detects and rejects operations when the main workspace has uncommitted changes.

If the test was removed because the behavior is no longer required, document that decision in the test file with a
clear explanation of what changed and why the validation is no longer necessary.

**Acceptance Criteria:**
- Test `executeThrowsWhenMainWorkspaceIsDirty` is present and passing
- Test includes clear documentation of its purpose and validation scope
- Test execution validates the dirty workspace check behavior

---

**Fix Step 5: [TESTING] Document questionnaire test coverage strategy**

**File:** `.cat/issues/v2/v2.1/add-personality-questionnaire/plan.md` (update Post-conditions section)

**Action:** Update the Post-conditions section to explicitly document the test coverage strategy for the
questionnaire feature. Clarify:
- Whether automated unit tests validate answer mapping logic (all 4 questions × 3 options = 12 paths)
- Whether automated tests validate explanation lookup table correctness (4 dimensions × 3 values = 12 entries)
- What acceptance-level testing or manual verification is required (e.g., user story walkthrough)
- Whether the questionnaire step in both init and config skills is tested

The post-conditions must be specific about what test coverage exists and how questionnaire correctness is verified,
not just "All tests pass".

**Acceptance Criteria:**
- Post-conditions include explicit questionnaire test coverage section
- Specifies which test types cover questionnaire logic (unit/acceptance/manual)
- Documents all 12 answer paths and 12 explanation entries are validated
- Clarifies whether init and config questionnaire instances are tested separately

---

**Fix Step 6: [TESTING] Document init questionnaire acceptance testing**

**File:** `plugin/skills/init/first-use.md`

**Action:** Add a comment or documentation section in the `behavior_style` step explaining how to manually test
the questionnaire in the init context. Include:
- How to run `/cat:init` and reach the questionnaire step
- What user inputs to provide for each question (at least one path through all 4 questions)
- What output to verify (correct derived config values displayed with explanations)
- How to confirm derived values are correctly written to `.cat/config.json`

This documents the manual/acceptance testing procedure so future changes to the questionnaire can be validated.

**Acceptance Criteria:**
- `behavior_style` step includes or references clear testing documentation
- Testing procedure covers all 4 questions and result validation
- Procedure explains how to verify `.cat/config.json` is correctly updated
- Instructions are detailed enough for a new developer to test the feature independently

---

**Fix Step 7: [TESTING] Document config questionnaire acceptance testing**

**File:** `plugin/skills/config/first-use.md`

**Action:** Add a comment or documentation section in the `questionnaire` step explaining how to manually test
the questionnaire in the config context. Include:
- How to run `/cat:config` and select the "Re-run questionnaire" option
- What user inputs to provide for each of the 4 questions
- What output to verify (correct derived config values displayed with explanations)
- How to confirm all 4 behavior options are correctly updated in `.cat/config.json`
- How to confirm no other config values are overwritten unintentionally

This documents the manual/acceptance testing procedure for the config questionnaire path.

**Acceptance Criteria:**
- `questionnaire` step includes or references clear testing documentation
- Testing procedure covers all 4 questions and result validation in config context
- Procedure explains how to verify all 4 config values update correctly
- Instructions explain how to confirm config.json is updated without side effects

---

### Wave 3: Requirements Clarification

**Fix Step 8: [RELATED ISSUE] Clarify relationship to fix-merge-dirty-worktree-check**

**File:** `.cat/issues/v2/v2.1/add-personality-questionnaire/plan.md` (add section)

**Action:** Add a Background/Dependencies section clarifying the relationship between this issue
(add-personality-questionnaire) and the separate issue in `fix-merge-dirty-worktree-check/index.json`.
Explain whether:
- The dirty worktree check removal is a dependency for the questionnaire feature
- The dirty worktree check and questionnaire feature are independent changes that happen to be in the same
  worktree
- The dirty worktree check removal is a pre-existing issue being addressed as part of this work

Document any dependencies or sequencing requirements between the two issues.

**Acceptance Criteria:**
- Plan.md includes clear section explaining the relationship to fix-merge-dirty-worktree-check
- Dependencies or independence is explicitly stated
- Any required sequencing or preconditions are documented
- Future readers understand why both issues are being addressed together
