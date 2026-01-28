# Java Red Flags

## Performance
| Pattern | Issue | Fix |
|---------|-------|-----|
| String `+` in loops | Creates objects each iteration | `StringBuilder` |
| `new` in tight loops | Unnecessary object creation | Cache or primitives |
| Missing `final` on cacheable | Recomputed each access | Cache in `final` field |
| `synchronized` in hot paths | Contention bottleneck | Lock-free alternatives |

## Security
| Pattern | Issue | Fix |
|---------|-------|-----|
| SQL string concat | Injection vulnerability | `PreparedStatement` |
| `ObjectInputStream.readObject()` on untrusted | Deserialization attack | Validate/whitelist |
| Hardcoded "password"/"secret"/"apikey" | Credential exposure | External config |

## Quality
| Pattern | Issue |
|---------|-------|
| Empty `catch` blocks | Silent failures |
| Raw types (`List` not `List<String>`) | Type safety loss |
| Mutable `static` fields | Thread safety risk |

## Testing
| Pattern | Issue |
|---------|-------|
| `Thread.sleep()` in tests | Flaky timing dependency |
| Missing `@Test` | Test won't execute |
| `assertEquals` with floats | Needs delta parameter |

## Architecture
| Pattern | Issue |
|---------|-------|
| Circular package deps | A imports B, B imports A |
| God classes | 20+ methods or 500+ lines |
| Static-only utility classes | Should be instance methods |
