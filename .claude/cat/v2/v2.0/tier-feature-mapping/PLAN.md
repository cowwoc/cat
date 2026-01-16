# Task Plan: tier-feature-mapping

## Objective
Create tier→feature entitlement mapping defining what each tier can access.

## Tasks
- [ ] Define feature list for CAT
- [ ] Map features to tiers (Indie/Team/Enterprise)
- [ ] Create configuration file format (JSON/YAML)
- [ ] Implement entitlement resolver function
- [ ] Document feature availability per tier

## Technical Approach
Per architect research: Simple lookup table, externalized as configuration. Entitlement Resolver maps tier → feature set.

Example structure:
```json
{
  "indie": ["single-agent", "basic-tasks", "local-execution"],
  "team": ["multi-agent", "parallel-execution", "shared-context", "basic-metrics"],
  "enterprise": ["advanced-orchestration", "audit-logs", "custom-hooks", "priority-support"]
}
```

## Verification
- [ ] All CAT features categorized into tiers
- [ ] Resolver returns correct features for each tier
- [ ] Configuration is externalized (not hardcoded)
