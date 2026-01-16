# Task Plan: feature-gate-middleware

## Objective
Build feature gate system using Java validation with Bash hook integration.

## Tasks
- [ ] Design gate check interface in Java
- [ ] Implement FeatureGate class with tier requirements
- [ ] Add tier context injection from cached license
- [ ] Create Bash hook for PreToolUse validation
- [ ] Implement graceful degradation (suggest upgrade vs hard block)
- [ ] Place gates at agent spawn, task dispatch, premium features

## Technical Approach

Per project conventions, validation logic must be in Java. Hooks remain in Bash.

**Java Components:**
- `FeatureGate.java` - Gate definitions and checks
- `FeatureRegistry.java` - Map of features to required tiers
- `GateCheckResult.java` - Result with upgrade suggestions

**Feature Registry Example:**
```java
public class FeatureRegistry {
    private static final Map<String, Tier> GATES = Map.of(
        "parallel-agents", Tier.TEAM,
        "stakeholder-review", Tier.PROFESSIONAL,
        "custom-hooks", Tier.TEAM
    );
}
```

**Bash Hook Integration:**
```bash
# In PreToolUse hook
RESULT=$(java -jar cat-license-validator.jar check-feature "parallel-agents")
if [[ $? -ne 0 ]]; then
    echo "Upgrade to Team tier for parallel agents"
    exit 2  # Block operation
fi
```

**Validation Flow:**
1. Bash hook intercepts command (PreToolUse)
2. Invokes Java: `java -jar cat-license-validator.jar check-feature <name>`
3. Java checks cached tier against feature requirements
4. Returns: `{"allowed": false, "requiredTier": "team", "message": "..."}`
5. Hook blocks or allows based on result

## Verification
- [ ] Free tier blocked from team features with upgrade prompt
- [ ] Team tier can access team features
- [ ] Validation completes in <10ms (cached tier lookup)
- [ ] Graceful error messages, not crashes
- [ ] Hook correctly interprets Java gate results
