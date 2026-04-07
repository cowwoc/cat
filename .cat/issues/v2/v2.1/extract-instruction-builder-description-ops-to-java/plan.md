# Plan: extract-instruction-builder-description-ops-to-java

## Type
refactor

## Goal
Extract the description extraction, validation, and replacement bash operations in instruction-builder's
Step 4 into a Java CLI tool, applying the llm-to-java policy to eliminate fragile grep/sed pipelines.

## Parent Requirements
None

## Post-conditions
- [ ] New Java CLI tool `update-skill-description` exists in `client/src/main/java/.../skills/`
- [ ] Tool accepts the skill/instruction file content on stdin and a new description as a positional
  argument; validates the description is ≤250 characters; replaces the `description:` frontmatter field;
  outputs the updated content to stdout; exits non-zero with an error message if validation fails
- [ ] `instruction-builder-agent/first-use.md` Step 4 bash block replaced with a single invocation of
  the new CLI tool: `INSTRUCTION_DRAFT=$(printf '%s' "${INSTRUCTION_DRAFT}" | update-skill-description "${NEW_DESCRIPTION}")`
- [ ] Unit tests cover: valid replacement, 250-char boundary accepted, 251-char rejected, missing
  frontmatter error, missing description field error
- [ ] All tests pass (`mvn -f client/pom.xml verify -e`)

## Jobs

### Job 1
- Implement `UpdateSkillDescription` Java class with `getOutput(String[] args)` that reads stdin,
  validates description length, replaces description in frontmatter, writes to stdout
- Add `update-skill-description` launcher entry to the jlink build configuration
- Add unit tests in `UpdateSkillDescriptionTest`

### Job 2 (depends on Job 1)
- Update `plugin/skills/instruction-builder-agent/first-use.md` Step 4 to replace the bash grep/sed
  pipeline with a call to `update-skill-description`
- Remove the `ESCAPED_DESCRIPTION` escaping block (now handled inside the Java tool)
