# Plan: replace-active-symbol

## Problem
The progress banner uses ◉ (filled circle) for the active phase indicator, but ◑ (half-filled circle)
better represents an in-progress state visually.

## Files to Modify
- `client/src/main/java/io/github/cowwoc/cat/hooks/skills/ProgressBanner.java` — change symbol
- `client/src/test/java/io/github/cowwoc/cat/hooks/test/ProgressBannerTest.java` — update assertions

## Post-conditions
- [ ] Active phase uses ◑ instead of ◉
- [ ] All tests pass
