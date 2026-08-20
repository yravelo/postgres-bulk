package io.ybr.postgresbulk.example.jdbc;

import io.ybr.postgresbulk.core.BulkWriteResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class PostgresBulkJdbcExampleApplication {

  public static void main(String[] args) {
    SpringApplication.run(PostgresBulkJdbcExampleApplication.class, args);
  }

  @Bean
  @ConditionalOnProperty(name = "example.demo.enabled", matchIfMissing = true)
  CommandLineRunner demo(ProductImportService service) {
    return args -> {
      String suffix = UUID.randomUUID().toString().substring(0, 8);
      Product keyboard = product("KEYBOARD-" + suffix, "peripherals", "Mechanical keyboard");
      Product mouse = product("MOUSE-" + suffix, "peripherals", "Ergonomic mouse");
      Product display = product("DISPLAY-" + suffix, "displays", "4K display");

      BulkWriteResult defaults = service.importProducts(List.of(keyboard));
      BulkWriteResult explicit = service.importProducts(List.of(mouse, display), 1);
      int simpleMatches = service.findBySkus(List.of(keyboard.sku(), mouse.sku())).size();
      int compositeMatches =
          service
              .findBySkuAndCategory(
                  List.of(new ProductLookupKey(display.sku(), display.category())))
              .size();

      System.out.printf(
          "default=%s explicit=%s simpleMatches=%d compositeMatches=%d%n",
          defaults, explicit, simpleMatches, compositeMatches);
    };
  }

  private static Product product(String sku, String category, String name) {
    return new Product(
        UUID.randomUUID(), sku, category, name, new BigDecimal("49.90"), new Address("Madrid"));
  }
}
