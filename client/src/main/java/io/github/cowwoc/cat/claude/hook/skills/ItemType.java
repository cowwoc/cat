/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.claude.hook.skills;

/**
 * Types of items that can be created via /cat:add-agent.
 */
public enum ItemType
{
  /**
   * An issue within a version.
   */
  ISSUE,

  /**
   * A version containing issues.
   */
  VERSION
}
