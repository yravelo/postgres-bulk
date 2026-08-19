package io.github.postgresbulk.pgjdbc.copy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValueEncoderRegistryTest {

  private final ValueEncoderRegistry registry = ValueEncoderRegistry.defaults();

  @Test
  void encodesStringsCharactersIntegralNumbersBooleansAndUuid() {
    assertEquals("A,B", encode(String.class, "A,B"));
    assertEquals("ñ", encode(Character.class, 'ñ'));
    assertEquals("-128", encode(Byte.class, Byte.MIN_VALUE));
    assertEquals("-7", encode(Byte.class, (byte) -7));
    assertEquals("0", encode(Byte.class, (byte) 0));
    assertEquals("8", encode(Byte.class, (byte) 8));
    assertEquals("127", encode(Byte.class, Byte.MAX_VALUE));
    assertEquals("-32768", encode(Short.class, Short.MIN_VALUE));
    assertEquals("-70", encode(Short.class, (short) -70));
    assertEquals("0", encode(Short.class, (short) 0));
    assertEquals("80", encode(Short.class, (short) 80));
    assertEquals("32767", encode(Short.class, Short.MAX_VALUE));
    assertEquals("-2147483648", encode(Integer.class, Integer.MIN_VALUE));
    assertEquals("-70000", encode(Integer.class, -70000));
    assertEquals("0", encode(Integer.class, 0));
    assertEquals("80000", encode(Integer.class, 80000));
    assertEquals("2147483647", encode(Integer.class, Integer.MAX_VALUE));
    assertEquals("-9223372036854775808", encode(Long.class, Long.MIN_VALUE));
    assertEquals("-7000000000", encode(Long.class, -7000000000L));
    assertEquals("0", encode(Long.class, 0L));
    assertEquals("8000000000", encode(Long.class, 8000000000L));
    assertEquals("9223372036854775807", encode(Long.class, Long.MAX_VALUE));
    assertEquals("true", encode(Boolean.class, true));
    assertEquals("false", encode(Boolean.class, false));
    UUID uuid = UUID.fromString("f81d4fae-7dec-11d0-a765-00a0c91e6bf6");
    assertEquals("f81d4fae-7dec-11d0-a765-00a0c91e6bf6", encode(UUID.class, uuid));
  }

  @Test
  void encodesArbitraryPrecisionNumbersWithoutPrecisionLossOrLocale() {
    assertEquals("0", encode(BigInteger.class, BigInteger.ZERO));
    assertEquals("-42", encode(BigInteger.class, BigInteger.valueOf(-42)));
    assertEquals(
        "1234567890123456789012345678901234567890",
        encode(BigInteger.class, new BigInteger("1234567890123456789012345678901234567890")));
    assertEquals("0", encode(BigDecimal.class, BigDecimal.ZERO));
    assertEquals("-42.75", encode(BigDecimal.class, new BigDecimal("-42.75")));
    assertEquals("1.2300", encode(BigDecimal.class, new BigDecimal("1.2300")));
    assertEquals(
        "12345678901234567890.12345678901234567890",
        encode(BigDecimal.class, new BigDecimal("12345678901234567890.12345678901234567890")));
    assertEquals("100000000000000000000", encode(BigDecimal.class, new BigDecimal("1E+20")));
    assertEquals("0.0000001", encode(BigDecimal.class, new BigDecimal("1E-7")));
  }

  @Test
  void encodesFloatingPointFiniteAndSpecialValues() {
    assertEquals("1.25", encode(Float.class, 1.25F));
    assertEquals("-9.5E100", encode(Double.class, -9.5E100));
    assertEquals("NaN", encode(Float.class, Float.NaN));
    assertEquals("Infinity", encode(Double.class, Double.POSITIVE_INFINITY));
    assertEquals("-Infinity", encode(Double.class, Double.NEGATIVE_INFINITY));
  }

  @Test
  void encodesTemporalValuesWithIsoFormatsAndExplicitOffsets() {
    assertEquals(
        "2026-08-18",
        encode(java.sql.Date.class, java.sql.Date.valueOf(LocalDate.of(2026, 8, 18))));
    assertEquals("2026-08-18", encode(LocalDate.class, LocalDate.of(2026, 8, 18)));
    assertEquals(
        "12:34:56.123456789", encode(LocalTime.class, LocalTime.of(12, 34, 56, 123456789)));
    assertEquals(
        "2026-08-18T12:34:56.123456789",
        encode(LocalDateTime.class, LocalDateTime.of(2026, 8, 18, 12, 34, 56, 123456789)));
    assertEquals(
        "2026-08-18T12:34:56.123456789+05:30",
        encode(
            OffsetDateTime.class,
            OffsetDateTime.of(
                2026, 8, 18, 12, 34, 56, 123456789, ZoneOffset.ofHoursMinutes(5, 30))));
    assertEquals(
        "12:34:56.123456789-04:00",
        encode(OffsetTime.class, OffsetTime.of(12, 34, 56, 123456789, ZoneOffset.ofHours(-4))));
    assertEquals(
        "2026-08-18T07:04:56.123456789Z",
        encode(Instant.class, Instant.parse("2026-08-18T07:04:56.123456789Z")));
  }

  @Test
  void ignoresDefaultLocaleAndTimezone() {
    Locale originalLocale = Locale.getDefault();
    TimeZone originalTimezone = TimeZone.getDefault();
    try {
      Locale.setDefault(Locale.FRANCE);
      TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));

      assertEquals("1234.50", encode(BigDecimal.class, new BigDecimal("1234.50")));
      assertEquals(
          "2026-08-18T07:04:56Z", encode(Instant.class, Instant.parse("2026-08-18T07:04:56Z")));
    } finally {
      Locale.setDefault(originalLocale);
      TimeZone.setDefault(originalTimezone);
    }
  }

  @Test
  void usesEnumNameInsteadOfOverriddenToString() {
    assertEquals("READY", encode(State.class, State.READY));
  }

  @Test
  void encodesByteArraysUsingPostgresqlHexInput() {
    assertEquals("\\x", encode(byte[].class, new byte[0]));
    assertEquals("\\x00017f80ff", encode(byte[].class, new byte[] {0, 1, 127, -128, -1}));
  }

  private String encode(Class<?> declaredType, Object value) {
    return registry.resolve(declaredType, "value_column").encode(value);
  }

  private enum State {
    READY;

    @Override
    public String toString() {
      return "localized-display-name";
    }
  }
}
