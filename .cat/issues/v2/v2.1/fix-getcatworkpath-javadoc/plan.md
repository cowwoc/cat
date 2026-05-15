# Plan

## Goal

Fix incorrect javadoc for getCatWorkPath() - comment claims path is always at {projectPath}/.cat/work/ but implementation shows it's configurable via workPath config field

## Pre-conditions

(none)

## Post-conditions

- [ ] Javadoc accurately describes configurable workPath behavior
- [ ] Variable expansion support (${CLAUDE_PROJECT_DIR} and ~) documented
- [ ] Default value documented
- [ ] No code changes required

## Research Findings

Examined `AbstractAgentScope.java`. The `catWorkPath` field is lazily initialized:
1. Reads `workPath` from config (default: `${CAT_PROJECT_DIR}/.cat/work`)
2. Expands `${CAT_PROJECT_DIR}` and `${CLAUDE_PROJECT_DIR}` to the project path
3. Expands leading `~` to `System.getProperty("user.home")`

The interface javadoc at `AgentScope.java` incorrectly stated the path is always
`{projectPath}/.cat/work/` with no mention of the `workPath` config field, variable expansion,
or the default value.

## Jobs

### Job 1

- Edit `client/cli/src/main/java/io/github/cowwoc/cat/agent/AgentScope.java`
  - Replace the `getCatWorkPath()` javadoc with accurate description:
    - State path is configurable via the `workPath` config field
    - State default value is `${CAT_PROJECT_DIR}/.cat/work`
    - Document that `${CAT_PROJECT_DIR}` and `${CLAUDE_PROJECT_DIR}` are expanded to the project root path
    - Document that leading `~` is expanded to the user home directory
  - Do NOT change the method signature or implementation
- Commit: `bugfix: fix getCatWorkPath() javadoc to reflect configurable workPath behavior`
- Update `index.json` status to `closed`
- Commit index.json update with the implementation commit (same commit)
- Run `mvn -f client/pom.xml verify -e`
