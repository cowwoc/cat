---
mainAgent: true
---
## Commit Before Review
**CRITICAL**: ALWAYS commit changes BEFORE asking users to review implementation.

Users cannot see unstaged changes in their environment. Showing code in chat without committing
means users cannot verify the actual file state, run tests, or validate the implementation.

**Pattern**: Implement -> Commit -> Then ask for review (include commit ID)

When presenting work for user review, always include the commit ID (short SHA) so the user can
inspect the exact changes with `git show` or `git diff`.
