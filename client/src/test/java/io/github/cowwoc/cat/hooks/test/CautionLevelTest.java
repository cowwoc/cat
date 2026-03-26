/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.util.CautionLevel;

import org.testng.annotations.Test;

/**
 * Tests for {@link CautionLevel}.
 */
public final class CautionLevelTest
{
  /**
   * Verifies that fromString("low") returns LOW.
   */
  @Test
  public void fromStringLowReturnsLOW()
  {
    requireThat(CautionLevel.fromString("low"), "level").isEqualTo(CautionLevel.LOW);
  }

  /**
   * Verifies that fromString("medium") returns MEDIUM.
   */
  @Test
  public void fromStringMediumReturnsMEDIUM()
  {
    requireThat(CautionLevel.fromString("medium"), "level").isEqualTo(CautionLevel.MEDIUM);
  }

  /**
   * Verifies that fromString("high") returns HIGH.
   */
  @Test
  public void fromStringHighReturnsHIGH()
  {
    requireThat(CautionLevel.fromString("high"), "level").isEqualTo(CautionLevel.HIGH);
  }

  /**
   * Verifies that fromString is case-insensitive (e.g., "HIGH" returns HIGH).
   */
  @Test
  public void fromStringIsCaseInsensitive()
  {
    requireThat(CautionLevel.fromString("HIGH"), "level").isEqualTo(CautionLevel.HIGH);
  }

  /**
   * Verifies that toString returns lowercase "low" for the LOW enum value.
   */
  @Test
  public void toStringReturnsLowForLOW()
  {
    requireThat(CautionLevel.LOW.toString(), "CautionLevel.LOW.toString()").isEqualTo("low");
  }

  /**
   * Verifies that toString returns lowercase "medium" for the MEDIUM enum value.
   */
  @Test
  public void toStringReturnsMediumForMEDIUM()
  {
    requireThat(CautionLevel.MEDIUM.toString(), "CautionLevel.MEDIUM.toString()").isEqualTo("medium");
  }

  /**
   * Verifies that toString returns lowercase "high" for the HIGH enum value.
   */
  @Test
  public void toStringReturnsHighForHIGH()
  {
    requireThat(CautionLevel.HIGH.toString(), "CautionLevel.HIGH.toString()").isEqualTo("high");
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for an unrecognized value.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*UNKNOWN.*")
  public void fromStringInvalidThrowsIllegalArgument()
  {
    CautionLevel.fromString("unknown");
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for blank input, with a message referencing
   * the parameter name.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*value.*")
  public void fromStringBlankThrowsIllegalArgument()
  {
    CautionLevel.fromString("");
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for whitespace-padded input.
   * <p>
   * The implementation does not strip whitespace before matching, so " low " is not a valid caution level.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".* LOW .*")
  public void fromStringWhitespacePaddedThrowsIllegalArgument()
  {
    CautionLevel.fromString(" low ");
  }

  /**
   * Verifies that fromString throws NullPointerException for null input.
   */
  @Test(expectedExceptions = NullPointerException.class,
    expectedExceptionsMessageRegExp = ".*value.*")
  public void fromStringNullThrowsNullPointerException()
  {
    CautionLevel.fromString(null);
  }
}
