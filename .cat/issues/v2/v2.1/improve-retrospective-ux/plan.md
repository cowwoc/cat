# Plan: improve-retrospective-ux

## Goal

Improve the retrospective analysis output box in `GetRetrospectiveOutput.java` to address 10 UX
problems: truncated descriptions, ambiguous notation, no executive summary, poor scannability, cognitive
overload, and unclear timestamps. The changes make the output immediately actionable without scrolling.

## Parent Requirements

None

## Risk Assessment

- **Risk Level:** MEDIUM
- **Concerns:** Many simultaneous changes to `GetRetrospectiveOutput.java`; risk of merge conflicts
  between waves; display width constraints may affect new section designs
- **Mitigation:** Use sub-agent waves on separate methods; run full test suite after each wave; keep
  changes within existing `displayWidth` constraint

## Files to Modify

- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/GetRetrospectiveOutput.java` — all 10
  UX changes (see Execution Steps for specifics)
- `client/src/test/java/io/github/cowwoc/cat/hooks/skills/GetRetrospectiveOutputTest.java` — update
  and add tests for all changed output formats

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1 — Data formatting fixes (independent methods, no new sections)

- **Fix Problem 10: Trigger phrasing** — In `generateAnalysis()`, change trigger line from
  `"Trigger: N mistakes accumulated (threshold: X)"` to
  `"Trigger: N mistakes since last retrospective (threshold: X)"` and for time-based:
  `"Trigger: N days since last retrospective (threshold: X days)"`.
  Files: `GetRetrospectiveOutput.java`

- **Fix Problem 9: Timestamp formatting** — In `formatPeriod()` (or equivalent), replace raw ISO
  8601 timestamps with human-readable format: `"Mar 5 7:10 PM — Mar 8 2:27 PM (3 days, 7 hours)"`.
  Use `DateTimeFormatter` with pattern `"MMM d h:mm a"` and `ZoneOffset.UTC`. Calculate duration
  using `Duration.between()`, format as `"N days"` or `"N days, M hours"`. Strip nanoseconds.
  Files: `GetRetrospectiveOutput.java`

- **Fix Problem 5: Category sort order** — In `generateCategoryBreakdownLines()`, change
  `categories.sort(String::compareTo)` to sort by count descending. Add percentage alongside count:
  format each line as `"  {category}: {count} ({pct}%)"` where `pct = count * 100 / total`.
  If more than 8 categories, show top 8 and summarize the rest as `"  [N more categories]"`.
  Files: `GetRetrospectiveOutput.java`

### Wave 2 — Semantic improvements (pattern notation, action item grouping)

- **Fix Problem 2: Ambiguous X/Y notation** — In `generatePatternStatusLines()`, replace
  `"occurrences: X/Y"` with semantic status:
  - If `occurrences_after_fix > occurrences_total`: `"WORSENING — {total} total, {after} after fix"`
  - If `occurrences_after_fix == occurrences_total`: `"NO IMPROVEMENT — {total} detected, {after} after fix"`
  - If `0 < occurrences_after_fix < occurrences_total`: `"IMPROVING — {total} total, {after} remaining"`
  - If `occurrences_after_fix == 0`: `"RESOLVED — {total} total"`
  Sort patterns by severity (WORSENING first, then NO IMPROVEMENT, then IMPROVING, then RESOLVED).
  Files: `GetRetrospectiveOutput.java`

- **Fix Problem 7: Priority grouping** — In `generateOpenActionItemLines()`, group items by priority
  (HIGH → MEDIUM → LOW) with section headers. Format:
  ```
    HIGH PRIORITY (N items):
      A001: description...
    MEDIUM PRIORITY (N items):
      A003: description...
  ```
  Files: `GetRetrospectiveOutput.java`

- **Fix Problem 3: New vs recurring indicators** — In `generateOpenActionItemLines()`, prefix each
  item with `[NEW]` if it was created in this retrospective period, or `[recurring × N]` if it has
  appeared in N previous retrospectives. Source `created_date` from action item JSON. If action item
  `created_date` is within the current period, mark as NEW; otherwise count how many retrospectives
  have passed since creation and show the count.
  Files: `GetRetrospectiveOutput.java`

- **Run `mvn -f client/pom.xml test`** to confirm Wave 2 changes pass all tests.
  Files: none

### Wave 3 — New sections (executive summary, descriptions, action guidance)

- **Fix Problem 1: Full descriptions** — Add new method `generateActionItemDetailsLines()` that
  renders the FULL (untruncated) description for each action item. Call it from `generateAnalysis()`
  after the open action items section. Format:
  ```
  Action Item Details:
    A001: Full description text here, not truncated
    A003: Full description text here, not truncated
  ```
  In `generateEffectivenessLines()` and `generateOpenActionItemLines()`, truncate descriptions to
  60 characters with `"... (see details)"` suffix instead of ellipsis alone.
  Files: `GetRetrospectiveOutput.java`

- **Fix Problem 6: Action guidance** — In `generateEffectivenessLines()`, add a second line per
  item with verdict-specific next step:
  - `"effective"` → `"     → Continue monitoring"`
  - `"ineffective"` → `"     → Escalate: root cause may be misdiagnosed"`
  - `"partially_effective"` → `"     → Refine approach based on remaining occurrences"`
  - `"pending"` → `"     → Awaiting data to evaluate"`
  Files: `GetRetrospectiveOutput.java`

- **Fix Problem 4: Executive summary** — Add new method `generateExecutiveSummaryLines()` called
  from `generateAnalysis()` BEFORE the trigger/period lines. The summary shows 3 bullets:
  1. Top mistake category: `"• Top category: {name} ({count} mistakes, {pct}%)"`
  2. Worsening patterns count: `"• {N} pattern(s) WORSENING — fixes not effective"` (omit if N=0)
  3. High-priority open items: `"• {N} high-priority action item(s) require attention"`
  Files: `GetRetrospectiveOutput.java`

- **Fix Problem 8: Section visual separation** — In `generateAnalysis()`, add a blank line between
  each major section (Category Breakdown, Action Item Effectiveness, Pattern Status, Open Action
  Items, Action Item Details). Use `contentLines.add("")` between sections.
  Files: `GetRetrospectiveOutput.java`

- **Add/update tests** for all Wave 3 changes in `GetRetrospectiveOutputTest.java`. Include tests
  for: executive summary content, full descriptions in details section, verdict guidance text,
  section separator blank lines.
  Files: `client/src/test/java/io/github/cowwoc/cat/hooks/skills/GetRetrospectiveOutputTest.java`

- **Run `mvn -f client/pom.xml test`** to confirm all tests pass.
  Files: none

## Post-conditions

- [ ] Category breakdown sorted by frequency descending, with percentages
- [ ] Pattern status uses WORSENING/IMPROVING/RESOLVED/NO IMPROVEMENT labels instead of X/Y numbers
- [ ] Open action items grouped by priority (HIGH/MEDIUM/LOW) with section headers
- [ ] Open action items show [NEW] or [recurring × N] indicators
- [ ] Executive summary section appears before trigger/period with 3 key bullets
- [ ] Action item descriptions truncated to 60 chars with `"... (see details)"` in main sections
- [ ] Full descriptions shown in "Action Item Details" section at the end
- [ ] Each effectiveness verdict includes a next-step recommendation line
- [ ] Major sections separated by blank lines
- [ ] Period shown as `"MMM d h:mm a — MMM d h:mm a (N days)"` format
- [ ] Trigger phrasing says `"since last retrospective"` explicitly
- [ ] All tests pass (`mvn -f client/pom.xml test`)
- [ ] E2E: Run `/cat:retrospective` and confirm all 10 UX improvements are visible in the output
