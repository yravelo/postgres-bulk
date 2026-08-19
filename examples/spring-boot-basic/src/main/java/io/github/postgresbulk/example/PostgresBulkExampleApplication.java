package io.github.postgresbulk.example;

import io.github.postgresbulk.core.BulkWriteResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class PostgresBulkExampleApplication {

  public static void main(String[] args) {
    SpringApplication.run(PostgresBulkExampleApplication.class, args);
  }

  @Bean
  @ConditionalOnProperty(name = "example.demo.enabled", matchIfMissing = true)
  CommandLineRunner demo(ProductImportService service, MeterRegistry meters) {
    return args -> {
      List<Product> input =
          List.of(
              product("SKU-001", "Mechanical keyboard", "129.90"),
              product("SKU-002", "Ergonomic mouse", "79.90"));
      BulkWriteResult result = service.importProducts(input);
      List<Product> found = service.findBySkus(List.of("SKU-002", "SKU-missing", "SKU-001"));
      double observedRows =
          meters.find("postgres.bulk.rows").tag("operation", "insert").counter().count();
      System.out.printf(
          "Inserted %d rows in %d COPY batch(es); found %d products; observed rows %.0f%n",
          result.affectedRows(), result.batches(), found.size(), observedRows);
    };
  }

  private static Product product(String sku, String name, String price) {
    return new Product(UUID.randomUUID(), sku, name, new BigDecimal(price), Instant.now());
  }
}
