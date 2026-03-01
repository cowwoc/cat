/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.util.EffortLevel;

import org.testng.annotations.Test;

/**
 * Tests for {@link EffortLevel}.
 */
public final class EffortLevelTest
{
  /**
   * Verifies that fromString("low") returns LOW.
   */
  @Test
  public void fromStringLowReturnsLOW()
  {
    requireThat(EffortLevel.fromString("low"), "level").isEqualTo(EffortLevel.LOW);
  }

  /**
   * Verifies that fromString("medium") returns MEDIUM.
   */
  @Test
  public void fromStringMediumReturnsMEDIUM()
  {
    requireThat(EffortLevel.fromString("medium"), "level").isEqualTo(EffortLevel.MEDIUM);
  }

  /**
   * Verifies that fromString("high") returns HIGH.
   */
  @Test
  public void fromStringHighReturnsHIGH()
  {
    requireThat(EffortLevel.fromString("high"), "level").isEqualTo(EffortLevel.HIGH);
  }

  /**
   * Verifies that fromString is case-insensitive (e.g., "HIGH" returns HIGH).
   */
  @Test
  public void fromStringIsCaseInsensitive()
  {
    requireThat(EffortLevel.fromString("HIGH"), "level").isEqualTo(EffortLevel.HIGH);
  }

  /**
   * Verifies that toString returns lowercase "low" for the LOW enum value.
   */
  @Test
  public void toStringLowReturnsLowercase()
  {
    requireThat(EffortLevel.LOW.toString(), "str").isEqualTo("low");
  }

  /**
   * Verifies that toString returns lowercase "medium" for the MEDIUM enum value.
   */
  @Test
  public void toStringMediumReturnsLowercase()
  {
    requireThat(EffortLevel.MEDIUM.toString(), "str").isEqualTo("medium");
  }

  /**
   * Verifies that toString returns lowercase "high" for the HIGH enum value.
   */
  @Test
  public void toStringHighReturnsLowercase()
  {
    requireThat(EffortLevel.HIGH.toString(), "str").isEqualTo("high");
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for an unrecognized value.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*UNKNOWN.*")
  public void fromStringInvalidThrowsIllegalArgument()
  {
    EffortLevel.fromString("unknown");
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for blank input, with a message referencing
   * the parameter name.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*value.*")
  public void fromStringBlankThrowsIllegalArgument()
  {
    EffortLevel.fromString("");
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for whitespace-padded input.
   * <p>
   * The implementation does not strip whitespace before matching, so " low " is not a valid effort level.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".* LOW .*")
  public void fromStringWhitespacePaddedThrowsIllegalArgument()
  {
    EffortLevel.fromString(" low ");
  }

  /**
   * Verifies that fromString throws NullPointerException for null input.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*value.*")
  public void fromStringNullThrowsNullPointerException()
  {
    EffortLevel.fromString(null);
  }
}
