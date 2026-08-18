package io.github.postgresbulk.pgjdbc.encoding;

import java.io.IOException;
import java.util.Objects;

/** Applies the exact field-level framing rules selected for PostgreSQL COPY CSV. */
final class CopyCsvFieldWriter {

  static final String NULL_MARKER = "\\N";

  private static final String LEGACY_END_OF_DATA_MARKER = "\\.";
  private static final char DELIMITER = ',';
  private static final char QUOTE = '"';

  private CopyCsvFieldWriter() {}

  static void write(EncodedValue value, Appendable destination) throws IOException {
    Objects.requireNonNull(value, "value must not be null");
    Objects.requireNonNull(destination, "destination must not be null");

    if (value.isNull()) {
      destination.append(NULL_MARKER);
      return;
    }

    String text = value.text();
    if (!requiresQuotes(text)) {
      destination.append(text);
      return;
    }

    destination.append(QUOTE);
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      if (character == QUOTE) {
        destination.append(QUOTE);
      }
      destination.append(character);
    }
    destination.append(QUOTE);
  }

  private static boolean requiresQuotes(String text) {
    if (text.isEmpty() || text.contains(NULL_MARKER) || text.equals(LEGACY_END_OF_DATA_MARKER)) {
      return true;
    }
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      if (character == DELIMITER || character == QUOTE || character == '\r' || character == '\n') {
        return true;
      }
    }
    return false;
  }
}
