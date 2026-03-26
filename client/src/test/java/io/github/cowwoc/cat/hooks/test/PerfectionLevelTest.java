/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.util.PerfectionLevel;

import org.testng.annotations.Test;

/**
 * Tests for {@link PerfectionLevel}.
 */
public final class PerfectionLevelTest
{
  /**
   * Verifies that fromString("low") returns LOW.
   */
  @Test
  public void fromStringLowReturnsLOW()
  {
    requireThat(PerfectionLevel.fromString("low"), "level").isEqualTo(PerfectionLevel.LOW);
  }

  /**
   * Verifies that fromString("medium") returns MEDIUM.
   */
  @Test
  public void fromStringMediumReturnsMEDIUM()
  {
    requireThat(PerfectionLevel.fromString("medium"), "level").isEqualTo(PerfectionLevel.MEDIUM);
  }

  /**
   * Verifies that fromString("high") returns HIGH.
   */
  @Test
  public void fromStringHighReturnsHIGH()
  {
    requireThat(PerfectionLevel.fromString("high"), "level").isEqualTo(PerfectionLevel.HIGH);
  }

  /**
   * Verifies that fromString is case-insensitive (e.g., "HIGH" returns HIGH).
   */
  @Test
  public void fromStringIsCaseInsensitive()
  {
    requireThat(PerfectionLevel.fromString("HIGH"), "level").isEqualTo(PerfectionLevel.HIGH);
  }

  /**
   * Verifies that toString returns lowercase "low" for the LOW enum value.
   */
  @Test
  public void toStringLowReturnsLowercase()
  {
    requireThat(PerfectionLevel.LOW.toString(), "PerfectionLevel.LOW.toString()").isEqualTo("low");
  }

  /**
   * Verifies that toString returns lowercase "medium" for the MEDIUM enum value.
   */
  @Test
  public void toStringMediumReturnsLowercase()
  {
    requireThat(PerfectionLevel.MEDIUM.toString(), "PerfectionLevel.MEDIUM.toString()").isEqualTo("medium");
  }

  /**
   * Verifies that toString returns lowercase "high" for the HIGH enum value.
   */
  @Test
  public void toStringHighReturnsLowercase()
  {
    requireThat(PerfectionLevel.HIGH.toString(), "PerfectionLevel.HIGH.toString()").isEqualTo("high");
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for an unrecognized value.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*UNKNOWN.*")
  public void fromStringInvalidThrowsIllegalArgument()
  {
    PerfectionLevel.fromString("unknown");
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for blank input, with a message referencing
   * the parameter name.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*value.*")
  public void fromStringBlankThrowsIllegalArgument()
  {
    PerfectionLevel.fromString("");
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for whitespace-padded input.
   * <p>
   * The implementation does not strip whitespace before matching, so " low " is not a valid perfection level.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".* LOW .*")
  public void fromStringWhitespacePaddedThrowsIllegalArgument()
  {
    PerfectionLevel.fromString(" low ");
  }

  /**
   * Verifies that fromString throws NullPointerException for null input.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*value.*")
  public void fromStringNullThrowsNullPointerException()
  {
    PerfectionLevel.fromString(null);
  }
}
