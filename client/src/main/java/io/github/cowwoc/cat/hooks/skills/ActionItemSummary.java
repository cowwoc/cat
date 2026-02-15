/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.skills;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Holds action item summary information for sorting and display.
 *
 * @param id          the action item ID
 * @param priority    the priority level
 * @param description the action item description
 * @throws NullPointerException     if any of the arguments are null
 * @throws IllegalArgumentException if {@code id} is blank
 */
public record ActionItemSummary(String id, Priority priority, String description)
{
  /**
   * Creates a new ActionItemSummary.
   */
  public ActionItemSummary
  {
    requireThat(id, "id").isNotBlank();
    requireThat(priority, "priority").isNotNull();
    requireThat(description, "description").isNotNull();
  }
}
