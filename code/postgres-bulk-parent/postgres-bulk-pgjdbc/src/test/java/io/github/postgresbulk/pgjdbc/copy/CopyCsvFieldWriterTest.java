package io.github.postgresbulk.pgjdbc.copy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CopyCsvFieldWriterTest {

  @Test
  void distinguishesNullEmptyAndLiteralNullMarker() throws IOException {
    assertEquals("\\N", write(EncodedValue.nullValue()));
    assertEquals("\"\"", write(EncodedValue.text("")));
    assertEquals("\"\\N\"", write(EncodedValue.text("\\N")));
  }

  @ParameterizedTest
  @MethodSource("csvCases")
  void appliesPostgresqlCsvFraming(String logicalText, String expected) throws IOException {
    assertEquals(expected, write(EncodedValue.text(logicalText)));
  }

  @Test
  void rejectsInvalidArguments() {
    assertThrows(
        NullPointerException.class, () -> CopyCsvFieldWriter.write(null, new StringBuilder()));
    assertThrows(
        NullPointerException.class,
        () -> CopyCsvFieldWriter.write(EncodedValue.text("value"), null));
  }

  private static Stream<Arguments> csvCases() {
    return Stream.of(
        Arguments.of("plain", "plain"),
        Arguments.of("with,comma", "\"with,comma\""),
        Arguments.of("with\"quote", "\"with\"\"quote\""),
        Arguments.of("line\nfeed", "\"line\nfeed\""),
        Arguments.of("carriage\rreturn", "\"carriage\rreturn\""),
        Arguments.of("windows\r\nline", "\"windows\r\nline\""),
        Arguments.of(" leading", " leading"),
        Arguments.of("trailing ", "trailing "),
        Arguments.of("café 😀", "café 😀"),
        Arguments.of("path\\segment", "path\\segment"),
        Arguments.of("prefix\\Nsuffix", "\"prefix\\Nsuffix\""),
        Arguments.of("\\.", "\"\\.\""));
  }

  private static String write(EncodedValue value) throws IOException {
    StringBuilder destination = new StringBuilder();
    CopyCsvFieldWriter.write(value, destination);
    return destination.toString();
  }
}
