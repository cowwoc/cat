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
   * Verifies that fromString is case-insensitive for all three values.
   */
  @Test
  public void fromStringIsCaseInsensitive()
  {
    requireThat(CautionLevel.fromString("LOW"), "lowLevel").isEqualTo(CautionLevel.LOW);
    requireThat(CautionLevel.fromString("MEDIUM"), "mediumLevel").isEqualTo(CautionLevel.MEDIUM);
    requireThat(CautionLevel.fromString("HIGH"), "highLevel").isEqualTo(CautionLevel.HIGH);
  }

  /**
   * Verifies that toString returns lowercase "low" for the LOW enum value.
   */
  @Test
  public void toStringLowReturnsLowercase()
  {
    requireThat(CautionLevel.LOW.toString(), "CautionLevel.LOW.toString()").isEqualTo("low");
  }

  /**
   * Verifies that toString returns lowercase "medium" for the MEDIUM enum value.
   */
  @Test
  public void toStringMediumReturnsLowercase()
  {
    requireThat(CautionLevel.MEDIUM.toString(), "CautionLevel.MEDIUM.toString()").isEqualTo("medium");
  }

  /**
   * Verifies that toString returns lowercase "high" for the HIGH enum value.
   */
  @Test
  public void toStringHighReturnsLowercase()
  {
    requireThat(CautionLevel.HIGH.toString(), "CautionLevel.HIGH.toString()").isEqualTo("high");
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for an unrecognized value.
   */
  @Test(expectedExceptions = IllegalArgumentException.class,
    expectedExceptionsMessageRegExp = ".*INVALID.*")
  public void fromStringInvalidThrowsIllegalArgument()
  {
    CautionLevel.fromString("invalid");
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

  /**
   * Verifies that LOW disables unit tests (compile only).
   */
  @Test
  public void lowIsUnitTestDisabled()
  {
    requireThat(CautionLevel.LOW.isUnitTestEnabled(), "isUnitTestEnabled").isFalse();
  }

  /**
   * Verifies that MEDIUM enables unit tests.
   */
  @Test
  public void mediumIsUnitTestEnabled()
  {
    requireThat(CautionLevel.MEDIUM.isUnitTestEnabled(), "isUnitTestEnabled").isTrue();
  }

  /**
   * Verifies that HIGH enables unit tests.
   */
  @Test
  public void highIsUnitTestEnabled()
  {
    requireThat(CautionLevel.HIGH.isUnitTestEnabled(), "isUnitTestEnabled").isTrue();
  }

  /**
   * Verifies that LOW disables E2E tests.
   */
  @Test
  public void lowIsE2eDisabled()
  {
    requireThat(CautionLevel.LOW.isE2eEnabled(), "isE2eEnabled").isFalse();
  }

  /**
   * Verifies that MEDIUM disables E2E tests.
   */
  @Test
  public void mediumIsE2eDisabled()
  {
    requireThat(CautionLevel.MEDIUM.isE2eEnabled(), "isE2eEnabled").isFalse();
  }

  /**
   * Verifies that HIGH enables E2E tests.
   */
  @Test
  public void highIsE2eEnabled()
  {
    requireThat(CautionLevel.HIGH.isE2eEnabled(), "isE2eEnabled").isTrue();
  }
}
