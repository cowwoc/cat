---
name: instruction-builder-implement-agent
description: >
  Implementation specialist for instruction-builder. Receives instruction content and a target
  file path, writes the content to disk, stages, and commits. Keeps file-write I/O out of the main
  agent's context window.
model: sonnet
effort: medium
---
<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->

<!-- cat:include ../common/instruction-builder-implement-agent.md -->
