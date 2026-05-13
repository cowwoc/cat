---
description: Research implementation approaches, technical details, or best practices.
argument-hint: "[query]"
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
Invoke the `cat:research` skill and pass `$ARGUMENTS` through unchanged. If the skill cannot be loaded or returns an
error, report that failure and stop instead of recreating its behavior manually.
