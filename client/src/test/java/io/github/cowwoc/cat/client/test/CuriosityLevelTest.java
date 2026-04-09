/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import io.github.cowwoc.cat.claude.hook.util.CuriosityLevel;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for {@link CuriosityLevel} enum behavior and curiosity-related plugin file conventions.
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

  /**
   * Verifies that the SPRT test scenario file for the instruction-builder-agent curiosity gate uses
   * {@code curiosity} terminology, not the legacy {@code effort} terminology.
   */
  @Test
  public void sprtTestScenarioUsesCuriosityTerminology() throws IOException
  {
    Path sprtFile = Paths.get(System.getProperty("user.dir"), "..",
      "plugin/tests/skills/instruction-builder-agent/first-use/" +
        "step43-sprt-runs-when-curiosity-not-low.md").normalize();
    requireThat(sprtFile.toFile().exists(), "sprtFile").isTrue();
    String content = Files.readString(sprtFile);
    requireThat(content, "content").doesNotContain("effort level");
    requireThat(content, "content").contains("curiosity level");
  }
}
