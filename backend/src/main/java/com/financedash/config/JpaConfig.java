package com.financedash.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA auditing is kept in its own config (rather than on the application class) so that
 * web-slice tests ({@code @WebMvcTest}), which don't load JPA infrastructure, aren't
 * forced to wire the auditing handler. The full context picks this up via component
 * scanning; {@code @DataJpaTest} slices import it explicitly.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
