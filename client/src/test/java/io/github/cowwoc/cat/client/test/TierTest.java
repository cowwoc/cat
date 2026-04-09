/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.claude.hook.licensing.Tier;

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
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*indie.*")
  public void fromStringIndieThrowsIllegalArgument()
  {
    Tier.fromString("indie");
  }

  /**
   * Verifies that fromString("team") throws IllegalArgumentException (old name rejected).
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*team.*")
  public void fromStringTeamThrowsIllegalArgument()
  {
    Tier.fromString("team");
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for blank input.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".+")
  public void fromStringBlankThrowsIllegalArgument()
  {
    Tier.fromString("");
  }

  /**
   * Verifies that fromString throws NullPointerException for null input.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*name.*")
  public void fromStringNullThrowsNullPointerException()
  {
    Tier.fromString(null);
  }
}
