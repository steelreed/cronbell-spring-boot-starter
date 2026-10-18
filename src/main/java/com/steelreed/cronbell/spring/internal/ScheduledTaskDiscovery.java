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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.steelreed.cronbell.spring.CronbellCheck;
import com.steelreed.cronbell.spring.CronbellProperties;
import com.steelreed.cronbell.spring.internal.shedlock.SchedulerLockAnnotation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.IntervalTask;
import org.springframework.scheduling.config.Task;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Turns Spring's scheduled tasks into checks.
 *
 * <ul>
 * <li>cron tasks keep their expression and zone ({@code @Scheduled(zone)}, else the JVM
 * zone);</li>
 * <li>{@code fixedRate} and {@code fixedDelay} become a period of at least 60 s, the
 * server's minimum; {@code initialDelay} is ignored;</li>
 * <li>one-time tasks, custom triggers, tasks that are not methods and methods with
 * several schedules are left out.</li>
 * </ul>
 */
public class ScheduledTaskDiscovery {

	private static final Log logger = LogFactory.getLog(ScheduledTaskDiscovery.class);

	private final ConfigurableListableBeanFactory beanFactory;

	private final CronbellProperties properties;

	private final SlugFilter filter;

	public ScheduledTaskDiscovery(ConfigurableListableBeanFactory beanFactory, CronbellProperties properties) {
		this.beanFactory = beanFactory;
		this.properties = properties;
		this.filter = new SlugFilter(properties.getInclude(), properties.getExclude());
	}

	/** A task to monitor. */
	public record Discovered(ScheduledKey key, MonitoredCheck check) {
	}

	/**
	 * Finds the checks of the given tasks.
	 * @param tasks scheduled tasks, from a registrar or a {@code ScheduledTaskHolder}
	 * @param known keys already monitored, skipped
	 * @return the checks to monitor
	 */
	public List<Discovered> discover(Collection<? extends Task> tasks, Set<ScheduledKey> known) {
		Map<ScheduledKey, List<Task>> byMethod = new LinkedHashMap<>();
		for (Task task : tasks) {
			ScheduledMethodRunnable runnable = methodRunnable(task);
			if (runnable != null) {
				ScheduledKey key = ScheduledKey.of(runnable.getTarget(), runnable.getMethod());
				List<Task> sameMethod = byMethod.computeIfAbsent(key, (k) -> new ArrayList<>());
				if (!sameMethod.contains(task)) {
					sameMethod.add(task);
				}
			}
		}
		List<Discovered> result = new ArrayList<>();
		for (Map.Entry<ScheduledKey, List<Task>> entry : byMethod.entrySet()) {
			if (known.contains(entry.getKey())) {
				continue;
			}
			List<Task> sameMethod = entry.getValue();
			ScheduledMethodRunnable runnable = methodRunnable(sameMethod.get(0));
			String source = "@Scheduled " + entry.getKey().targetClass().getSimpleName() + "."
					+ runnable.getMethod().getName() + "()";
			if (sameMethod.size() > 1) {
				logger.info("Cronbell: " + source + " has " + sameMethod.size()
						+ " schedules and is not monitored: a check has one schedule.");
				continue;
			}
			try {
				MonitoredCheck check = toCheck(sameMethod.get(0), runnable, source);
				if (check != null) {
					result.add(new Discovered(entry.getKey(), check));
				}
			}
			catch (RuntimeException ex) {
				logger.warn("Cronbell: " + source + " is not monitored: " + ex.getMessage());
			}
		}
		return result;
	}

	/**
	 * The {@code ScheduledMethodRunnable} of a task. Spring Framework 6.2+ wraps it in a
	 * private outcome-tracking runnable with no accessor, so the wrapper's field is read.
	 * @param task the task
	 * @return the method runnable, or {@code null} if the task does not run a method
	 */
	public static ScheduledMethodRunnable methodRunnable(Task task) {
		Runnable runnable = task.getRunnable();
		for (int depth = 0; depth < 3 && runnable != null; depth++) {
			if (runnable instanceof ScheduledMethodRunnable methodRunnable) {
				return methodRunnable;
			}
			runnable = wrapped(runnable);
		}
		return null;
	}

	private static Runnable wrapped(Runnable runnable) {
		try {
			Field field = ReflectionUtils.findField(runnable.getClass(), "runnable", Runnable.class);
			if (field == null) {
				return null;
			}
			ReflectionUtils.makeAccessible(field);
			return (Runnable) field.get(runnable);
		}
		catch (Exception | LinkageError ex) {
			return null;
		}
	}

	private MonitoredCheck toCheck(Task task, ScheduledMethodRunnable runnable, String source) {
		Object target = runnable.getTarget();
		Method method = AopUtils.getMostSpecificMethod(runnable.getMethod(), AopProxyUtils.ultimateTargetClass(target));
		CronbellCheck annotation = AnnotatedElementUtils.findMergedAnnotation(method, CronbellCheck.class);
		if (annotation != null && !annotation.enabled()) {
			logger.debug("Cronbell: " + source + " has @CronbellCheck(enabled = false)");
			return null;
		}

		String cron = null;
		String zone = null;
		Long period = null;
		if (task instanceof CronTask cronTask) {
			cron = cronTask.getExpression();
			zone = zoneOf(method);
		}
		else if (task instanceof FixedRateTask || task instanceof FixedDelayTask) {
			period = periodOf((IntervalTask) task);
		}
		else {
			logger.debug("Cronbell: " + source + " has a one-time or custom trigger and is not monitored");
			return null;
		}

		String explicitSlug = (annotation != null) ? resolve(annotation.slug()) : "";
		String slug = StringUtils.hasText(explicitSlug) ? explicitSlug.trim()
				: SlugNaming.forScheduled(beanNameOf(target), runnable.getMethod().getName());
		if (!SlugNaming.isValid(slug)) {
			throw new IllegalArgumentException(
					"'" + slug + "' is not a valid slug (1-128 letters, digits, '.', '_', '-')");
		}
		if (!this.filter.accepts(slug)) {
			logger.debug("Cronbell: " + source + " (" + slug + ") is excluded by cronbell.include/exclude");
			return null;
		}

		String name = (annotation != null) ? resolve(annotation.name()) : "";
		if (!StringUtils.hasText(name)) {
			name = ScheduledKey.of(target, method).targetClass().getSimpleName() + "." + method.getName();
		}
		Duration grace = (annotation != null) ? Durations.parse(resolve(annotation.grace())) : null;
		if (grace == null) {
			grace = this.properties.getGrace();
		}
		Duration maxRuntime = (annotation != null) ? Durations.parse(resolve(annotation.maxRuntime())) : null;
		SchedulerLockAnnotation lock = SchedulerLockAnnotation.find(method);
		if (maxRuntime == null && lock != null) {
			maxRuntime = lockAtMostFor(lock);
		}

		CheckDefinition definition = new CheckDefinition(
				SlugNaming.withEnvironment(this.properties.getEnvironment(), slug), name, cron, zone, period,
				Durations.toSecondsCeil(grace), Durations.toSecondsCeil(maxRuntime));
		return new MonitoredCheck(definition, source, lock != null);
	}

	private String zoneOf(Method method) {
		Set<Scheduled> annotations = AnnotatedElementUtils.getMergedRepeatableAnnotations(method, Scheduled.class,
				Schedules.class);
		for (Scheduled scheduled : annotations) {
			String zone = resolve(scheduled.zone());
			if (StringUtils.hasText(zone)) {
				return StringUtils.parseTimeZoneString(zone).toZoneId().getId();
			}
		}
		return ZoneId.systemDefault().getId();
	}

	static long periodOf(IntervalTask task) {
		Duration interval = task.getIntervalDuration();
		long seconds = Math.max(Durations.toSecondsCeil(interval), CheckDefinition.MIN_PERIOD_SECONDS);
		return seconds;
	}

	private Duration lockAtMostFor(SchedulerLockAnnotation lock) {
		try {
			String value = resolve(lock.lockAtMostFor());
			Duration duration = Durations.parse(value);
			return (duration != null && !duration.isZero() && !duration.isNegative()) ? duration : null;
		}
		catch (RuntimeException ex) {
			return null;
		}
	}

	private String beanNameOf(Object target) {
		Class<?> type = target.getClass();
		for (String name : this.beanFactory.getBeanNamesForType(type, true, false)) {
			if (this.beanFactory.containsSingleton(name) && this.beanFactory.getSingleton(name) == target) {
				return name;
			}
		}
		return StringUtils.uncapitalize(ClassUtils.getUserClass(target).getSimpleName());
	}

	private String resolve(String value) {
		if (!StringUtils.hasText(value)) {
			return "";
		}
		String resolved = this.beanFactory.resolveEmbeddedValue(value);
		return (resolved != null) ? resolved : "";
	}

}
