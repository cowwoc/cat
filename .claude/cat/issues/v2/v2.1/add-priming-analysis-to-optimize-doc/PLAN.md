# Plan: add-priming-analysis-to-optimize-doc

## Goal
Add a pre-compression analysis step to the optimize-doc workflow that identifies negative rules ("what not to do") in
the target document, searches all Claude-facing project files for priming content that causes the wrong behavior, and
removes both the rules and priming when empirical tests confirm the fix. When no priming source is found, the step
completes silently and the compression agent applies the standard negative→positive rewrite instead.

## Satisfies
None

## Approaches

### A: Workflow-only change (first-use.md)
- **Risk:** LOW
- **Scope:** 2 files (minimal)
- **Description:** Insert new step in first-use.md between current Steps 1 and 2; update COMPRESSION-AGENT.md to
  note negative→positive is a fallback when priming source not eliminated. No Java changes needed since optimize-doc
  orchestration lives in first-use.md.

### B: Java pre-processor
- **Risk:** MEDIUM
- **Scope:** 5+ files (comprehensive)
- **Description:** Implement a Java handler that performs priming analysis before the skill runs. More testable but
  requires Java compilation and higher complexity for what is essentially workflow logic.

**Selected: Approach A** — optimize-doc orchestration is documented workflow; adding a step maintains consistency
with existing skill architecture.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Empirical test quality depends on how well the "wrong behavior" is inferred from the negative rule;
  overly aggressive priming search may produce false positives
- **Mitigation:** Conservative inference (only act on explicit negative rule patterns like NEVER/DO NOT/don’t/avoid);
  require priming content to explicitly describe the forbidden behavior (keyword match); silently revert on test
  failure rather than erroring

## Files to Modify
- `plugin/skills/optimize-doc/first-use.md` — insert new Step 2 (priming analysis); renumber old Steps 2–6 to
  3–7; update all internal step references
- `plugin/skills/optimize-doc/COMPRESSION-AGENT.md` — update "Negative → Positive" section to note it is a
  fallback for when priming source was not eliminated in Step 2

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1

- Update `plugin/skills/optimize-doc/first-use.md`:
  - Files: `plugin/skills/optimize-doc/first-use.md`
  - Locate Step 1 (Validate Document Type) and old Step 2 (Check for Existing Baseline)
  - Insert new **Step 2: Priming Analysis** between them (content in detail section below)
  - Renumber old Step 2 → Step 3, Step 3 → Step 4, Step 4 → Step 5, Step 5 → Step 6, Step 6 → Step 7
  - Update all cross-references within the file (e.g., "Go to Step 6" → "Go to Step 7")

  **New Step 2 content:**

  ```
  ### Step 2: Priming Analysis

  BEFORE compression, scan the document for negative rules and attempt to eliminate their root cause.

  #### 2a: Detect Negative Rules

  Scan the document for explicit negative rule patterns:
  - Lines containing: NEVER, DO NOT, DON’T, don’t, MUST NOT, must not, PROHIBITED, FORBIDDEN, avoid, Never, Do not
  - Extract the forbidden behavior for each rule (e.g., "NEVER use X" → forbidden behavior = "use X")

  If no negative rule patterns found: skip to Step 3 (no priming analysis needed).

  #### 2b: Search for Priming Sources

  For each forbidden behavior identified, search ALL Claude-facing files for content that primes the agent toward
  that behavior. Claude-facing files are:
  - plugin/skills/**/*.md
  - plugin/agents/**/*.md
  - plugin/hooks/**/*.md (concept/reference docs)
  - .claude/rules/**/*.md
  - CLAUDE.md
  - client/src/main/java/**/InjectSessionInstructions.java

  Search strategy: For each forbidden behavior, extract 2-3 key keywords and grep across all Claude-facing files
  for those keywords. Consider a file a priming source only if it:
  1. Contains the same keywords as the forbidden behavior, AND
  2. Describes or encourages that behavior (not just mentions it in a prohibition context)

  If no priming sources found for ANY negative rule: skip to Step 3 (compression agent handles
  negative→positive rewrite via COMPRESSION-AGENT.md).

  #### 2c: Remove Negative Rules from Document

  For each negative rule that has an identified priming source:
  - Remove the rule from the document using the Edit tool
  - Track which rules were removed (for potential revert)

  #### 2d: Empirical Test — Confirm Priming Exists

  For each identified priming source, spawn a subagent (Task tool, general-purpose) to test whether the agent
  still exhibits the forbidden behavior with the rules removed but priming intact. Subagent reports:
  BEHAVIOR_OBSERVED or BEHAVIOR_NOT_OBSERVED.

  If all tests report BEHAVIOR_NOT_OBSERVED: rules were the only guard (no active priming).
  Revert rule removals from step 2c and skip to Step 3.

  #### 2e: Remove Priming Sources

  For each confirmed priming source (BEHAVIOR_OBSERVED in 2d):
  - Remove the priming content from the source file using the Edit tool
  - Track which edits were made (for potential revert)

  #### 2f: Empirical Test — Confirm Priming Eliminated

  Re-run the same empirical tests from step 2d.

  If all tests report BEHAVIOR_NOT_OBSERVED: priming eliminated. Keep all removals. Continue to Step 3.

  If any test reports BEHAVIOR_OBSERVED: priming source may be deeper.
  - Silently revert ALL changes from steps 2c and 2e (restore original document and priming files)
  - Continue to Step 3 (compression agent will handle negative→positive rewrite)
  ```

- Update `plugin/skills/optimize-doc/COMPRESSION-AGENT.md`:
  - Files: `plugin/skills/optimize-doc/COMPRESSION-AGENT.md`
  - In the `**Negative → Positive:**` subsection under `## Normalization for Clarity`, add a note before the
    existing examples:
    > Note: Negative rules that reach this compression step were not eliminated by priming analysis
    > (no priming source found or elimination failed). Convert them to positive actionable instructions.

## Post-conditions
- [ ] first-use.md Step 2 describes the priming analysis procedure with sub-steps 2a–2f
- [ ] Old Steps 2–6 in first-use.md are renumbered to 3–7 with all internal cross-references updated
- [ ] COMPRESSION-AGENT.md notes that negative→positive conversion is a fallback for uneliminated rules
- [ ] Step 2 completes silently when no negative rules are found in the document
- [ ] Step 2 completes silently when no priming sources are found
- [ ] On test failure in 2f, all document and priming file edits are silently reverted
- [ ] E2E: Run optimize-doc on a document containing a "NEVER do X" rule where a Claude-facing file primes
  the agent toward doing X; confirm the rule is removed from the document AND the priming content is removed
  from the source file