---
paths: ["client/**"]
---
## Bug Workaround Convention

This rule is intentionally scoped to `client/**` because it governs source-code comments. Do not apply the
`// WORKAROUND:` syntax to Markdown-only convention files or generated artifacts; use ordinary prose in those files
unless a more specific Markdown or generated-artifact rule says otherwise.

When writing code that works around an external bug, add a comment using this syntax:

```
// WORKAROUND: <link to bug report>
```

This makes workarounds easy to find and remove once the upstream bug is fixed.
