package io.github.postgresbulk.pgjdbc.copy;

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
    ValueEncoderRegistry registry = ValueEncoderRegistry.defaults();
    List<PreparedColumn<T>> prepared = new ArrayList<>(metadata.insertColumns().size());
    for (ColumnMetadata<T> column : metadata.insertColumns()) {
      prepared.add(
          new PreparedColumn<>(column, registry.resolve(column.javaType(), column.columnName())));
    }
    return new PreparedCopyCsvRowEncoder<>(prepared);
  }

  void writeRow(T source, Appendable destination) throws IOException {
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(destination, "destination must not be null");

    for (int index = 0; index < columns.size(); index++) {
      if (index > 0) {
        destination.append(',');
      }
      CopyCsvFieldWriter.write(columns.get(index).encode(source), destination);
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
  }
}
