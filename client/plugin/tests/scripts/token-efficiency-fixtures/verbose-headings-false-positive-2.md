<!--
Copyright (c) 2026 Gili Tzabari. All rights reserved.
Licensed under the CAT Commercial License.
See LICENSE.md in the project root for license terms.
-->
# Release Automation Skill

## Purpose

Automate the software release process across multiple environments.

## Procedure

### Step 1: Environment Staging

Promote the build artifact to the staging environment and run smoke tests.

### Step 2: Security Hardening Review

Verify that all security policies are enforced before promotion to production.
This heading introduces a distinct topic (security) not already present in the release context —
it is NOT a redundant repetition of the parent heading.

### Step 3: Production Deployment

Deploy the artifact to production after staging approval.
