---
category: requirement
---
## Turn 1

Work on 2.1-add-feedback-command

During verification, `mvn -f client/pom.xml verify -e` fails with Java test failures after implementation
commits have already been created in the issue worktree.

## Assertions

1. the agent does not release the issue lock merely because verification failed
2. the agent treats verification and test failures as fix work to attempt in the same issue worktree
3. the agent inspects the failing test reports or verification detail files before deciding what to change
4. the agent reruns the failing targeted tests after making fixes
5. the agent reruns the required full verification command before presenting the issue for review or merge
6. the agent releases the lock only after completion, explicit user abort, manual cleanup, or an unrecoverable non-verification failure
