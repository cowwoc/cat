/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.licensing.Tier;

import org.testng.annotations.Test;

/**
 * Tests for {@link Tier}.
 */
public final class TierTest
{
  /**
   * Verifies that fromString("core") returns CORE.
   */
  @Test
  public void fromStringCoreReturnsCORE()
  {
    requireThat(Tier.fromString("core"), "tier").isEqualTo(Tier.CORE);
  }

  /**
   * Verifies that fromString("pro") returns PRO.
   */
  @Test
  public void fromStringProReturnsPRO()
  {
    requireThat(Tier.fromString("pro"), "tier").isEqualTo(Tier.PRO);
  }

  /**
   * Verifies that fromString("enterprise") returns ENTERPRISE.
   */
  @Test
  public void fromStringEnterpriseReturnsENTERPRISE()
  {
    requireThat(Tier.fromString("enterprise"), "tier").isEqualTo(Tier.ENTERPRISE);
  }

  /**
   * Verifies that fromString is case-insensitive (e.g., "CORE" returns CORE).
   */
  @Test
  public void fromStringIsCaseInsensitive()
  {
    requireThat(Tier.fromString("CORE"), "tier").isEqualTo(Tier.CORE);
  }

  /**
   * Verifies that fromString("indie") throws IllegalArgumentException (old name rejected).
   */
  @Test
  public void fromStringIndieThrowsIllegalArgument()
  {
    try
    {
      Tier.fromString("indie");
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("indie");
    }
  }

  /**
   * Verifies that fromString("team") throws IllegalArgumentException (old name rejected).
   */
  @Test
  public void fromStringTeamThrowsIllegalArgument()
  {
    try
    {
      Tier.fromString("team");
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("team");
    }
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for blank input.
   */
  @Test
  public void fromStringBlankThrowsIllegalArgument()
  {
    try
    {
      Tier.fromString("");
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").isNotEmpty();
    }
  }

  /**
   * Verifies that fromString throws NullPointerException for null input.
   */
  @Test
  public void fromStringNullThrowsNullPointerException()
  {
    try
    {
      Tier.fromString(null);
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("name");
    }
  }
}
