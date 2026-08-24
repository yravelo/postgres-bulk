package io.ybr.postgresbulk.example;

import io.ybr.postgresbulk.core.BulkInsertOptions;
import io.ybr.postgresbulk.core.BulkWriteResult;
import io.ybr.postgresbulk.core.metadata.BulkKeyMetadata;
import io.ybr.postgresbulk.core.metadata.ColumnMetadata;
import io.ybr.postgresbulk.core.metadata.TableName;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductImportService {

  private static final Map<String, String> CUSTOMER_SCHEMAS =
      Map.of(
          "customer-a", "example_jpa_a",
          "customer-b", "example_jpa_b",
          "quoted-customer", "Example JPA Quoted");

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

  @Transactional
  public BulkWriteResult importProductsForCustomer(String customer, List<Product> input) {
    return products.bulkInsert(targetForCustomer(customer), input);
  }

  @Transactional
  public List<Product> findBySkusForCustomer(String customer, List<String> skus) {
    return products.findAllByBulkKey(skus, SKU_KEY, targetForCustomer(customer));
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

  @Transactional
  public void importForCustomerThenRollback(String customer, List<Product> input) {
    products.bulkInsert(targetForCustomer(customer), input);
    throw new IntentionalRollbackException("Demonstrating target-aware transaction rollback");
  }

  private static TableName targetForCustomer(String customer) {
    String schema = CUSTOMER_SCHEMAS.get(customer);
    if (schema == null) {
      throw new IllegalArgumentException("Unknown or unauthorized customer");
    }
    return TableName.of(schema, "product");
  }

  public static final class IntentionalRollbackException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private IntentionalRollbackException(String message) {
      super(message);
    }
  }
}
