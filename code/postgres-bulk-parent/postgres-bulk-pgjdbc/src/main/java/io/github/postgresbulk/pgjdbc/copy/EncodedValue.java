package io.github.postgresbulk.pgjdbc.copy;

import java.util.Objects;

/** Explicitly distinguishes a SQL NULL from non-null logical text, including empty text. */
final class EncodedValue {

  private static final EncodedValue NULL = new EncodedValue(true, null);

  private final boolean nullValue;
  private final String text;

  private EncodedValue(boolean nullValue, String text) {
    this.nullValue = nullValue;
    this.text = text;
  }

  static EncodedValue nullValue() {
    return NULL;
  }

  static EncodedValue text(String text) {
    return new EncodedValue(false, Objects.requireNonNull(text, "text must not be null"));
  }

  boolean isNull() {
    return nullValue;
  }

  String text() {
    if (nullValue) {
      throw new IllegalStateException("NULL has no logical text");
    }
    return text;
  }
}
