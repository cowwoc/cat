# Stakeholder Review System

Multi-perspective code review through specialized stakeholder agents.

## Available Stakeholders

| Stakeholder | Focus Area | Key Concerns |
|-------------|------------|--------------|
| requirements | Functional correctness | Specification compliance, missing functionality, incorrect behavior |
| architect | System design | Module boundaries, dependencies, API design, stack selection |
| security | Vulnerabilities | Injection, auth, input validation, resource limits |
| quality | Code quality | Duplication, complexity, maintainability, bugs |
| tester | Test coverage | Missing tests, edge cases, test quality |
| performance | Efficiency | Algorithm complexity, memory, resource usage |
| ux | User experience | Usability, accessibility, interaction design, user feedback |
| sales | Sales readiness | Customer value, competitive positioning, demo-readiness, objections |
| marketing | Market readiness | Positioning, messaging, target audience, go-to-market strategy |

## Stakeholder Modes

Each stakeholder operates in two modes:

| Mode | When Used | Purpose |
|------|-----------|---------|
| **research** | Pre-implementation (version planning) | Investigate domain for planning insights |
| **review** | Post-implementation (before merge) | Analyze code for concerns |

## When Stakeholders Are Used

### Research Mode Triggers

Stakeholders run in research mode **automatically** during:

| Command | Trigger | What Happens |
|---------|---------|--------------|
| `/cat:add-major-version` | After discuss step | All 9 stakeholders research the domain in parallel |
| `/cat:add-minor-version` | After discuss step | All 9 stakeholders research the domain in parallel |
| `/cat:research` | Manual invocation | All 9 stakeholders research specific topic |

**Research output** is merged into the `## Research` section of PLAN.md.

### Review Mode Triggers

Stakeholders run in review mode during:

| Command | Trigger | What Happens |
|---------|---------|--------------|
| `/cat:work` | After implementation complete | All 9 stakeholders review code in parallel |
| `/cat:stakeholder-review` | Manual invocation | All 9 stakeholders review specified changes |

**Review output** determines if implementation can proceed to merge.

### Stakeholder Responsibilities

| Stakeholder | Research Mode Investigates | Review Mode Checks |
|-------------|---------------------------|-------------------|
| **requirements** | Must-have requirements, acceptance criteria, verification approaches | Specification compliance, missing/incorrect functionality |
| **architect** | Stack selection, architecture patterns, integration approaches | Module boundaries, API design, dependency direction |
| **security** | Domain-specific risks, secure patterns, OWASP concerns | Injection, auth bypasses, data exposure |
| **quality** | Best practices, anti-patterns, maintainability patterns | Duplication, complexity, dead code, obvious bugs |
| **tester** | Testing strategies, edge cases to anticipate, test data needs | Missing tests, broken tests, coverage gaps |
| **performance** | Performance characteristics, efficient patterns, scaling concerns | Algorithm complexity, memory leaks, blocking operations |
| **ux** | UX patterns, usability considerations, accessibility requirements | Broken flows, missing feedback, accessibility barriers |
| **sales** | Customer value, competitive positioning, objection handling | Value delivery, demo-readiness, competitive disadvantage |
| **marketing** | Positioning, messaging, target audience, go-to-market | Marketability, differentiation, naming, launch readiness |

## Review Process

1. **Spawn**: Each stakeholder runs as a subagent in parallel
2. **Analyze**: Stakeholders review implementation against their criteria
3. **Report**: Each returns structured JSON with concerns and severity
4. **Aggregate**: Main agent collects and evaluates all concerns
5. **Decide**: Based on trust level:
   - `low`: Ask user to fix, override, or abort
   - `medium`: Auto-loop to fix (up to 3 iterations)
   - `high`: Skips review entirely (autonomous mode)

## Research Process

1. **Spawn**: Each stakeholder runs as a subagent in parallel with `mode: research`
2. **Investigate**: Stakeholders research domain from their perspective
3. **Report**: Each returns structured JSON with findings
4. **Aggregate**: Main agent merges findings into PLAN.md `## Research` section

## Severity Levels

- **CRITICAL**: Must fix before merge - missing core functionality, security vulnerabilities, incorrect behavior
- **HIGH**: Should fix - partial implementations, significant quality/performance/test gaps
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
