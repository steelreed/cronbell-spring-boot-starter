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

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * Turns bean, method, group and trigger names into check slugs.
 *
 * <p>
 * Slugs are 1 to 128 characters of letters, digits, {@code .}, {@code _} and {@code -}
 * (DESIGN.md §6). Generated slugs are kebab case: {@code reportJob.sendDaily} becomes
 * {@code report-job.send-daily}.
 */
public final class SlugNaming {

	/** Longest slug the server accepts. */
	public static final int MAX_LENGTH = 128;

	private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1," + MAX_LENGTH + "}");

	private static final Pattern INVALID_CHARS = Pattern.compile("[^a-z0-9._-]+");

	private static final Pattern DASHES = Pattern.compile("-{2,}");

	private static final Pattern DASHES_AROUND_DOTS = Pattern.compile("-*\\.-*");

	private SlugNaming() {
	}

	/**
	 * Slug of a {@code @Scheduled} method: {@code <bean>.<method>}. A bean named after
	 * its class ({@code com.example.Jobs}) contributes its simple name.
	 * @param beanName the bean name
	 * @param methodName the method name
	 * @return the slug, without environment prefix
	 */
	public static String forScheduled(String beanName, String methodName) {
		String bean = beanName;
		int hash = bean.indexOf('#');
		String suffix = "";
		if (hash >= 0) {
			suffix = bean.substring(hash + 1);
			bean = bean.substring(0, hash);
		}
		int dot = bean.lastIndexOf('.');
		if (dot >= 0 && dot < bean.length() - 1) {
			bean = bean.substring(dot + 1);
		}
		if (!suffix.isEmpty()) {
			bean = bean + "-" + suffix;
		}
		return fit(kebab(bean) + "." + kebab(methodName));
	}

	/**
	 * Slug of a Quartz trigger: {@code <group>.<name>}.
	 * @param group the trigger group
	 * @param name the trigger name
	 * @return the slug, without environment prefix
	 */
	public static String forQuartz(String group, String name) {
		return fit(kebab(group) + "." + kebab(name));
	}

	/**
	 * Adds the environment prefix.
	 * @param environment the environment, may be {@code null} or blank
	 * @param slug the slug
	 * @return {@code <environment>.<slug>}, or the slug alone
	 */
	public static String withEnvironment(String environment, String slug) {
		if (environment == null || environment.isBlank()) {
			return slug;
		}
		return fit(kebab(environment) + "." + slug);
	}

	/**
	 * Whether the server accepts this slug.
	 * @param slug the slug
	 * @return {@code true} if valid
	 */
	public static boolean isValid(String slug) {
		return slug != null && VALID.matcher(slug).matches();
	}

	/**
	 * Kebab case: words split at lower-to-upper case changes, lower case, characters the
	 * server does not accept replaced by {@code -}.
	 * @param name a Java-style name
	 * @return the kebab-case form, never empty
	 */
	public static String kebab(String name) {
		StringBuilder out = new StringBuilder(name.length() + 8);
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (Character.isUpperCase(c) && i > 0) {
				char previous = name.charAt(i - 1);
				boolean nextIsLower = i + 1 < name.length() && Character.isLowerCase(name.charAt(i + 1));
				if (Character.isLowerCase(previous) || Character.isDigit(previous)
						|| (Character.isUpperCase(previous) && nextIsLower)) {
					out.append('-');
				}
			}
			out.append(c);
		}
		String slug = out.toString().toLowerCase(Locale.ROOT);
		slug = INVALID_CHARS.matcher(slug).replaceAll("-");
		slug = DASHES.matcher(slug).replaceAll("-");
		slug = DASHES_AROUND_DOTS.matcher(slug).replaceAll(".");
		slug = trim(slug);
		return slug.isEmpty() ? "job" : slug;
	}

	/**
	 * Shortens a slug to {@link #MAX_LENGTH}, keeping it unique with a hash of the whole.
	 * @param slug the slug
	 * @return the slug, at most 128 characters
	 */
	static String fit(String slug) {
		if (slug.length() <= MAX_LENGTH) {
			return slug;
		}
		CRC32 crc = new CRC32();
		crc.update(slug.getBytes(StandardCharsets.UTF_8));
		String hash = String.format("%08x", crc.getValue());
		return slug.substring(0, MAX_LENGTH - hash.length() - 1) + "-" + hash;
	}

	private static String trim(String slug) {
		int start = 0;
		int end = slug.length();
		while (start < end && (slug.charAt(start) == '-' || slug.charAt(start) == '.')) {
			start++;
		}
		while (end > start && (slug.charAt(end - 1) == '-' || slug.charAt(end - 1) == '.')) {
			end--;
		}
		return slug.substring(start, end);
	}

}
