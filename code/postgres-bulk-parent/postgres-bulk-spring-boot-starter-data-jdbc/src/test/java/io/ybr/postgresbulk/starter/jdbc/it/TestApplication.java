package io.ybr.postgresbulk.starter.jdbc.it;

import java.util.List;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;

@SpringBootApplication
class TestApplication {

  @Bean
  JdbcCustomConversions jdbcCustomConversions() {
    return new JdbcCustomConversions(
        List.of(MoneyWritingConverter.INSTANCE, MoneyReadingConverter.INSTANCE));
  }
}
