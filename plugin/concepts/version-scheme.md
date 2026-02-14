<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Versioning Schemes

## Overview

CAT supports flexible versioning schemes. Projects can choose the granularity that fits their workflow.

## Supported Schemes

| Scheme | Structure | Example Paths | Use Case |
|--------|-----------|---------------|----------|
| MAJOR only | `v$MAJOR/` | `v1/`, `v2/` | Simple projects, rapid iteration |
| MAJOR.MINOR | `v$MAJOR/v$MAJOR.$MINOR/` | `v1/v1.0/`, `v1/v1.1/` | Most projects (default) |
| MAJOR.MINOR.PATCH | `v$MAJOR/v$MAJOR.$MINOR/v$MAJOR.$MINOR.$PATCH/` | `v1/v1.0/v1.0.1/` | Projects needing hotfix granularity |

## Scheme Detection

The versioning scheme is auto-detected from the existing directory structure:

```bash
# Detection priority (most specific first):
# 1. If v*.*.* directories exist → MAJOR.MINOR.PATCH scheme
# 2. If v*.* directories exist → MAJOR.MINOR scheme
# 3. Otherwise → MAJOR only scheme
```

See [version-paths.md](version-paths.md) for the `detect_version_scheme()` implementation.

## Version Boundary Detection

When comparing versions (e.g., for `/cat:work` auto-continue), boundary detection adapts to the scheme:

| Scheme | Boundary Crossed When |
|--------|----------------------|
| MAJOR only | `COMPLETED_MAJOR != NEXT_MAJOR` |
| MAJOR.MINOR | `COMPLETED_MAJOR != NEXT_MAJOR OR COMPLETED_MINOR != NEXT_MINOR` |
| MAJOR.MINOR.PATCH | Any version component differs |

## Related Documentation

- [hierarchy.md](hierarchy.md) - Version hierarchy and dependency rules
- [version-paths.md](version-paths.md) - Path resolution functions
