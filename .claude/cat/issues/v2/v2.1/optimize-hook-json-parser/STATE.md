# State

- **Status:** in-progress
- **Progress:** 0%
- **Decomposed:** true
- **Decomposed At:** 2026-02-10
- **Reason:** Task scope (55 files, 265 jackson-databind occurrences) exceeds single-agent context capacity
- **Dependencies:** []
- **Last Updated:** 2026-02-10

## Decomposed Into
- hook-sh-fix
- json-core-api-migration
- json-complex-handler-migration
- build-optimize-validate

## Parallel Execution Plan

### Wave 1 (Concurrent)
| Issue | Est. Tokens | Dependencies |
|-------|-------------|--------------|
| hook-sh-fix | 5K | None |
| json-core-api-migration | 45K | None |

### Wave 2 (After Wave 1)
| Issue | Est. Tokens | Dependencies |
|-------|-------------|--------------|
| json-complex-handler-migration | 35K | json-core-api-migration |

### Wave 3 (After Wave 2)
| Issue | Est. Tokens | Dependencies |
|-------|-------------|--------------|
| build-optimize-validate | 10K | json-complex-handler-migration |

**Total sub-issues:** 4
**Max concurrent subagents:** 2 (in Wave 1)

## Conflict Check
- Wave 1: hook-sh-fix touches `plugin/hooks/hook.sh`, json-core-api-migration touches `hooks/src/main/java/**`
- No file overlap - safe to parallelize
