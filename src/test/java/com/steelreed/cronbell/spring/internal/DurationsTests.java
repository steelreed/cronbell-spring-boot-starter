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

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DurationsTests {

	@Test
	void simpleIsoAndMillis() {
		assertThat(Durations.parse("30s")).isEqualTo(Duration.ofSeconds(30));
		assertThat(Durations.parse("5m")).isEqualTo(Duration.ofMinutes(5));
		assertThat(Durations.parse("2h")).isEqualTo(Duration.ofHours(2));
		assertThat(Durations.parse("1d")).isEqualTo(Duration.ofDays(1));
		assertThat(Durations.parse("PT10M")).isEqualTo(Duration.ofMinutes(10));
		assertThat(Durations.parse("pt1h")).isEqualTo(Duration.ofHours(1));
		assertThat(Durations.parse("1500")).isEqualTo(Duration.ofMillis(1500));
		assertThat(Durations.parse(" ")).isNull();
		assertThat(Durations.parse(null)).isNull();
	}

	@Test
	void rejectsGarbage() {
		assertThatIllegalArgumentException().isThrownBy(() -> Durations.parse("soon"));
		assertThatIllegalArgumentException().isThrownBy(() -> Durations.parse("PTX"));
	}

	@Test
	void secondsRoundUp() {
		assertThat(Durations.toSecondsCeil(Duration.ofMillis(1500))).isEqualTo(2);
		assertThat(Durations.toSecondsCeil(Duration.ofSeconds(90))).isEqualTo(90);
		assertThat(Durations.toSecondsCeil(null)).isNull();
	}

}
