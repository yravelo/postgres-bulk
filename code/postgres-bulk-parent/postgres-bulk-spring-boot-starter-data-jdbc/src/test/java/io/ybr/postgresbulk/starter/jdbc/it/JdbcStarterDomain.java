package io.ybr.postgresbulk.starter.jdbc.it;

import io.ybr.postgresbulk.springdata.jdbc.repository.PostgresBulkJdbcRepository;
import java.math.BigDecimal;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.annotation.Id;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.relational.core.mapping.Embedded;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.CrudRepository;

interface ProductRepository
    extends CrudRepository<Product, Long>, PostgresBulkJdbcRepository<Product> {}

interface GeneratedProductRepository
    extends CrudRepository<GeneratedProduct, Long>, PostgresBulkJdbcRepository<GeneratedProduct> {}

@Table("j6_products")
record Product(
    @Id Long id,
    String sku,
    String category,
    Money amount,
    @Embedded.Nullable(prefix = "address_") Address address) {}

@Table("j6_generated_products")
record GeneratedProduct(@Id Long id, String code) {}

record Address(String city, String postalCode) {}

record ProductKey(String sku, String category) {}

record Money(BigDecimal value) {
  Money(String value) {
    this(new BigDecimal(value));
  }
}

@WritingConverter
enum MoneyWritingConverter implements Converter<Money, BigDecimal> {
  INSTANCE;

  @Override
  public BigDecimal convert(Money source) {
    return source.value();
  }
}

@ReadingConverter
enum MoneyReadingConverter implements Converter<BigDecimal, Money> {
  INSTANCE;

  @Override
  public Money convert(BigDecimal source) {
    return new Money(source);
  }
}
