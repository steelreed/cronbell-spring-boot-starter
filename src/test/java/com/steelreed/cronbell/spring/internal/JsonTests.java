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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class JsonTests {

	@Test
	void writesObjectsWithoutNulls() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("name", "Say \"hi\"\n");
		body.put("cron", null);
		body.put("periodSeconds", 60L);
		assertThat(Json.write(body)).isEqualTo("{\"name\":\"Say \\\"hi\\\"\\n\",\"periodSeconds\":60}");
	}

	@Test
	void parsesServerAnswers() {
		Object parsed = Json.parse(
				"{\"slug\":\"a\",\"pingUrl\":\"http://x/ping/k\",\"periodSeconds\":60,\"grace\":1.5,\"paused\":false,"
						+ "\"cron\":null,\"tags\":[\"x\",{\"y\":true}],\"note\":\"caf\\u00e9\"}");
		assertThat(parsed).isInstanceOf(Map.class);
		Map<?, ?> map = (Map<?, ?>) parsed;
		assertThat(map.get("pingUrl")).isEqualTo("http://x/ping/k");
		assertThat(map.get("periodSeconds")).isEqualTo(60L);
		assertThat(map.get("grace")).isEqualTo(1.5);
		assertThat(map.get("paused")).isEqualTo(false);
		assertThat(map.containsKey("cron")).isTrue();
		assertThat(map.get("tags")).isEqualTo(List.of("x", Map.of("y", true)));
		assertThat(map.get("note")).isEqualTo("café");
	}

	@Test
	void rejectsBrokenJson() {
		assertThatIllegalArgumentException().isThrownBy(() -> Json.parse("{\"a\":"));
		assertThatIllegalArgumentException().isThrownBy(() -> Json.parse("<html>"));
		assertThatIllegalArgumentException().isThrownBy(() -> Json.parse("{} x"));
	}

}
