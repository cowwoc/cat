<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# CAT Configuration

Interactive configuration wizard to customize CAT settings.

## Purpose

Display current CAT configuration and guide users through modifying their preferences via an interactive wizard.
Supports personality settings (trust, caution, curiosity, perfection, verbosity), width settings, completion
workflow, min severity, and version pre/post-conditions.

## Procedure

### Step 1: Read current configuration

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/get-config-output" effective
```

If the command fails, inform the user to run `/cat:init` first.

Extract the following values from the JSON output for use in subsequent steps:
- `trust`, `caution`, `curiosity`, `perfection`, `verbosity`
- `fileWidth`, `displayWidth`, `completionWorkflow`, `minSeverity`

### Step 2: Display settings box

**MANDATORY - Display-Before-Prompt Protocol:**

BLOCKING REQUIREMENT: You MUST output a visual display box BEFORE calling AskUserQuestion.

INVOKE: Skill("cat:get-output-agent", args="config.settings")

### Step 3: Present main menu

**CHECKPOINT: Verify settings box was displayed in Step 2. If not, STOP and output it now.**

**Present main menu using AskUserQuestion:**

Show current values in descriptions using data from Step 1.

- header: "Settings"
- question: "What would you like to configure?"
- options:
  - label: "🎭 Personality"
    description: "Currently: {trust} · {caution} · {curiosity} · {perfection} · {verbosity}"
  - label: "📏 Width Settings"
    description: "Currently: file={fileWidth || 120} · display={displayWidth || 120} characters"
  - label: "🔀 Completion Workflow"
    description: "Currently: {completionWorkflow || 'merge'}"
  - label: "📈 Min Severity"
    description: "Currently: {minSeverity || 'low'}"
  - label: "📊 Version Conditions"
    description: "Pre/post-conditions for versions"

Dispatch based on selection:
- "🎭 Personality" → go to Step 4 (personality menu)
- "📏 Width Settings" → go to Step 6 (width settings)
- "🔀 Completion Workflow" → go to Step 7 (completion workflow)
- "📈 Min Severity" → go to Step 8 (min severity)
- "📊 Version Conditions" → go to Step 9 (version conditions)

If user selects "Other" and types "done", "exit", or "back", proceed to Step 11 (exit).

**Note:** Context limits are fixed and not configurable. See agent-architecture.md § Context Limit Constants.

### Step 4: Personality menu

**🎭 Personality — derive or set all personality settings**

AskUserQuestion:
- header: "Personality"
- question: "How would you like to configure your personality settings?"
- options:
  - label: "🧭 Guided setup"
    description: "Answer 5 scenario questions to derive all personality settings at once"
  - label: "⚙️ Manual settings"
    description: "Set trust, caution, curiosity, perfection, and verbosity directly"
  - label: "← Back"
    description: "Return to main menu"

Dispatch based on selection:
- "🧭 Guided setup" → go to Step 5 (questionnaire)
- "⚙️ Manual settings" → go to Step 5a (manual settings)
- "← Back" → return to Step 3 (main menu)

### Step 5: Questionnaire

**Personality Questionnaire — re-derive all behavior settings from situational questions**

Present 5 situational questions without revealing which config option each derives. After collecting all
5 answers, update config.json with all derived values and display the results.

See `plugin/templates/questionnaire.md` for question content, answer mappings, and explanation text.

**Question 1:**

AskUserQuestion:
- header: "How do you lead? (1/5)"
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
- header: "Friday deploy (2/5)"
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
- header: "The old module (3/5)"
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
- header: "Someone else's mess (4/5)"
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

**Question 5:**

AskUserQuestion:
- header: "The code review (5/5)"
- question: |
    You're reviewing a PR with a tricky bug. You'd prefer CAT to:
- options:
  - "Give you the short answer"
  - "Walk you through the reasoning"
  - "Explain everything, including what it ruled out"

Map answer to VERBOSITY:
- "Give you the short answer" → VERBOSITY=low
- "Walk you through the reasoning" → VERBOSITY=medium
- "Explain everything, including what it ruled out" → VERBOSITY=high

**After collecting all 5 answers, display results as plain text:**

Select explanation text from the table in `plugin/templates/questionnaire.md` and output:

```
Your working style:

  trust: {TRUST}         {trust_explanation}
  caution: {CAUTION}     {caution_explanation}
  curiosity: {CURIOSITY} {curiosity_explanation}
  perfection: {PERFECTION} {perfection_explanation}
  verbosity: {VERBOSITY} {verbosity_explanation}

You can update any of these later with /cat:config.
```

**Update config.json with all 5 derived values:**

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/update-config" \
  "trust={TRUST}" "caution={CAUTION}" "curiosity={CURIOSITY}" \
  "perfection={PERFECTION}" "verbosity={VERBOSITY}"
```

Where each value is the lowercase derived value (e.g., "low", "medium", "high").
If the command outputs `{"status":"ERROR",...}`, display the error message.

**Manual Testing:**
To test the questionnaire behavior manually:
1. Run `/cat:config` on an existing CAT project
2. Select "🎭 Personality" then "🧭 Guided setup" from the menus
3. Verify all 5 questions appear in sequence
4. Provide one answer to each question
5. Verify the result display shows all 5 values with correct explanations (verify against template)
6. Confirm config.json was updated with all 5 values

After updating config.json, return to Step 2 (display settings box and main menu).

### Step 5a: Manual settings

**⚙️ Manual Settings — set all personality settings directly**

Present two pages. Read current values from config (loaded in Step 1) to mark the current selection
in each option description with " (current)".

**Page 1 of 2 — Behavior:**

AskUserQuestion:
- header: "Behavior (1/2)"
- question: "Set your behavior preferences:"
- questions array (4 questions):
  1. question: "Trust — How much autonomy should CAT have?"
     header: "Trust"
     options:
     - label: "Low"
       description: "Stops frequently to request approval{' (current)' if trust=='low'}"
     - label: "Medium"
       description: "Auto-continues between issues, stops at approval gates{' (current)' if trust=='medium'}"
     - label: "High"
       description: "Fully autonomous, skips approval gates{' (current)' if trust=='high'}"
  2. question: "Caution — How thoroughly should CAT test before merging?"
     header: "Caution"
     options:
     - label: "Low"
       description: "Compile only (fastest feedback){' (current)' if caution=='low'}"
     - label: "Medium"
       description: "Compile, unit tests, and issue-specific E2E tests (default){' (current)' if caution=='medium'}"
     - label: "High"
       description: "Compile, unit tests, and all E2E tests (maximum confidence){' (current)' if caution=='high'}"
  3. question: "Curiosity — How broadly should CAT run stakeholder review?"
     header: "Curiosity"
     options:
     - label: "Low"
       description: "Skip automatic stakeholder review; review only runs if explicitly invoked{' (current)' if curiosity=='low'}"
     - label: "Medium"
       description: "Run automatic stakeholder review scoped to changed files and direct dependencies{' (current)' if curiosity=='medium'}"
     - label: "High"
       description: "Run automatic stakeholder review with holistic system integration scope{' (current)' if curiosity=='high'}"
  4. question: "Perfection — How much should CAT pursue perfection?"
     header: "Perfection"
     options:
     - label: "Low"
       description: "Stay focused on the primary goal, defer tangential improvements{' (current)' if perfection=='low'}"
     - label: "Medium"
       description: "Fix issues that are easy to address, defer complex ones{' (current)' if perfection=='medium'}"
     - label: "High"
       description: "Fix every issue encountered, even if tangential to the primary goal{' (current)' if perfection=='high'}"

Map answers: Low → "low", Medium → "medium", High → "high" for each respective setting.

**Page 2 of 2 — Communication:**

AskUserQuestion:
- header: "Communication (2/2)"
- question: "Set your communication preference:"
- questions array (1 question):
  1. question: "Verbosity — How much should CAT explain its reasoning?"
     header: "Verbosity"
     options:
     - label: "Low"
       description: "Progress banners and errors only — no reasoning, no summaries beyond phase markers{' (current)' if verbosity=='low'}"
     - label: "Medium"
       description: "Phase-transition summaries — what was done, key decisions{' (current)' if verbosity=='medium'}"
     - label: "High"
       description: "Full reasoning — alternatives considered, tradeoffs noted, rationale for each decision{' (current)' if verbosity=='high'}"

Map answer: Low → "low", Medium → "medium", High → "high" for verbosity.

**After collecting all answers from both pages, update config.json:**

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/update-config" \
  "trust={trust_value}" "caution={caution_value}" "curiosity={curiosity_value}" \
  "perfection={perfection_value}" "verbosity={verbosity_value}"
```

Where `{trust_value}`, `{caution_value}`, etc. are the lowercase values selected above (e.g., "low", "medium", "high").
If the command outputs `{"status":"ERROR",...}`, display the error message and do not proceed.

INVOKE: Skill("cat:get-output-agent", args="config.setting-updated personality {old_summary} {new_summary}")

Where `{old_summary}` is the previous values joined as "trust:X caution:X curiosity:X perfection:X verbosity:X"
and `{new_summary}` is the new values in the same format.

Return to Step 3 (main menu).

### Step 6: Width settings

**📏 Width Settings:**

AskUserQuestion:
- header: "Width Settings"
- question: "Which width would you like to configure?"
- options:
  - label: "📄 File Width"
    description: "Currently: {fileWidth || 120} — wrapping for content written to files (e.g., markdown docs)"
  - label: "🖥️ Display Width"
    description: "Currently: {displayWidth || 120} — wrapping for terminal rendering (e.g., diffs, status boxes)"
  - label: "← Back"
    description: "Return to main menu"

**If File Width selected:**

AskUserQuestion:
- header: "File Width"
- question: "What device are you primarily writing files on?"
- options:
  - label: "🖥️ Desktop/Laptop (Default)"
    description: "120 characters - standard for wide monitors and markdown editors"
  - label: "⚙️ Custom value"
    description: "Enter a specific width (40-200)"
  - label: "← Back"
    description: "Return to width menu"

Map selections:
- Desktop/Laptop → `fileWidth: 120`
- Custom → prompt for value, validate 40-200

**If Display Width selected:**

AskUserQuestion:
- header: "Display Width"
- question: "What device are you primarily using?"
- options:
  - label: "🖥️ Desktop/Laptop (Default)"
    description: "120 characters - optimized for wide monitors"
  - label: "📱 Mobile"
    description: "50 characters - optimized for phones and narrow screens"
  - label: "⚙️ Custom value"
    description: "Enter a specific width (40-200)"
  - label: "← Back"
    description: "Return to width menu"

Map selections:
- Desktop/Laptop → `displayWidth: 120`
- Mobile → `displayWidth: 50`
- Custom → prompt for value, validate 40-200

**If Custom value selected (for either):**

AskUserQuestion:
- header: "Custom Width"
- question: "Enter width (40-200):"
- options: ["← Back"]

Validate input is a number between 40-200. If invalid, show error and re-prompt.

**Update config:**

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/update-config" "fileWidth={value}"
```
(or `displayWidth={value}` as appropriate). Replace `{value}` with the integer value selected.
If the command outputs `{"status":"ERROR",...}`, display the error message.

Return to Step 3 (main menu) after updating.

### Step 7: Completion workflow

**🔀 Completion Workflow selection:**

AskUserQuestion:
- header: "Completion Workflow"
- question: "How should completed issues be integrated? (Current: {completionWorkflow || 'merge'})"
- options:
  - label: "🔀 Merge (Default)"
    description: "Merge source branch directly to target branch after approval"
  - label: "📝 Pull Request"
    description: "Create a PR instead of merging directly"
  - label: "← Back"
    description: "Return to main menu"

Map: Merge → `completionWorkflow: "merge"`, Pull Request → `completionWorkflow: "pr"`

**Update config:**

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/update-config" "completionWorkflow={value}"
```
Replace `{value}` with the mapped value ("merge" or "pr").
If the command outputs `{"status":"ERROR",...}`, display the error message.

Return to Step 3 (main menu) after updating.

### Step 8: Min severity

**📈 Min Severity configuration:**

Min severity controls the minimum concern severity level that is visible at all. Concerns below this level are
silently ignored — not fixed, not deferred, not tracked.

AskUserQuestion:
- header: "Min Severity — Concern Visibility"
- question: "Minimum severity level to make visible? (Current: {minSeverity || 'low'})"
- options:
  - label: "LOW (Default)"
    description: "All concerns visible (CRITICAL, HIGH, MEDIUM, and LOW)"
  - label: "MEDIUM"
    description: "MEDIUM, HIGH, and CRITICAL concerns visible; LOW are ignored"
  - label: "HIGH"
    description: "HIGH and CRITICAL concerns visible; MEDIUM and LOW are ignored"
  - label: "CRITICAL"
    description: "Only CRITICAL concerns visible; HIGH, MEDIUM, and LOW are ignored"
  - label: "← Back"
    description: "Return to main menu"

Map selections:
- LOW → `minSeverity: "low"`
- MEDIUM → `minSeverity: "medium"`
- HIGH → `minSeverity: "high"`
- CRITICAL → `minSeverity: "critical"`

**Update config:**

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/update-config" "minSeverity={value}"
```
Replace `{value}` with the mapped severity ("low", "medium", "high", or "critical").
If the command outputs `{"status":"ERROR",...}`, display the error message.

Return to Step 3 (main menu) after updating.

### Step 9: Version conditions

**📊 Version Conditions configuration:**

INVOKE: Skill("cat:get-output-agent", args="config.versions")

**Step 9.1: Select version to configure**

First, scan for available versions:
```bash
ls -1d .cat/v[0-9]*/v[0-9]*.[0-9]* 2>/dev/null | \
  sed 's|.cat/v[0-9]*/v||' | sort -V
```

Determine current minor version from roadmap.md (first non-completed).

Use AskUserQuestion:
- header: "Select Version"
- question: "Which version's conditions do you want to configure?"
- options:
  - "v{X}.{Y-1} - Previous minor" (if exists)
  - "v{X}.{Y} - Current minor" (highlighted)
  - "v{X}.{Y+1} - Next minor" (if exists)
  - "Enter version number" - Custom input

**If "Enter version number":**

Use AskUserQuestion:
- header: "Version"
- question: "Enter the version number (e.g., 0.5 or just 0 for major):"
- options: ["← Back"]

Parse input to determine if major (single digit) or minor (X.Y format).

**Step 9.2: Display current conditions**

Read the plan.md for selected version:
```bash
cat .cat/issues/v{major}/v{major}.{minor}/plan.md 2>/dev/null || \
cat .cat/issues/v{major}/plan.md 2>/dev/null
```

Extract the `## Pre-conditions` and `## Post-conditions` sections.

INVOKE: Skill("cat:get-output-agent", args="config.conditions-for-version {version} {preconditions} {postconditions}")

Replace `{version}`, `{preconditions}`, and `{postconditions}` with actual values extracted from plan.md.
Empty conditions should be represented as "(none)".

**Step 9.3: Choose action**

Use AskUserQuestion:
- header: "Action"
- question: "What would you like to do?"
- options:
  - label: "Edit pre-conditions"
    description: "Change when work can start"
  - label: "Edit post-conditions"
    description: "Change completion criteria"
  - label: "View another version"
    description: "Select a different version"
  - label: "← Back"
    description: "Return to main menu"

**Step 9.4a: Edit pre-conditions**

Use AskUserQuestion:
- header: "Pre-conditions"
- question: "Select entry conditions (current: {current conditions}):"
- multiSelect: true
- options:
  - "Previous version complete" - sequential dependency
  - "Specific issue(s) complete" - named issues required
  - "Specific version(s) complete" - named versions required
  - "Manual approval required" - explicit sign-off

If "Specific issue(s) complete":
- Ask: "Which issue(s)? (e.g., 0.5-design-review, comma-separated)"

If "Specific version(s) complete":
- Ask: "Which version(s)? (e.g., 0.3, 0.4, comma-separated)"

**Step 9.4b: Edit post-conditions**

Use AskUserQuestion:
- header: "Post-conditions"
- question: "Select exit conditions (current: {current conditions}):"
- multiSelect: true
- options:
  - "All issues complete" - every issue in version done
  - "Specific issue(s) complete" - only named issues required
  - "Tests passing" - test suite must pass
  - "Manual sign-off" - explicit approval

If "Specific issue(s) complete":
- Ask: "Which issue(s)? (comma-separated)"

**Step 9.5: Update plan.md**

Read the version's plan.md, update the `## Pre-conditions` and `## Post-conditions` sections:

```markdown
## Pre-conditions
- {condition 1}
- {condition 2}

## Post-conditions
- {condition 1}
- {condition 2}
```

If the plan.md doesn't have these sections, insert them after `## Focus` or `## Vision`.

Write the updated plan.md using the Write tool.

**Step 9.6: Confirm and loop**

INVOKE: Skill("cat:get-output-agent", args="config.conditions-updated {version} {new_preconditions} {new_postconditions}")

Replace `{version}`, `{new_preconditions}`, `{new_postconditions}` with actual values.

Return to Step 9.3 (Choose action) to allow further edits or navigation.

### Step 10: Confirm change

**Confirm change and return to parent menu:**

INVOKE: Skill("cat:get-output-agent", args="config.setting-updated {setting_name} {old_value} {new_value}")

Replace `{setting_name}`, `{old_value}`, `{new_value}` with actual values.

**After confirming**: Return to the **parent menu** and re-display its options.

### Step 11: Exit

**Exit screen:**

If changes were made:

INVOKE: Skill("cat:get-output-agent", args="config.saved")

If no changes:

INVOKE: Skill("cat:get-output-agent", args="config.no-changes")

## Verification

<success_criteria>

- [ ] Current configuration displayed
- [ ] User navigated wizard successfully
- [ ] Settings updated in config.json using the update-config binary
- [ ] Version conditions viewable and editable via wizard
- [ ] Gate changes saved to version plan.md files
- [ ] Changes confirmed with before/after values
- [ ] Personality submenu presents Guided setup and Manual settings options
- [ ] Manual settings wizard covers all 5 personality settings across two pages
- [ ] Current values highlighted in Manual settings options

</success_criteria>

<configuration_reference>

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `trust` | string | "medium" | How much autonomy CAT has to act independently |
| `caution` | string | "medium" | How cautious the agent is when validating changes |
| `curiosity` | string | "medium" | How curious the agent is when investigating problems and exploring solutions |
| `perfection` | string | "medium" | How much the agent pursues perfection (high=fix all issues, low=stay focused on primary goal) |
| `verbosity` | string | "medium" | How much CAT explains its reasoning during task execution |
| `completionWorkflow` | string | "merge" | Issue completion behavior (merge or PR) |
| `minSeverity` | string | "low" | Minimum severity level for concerns to be visible at all |
| `fileWidth` | integer | 120 | Line width for content written to files (e.g., markdown docs) |
| `displayWidth` | integer | 120 | Line width for terminal rendering (e.g., diffs, status boxes) |

**Context Limits:** Fixed values, not configurable. See agent-architecture.md § Context Limit Constants.

### Trust Values
- `low` — Stops frequently to request approval.
- `medium` — Auto-continues between issues, stops at approval gates.
- `high` — Fully autonomous, skips approval gates.

### Caution Values
- `low` — Compile only (fastest feedback).
- `medium` — Compile, unit tests, and issue-specific E2E tests (default).
- `high` — Compile, unit tests, and all E2E tests (maximum confidence).

### Curiosity Values
- `low` — Skip automatic stakeholder review; review only runs if explicitly invoked.
- `medium` — Run automatic stakeholder review scoped to changed files and direct dependencies.
- `high` — Run automatic stakeholder review with holistic system integration scope.

### Perfection Values
- `high` — Fix every issue encountered, even if tangential to the primary goal.
- `medium` — Fix issues that are easy to address, defer complex ones.
- `low` — Stay focused on the primary goal, defer tangential improvements.

### Verbosity Values
- `low` — Progress banners and errors only.
- `medium` — Phase-transition summaries — what was done, key decisions.
- `high` — Full reasoning — alternatives considered, tradeoffs noted, rationale for each decision.

### Completion Workflow Values
- `merge` — Merge source branch directly to target branch after approval (default).
- `pr` — Create a pull request instead of merging directly.

### Min Severity Values
- `low` — All concerns visible (CRITICAL, HIGH, MEDIUM, and LOW) (default).
- `medium` — MEDIUM, HIGH, and CRITICAL concerns visible; LOW are ignored.
- `high` — HIGH and CRITICAL concerns visible; MEDIUM and LOW are ignored.
- `critical` — Only CRITICAL concerns visible; HIGH, MEDIUM, and LOW are ignored.

</configuration_reference>
