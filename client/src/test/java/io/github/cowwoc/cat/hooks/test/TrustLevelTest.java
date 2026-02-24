/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.util.TrustLevel;

import org.testng.annotations.Test;

/**
 * Tests for {@link TrustLevel}.
 */
public final class TrustLevelTest
{
  /**
   * Verifies that fromString("low") returns LOW.
   */
  @Test
  public void fromStringLowReturnsLOW()
  {
    requireThat(TrustLevel.fromString("low"), "level").isEqualTo(TrustLevel.LOW);
  }

  /**
   * Verifies that fromString("medium") returns MEDIUM.
   */
  @Test
  public void fromStringMediumReturnsMEDIUM()
  {
    requireThat(TrustLevel.fromString("medium"), "level").isEqualTo(TrustLevel.MEDIUM);
  }

  /**
   * Verifies that fromString("high") returns HIGH.
   */
  @Test
  public void fromStringHighReturnsHIGH()
  {
    requireThat(TrustLevel.fromString("high"), "level").isEqualTo(TrustLevel.HIGH);
  }

  /**
   * Verifies that fromString is case-insensitive (e.g., "HIGH" returns HIGH).
   */
  @Test
  public void fromStringIsCaseInsensitive()
  {
    requireThat(TrustLevel.fromString("HIGH"), "level").isEqualTo(TrustLevel.HIGH);
  }

  /**
   * Verifies that toString returns lowercase "low" for the LOW enum value.
   */
  @Test
  public void toStringLowReturnsLowercase()
  {
    requireThat(TrustLevel.LOW.toString(), "str").isEqualTo("low");
  }

  /**
   * Verifies that toString returns lowercase "medium" for the MEDIUM enum value.
   */
  @Test
  public void toStringMediumReturnsLowercase()
  {
    requireThat(TrustLevel.MEDIUM.toString(), "str").isEqualTo("medium");
  }

  /**
   * Verifies that toString returns lowercase "high" for the HIGH enum value.
   */
  @Test
  public void toStringHighReturnsLowercase()
  {
    requireThat(TrustLevel.HIGH.toString(), "str").isEqualTo("high");
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for an unrecognized value.
   */
  @Test
  public void fromStringInvalidThrowsIllegalArgument()
  {
    try
    {
      TrustLevel.fromString("unknown");
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
      TrustLevel.fromString("");
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("value");
    }
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for whitespace-padded input.
   * <p>
   * The implementation does not strip whitespace before matching, so " low " is not a valid trust level.
   */
  @Test
  public void fromStringWhitespacePaddedThrowsIllegalArgument()
  {
    try
    {
      TrustLevel.fromString(" low ");
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains(" LOW ");
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
      TrustLevel.fromString(null);
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("value");
    }
  }
}
