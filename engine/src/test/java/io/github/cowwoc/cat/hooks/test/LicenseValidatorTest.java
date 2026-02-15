/*
 * Copyright (c) 2026 Gili Tzabari. All rights reserved.
 *
 * Licensed under the CAT Commercial License.
 * See LICENSE.md in the project root for license terms.
 */
package io.github.cowwoc.cat.hooks.test;

import io.github.cowwoc.cat.hooks.JvmScope;
import io.github.cowwoc.cat.hooks.licensing.LicenseResult;
import io.github.cowwoc.cat.hooks.licensing.LicenseValidator;
import io.github.cowwoc.cat.hooks.licensing.Tier;
import io.github.cowwoc.pouch10.core.WrappedCheckedException;
import org.testng.annotations.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

import static io.github.cowwoc.requirements13.java.DefaultJavaValidators.requireThat;

/**
 * Tests for LicenseValidator JWT validation logic.
 * <p>
 * Tests verify JWT parsing, signature verification, expiration handling,
 * and grace period logic. Uses generated Ed25519 key pairs for testing.
 * <p>
 * Tests are designed for parallel execution - each test is self-contained
 * with no shared state.
 */
public class LicenseValidatorTest
{
  /**
   * Verifies that missing config file returns indie tier.
   */
  @Test
  public void missingConfigFileReturnsIndie() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempDir();
      Path pluginRoot = createPluginRoot();
      try
      {
        LicenseValidator validator = new LicenseValidator(pluginRoot, mapper);
        LicenseResult result = validator.validate(tempDir);

        requireThat(result.valid(), "valid").isFalse();
        requireThat(result.tier(), "tier").isEqualTo(Tier.INDIE);
        requireThat(result.error(), "error").isEmpty();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
  }

  /**
   * Verifies that empty license token returns indie tier.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void emptyLicenseTokenReturnsIndie() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempDir();
      Path pluginRoot = createPluginRoot();
      try
      {
        Path catDir = tempDir.resolve(".claude").resolve("cat");
        Files.createDirectories(catDir);
        Files.writeString(catDir.resolve("cat-config.local.json"), """
          {
            "license": ""
          }
          """);

        LicenseValidator validator = new LicenseValidator(pluginRoot, mapper);
        LicenseResult result = validator.validate(tempDir);

        requireThat(result.valid(), "valid").isFalse();
        requireThat(result.tier(), "tier").isEqualTo(Tier.INDIE);
        requireThat(result.error(), "error").isEmpty();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
  }

  /**
   * Verifies that valid JWT format with 3 parts is parsed correctly.
   *
   * @throws Exception if key generation or signing fails
   */
  @Test
  public void validJwtFormatIsParsedCorrectly() throws Exception
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempDir();
      Path pluginRoot = createPluginRoot();
      try
      {
        KeyPair keyPair = generateKeyPair();
        writePublicKey(pluginRoot, keyPair.getPublic());

        String token = createToken(keyPair.getPrivate(), "team", null, null);
        writeConfig(tempDir, token);

        LicenseValidator validator = new LicenseValidator(pluginRoot, mapper);
        LicenseResult result = validator.validate(tempDir);

        requireThat(result.valid(), "valid").isTrue();
        requireThat(result.tier(), "tier").isEqualTo(Tier.TEAM);
        requireThat(result.expired(), "expired").isFalse();
        requireThat(result.error(), "error").isEmpty();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
  }

  /**
   * Verifies that invalid JWT format (not 3 parts) returns error.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void invalidJwtFormatReturnsError() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempDir();
      Path pluginRoot = createPluginRoot();
      try
      {
        KeyPair keyPair = generateKeyPair();
        writePublicKey(pluginRoot, keyPair.getPublic());

        String token = "invalid.token";
        writeConfig(tempDir, token);

        LicenseValidator validator = new LicenseValidator(pluginRoot, mapper);
        LicenseResult result = validator.validate(tempDir);

        requireThat(result.valid(), "valid").isFalse();
        requireThat(result.tier(), "tier").isEqualTo(Tier.INDIE);
        requireThat(result.error(), "error").contains("Invalid token format");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
  }

  /**
   * Verifies that missing public key returns error.
   *
   * @throws Exception if key generation or signing fails
   */
  @Test
  public void missingPublicKeyReturnsError() throws Exception
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempDir();
      Path pluginRoot = createPluginRoot();
      try
      {
        KeyPair keyPair = generateKeyPair();
        String token = createToken(keyPair.getPrivate(), "team", null, null);
        writeConfig(tempDir, token);

        LicenseValidator validator = new LicenseValidator(pluginRoot, mapper);
        LicenseResult result = validator.validate(tempDir);

        requireThat(result.valid(), "valid").isFalse();
        requireThat(result.tier(), "tier").isEqualTo(Tier.INDIE);
        requireThat(result.error(), "error").contains("Public key not found");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
  }

  /**
   * Verifies that expired token past grace period falls back to indie.
   *
   * @throws Exception if key generation or signing fails
   */
  @Test
  public void expiredTokenPastGracePeriodFallsBackToIndie() throws Exception
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempDir();
      Path pluginRoot = createPluginRoot();
      try
      {
        KeyPair keyPair = generateKeyPair();
        writePublicKey(pluginRoot, keyPair.getPublic());

        long expiredTime = Instant.now().minusSeconds(10 * 86_400).getEpochSecond();
        String token = createToken(keyPair.getPrivate(), "team", expiredTime, 7);
        writeConfig(tempDir, token);

        LicenseValidator validator = new LicenseValidator(pluginRoot, mapper);
        LicenseResult result = validator.validate(tempDir);

        requireThat(result.valid(), "valid").isTrue();
        requireThat(result.tier(), "tier").isEqualTo(Tier.INDIE);
        requireThat(result.expired(), "expired").isTrue();
        requireThat(result.inGrace(), "inGrace").isFalse();
        requireThat(result.warning(), "warning").contains("expired");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
  }

  /**
   * Verifies that expired token within grace period retains tier with warning.
   *
   * @throws Exception if key generation or signing fails
   */
  @Test
  public void expiredTokenWithinGracePeriodRetainsTierWithWarning() throws Exception
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempDir();
      Path pluginRoot = createPluginRoot();
      try
      {
        KeyPair keyPair = generateKeyPair();
        writePublicKey(pluginRoot, keyPair.getPublic());

        long expiredTime = Instant.now().minusSeconds(3 * 86_400).getEpochSecond();
        String token = createToken(keyPair.getPrivate(), "team", expiredTime, 7);
        writeConfig(tempDir, token);

        LicenseValidator validator = new LicenseValidator(pluginRoot, mapper);
        LicenseResult result = validator.validate(tempDir);

        requireThat(result.valid(), "valid").isTrue();
        requireThat(result.tier(), "tier").isEqualTo(Tier.TEAM);
        requireThat(result.expired(), "expired").isTrue();
        requireThat(result.inGrace(), "inGrace").isTrue();
        requireThat(result.warning(), "warning").contains("expired");
        requireThat(result.warning(), "warning").contains("3 days ago");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
  }

  /**
   * Verifies that token with invalid signature returns error.
   *
   * @throws Exception if key generation or signing fails
   */
  @Test
  public void tokenWithInvalidSignatureReturnsError() throws Exception
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempDir();
      Path pluginRoot = createPluginRoot();
      try
      {
        KeyPair keyPair1 = generateKeyPair();
        KeyPair keyPair2 = generateKeyPair();
        writePublicKey(pluginRoot, keyPair1.getPublic());

        String token = createToken(keyPair2.getPrivate(), "team", null, null);
        writeConfig(tempDir, token);

        LicenseValidator validator = new LicenseValidator(pluginRoot, mapper);
        LicenseResult result = validator.validate(tempDir);

        requireThat(result.valid(), "valid").isFalse();
        requireThat(result.tier(), "tier").isEqualTo(Tier.INDIE);
        requireThat(result.error(), "error").contains("Invalid signature");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
  }

  /**
   * Verifies that token with no expiration is valid indefinitely.
   *
   * @throws Exception if key generation or signing fails
   */
  @Test
  public void tokenWithNoExpirationIsValidIndefinitely() throws Exception
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempDir();
      Path pluginRoot = createPluginRoot();
      try
      {
        KeyPair keyPair = generateKeyPair();
        writePublicKey(pluginRoot, keyPair.getPublic());

        String token = createToken(keyPair.getPrivate(), "enterprise", null, null);
        writeConfig(tempDir, token);

        LicenseValidator validator = new LicenseValidator(pluginRoot, mapper);
        LicenseResult result = validator.validate(tempDir);

        requireThat(result.valid(), "valid").isTrue();
        requireThat(result.tier(), "tier").isEqualTo(Tier.ENTERPRISE);
        requireThat(result.expired(), "expired").isFalse();
        requireThat(result.inGrace(), "inGrace").isFalse();
        requireThat(result.daysRemaining(), "daysRemaining").isEqualTo(0);
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
  }

  /**
   * Verifies that token with 1 part returns error.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void tokenWithOnePartReturnsError() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempDir();
      Path pluginRoot = createPluginRoot();
      try
      {
        KeyPair keyPair = generateKeyPair();
        writePublicKey(pluginRoot, keyPair.getPublic());

        String token = "singlepart";
        writeConfig(tempDir, token);

        LicenseValidator validator = new LicenseValidator(pluginRoot, mapper);
        LicenseResult result = validator.validate(tempDir);

        requireThat(result.valid(), "valid").isFalse();
        requireThat(result.tier(), "tier").isEqualTo(Tier.INDIE);
        requireThat(result.error(), "error").contains("Invalid token format");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
  }

  /**
   * Verifies that token with 4 parts returns error.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void tokenWithFourPartsReturnsError() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempDir();
      Path pluginRoot = createPluginRoot();
      try
      {
        KeyPair keyPair = generateKeyPair();
        writePublicKey(pluginRoot, keyPair.getPublic());

        String token = "part1.part2.part3.part4";
        writeConfig(tempDir, token);

        LicenseValidator validator = new LicenseValidator(pluginRoot, mapper);
        LicenseResult result = validator.validate(tempDir);

        requireThat(result.valid(), "valid").isFalse();
        requireThat(result.tier(), "tier").isEqualTo(Tier.INDIE);
        requireThat(result.error(), "error").contains("Invalid token format");
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
  }

  /**
   * Verifies that token with invalid JSON in payload returns error.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void tokenWithInvalidJsonReturnsError() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempDir();
      Path pluginRoot = createPluginRoot();
      try
      {
        KeyPair keyPair = generateKeyPair();
        writePublicKey(pluginRoot, keyPair.getPublic());

        String headerB64 = base64UrlEncode("{\"alg\":\"Ed25519\"}".getBytes(StandardCharsets.UTF_8));
        String payloadB64 = base64UrlEncode("not-valid-json".getBytes(StandardCharsets.UTF_8));
        String signatureB64 = base64UrlEncode(new byte[64]);
        String token = headerB64 + "." + payloadB64 + "." + signatureB64;
        writeConfig(tempDir, token);

        LicenseValidator validator = new LicenseValidator(pluginRoot, mapper);
        LicenseResult result = validator.validate(tempDir);

        requireThat(result.valid(), "valid").isFalse();
        requireThat(result.tier(), "tier").isEqualTo(Tier.INDIE);
        requireThat(result.error(), "error").isNotEmpty();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
  }

  /**
   * Verifies that token with invalid base64 returns error.
   *
   * @throws IOException if an I/O error occurs
   */
  @Test
  public void tokenWithInvalidBase64ReturnsError() throws IOException
  {
    try (JvmScope scope = new TestJvmScope())
    {
      JsonMapper mapper = scope.getJsonMapper();
      Path tempDir = createTempDir();
      Path pluginRoot = createPluginRoot();
      try
      {
        KeyPair keyPair = generateKeyPair();
        writePublicKey(pluginRoot, keyPair.getPublic());

        String token = "not-base64!@#.not-base64!@#.not-base64!@#";
        writeConfig(tempDir, token);

        LicenseValidator validator = new LicenseValidator(pluginRoot, mapper);
        LicenseResult result = validator.validate(tempDir);

        requireThat(result.valid(), "valid").isFalse();
        requireThat(result.tier(), "tier").isEqualTo(Tier.INDIE);
        requireThat(result.error(), "error").isNotEmpty();
      }
      finally
      {
        TestUtils.deleteDirectoryRecursively(tempDir);
        TestUtils.deleteDirectoryRecursively(pluginRoot);
      }
    }
  }

  /**
   * Creates a temporary directory for test isolation.
   *
   * @return the path to the created temporary directory
   */
  private Path createTempDir()
  {
    try
    {
      return Files.createTempDirectory("license-test");
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Creates a temporary plugin root directory with config subdirectory.
   *
   * @return the path to the plugin root directory
   */
  private Path createPluginRoot()
  {
    try
    {
      Path pluginRoot = Files.createTempDirectory("plugin-root");
      Files.createDirectories(pluginRoot.resolve("config"));
      return pluginRoot;
    }
    catch (IOException e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Generates an Ed25519 key pair for testing.
   *
   * @return the generated key pair
   */
  private KeyPair generateKeyPair()
  {
    try
    {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
      return generator.generateKeyPair();
    }
    catch (Exception e)
    {
      throw WrappedCheckedException.wrap(e);
    }
  }

  /**
   * Writes a public key to the plugin config directory in PEM format.
   *
   * @param pluginRoot the plugin root directory
   * @param publicKey the public key to write
   * @throws IOException if writing fails
   */
  private void writePublicKey(Path pluginRoot, PublicKey publicKey) throws IOException
  {
    String base64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
    String pem = "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
    Path keyPath = pluginRoot.resolve("config").resolve("cat-public-key.pem");
    Files.writeString(keyPath, pem);
  }

  /**
   * Creates a signed JWT token for testing.
   *
   * @param privateKey the private key for signing
   * @param tier the license tier
   * @param exp the expiration timestamp (null for no expiration)
   * @param graceDays the grace period in days (null for default)
   * @return the JWT token string
   * @throws Exception if signing fails
   */
  private String createToken(PrivateKey privateKey, String tier, Long exp, Integer graceDays) throws Exception
  {
    String header = """
      {"alg":"Ed25519","typ":"JWT"}""";
    String headerB64 = base64UrlEncode(header.getBytes(StandardCharsets.UTF_8));

    StringBuilder payload = new StringBuilder(100);
    payload.append("{\"tier\":\"").append(tier).append('\"');
    if (exp != null)
      payload.append(",\"exp\":").append(exp);
    if (graceDays != null)
      payload.append(",\"grace_days\":").append(graceDays);
    payload.append('}');

    String payloadB64 = base64UrlEncode(payload.toString().getBytes(StandardCharsets.UTF_8));

    String message = headerB64 + "." + payloadB64;
    Signature sig = Signature.getInstance("Ed25519");
    sig.initSign(privateKey);
    sig.update(message.getBytes(StandardCharsets.UTF_8));
    byte[] signature = sig.sign();
    String signatureB64 = base64UrlEncode(signature);

    return message + "." + signatureB64;
  }

  /**
   * Encodes bytes to base64url format without padding.
   *
   * @param data the bytes to encode
   * @return the base64url string
   */
  private String base64UrlEncode(byte[] data)
  {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
  }

  /**
   * Writes a config file with the license token.
   *
   * @param projectDir the project directory
   * @param token the license token
   * @throws IOException if writing fails
   */
  private void writeConfig(Path projectDir, String token) throws IOException
  {
    Path catDir = projectDir.resolve(".claude").resolve("cat");
    Files.createDirectories(catDir);
    Files.writeString(catDir.resolve("cat-config.local.json"),
      "{\"license\":\"" + token + "\"}");
  }
}
