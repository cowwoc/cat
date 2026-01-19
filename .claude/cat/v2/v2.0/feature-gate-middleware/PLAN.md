# Task Plan: feature-gate-middleware

## Objective
Build feature gate middleware to enforce tier-based access at system edges.

## Tasks
- [ ] Design gate decorator/middleware pattern
- [ ] Implement @RequiresTier('team') decorator for commands
- [ ] Add tier context injection from cached license
- [ ] Implement graceful degradation (suggest upgrade vs hard block)
- [ ] Place gates at agent spawn, task dispatch, premium imports

## Technical Approach
Per architect research: Middleware validates token, injects tier context. Gates at system edges (<1ms validation). Graceful degradation over hard stops.

Example:
```javascript
@RequiresTier('team')
function spawnMultipleAgents() { ... }
```

## Verification
- [ ] Free tier blocked from team features with upgrade prompt
- [ ] Team tier can access team features
- [ ] Validation completes in <1ms
- [ ] Graceful error messages, not crashes
