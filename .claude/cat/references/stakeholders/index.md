# Stakeholder Review System

Multi-perspective code review through specialized stakeholder agents.

## Available Stakeholders

| Stakeholder | Focus Area | Key Concerns |
|-------------|------------|--------------|
| architect | System design | Module boundaries, dependencies, API design |
| security | Vulnerabilities | Injection, auth, input validation, resource limits |
| quality | Code quality | Duplication, complexity, maintainability, bugs |
| tester | Test coverage | Missing tests, edge cases, test quality |
| performance | Efficiency | Algorithm complexity, memory, resource usage |

## Review Process

1. **Spawn**: Each stakeholder runs as a subagent in parallel
2. **Analyze**: Stakeholders review implementation against their criteria
3. **Report**: Each returns structured JSON with concerns and severity
4. **Aggregate**: Main agent collects and evaluates all concerns
5. **Decide**: If non-trivial concerns exist, loop back to implementation

## Severity Levels

- **CRITICAL**: Must fix before merge - architectural violations, security vulnerabilities, obvious bugs
- **HIGH**: Should fix - significant quality/performance/test gaps
- **MEDIUM**: Track for later - minor improvements, nice-to-haves

## Approval States

- **APPROVED**: No critical/high concerns, acceptable for merge
- **CONCERNS**: Has issues worth noting but not blocking
- **REJECTED**: Has critical issues that require fixes

## Aggregation Rules

The review gate REJECTS if ANY stakeholder returns:
- Any CRITICAL concern
- 3+ HIGH concerns across all stakeholders
- A stakeholder returns REJECTED status

Otherwise, concerns are documented but implementation proceeds to user approval.

## Language Supplements

Stakeholder definitions are language-agnostic. Language-specific red flags and patterns are in:

```
lang/
├── java.md       # Java-specific patterns (StringBuilder, final, etc.)
├── python.md     # Python-specific (GIL, memory, etc.) [planned]
└── typescript.md # TypeScript-specific (any, async, etc.) [planned]
```

**Loading**: When spawning stakeholder reviewers, detect the primary language from file extensions
and include the relevant `lang/{language}.md` content in the reviewer prompt.

**Detection heuristic**:
```bash
# Count files by extension in changed files
PRIMARY_LANG=$(echo "$CHANGED_FILES" | grep -oE '\.[a-z]+$' | sort | uniq -c | sort -rn | head -1 | awk '{print $2}')
```
