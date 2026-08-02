package com.financedash.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS policy for {@code /api/**}.
 *
 * <p><b>This is load-bearing security, not boilerplate.</b> The API has no authentication of any
 * kind, so the origin allowlist is the only thing stopping a malicious page from issuing writes
 * against a visitor's data. Do not widen it to {@code "*"}.
 *
 * <p><b>Why the deployed origin must be listed even though everything is same-origin.</b> Browsers
 * send an {@code Origin} header on <em>every</em> non-GET request, including same-origin ones, and
 * since Spring 5.0 {@code CorsUtils.isCorsRequest()} is simply "is an Origin header present" — it no
 * longer exempts same-origin requests. So any POST/PUT/DELETE from the deployed site is CORS-checked
 * against this list, and an origin that isn't on it gets a bare {@code 403 Invalid CORS request}
 * from Spring before the controller ever runs. GETs are unaffected (no Origin header), which is why
 * a misconfiguration looks like "the app loads fine but nothing can be saved".
 *
 * <p>This is invisible locally: Docker, the test stack and the Vite dev server are all served from
 * {@code localhost}, which the default pattern already matches. It only bites on a real domain, so
 * {@code CORS_ALLOWED_ORIGIN_PATTERNS} must include the public origin of every deployment. The
 * resolved list is logged at startup to make a misconfiguration obvious.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    private final List<String> allowedOriginPatterns;

    public WebConfig(@Value("${app.cors.allowed-origin-patterns}") List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        log.info("CORS allowed origin patterns for /api/**: {}", allowedOriginPatterns);
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
