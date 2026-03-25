# Scope Passing Convention

## Pass Scope Objects, Not Their Parts

When a method needs multiple values from a scope object (`JvmScope`, `ClaudeTool`, `ClaudeHook`), pass the scope
itself — do not destructure it at the call site.

**Correct:**
```java
String listing = SkillDiscovery.getMainAgentSkillListing(scope);
```

**Wrong:**
```java
String listing = SkillDiscovery.getMainAgentSkillListing(scope.getClaudeConfigPath(),
    scope.getProjectPath(), scope.getJsonMapper());
```

**Why:**
- Cleaner call sites with less visual noise
- Adding a new dependency to the method doesn't require updating every call site
- The method decides what it needs from the scope, not the caller

**Applies to:** All methods that accept 2+ values extractable from the same scope object. If a method only needs
one value (e.g., just a `Path`), passing that single value directly is acceptable.
