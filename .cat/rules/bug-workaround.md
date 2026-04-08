---
paths: ["plugin/**", "client/**"]
---
## Bug Workaround Convention

When writing code that works around an external bug, add a comment using this syntax:

```
// WORKAROUND: <link to bug report>
```

This makes workarounds easy to find and remove once the upstream bug is fixed.
