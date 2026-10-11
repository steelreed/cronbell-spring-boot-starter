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

package com.steelreed.cronbell.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.core.annotation.AliasFor;

/**
 * Customises how Cronbell monitors a {@code @Scheduled} method. Optional: every
 * {@code @Scheduled} method is monitored without it.
 *
 * <pre class="code">
 * &#64;Scheduled(cron = "0 0 2 * * *", zone = "Europe/Berlin")
 * &#64;CronbellCheck(value = "nightly-backup", grace = "30m", maxRuntime = "2h")
 * void backup() { ... }
 * </pre>
 *
 * <p>
 * Attributes accept {@code ${...}} placeholders. Durations are written as {@code 30s},
 * {@code 5m}, {@code 2h}, {@code 1d}, an ISO-8601 duration ({@code PT5M}) or a number of
 * milliseconds.
 *
 * @author Nguyen Quang Vinh
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CronbellCheck {

	/**
	 * Alias for {@link #slug()}.
	 * @return the slug
	 */
	@AliasFor("slug")
	String value() default "";

	/**
	 * The check's slug, stable across refactorings: 1 to 128 letters, digits, {@code .},
	 * {@code _} or {@code -}. Defaults to {@code <bean>.<method>} in kebab case. The
	 * {@code cronbell.environment} prefix is still added.
	 * @return the slug
	 */
	@AliasFor("value")
	String slug() default "";

	/**
	 * Display name on the dashboard. Defaults to {@code ClassName.method}.
	 * @return the name
	 */
	String name() default "";

	/**
	 * How late a run may be before the check goes down. Defaults to
	 * {@code cronbell.grace}, else the server's default.
	 * @return the grace time
	 */
	String grace() default "";

	/**
	 * How long a run may take before the check goes down. Defaults to ShedLock's
	 * {@code lockAtMostFor} when the method has {@code @SchedulerLock}, else no limit.
	 * @return the maximum run time
	 */
	String maxRuntime() default "";

	/**
	 * Set to {@code false} to leave this method unmonitored.
	 * @return whether the method is monitored
	 */
	boolean enabled() default true;

}
