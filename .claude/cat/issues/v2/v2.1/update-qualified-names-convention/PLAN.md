# Plan: update-qualified-names-convention

## Goal

Extend the qualified names convention in `plugin/rules/qualified-issue-names.md` to cover skills and
files in addition to issues. Bare names (e.g., `git-rebase-agent`) are only acceptable for user input;
all internal references in responses, planning files, and agent instructions must use qualified names
(e.g., `cat:git-rebase-agent`).

## Parent Requirements

None

## Files to Modify

- `plugin/rules/qualified-issue-names.md` — extend to cover skills (`cat:skill-name`) and file
  references (full paths); update title and format to reflect broader scope

## Pre-conditions

- [ ] All dependent issues are closed

## Sub-Agent Waves

### Wave 1

- Update `plugin/rules/qualified-issue-names.md` to add skill and file naming sections:
  - Files: `plugin/rules/qualified-issue-names.md`

  New content (replace existing file body after frontmatter):

  ```markdown
  ## Qualified Names
  **MANDATORY**: Always use fully-qualified names when referencing issues, skills, and files.
  Bare names exist only to facilitate easier user input; qualified names are preferred everywhere else.

  **Issues** — Format: `{major}.{minor}-{bare-name}` (e.g., `2.1-create-config-property-enums`)

  **Applies to**: All free-text responses — after adding issues, when suggesting next work, when
  summarizing created issues, when referencing issues in any context.

  **Never use** bare issue names (e.g., `create-config-property-enums`) in agent-to-user text.

  **Skills** — Format: `{prefix}:{bare-name}` (e.g., `cat:git-rebase-agent`)

  **Applies to**: All free-text responses, planning files, SKILL.md content, and agent instructions
  when referring to skills by name.

  **Never use** bare skill names (e.g., `git-rebase-agent`) in agent-to-user text.

  **Files** — Use full paths relative to the project root (e.g., `plugin/rules/qualified-issue-names.md`)

  **Never use** bare file names (e.g., `qualified-issue-names.md`) without their directory path.
  ```

## Post-conditions

- [ ] `plugin/rules/qualified-issue-names.md` covers issues, skills, and files
- [ ] The rule applies to `mainAgent` and subagents (update frontmatter if needed)
- [ ] E2E: The next agent session injects the updated rule and references skills with `cat:` prefix
