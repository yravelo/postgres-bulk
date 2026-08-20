package io.ybr.postgresbulk.benchmarks;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jdbc.JdbcRepositoriesAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(exclude = JdbcRepositoriesAutoConfiguration.class)
@ComponentScan(
    basePackageClasses = BenchmarkApplication.class,
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "io\\.ybr\\.postgresbulk\\.benchmarks\\.jdbc\\..*"))
@EnableJpaRepositories(
    basePackageClasses = BenchmarkRepository.class,
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "io\\.ybr\\.postgresbulk\\.benchmarks\\.jdbc\\..*"))
class BenchmarkApplication {}
