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

**Shortcut:** When invoked with a description argument (e.g., say 'add an issue: make installation easier'),
treats the argument as a issue description and skips directly to issue creation workflow.

**Research-then-propose model:** Instead of a multi-step wizard, the skill researches all fields upfront
(versions, requirements, skill dependencies, name suggestions), renders a single proposal display box, then asks
conversationally for approval. Zero AskUserQuestion calls on the happy path.

**Post-completion workflow:** After issue or version creation completes, offer any next-step workflow
progression using AskUserQuestion — do NOT mention internal slash commands (e.g., `/cat:work`,
`/cat:status`) in conversational text. Internal slash commands are not visible to users.

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

If the skill was invoked with arguments (e.g., user said 'add an issue: make installation easier'):
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
  step: issue_research_proposal (bypassing select_type and the freeform description question)

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

**Gather issue intent BEFORE researching:**

If ISSUE_DESCRIPTION already set (from command args):
- Continue directly to step: issue_research_proposal

Otherwise, ask for description (FREEFORM):

Ask inline: "What do you want to accomplish? Describe the issue you have in mind."

Capture as ISSUE_DESCRIPTION, then continue to step: issue_research_proposal.

</step>

<step name="issue_research_proposal">

**Research all fields upfront and prepare proposal data:**

This step performs all analysis needed to render a comprehensive proposal without wizard questions.

**1. Read and validate configuration:**

Read the `curiosity` value from effective config:

```bash
CONFIG=$("${CLAUDE_PLUGIN_ROOT}/client/bin/get-config-output" effective)
if [[ $? -ne 0 ]]; then
    echo "ERROR: Failed to read effective config" >&2
    exit 1
fi
CURIOSITY=$(echo "$CONFIG" | grep -o '"curiosity"[[:space:]]*:[[:space:]]*"[^"]*"' \
  | sed 's/.*"curiosity"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
if [[ -z "$CURIOSITY" ]]; then
    echo "ERROR: 'curiosity' key not found in effective config." >&2
    exit 1
fi
```

Store CURIOSITY for use in downstream steps.

**2. Analyze issue description:**

Analyze ISSUE_DESCRIPTION to determine likely:
- Issue type (feature/bugfix/refactor/performance) based on action verbs and context
- Scope estimate (1-2 files, 3-5 files, or 6+ files) based on description and type
- Key skill dependencies by scanning for patterns like `plugin/skills/<name>/` or "modify <name> skill"

Store these as ISSUE_TYPE, SCOPE_ESTIMATE, and SKILL_NAMES list.

**3. Generate standard post-conditions:**

Based on ISSUE_TYPE, set POSTCONDITIONS to standard post-conditions:

| Type | Standard Post-conditions |
|------|--------------------------|
| Feature | Functionality works, Tests passing, No regressions, E2E verification |
| Bugfix | Bug fixed, Regression test added, No new issues, E2E verification |
| Refactor | User-visible behavior unchanged, Tests passing, Code quality improved, E2E verification |
| Performance | Target met, Benchmarks added, No functionality regression, E2E verification |

**4. Suggest issue name:**

Generate 2-3 suggested names based on ISSUE_DESCRIPTION and ISSUE_TYPE:
- Extract key verbs and nouns from description
- Apply standard prefixes (add-, fix-, refactor-, optimize-, etc.)
- Keep names under 50 characters, lowercase with hyphens
- Check uniqueness against existing issues in all versions

Store as NAME_SUGGESTIONS list with first suggestion as PRIMARY_NAME.

**5. Analyze versions:**

Use version data from output.versions (pre-filtered to exclude closed versions).

For each version:
- Extract version number, status, and issue_count
- Score fit against ISSUE_DESCRIPTION using keyword matching and domain alignment
- Rank by fit (topic alignment weighted higher than issue grouping)

Identify BEST_FIT_VERSION and second-best match if applicable.

**6. Detect skill dependencies:**

If SKILL_NAMES is non-empty, scan open issues for plan.md files that reference the same skills.
Collect matching issue IDs into AUTO_DETECTED_DEPS (deduplicated).

If AUTO_DETECTED_DEPS is non-empty, store for proposal display.

**7. Read parent version requirements:**

Extract REQ-XXX items from the best-fit version's plan.md.
Store as VERSION_REQUIREMENTS (may be empty).

**8. Prepare proposal structure:**

Organize all researched data into a proposal object containing:
- issue_description
- issue_type
- primary_name
- name_suggestions
- best_fit_version
- version_alternatives (up to 2 other versions)
- scope_estimate
- auto_detected_deps (if non-empty)
- version_requirements (if non-empty)

Continue to step: issue_render_proposal.

</step>

<step name="issue_render_proposal">

**Render proposal display box via cat:get-output-agent:**

Invoke the output rendering skill to display the proposal as a formatted box:

```bash
PROPOSAL_JSON=$(cat <<'PROPOSAL_EOF'
{
  "issue_description": "${ISSUE_DESCRIPTION}",
  "issue_type": "${ISSUE_TYPE}",
  "primary_name": "${PRIMARY_NAME}",
  "name_suggestions": [${NAME_SUGGESTIONS}],
  "best_fit_version": "${BEST_FIT_VERSION}",
  "version_alternatives": [${VERSION_ALTERNATIVES}],
  "scope_estimate": "${SCOPE_ESTIMATE}",
  "auto_detected_deps": [${AUTO_DETECTED_DEPS}],
  "version_requirements": [${VERSION_REQUIREMENTS}]
}
PROPOSAL_EOF
)

${CLAUDE_PLUGIN_ROOT}/client/bin/get-output-agent proposal-issue "$PROPOSAL_JSON"
```

The output agent renders a comprehensive display box showing:
- Proposed issue name with alternatives
- Issue description and type
- Target version (with reason for selection)
- Scope estimate
- Any auto-detected skill dependencies
- Parent version requirements (if any)

After rendering, continue to step: issue_approve_proposal.

</step>

<step name="issue_approve_proposal">

**Ask conversationally for approval:**

Display message: "Does this look good? Should I create it?"

This is a conversational check (not AskUserQuestion) — allow user to:
- Say "yes" / "looks good" / "create it" to proceed to step: issue_validate_criteria
- Ask clarifying questions about the proposal
- Request changes to specific fields

**If user asks for changes:**

Guide the user to redefine specific fields:
- To change the name: "I'll use {new-name} instead"
- To change the type: "I'll mark this as a {new-type}"
- To change the version: "I'll target {new-version}"
- To add/remove dependencies: "I'll {add/remove} {dependency}"

Apply any user-specified changes and re-render the proposal (loop back to issue_render_proposal).

**If user approves (conversational yes):**

Continue to step: issue_validate_criteria.

</step>

<step name="issue_research">

**Research-then-propose flow complete, continue to creation:**

The research and proposal phases are now complete. Continue to step: issue_create to finalize the issue creation.

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

**Analyze potential impact of the proposed issue on existing features (curiosity-scaled):**

Initialize IMPACT_NOTES="".

Use the CURIOSITY value set in issue_read_config.

**If CURIOSITY is "low":**

Skip this step entirely. Continue to step: issue_create.

**If CURIOSITY is "medium":**

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

Inform user: "Say 'add an issue' for each sub-issue. You may use the current description as a starting point for
each." Then STOP execution.

**If "Add impact notes to plan":**

Set IMPACT_NOTES to the concern descriptions. These will be appended to the plan.md in the issue_create step.

**If no concerns are detected:**

Skip silently to step: issue_create.

**If CURIOSITY is "high":**

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

Inform user: "Say 'add an issue' for each sub-issue. You may use the current description as a starting point for
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
plan_temp_file=$(mktemp --suffix=.md)
```

2. Write the lightweight plan.md to `${plan_temp_file}` using the Write tool with the following structure:

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

3. Write the index.json content to a unique temporary file (multi-instance safe):

```bash
index_temp_file=$(mktemp /tmp/cat-index-XXXXXX.json)
cat > "${index_temp_file}" << 'INDEXEOF'
{full index.json content}
INDEXEOF
```

4. Create the issue, passing both generated temp file paths.

**JSON escaping:** All interpolated string values ({issue-name}, {one-line description}, dependency names) must be
valid JSON string values. Escape any double quotes as `\"` and backslashes as `\\` before embedding them in the JSON
argument. Index content is written to a temp file and does not need JSON escaping.

```bash
"${CLAUDE_PLUGIN_ROOT}/client/bin/create-issue" --json '{
  "major": "${BEST_FIT_VERSION%.*}",
  "minor": "${BEST_FIT_VERSION#*.}",
  "issue_name": "${PRIMARY_NAME}",
  "issue_type": "${ISSUE_TYPE}",
  "dependencies": [${AUTO_DETECTED_DEPS_JSON}],
  "index_file": "'"${index_temp_file}"'",
  "plan_file": "'"${plan_temp_file}"'",
  "commit_description": "${ISSUE_DESCRIPTION}"
}'
```

The script handles:
- Creating the issue directory
- Writing index.json (always pretty-printed) and plan.md
- Updating parent version index.json
- Git add and commit

Check the JSON output for success status. If create-issue returns an error, clean up both temporary files
before reporting the error and stopping:

```bash
rm -f "${plan_temp_file}" "${index_temp_file}"
```

Clean up both temporary files after a successful create-issue call:

```bash
rm -f "${plan_temp_file}" "${index_temp_file}"
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
"$CLIENT_BIN/get-add-output" --type issue --name "${PRIMARY_NAME}" --version "${BEST_FIT_VERSION}" --issue-type "${ISSUE_TYPE}" --dependencies "${AUTO_DETECTED_DEPS_JSON}"
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
    STATUS=$(grep '"status"' "$d/index.json" 2>/dev/null | sed 's/.*"status"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/' || echo "open")
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
