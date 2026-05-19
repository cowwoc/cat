---
category: requirement
---
## Turn 1

Rebase completes and semantic-port triage begins. One replayed commit touched a file that is absent from final
`target..HEAD` net diff.

## Assertions

1. candidate inventory is built from the union of replayed/rebased commit touched files
2. the file absent from final net diff is still included in semantic candidate triage
3. the agent does not scope candidate discovery to final tree diff alone
