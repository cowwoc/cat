<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# CAT Configuration

Interactive configuration wizard to customize CAT settings.

<objective>

Interactive configuration wizard to customize CAT settings. Displays current configuration and guides
users through modifying their preferences.

</objective>

<process>

<step name="read-config">

**Read current configuration:**

```bash
cat .cat/config.json
```

If file doesn't exist, inform user to run `/cat:init` first.

</step>

<step name="display-settings">

**MANDATORY - Display-Before-Prompt Protocol:**

BLOCKING REQUIREMENT: You MUST output a visual display box BEFORE calling AskUserQuestion.

INVOKE: Skill("cat:get-output-agent", args="config.settings")

The user wants an interactive wizard — go directly to the main-menu.

Continue to step: main-menu

</step>

<step name="main-menu">

**CHECKPOINT: Verify settings box was displayed in previous step. If not, STOP and output it now.**

**Present main menu using AskUserQuestion:**

Show current values in descriptions using data from read-config step.

- header: "Settings"
- question: "What would you like to configure?"
- options:
  - label: "🐱 CAT Behavior"
    description: "Currently: {trust} · {caution} · {curiosity} · {perfection}"
  - label: "📏 Width Settings"
    description: "Currently: file={fileWidth || 120} · display={displayWidth || 120} characters"
  - label: "🔀 Completion Workflow"
    description: "Currently: {completionWorkflow || 'merge'}"
  - label: "📈 Min Severity"
    description: "Currently: {minSeverity || 'low'}"
  - label: "📊 Version Conditions"
    description: "Pre/post-conditions for versions"

If user selects "Other" and types "done", "exit", or "back", proceed to exit step.

**Note:** Context limits are fixed and not configurable. See agent-architecture.md § Context Limit Constants.

</step>


<step name="cat-behavior">

**🐱 CAT Behavior selection:**

**MANDATORY - Display behavior summary BEFORE prompting:**

INVOKE: Skill("cat:get-output-agent", args="config.settings")

Then AskUserQuestion:
- header: "Behavior"
- question: "Which setting would you like to adjust?"
- options (show current values in descriptions):
  - label: "🤝 Trust"
    description: "Currently: {trust || 'medium'}"
  - label: "✅ Caution"
    description: "Currently: {caution || 'medium'}"
  - label: "🔍 Curiosity"
    description: "Currently: {curiosity || 'medium'}"
  - label: "⏳ Perfection"
    description: "Currently: {perfection || 'medium'}"
  - label: "🗣️ Verbosity"
    description: "Currently: {verbosity || 'medium'}"
  - label: "← Back"
    description: "Return to main menu"

</step>

<step name="trust">

**🤝 Trust — How much autonomy CAT has to act independently**

Display current setting, then AskUserQuestion:
- header: "Trust"
- question: "How much autonomy should CAT have when making decisions? (Current: {trust || 'medium'})"
- options:
  - label: "Low"
    description: "Stops frequently to request approval"
  - label: "Medium (Default)"
    description: "Auto-continues between issues, stops at approval gates"
  - label: "High"
    description: "Fully autonomous, skips approval gates"
  - label: "← Back"
    description: "Return to behavior menu"

Map: Low → `trust: "low"`, Medium → `trust: "medium"`, High → `trust: "high"`

</step>

<step name="caution">

**✅ Caution — How cautiously the agent validates changes**

Display current setting, then AskUserQuestion:
- header: "Caution"
- question: "How cautious should the agent be when validating changes? (Current: {caution || 'medium'})"
- options:
  - label: "Low"
    description: "Compile only (fastest feedback)"
  - label: "Medium (Default)"
    description: "Compile and unit tests"
  - label: "High"
    description: "Compile, unit tests, and E2E tests (maximum confidence)"
  - label: "← Back"
    description: "Return to behavior menu"

Map: Low → `caution: "low"`, Medium → `caution: "medium"`, High → `caution: "high"`

</step>

<step name="curiosity">

**🔍 Curiosity — How curious the agent is when investigating problems and exploring solutions**

Display current setting, then AskUserQuestion:
- header: "Curiosity"
- question: "How curious should CAT be when investigating problems? (Current: {curiosity || 'medium'})"
- options:
  - label: "Low"
    description: "Investigate the issue as stated, no extra exploration"
  - label: "Medium (Default)"
    description: "Notice related issues while investigating"
  - label: "High"
    description: "Actively explore solutions and alternatives"
  - label: "← Back"
    description: "Return to behavior menu"

Map: Low → `curiosity: "low"`, Medium → `curiosity: "medium"`, High → `curiosity: "high"`

</step>

<step name="perfection">

**⏳ Perfection — How much the agent pursues perfection in the current task**

Display current setting, then AskUserQuestion:
- header: "Perfection"
- question: "How much should CAT pursue perfection in the current task? (Current: {perfection || 'medium'})"
- options:
  - label: "Low"
    description: "Stay focused on the primary goal, defer improvements"
  - label: "Medium (Default)"
    description: "Fix issues within the current task scope"
  - label: "High"
    description: "Fix every issue encountered, expand scope as needed"
  - label: "← Back"
    description: "Return to behavior menu"

Map: Low → `perfection: "low"`, Medium → `perfection: "medium"`, High → `perfection: "high"`

**Priority-based deferral (when perfection is low):**
- High benefit, low cost → Current or next version
- Moderate → Next major version
- Low benefit, high cost → Backlog or distant future

</step>

<step name="verbosity">

**🗣️ Verbosity — How much CAT explains itself during task execution**

Display current setting, then AskUserQuestion:
- header: "Verbosity"
- question: "How much should CAT explain its reasoning? (Current: {verbosity || 'medium'})"
- options:
  - label: "Low"
    description: "Progress banners and errors only — no reasoning, no summaries beyond phase markers"
  - label: "Medium (Default)"
    description: "Phase-transition summaries — what was done, key decisions"
  - label: "High"
    description: "Full reasoning — alternatives considered, tradeoffs noted, rationale for each decision"
  - label: "← Back"
    description: "Return to behavior menu"

Map: Low → `verbosity: "low"`, Medium → `verbosity: "medium"`, High → `verbosity: "high"`

**Update config using the Write tool:**

1. Read the current `.cat/config.json` content using the Read tool.
2. Merge the new `verbosity` string value into the existing config object (update or add the key).
3. Write the complete updated JSON back using the Write tool.

Do NOT use `python3`, `jq`, or any external tool. Use the Write tool directly.

</step>

<step name="width-settings">

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

**Update config using the Write tool:**

1. Read the current `.cat/config.json` content using the Read tool.
2. Merge the new `fileWidth` or `displayWidth` integer value into the existing config object (update or add the key).
3. Write the complete updated JSON back using the Write tool.

Example: if the current config has `{"trust": "medium"}` and the user set displayWidth to 100, write:

```json
{
  "trust": "medium",
  "displayWidth": 100
}
```

Example: if the user also set fileWidth to 120, write:

```json
{
  "trust": "medium",
  "displayWidth": 100,
  "fileWidth": 120
}
```

Do NOT use `python3`, `jq`, or any external tool. Use the Write tool directly.

</step>

<step name="completion-workflow">

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

**Update config using the Write tool:**

1. Read the current `.cat/config.json` content using the Read tool.
2. Merge the new `completionWorkflow` string value into the existing config object (update or add the key).
3. Write the complete updated JSON back using the Write tool.

Example: if the current config has `{"trust": "medium"}` and the user selected "Pull Request", write:

```json
{
  "trust": "medium",
  "completionWorkflow": "pr"
}
```

Do NOT use `python3`, `jq`, or any external tool. Use the Write tool directly.

</step>

<step name="min-severity">

**📊 Min Severity configuration:**

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

**Update config using the Write tool:**

1. Read the current `.cat/config.json` content using the Read tool.
2. Merge the new `minSeverity` string value into the existing config object (update or add the key).
3. Write the complete updated JSON back using the Write tool.

Example: if the current config has `{"trust": "medium"}` and the user selected "HIGH", write:

```json
{
  "trust": "medium",
  "minSeverity": "high"
}
```

Do NOT use `python3`, `jq`, or any external tool. Use the Write tool directly.

</step>

<step name="version-conditions">

**📊 Version Conditions configuration:**

INVOKE: Skill("cat:get-output-agent", args="config.versions")

**Step 1: Select version to configure**

First, scan for available versions:
```bash
ls -1d .cat/v[0-9]*/v[0-9]*.[0-9]* 2>/dev/null | \
  sed 's|.cat/v[0-9]*/v||' | sort -V
```

Determine current minor version from ROADMAP.md (first non-completed).

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

**Step 2: Display current conditions**

Read the plan.md for selected version:
```bash
cat .cat/issues/v{major}/v{major}.{minor}/plan.md 2>/dev/null || \
cat .cat/issues/v{major}/plan.md 2>/dev/null
```

Extract the `## Pre-conditions` and `## Post-conditions` sections.

INVOKE: Skill("cat:get-output-agent", args="config.conditions-for-version {version} {preconditions} {postconditions}")

Replace `{version}`, `{preconditions}`, and `{postconditions}` with actual values extracted from plan.md.
Empty conditions should be represented as "(none)".

**Step 3: Choose action**

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

**Step 4a: Edit pre-conditions**

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

**Step 4b: Edit post-conditions**

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

**Step 5: Update plan.md**

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

**Step 6: Confirm and loop**

INVOKE: Skill("cat:get-output-agent", args="config.conditions-updated {version} {new-preconditions} {new-postconditions}")

Replace `{version}`, `{new-preconditions}`, `{new-postconditions}` with actual values.

Return to Step 3 (Choose action) to allow further edits or navigation.

</step>

<step name="update-config">

**Update configuration file:**

Use the Read tool to read `.cat/config.json`, modify the target setting value, then use the Write tool to
write the updated JSON back to the same path.

</step>

<step name="confirm">

**Confirm change and return to parent menu:**

INVOKE: Skill("cat:get-output-agent", args="config.setting-updated {setting-name} {old-value} {new-value}")

Replace `{setting-name}`, `{old-value}`, `{new-value}` with actual values.

**After confirming**: Return to the **parent menu** and re-display its options.

Examples:
- Changed "Trust" → return to CAT Behavior menu
- Changed "Context window size" → return to Context Limits menu

</step>

<step name="exit">

**Exit screen:**

If changes were made:

INVOKE: Skill("cat:get-output-agent", args="config.saved")

If no changes:

INVOKE: Skill("cat:get-output-agent", args="config.no-changes")

</step>

</process>

<configuration_reference>

| Setting | Type | Default | Description |
|---------|------|---------|-------------|
| `trust` | string | "medium" | How much autonomy CAT has to act independently |
| `caution` | string | "medium" | How cautious the agent is when validating changes |
| `curiosity` | string | "medium" | How curious the agent is when investigating problems and exploring solutions |
| `perfection` | string | "medium" | How much the agent pursues perfection (high=fix all issues, low=stay focused on primary goal) |
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
- `medium` — Compile and unit tests (default).
- `high` — Compile, unit tests, and E2E tests (maximum confidence).

### Curiosity Values
- `low` — Investigate the issue as stated, no extra exploration.
- `medium` — Notice related issues while investigating.
- `high` — Actively explore solutions and alternatives.

### Perfection Values
- `high` — Fix every issue encountered, expand scope as needed.
- `medium` — Fix issues within the current task scope.
- `low` — Stay focused on the primary goal, defer improvements.

### Completion Workflow Values
- `merge` — Merge source branch directly to target branch after approval (default).
- `pr` — Create a pull request instead of merging directly.

### Min Severity Values
- `low` — All concerns visible (CRITICAL, HIGH, MEDIUM, and LOW) (default).
- `medium` — MEDIUM, HIGH, and CRITICAL concerns visible; LOW are ignored.
- `high` — HIGH and CRITICAL concerns visible; MEDIUM and LOW are ignored.
- `critical` — Only CRITICAL concerns visible; HIGH, MEDIUM, and LOW are ignored.

</configuration_reference>

<success_criteria>

- [ ] Current configuration displayed
- [ ] User navigated wizard successfully
- [ ] Settings updated in config.json using Write tool
- [ ] Version conditions viewable and editable via wizard
- [ ] Gate changes saved to version plan.md files
- [ ] Changes confirmed with before/after values

</success_criteria>
