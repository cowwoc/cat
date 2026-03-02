# Plan: add-monthly-winner-program

## Goal
Add a "Monthly Winner Program" section to README.md featuring a Hall of Fame that showcases one public-facing project
each month that uses CAT for development. Winners receive a free Enterprise license for one year and are listed in a
rolling 12-month Hall of Fame at the bottom of the README.

## Satisfies
None

## Approaches

### A: Static Markdown Table
- **Risk:** LOW
- **Scope:** 1 file (minimal)
- **Description:** Add a simple Markdown table at the bottom of README.md with columns for month/year, project name,
  project link, and a brief description. Maintainers manually edit the table each month to add new winners and remove
  entries older than 12 months.

### B: Structured Section with Rules and Table
- **Risk:** LOW
- **Scope:** 1 file (minimal)
- **Description:** Add a full section including program description (eligibility, prize details, how to participate),
  followed by the Hall of Fame table. More self-documenting but slightly longer.

> Approach B is recommended: the section should be self-explanatory for visitors who discover it without prior context.

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Section may become stale if not updated monthly; no automated enforcement
- **Mitigation:** Keep the format simple enough that manual monthly updates take under a minute

## Files to Modify
- `README.md` — Add "Monthly Winner Program" section at the bottom of the file, containing:
  1. Program overview paragraph (what it is, eligibility, prize)
  2. Hall of Fame table (Month | Project | Description)
  3. The table starts empty with a note indicating the first winner will be announced

## Pre-conditions
- [ ] All dependent issues are closed

## Execution Waves

### Wave 1
- Add a new `## Monthly Winner Program` section at the bottom of `README.md`
  - Files: `README.md`
- Include a brief program description paragraph covering:
  - One public-facing project selected each month
  - Project must use CAT for development
  - Winner receives a free Enterprise license for one year
  - Winner is featured in the Hall of Fame below
  - Files: `README.md`
- Add a `### Hall of Fame` subsection with a Markdown table:
  - Columns: `Month` | `Project` | `Description`
  - Table starts empty (no data rows yet)
  - Add a note: "*First winner to be announced soon. Stay tuned!*"
  - Files: `README.md`
- The Hall of Fame displays winners from the past 12 months (rolling window); older entries are removed during monthly
  updates
  - Files: `README.md`

## Post-conditions
- [ ] README.md contains a "Monthly Winner Program" section at the bottom of the file
- [ ] The section includes a program overview paragraph explaining eligibility and the Enterprise license prize
- [ ] The section includes a "Hall of Fame" subsection with a Markdown table (Month, Project, Description columns)
- [ ] The table is initially empty with placeholder text indicating the first winner is upcoming
- [ ] The section documents that the Hall of Fame shows winners from the past 12 months
- [ ] E2E: README.md renders correctly in a Markdown viewer with the new section visible and properly formatted
