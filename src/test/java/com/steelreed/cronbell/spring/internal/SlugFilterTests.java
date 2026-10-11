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

package com.steelreed.cronbell.spring.internal;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugFilterTests {

	@Test
	void everythingWhenNothingIsConfigured() {
		SlugFilter filter = new SlugFilter(List.of(), List.of());
		assertThat(filter.accepts("any.thing")).isTrue();
	}

	@Test
	void includeLimitsToMatchingSlugs() {
		SlugFilter filter = new SlugFilter(List.of("billing.*", "report-job.send-?"), List.of());
		assertThat(filter.accepts("billing.close-month")).isTrue();
		assertThat(filter.accepts("report-job.send-1")).isTrue();
		assertThat(filter.accepts("report-job.send-10")).isFalse();
		assertThat(filter.accepts("cleanup.run")).isFalse();
	}

	@Test
	void excludeWinsOverInclude() {
		SlugFilter filter = new SlugFilter(List.of("*"), List.of("*.health-ping", "tmp*"));
		assertThat(filter.accepts("jobs.health-ping")).isFalse();
		assertThat(filter.accepts("tmp-cleaner.run")).isFalse();
		assertThat(filter.accepts("jobs.report")).isTrue();
	}

	@Test
	void dotsAndOtherRegexCharactersAreLiteral() {
		SlugFilter filter = new SlugFilter(List.of("a.b"), List.of());
		assertThat(filter.accepts("a.b")).isTrue();
		assertThat(filter.accepts("axb")).isFalse();
	}

	@Test
	void matchingIgnoresCaseAndBlankPatterns() {
		SlugFilter filter = new SlugFilter(List.of("Billing.*", " "), List.of(""));
		assertThat(filter.accepts("billing.x")).isTrue();
	}

}
