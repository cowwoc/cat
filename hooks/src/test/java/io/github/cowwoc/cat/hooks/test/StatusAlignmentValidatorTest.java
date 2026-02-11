package io.github.cowwoc.cat.hooks.test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

import io.github.cowwoc.cat.hooks.util.StatusAlignmentValidator;
import io.github.cowwoc.cat.hooks.util.StatusAlignmentValidator.ValidationResult;
import org.testng.annotations.Test;

/**
 * Tests for StatusAlignmentValidator.
 */
public final class StatusAlignmentValidatorTest
{
  /**
   * Verifies that a properly aligned box passes validation.
   */
  @Test
  public void validBoxPasses()
  {
    String input = """
      ╭─────────────────╮
      │ Content line 1  │
      │ Content line 2  │
      ╰─────────────────╯""";
    ValidationResult result = StatusAlignmentValidator.validate(input);
    requireThat(result.valid(), "valid").isTrue();
    requireThat(result.errors(), "errors").isEmpty();
    requireThat(result.contentLines(), "contentLines").isEqualTo(2);
  }

  /**
   * Verifies that missing box structure is detected.
   */
  @Test
  public void missingBoxStructure()
  {
    String input = "Just some text without box structure";
    ValidationResult result = StatusAlignmentValidator.validate(input);
    requireThat(result.valid(), "valid").isFalse();
    requireThat(result.errors(), "errors").contains("ERROR: No box structure found");
  }

  /**
   * Verifies that missing left border is detected.
   */
  @Test
  public void missingLeftBorder()
  {
    String input = """
      ╭─────────────────╮
       Content line 1  │
      ╰─────────────────╯""";
    ValidationResult result = StatusAlignmentValidator.validate(input);
    requireThat(result.valid(), "valid").isFalse();
    requireThat(result.errors().get(0), "error").contains("Missing left border │");
  }

  /**
   * Verifies that missing right border is detected.
   */
  @Test
  public void missingRightBorder()
  {
    String input = """
      ╭─────────────────╮
      │ Content line 1
      ╰─────────────────╯""";
    ValidationResult result = StatusAlignmentValidator.validate(input);
    requireThat(result.valid(), "valid").isFalse();
    requireThat(result.errors().get(0), "error").contains("Missing right border │");
  }

  /**
   * Verifies that divider lines are properly skipped.
   */
  @Test
  public void dividerLinesSkipped()
  {
    String input = """
      ╭─────────────────╮
      │ Section 1       │
      ├─────────────────┤
      │ Section 2       │
      ╰─────────────────╯""";
    ValidationResult result = StatusAlignmentValidator.validate(input);
    requireThat(result.valid(), "valid").isTrue();
    requireThat(result.contentLines(), "contentLines").isEqualTo(2);
  }

  /**
   * Verifies that inner box structure is validated.
   */
  @Test
  public void innerBoxValidation()
  {
    String input = """
      ╭─────────────────────────────╮
      │ Outer box                   │
      │ ╭─────────────────────────╮ │
      │ │ Inner box content       │ │
      │ ╰─────────────────────────╯ │
      ╰─────────────────────────────╯""";
    ValidationResult result = StatusAlignmentValidator.validate(input);
    requireThat(result.valid(), "valid").isTrue();
    requireThat(result.contentLines(), "contentLines").isEqualTo(4);
  }

  /**
   * Verifies that inner box with missing outer right border is detected.
   */
  @Test
  public void innerBoxMissingOuterRightBorder()
  {
    String input = """
      ╭─────────────────────────────╮
      │ ╭─────────────────────────╮
      ╰─────────────────────────────╯""";
    ValidationResult result = StatusAlignmentValidator.validate(input);
    requireThat(result.valid(), "valid").isFalse();
    requireThat(result.errors().size(), "errorCount").isGreaterThanOrEqualTo(2);
    requireThat(result.errors().get(1), "error").contains("Inner box top border missing outer right border");
  }

  /**
   * Verifies that multiple errors are collected.
   */
  @Test
  public void multipleErrors()
  {
    String input = """
      ╭─────────────────╮
       Content line 1
       Content line 2
      ╰─────────────────╯""";
    ValidationResult result = StatusAlignmentValidator.validate(input);
    requireThat(result.valid(), "valid").isFalse();
    requireThat(result.errors().size(), "errorCount").isGreaterThanOrEqualTo(4);
  }

  /**
   * Verifies that validateAndFormat produces proper output for valid input.
   */
  @Test
  public void formatValidInput()
  {
    String input = """
      ╭─────────────────╮
      │ Content line 1  │
      │ Content line 2  │
      ╰─────────────────╯""";
    String result = StatusAlignmentValidator.validateAndFormat(input);
    requireThat(result, "result").startsWith("PASS: Alignment validation successful");
    requireThat(result, "result").contains("2 content lines");
  }

  /**
   * Verifies that validateAndFormat produces proper output for invalid input.
   */
  @Test
  public void formatInvalidInput()
  {
    String input = """
      ╭─────────────────╮
       Content line 1
      ╰─────────────────╯""";
    String result = StatusAlignmentValidator.validateAndFormat(input);
    requireThat(result, "result").startsWith("ALIGNMENT ERRORS DETECTED");
    requireThat(result, "result").contains("Missing left border");
  }

  /**
   * Verifies that trailing whitespace does not affect validation.
   */
  @Test
  public void trailingWhitespaceHandled()
  {
    String input = """
      ╭─────────────────╮
      │ Content line 1  │   \s
      │ Content line 2  │\s
      ╰─────────────────╯""";
    ValidationResult result = StatusAlignmentValidator.validate(input);
    requireThat(result.valid(), "valid").isTrue();
  }

  /**
   * Verifies that empty content lines with borders are valid.
   */
  @Test
  public void emptyContentLine()
  {
    String input = """
      ╭─────────────────╮
      │                 │
      │ Content line    │
      ╰─────────────────╯""";
    ValidationResult result = StatusAlignmentValidator.validate(input);
    requireThat(result.valid(), "valid").isTrue();
    requireThat(result.contentLines(), "contentLines").isEqualTo(2);
  }

  /**
   * Verifies that nested boxes are validated correctly.
   */
  @Test
  public void nestedBoxes()
  {
    String input = """
      ╭─────────────────────────────╮
      │ ╭─────────────────────────╮ │
      │ │ ╭───────────────────╮   │ │
      │ │ │ Deep content      │   │ │
      │ │ ╰───────────────────╯   │ │
      │ ╰─────────────────────────╯ │
      ╰─────────────────────────────╯""";
    ValidationResult result = StatusAlignmentValidator.validate(input);
    requireThat(result.valid(), "valid").isTrue();
  }

  /**
   * Verifies that line numbers in errors are accurate.
   */
  @Test
  public void errorLineNumbers()
  {
    String input = """
      ╭─────────────────╮
      │ Line 1          │
      │ Line 2
      │ Line 3          │
      ╰─────────────────╯""";
    ValidationResult result = StatusAlignmentValidator.validate(input);
    requireThat(result.valid(), "valid").isFalse();
    requireThat(result.errors().get(0), "error").contains("Line 3");
  }

  /**
   * Verifies that null input is rejected by validate().
   */
  @Test
  public void validateRejectsNullInput()
  {
    try
    {
      StatusAlignmentValidator.validate(null);
      requireThat(false, "expectedException").isEqualTo(true);
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("input");
    }
  }

  /**
   * Verifies that null input is rejected by validateAndFormat().
   */
  @Test
  public void validateAndFormatRejectsNullInput()
  {
    try
    {
      StatusAlignmentValidator.validateAndFormat(null);
      requireThat(false, "expectedException").isEqualTo(true);
    }
    catch (NullPointerException e)
    {
      requireThat(e.getMessage(), "message").contains("input");
    }
  }
}
