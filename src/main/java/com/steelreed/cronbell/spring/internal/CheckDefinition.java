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
import java.util.Map;

/**
 * A check as sent to {@code PUT /api/v1/checks/{slug}}: exactly one of {@code cron} and
 * {@code periodSeconds}.
 *
 * @param slug the slug, environment prefix included
 * @param name the display name
 * @param cron the cron expression, or {@code null}
 * @param timeZone the IANA time zone of the cron expression, or {@code null}
 * @param periodSeconds the period, or {@code null}
 * @param graceSeconds the grace time, or {@code null} for the server default
 * @param maxRuntimeSeconds the longest run, or {@code null} for no limit
 */
public record CheckDefinition(String slug, String name, String cron, String timeZone, Long periodSeconds,
		Long graceSeconds, Long maxRuntimeSeconds) {

	/** Shortest period the server accepts. */
	public static final long MIN_PERIOD_SECONDS = 60;

	/**
	 * The request body.
	 * @return the JSON object
	 */
	public String toJson() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("name", this.name);
		body.put("cron", this.cron);
		body.put("timeZone", this.timeZone);
		body.put("periodSeconds", this.periodSeconds);
		body.put("graceSeconds", this.graceSeconds);
		body.put("maxRuntimeSeconds", this.maxRuntimeSeconds);
		return Json.write(body);
	}

	/**
	 * A short description of the schedule for logs.
	 * @return for example {@code cron "0 0 * * * *" Asia/Ho_Chi_Minh} or
	 * {@code every 300s}
	 */
	public String describeSchedule() {
		if (this.cron != null) {
			return "cron \"" + this.cron + "\" " + this.timeZone;
		}
		return "every " + this.periodSeconds + "s";
	}

}
