# State

- **Status:** open
- **Progress:** 0%
- **Dependencies:** []
- **Last Updated:** 2026-02-06
- **Decomposed At:** 2026-01-26
- **Reason:** Task exceeded context threshold (50 files = ~220K tokens)

## Decomposed Into
- java-jdk-infrastructure (JDK bundle, bootstrap scripts)
- java-core-hooks (wire up entry points in hooks.json)
- java-skill-handlers (5 missing + verify 11 existing)
- java-bash-handlers (3 missing + verify 14 existing)
- java-other-handlers (6 missing + verify 6 existing)
- add-java-build-to-ci (JAR build step for SessionStart)
- migrate-enforce-hooks (EnforceWorktreeIsolation + EnforceStatusOutput to Java)
- migrate-token-counting (Python tiktoken to Java JTokkit)
- migrate-python-tests (18 Python test files to Java TestNG)
- cleanup-python-files (remove all Python hook/test files)

## Parallel Execution Plan

### Wave 1 (Sequential - Foundation)
| Task | Est. Tokens | Dependencies |
|------|-------------|--------------|
| java-jdk-infrastructure | ~25K | None |

### Wave 2 (Sequential - Core)
| Task | Est. Tokens | Dependencies |
|------|-------------|--------------|
| java-core-hooks | ~20K | java-jdk-infrastructure, add-java-build-to-ci |

### Wave 3 (Concurrent - Handlers + Token Counting)
| Task | Est. Tokens | Dependencies |
|------|-------------|--------------|
| java-skill-handlers | ~35K | java-core-hooks |
| java-bash-handlers | ~25K | java-core-hooks |
| java-other-handlers | ~25K | java-core-hooks |
| migrate-enforce-hooks | ~15K | java-core-hooks |
| migrate-token-counting | ~15K | java-core-hooks |
| add-java-build-to-ci | ~10K | None (can start anytime) |

### Wave 4 (Sequential - Tests)
| Task | Est. Tokens | Dependencies |
|------|-------------|--------------|
| migrate-python-tests | ~30K | All handler subtasks |

### Wave 5 (Sequential - Cleanup)
| Task | Est. Tokens | Dependencies |
|------|-------------|--------------|
| cleanup-python-files | ~10K | migrate-python-tests |

**Total sub-tasks:** 10
**Max concurrent:** 6 (in wave 3)
