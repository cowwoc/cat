@templates/issue-state.md
@templates/issue-plan.md
@templates/major-state.md
@templates/major-plan.md
@templates/minor-state.md
@templates/minor-plan.md
@templates/changelog.md
@concepts/questioning.md
@concepts/version-paths.md

# Add Issue or Version

Unified command for adding issues or versions to the CAT planning structure.

<objective>

Unified command for adding issues or versions to the CAT planning structure. Routes to the appropriate
workflow based on user selection.

**Shortcut:** When invoked with a description argument (e.g., `/cat:add make installation easier`),
treats the argument as a issue description and skips directly to issue creation workflow.

</objective>


<process>

<step name="verify">

**Verify planning structure exists:**

Parse HANDLER_DATA from the execution context. The handler has pre-loaded version data.

If `planning_valid` is false in HANDLER_DATA:
- Output the error message from HANDLER_DATA
- STOP execution

</step>

<step name="check_args">

**Check if description argument was provided:**

If the command was invoked with arguments (e.g., `/cat:add make installation easier`):
- Capture the full argument string as TASK_DESCRIPTION
- Skip directly to step: task_ask_type_and_criteria (bypassing select_type and the freeform description question)

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
- Continue to add_task workflow (step: task_gather_intent)

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

<step name="task_gather_intent">

**Gather issue intent BEFORE selecting version:**

The goal is to understand what the user wants to accomplish first, then intelligently suggest which
version it belongs to.

**If TASK_DESCRIPTION already set (from command args):**
- Skip the freeform question
- Continue directly to step: task_ask_type_and_criteria

**Otherwise, ask for description (FREEFORM):**

Ask inline: "What do you want to accomplish? Describe the issue you have in mind."

Capture as TASK_DESCRIPTION, then continue to step: task_clarify_intent.

</step>

<step name="task_clarify_intent">

**Clarify vague requirements if needed:**

Analyze TASK_DESCRIPTION for vagueness indicators:
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
Append clarification to TASK_DESCRIPTION.

**If "Description is complete":**
Continue to next step.

</step>

<step name="task_ask_type_and_criteria">

**Ask issue type and custom acceptance criteria:**

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

    - question: "Standard criteria (functionality, tests, no regressions) will be applied. Any additional acceptance
      criteria?"
      header: "Custom Criteria"
      options:
        - label: "No, standard criteria are sufficient"
          description: "Use the default acceptance criteria for this issue type"
        - label: "Yes, add custom criteria"
          description: "I have specific requirements beyond the standard"
      multiSelect: false

Capture issue type as TASK_TYPE.

**If "Yes, add custom criteria":**

Ask inline: "What additional acceptance criteria should be met?"

Append custom criteria to the standard list for TASK_TYPE.

**Standard criteria by type (applied automatically):**

| Type | Standard Criteria |
|------|-------------------|
| Feature | Functionality works, Tests passing, No regressions |
| Bugfix | Bug fixed, Regression test added, No new issues |
| Refactor | User-visible behavior unchanged, Tests passing, Code quality improved |
| Performance | Target met, Benchmarks added, No functionality regression |

Set ACCEPTANCE_CRITERIA to standard criteria for TASK_TYPE, plus any custom additions.

</step>

<step name="task_analyze_versions">

**Analyze existing versions and suggest best fit:**

Use version data from HANDLER_DATA.versions. The handler has pre-filtered closed versions.

**1. Extract version information:**

For each version in HANDLER_DATA.versions:
- version: Version number (e.g., "2.1")
- status: Current status (open or in-progress)
- summary: Brief description from PLAN.md goals
- issue_count: Number of existing issues

All versions in HANDLER_DATA are already filtered to exclude closed versions.

**2. Build version summaries:**

Create a mental map of each version's focus using the pre-loaded summaries.

**3. Compare issue to version focuses:**

Analyze TASK_DESCRIPTION against each version's focus:
- Keyword matching (e.g., "parser" matches parser-focused versions)
- Domain alignment (e.g., UI issue matches UI-focused versions)
- Scope fit (bugfix in active development version vs new feature in upcoming version)

**4. Rank versions by fit:**

Score each version based on:
- Topic alignment (high weight)
- Logical grouping with existing issues (medium weight)

</step>

<step name="task_suggest_version">

**Present intelligent version recommendation:**

Based on analysis, present options with the best match first:

**If clear best match exists:**

Use AskUserQuestion:
- header: "Suggested Version"
- question: "Based on your issue description, I recommend adding this to version {best_match}
  ({brief_reason}). Which version should this issue be added to?"
- options:
  - "{best_match} (Recommended)" - {version_focus_summary}
  - "{second_match}" - {version_focus_summary} (if applicable)
  - "Show all versions" - See complete list
  - "Create new minor" - This doesn't fit existing versions

**If "Show all versions" selected:**

List all available minor versions with their focus summaries:

```bash
find .claude/cat -maxdepth 2 -type d -name "v[0-9]*.[0-9]*" 2>/dev/null | \
    sed 's|.*/v\([0-9]*\)/v\1\.\([0-9]*\)|\1.\2|' | sort -V
```

Use AskUserQuestion:
- header: "All Versions"
- question: "Select a version for this issue:"
- options: [List of all versions with focus summaries] + "Create new minor version"

**If no versions exist or "Create new minor version" selected:**
- Go to step: minor_select_major

</step>

<step name="task_validate_version">

**Validate selected version exists AND is not completed (M168):**

Verify the selected version exists in HANDLER_DATA.versions:
- Check if version number matches a version in the list
- Verify status is not "closed" (should already be filtered, but double-check)

If version not found in HANDLER_DATA.versions:
- Output error: "Version {major}.{minor} does not exist or is closed"
- STOP execution

</step>

<step name="task_suggest_names">

**Generate and present issue name suggestions:**

Based on TASK_DESCRIPTION and TASK_TYPE, generate 3-4 suggested names:

**Name generation rules:**
1. Extract key action verbs and nouns from description
2. Use standard prefixes based on TASK_TYPE:
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
Capture custom input as TASK_NAME.

Otherwise, capture selected suggestion as TASK_NAME.

</step>

<step name="task_validate_name">

**Validate issue name:**

Use HANDLER_DATA.versions[selected_version].existing_issues to check both format and uniqueness.

**Format validation:**

Check that TASK_NAME matches the regex pattern: `^[a-z][a-z0-9-]{0,48}[a-z0-9]$`

If format is invalid:
- Output error: "Invalid issue name. Use lowercase letters, numbers, and hyphens only."
- Provide examples: parse-tokens, fix-memory-leak, add-user-auth
- Prompt user to select different suggestion or enter valid custom name

**Uniqueness check:**

Check if TASK_NAME appears in HANDLER_DATA.versions[selected_version].existing_issues list.

If name already exists:
- Output error: "Issue '{TASK_NAME}' already exists in version {major}.{minor}"
- Return to task_suggest_names step with different suggestions

</step>

<step name="task_discuss">

**Gather additional issue context (M374 - skip obvious questions):**

Note: Issue description and type were already captured in task_gather_intent step.
Use TASK_DESCRIPTION and TASK_TYPE from that step.

Initialize UNKNOWNS as empty list.

**1. Check if version has existing issues:**

Use HANDLER_DATA.versions[selected_version].issue_count to determine if this is the first issue.

**2. Smart defaults based on context (M374):**

**If issue_count = 0 (first issue in version):**
- Set DEPENDENCIES = [] (no issues to depend on)
- Set BLOCKS = [] (no issues to block)
- Skip dependency/blocker questions entirely

**If issue_count > 0:**
- Only then ask about dependencies and blockers

**3. Scope estimation (LLM inference):**

Estimate the number of files this issue will touch based on the description and type:
- Consider the issue type (Feature, Bugfix, Refactor, Performance)
- Analyze the scope described in TASK_DESCRIPTION
- Estimate whether it's likely 1-2 files, 3-5 files, or 6+ files

Store the estimate internally as SCOPE_ESTIMATE (do not ask user).

**4. Dependencies/Blockers (only if version has existing issues):**

**If EXISTING_ISSUES > 0:**

Use AskUserQuestion:
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
When blockers are selected, add this new issue to their Dependencies list in STATE.md.

</step>

<step name="task_research">

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
Skill: "research"
Args: "{TASK_DESCRIPTION}"
```

Capture research findings as RESEARCH_FINDINGS.

**If UNKNOWNS is empty:**
Skip to step: task_select_requirements.

</step>

<step name="task_select_requirements">

**Select requirements this issue satisfies:**

Requirements can be defined at any version level (major, minor, or patch). This step reads
requirements from the parent version's PLAN.md, regardless of which level that is.

**1. Read parent version requirements:**

```bash
# VERSION_PLAN is set to the parent version path (works for any level: major, minor, or patch)
VERSION_PLAN=".claude/cat/issues/v$MAJOR/v$MAJOR.$MINOR/PLAN.md"
```

**2. Present requirements for selection:**

If requirements exist in the parent version's PLAN.md:

Use AskUserQuestion:
- header: "Satisfies"
- question: "Which requirements does this issue satisfy? (Select all that apply)"
- multiSelect: true
- options: [List of REQ-XXX from parent version PLAN.md] + "None - infrastructure/setup issue"

If no requirements defined in parent version: Satisfies = None

</step>

<step name="task_validate_criteria">

**Validate acceptance criteria against requirements:**

This step ensures acceptance criteria comprehensively cover the task requirements before PLAN.md creation.
Addresses the known gap (M462) where incorrect acceptance criteria primed incorrect implementations.

**Prepare validation context:**

Gather the following for validation:
- TASK_DESCRIPTION (from task_gather_intent)
- TASK_TYPE (from task_ask_type_and_criteria)
- ACCEPTANCE_CRITERIA (from task_ask_type_and_criteria)
- All ancestor version requirements (from task_select_requirements and ancestor PLAN.md files)

**Spawn requirements stakeholder subagent:**

Use Task tool to spawn a cat:stakeholder-requirements subagent with the following prompt:

```
You are validating acceptance criteria for a CAT issue before PLAN.md creation.

**Task Description:**
{TASK_DESCRIPTION}

**Task Type:**
{TASK_TYPE}

**Proposed Acceptance Criteria:**
{ACCEPTANCE_CRITERIA}

**Ancestor Version Requirements Satisfied:**
{Selected REQ-XXX requirements from all ancestor versions, or "None"}

**Your validation responsibilities:**

1. **Completeness Check:** Break TASK_DESCRIPTION into discrete requirements. Verify each requirement has at least one corresponding acceptance criterion. List any missing requirements.

2. **Version Requirements Cross-Check:** If this issue satisfies REQ-XXX requirements, verify the acceptance criteria address the intent of those requirements. Check requirements from ALL ancestor versions recursively (e.g., for an issue in v2.1, check both v2.1 PLAN.md and v2 PLAN.md requirements). Flag any satisfied requirement from any ancestor that has no corresponding acceptance criterion.

3. **Contradiction Check:** Verify no criterion contradicts the task goal or established principles (e.g., M462 pattern: fail-fast criteria must not require recovery instructions).

**Output format:**

COMPLETENESS: [PASS|FAIL]
Missing requirements (if FAIL): [list each missing requirement]

VERSION_REQUIREMENTS: [PASS|FAIL|N/A]
Unaddressed requirements (if FAIL): [list each REQ-XXX with missing criteria]

CONTRADICTIONS: [PASS|FAIL]
Contradictions found (if FAIL): [describe each contradiction with reference to principle]

ADDITIONAL_CRITERIA: [list additional criteria to add, or "None"]
```

Capture the subagent's response.

**Handle subagent failure:**

If the subagent fails to return output, times out, or returns unparseable output:
- Display: "Requirements validation could not be completed. Proceeding with existing criteria."
- Skip validation processing and proceed to next step (task_create)

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

- Auto-add the criteria from ADDITIONAL_CRITERIA to ACCEPTANCE_CRITERIA
- Display to user:
  ```
  Added missing acceptance criteria:
  {list each added criterion}
  ```

**If CONTRADICTIONS is FAIL:**

- Display contradictions to user
- Use AskUserQuestion:
  - header: "Acceptance Criteria Contradiction"
  - question: "The following contradictions were found in acceptance criteria. How should we proceed?"
  - Provide the contradiction details from subagent
  - options:
    - "Revise criteria manually" - Let me rewrite the problematic criteria
    - "Remove contradicting criteria" - Remove the criteria that contradict principles
    - "Override - criteria are correct" - Proceed with criteria as-is (I understand the risk)

- If "Revise criteria manually": use AskUserQuestion to gather revised ACCEPTANCE_CRITERIA
- If "Remove contradicting criteria": auto-remove the contradicting criteria identified by subagent
- If "Override": proceed without changes

**If all checks PASS:**

Proceed silently to next step (no user interaction needed).

</step>

<step name="task_create">

**Note branching strategy information:**

Use HANDLER_DATA.branch_strategy and HANDLER_DATA.branch_pattern for reference:
- branch_strategy: "feature" or "main-only"
- branch_pattern: Custom pattern if defined, or null

Calculate the issue branch name:
- If branch_pattern is defined: Apply pattern (replace {major}, {minor}, {version}, {issue-name})
- If branch_strategy is "main-only": No issue branch
- Default: {major}.{minor}-{issue-name}

**Generate STATE.md content:**

Use appropriate template format:

```markdown
# State

- **Status:** open
- **Progress:** 0%
- **Dependencies:** [{dep1}, {dep2}] or []
- **Last Updated:** {timestamp}
```

**Generate PLAN.md content based on issue type:**

Use appropriate template (Feature, Bugfix, or Refactor) from issue-plan.md reference.

**CRITICAL:** Follow template guidance to separate Execution Steps (actions only) from Success Criteria (measurable
outcomes). Do NOT include expected values like "score = 1.0" in Execution Steps as this primes subagents to fabricate
results.

**PLAN.md Comprehensiveness (M430):** The PLAN.md must be comprehensive enough for a haiku-level
model to implement mechanically without making architectural decisions. Include:
- Exact file paths to create/modify
- Specific code patterns or formats to use
- Complete lists (all files, all references to update, all acceptance criteria)
- Research findings that inform implementation decisions

If the execution subagent needs to make judgment calls about "how" to implement, the PLAN.md
is not detailed enough. The subagent should only decide "how to write the code", not "what approach to take".

**Batch Execution Check (M427):** When the issue involves multiple files AND a skill (e.g., compress 9 files with
/cat:shrink-doc):
1. Read the target skill's documentation for batch/parallel execution patterns
2. If the skill documents using `/cat:delegate` for multiple files, write execution steps to use delegate
3. Example: Instead of "For each file: Run /cat:shrink-doc", use "/cat:delegate --skill shrink-doc file1.md file2.md
   ..."

This ensures batch tasks leverage parallel execution rather than sequential processing.

**If RESEARCH_FINDINGS exists:**

Add a Research Findings section to PLAN.md after the Goal/Problem section:

```markdown
## Research Findings
{RESEARCH_FINDINGS}
```

This section should appear before the "Satisfies" section in all templates.

**After generating STATE.md and PLAN.md content, create the issue:**

Call the create-issue.py script with JSON input:

```bash
python3 "${CLAUDE_PLUGIN_ROOT}/scripts/create-issue.py" --json '{
  "major": "{major}",
  "minor": "{minor}",
  "issue_name": "{issue-name}",
  "issue_type": "{issue-type}",
  "dependencies": ["{dep1}", "{dep2}"],
  "state_content": "{full STATE.md content}",
  "plan_content": "{full PLAN.md content}",
  "commit_description": "{one-line description}"
}'
```

The script handles:
- Creating the issue directory
- Writing STATE.md and PLAN.md
- Updating parent version STATE.md
- Git add and commit

Check the JSON output for success status.

</step>

<step name="task_check_parent_decomposition">

**Check if parent task is decomposed (M468):**

After creating a sub-issue, verify if it's being added to a decomposed parent issue.

```bash
# Check if this issue is a sub-issue of a decomposed parent
PARENT_ISSUE_DIR=$(dirname "${ISSUE_DIR}")
if [[ -f "${PARENT_ISSUE_DIR}/STATE.md" ]] && grep -q "^## Decomposed Into" "${PARENT_ISSUE_DIR}/STATE.md"; then
  PARENT_NAME=$(basename "${PARENT_ISSUE_DIR}")

  # Output warning about parent completion requirements
  echo ""
  echo "⚠️  Parent Decomposition Status"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "This sub-issue was added to decomposed parent: ${PARENT_NAME}"
  echo ""
  echo "IMPORTANT: The parent task ${PARENT_NAME} cannot be closed or merged"
  echo "until ALL sub-issues (including this new one) are completed and closed."
  echo ""
  echo "Completion requirements (M467):"
  echo "  1. All sub-issues must be individually completed and closed"
  echo "  2. Parent's own acceptance criteria must be verified"
  echo "  3. Only then can the parent be marked complete"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo ""
fi
```

This check ensures the agent is reminded that decomposed parents require all sub-issues to be completed before the parent can be closed or merged, preventing premature parent completion (M467, M468).

</step>

<step name="task_done">

**Present completion:**

Run the renderer script and output its result verbatim:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/render-add-complete.sh" --type issue --name "{issue-name}" --version "{version}" --issue-type "{type}" --deps "{dependencies}"
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
[ -z "$(ls -d .claude/cat/v[0-9]* 2>/dev/null)" ] && echo "No major versions exist." && exit 0
```

If no major versions exist:
- Inform user: "No major versions exist. Creating one first."
- Set VERSION_TYPE="major", PARENT_TYPE="none"
- Go to step: version_find_next

```bash
ls -1d .claude/cat/v[0-9]* 2>/dev/null | sed 's|.claude/cat/v||' | sort -V
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
find .claude/cat -maxdepth 2 -type d -name "v[0-9]*.[0-9]*" 2>/dev/null | while read d; do
    VERSION=$(basename "$d" | sed 's/v//')
    MAJOR=$(echo "$VERSION" | cut -d. -f1)
    MINOR=$(echo "$VERSION" | cut -d. -f2)
    STATUS=$(grep -oP '(?<=\*\*Status:\*\* )\w+' "$d/STATE.md" 2>/dev/null || echo "open")
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
PARENT_PATH=".claude/cat/issues/v$MAJOR"
[ ! -d "$PARENT_PATH" ] && echo "ERROR: Major version $MAJOR does not exist" && exit 1
```

**If VERSION_TYPE is "patch":**

```bash
MAJOR="{major}"
MINOR="{minor}"
PARENT_PATH=".claude/cat/issues/v$MAJOR/v$MAJOR.$MINOR"
[ ! -d "$PARENT_PATH" ] && echo "ERROR: Minor version $MAJOR.$MINOR does not exist" && exit 1
```

</step>

<step name="version_find_next">

**Determine next version number:**

**If VERSION_TYPE is "major":**

```bash
NEXT_NUMBER=$(ls -1d .claude/cat/v[0-9]* 2>/dev/null | sed 's|.claude/cat/v||' | sort -V | tail -1)
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
NEXT_NUMBER=$(echo "$EXISTING" | tail -1)
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
NEXT_NUMBER=$(echo "$EXISTING" | tail -1)
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
if [ -d ".claude/cat/issues/v$REQUESTED_NUMBER" ]; then
    echo "Version $REQUESTED_NUMBER already exists."
fi
```

**If VERSION_TYPE is "minor":**

```bash
if [ -d "$PARENT_PATH/v$MAJOR.$REQUESTED_NUMBER" ]; then
    echo "Version $MAJOR.$REQUESTED_NUMBER already exists."
fi
```

**If VERSION_TYPE is "patch":**

```bash
if [ -d "$PARENT_PATH/v$MAJOR.$MINOR.$REQUESTED_NUMBER" ]; then
    echo "Patch version $MAJOR.$MINOR.$REQUESTED_NUMBER already exists."
fi
```

**If version already exists:**

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

**If VERSION_TYPE is "major":**

```bash
for v in $(ls -1d .claude/cat/v[0-9]* 2>/dev/null | sed 's|.claude/cat/v||' | sort -rV); do
    if [ "$v" -ge "$REQUESTED_NUMBER" ]; then
        NEW_V=$((v + 1))
        echo "Renumbering v$v -> v$NEW_V"
        mv ".claude/cat/issues/v$v" ".claude/cat/issues/v$NEW_V"
        find ".claude/cat/issues/v$NEW_V" -name "*.md" -exec \
            sed -i "s/v$v\./v$NEW_V./g; s/Major $v/Major $NEW_V/g" {} \;
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
        find "$PARENT_PATH/v$MAJOR.$NEW_V" -name "*.md" -exec \
            sed -i "s/v$MAJOR\.$v/v$MAJOR.$NEW_V/g" {} \;
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
        find "$PARENT_PATH/v$MAJOR.$MINOR.$NEW_P" -name "*.md" -exec \
            sed -i "s/v$MAJOR\.$MINOR\.$p/v$MAJOR.$MINOR.$NEW_P/g" {} \;
    fi
done
```

**Update ROADMAP.md with new version numbers.**

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

**3. Boundaries (M374 - skip unless user mentioned exclusions):**

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

<step name="version_configure_gates">

**Apply standard gates with option for customization (M374):**

Standard gates are applied automatically - do not ask users to confirm obvious requirements.

**Standard gates by version type:**

| Type | Entry Gate | Exit Gate |
|------|------------|-----------|
| Major | Previous major complete (or none) | All minors complete, vision satisfied |
| Minor | Previous minor complete (or none) | All issues complete, tests pass |
| Patch | Issue identified | Fix verified, regression tests pass |

**Only ask if user wants CUSTOM gates:**

Use AskUserQuestion:
- header: "Custom Gates"
- question: "Standard gates will be applied (entry: dependencies complete, exit: all issues + tests pass). Any custom
  gate requirements?"
- options:
  - label: "No, standard gates are sufficient"
    description: "Use default entry/exit criteria"
  - label: "Yes, add custom gates"
    description: "I have specific gate requirements"

**If "Yes, add custom gates":**

Ask inline: "What additional entry or exit gate requirements should be met?"

Append custom gates to standard gates.

</step>

<step name="version_create">

**Create version structure:**

**If VERSION_TYPE is "major":**

```bash
MAJOR=$VERSION_NUMBER
VERSION_PATH=".claude/cat/issues/v$MAJOR"
mkdir -p "$VERSION_PATH/v$MAJOR.0/issue"
```

**Create major STATE.md:**

```bash
cat > "$VERSION_PATH/STATE.md" << EOF
# Major Version $MAJOR State

## Status
- **Status:** open
- **Progress:** 0%
- **Started:** $(date +%Y-%m-%d)
- **Last Updated:** $(date +%Y-%m-%d)

## Minor Versions
- v$MAJOR.0

## Summary
$VERSION_DESCRIPTION
EOF
[ -f "$VERSION_PATH/STATE.md" ] || echo "ERROR: Major STATE.md not created"
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
mkdir -p "$VERSION_PATH/issue"
```

**Create minor STATE.md:**

```bash
cat > "$VERSION_PATH/STATE.md" << EOF
# Minor Version $MAJOR.$MINOR State

## Status
- **Status:** open
- **Progress:** 0%
- **Started:** $(date +%Y-%m-%d)
- **Last Updated:** $(date +%Y-%m-%d)

## Issues Pending
(No issues yet)

## Issues Completed
(None)

## Summary
$VERSION_SUMMARY
EOF
[ -f "$VERSION_PATH/STATE.md" ] || echo "ERROR: Minor STATE.md not created"
```

**Create minor PLAN.md:**

```bash
cat > "$VERSION_PATH/PLAN.md" << EOF
# Minor Version $MAJOR.$MINOR Plan

## Goals
$VERSION_GOALS

## Requirements
$VERSION_REQUIREMENTS

## Entry Gate
$ENTRY_GATE

## Exit Gate
$EXIT_GATE
EOF
[ -f "$VERSION_PATH/PLAN.md" ] || echo "ERROR: Minor PLAN.md not created"
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

**Create STATE.md:**

```bash
cat > "$VERSION_PATH/STATE.md" << EOF
# Patch Version $MAJOR.$MINOR.$PATCH State

## Status
- **Status:** open
- **Progress:** 0%
- **Started:** $(date +%Y-%m-%d)
- **Last Updated:** $(date +%Y-%m-%d)

## Issues Pending
(No issues yet)

## Issues Completed
(None)

## Summary
$VERSION_DESCRIPTION
EOF
[ -f "$VERSION_PATH/STATE.md" ] || echo "ERROR: Patch STATE.md not created"
```

**Create PLAN.md:**

```bash
cat > "$VERSION_PATH/PLAN.md" << EOF
# Patch Version $MAJOR.$MINOR.$PATCH Plan

## Goals
$VERSION_GOALS

## Requirements
$VERSION_REQUIREMENTS

## Entry Gate
$ENTRY_GATE

## Exit Gate
$EXIT_GATE
EOF
[ -f "$VERSION_PATH/PLAN.md" ] || echo "ERROR: Patch PLAN.md not created"
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
ROADMAP=".claude/cat/ROADMAP.md"
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
  sed -i "$((LINE_NUM))a\\  - **$MAJOR.$MINOR.$PATCH:** $VERSION_DESCRIPTION (PENDING)" "$ROADMAP"
else
  echo "WARNING: Minor version $MAJOR.$MINOR entry not found in ROADMAP.md"
fi && \
grep -q "$MAJOR.$MINOR.$PATCH" "$ROADMAP" || echo "WARNING: Patch not added to ROADMAP.md"
```

</step>

<step name="version_update_parent">

**Update parent STATE.md (skip if VERSION_TYPE="major"):**

**If VERSION_TYPE is "major":**
- Skip to step: version_commit (no parent to update)

**If VERSION_TYPE is "minor":**

```bash
PARENT_STATE="$PARENT_PATH/STATE.md" && \
if grep -q "^## Minor Versions" "$PARENT_STATE"; then
  sed -i "/^## Minor Versions/a - v$MAJOR.$MINOR" "$PARENT_STATE"
else
  echo -e "\n## Minor Versions\n- v$MAJOR.$MINOR" >> "$PARENT_STATE"
fi && \
grep -q "v$MAJOR.$MINOR" "$PARENT_STATE" || echo "ERROR: Minor version not added to major STATE.md"
```

**If VERSION_TYPE is "patch":**

```bash
PARENT_STATE="$PARENT_PATH/STATE.md" && \
if grep -q "^## Patch Versions" "$PARENT_STATE"; then
  sed -i "/^## Patch Versions/a - v$MAJOR.$MINOR.$PATCH" "$PARENT_STATE"
else
  echo -e "\n## Patch Versions\n- v$MAJOR.$MINOR.$PATCH" >> "$PARENT_STATE"
fi && \
grep -q "v$MAJOR.$MINOR.$PATCH" "$PARENT_STATE" || echo "ERROR: Patch version not added to minor STATE.md"
```

</step>

<step name="version_commit">

**Commit version creation:**

**If VERSION_TYPE is "major":**

```bash
git add ".claude/cat/issues/v$MAJOR/" ".claude/cat/ROADMAP.md" && \
git commit -m "$(cat <<'EOF'
planning: add major version {major}

{One-line description of major version vision}

Creates Major {major} with initial minor version {major}.0.
EOF
)"
```

**If VERSION_TYPE is "minor":**

```bash
git add "$VERSION_PATH/" ".claude/cat/ROADMAP.md" "$PARENT_PATH/STATE.md" && \
git commit -m "$(cat <<'EOF'
planning: add minor version {major}.{minor}

{One-line description of minor version focus}
EOF
)"
```

**If VERSION_TYPE is "patch":**

```bash
git add "$VERSION_PATH/" ".claude/cat/ROADMAP.md" "$PARENT_PATH/STATE.md" && \
git commit -m "$(cat <<'EOF'
planning: add patch version {major}.{minor}.{patch}

{One-line description of patch version focus}
EOF
)"
```

</step>

<step name="version_done">

**Present completion:**

Run the renderer script and output its result verbatim:

```bash
"${CLAUDE_PLUGIN_ROOT}/scripts/render-add-complete.sh" --type version --name "{version-name}" --version "{version}" --version-type "{VERSION_TYPE}" --parent "{parent-info}" --path "{version-path}"
```

</step>

</process>

<success_criteria>

**For Issue:**
- [ ] Target version selected or created
- [ ] Issue name validated (format and uniqueness)
- [ ] Discussion captured issue details
- [ ] Requirements selected (or explicitly set to None)
- [ ] STATE.md and PLAN.md created
- [ ] Parent STATE.md updated
- [ ] All committed to git

**For Version (Major/Minor/Patch):**
- [ ] Parent version validated (if applicable)
- [ ] Version number determined (with renumbering if needed)
- [ ] Discussion captured focus/scope/vision
- [ ] Requirements derived
- [ ] Gates configured
- [ ] Directory structure created
- [ ] Files created and ROADMAP.md updated
- [ ] Parent STATE.md updated (if applicable)
- [ ] All committed to git

</success_criteria>
