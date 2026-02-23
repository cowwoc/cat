/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;
import io.github.cowwoc.cat.hooks.util.GlobMatcher;
import org.testng.annotations.Test;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for GlobMatcher - glob pattern compilation, caching, and matching behavior.
 */
public final class GlobMatcherTest
{
  // ---- Basic extension glob: *.java ----

  /**
   * Verifies that {@code *.java} matches a simple Java filename.
   */
  @Test
  public void basicGlobMatchesJavaFile()
  {
    requireThat(GlobMatcher.matches("*.java", "MyClass.java"),
      "matches(*.java,MyClass.java)").isTrue();
  }

  /**
   * Verifies that {@code *.java} does not match a TypeScript file.
   */
  @Test
  public void basicGlobDoesNotMatchTsFile()
  {
    requireThat(GlobMatcher.matches("*.java", "MyClass.ts"),
      "matches(*.java,MyClass.ts)").isFalse();
  }

  /**
   * Verifies that {@code *.java} does not match a file without extension.
   */
  @Test
  public void basicGlobDoesNotMatchFileWithoutExtension()
  {
    requireThat(GlobMatcher.matches("*.java", "MyClass"),
      "matches(*.java,MyClass)").isFalse();
  }

  // ---- Double-star glob: src/**/*.java ----

  /**
   * Verifies that {@code src/**} matches a deeply nested path under src/.
   */
  @Test
  public void doubleStarMatchesDeepPath()
  {
    requireThat(GlobMatcher.matches("src/**/*.java", "src/main/MyClass.java"),
      "matches(src/**/*.java,src/main/MyClass.java)").isTrue();
    requireThat(GlobMatcher.matches("src/**/*.java", "src/main/java/io/example/MyClass.java"),
      "matches(src/**/*.java,src/main/java/io/example/MyClass.java)").isTrue();
  }

  /**
   * Verifies that {@code src/**} does not match a path outside src/.
   */
  @Test
  public void doubleStarDoesNotMatchOutsideRoot()
  {
    requireThat(GlobMatcher.matches("src/**/*.java", "test/MyTest.java"),
      "matches(src/**/*.java,test/MyTest.java)").isFalse();
  }

  // ---- Single-char wildcard: file?.txt ----

  /**
   * Verifies that {@code file?.txt} matches a filename with exactly one extra character.
   */
  @Test
  public void questionMarkMatchesSingleChar()
  {
    requireThat(GlobMatcher.matches("file?.txt", "fileA.txt"),
      "matches(file?.txt,fileA.txt)").isTrue();
  }

  /**
   * Verifies that {@code file?.txt} does not match a filename with zero extra characters.
   */
  @Test
  public void questionMarkDoesNotMatchZeroChars()
  {
    requireThat(GlobMatcher.matches("file?.txt", "file.txt"),
      "matches(file?.txt,file.txt)").isFalse();
  }

  /**
   * Verifies that {@code file?.txt} does not match a filename with two extra characters.
   */
  @Test
  public void questionMarkDoesNotMatchTwoChars()
  {
    requireThat(GlobMatcher.matches("file?.txt", "fileAB.txt"),
      "matches(file?.txt,fileAB.txt)").isFalse();
  }

  /**
   * Verifies that {@code file?.txt} does not match a path separator.
   */
  @Test
  public void questionMarkDoesNotMatchPathSeparator()
  {
    requireThat(GlobMatcher.matches("file?.txt", "file/.txt"),
      "matches(file?.txt,file/.txt)").isFalse();
  }

  // ---- Caching: calling matches() twice returns correct results ----

  /**
   * Verifies that calling matches() twice with the same glob produces consistent results.
   * This exercises the cached PathMatcher path.
   */
  @Test
  public void cachingProducesConsistentResults()
  {
    // First call compiles and caches the matcher
    boolean firstCall = GlobMatcher.matches("*.java", "Example.java");
    // Second call reuses the cached matcher
    boolean secondCall = GlobMatcher.matches("*.java", "Example.java");
    requireThat(firstCall, "firstCall").isTrue();
    requireThat(secondCall, "secondCall").isTrue();

    // Also verify a non-matching call uses the cached matcher correctly
    boolean nonMatch = GlobMatcher.matches("*.java", "Example.ts");
    requireThat(nonMatch, "nonMatch").isFalse();
  }

  // ---- Exact match ----

  /**
   * Verifies that an exact glob pattern matches the exact string.
   */
  @Test
  public void exactGlobMatchesExactString()
  {
    requireThat(GlobMatcher.matches("pom.xml", "pom.xml"),
      "matches(pom.xml,pom.xml)").isTrue();
    requireThat(GlobMatcher.matches("pom.xml", "pomXxml"),
      "matches(pom.xml,pomXxml)").isFalse();
  }

  // ---- Null safety: NullPointerException documented behavior ----

  /**
   * Verifies that passing null glob throws NullPointerException (JDK PathMatcher behavior).
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void nullGlobThrowsNullPointerException()
  {
    GlobMatcher.matches(null, "file.java");
  }

  /**
   * Verifies that passing null path throws NullPointerException (JDK PathMatcher behavior).
   */
  @Test(expectedExceptions = NullPointerException.class)
  public void nullPathThrowsNullPointerException()
  {
    GlobMatcher.matches("*.java", null);
  }
}
