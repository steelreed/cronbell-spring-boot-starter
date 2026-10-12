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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugNamingTests {

	@Test
	void scheduledSlugIsBeanAndMethodInKebabCase() {
		assertThat(SlugNaming.forScheduled("reportJob", "sendDailyReport")).isEqualTo("report-job.send-daily-report");
		assertThat(SlugNaming.forScheduled("jobs", "run")).isEqualTo("jobs.run");
	}

	@Test
	void acronymsAndDigitsSplitSensibly() {
		assertThat(SlugNaming.kebab("HTTPServerJob")).isEqualTo("http-server-job");
		assertThat(SlugNaming.kebab("syncV2Orders")).isEqualTo("sync-v2-orders");
		assertThat(SlugNaming.kebab("already-kebab_case")).isEqualTo("already-kebab_case");
	}

	@Test
	void beanNamedAfterItsClassUsesTheSimpleName() {
		assertThat(SlugNaming.forScheduled("com.example.billing.InvoiceJobs", "close")).isEqualTo("invoice-jobs.close");
		assertThat(SlugNaming.forScheduled("com.example.Jobs#1", "run")).isEqualTo("jobs-1.run");
	}

	@Test
	void charactersTheServerRejectsBecomeDashes() {
		assertThat(SlugNaming.kebab("weird name$with spaces")).isEqualTo("weird-name-with-spaces");
		assertThat(SlugNaming.kebab("--x--")).isEqualTo("x");
		assertThat(SlugNaming.kebab("$$$")).isEqualTo("job");
		assertThat(SlugNaming.forQuartz("DEFAULT", "Nightly Report Trigger"))
			.isEqualTo("default.nightly-report-trigger");
	}

	@Test
	void environmentIsAPrefix() {
		assertThat(SlugNaming.withEnvironment("Prod", "jobs.run")).isEqualTo("prod.jobs.run");
		assertThat(SlugNaming.withEnvironment(" ", "jobs.run")).isEqualTo("jobs.run");
		assertThat(SlugNaming.withEnvironment(null, "jobs.run")).isEqualTo("jobs.run");
	}

	@Test
	void longSlugsAreCutWithAHashThatKeepsThemApart() {
		String a = SlugNaming.forScheduled("bean", "m".repeat(200) + "A");
		String b = SlugNaming.forScheduled("bean", "m".repeat(200) + "B");
		assertThat(a).hasSize(SlugNaming.MAX_LENGTH).isNotEqualTo(b);
		assertThat(SlugNaming.isValid(a)).isTrue();
	}

	@Test
	void validity() {
		assertThat(SlugNaming.isValid("a.b_c-1")).isTrue();
		assertThat(SlugNaming.isValid("")).isFalse();
		assertThat(SlugNaming.isValid("has space")).isFalse();
		assertThat(SlugNaming.isValid("x".repeat(129))).isFalse();
	}

}
