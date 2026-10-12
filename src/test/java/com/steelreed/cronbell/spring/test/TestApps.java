package com.steelreed.cronbell.spring.test;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Starts small Spring Boot applications for the integration tests. */
public final class TestApps {

	/**
	 * Quartz auto-configuration on Boot 3 and Boot 4; excluded unless a test wants it.
	 */
	public static final String NO_QUARTZ = "spring.autoconfigure.exclude="
			+ "org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration,"
			+ "org.springframework.boot.quartz.autoconfigure.QuartzAutoConfiguration";

	private TestApps() {
	}

	public static ConfigurableApplicationContext start(Class<?> app, String... properties) {
		List<String> all = new ArrayList<>(List.of("spring.main.web-application-type=none",
				"spring.main.banner-mode=off", "cronbell.api-key=cb_test_key"));
		all.addAll(List.of(properties));
		return new SpringApplicationBuilder(app).properties(all.toArray(String[]::new)).run();
	}

}
