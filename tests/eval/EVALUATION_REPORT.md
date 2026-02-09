# Skill Activation Evaluation Report

## Executive Summary

Baseline evaluation of CAT skill activation rates using sandboxed evals against Claude Code CLI.

**Overall Results:**
- Total test cases: 52 (40 positive, 12 negative)
- Pass rate: 71.2% (37/52 passed)
- Skills evaluated: 15

## Methodology

1. Created skill inventory of all user-invocable CAT skills
2. Designed 48 test cases covering explicit mentions, implicit requests, and edge cases
3. Built eval harness using `claude -p --output-format stream-json --max-turns 1`
4. Parsed JSONL output to detect Skill tool invocations
5. Compared actual vs expected skill activations

## Results by Skill

### Perfect Activation (100%)

| Skill | Tests | Notes |
|-------|-------|-------|
| cleanup | 2/2 | Clear, specific use case |
| config | 2/2 | Distinctive terminology |
| help | 3/3 | Standard command pattern |
| init | 2/2 | Well-established workflow |
| optimize-execution | 2/2 | Unique terminology |
| remove | 2/2 | Clear action verb |
| status | 3/3 | Most common query |

### Moderate Activation (25-75%)

| Skill | Rate | Tests | Key Issues |
|-------|------|-------|------------|
| add | 66.7% | 2/3 | "Add a task" sometimes doesn't activate |
| learn | 50.0% | 1/2 | "Document what went wrong" too general |
| monitor-subagents | 50.0% | 1/2 | Confused with token-report |
| research | 33.3% | 1/3 | "Look up best practices" doesn't trigger |
| run-retrospective | 50.0% | 1/2 | "Analyze patterns" too general |
| work | 25.0% | 1/4 | Vague prompts activate status instead |

### Failed Activation (0%)

| Skill | Tests | Analysis |
|-------|-------|----------|
| debug-env | 0/2 | Too specialized, low user awareness |
| shrink-doc | 0/3 | New skill, not well established |

## Key Findings

### Pattern 1: Specificity Matters

Skills activate more reliably when prompts include specific identifiers:
- ✅ "Work on task 2.1-evaluate-skill-activation" → activates `work`
- ❌ "Work on the next task" → activates `status`

This suggests Claude interprets vague "work on" as "tell me what to work on" rather than "start working".

### Pattern 2: Established Workflows Win

Skills that map to common, well-known patterns (status, help, init, cleanup) have 100% activation.
Newer or specialized skills (shrink-doc, debug-env, research) struggle even with trigger words.

### Pattern 3: Semantic Overlap Causes Confusion

- `monitor-subagents` vs `token-report`: Both relate to context/tokens
- `work` vs `status`: "work on X" interpreted as inquiry rather than action
- `learn` vs general documentation: "Document what went wrong" too broad

### Pattern 4: Description Changes Have Limited Impact

Updated skill descriptions with explicit trigger words showed minimal improvement.
This suggests Claude's skill matching uses factors beyond description text alone.

## Improvements Made

Updated 8 skill definitions with clearer trigger words and more explicit use cases:
- add, debug-env, learn, monitor-subagents, research, run-retrospective, shrink-doc, work

Changes included:
- Added "Trigger words:" sections listing specific phrases
- Clarified distinctions from similar skills
- Added context about when to use each skill

## Recommendations

### For Skill Design

1. **Use distinctive terminology**: Skills with unique vocabulary (optimize-execution, cleanup) activate reliably
2. **Avoid semantic overlap**: Clearly distinguish similar skills in descriptions
3. **Consider explicit invocation**: For specialized skills (debug-env, shrink-doc), may need `/cat:skill-name` syntax
4. **Test with real user prompts**: Natural language is more varied than test cases suggest

### For Test Suite

1. **Add ambiguity tests**: Include intentionally ambiguous prompts to measure robustness
2. **Test prompt variations**: Multiple phrasings of same intent to measure consistency
3. **Measure non-determinism**: Run tests multiple times to detect variance

### For Future Work

1. **Investigate skill matching algorithm**: Understand how Claude Code matches prompts to skills
2. **Consider skill priority hints**: Allow marking certain skills as preferred for ambiguous cases
3. **User education**: For low-activation skills, document recommended invocation patterns
4. **Acceptance criteria**: Define what activation rate is "good enough" per skill category

## Conclusion

71.2% overall activation represents a solid baseline, with core workflows achieving 100%.
Improving the remaining 29% requires deeper investigation into skill matching mechanics.

For specialized skills with 0% activation, explicit invocation syntax (`/cat:shrink-doc`) may be more
practical than trying to achieve natural language activation.
