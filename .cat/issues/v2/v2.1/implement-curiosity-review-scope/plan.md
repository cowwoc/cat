# Plan

## Goal

Implement the curiosity level review scope so each level determines how broadly stakeholder review and
research considers system context when evaluating an issue. Currently curiosity maps to the old `effort`
level which controlled planning depth, not review scope. This issue redefines behavior in terms of
automatic vs. manual triggering and narrow vs. holistic analysis scope.

## Curiosity Level Definitions

### low — skip automatic review (user-triggered only)

Stakeholder review and research do NOT run automatically as part of `/cat:work`.
They only run if the user explicitly invokes them (e.g., `/cat:stakeholder-review-agent` directly).

Suitable for highly-trusted teams with established code review processes who find the automated review
cycle adds friction without value.

### medium — automatic, scoped to immediate issue (current default behavior)

Stakeholder review and research run automatically as part of `/cat:work`.

Scope is limited to:
- Files changed in the implementation
- Direct dependencies referenced in the changed files
- The issue's own plan.md post-conditions and goal

Reviewers are instructed to focus on: "Does this change correctly and completely implement its stated
goal without introducing regressions in the changed files and their direct dependencies?"

This is the current behavior.

### high — automatic, holistic system integration

Stakeholder review and research run automatically as part of `/cat:work`.

Scope is expanded to consider the broader system:
- How does this change interact with other open issues in the same version?
- Are there architectural patterns in the rest of the codebase that this change should follow or
  that this change might inadvertently break?
- Are there cross-cutting concerns (security, performance, accessibility) that need validation
  beyond the immediately changed files?

Reviewer prompts include explicit instructions to read surrounding code context (not just the diff)
and consider downstream impact on consumers of changed APIs or interfaces.

Research (`cat:research-agent`) also runs with broader context:
- Surveys existing patterns in the codebase before proposing an approach
- Checks whether similar problems have been solved elsewhere in the repo

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Changing skip behavior may suppress review for users who rely on caution=low currently
- **Mitigation:** curiosity=low and caution=low are independent skip conditions; both remain honored

## Files to Modify

- `plugin/skills/work-review-agent/first-use.md` — add curiosity=low skip condition and curiosity=high
  research invocation
- `plugin/skills/stakeholder-review-agent/first-use.md` — update REVIEW_SCOPE for high curiosity to be
  explicitly holistic
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/CuriosityLevel.java` — update Javadoc to reflect
  review scope behavior (not planning depth)
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/CuriosityLevelTest.java` — new file: unit tests
  for CuriosityLevel.fromString() and toString() routing logic

## Pre-conditions

(none)

## Sub-Agent Waves

### Wave 1

- Update `client/src/main/java/io/github/cowwoc/cat/hooks/util/CuriosityLevel.java`: replace the class
  Javadoc and all three enum constant Javadocs to match review-scope behavior:
  - Class Javadoc: "How broadly stakeholder review and research considers system context when evaluating an
    issue."
  - LOW Javadoc: "Skip automatic stakeholder review. Review only runs if the user explicitly invokes it."
  - MEDIUM Javadoc: "Run automatic stakeholder review scoped to changed files and direct dependencies."
  - HIGH Javadoc: "Run automatic stakeholder review with holistic system integration scope. Reviewers
    consider broader codebase context, cross-cutting concerns, and downstream impact."
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/util/CuriosityLevel.java`
- Create `client/src/test/java/io/github/cowwoc/cat/hooks/test/CuriosityLevelTest.java` with tests:
  - `fromStringLowReturnsLow` — verifies fromString("low") returns LOW
  - `fromStringMediumReturnsMedium` — verifies fromString("medium") returns MEDIUM
  - `fromStringHighReturnsHigh` — verifies fromString("high") returns HIGH
  - `fromStringCaseInsensitive` — verifies fromString("LOW"), fromString("Medium"), fromString("HIGH") all work
  - `fromStringBlankThrowsIllegalArgumentException` — verifies fromString("") throws IllegalArgumentException
  - `fromStringUnknownValueThrowsIllegalArgumentException` — verifies fromString("invalid") throws
    IllegalArgumentException
  - `toStringReturnsLowercase` — verifies LOW.toString()="low", MEDIUM.toString()="medium",
    HIGH.toString()="high"
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/CuriosityLevelTest.java`
- Run `mvn -f client/pom.xml verify -e` and ensure all tests pass
- Update index.json with status: closed, progress: 100% in this wave's final commit

### Wave 2

- Update `plugin/skills/stakeholder-review-agent/first-use.md`:
  - Find the `case "$CURIOSITY" in` block (around line 373)
  - Replace the `high)` REVIEW_SCOPE value with:
    `"Review the broader system context. For each changed file, read the surrounding code that references
    or depends on it. Consider: (1) how this change interacts with other open issues in the same version,
    (2) architectural patterns in the rest of the codebase this change should follow or might inadvertently
    break, (3) cross-cutting concerns (security, performance, accessibility) beyond immediately changed
    files. Flag pre-existing issues in any file you read. Consider downstream impact on consumers of
    changed APIs or interfaces."`
  - Files: `plugin/skills/stakeholder-review-agent/first-use.md`
- Update `plugin/skills/work-review-agent/first-use.md`:
  - In the "Skip Review if Configured" section (after the CAUTION validation block, around line 172-193),
    add a new block to read and check CURIOSITY from effective config:
    ```bash
    # Read CURIOSITY from effective config to determine review triggering
    EFFECTIVE_CONFIG_FOR_CURIOSITY=$("${CLAUDE_PLUGIN_ROOT}/client/bin/get-config-output" effective) || {
        echo "ERROR: Failed to read effective config" >&2
        exit 1
    }
    CURIOSITY=$(echo "$EFFECTIVE_CONFIG_FOR_CURIOSITY" | \
        grep -o '"curiosity"[[:space:]]*:[[:space:]]*"[^"]*"' | \
        sed 's/.*"curiosity"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/')
    if [[ -z "$CURIOSITY" ]]; then
        CURIOSITY="medium"
    fi
    if [[ "$CURIOSITY" == "low" ]]; then
        echo "Review skipped (curiosity: ${CURIOSITY})"
        # Output JSON result indicating review was skipped
    fi
    ```
  - The skip block should output a valid JSON result matching the Output Contract (status: REVIEW_PASSED,
    empty concern arrays, allCommitsCompact passed through) and return early.
  - The CURIOSITY=low skip should appear AFTER the CAUTION=low skip check, so both remain independent.
  - For curiosity=high: before the "Invoke Stakeholder Review" sub-section, add a step that invokes
    `cat:research-agent` with broader codebase survey context:
    ```
    If CURIOSITY == "high": invoke cat:research-agent with args describing the issue and requesting
    a broader codebase survey (existing patterns, similar problems solved elsewhere in the repo).
    Pass the research results as additional context to stakeholder reviewers via DOMAIN_KNOWLEDGE or
    as supplementary context in the reviewer prompts.
    ```
  - Files: `plugin/skills/work-review-agent/first-use.md`

### Wave 3

- Create `tests/curiosity-review-scope.bats` — a Bats test file that validates the CURIOSITY extraction and
  branching logic extracted from `work-review-agent/first-use.md` and `stakeholder-review-agent/first-use.md`:
  - Extract the CURIOSITY-reading snippet (the `grep -o` + `sed` + empty-fallback pattern) into a testable
    helper function inside the Bats file.
  - Test `curiosity_from_json`: given JSON containing `"curiosity": "low"`, returns `"low"`.
  - Test `curiosity_from_json`: given JSON containing `"curiosity": "medium"`, returns `"medium"`.
  - Test `curiosity_from_json`: given JSON containing `"curiosity": "high"`, returns `"high"`.
  - Test `curiosity_from_json`: given JSON with NO curiosity field, returns the default `"medium"`.
  - Test skip-branch: when CURIOSITY is `"low"`, the skip condition (`[[ "$CURIOSITY" == "low" ]]`) evaluates
    true; when CURIOSITY is `"medium"` or `"high"`, it evaluates false.
  - Test research-branch: when CURIOSITY is `"high"`, the research-invocation condition
    (`[[ "$CURIOSITY" == "high" ]]`) evaluates true; when `"low"` or `"medium"`, it evaluates false.
  - Test REVIEW_SCOPE assignment: given CURIOSITY `"low"`, REVIEW_SCOPE equals the low-scope string; given
    `"medium"`, the medium-scope string; given `"high"`, the holistic-system-context string.
  - Run the Bats suite with `bats tests/curiosity-review-scope.bats` and confirm all tests pass (exit 0).
  - Files: `tests/curiosity-review-scope.bats`

## Post-conditions

- [ ] curiosity=low: stakeholder review and research are skipped in /cat:work; no automatic invocation
- [ ] curiosity=low: user can still manually trigger review via explicit skill invocation
- [ ] curiosity=medium: stakeholder review runs automatically, scoped to changed files and direct deps (current behavior preserved)
- [ ] curiosity=high: stakeholder review runs with expanded scope; reviewer prompts include explicit instructions to consider broader system context
- [ ] curiosity=high: research skill (cat:research-agent) is invoked with broader codebase survey context
- [ ] curiosity=high reviewer prompts: each stakeholder receives instructions to read surrounding files and consider downstream impact
- [ ] curiosity level read from effective config in work-with-issue orchestration before spawning reviewers
- [ ] Unit tests for curiosity level routing logic
- [ ] No regressions in existing curiosity=medium workflows
- [ ] E2E: run /cat:work at curiosity=low and verify no review runs; curiosity=medium verify scoped review; curiosity=high verify holistic reviewer prompt is used
