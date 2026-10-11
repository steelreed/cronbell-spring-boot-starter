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
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses durations written in annotations: {@code 30s}, {@code 5m}, {@code 2h},
 * {@code 1d}, {@code 500ms}, ISO-8601 ({@code PT5M}) or a plain number of milliseconds,
 * the formats Spring Boot and ShedLock accept.
 */
public final class Durations {

	private static final Pattern SIMPLE = Pattern.compile("(-?\\d+)\\s*(ns|us|ms|s|m|h|d)?");

	private Durations() {
	}

	/**
	 * Parses a duration.
	 * @param text the text, may be {@code null} or blank
	 * @return the duration, or {@code null} for blank text
	 * @throws IllegalArgumentException if the text is not a duration
	 */
	public static Duration parse(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String value = text.trim();
		String upper = value.toUpperCase(Locale.ROOT);
		if (upper.startsWith("P") || upper.startsWith("-P")) {
			try {
				return Duration.parse(upper);
			}
			catch (DateTimeParseException ex) {
				throw new IllegalArgumentException("not a duration: " + text, ex);
			}
		}
		Matcher matcher = SIMPLE.matcher(value.toLowerCase(Locale.ROOT));
		if (!matcher.matches()) {
			throw new IllegalArgumentException("not a duration: " + text);
		}
		long amount = Long.parseLong(matcher.group(1));
		String unit = (matcher.group(2) != null) ? matcher.group(2) : "ms";
		return switch (unit) {
			case "ns" -> Duration.ofNanos(amount);
			case "us" -> Duration.of(amount, ChronoUnit.MICROS);
			case "ms" -> Duration.ofMillis(amount);
			case "s" -> Duration.ofSeconds(amount);
			case "m" -> Duration.ofMinutes(amount);
			case "h" -> Duration.ofHours(amount);
			default -> Duration.ofDays(amount);
		};
	}

	/**
	 * Whole seconds, rounded up, for the server's second-based fields.
	 * @param duration the duration, may be {@code null}
	 * @return the seconds, or {@code null}
	 */
	public static Long toSecondsCeil(Duration duration) {
		if (duration == null) {
			return null;
		}
		long seconds = duration.getSeconds();
		return (duration.getNano() > 0) ? seconds + 1 : seconds;
	}

}
