/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.util.CuriosityLevel;
import org.testng.annotations.Test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for CuriosityLevel enum behavior.
 */
public class CuriosityLevelTest
{
  /**
   * Verifies that fromString("low") returns LOW.
   */
  @Test
  public void fromStringLowReturnsLow()
  {
    CuriosityLevel result = CuriosityLevel.fromString("low");
    requireThat(result, "result").isEqualTo(CuriosityLevel.LOW);
  }

  /**
   * Verifies that fromString("medium") returns MEDIUM.
   */
  @Test
  public void fromStringMediumReturnsMedium()
  {
    CuriosityLevel result = CuriosityLevel.fromString("medium");
    requireThat(result, "result").isEqualTo(CuriosityLevel.MEDIUM);
  }

  /**
   * Verifies that fromString("high") returns HIGH.
   */
  @Test
  public void fromStringHighReturnsHigh()
  {
    CuriosityLevel result = CuriosityLevel.fromString("high");
    requireThat(result, "result").isEqualTo(CuriosityLevel.HIGH);
  }

  /**
   * Verifies that fromString is case-insensitive: "LOW", "Medium", "HIGH" all parse correctly.
   */
  @Test
  public void fromStringCaseInsensitive()
  {
    requireThat(CuriosityLevel.fromString("LOW"), "LOW").isEqualTo(CuriosityLevel.LOW);
    requireThat(CuriosityLevel.fromString("Medium"), "Medium").isEqualTo(CuriosityLevel.MEDIUM);
    requireThat(CuriosityLevel.fromString("HIGH"), "HIGH").isEqualTo(CuriosityLevel.HIGH);
  }

  /**
   * Verifies that fromString("") throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void fromStringBlankThrowsIllegalArgumentException()
  {
    CuriosityLevel.fromString("");
  }

  /**
   * Verifies that fromString("invalid") throws IllegalArgumentException.
   */
  @Test(expectedExceptions = IllegalArgumentException.class)
  public void fromStringUnknownValueThrowsIllegalArgumentException()
  {
    CuriosityLevel.fromString("invalid");
  }

  /**
   * Verifies that toString() returns the lowercase name: "low", "medium", "high".
   */
  @Test
  public void toStringReturnsLowercase()
  {
    requireThat(CuriosityLevel.LOW.toString(), "LOW.toString()").isEqualTo("low");
    requireThat(CuriosityLevel.MEDIUM.toString(), "MEDIUM.toString()").isEqualTo("medium");
    requireThat(CuriosityLevel.HIGH.toString(), "HIGH.toString()").isEqualTo("high");
  }
}
