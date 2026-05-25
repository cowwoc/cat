/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.client.test;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertEquals;

public class JsonHandoffRoundTripTest
{
  /**
   * Verifies that JSON containing braces, quotes, and brackets survives a write/read round-trip.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void jsonWithSpecialCharactersSurvivesRound() throws IOException
  {
    Path tempFile = Files.createTempFile("json-handoff-", ".json");
    try
    {
      String original = "[{\"hash\":\"abc123\",\"message\":\"feat: add {feature} with " +
        "\\\"quotes\\\" and [brackets]\",\"filesChanged\":3}]";
      Files.writeString(tempFile, original);
      String roundTrip = Files.readString(tempFile);
      assertEquals(roundTrip, original);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that pretty-printed multiline JSON survives a write/read round-trip unchanged.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void multilineJsonSurvivesRoundTrip() throws IOException
  {
    Path tempFile = Files.createTempFile("json-handoff-", ".json");
    try
    {
      String original = "[\n" +
        "  {\n" +
        "    \"hash\": \"def456\",\n" +
        "    \"message\": \"refactor: restructure {module}: [old] -> [new]\",\n" +
        "    \"filesChanged\": 7\n" +
        "  }\n" +
        "]";
      Files.writeString(tempFile, original);
      String roundTrip = Files.readString(tempFile);
      assertEquals(roundTrip, original);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Verifies that an empty JSON array survives a write/read round-trip unchanged.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyJsonArraySurvivesRoundTrip() throws IOException
  {
    Path tempFile = Files.createTempFile("json-handoff-", ".json");
    try
    {
      String original = "[]";
      Files.writeString(tempFile, original);
      String roundTrip = Files.readString(tempFile);
      assertEquals(roundTrip, original);
    }
    finally
    {
      Files.deleteIfExists(tempFile);
    }
  }
}
