package io.github.postgresbulk.pgjdbc.copy;

import io.github.postgresbulk.core.metadata.BulkKeyMetadata;
import io.github.postgresbulk.core.metadata.ColumnMetadata;
import io.github.postgresbulk.core.metadata.EntityMetadata;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable row encoder with value encoders resolved once for each ordered column. */
final class PreparedCopyCsvRowEncoder<T> {

  private final List<PreparedColumn<T>> columns;

  private PreparedCopyCsvRowEncoder(List<PreparedColumn<T>> columns) {
    this.columns = List.copyOf(columns);
  }

  static <T> PreparedCopyCsvRowEncoder<T> prepare(EntityMetadata<T> metadata) {
    Objects.requireNonNull(metadata, "metadata must not be null");
    return prepare(metadata.insertColumns());
  }

  static <T> PreparedCopyCsvRowEncoder<T> prepare(BulkKeyMetadata<T> metadata) {
    Objects.requireNonNull(metadata, "metadata must not be null");
    return prepare(metadata.components());
  }

  private static <T> PreparedCopyCsvRowEncoder<T> prepare(List<ColumnMetadata<T>> columns) {
    ValueEncoderRegistry registry = ValueEncoderRegistry.defaults();
    List<PreparedColumn<T>> prepared = new ArrayList<>(columns.size());
    for (ColumnMetadata<T> column : columns) {
      prepared.add(
          new PreparedColumn<>(column, registry.resolve(column.javaType(), column.columnName())));
    }
    return new PreparedCopyCsvRowEncoder<>(prepared);
  }

  void writeRow(T source, Appendable destination) throws IOException {
    writeRow(source, destination, false, 0);
  }

  void writeRowRejectingNulls(T source, Appendable destination, long position) throws IOException {
    writeRow(source, destination, true, position);
  }

  private void writeRow(T source, Appendable destination, boolean rejectNulls, long position)
      throws IOException {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(destination, "destination must not be null");

    for (int index = 0; index < columns.size(); index++) {
      if (index > 0) {
        destination.append(',');
      }
      PreparedColumn<T> column = columns.get(index);
      EncodedValue encoded = column.encode(source);
      if (rejectNulls && encoded.isNull()) {
        throw new IllegalArgumentException(
            "keys must not contain null components; null found at position "
                + position
                + " for column '"
                + column.columnName()
                + "'");
      }
      CopyCsvFieldWriter.write(encoded, destination);
    }
    destination.append('\n');
  }

  private static final class PreparedColumn<T> {

    private final ColumnMetadata<T> metadata;
    private final ValueEncoderRegistry.ValueEncoder encoder;

    private PreparedColumn(ColumnMetadata<T> metadata, ValueEncoderRegistry.ValueEncoder encoder) {
      this.metadata = metadata;
      this.encoder = encoder;
    }

    private EncodedValue encode(T source) {
      Object value = metadata.read(source);
      if (value == null) {
        return EncodedValue.nullValue();
      }
      if (!metadata.javaType().isInstance(value)) {
        throw new BulkEncodingException(
            "Value for column '"
                + metadata.columnName()
                + "' has runtime Java type "
                + value.getClass().getName()
                + ", incompatible with declared type "
                + metadata.javaType().getName());
      }
      return EncodedValue.text(encoder.encode(value));
    }

    private String columnName() {
      return metadata.columnName();
    }
  }
}
