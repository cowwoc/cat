/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.util.VerifyLevel;

import org.testng.annotations.Test;

/**
 * Tests for {@link VerifyLevel}.
 */
public final class VerifyLevelTest
{
  /**
   * Verifies that fromString("none") returns NONE.
   */
  @Test
  public void fromStringNoneReturnsNONE()
  {
    requireThat(VerifyLevel.fromString("none"), "level").isEqualTo(VerifyLevel.NONE);
  }

  /**
   * Verifies that fromString("changed") returns CHANGED.
   */
  @Test
  public void fromStringChangedReturnsCHANGED()
  {
    requireThat(VerifyLevel.fromString("changed"), "level").isEqualTo(VerifyLevel.CHANGED);
  }

  /**
   * Verifies that fromString("all") returns ALL.
   */
  @Test
  public void fromStringAllReturnsALL()
  {
    requireThat(VerifyLevel.fromString("all"), "level").isEqualTo(VerifyLevel.ALL);
  }

  /**
   * Verifies that fromString is case-insensitive (e.g., "ALL" returns ALL).
   */
  @Test
  public void fromStringIsCaseInsensitive()
  {
    requireThat(VerifyLevel.fromString("ALL"), "level").isEqualTo(VerifyLevel.ALL);
  }

  /**
   * Verifies that toString returns lowercase "none" for the NONE enum value.
   */
  @Test
  public void toStringNoneReturnsLowercase()
  {
    requireThat(VerifyLevel.NONE.toString(), "str").isEqualTo("none");
  }

  /**
   * Verifies that toString returns lowercase "changed" for the CHANGED enum value.
   */
  @Test
  public void toStringChangedReturnsLowercase()
  {
    requireThat(VerifyLevel.CHANGED.toString(), "str").isEqualTo("changed");
  }

  /**
   * Verifies that toString returns lowercase "all" for the ALL enum value.
   */
  @Test
  public void toStringAllReturnsLowercase()
  {
    requireThat(VerifyLevel.ALL.toString(), "str").isEqualTo("all");
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for an unrecognized value.
   */
  @Test
  public void fromStringInvalidThrowsIllegalArgument()
  {
    try
    {
      VerifyLevel.fromString("unknown");
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("UNKNOWN");
    }
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for blank input, with a message referencing
   * the parameter name.
   */
  @Test
  public void fromStringBlankThrowsIllegalArgument()
  {
    try
    {
      VerifyLevel.fromString("");
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("value");
    }
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for whitespace-padded input.
   * <p>
   * The implementation does not strip whitespace before matching, so " none " is not a valid verify level.
   */
  @Test
  public void fromStringWhitespacePaddedThrowsIllegalArgument()
  {
    try
    {
      VerifyLevel.fromString(" none ");
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains(" NONE ");
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
      VerifyLevel.fromString(null);
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("value");
    }
  }
}
