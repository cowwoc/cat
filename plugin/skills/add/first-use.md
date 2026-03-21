<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Add Issue or Version

Unified command for adding issues or versions to the CAT planning structure.

<objective>

Unified command for adding issues or versions to the CAT planning structure. Routes to the appropriate
workflow based on user selection.

**Shortcut:** When invoked with a description argument (e.g., `/cat:add make installation easier`),
treats the argument as a issue description and skips directly to issue creation workflow.

**Efficiency:** Independent questions are batched into single AskUserQuestion calls (up to 4 questions per call)
to minimize wizard interactions and reduce user friction.

**Reference files** — read on demand as needed:
- See `${CLAUDE_PLUGIN_ROOT}/templates/issue-index.json` for the issue index.json template.
- See `${CLAUDE_PLUGIN_ROOT}/templates/issue-plan.md` for issue plan.md templates.
- See `${CLAUDE_PLUGIN_ROOT}/templates/major-state.md` and `${CLAUDE_PLUGIN_ROOT}/templates/major-plan.md` for major version templates.
- See `${CLAUDE_PLUGIN_ROOT}/templates/minor-state.md` and `${CLAUDE_PLUGIN_ROOT}/templates/minor-plan.md` for minor version templates.
- See `${CLAUDE_PLUGIN_ROOT}/templates/changelog.md` for the CHANGELOG.md template.
- See `${CLAUDE_PLUGIN_ROOT}/concepts/questioning.md` for smart questioning guidance.
- See `${CLAUDE_PLUGIN_ROOT}/concepts/version-paths.md` for version path conventions.

</objective>


<process>

<step name="verify">

**Verify planning structure exists:**

Parse `<output type="add">` (hereafter "output") from the execution context. The handler has pre-loaded version data.

If output.planning_valid is false:
- Display output.error_message to the user
- STOP execution

</step>

<step name="check_args">

**Check if description argument was provided:**

If the command was invoked with arguments (e.g., `/cat:add make installation easier`):
- First, check if the argument text indicates version creation intent. If the text matches any of these patterns,
  route to step: select_type instead of treating it as an issue description:
  - Contains "version" AND one of: "major", "minor", "patch", "new", "create", "add"
  - Starts with "new major", "new minor", "new patch", "add major", "add minor", "add patch"
  - Contains one of "major", "minor", "patch" AND one of: "bump", "release", "start", "create", "new", "next"
  - Matches the pattern `v\d+` or `v\d+\.\d+` (explicit version number reference like "start v3" or "bump to v2.1")
- These patterns are non-exhaustive. If the argument text does not match any pattern but still appears to express
  version creation intent (e.g., uses synonyms or indirect phrasing), treat it as version intent.
- If version intent is detected: continue to step: select_type (do NOT capture as ISSUE_DESCRIPTION)
- If no version intent detected: capture the full argument string as ISSUE_DESCRIPTION and skip directly to
  step: issue_read_config (bypassing select_type and the freeform description question)

If no arguments provided:
- Continue to step: select_type

</step>

<step name="select_type">

**Ask what to add:**

Use AskUserQuestion:
- header: "Add What?"
- question: "What would you like to add?"
- options:
  - "Issue" - Add a issue to an existing minor version
  - "Patch version" - Add a patch version to an existing minor
  - "Minor version" - Add a minor version to an existing major
  - "Major version" - Add a new major version

</step>

<step name="route">

**Route based on selection:**

**If "Issue":**
- Continue to add_issue workflow (step: issue_gather_intent)

**If "Patch version":**
- Set VERSION_TYPE="patch", PARENT_TYPE="minor", CHILD_TYPE="issue"
- Continue to unified version workflow (step: version_select_parent)

**If "Minor version":**
- Set VERSION_TYPE="minor", PARENT_TYPE="major", CHILD_TYPE="issue"
- Continue to unified version workflow (step: version_select_parent)

**If "Major version":**
- Set VERSION_TYPE="major", PARENT_TYPE="none", CHILD_TYPE="minor"
- Continue to unified version workflow (step: version_find_next)

</step>

<!-- ========== ISSUE WORKFLOW ========== -->

<step name="issue_gather_intent">

**Gather issue intent BEFORE selecting version:**

The goal is to understand what the user wants to accomplish first, then intelligently suggest which
version it belongs to.

**If ISSUE_DESCRIPTION already set (from command args):**
- Skip the freeform question
- Continue directly to step: issue_read_config

**Otherwise, ask for description (FREEFORM):**

Ask inline: "What do you want to accomplish? Describe the issue you have in mind."

Capture as ISSUE_DESCRIPTION, then continue to step: issue_read_config.

</step>

<step name="issue_read_config">

**Read and validate configuration:**

Read the `effort` value from effective config and store it for all downstream steps:

```bash
CONFIG=$("${CLAUDE_PLUGIN_ROOT}/client/bin/get-config-output" effective)
if [[ $? -ne 0 ]]; then
    echo "ERROR: Failed to read effective config" >&2
    exit 1
fi
EFFORT=$(echo "$CONFIG" | grep -o '"effort"[[:space:]]*:[[:space:]]*"[^"]*"' \
  | sed 's/.*"effort"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
if [[ -z "$EFFORT" ]]; then
    echo "ERROR: 'effort' key not found in effective config." >&2
    echo "Add: \"effort\": \"low|medium|high\" to .cat/config.json" >&2
    exit 1
fi
if [[ "$EFFORT" != "low" && "$EFFORT" != "medium" && "$EFFORT" != "high" ]]; then
    echo "ERROR: Invalid effort value '$EFFORT' in effective config." >&2
    echo "Valid values: low, medium, high" >&2
    exit 1
fi
```

Store EFFORT for use in issue_smart_questioning, issue_impact_analysis, and issue_create.

</step>

<step name="issue_clarify_intent">

**Clarify vague requirements if needed:**

Analyze ISSUE_DESCRIPTION for vagueness indicators:
- Less than 10 words
- Contains generic terms like "improve", "fix", "make better" without specifics
- Missing what/where/why context

**If description appears vague:**

Use AskUserQuestion:
- header: "Clarification"
- question: "Can you provide more details about this issue?"
- options:
  - "Describe the expected behavior" - What should happen when complete?
  - "Describe the current problem" - What's wrong or missing now?
  - "Show an example" - Provide a concrete use case
  - "Description is complete" - Proceed without clarification

**If user provides more details:**
Append clarification to ISSUE_DESCRIPTION.

**If "Description is complete":**
Continue to next step.

</step>

<step name="issue_smart_questioning">

**Probe for ambiguities in the issue description (effort-scaled):**

Use the EFFORT value set in issue_read_config.

**If EFFORT is "low":**

Skip this step entirely. Continue to step: issue_analyze_versions.

**If EFFORT is "medium":**

Analyze ISSUE_DESCRIPTION for the following ambiguity indicators:
- **Scope ambiguity:** The description could apply to multiple subsystems, layers, or components without specifying which
- **Conflicting requirements:** The description implies goals that are difficult to achieve simultaneously (e.g., "faster and more thorough")
- **Unclear success criteria:** No observable outcome is described (e.g., "improve the UX" without saying what "improved" looks like)

If one or more ambiguities are detected, use AskUserQuestion to present them. Ask only the ambiguities that were
actually detected (omit questions where no ambiguity exists). Batch all detected ambiguities into a single
AskUserQuestion call.

Example questions (adapt to the specific ambiguity found):

- **Scope ambiguity detected:**
  - question: "Your description could apply to multiple areas. Which scope is intended?"
  - options: [Two or three concrete scope interpretations derived from the description] + "Covers all of the above"

- **Conflicting requirements detected:**
  - question: "The description implies [goal A] and [goal B], which may be in tension. Which takes priority?"
  - options: ["Prioritize [goal A]", "Prioritize [goal B]", "Balance both — I understand the trade-off", "Clarify description"]

- **Unclear success criteria detected:**
  - question: "How will we know this issue is complete? What should a user observe?"
  - options: [Two or three concrete observable outcomes derived from context] + "I'll describe it: (free text)"

If user provides clarification, append it to ISSUE_DESCRIPTION.

If no ambiguities are detected, skip to step: issue_analyze_versions.

**If EFFORT is "high":**

Perform a deeper analysis of ISSUE_DESCRIPTION covering:
- All medium-level checks above
- **Edge cases:** Are there boundary conditions or unusual inputs the description does not address?
- **Trade-offs:** Does the approach imply architectural trade-offs (e.g., memory vs. speed, simplicity vs. flexibility)?
- **Alternative interpretations:** Are there materially different ways to read the description?
- **Missing context:** Are there referenced systems, components, or dependencies that are not named?

For each detected concern, batch into AskUserQuestion calls (up to 4 questions per call).

In addition to the medium-level questions, include:

- **Edge case gap detected:**
  - question: "The description doesn't address [specific edge case]. Should it?"
  - options: ["Yes, include edge case handling", "No, out of scope for this issue", "Add to UNKNOWNS for research"]

- **Trade-off detected:**
  - question: "This approach implies a trade-off between [A] and [B]. Which is preferred?"
  - options: ["Prioritize [A]", "Prioritize [B]", "Document the trade-off and decide during implementation"]

- **Alternative interpretation detected:**
  - question: "The description could mean [interpretation 1] or [interpretation 2]. Which is correct?"
  - options: ["[Interpretation 1]", "[Interpretation 2]", "Both — describe the full scope", "Neither — clarify description"]

- **Missing context detected:**
  - question: "The description references [component/system] without specifying [what's missing]. Please clarify:"
  - options: [Two or three reasonable defaults derived from context] + "I'll describe it: (free text)"

If user provides clarification, append it to ISSUE_DESCRIPTION.

If no concerns are detected at any level, skip silently to step: issue_analyze_versions.

</step>

<step name="issue_analyze_versions">

**Analyze existing versions and suggest best fit:**

Use version data from output.versions. The handler has pre-filtered closed versions.

**1. Extract version information:**

For each version in output.versions:
- version: Version number (e.g., "2.1")
- status: Current status (open or in-progress)
- summary: Brief description from plan.md goals
- issue_count: Number of existing issues

All versions in output.versions are already filtered to exclude closed versions.

**2. Build version summaries:**

Create a mental map of each version's focus using the pre-loaded summaries.

**3. Compare issue to version focuses:**

Analyze ISSUE_DESCRIPTION against each version's focus:
- Keyword matching (e.g., "parser" matches parser-focused versions)
- Domain alignment (e.g., UI issue matches UI-focused versions)
- Scope fit (bugfix in active development version vs new feature in upcoming version)

**4. Rank versions by fit:**

Score each version based on:
- Topic alignment (high weight)
- Logical grouping with existing issues (medium weight)

</step>

<step name="issue_ask_type_and_criteria">

**Ask issue type, custom post-conditions, and version selection in a single batch:**

This step combines type selection, post-conditions, and version selection into one AskUserQuestion call.

First, build the version options from the analysis performed in issue_analyze_versions:
- If a clear best match exists, list it first as "{best_match} (Recommended) — {brief_reason}"
- Include the second-best match if applicable
- Always include "Show all versions" and "Create new minor" as trailing options

Use AskUserQuestion with multiple questions:
- questions:
    - question: "What type of work is this?"
      header: "Issue Type"
      options:
        - label: "Feature"
          description: "Add new functionality"
        - label: "Bugfix"
          description: "Fix a problem"
        - label: "Refactor"
          description: "Improve code structure"
        - label: "Performance"
          description: "Improve speed/efficiency"
      multiSelect: false

    - question: "Standard post-conditions (functionality, tests, no regressions) will be applied. Any additional
      post-conditions?"
      header: "Custom Post-conditions"
      options:
        - label: "No, standard post-conditions are sufficient"
          description: "Use the default post-conditions for this issue type"
        - label: "Yes, add custom post-conditions"
          description: "I have specific requirements beyond the standard"
      multiSelect: false

    - question: "Based on your issue description, which version should this issue be added to?"
      header: "Target Version"
      options:
        - "{best_match} (Recommended)" - {version_focus_summary}
        - "{second_match}" - {version_focus_summary} (if applicable)
        - "Show all versions" - See complete list
        - "Create new minor" - This doesn't fit existing versions
      multiSelect: false

Capture issue type as ISSUE_TYPE.

**If "Yes, add custom post-conditions":**

Ask inline: "What additional post-conditions should be met?"

Append custom post-conditions to the standard list for ISSUE_TYPE.

**Standard post-conditions by type (applied automatically):**

| Type | Standard Post-conditions |
|------|--------------------------|
| Feature | Functionality works, Tests passing, No regressions, E2E verification |
| Bugfix | Bug fixed, Regression test added, No new issues, E2E verification |
| Refactor | User-visible behavior unchanged, Tests passing, Code quality improved, E2E verification |
| Performance | Target met, Benchmarks added, No functionality regression, E2E verification |

**E2E verification post-condition:** For all implementation issues (feature, bugfix, refactor, performance), always
include at least one post-condition that verifies the change works end-to-end in its real environment, not just that
unit tests pass. Describe an observable outcome (e.g., "Spawn a subagent and confirm it receives the skill listing",
"Run the hook and verify output contains expected fields", or "Reproduce the bug scenario and confirm it no longer
occurs"). This ensures the change is tested as a whole before review.

Set POSTCONDITIONS to standard post-conditions for ISSUE_TYPE, plus any custom additions.

**If "Show all versions" selected:**

List all available minor versions with their focus summaries:

```bash
find .cat -maxdepth 2 -type d -name "v[0-9]*.[0-9]*" 2>/dev/null | \
    sed 's|.*/v\([0-9]*\)/v\1\.\([0-9]*\)|\1.\2|' | sort -V
```

Use AskUserQuestion:
- header: "All Versions"
- question: "Select a version for this issue:"
- options: [List of all versions with focus summaries] + "Create new minor version"

**If no versions exist or "Create new minor version" selected:**
- Go to step: minor_select_major

</step>

<step name="issue_validate_version">

**Validate selected version exists AND is not completed:**

Verify the selected version exists in output.versions:
- Check if version number matches a version in the list
- Verify status is not "closed" (should already be filtered, but double-check)

If version not found in output.versions:
- Output error: "Version {major}.{minor} does not exist or is closed"
- STOP execution

</step>

<step name="issue_suggest_names">

**Generate and present issue name suggestions:**

Based on ISSUE_DESCRIPTION and ISSUE_TYPE, generate 3-4 suggested names:

**Name generation rules:**
1. Extract key action verbs and nouns from description
2. Use standard prefixes based on ISSUE_TYPE:
   - Feature: `add-`, `implement-`, `create-`, `enable-`
   - Bugfix: `fix-`, `resolve-`, `correct-`
   - Refactor: `refactor-`, `restructure-`, `simplify-`, `extract-`
   - Performance: `optimize-`, `speed-up-`, `improve-`
3. Keep names under 50 characters
4. Use lowercase letters, numbers, and hyphens only
5. Make names descriptive but concise

**Example generation:**
- Description: "Add the ability to export reports to PDF format"
- Type: Feature
- Suggestions: `add-pdf-export`, `implement-pdf-reports`, `enable-report-export`

**Present suggestions:**

Use AskUserQuestion:
- header: "Issue Name"
- question: "Choose a name for this issue (or enter a custom name):"
- options:
  - "{suggestion1}" - Based on key terms in description
  - "{suggestion2}" - Alternative phrasing
  - "{suggestion3}" - Shorter variant (if applicable)

**If user selects "Other" (custom name):**
Capture custom input as ISSUE_NAME.

Otherwise, capture selected suggestion as ISSUE_NAME.

</step>

<step name="issue_validate_name">

**Validate issue name:**

Use output.versions[selected_version].existing_issues to check both format and uniqueness.

**Format validation:**

Check that ISSUE_NAME matches the regex pattern: `^[a-z][a-z0-9-]{0,48}[a-z0-9]$`

If format is invalid:
- Output error: "Invalid issue name. Use lowercase letters, numbers, and hyphens only."
- Provide examples: parse-tokens, fix-memory-leak, add-user-auth
- Prompt user to select different suggestion or enter valid custom name

**Uniqueness check:**

Check if ISSUE_NAME appears in output.versions[selected_version].existing_issues list.

If name already exists:
- Output error: "Issue '{ISSUE_NAME}' already exists in version {major}.{minor}"
- Return to issue_suggest_names step with different suggestions

</step>

<step name="issue_detect_skill_deps">

**Detect skill dependencies from issue description and suggest dependent issues:**

This step runs only when the new issue involves modifying a skill file. It scans open issues for references to the
same skill and suggests adding them as dependents (i.e., they should depend on the new issue completing first,
because the new issue changes the skill they rely on).

**1. Detect target skill names from ISSUE_DESCRIPTION:**

Scan ISSUE_DESCRIPTION for patterns that indicate a skill file is being modified:
- Explicit skill file path: `plugin/skills/<skill-name>/` (e.g., `plugin/skills/add-agent/first-use.md`)
- Skill name reference: phrases like "modify the <skill-name> skill", "update <skill-name> skill",
  "add a step to <skill-name>", "change <skill-name> first-use.md"

Extract all skill names mentioned. Store as SKILL_NAMES list (may be empty).

**If SKILL_NAMES is empty:**
- Skip to step: issue_discuss_and_requirements (no skill involvement detected)

**2. Scan open issues for skill references:**

The scanning uses a two-level loop structure:

- **Outer loop** — iterates over each skill name in SKILL_NAMES. A single issue description may reference more than
  one skill (e.g., both `add` and `add-agent`), so every skill name must be checked independently.
- **Inner loop** — for each skill name, iterates over all index.json files found under ISSUES_DIR (excluding the
  current issue). It reads the STATUS field and, for open issues, checks whether the corresponding plan.md contains
  a reference to `plugin/skills/<skill_name>/`.

Deduplication is handled after the outer loop by collecting all matched issue IDs into a single array and running
`sort -u`. An issue is included at most once even if its plan.md references multiple skills that are all in
SKILL_NAMES.

```bash
source "${CLAUDE_PLUGIN_ROOT}/skills/add-agent/skill_dep_helpers.sh"

ISSUES_DIR="${CLAUDE_PROJECT_DIR}/.cat/issues"

# Accumulate all matching issues across all skill names (deduplicated)
AUTO_DETECTED_DEPS=()
AUTO_DETECTED_DEP_PATHS=()
ALL_MATCHED_IDS=()
ALL_MATCHED_PATHS=()

for SKILL_NAME in "${SKILL_NAMES[@]}"; do
    # Direct call (not subshell) required to propagate MATCHING_ISSUES and MATCHING_ISSUE_PATHS
    run_detection "$SKILL_NAME" "$ISSUE_NAME"
    ALL_MATCHED_IDS+=("${MATCHING_ISSUES[@]+"${MATCHING_ISSUES[@]}"}")
    ALL_MATCHED_PATHS+=("${MATCHING_ISSUE_PATHS[@]+"${MATCHING_ISSUE_PATHS[@]}"}")
done

# Deduplicate: build final arrays preserving first occurrence of each issue ID
seen_ids=()
for idx in "${!ALL_MATCHED_IDS[@]}"; do
    ISSUE_ID="${ALL_MATCHED_IDS[$idx]}"
    already_found=false
    for existing in "${seen_ids[@]+"${seen_ids[@]}"}"; do
        if [[ "$existing" == "$ISSUE_ID" ]]; then
            already_found=true
            break
        fi
    done
    if [[ "$already_found" == false ]]; then
        seen_ids+=("$ISSUE_ID")
        AUTO_DETECTED_DEPS+=("$ISSUE_ID")
        AUTO_DETECTED_DEP_PATHS+=("${ALL_MATCHED_PATHS[$idx]}")
    fi
done
```

Exclude the current issue being created from results. Collect all matching issue IDs across all skill names into
AUTO_DETECTED_DEPS (deduplicated list). Collect corresponding full index.json paths into AUTO_DETECTED_DEP_PATHS.

**3. Present suggestions if matches found:**

**If AUTO_DETECTED_DEPS is empty:**
- Skip to step: issue_discuss_and_requirements (no dependent issues found)

**If AUTO_DETECTED_DEPS is non-empty:**

Display context to the user before the question:

```
Auto-detected: The following open issues reference the skill(s) you are modifying:
{for each issue in AUTO_DETECTED_DEPS: "  - {issue-id}"}

These issues use the skill being changed. If your change alters the skill's interface
or behavior, those issues should depend on this one completing first.
```

Use AskUserQuestion:
- header: "Skill Dependency Suggestion"
- question: "Should any of these issues be marked as depending on the new issue?"
- options:
  - label: "Yes, mark all as dependents"
    description: "All listed issues will depend on this new issue"
  - label: "Yes, let me choose"
    description: "Show the list and I'll select which ones"
  - label: "No, skip"
    description: "None of these issues need to depend on the new issue"

**If "Yes, mark all as dependents":**
- AUTO_DETECTED_DEPS is already set from the scan above
- AUTO_DETECTED_DEP_PATHS is already set from the scan above
- Continue to step: issue_discuss_and_requirements

**If "Yes, let me choose":**

Use AskUserQuestion:
- header: "Select Dependent Issues"
- question: "Which issues should depend on this new issue? (Select all that apply)"
- options: [One entry per issue in AUTO_DETECTED_DEPS with its ID]
- multiSelect: true

Capture selected issue IDs as AUTO_DETECTED_DEPS. Populate AUTO_DETECTED_DEP_PATHS with the corresponding
paths from the original AUTO_DETECTED_DEP_PATHS. Always look up each selected ID in the original
AUTO_DETECTED_DEPS array to find its index, then use that index to get the corresponding path from
AUTO_DETECTED_DEP_PATHS. Never use positional order of selected items — always resolve by ID match.
Continue to step: issue_discuss_and_requirements.

**If "No, skip":**
- Set AUTO_DETECTED_DEPS = []
- Set AUTO_DETECTED_DEP_PATHS = []
- Continue to step: issue_discuss_and_requirements

</step>

<step name="issue_discuss_and_requirements">

**Gather additional issue context and requirements in a single batch:**

Note: Issue description and type were already captured in issue_gather_intent step.
Use ISSUE_DESCRIPTION and ISSUE_TYPE from that step.

Initialize UNKNOWNS as empty list.

**1. Check if version has existing issues:**

Use output.versions[selected_version].issue_count to determine if this is the first issue.

**2. Scope estimation (LLM inference):**

Estimate the number of files this issue will touch based on the description and type:
- Consider the issue type (Feature, Bugfix, Refactor, Performance)
- Analyze the scope described in ISSUE_DESCRIPTION
- Estimate whether it's likely 1-2 files, 3-5 files, or 6+ files

Store the estimate internally as SCOPE_ESTIMATE (do not ask user).

**3. Read parent version requirements:**

```bash
# VERSION_PLAN is set to the parent version path (works for any level: major, minor, or patch)
VERSION_PLAN=".cat/issues/v$MAJOR/v$MAJOR.$MINOR/plan.md"
```

Extract REQ-XXX items from plan.md. Store as VERSION_REQUIREMENTS (may be empty).

**4. Batch question strategy:**

**If issue_count = 0 (first issue in version):**
- Set DEPENDENCIES = [] (no issues to depend on)
- Set BLOCKS = [] (no issues to block)
- Only ask requirements (if any exist):
  - If VERSION_REQUIREMENTS is non-empty: use AskUserQuestion with the requirements question below
  - If VERSION_REQUIREMENTS is empty: set Parent Requirements = None, skip to step: issue_research

**If issue_count > 0 (version has existing issues):**
- Batch dependencies, blocks, and requirements into a single AskUserQuestion call (3 questions):

Use AskUserQuestion with multiple questions:
- questions:
    - question: "Does this issue depend on other issues completing first?"
      header: "Dependencies"
      options:
        - label: "No dependencies"
          description: "Can start immediately"
        - label: "Yes, select dependencies"
          description: "Show issue list to choose from"
      multiSelect: false

    - question: "Does this issue block any existing issues?"
      header: "Blocks"
      options:
        - label: "No, doesn't block anything"
          description: "Continue without blockers"
        - label: "Yes, select blocked issues"
          description: "Show issue list to choose from"
      multiSelect: false

    - question: "Which requirements does this issue satisfy? (Select all that apply)"
      header: "Parent Requirements"
      options: [List of REQ-XXX from VERSION_REQUIREMENTS] + "None - infrastructure/setup issue"
      multiSelect: true
      (omit this question entirely if VERSION_REQUIREMENTS is empty; set Parent Requirements = None)

**5. Conditional follow-ups:**

**If SCOPE_ESTIMATE is "6+ files":**

Use AskUserQuestion:
- header: "Issue Size"
- question: "This seems like a large issue. Should we split it into multiple smaller issues?"
- options:
  - "Split into multiple issues" - Create several focused issues
  - "Keep as single issue" - I understand the token risk

If "Split into multiple issues" -> guide user to define multiple issues, loop this command.

**If Dependencies = "Yes, select dependencies":**

List existing issues in same minor version for selection using AskUserQuestion with multiSelect.

**If Blocks = "Yes, select blocked issues":**

List existing issues in same minor version for selection using AskUserQuestion with multiSelect.
When blockers are selected, store the selected issue IDs as BLOCKED_ISSUES. The dependency updates to
their index.json files are deferred to issue_create (after the new issue is committed), following the
same pattern as AUTO_DETECTED_DEPS. Do NOT modify blocked issues' index.json files at this point.

</step>

<step name="issue_research">

**Run research if unknowns exist:**

**If UNKNOWNS list is not empty:**

Display to user:
```
Research needed for: {UNKNOWNS}
Running /cat:research to gather information...
```

Invoke the research skill:
```bash
# Use Skill tool to invoke research
Skill: "cat:research"
Args: "{ISSUE_DESCRIPTION}"
```

Capture research findings as RESEARCH_FINDINGS.

**If UNKNOWNS is empty:**
Skip to step: issue_validate_criteria.

</step>

<step name="issue_validate_criteria">

**Validate acceptance criteria against requirements:**

This step ensures acceptance criteria comprehensively cover the issue requirements before plan.md creation.
Addresses the known gap where incorrect acceptance criteria primed incorrect implementations.

**Prepare validation context:**

Gather the following for validation:
- ISSUE_DESCRIPTION (from issue_gather_intent)
- ISSUE_TYPE (from issue_ask_type_and_criteria)
- POSTCONDITIONS (from issue_ask_type_and_criteria)
- All ancestor version requirements (from issue_discuss_and_requirements and ancestor plan.md files)

**Spawn requirements stakeholder subagent:**

Use Task tool to spawn a cat:stakeholder-requirements subagent with the following prompt:

```
You are validating post-conditions for a CAT issue before plan.md creation.

**Issue Description:**
{ISSUE_DESCRIPTION}

**Issue Type:**
{ISSUE_TYPE}

**Proposed Post-conditions:**
{POSTCONDITIONS}

**Ancestor Version Requirements Satisfied:**
{Selected REQ-XXX requirements from all ancestor versions, or "None"}

**Your validation responsibilities:**

1. **Completeness Check:** Break ISSUE_DESCRIPTION into discrete requirements. Verify each requirement has at least one corresponding post-condition. List any missing requirements.

2. **Version Requirements Cross-Check:** If this issue satisfies REQ-XXX requirements, verify the post-conditions address the intent of those requirements. Check requirements from ALL ancestor versions recursively (e.g., for an issue in v2.1, check both v2.1 plan.md and v2 plan.md requirements). Flag any satisfied requirement from any ancestor that has no corresponding post-condition.

3. **Contradiction Check:** Verify no post-condition contradicts the issue goal or established principles (e.g., M462 pattern: fail-fast post-conditions must not require recovery instructions).

**Output format:**

COMPLETENESS: [PASS|FAIL]
Missing requirements (if FAIL): [list each missing requirement]

VERSION_REQUIREMENTS: [PASS|FAIL|N/A]
Unaddressed requirements (if FAIL): [list each REQ-XXX with missing post-conditions]

CONTRADICTIONS: [PASS|FAIL]
Contradictions found (if FAIL): [describe each contradiction with reference to principle]

ADDITIONAL_CRITERIA: [list additional post-conditions to add, or "None"]
```

Capture the subagent's response.

**Handle subagent failure:**

If the subagent fails to return output, times out, or returns unparseable output:
- Display: "Requirements validation could not be completed. Proceeding with existing criteria."
- Skip validation processing and proceed to next step (issue_impact_analysis)

If the subagent returns output but individual fields are missing or unparseable:
- Treat missing fields as PASS (no issues detected for that check)
- Process any successfully returned fields normally

**Process validation results:**

Parse the subagent response to extract:
- COMPLETENESS status
- VERSION_REQUIREMENTS status
- CONTRADICTIONS status
- ADDITIONAL_CRITERIA list

**If COMPLETENESS is FAIL or VERSION_REQUIREMENTS is FAIL:**

- Auto-add the criteria from ADDITIONAL_CRITERIA to POSTCONDITIONS
- Display to user:
  ```
  Added missing post-conditions:
  {list each added post-condition}
  ```

**If CONTRADICTIONS is FAIL:**

- Display contradictions to user
- Use AskUserQuestion:
  - header: "Post-conditions Contradiction"
  - question: "The following contradictions were found in post-conditions. How should we proceed?"
  - Provide the contradiction details from subagent
  - options:
    - "Revise post-conditions manually" - Let me rewrite the problematic post-conditions
    - "Remove contradicting post-conditions" - Remove the post-conditions that contradict principles
    - "Override - post-conditions are correct" - Proceed with post-conditions as-is (I understand the risk)

- If "Revise post-conditions manually": use AskUserQuestion to gather revised POSTCONDITIONS
- If "Remove contradicting post-conditions": auto-remove the contradicting post-conditions identified by subagent
- If "Override": proceed without changes

**If all checks PASS:**

Proceed silently to step: issue_impact_analysis (no user interaction needed).

</step>

<step name="issue_impact_analysis">

**Analyze potential impact of the proposed issue on existing features (effort-scaled):**

Initialize IMPACT_NOTES="".

Use the EFFORT value set in issue_read_config.

**If EFFORT is "low":**

Skip this step entirely. Continue to step: issue_create.

**If EFFORT is "medium":**

Load existing issues from the selected version using output.versions[selected_version].existing_issues.

For each existing issue in the version, compare its name and any available index.json/plan.md summary against
ISSUE_DESCRIPTION. Identify:
- **Direct conflicts:** The new issue modifies or removes something an existing issue depends on
- **Overlap:** The new issue covers ground already addressed by an existing issue
- **Ordering constraints:** The new issue should logically precede or follow an existing issue but no dependency
  is currently declared

If one or more concerns are found, use AskUserQuestion:
- header: "Impact Concerns"
- question: "The following potential impacts were detected with existing issues in v{major}.{minor}. How would you like to proceed?"
- Present each concern as context above the question (not as a selectable option):
  - "[existing-issue-name]: {brief description of the conflict or overlap}"
- options:
  - "Proceed as described" — Create the issue without changes
  - "Revise description" — I want to adjust the scope to avoid the conflict
  - "Split into multiple issues" — Separate the conflicting parts
  - "Add impact notes to plan" — Document the impact relationship in plan.md

**If "Revise description":**

Ask inline: "Please provide the revised issue description:"

Capture revised input and replace ISSUE_DESCRIPTION.

<!-- Note: Smart questioning (issue_smart_questioning) is not re-run after description revision here.
     The revised description goes through re-evaluation within impact_analysis only. Re-running the full
     smart questioning loop would require returning to issue_smart_questioning and re-traversing the entire
     pipeline, which is deferred as a future enhancement. -->

Loop back to the start of issue_impact_analysis to re-evaluate the revised description for impact concerns.

**If "Split into multiple issues":**

Inform user: "Restart `/cat:add` for each sub-issue. You may use the current description as a starting point for
each." Then STOP execution.

**If "Add impact notes to plan":**

Set IMPACT_NOTES to the concern descriptions. These will be appended to the plan.md in the issue_create step.

**If no concerns are detected:**

Skip silently to step: issue_create.

**If EFFORT is "high":**

Perform a broader impact analysis covering multiple dimensions:

**1. Same-version conflict check (same as medium):**

Apply the medium-level check against existing issues in the selected version.

**2. Cross-version dependency analysis:**

Scan output.versions for other open versions. For each, check if ISSUE_DESCRIPTION touches areas those versions depend
on. Flag potential backward compatibility breaks or API changes that downstream versions consume.

**3. Feature interaction analysis:**

Based on ISSUE_DESCRIPTION and ISSUE_TYPE, reason about which existing system behaviors the change might
implicitly alter:
- If ISSUE_TYPE is "Refactor": flag tests that exercise the refactored area as potentially needing updates
- If ISSUE_TYPE is "Feature": flag additive changes that could conflict with planned features in other open versions
- If ISSUE_TYPE is "Bugfix": flag whether the fix is a targeted correction or requires broader behavioral change

**4. Present consolidated impact report:**

If any concerns were found across dimensions, use AskUserQuestion:
- header: "Impact Analysis"
- question: "Impact analysis found the following concerns. How would you like to proceed?"
- Present each concern with its dimension label (e.g., "Same-version overlap:", "Cross-version compatibility:",
  "Feature interaction:") as context above the question
- options:
  - "Proceed as described" — Create the issue without changes
  - "Revise description" — I want to adjust the scope
  - "Split into multiple issues" — Separate the impacted parts
  - "Add impact notes to plan" — Document the relationships in plan.md

**If "Revise description":**

Ask inline: "Please provide the revised issue description:"

Capture revised input and replace ISSUE_DESCRIPTION.

Loop back to the start of issue_impact_analysis to re-evaluate the revised description for impact concerns.

**If "Split into multiple issues":**

Inform user: "Restart `/cat:add` for each sub-issue. You may use the current description as a starting point for
each." Then STOP execution.

**If "Add impact notes to plan":**

Set IMPACT_NOTES to the concern descriptions. These will be appended to the plan.md in the issue_create step.

**If no concerns are detected at any level:**

Skip silently to step: issue_create.

</step>

<step name="issue_create">

**Note branching strategy information:**

Use output.branch_strategy and output.branch_pattern for reference:
- branch_strategy: "feature" or "main-only"
- branch_pattern: Custom pattern if defined, or null

Calculate the issue branch name:
- If branch_pattern is defined: Apply pattern (replace {major}, {minor}, {version}, {issue-name})
- If branch_strategy is "main-only": No issue branch
- Default: {major}.{minor}-{issue-name}

**Generate index.json content:**

Construct the `indexContent` field as valid JSON matching `${CLAUDE_PLUGIN_ROOT}/templates/issue-index.json`:

```json
{
  "status": "open",
  "dependencies": ["dep1", "dep2"],
  "blocks": []
}
```

Omit `dependencies` if the array is empty. Use only string values for dependency names (e.g., `"2.1-some-issue"`).

**Generate lightweight plan.md:**

Create a lightweight plan.md containing only the goal and post-conditions. Full implementation steps are generated
later by `cat:work-implement-agent` when work begins.

1. Create a unique temporary plan.md file (multi-instance safe):

```bash
planTempFile=$(mktemp --suffix=.md)
```

2. Write the lightweight plan.md to `${planTempFile}` using the Write tool with the following structure:

```
# Plan

## Goal

${ISSUE_DESCRIPTION}

## Pre-conditions

(none)

## Post-conditions

- [ ] ${postcondition_1}
- [ ] ${postcondition_2}
...
```

3. Create the issue, passing the generated plan.md file path.

**JSON escaping:** All interpolated string values ({issue-name}, {one-line description}, dependency names, and
{full index.json content}) must be valid JSON string values. Escape any double quotes as `\"` and backslashes
as `\\` before embedding them in the JSON argument.

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/create-issue" --json '{
  "major": "{major}",
  "minor": "{minor}",
  "issueName": "{issue-name}",
  "issue_type": "{issue-type}",
  "dependencies": ["{dep1}", "{dep2}"],
  "indexContent": "{full index.json content}",
  "planFile": "'"${planTempFile}"'",
  "commitDescription": "{one-line description}"
}'
```

The script handles:
- Creating the issue directory
- Writing index.json and plan.md
- Updating parent version index.json
- Git add and commit

Check the JSON output for success status. If create-issue returns an error, clean up the temporary plan file
before reporting the error and stopping:

```bash
rm -f "${planTempFile}"
```

Clean up the temporary plan file after a successful create-issue call:

```bash
rm -f "${planTempFile}"
```

**Apply auto-detected skill dependency updates (if any):**

After the issue is fully created, apply index.json dependency updates for issues that were flagged in
`issue_detect_skill_deps`. This is done AFTER creation to ensure that orphaned writes never happen if
the wizard is abandoned before issue creation completes.

If AUTO_DETECTED_DEPS is non-empty, for each index `i` in AUTO_DETECTED_DEPS:

```bash
NEW_ISSUE_ID="{new-issue-id}"   # The ID of the newly created issue
STATE_FILE="${AUTO_DETECTED_DEP_PATHS[$i]}"

update_state_dependency "$STATE_FILE" "$NEW_ISSUE_ID"
```

After updating all auto-detected dependency index.json files, commit with a `planning:` commit message such as:
`planning: add {new-issue-id} as dependency of auto-detected dependent issues`

**Apply blocker dependency updates (if any):**

If BLOCKED_ISSUES is non-empty (from issue_discuss_and_requirements), for each blocked issue, add the new
issue as a dependency in that blocked issue's index.json. BLOCKED_ISSUES contains bare issue directory names
(e.g., `fix-something`, not full issue IDs like `2.1-fix-something`). The AskUserQuestion in
issue_discuss_and_requirements must present options using bare directory names only. Iterate over each entry:

```bash
NEW_ISSUE_ID="{new-issue-id}"
for BLOCKED_ISSUE_NAME in "${BLOCKED_ISSUES[@]}"; do
    BLOCKED_ISSUE_DIR=".cat/issues/v$MAJOR/v$MAJOR.$MINOR/$BLOCKED_ISSUE_NAME"
    BLOCKED_STATE_FILE="$BLOCKED_ISSUE_DIR/index.json"

    update_state_dependency "$BLOCKED_STATE_FILE" "$NEW_ISSUE_ID"
done
```

After updating all blocked issues' index.json files, commit with a `planning:` commit message such as:
`planning: add {new-issue-id} as dependency of blocked issues`

</step>

<step name="issue_check_parent_decomposition">

**Check if parent issue is decomposed:**

After creating a sub-issue, verify if it's being added to a decomposed parent issue.

```bash
# Check if this issue is a sub-issue of a decomposed parent
PARENT_ISSUE_DIR=$(dirname "${ISSUE_DIR}")
if [[ -f "${PARENT_ISSUE_DIR}/index.json" ]] && grep -q '"decomposedInto"' "${PARENT_ISSUE_DIR}/index.json"; then
  PARENT_NAME=$(basename "${PARENT_ISSUE_DIR}")

  # Output warning about parent completion requirements
  echo ""
  echo "⚠️  Parent Decomposition Status"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "This sub-issue was added to decomposed parent: ${PARENT_NAME}"
  echo ""
  echo "IMPORTANT: The parent issue ${PARENT_NAME} cannot be closed or merged"
  echo "until ALL sub-issues (including this new one) are completed and closed."
  echo ""
  echo "Completion requirements:"
  echo "  1. All sub-issues must be individually completed and closed"
  echo "  2. Parent's own post-conditions must be verified"
  echo "  3. Only then can the parent be marked complete"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo ""
fi
```

This check ensures the agent is reminded that decomposed parents require all sub-issues to be completed before the parent can be closed or merged, preventing premature parent completion.

</step>

<step name="issue_done">

**Present completion:**

Run the renderer and output its result verbatim:

```bash
CLIENT_BIN="${CLAUDE_PROJECT_DIR}/client/target/jlink/bin"
"$CLIENT_BIN/get-add-output" --type issue --name "{issue-name}" --version "{version}" --issue-type "{type}" --dependencies "{dependencies}"
```

</step>

<!-- ========== UNIFIED VERSION WORKFLOW ========== -->
<!--
  This workflow handles major, minor, and patch version creation with parameterization.
  Variables set by route step:
    VERSION_TYPE: "major" | "minor" | "patch"
    PARENT_TYPE: "none" | "major" | "minor"
    CHILD_TYPE: "minor" | "issue" | "issue"
-->

<step name="version_select_parent">

**Determine target parent version (skip if VERSION_TYPE="major"):**

**If VERSION_TYPE is "major":**
- Skip directly to step: version_find_next

**If VERSION_TYPE is "minor":**
- PARENT_LABEL = "major version"
- List available major versions:

```bash
[ -z "$(ls -d .cat/issues/v[0-9]* 2>/dev/null)" ] && echo "No major versions exist." && exit 0
```

If no major versions exist:
- Inform user: "No major versions exist. Creating one first."
- Set VERSION_TYPE="major", PARENT_TYPE="none"
- Go to step: version_find_next

```bash
ls -1d .cat/issues/v[0-9]* 2>/dev/null | sed 's|.cat/issues/v||' | sort -V
```

Use AskUserQuestion:
- header: "Target Major"
- question: "Which major version should this minor be added to?"
- options: [List of available major versions] + "Create new major version"

If "Create new major version":
- Set VERSION_TYPE="major", PARENT_TYPE="none"
- Go to step: version_find_next

**If VERSION_TYPE is "patch":**
- PARENT_LABEL = "minor version"
- List available minor versions:

```bash
find .cat -maxdepth 2 -type d -name "v[0-9]*.[0-9]*" 2>/dev/null | while read d; do
    VERSION=$(basename "$d" | sed 's/v//')
    MAJOR=$(echo "$VERSION" | cut -d. -f1)
    MINOR=$(echo "$VERSION" | cut -d. -f2)
    STATUS=$(grep -oP '(?<="status":")[^"]+' "$d/index.json" 2>/dev/null || echo "open")
    PATCH_COUNT=$(find "$d" -maxdepth 1 -type d -name "v$MAJOR.$MINOR.*" 2>/dev/null | wc -l)
    echo "$MAJOR.$MINOR ($STATUS, $PATCH_COUNT patches)"
done | sort -V
```

Use AskUserQuestion:
- header: "Target Minor Version"
- question: "Which minor version should this patch be added to?"
- options: [List of available minor versions] + "Cancel"

If "Cancel" -> exit command.

</step>

<step name="version_validate_parent">

**Validate selected parent exists (skip if VERSION_TYPE="major"):**

**If VERSION_TYPE is "major":**
- Skip to step: version_find_next

**If VERSION_TYPE is "minor":**

```bash
MAJOR="{selected_major}"
PARENT_PATH=".cat/issues/v$MAJOR"
[ ! -d "$PARENT_PATH" ] && echo "ERROR: Major version $MAJOR does not exist" && exit 1
```

**If VERSION_TYPE is "patch":**

```bash
MAJOR="{major}"
MINOR="{minor}"
PARENT_PATH=".cat/issues/v$MAJOR/v$MAJOR.$MINOR"
[ ! -d "$PARENT_PATH" ] && echo "ERROR: Minor version $MAJOR.$MINOR does not exist" && exit 1
```

</step>

<step name="version_find_next">

**Determine next version number:**

**If VERSION_TYPE is "major":**

```bash
NEXT_NUMBER=$(ls -1d .cat/issues/v[0-9]* 2>/dev/null | sed 's|.cat/issues/v||' | sort -V | tail -1 | tr -d '[:space:]')
if [ -z "$NEXT_NUMBER" ]; then
    NEXT_NUMBER=1
else
    NEXT_NUMBER=$((NEXT_NUMBER + 1))
fi
VERSION_LABEL="Major version"
NEXT_VERSION="$NEXT_NUMBER"
```

**If VERSION_TYPE is "minor":**

```bash
EXISTING=$(ls -1d "$PARENT_PATH"/v$MAJOR.[0-9]* 2>/dev/null | sed "s|$PARENT_PATH/v$MAJOR\.||" | sort -V)
NEXT_NUMBER=$(echo "$EXISTING" | tail -1 | tr -d '[:space:]')
if [ -z "$NEXT_NUMBER" ]; then
    NEXT_NUMBER=0
else
    NEXT_NUMBER=$((NEXT_NUMBER + 1))
fi
VERSION_LABEL="Minor version"
NEXT_VERSION="$MAJOR.$NEXT_NUMBER"
```

**If VERSION_TYPE is "patch":**

```bash
EXISTING=$(ls -1d "$PARENT_PATH"/v$MAJOR.$MINOR.[0-9]* 2>/dev/null | sed "s|$PARENT_PATH/v$MAJOR.$MINOR\.||" | sort -V)
NEXT_NUMBER=$(echo "$EXISTING" | tail -1 | tr -d '[:space:]')
if [ -z "$NEXT_NUMBER" ]; then
    NEXT_NUMBER=1
else
    NEXT_NUMBER=$((NEXT_NUMBER + 1))
fi
VERSION_LABEL="Patch version"
NEXT_VERSION="$MAJOR.$MINOR.$NEXT_NUMBER"
```

</step>

<step name="version_ask_number">

**Ask for version number:**

Use AskUserQuestion:
- header: "Version Number"
- question: "{VERSION_LABEL} number? (Next available: $NEXT_VERSION)"
- options:
  - "Use $NEXT_VERSION (Recommended)" - Auto-increment
  - "Specify different number" - Enter custom number

**If "Specify different number":**

Ask inline: "Enter the {VERSION_TYPE} version number:"

Capture as REQUESTED_NUMBER.

</step>

<step name="version_check_conflict">

**Check if requested number conflicts:**

If user specified a custom number:

**If VERSION_TYPE is "major":**

```bash
VERSION_EXISTS=false
if [ -d ".cat/issues/v$REQUESTED_NUMBER" ]; then
    echo "Version $REQUESTED_NUMBER already exists."
    VERSION_EXISTS=true
fi
```

**If VERSION_TYPE is "minor":**

```bash
VERSION_EXISTS=false
if [ -d "$PARENT_PATH/v$MAJOR.$REQUESTED_NUMBER" ]; then
    echo "Version $MAJOR.$REQUESTED_NUMBER already exists."
    VERSION_EXISTS=true
fi
```

**If VERSION_TYPE is "patch":**

```bash
VERSION_EXISTS=false
if [ -d "$PARENT_PATH/v$MAJOR.$MINOR.$REQUESTED_NUMBER" ]; then
    echo "Patch version $MAJOR.$MINOR.$REQUESTED_NUMBER already exists."
    VERSION_EXISTS=true
fi
```

**If VERSION_EXISTS is true:** (Do NOT proceed to version_create without resolving the conflict.)

Use AskUserQuestion:
- header: "Version Conflict"
- question: "{VERSION_LABEL} {conflict_version} already exists. What would you like to do?"
- options:
  - "Insert before it" - Create at requested number and renumber existing versions
  - "Use next available ($NEXT_VERSION)" - Skip to next free number
  - "Cancel" - Abort

**If "Insert before it":**
- Go to step: version_renumber

**If "Use next available":**
- Set version number to NEXT_NUMBER
- Continue to step: version_discuss

**If "Cancel":**
- Exit command

</step>

<step name="version_renumber">

**Renumber existing versions:**

This is a significant operation. Renumber all versions >= REQUESTED_NUMBER by +1.

**Sed pattern safety:** The sed replacements below target only version references that belong to the version
being renumbered. Use word-boundary-safe patterns to avoid corrupting references to other versions or prose text.
For major renumbering, use `\bv$v\.\b` (not bare `v$v\.`) and `\bMajor $v\b` to prevent matching substrings in
unrelated contexts. If the sed implementation does not support `\b`, use anchored patterns or verify each match
manually after the operation.

**If VERSION_TYPE is "major":**

```bash
for v in $(ls -1d .cat/issues/v[0-9]* 2>/dev/null | sed 's|.cat/issues/v||' | sort -rV); do
    if [ "$v" -ge "$REQUESTED_NUMBER" ]; then
        NEW_V=$((v + 1))
        echo "Renumbering v$v -> v$NEW_V"
        mv ".cat/issues/v$v" ".cat/issues/v$NEW_V"
        # Replace version references (v-prefixed) and dependency IDs (no v-prefix).
        # Pattern: v{old}. followed by a digit (to match v1.0, v1.1, etc. but not prose).
        # Also update dependency IDs like "1.0-some-issue" -> "2.0-some-issue".
        find ".cat/issues/v$NEW_V" \( -name "*.md" -o -name "*.json" \) -exec \
            sed -i "s/v${v}\.\([0-9]\)/v${NEW_V}.\1/g; s/\bMajor ${v}\b/Major ${NEW_V}/g; s/\b${v}\.\([0-9][0-9]*-\)/${NEW_V}.\1/g" {} \;
        # Update cross-references from OTHER versions that depend on issues in the renumbered version.
        find .cat/issues/ \( -name "*.json" \) -not -path ".cat/issues/v$NEW_V/*" -exec \
            sed -i "s/\b${v}\.\([0-9][0-9]*-\)/${NEW_V}.\1/g" {} \;
    fi
done
```

**If VERSION_TYPE is "minor":**

```bash
for v in $(ls -1d "$PARENT_PATH"/v$MAJOR.[0-9]* 2>/dev/null | sed "s|$PARENT_PATH/v$MAJOR\.||" | sort -rV); do
    if [ "$v" -ge "$REQUESTED_NUMBER" ]; then
        NEW_V=$((v + 1))
        echo "Renumbering v$MAJOR.$v -> v$MAJOR.$NEW_V"
        mv "$PARENT_PATH/v$MAJOR.$v" "$PARENT_PATH/v$MAJOR.$NEW_V"
        # Replace version references (v-prefixed) and dependency IDs (no v-prefix, e.g., "2.0-issue-name").
        find "$PARENT_PATH/v$MAJOR.$NEW_V" \( -name "*.md" -o -name "*.json" \) -exec \
            sed -i "s/v$MAJOR\.$v\b/v$MAJOR.$NEW_V/g; s/\b$MAJOR\.$v-/$MAJOR.$NEW_V-/g" {} \;
        # Update the parent major index.json (minorVersions array references)
        sed -i "s/v$MAJOR\.$v\b/v$MAJOR.$NEW_V/g" "$PARENT_PATH/index.json"
        # Update cross-references from OTHER versions that depend on issues in the renumbered version.
        find .cat/issues/ \( -name "*.json" \) -not -path "$PARENT_PATH/v$MAJOR.$NEW_V/*" -exec \
            sed -i "s/\b$MAJOR\.$v-/$MAJOR.$NEW_V-/g" {} \;
    fi
done
```

**If VERSION_TYPE is "patch":**

```bash
for p in $(ls -1d "$PARENT_PATH"/v$MAJOR.$MINOR.[0-9]* 2>/dev/null | sed "s|$PARENT_PATH/v$MAJOR.$MINOR\.||" | sort -rV); do
    if [ "$p" -ge "$REQUESTED_NUMBER" ]; then
        NEW_P=$((p + 1))
        echo "Renumbering v$MAJOR.$MINOR.$p -> v$MAJOR.$MINOR.$NEW_P"
        mv "$PARENT_PATH/v$MAJOR.$MINOR.$p" "$PARENT_PATH/v$MAJOR.$MINOR.$NEW_P"
        # Replace version references (v-prefixed) and dependency IDs (no v-prefix, e.g., "2.1.0-issue-name").
        find "$PARENT_PATH/v$MAJOR.$MINOR.$NEW_P" \( -name "*.md" -o -name "*.json" \) -exec \
            sed -i "s/v$MAJOR\.$MINOR\.$p\b/v$MAJOR.$MINOR.$NEW_P/g; s/\b$MAJOR\.$MINOR\.$p-/$MAJOR.$MINOR.$NEW_P-/g" {} \;
        # Update the parent minor index.json (patchVersions array references)
        sed -i "s/v$MAJOR\.$MINOR\.$p\b/v$MAJOR.$MINOR.$NEW_P/g" "$PARENT_PATH/index.json"
        # Update cross-references from OTHER versions that depend on issues in the renumbered version.
        find .cat/issues/ \( -name "*.json" \) -not -path "$PARENT_PATH/v$MAJOR.$MINOR.$NEW_P/*" -exec \
            sed -i "s/\b$MAJOR\.$MINOR\.$p-/$MAJOR.$MINOR.$NEW_P-/g" {} \;
    fi
done
```

**Update ROADMAP.md with new version numbers.** Apply the same version renumbering to `.cat/ROADMAP.md`:

```bash
if [ -f .cat/ROADMAP.md ]; then
    if [ "$VERSION_TYPE" = "major" ]; then
        for v in $(seq $((REQUESTED_NUMBER + $(ls -1d .cat/issues/v[0-9]* 2>/dev/null | wc -l) - REQUESTED_NUMBER)) -1 "$REQUESTED_NUMBER"); do
            NEW_V=$((v + 1))
            sed -i "s/v${v}\.\([0-9]\)/v${NEW_V}.\1/g; s/\bMajor ${v}\b/Major ${NEW_V}/g; s/\b${v}\.\([0-9][0-9]*-\)/${NEW_V}.\1/g" .cat/ROADMAP.md
        done
    elif [ "$VERSION_TYPE" = "minor" ]; then
        for v in $(seq $((REQUESTED_NUMBER + $(ls -1d "$PARENT_PATH"/v$MAJOR.[0-9]* 2>/dev/null | wc -l) - REQUESTED_NUMBER)) -1 "$REQUESTED_NUMBER"); do
            NEW_V=$((v + 1))
            sed -i "s/v$MAJOR\.$v\b/v$MAJOR.$NEW_V/g; s/\b$MAJOR\.$v-/$MAJOR.$NEW_V-/g" .cat/ROADMAP.md
        done
    elif [ "$VERSION_TYPE" = "patch" ]; then
        for p in $(seq $((REQUESTED_NUMBER + $(ls -1d "$PARENT_PATH"/v$MAJOR.$MINOR.[0-9]* 2>/dev/null | wc -l) - REQUESTED_NUMBER)) -1 "$REQUESTED_NUMBER"); do
            NEW_P=$((p + 1))
            sed -i "s/v$MAJOR\.$MINOR\.$p\b/v$MAJOR.$MINOR.$NEW_P/g; s/\b$MAJOR\.$MINOR\.$p-/$MAJOR.$MINOR.$NEW_P-/g" .cat/ROADMAP.md
        done
    fi
fi
```

**Commit renumbered versions immediately** to prevent an inconsistent working tree if subsequent steps fail:

```bash
git add .cat/issues/ .cat/ROADMAP.md && \
git commit -m "$(cat <<'EOF'
planning: renumber versions to make room for v{version_number}

Renumbered all {VERSION_TYPE} versions >= {REQUESTED_NUMBER} by +1 to insert at the requested position.
EOF
)"
```

Set version number to REQUESTED_NUMBER and continue.

</step>

<step name="version_discuss">

**Gather version context through collaborative thinking:**

**If VERSION_TYPE is "major":**

Follow the discussion workflow:
1. Vision - what to build/add/fix
2. Explore features
3. Sharpen core
4. Find boundaries
5. Dependencies
6. Synthesize and confirm

**If VERSION_TYPE is "minor":**

**1. Open - Features First:**

Use AskUserQuestion:
- header: "Focus"
- question: "What do you want to accomplish in minor version $MAJOR.$MINOR?"
- options: ["Bug fixes", "Small features", "Improvements", "Let me describe"]

**2. Explore specifics:**

Based on response, ask follow-up questions using AskUserQuestion.

**3. Boundaries:**

Only ask about scope boundaries if user's previous answers mentioned:
- "later", "future", "not yet", "eventually"
- Multiple distinct features that might not all fit

Otherwise, assume all mentioned items are in scope and skip this question.

**4. Synthesize and confirm:**

Present synthesis and confirm with user.

**If VERSION_TYPE is "patch":**

**1. Open - Purpose First:**

Use AskUserQuestion:
- header: "Patch Focus"
- question: "What is the purpose of patch version $MAJOR.$MINOR.$PATCH?"
- options: ["Bug fixes", "Hot fixes", "Security patches", "Let me describe"]

**2. Explore specifics:**

Based on response, ask follow-up questions using AskUserQuestion.

**3. Scope:**

Use AskUserQuestion:
- header: "Scope"
- question: "How urgent is this patch?"
- options: ["Critical - production issue", "High - needs release soon", "Normal - next maintenance window", "Low -
  convenience fix"]

**4. Synthesize and confirm:**

Present synthesis and confirm with user.

</step>

<step name="version_derive_requirements">

**Derive requirements from goals using backward thinking.**

Apply backward thinking to each goal/focus item and generate REQ-001, REQ-002, etc.

Present for review with AskUserQuestion.

</step>

<step name="version_configure_conditions">

**Apply standard pre/post-conditions with option for customization:**

Standard conditions are applied automatically - do not ask users to confirm obvious requirements.

**Standard conditions by version type:**

| Type | Pre-conditions | Post-conditions |
|------|----------------|-----------------|
| Major | Previous major complete (or none) | All minors complete, vision satisfied |
| Minor | Previous minor complete (or none) | All issues complete, tests pass |
| Patch | Issue identified | Fix verified, regression tests pass |

**Only ask if user wants CUSTOM conditions:**

Use AskUserQuestion:
- header: "Custom Conditions"
- question: "Standard conditions will be applied (pre-conditions: dependencies complete, post-conditions: all issues +
  tests pass). Any custom conditions?"
- options:
  - label: "No, standard conditions are sufficient"
    description: "Use default pre/post-conditions"
  - label: "Yes, add custom conditions"
    description: "I have specific condition requirements"

**If "Yes, add custom conditions":**

Ask inline: "What additional pre-condition or post-condition requirements should be met?"

Append custom conditions to standard conditions.

</step>

<step name="version_create">

**Create version structure:**

**If VERSION_TYPE is "major":**

```bash
MAJOR=$VERSION_NUMBER
VERSION_PATH=".cat/issues/v$MAJOR"
mkdir -p "$VERSION_PATH/v$MAJOR.0"
```

**Create major index.json** (include the initial minor version in `minorVersions`):

```bash
cat > "$VERSION_PATH/index.json" << EOF
{
  "status": "open",
  "minorVersions": ["v$MAJOR.0"]
}
EOF
[ -f "$VERSION_PATH/index.json" ] || echo "ERROR: Major index.json not created"
```

**Create major plan.md** using the template at `${CLAUDE_PLUGIN_ROOT}/templates/major-plan.md`:

```bash
cat > "$VERSION_PATH/plan.md" << EOF
# Plan: v$MAJOR - $VERSION_TITLE

## Vision
$VERSION_VISION

## Scope
$VERSION_SCOPE

## Requirements
$VERSION_REQUIREMENTS

## Pre-conditions
$PRECONDITIONS

## Post-conditions
$POSTCONDITIONS
EOF
[ -f "$VERSION_PATH/plan.md" ] || echo "ERROR: Major plan.md not created"
```

**Create major CHANGELOG.md:**

```bash
cat > "$VERSION_PATH/CHANGELOG.md" << EOF
# Major Version $MAJOR Changelog

## [Unreleased]

### Added
- (pending changes)

---
*Major version started: $(date +%Y-%m-%d)*
EOF
[ -f "$VERSION_PATH/CHANGELOG.md" ] || echo "ERROR: Major CHANGELOG.md not created"
```

**Create initial minor version (X.0):**

Set up variables and use the minor version templates below:

```bash
MINOR=0
VERSION_PATH="$VERSION_PATH/v$MAJOR.$MINOR"
VERSION_SUMMARY="Initial release for major version $MAJOR"
```

Then execute the three "Create minor [STATE/PLAN/CHANGELOG].md" bash blocks in the minor version
section below. The templates use `$VERSION_PATH`, `$MAJOR`, `$MINOR`, and `$VERSION_SUMMARY`.

**If VERSION_TYPE is "minor":**

```bash
MINOR=$VERSION_NUMBER
VERSION_PATH="$PARENT_PATH/v$MAJOR.$MINOR"
VERSION_SUMMARY="$VERSION_DESCRIPTION"
mkdir -p "$VERSION_PATH"
```

**Create minor index.json:**

```bash
cat > "$VERSION_PATH/index.json" << EOF
{
  "status": "open",
  "progress": 0
}
EOF
[ -f "$VERSION_PATH/index.json" ] || echo "ERROR: Minor index.json not created"
```

**Create minor plan.md:**

```bash
cat > "$VERSION_PATH/plan.md" << EOF
# Minor Version $MAJOR.$MINOR Plan

## Goals
$VERSION_GOALS

## Requirements
$VERSION_REQUIREMENTS

## Pre-conditions
$PRECONDITIONS

## Post-conditions
$POSTCONDITIONS
EOF
[ -f "$VERSION_PATH/plan.md" ] || echo "ERROR: Minor plan.md not created"
```

**Create minor CHANGELOG.md:**

```bash
cat > "$VERSION_PATH/CHANGELOG.md" << EOF
# Minor $MAJOR.$MINOR Changelog

## [Unreleased]

### Added
- (pending changes)

---
*Minor version started: $(date +%Y-%m-%d)*
EOF
[ -f "$VERSION_PATH/CHANGELOG.md" ] || echo "ERROR: Minor CHANGELOG.md not created"
```

**If VERSION_TYPE is "patch":**

```bash
PATCH=$VERSION_NUMBER
VERSION_PATH="$PARENT_PATH/v$MAJOR.$MINOR.$PATCH"
mkdir -p "$VERSION_PATH"
```

**Create index.json:**

```bash
cat > "$VERSION_PATH/index.json" << EOF
{
  "status": "open",
  "progress": 0
}
EOF
[ -f "$VERSION_PATH/index.json" ] || echo "ERROR: Patch index.json not created"
```

**Create plan.md:**

```bash
cat > "$VERSION_PATH/plan.md" << EOF
# Patch Version $MAJOR.$MINOR.$PATCH Plan

## Goals
$VERSION_GOALS

## Requirements
$VERSION_REQUIREMENTS

## Pre-conditions
$PRECONDITIONS

## Post-conditions
$POSTCONDITIONS
EOF
[ -f "$VERSION_PATH/plan.md" ] || echo "ERROR: Patch plan.md not created"
```

**Create CHANGELOG.md:**

```bash
cat > "$VERSION_PATH/CHANGELOG.md" << EOF
# Patch $MAJOR.$MINOR.$PATCH Changelog

## [Unreleased]

### Fixed
- (pending changes)

---
*Patch started: $(date +%Y-%m-%d)*
EOF
[ -f "$VERSION_PATH/CHANGELOG.md" ] || echo "ERROR: Patch CHANGELOG.md not created"
```

</step>

<step name="version_update_roadmap">

**Update ROADMAP.md with new version entry:**

```bash
ROADMAP=".cat/ROADMAP.md"
```

**If VERSION_TYPE is "major":**

```bash
cat >> "$ROADMAP" << EOF

## Version $MAJOR: $VERSION_TITLE (PLANNED)
- **$MAJOR.0:** Initial Release (PENDING)
EOF
grep -q "## Version $MAJOR:" "$ROADMAP" || echo "ERROR: Major version section not added to ROADMAP.md"
```

**If VERSION_TYPE is "minor":**

```bash
if grep -q "^## Version $MAJOR:" "$ROADMAP"; then
  LINE_NUM=$(grep -n "^## Version $MAJOR:" "$ROADMAP" | cut -d: -f1)
  sed -i "$((LINE_NUM + 1))a - **$MAJOR.$MINOR:** $VERSION_DESCRIPTION (PENDING)" "$ROADMAP"
else
  echo "WARNING: Major version $MAJOR section not found in ROADMAP.md"
fi && \
grep -q "$MAJOR.$MINOR" "$ROADMAP" || echo "WARNING: Minor not added to ROADMAP.md"
```

**If VERSION_TYPE is "patch":**

```bash
if grep -q "^- \*\*$MAJOR.$MINOR:\*\*" "$ROADMAP"; then
  LINE_NUM=$(grep -n "^- \*\*$MAJOR.$MINOR:\*\*" "$ROADMAP" | cut -d: -f1)
  sed -i "$((LINE_NUM))a\  - **$MAJOR.$MINOR.$PATCH:** $VERSION_DESCRIPTION (PENDING)" "$ROADMAP"
else
  echo "WARNING: Minor version $MAJOR.$MINOR entry not found in ROADMAP.md"
fi && \
grep -q "$MAJOR.$MINOR.$PATCH" "$ROADMAP" || echo "WARNING: Patch not added to ROADMAP.md"
```

</step>

<step name="version_update_parent">

**Update parent index.json (skip if VERSION_TYPE="major"):**

**If VERSION_TYPE is "major":**
- Skip to step: version_commit (no parent to update)

**Important:** Version index.json files are always flat JSON with no nested objects (e.g.,
`{"status": "open"}`). The sed patterns below rely on this structure. Do NOT use these patterns on JSON
files that may contain nested objects.

**If VERSION_TYPE is "minor":**

```bash
PARENT_STATE="$PARENT_PATH/index.json" && \
if grep -q '"minorVersions"' "$PARENT_STATE"; then
  sed -i "s/\"minorVersions\": \[/\"minorVersions\": [\"v$MAJOR.$MINOR\", /" "$PARENT_STATE"
else
  # Safe because version index.json is always flat JSON (no nested objects).
  sed -i "s/\}$/,\"minorVersions\":[\"v$MAJOR.$MINOR\"]}/" "$PARENT_STATE"
fi && \
grep -q "v$MAJOR.$MINOR" "$PARENT_STATE" || echo "ERROR: Minor version not added to major index.json"
```

**If VERSION_TYPE is "patch":**

```bash
PARENT_STATE="$PARENT_PATH/index.json" && \
if grep -q '"patchVersions"' "$PARENT_STATE"; then
  sed -i "s/\"patchVersions\": \[/\"patchVersions\": [\"v$MAJOR.$MINOR.$PATCH\", /" "$PARENT_STATE"
else
  # Safe because version index.json is always flat JSON (no nested objects).
  sed -i "s/\}$/,\"patchVersions\":[\"v$MAJOR.$MINOR.$PATCH\"]}/" "$PARENT_STATE"
fi && \
grep -q "v$MAJOR.$MINOR.$PATCH" "$PARENT_STATE" || echo "ERROR: Patch version not added to minor index.json"
```

</step>

<step name="version_commit">

**Commit version creation:**

**If VERSION_TYPE is "major":**

```bash
git add ".cat/issues/v$MAJOR/" ".cat/ROADMAP.md" && \
git commit -m "$(cat <<'EOF'
planning: add major version {major}

{One-line description of major version vision}

Creates Major {major} with initial minor version {major}.0.
EOF
)"
```

**If VERSION_TYPE is "minor":**

```bash
git add "$VERSION_PATH/" ".cat/ROADMAP.md" "$PARENT_PATH/index.json" && \
git commit -m "$(cat <<'EOF'
planning: add minor version {major}.{minor}

{One-line description of minor version focus}
EOF
)"
```

**If VERSION_TYPE is "patch":**

```bash
git add "$VERSION_PATH/" ".cat/ROADMAP.md" "$PARENT_PATH/index.json" && \
git commit -m "$(cat <<'EOF'
planning: add patch version {major}.{minor}.{patch}

{One-line description of patch version focus}
EOF
)"
```

</step>

<step name="version_done">

**Present completion:**

Run the renderer and output its result verbatim:

```bash
CLIENT_BIN="${CLAUDE_PROJECT_DIR}/client/target/jlink/bin"
"$CLIENT_BIN/get-add-output" --type version --name "{version-name}" --version "{version}" --version-type "{VERSION_TYPE}" --parent "{parent-info}" --path "{version-path}"
```

</step>

</process>

<success_criteria>

**For Issue:**
- [ ] Target version selected or created
- [ ] Issue name validated (format and uniqueness)
- [ ] Discussion captured issue details
- [ ] Requirements selected (or explicitly set to None)
- [ ] index.json and plan.md created
- [ ] Parent index.json updated
- [ ] All committed to git

**For Version (Major/Minor/Patch):**
- [ ] Parent version validated (if applicable)
- [ ] Version number determined (with renumbering if needed)
- [ ] Discussion captured focus/scope/vision
- [ ] Requirements derived
- [ ] Gates configured
- [ ] Directory structure created
- [ ] Files created and ROADMAP.md updated
- [ ] Parent index.json updated (if applicable)
- [ ] All committed to git

</success_criteria>

!`"${CLAUDE_PLUGIN_ROOT}/client/bin/get-output" add`
