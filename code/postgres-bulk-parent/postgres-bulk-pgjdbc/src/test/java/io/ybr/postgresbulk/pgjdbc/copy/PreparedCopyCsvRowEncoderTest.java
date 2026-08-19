package io.ybr.postgresbulk.pgjdbc.copy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.EntityMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class PreparedCopyCsvRowEncoderTest {

  @Test
  void writesColumnsInMetadataOrderAndOneLfTerminatorPerRow() throws IOException {
    EntityMetadata<Product> metadata =
        EntityMetadata.of(
            Product.class,
            TableName.of("inventory", "product"),
            List.of(
                ColumnMetadata.of("sku", String.class, Product::sku),
                ColumnMetadata.of("quantity", Integer.class, Product::quantity),
                ColumnMetadata.of("description", String.class, Product::description)));
    PreparedCopyCsvRowEncoder<Product> encoder = PreparedCopyCsvRowEncoder.prepare(metadata);
    StringBuilder destination = new StringBuilder();

    encoder.writeRow(new Product("SKU,1", 7, null), destination);
    encoder.writeRow(new Product("SKU-2", 0, ""), destination);

    assertEquals("\"SKU,1\",7,\\N\nSKU-2,0,\"\"\n", destination.toString());
  }

  @Test
  void resolvesUsingDeclaredTypeBeforeAnyRowsAreRead() {
    AtomicInteger reads = new AtomicInteger();
    EntityMetadata<ValueHolder> metadata =
        EntityMetadata.of(
            ValueHolder.class,
            TableName.of("unsupported_values"),
            List.of(
                ColumnMetadata.of(
                    "payload",
                    CharSequence.class,
                    value -> {
                      reads.incrementAndGet();
                      return value.payload();
                    })));

    BulkEncodingException exception =
        assertThrows(
            BulkEncodingException.class, () -> PreparedCopyCsvRowEncoder.prepare(metadata));

    assertEquals(0, reads.get());
    assertTrue(exception.getMessage().contains("payload"));
    assertTrue(exception.getMessage().contains("java.lang.CharSequence"));
    assertFalse(exception.getMessage().contains("runtime text"));
  }

  @Test
  void propagatesAccessorFailuresUnchanged() {
    IllegalStateException failure = new IllegalStateException("mapping unavailable");
    EntityMetadata<Product> metadata =
        EntityMetadata.of(
            Product.class,
            TableName.of("product"),
            List.of(
                ColumnMetadata.of(
                    "sku",
                    String.class,
                    product -> {
                      throw failure;
                    })));
    PreparedCopyCsvRowEncoder<Product> encoder = PreparedCopyCsvRowEncoder.prepare(metadata);

    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () -> encoder.writeRow(new Product("secret-value", 1, null), new StringBuilder()));

    assertSame(failure, thrown);
  }

  @Test
  void reportsRuntimeTypeMismatchWithoutIncludingValue() {
    EntityMetadata<ValueHolder> metadata = mismatchedMetadata();
    PreparedCopyCsvRowEncoder<ValueHolder> encoder = PreparedCopyCsvRowEncoder.prepare(metadata);

    BulkEncodingException exception =
        assertThrows(
            BulkEncodingException.class,
            () -> encoder.writeRow(new ValueHolder("sensitive-text"), new StringBuilder()));

    assertTrue(exception.getMessage().contains("payload"));
    assertTrue(exception.getMessage().contains("java.lang.String"));
    assertTrue(exception.getMessage().contains("java.lang.Integer"));
    assertFalse(exception.getMessage().contains("sensitive-text"));
  }

  @Test
  void propagatesDestinationIoFailureAndDoesNotCloseOwnedWriter() throws IOException {
    EntityMetadata<Product> metadata =
        EntityMetadata.of(
            Product.class,
            TableName.of("product"),
            List.of(ColumnMetadata.of("sku", String.class, Product::sku)));
    PreparedCopyCsvRowEncoder<Product> encoder = PreparedCopyCsvRowEncoder.prepare(metadata);
    TrackingWriter writer = new TrackingWriter();

    encoder.writeRow(new Product("SKU-1", 1, null), writer);

    assertEquals("SKU-1\n", writer.toString());
    assertFalse(writer.closed);

    IOException failure = new IOException("sink unavailable");
    IOException thrown =
        assertThrows(
            IOException.class,
            () -> encoder.writeRow(new Product("SKU-2", 1, null), new FailingAppendable(failure)));
    assertSame(failure, thrown);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static EntityMetadata<ValueHolder> mismatchedMetadata() {
    Function<ValueHolder, Object> mismatchedAccessor = ValueHolder::payload;
    ColumnMetadata<ValueHolder> rawColumn =
        (ColumnMetadata<ValueHolder>)
            (ColumnMetadata<?>)
                ColumnMetadata.of("payload", (Class) Integer.class, (Function) mismatchedAccessor);
    return EntityMetadata.of(
        ValueHolder.class, TableName.of("mismatched_values"), List.of(rawColumn));
  }

  private record Product(String sku, int quantity, String description) {}

  private record ValueHolder(String payload) {}

  private static final class TrackingWriter extends StringWriter {

    private boolean closed;

    @Override
    public void close() {
      closed = true;
    }
  }

  private static final class FailingAppendable implements Appendable {

    private final IOException failure;

    private FailingAppendable(IOException failure) {
      this.failure = failure;
    }

    @Override
    public Appendable append(CharSequence value) throws IOException {
      throw failure;
    }

    @Override
    public Appendable append(CharSequence value, int start, int end) throws IOException {
      throw failure;
    }

    @Override
    public Appendable append(char value) throws IOException {
      throw failure;
    }
  }
}
