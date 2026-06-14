---
paths: ["*.java"]
---
# Scope Passing Convention

## Pass Scope Objects, Not Their Parts

When a method needs multiple values from a scope object (`AgentScope`, `AgentPluginScope`, `CliTool`, engine hook
scopes), pass the scope itself — do not destructure it at the call site.

**Correct:**
```java
String listing = SkillDiscovery.getMainAgentSkillListing(scope);
```

**Wrong:**
```java
String listing = SkillDiscovery.getMainAgentSkillListing(scope.getEngineConfigPath(),
    scope.getProjectPath(), scope.getJsonMapper());
```

**Why:**
- Cleaner call sites with less visual noise
- Adding a new dependency to the method doesn't require updating every call site
- The method decides what it needs from the scope, not the caller
- Scope APIs should expose values directly. Do not introduce pass-through `*Config` objects from scopes; move those
  accessors onto the relevant scope interface/class and let abstract scope implementations derive shared values.

**Applies to:** All methods that accept 2+ values extractable from the same scope object. If a method only needs
one value (e.g., just a `Path`), passing that single value directly is acceptable.
