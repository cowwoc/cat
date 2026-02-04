# Learn Skill Anti-Patterns

Reference document for common mistakes when using the learn skill.
Lazy-loaded when reviewing RCA quality or debugging recurring mistakes.

## Always analyze token metrics in 5-whys

```yaml
# ❌ Standard analysis only
five_whys:
  - "Why error?" -> "Bad implementation"
  - "Why bad?" -> "Misunderstood requirements"
  # Stops here, misses context cause

# ✅ CAT-specific analysis
five_whys:
  - "Why error?" -> "Bad implementation"
  - "Why bad?" -> "Misunderstood requirements"
  - "Why misunderstood?" -> "Earlier context not referenced"
  - "Why not referenced?" -> "95K tokens, context pressure"
  - "Why 95K tokens?" -> "Issue not decomposed"
```

## Distinguish context-related from non-context mistakes

```yaml
# ❌ Blaming context for everything
mistake: "Typo in variable name"
analysis: "Must be context degradation"

# ✅ Honest analysis
mistake: "Typo in variable name"
analysis: |
  Tokens at error: 15000 (15% of context)
  Compactions: 0
  Context-related: NO
  Actual cause: Simple typo, needs spellcheck
```

## Base threshold adjustments on data

```yaml
# ❌ Arbitrary threshold change
new_threshold: 20000  # "Let's be extra safe"

# ✅ Data-driven adjustment
analysis: |
  Errors consistently occur after 70K tokens.
  Quality degradation measurable at 60K.
  Setting threshold at 50K provides safety margin.
new_threshold: 50000
```

## Always verify prevention works

```yaml
# ❌ Implement and forget
prevention: "Lower threshold to 30%"
# Never verified!

# ✅ Verify prevention works
prevention: "Lower threshold to 30%"
verification:
  - Run similar issue
  - Confirm decomposition triggers at 30%
  - Confirm mistake type doesn't recur
```

## Use robust positive verification

```yaml
# ❌ Check for specific failure pattern (fragile)
prevention: |
  grep "TODO" file.java  # Only catches THIS exact text

# ✅ Check for correct format (robust)
prevention: |
  ./mvnw checkstyle:check  # Verifies code meets all style requirements

# Key insight: Verify what you WANT, not what you DON'T want
```

## Invoke the skill first when user says "Learn from mistakes" (M072)

```yaml
# ❌ WRONG: Fix immediate problem, skip skill invocation
user: "Learn from mistakes: you didn't commit before approval"
agent: [makes the commit]
# Mistake not recorded, will recur!

# ✅ CORRECT: Invoke skill, analyze, record, THEN fix
user: "Learn from mistakes: you didn't commit before approval"
agent: [invokes /cat:learn skill]
agent: [performs 5-whys analysis]
agent: [records in mistakes.json]
agent: [implements prevention]
agent: [then fixes immediate problem]
```

## Escalate to enforcement when documentation failed (M084)

```yaml
# ❌ WRONG: Documentation already existed and was ignored
situation: "Workflow said MANDATORY but agent ignored it"
recorded_prevention:
  type: documentation
  path: "work.md"  # Same file that was already ignored!

# ✅ CORRECT: Escalate to enforcement
situation: "Workflow said MANDATORY but agent ignored it"
recorded_prevention:
  type: hook
  path: ".claude/hooks/enforce-lock-protocol.sh"
  action: "Created hook that blocks lock bypass attempts"
```

**Key insight:** If you're pointing to a file that already contained the instruction you violated,
you have NOT implemented prevention. Escalate to automation.
