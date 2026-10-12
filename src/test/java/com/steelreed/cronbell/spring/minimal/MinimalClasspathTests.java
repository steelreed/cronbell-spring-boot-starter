/*
 * Copyright 2026 the Cronbell authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.steelreed.cronbell.spring.minimal;

import java.time.Duration;

import com.steelreed.cronbell.spring.CronbellCheck;
import com.steelreed.cronbell.spring.test.Await;
import com.steelreed.cronbell.spring.test.FakeCronbellServer;
import com.steelreed.cronbell.spring.test.TestApps;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Runs in its own Surefire execution whose classpath has no Actuator, Quartz or ShedLock,
 * like a plain {@code spring-boot-starter} application. A starter that loads one of those
 * types eagerly would stop such an application from starting.
 */
class MinimalClasspathTests {

	@Test
	void optionalLibrariesAreReallyAbsent() {
		for (String type : new String[] { "org.springframework.boot.actuate.info.InfoContributor",
				"org.quartz.Scheduler", "net.javacrumbs.shedlock.core.LockProvider" }) {
			assertThatExceptionOfType(ClassNotFoundException.class).isThrownBy(() -> Class.forName(type));
		}
	}

	@Test
	void plainApplicationStartsAndReportsRuns() throws Exception {
		try (FakeCronbellServer server = new FakeCronbellServer();
				ConfigurableApplicationContext context = TestApps.start(App.class, "cronbell.url=" + server.url())) {
			Await.until(Duration.ofSeconds(10), () -> !server.pings("minimal.tick", "").isEmpty(),
					"a success ping from a plain Spring Boot application");
			assertThat(context.isRunning()).isTrue();
		}
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAutoConfiguration
	@EnableScheduling
	@Import(Minimal.class)
	static class App {

	}

	static class Minimal {

		@Scheduled(fixedRate = 200)
		@CronbellCheck("minimal.tick")
		void tick() {
		}

	}

}
