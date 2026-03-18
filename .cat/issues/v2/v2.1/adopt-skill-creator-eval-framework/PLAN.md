# Plan: adopt-skill-creator-eval-framework

## Goal
Adopt valuable ideas from Anthropic's skill-creator repository to improve skill-builder and skill validation:
eval-driven iteration loops, description trigger testing, post-hoc analysis patterns, and guidance emphasizing
explanation over mandates.

## Satisfies
None

## Risk Assessment
- **Risk Level:** MEDIUM
- **Concerns:** Adding new subagent handlers increases surface area; description trigger testing requires calibrated
  prompts that may vary across Claude model versions
- **Mitigation:** Implement in Java only; add unit tests for all new handlers; keep existing skill-builder steps intact
  and only append new validation step

## Files to Modify
- `plugin/skills/skill-builder-agent/first-use.md` - add Step 8 (eval-driven validation loop) and update guidance to
  emphasize explanation over mandates
- `plugin/agents/skill-validator-agent/SKILL.md` - new subagent: validate skill against should-trigger /
  should-not-trigger test prompts
- `plugin/agents/description-tester-agent/SKILL.md` - new subagent: generate and evaluate description trigger
  calibration queries
- `plugin/agents/skill-comparison-agent/SKILL.md` - new subagent: structured comparison of two skill versions using
  rubric scoring, winner determination, strengths/weaknesses extraction
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/SkillValidatorHandler.java` - Java handler for
  skill-validator-agent
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/DescriptionTesterHandler.java` - Java handler for
  description-tester-agent
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/SkillComparisonHandler.java` - Java handler for
  skill-comparison-agent
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillValidatorHandlerTest.java` - unit tests
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/DescriptionTesterHandlerTest.java` - unit tests
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillComparisonHandlerTest.java` - unit tests
- `plugin/concepts/skill-validation.md` - concept documentation for eval-driven validation approach
- `plugin/concepts/eval-patterns.md` - concept documentation for post-hoc comparison methodology

## Pre-conditions
None

## Sub-Agent Waves

### Wave 1
- Update `plugin/skills/skill-builder-agent/first-use.md`:
  - Add Step 8: after skill is written, generate 2-3 should-trigger test prompts and 2-3 should-not-trigger test
    prompts; optionally delegate to skill-validator-agent subagent to run prompts and grade output
  - Replace ALL-CAPS mandate language with reasoned explanations — explain *why* each guideline exists
  - Files: `plugin/skills/skill-builder-agent/first-use.md`

### Wave 2
- Create `plugin/agents/skill-validator-agent/SKILL.md` with frontmatter (description, model, allowed-tools) and
  instructions for running should-trigger / should-not-trigger test prompts against a skill and returning pass/fail
  results per prompt
  - Files: `plugin/agents/skill-validator-agent/SKILL.md`
- Create `plugin/agents/description-tester-agent/SKILL.md` with instructions for generating calibration queries from
  a skill's `description:` frontmatter and evaluating whether each query would trigger the skill
  - Files: `plugin/agents/description-tester-agent/SKILL.md`
- Create `plugin/agents/skill-comparison-agent/SKILL.md` with instructions for structured rubric-based comparison of
  two skill versions: score each against rubric criteria, determine winner, extract specific strengths/weaknesses with
  evidence
  - Files: `plugin/agents/skill-comparison-agent/SKILL.md`

### Wave 3
- Implement `SkillValidatorHandler.java` in Java: parse skill file and test prompt list, invoke subagent per prompt,
  collect and format pass/fail results
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/SkillValidatorHandler.java`
- Implement `DescriptionTesterHandler.java` in Java: parse description frontmatter, generate calibration queries,
  evaluate and report trigger calibration results
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/DescriptionTesterHandler.java`
- Implement `SkillComparisonHandler.java` in Java: load two skill versions, apply rubric scoring, output structured
  comparison report
  - Files: `client/src/main/java/io/github/cowwoc/cat/hooks/skills/SkillComparisonHandler.java`
- Write unit tests for all three handlers
  - Files: `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillValidatorHandlerTest.java`,
    `client/src/test/java/io/github/cowwoc/cat/hooks/test/DescriptionTesterHandlerTest.java`,
    `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillComparisonHandlerTest.java`

### Wave 4
- Create `plugin/concepts/skill-validation.md`: document the eval-driven validation approach, explain when and how to
  use skill-validator-agent and description-tester-agent
  - Files: `plugin/concepts/skill-validation.md`
- Create `plugin/concepts/eval-patterns.md`: document post-hoc comparison methodology — rubric design, scoring,
  winner determination, strength/weakness extraction
  - Files: `plugin/concepts/eval-patterns.md`
- Run `mvn -f client/pom.xml test` and confirm all tests pass
  - Files: (build verification)

## Post-conditions
- [ ] `plugin/skills/skill-builder-agent/first-use.md` includes Step 8 with eval-driven validation loop
- [ ] `plugin/skills/skill-builder-agent/first-use.md` guidance uses reasoned explanations rather than ALL-CAPS mandates
- [ ] `plugin/agents/skill-validator-agent/SKILL.md` exists and is well-formed (valid frontmatter, clear instructions)
- [ ] `plugin/agents/description-tester-agent/SKILL.md` exists and is well-formed
- [ ] `plugin/agents/skill-comparison-agent/SKILL.md` exists and is well-formed
- [ ] All three Java handlers exist with unit test coverage
- [ ] `plugin/concepts/skill-validation.md` and `plugin/concepts/eval-patterns.md` exist and are documented
- [ ] `mvn -f client/pom.xml test` exits 0 with no regressions
- [ ] No Python dependencies; all implementations are in Java targeting Claude Code only
