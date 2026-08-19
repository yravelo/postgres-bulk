package io.ybr.postgresbulk.example;

import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductImportService {

  private static final BulkKeyMetadata<String> SKU_KEY =
      BulkKeyMetadata.of(String.class, List.of(ColumnMetadata.of("sku", String.class, sku -> sku)));

  private static final BulkKeyMetadata<ProductLookupKey> SKU_AND_NAME_KEY =
      BulkKeyMetadata.of(
          ProductLookupKey.class,
          List.of(
              ColumnMetadata.of("sku", String.class, ProductLookupKey::sku),
              ColumnMetadata.of("name", String.class, ProductLookupKey::name)));

  private final ProductRepository products;

  public ProductImportService(ProductRepository products) {
    this.products = products;
  }

  @Transactional
  public BulkWriteResult importProducts(List<Product> input) {
    return products.bulkInsert(input);
  }

  @Transactional
  public BulkWriteResult importProducts(List<Product> input, int batchSize) {
    return products.bulkInsert(input, BulkInsertOptions.ofBatchSize(batchSize));
  }

  @Transactional
  public List<Product> findBySkus(List<String> skus) {
    return products.findAllByBulkKey(skus, SKU_KEY);
  }

  @Transactional
  public List<Product> findBySkuAndName(List<ProductLookupKey> keys) {
    return products.findAllByBulkKey(keys, SKU_AND_NAME_KEY);
  }

  @Transactional(readOnly = true)
  public List<Product> findBySkusInReadOnlyTransaction(List<String> skus) {
    return products.findAllByBulkKey(skus, SKU_KEY);
  }

  @Transactional
  public void importThenRollback(List<Product> input) {
    products.bulkInsert(input);
    throw new IntentionalRollbackException("Demonstrating transaction rollback");
  }

  public static final class IntentionalRollbackException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private IntentionalRollbackException(String message) {
      super(message);
    }
  }
}
