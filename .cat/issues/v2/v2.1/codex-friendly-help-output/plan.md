# Codex-Friendly Help Output

## Objective
Redesign `$cat:help` output so it renders cleanly in the Codex terminal UI.

## Background
Codex TUI renders headings, emphasis, inline code, lists, links, and code blocks, but it does not render
GitHub-flavored Markdown tables. Pipe tables remain raw text, and headings keep their literal `#` markers with styling.
The help output should therefore use terminal-friendly sections and compact lists instead of Markdown tables.

## Requirements
- Replace Markdown pipe tables in `$cat:help` output with Codex-friendly list or aligned text formats.
- Preserve the same user-facing information:
  - user-facing skills
  - work scopes
  - project structure
  - branch naming
- Keep the first screen scanable in a terminal.
- Avoid nested bullets where a short labeled line is clearer.
- Avoid decorative boxes or layouts that depend on exact terminal width.

## Proposed Output
````markdown
# CAT Command Reference

Use dollar-prefixed skill mentions to select a CAT workflow explicitly.

## Start Here

- `$cat:init` - Set up a new or existing project.
- `$cat:status` - See what's happening and what to do next.
- `$cat:config` - Change trust level and workflow preferences.
- `$cat:cleanup` - Remove stale locks and abandoned worktrees.

## Work Scope

Ask the agent to work at different scopes:

- `Next issue` - Work through all incomplete issues.
- `Work on v1 issues` - Work on all issues in `v1.x.x`.
- `Work on v1.0 issues` - Work on all issues in `v1.0.x`.
- `Work on v1.0.1 issues` - Work on all issues in `v1.0.1`.
- `Work on 1.0-parse` - Work on one specific issue.

Behavior:
- Auto-continues to the next issue when trust is `medium` or `high`.
- Creates a worktree and issue branch per issue.
- Runs an approval gate when trust is below `high`.

## Project Structure

CAT supports two layouts:

- 2-level: `MAJOR -> MINOR -> ISSUE`
- 3-level: `MAJOR -> MINOR -> PATCH -> ISSUE`

```text
.cat/
├── project.md
├── roadmap.md
├── config.json
└── v{major}/
    └── v{major}.{minor}/
        ├── {issue-name}/
        └── v{major}.{minor}.{patch}/
            └── {issue-name}/
```

Issue changelog content is embedded in commit messages.

## Branch Naming

- Issue, 2-level: `{major}.{minor}-{issue-name}`
  Example: `1.0-parse-tokens`
- Issue, 3-level: `{major}.{minor}.{patch}-{issue-name}`
  Example: `1.0.1-fix-edge-case`
- Subagent: `{issue-branch}-sub-{uuid}`
  Example: `1.0-parse-tokens-sub-a1b2c3`
````

## Research Findings
- Current help output is stored directly in:
  - `client/plugin/skills/codex/help/first-use.md`
  - `client/plugin/skills/claude/help/first-use.md`
- Both files currently use Markdown pipe tables in the user-facing skill/command, work scope, and branch naming sections.
- `client/cli/src/test/java/io/github/cowwoc/cat/client/test/ReleaseDocumentationTest.java` already validates the help skill contract for both runtime-specific help files.
- The implementation should keep the Claude help file slash-command specific and the Codex help file dollar-prefixed skill specific.
- Because the help content is static Markdown, a focused release documentation test can validate the absence of pipe-table rows and the preservation of key command/scope/branch information.

## Jobs

### Job 1
- Use TDD for the help-output behavior change:
  - Update `client/cli/src/test/java/io/github/cowwoc/cat/client/test/ReleaseDocumentationTest.java`.
  - Extend `helpSkillsEmitMarkdownDirectlyWithoutIntroCopy()` (not a helper) to assert that both help files do not contain Markdown pipe table separators such as `|---------|`, `|-------|`, or `|-----------------|`.
  - Add assertions that the Codex help still contains `$cat:init`, `$cat:status`, `$cat:config`, `$cat:cleanup`, `Work on v1 issues`, `Work on v1.0 issues`, `Work on v1.0.1 issues`, `{major}.{minor}-{issue-name}`, `{major}.{minor}.{patch}-{issue-name}`, and `{issue-branch}-sub-{uuid}`.
  - Add equivalent assertions that the Claude help still contains `/cat:init`, `/cat:status`, `/cat:config`, `/cat:cleanup`, the same work-scope examples, and the same branch naming patterns.
  - Run `mvn -f client/pom.xml -Dtest=ReleaseDocumentationTest#helpSkillsEmitMarkdownDirectlyWithoutIntroCopy test -e` before editing help output and confirm it fails because the existing help files still contain Markdown pipe tables.
- Update `client/plugin/skills/codex/help/first-use.md`:
  - Replace the `## User-Facing Skills` pipe table with a short list using the format ``- `$cat:init` - Set up a new or existing project.``.
  - Keep the first-screen structure scanable: `# CAT Command Reference`, `## Start Here`, the one-sentence Codex skill mention explanation, then the user-facing skills list.
  - Replace the Work Scope pipe table with labeled list entries for `Next issue`, `Work on v1 issues`, `Work on v1.0 issues`, `Work on v1.0.1 issues`, and `Work on 1.0-parse`.
  - Keep the behavior bullets but use explicit phrases: auto-continues when trust is `medium` or `high`, creates a worktree and issue branch per issue, and runs an approval gate when trust is below `high`.
  - Keep the project structure code block and branch naming information, but replace the branch naming pipe table with list entries plus example lines.
  - Do not add decorative boxes or width-dependent alignment.
- Update `client/plugin/skills/claude/help/first-use.md` with the same table-free structure while preserving slash-command wording:
  - Use `/cat:init`, `/cat:status`, `/cat:config`, and `/cat:cleanup`.
  - Preserve the Claude-specific sentence "Use slash commands to select a CAT workflow explicitly."
  - Keep the same work scope, project structure, and branch naming information as the Codex help file.
- Run `mvn -f client/pom.xml -Dtest=ReleaseDocumentationTest#helpSkillsEmitMarkdownDirectlyWithoutIntroCopy test -e` after the help edits and confirm it passes.
- Run `mvn -f client/pom.xml verify -e` from the worktree.
- Update `/home/node/.cat/worktrees/2.1-codex-friendly-help-output/.cat/issues/v2/v2.1/codex-friendly-help-output/index.json` in the same implementation commit as the help/test changes: set `status` to `closed` and `progress` to `100`.

## Acceptance Criteria
- `client/plugin/skills/codex/help/first-use.md` contains zero Markdown pipe-table rows (no lines matching regex `^\|.*\|$`) and still contains: `$cat:init`, `$cat:status`, `$cat:config`, `$cat:cleanup`, `Work on v1 issues`, `Work on v1.0 issues`, `Work on v1.0.1 issues`, `{major}.{minor}-{issue-name}`, `{major}.{minor}.{patch}-{issue-name}`, `{issue-branch}-sub-{uuid}`.
- `client/plugin/skills/claude/help/first-use.md` contains zero Markdown pipe-table rows (no lines matching regex `^\|.*\|$`) and still contains: `/cat:init`, `/cat:status`, `/cat:config`, `/cat:cleanup`, `Work on v1 issues`, `Work on v1.0 issues`, `Work on v1.0.1 issues`, `{major}.{minor}-{issue-name}`, `{major}.{minor}.{patch}-{issue-name}`, `{issue-branch}-sub-{uuid}`.
- `client/plugin/skills/codex/help/first-use.md` contains the heading `# CAT Command Reference` and `## Start Here`; `client/plugin/skills/claude/help/first-use.md` contains `# CAT Command Reference` and the sentence `Use slash commands to select a CAT workflow explicitly.`.
- `client/cli/src/test/java/io/github/cowwoc/cat/client/test/ReleaseDocumentationTest.java` includes assertions that enforce all of the required tokens above and enforce absence of Markdown pipe-table separators in both help files.
- `mvn -f client/pom.xml -Dtest=ReleaseDocumentationTest#helpSkillsEmitMarkdownDirectlyWithoutIntroCopy test -e` passes after edits.
- `mvn -f client/pom.xml verify -e` passes.

## Post-conditions
- User-facing help is optimized for Codex terminal rendering.
- No behavior changes outside `$cat:help` output.
