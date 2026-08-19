package io.github.postgresbulk.pgjdbc.copy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable registry for Java-value to logical-text conversion. */
final class ValueEncoderRegistry {

  private static final char[] HEX = "0123456789abcdef".toCharArray();
  private static final ValueEncoder ENUM_ENCODER = value -> ((Enum<?>) value).name();
  private static final ValueEncoderRegistry DEFAULTS =
      new ValueEncoderRegistry(
          Map.ofEntries(
              Map.entry(String.class, value -> (String) value),
              Map.entry(Character.class, value -> Character.toString((Character) value)),
              Map.entry(Byte.class, value -> Byte.toString((Byte) value)),
              Map.entry(Short.class, value -> Short.toString((Short) value)),
              Map.entry(Integer.class, value -> Integer.toString((Integer) value)),
              Map.entry(Long.class, value -> Long.toString((Long) value)),
              Map.entry(Float.class, value -> Float.toString((Float) value)),
              Map.entry(Double.class, value -> Double.toString((Double) value)),
              Map.entry(BigDecimal.class, value -> ((BigDecimal) value).toPlainString()),
              Map.entry(BigInteger.class, value -> ((BigInteger) value).toString()),
              Map.entry(Boolean.class, value -> (Boolean) value ? "true" : "false"),
              Map.entry(UUID.class, value -> ((UUID) value).toString()),
              Map.entry(
                  java.sql.Date.class,
                  value ->
                      DateTimeFormatter.ISO_LOCAL_DATE.format(
                          ((java.sql.Date) value).toLocalDate())),
              Map.entry(
                  LocalDate.class,
                  value -> DateTimeFormatter.ISO_LOCAL_DATE.format((LocalDate) value)),
              Map.entry(
                  LocalTime.class,
                  value -> DateTimeFormatter.ISO_LOCAL_TIME.format((LocalTime) value)),
              Map.entry(
                  LocalDateTime.class,
                  value -> DateTimeFormatter.ISO_LOCAL_DATE_TIME.format((LocalDateTime) value)),
              Map.entry(
                  OffsetDateTime.class,
                  value -> DateTimeFormatter.ISO_OFFSET_DATE_TIME.format((OffsetDateTime) value)),
              Map.entry(
                  OffsetTime.class,
                  value -> DateTimeFormatter.ISO_OFFSET_TIME.format((OffsetTime) value)),
              Map.entry(
                  Instant.class, value -> DateTimeFormatter.ISO_INSTANT.format((Instant) value)),
              Map.entry(byte[].class, ValueEncoderRegistry::encodeBytes)));

  private final Map<Class<?>, ValueEncoder> encoders;

  private ValueEncoderRegistry(Map<Class<?>, ValueEncoder> encoders) {
    this.encoders = Map.copyOf(encoders);
  }

  static ValueEncoderRegistry defaults() {
    return DEFAULTS;
  }

  ValueEncoder resolve(Class<?> declaredType, String columnName) {
    Objects.requireNonNull(declaredType, "declaredType must not be null");
    Objects.requireNonNull(columnName, "columnName must not be null");

    ValueEncoder encoder = encoders.get(declaredType);
    if (encoder != null) {
      return encoder;
    }
    if (declaredType.isEnum()) {
      return ENUM_ENCODER;
    }
    throw new BulkEncodingException(
        "No COPY CSV encoder for column '"
            + columnName
            + "' with declared Java type "
            + declaredType.getName());
  }

  private static String encodeBytes(Object value) {
    byte[] bytes = (byte[]) value;
    char[] encoded = new char[2 + bytes.length * 2];
    encoded[0] = '\\';
    encoded[1] = 'x';
    for (int index = 0; index < bytes.length; index++) {
      int unsigned = bytes[index] & 0xff;
      encoded[2 + index * 2] = HEX[unsigned >>> 4];
      encoded[3 + index * 2] = HEX[unsigned & 0x0f];
    }
    return new String(encoded);
  }

  @FunctionalInterface
  interface ValueEncoder {

    String encode(Object value);
  }
}
