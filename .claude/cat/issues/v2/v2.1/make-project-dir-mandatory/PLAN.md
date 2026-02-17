# Plan: Make project-dir Mandatory in SkillLoader

## Goal
Make the `project-dir` CLI argument mandatory in SkillLoader to follow the fail-fast principle. Currently it defaults to
empty string when omitted, which silently produces empty `${CLAUDE_PROJECT_DIR}` substitutions.

## Satisfies
None (infrastructure improvement)

## Risk Assessment
- **Risk Level:** LOW
- **Concerns:** Callers that omit project-dir will break
- **Mitigation:** Update all callers (load-skill.sh) to pass project-dir

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/util/SkillLoader.java` - Make args.length == 4 required, remove
  empty string fallback
- `plugin/hooks/bin/load-skill.sh` (or equivalent caller) - Ensure project-dir is always passed
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/SkillLoaderTest.java` - Update test constructors

## Acceptance Criteria
- [ ] SkillLoader.main() requires exactly 4 arguments
- [ ] Constructor requires non-empty projectDir
- [ ] All callers pass project-dir
- [ ] Tests pass

## Execution Steps
1. **Update SkillLoader.main():** Change arg count check from `< 3 || > 4` to `!= 4`, remove empty string fallback
2. **Update SkillLoader constructor:** Add `requireThat(projectDir).isNotEmpty()` validation
3. **Verify callers:** Check load-skill.sh passes `$CLAUDE_PROJECT_DIR` to SkillLoader
4. **Update tests:** Ensure all test constructors pass a non-empty projectDir
5. **Build and test:** `mvn -f client/pom.xml test`
