/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.util.PatienceLevel;

import org.testng.annotations.Test;

/**
 * Tests for {@link PatienceLevel}.
 */
public final class PatienceLevelTest
{
  /**
   * Verifies that fromString("low") returns LOW.
   */
  @Test
  public void fromStringLowReturnsLOW()
  {
    requireThat(PatienceLevel.fromString("low"), "level").isEqualTo(PatienceLevel.LOW);
  }

  /**
   * Verifies that fromString("medium") returns MEDIUM.
   */
  @Test
  public void fromStringMediumReturnsMEDIUM()
  {
    requireThat(PatienceLevel.fromString("medium"), "level").isEqualTo(PatienceLevel.MEDIUM);
  }

  /**
   * Verifies that fromString("high") returns HIGH.
   */
  @Test
  public void fromStringHighReturnsHIGH()
  {
    requireThat(PatienceLevel.fromString("high"), "level").isEqualTo(PatienceLevel.HIGH);
  }

  /**
   * Verifies that fromString is case-insensitive (e.g., "HIGH" returns HIGH).
   */
  @Test
  public void fromStringIsCaseInsensitive()
  {
    requireThat(PatienceLevel.fromString("HIGH"), "level").isEqualTo(PatienceLevel.HIGH);
  }

  /**
   * Verifies that toString returns lowercase "low" for the LOW enum value.
   */
  @Test
  public void toStringLowReturnsLowercase()
  {
    requireThat(PatienceLevel.LOW.toString(), "str").isEqualTo("low");
  }

  /**
   * Verifies that toString returns lowercase "medium" for the MEDIUM enum value.
   */
  @Test
  public void toStringMediumReturnsLowercase()
  {
    requireThat(PatienceLevel.MEDIUM.toString(), "str").isEqualTo("medium");
  }

  /**
   * Verifies that toString returns lowercase "high" for the HIGH enum value.
   */
  @Test
  public void toStringHighReturnsLowercase()
  {
    requireThat(PatienceLevel.HIGH.toString(), "str").isEqualTo("high");
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for an unrecognized value.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*UNKNOWN.*")
  public void fromStringInvalidThrowsIllegalArgument()
  {
    PatienceLevel.fromString("unknown");
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for blank input, with a message referencing
   * the parameter name.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*value.*")
  public void fromStringBlankThrowsIllegalArgument()
  {
    PatienceLevel.fromString("");
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for whitespace-padded input.
   * <p>
   * The implementation does not strip whitespace before matching, so " low " is not a valid patience level.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".* LOW .*")
  public void fromStringWhitespacePaddedThrowsIllegalArgument()
  {
    PatienceLevel.fromString(" low ");
  }

  /**
   * Verifies that fromString throws NullPointerException for null input.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*value.*")
  public void fromStringNullThrowsNullPointerException()
  {
    PatienceLevel.fromString(null);
  }
}
