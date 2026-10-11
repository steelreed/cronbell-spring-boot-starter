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

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Decides whether monitoring is on: {@code cronbell.enabled} is not {@code false} and
 * both {@code cronbell.url} and {@code cronbell.api-key} are set.
 */
public final class CronbellActivation {

	private CronbellActivation() {
	}

	/**
	 * Why monitoring is off.
	 * @param environment the environment
	 * @return the reason, or {@code null} if monitoring is on
	 */
	public static String offReason(Environment environment) {
		if (!environment.getProperty("cronbell.enabled", Boolean.class, true)) {
			return "Cronbell monitoring is off: cronbell.enabled is false.";
		}
		boolean hasUrl = StringUtils.hasText(environment.getProperty("cronbell.url"));
		boolean hasKey = StringUtils.hasText(environment.getProperty("cronbell.api-key"));
		if (!hasUrl || !hasKey) {
			String missing = (!hasUrl && !hasKey) ? "cronbell.url and cronbell.api-key are"
					: (!hasUrl ? "cronbell.url is" : "cronbell.api-key is");
			return "Cronbell monitoring is off: " + missing + " not set.";
		}
		String url = environment.getProperty("cronbell.url").trim();
		try {
			URI uri = URI.create(url);
			if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
					|| uri.getHost() == null) {
				return "Cronbell monitoring is off: cronbell.url must be an http(s) URL, got '" + url + "'.";
			}
		}
		catch (IllegalArgumentException ex) {
			return "Cronbell monitoring is off: cronbell.url must be an http(s) URL, got '" + url + "'.";
		}
		return null;
	}

	/** Matches when monitoring is on. */
	public static class On extends SpringBootCondition {

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
			String reason = offReason(context.getEnvironment());
			return (reason == null) ? ConditionOutcome.match("cronbell.url and cronbell.api-key are set")
					: ConditionOutcome.noMatch(reason);
		}

	}

	/** Matches when monitoring is off. */
	public static class Off extends SpringBootCondition {

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
			String reason = offReason(context.getEnvironment());
			return (reason != null) ? ConditionOutcome.match(reason)
					: ConditionOutcome.noMatch("cronbell.url and cronbell.api-key are set");
		}

	}

}
