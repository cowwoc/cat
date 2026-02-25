/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.util.ConcernSeverity;

import org.testng.annotations.Test;

/**
 * Tests for {@link ConcernSeverity}.
 */
public final class ConcernSeverityTest
{
  /**
   * Verifies that fromString("critical") returns CRITICAL.
   */
  @Test
  public void fromStringCriticalReturnsCRITICAL()
  {
    requireThat(ConcernSeverity.fromString("critical"), "severity").isEqualTo(ConcernSeverity.CRITICAL);
  }

  /**
   * Verifies that fromString("high") returns HIGH.
   */
  @Test
  public void fromStringHighReturnsHIGH()
  {
    requireThat(ConcernSeverity.fromString("high"), "severity").isEqualTo(ConcernSeverity.HIGH);
  }

  /**
   * Verifies that fromString("medium") returns MEDIUM.
   */
  @Test
  public void fromStringMediumReturnsMEDIUM()
  {
    requireThat(ConcernSeverity.fromString("medium"), "severity").isEqualTo(ConcernSeverity.MEDIUM);
  }

  /**
   * Verifies that fromString("low") returns LOW.
   */
  @Test
  public void fromStringLowReturnsLOW()
  {
    requireThat(ConcernSeverity.fromString("low"), "severity").isEqualTo(ConcernSeverity.LOW);
  }

  /**
   * Verifies that fromString is case-insensitive (e.g., "HIGH" returns HIGH).
   */
  @Test
  public void fromStringIsCaseInsensitive()
  {
    requireThat(ConcernSeverity.fromString("HIGH"), "severity").isEqualTo(ConcernSeverity.HIGH);
    requireThat(ConcernSeverity.fromString("Critical"), "severity").isEqualTo(ConcernSeverity.CRITICAL);
    requireThat(ConcernSeverity.fromString("MEDIUM"), "severity").isEqualTo(ConcernSeverity.MEDIUM);
    requireThat(ConcernSeverity.fromString("LOW"), "severity").isEqualTo(ConcernSeverity.LOW);
  }

  /**
   * Verifies that toString returns lowercase "critical" for the CRITICAL enum value.
   */
  @Test
  public void toStringCriticalReturnsLowercase()
  {
    requireThat(ConcernSeverity.CRITICAL.toString(), "str").isEqualTo("critical");
  }

  /**
   * Verifies that toString returns lowercase "high" for the HIGH enum value.
   */
  @Test
  public void toStringHighReturnsLowercase()
  {
    requireThat(ConcernSeverity.HIGH.toString(), "str").isEqualTo("high");
  }

  /**
   * Verifies that toString returns lowercase "medium" for the MEDIUM enum value.
   */
  @Test
  public void toStringMediumReturnsLowercase()
  {
    requireThat(ConcernSeverity.MEDIUM.toString(), "str").isEqualTo("medium");
  }

  /**
   * Verifies that toString returns lowercase "low" for the LOW enum value.
   */
  @Test
  public void toStringLowReturnsLowercase()
  {
    requireThat(ConcernSeverity.LOW.toString(), "str").isEqualTo("low");
  }

  /**
   * Verifies that fromString throws IllegalArgumentException for an unrecognized value.
   */
  @Test
  public void fromStringInvalidThrowsIllegalArgument()
  {
    try
    {
      ConcernSeverity.fromString("unknown");
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("UNKNOWN");
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
      ConcernSeverity.fromString("");
    }
    catch (IllegalArgumentException e)
    {
      requireThat(e.getMessage(), "message").contains("value");
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
      ConcernSeverity.fromString(null);
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("value");
    }
  }

  /**
   * Verifies that isAtLeast returns true when the severity equals the threshold.
   */
  @Test
  public void isAtLeastReturnsTrueForEqualSeverity()
  {
    requireThat(ConcernSeverity.HIGH.isAtLeast(ConcernSeverity.HIGH), "result").isTrue();
    requireThat(ConcernSeverity.MEDIUM.isAtLeast(ConcernSeverity.MEDIUM), "result").isTrue();
    requireThat(ConcernSeverity.LOW.isAtLeast(ConcernSeverity.LOW), "result").isTrue();
    requireThat(ConcernSeverity.CRITICAL.isAtLeast(ConcernSeverity.CRITICAL), "result").isTrue();
  }

  /**
   * Verifies that isAtLeast returns true when the severity is higher than the threshold.
   * <p>
   * CRITICAL > HIGH > MEDIUM > LOW in terms of severity ordering.
   */
  @Test
  public void isAtLeastReturnsTrueForHigherSeverity()
  {
    requireThat(ConcernSeverity.CRITICAL.isAtLeast(ConcernSeverity.HIGH), "result").isTrue();
    requireThat(ConcernSeverity.CRITICAL.isAtLeast(ConcernSeverity.MEDIUM), "result").isTrue();
    requireThat(ConcernSeverity.CRITICAL.isAtLeast(ConcernSeverity.LOW), "result").isTrue();
    requireThat(ConcernSeverity.HIGH.isAtLeast(ConcernSeverity.MEDIUM), "result").isTrue();
    requireThat(ConcernSeverity.HIGH.isAtLeast(ConcernSeverity.LOW), "result").isTrue();
    requireThat(ConcernSeverity.MEDIUM.isAtLeast(ConcernSeverity.LOW), "result").isTrue();
  }

  /**
   * Verifies that isAtLeast returns false when the severity is lower than the threshold.
   */
  @Test
  public void isAtLeastReturnsFalseForLowerSeverity()
  {
    requireThat(ConcernSeverity.LOW.isAtLeast(ConcernSeverity.MEDIUM), "result").isFalse();
    requireThat(ConcernSeverity.LOW.isAtLeast(ConcernSeverity.HIGH), "result").isFalse();
    requireThat(ConcernSeverity.LOW.isAtLeast(ConcernSeverity.CRITICAL), "result").isFalse();
    requireThat(ConcernSeverity.MEDIUM.isAtLeast(ConcernSeverity.HIGH), "result").isFalse();
    requireThat(ConcernSeverity.MEDIUM.isAtLeast(ConcernSeverity.CRITICAL), "result").isFalse();
    requireThat(ConcernSeverity.HIGH.isAtLeast(ConcernSeverity.CRITICAL), "result").isFalse();
  }

  /**
   * Verifies that isAtLeast with a minSeverity of LOW returns true for all severity values.
   * <p>
   * With minSeverity="low", all concerns (CRITICAL, HIGH, MEDIUM, LOW) are at or above threshold.
   */
  @Test
  public void isAtLeastLowReturnsTrueForAllSeverities()
  {
    requireThat(ConcernSeverity.CRITICAL.isAtLeast(ConcernSeverity.LOW), "critical").isTrue();
    requireThat(ConcernSeverity.HIGH.isAtLeast(ConcernSeverity.LOW), "high").isTrue();
    requireThat(ConcernSeverity.MEDIUM.isAtLeast(ConcernSeverity.LOW), "medium").isTrue();
    requireThat(ConcernSeverity.LOW.isAtLeast(ConcernSeverity.LOW), "low").isTrue();
  }

  /**
   * Verifies that isAtLeast with a minSeverity of CRITICAL returns true only for CRITICAL.
   * <p>
   * With minSeverity="critical", only CRITICAL concerns pass the threshold.
   */
  @Test
  public void isAtLeastCriticalReturnsTrueOnlyForCritical()
  {
    requireThat(ConcernSeverity.CRITICAL.isAtLeast(ConcernSeverity.CRITICAL), "critical").isTrue();
    requireThat(ConcernSeverity.HIGH.isAtLeast(ConcernSeverity.CRITICAL), "high").isFalse();
    requireThat(ConcernSeverity.MEDIUM.isAtLeast(ConcernSeverity.CRITICAL), "medium").isFalse();
    requireThat(ConcernSeverity.LOW.isAtLeast(ConcernSeverity.CRITICAL), "low").isFalse();
  }
}
