---
category: requirement
---
## Turn 1

Rename the class `UserAuthenticator` to `AuthenticationManager` across the codebase.

## Assertions

1. agent invokes the plan-before-edit-agent skill with UserAuthenticator as the symbol argument
2. skill invocation includes the target name AuthenticationManager
