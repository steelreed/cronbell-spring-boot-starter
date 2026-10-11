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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.steelreed.cronbell.spring.CronbellProperties;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.Task;

/**
 * Holds the monitored checks and the reporter; adds newly discovered jobs.
 */
public class CronbellMonitor implements AutoCloseable {

	private static final Log logger = LogFactory.getLog(CronbellMonitor.class);

	private final CheckRegistry registry = new CheckRegistry();

	private final Reporter reporter;

	private final CronbellProperties properties;

	public CronbellMonitor(CronbellProperties properties, Reporter reporter) {
		this.properties = properties;
		this.reporter = reporter;
	}

	public CheckRegistry registry() {
		return this.registry;
	}

	public Reporter reporter() {
		return this.reporter;
	}

	public CronbellProperties properties() {
		return this.properties;
	}

	/**
	 * Monitors the {@code @Scheduled} methods among these tasks that are not monitored
	 * yet.
	 * @param discovery the discovery
	 * @param tasks registrar tasks
	 * @return the new checks
	 */
	public List<MonitoredCheck> addScheduled(ScheduledTaskDiscovery discovery, Collection<? extends Task> tasks) {
		Set<ScheduledKey> known = new HashSet<>();
		for (Task task : tasks) {
			if (task.getRunnable() instanceof org.springframework.scheduling.support.ScheduledMethodRunnable runnable) {
				ScheduledKey key = ScheduledKey.of(runnable.getTarget(), runnable.getMethod());
				if (this.registry.contains(key)) {
					known.add(key);
				}
			}
		}
		List<MonitoredCheck> added = new ArrayList<>();
		for (ScheduledTaskDiscovery.Discovered discovered : discovery.discover(tasks, known)) {
			if (add(discovered.key(), discovered.check())) {
				added.add(discovered.check());
			}
		}
		return added;
	}

	/**
	 * Tasks of a {@code ScheduledTaskHolder}.
	 * @param scheduled the scheduled tasks
	 * @return their tasks
	 */
	public static List<Task> tasksOf(Collection<ScheduledTask> scheduled) {
		return scheduled.stream().map(ScheduledTask::getTask).collect(Collectors.toList());
	}

	/**
	 * Monitors a job, registering its check in the background.
	 * @param key the runtime key
	 * @param check the check
	 * @return {@code true} if it was new
	 */
	public boolean add(Object key, MonitoredCheck check) {
		if (!this.registry.add(key, check)) {
			return false;
		}
		this.reporter.register(check);
		return true;
	}

	/**
	 * Logs which jobs are monitored, once the application is ready.
	 */
	public void logSummary() {
		List<MonitoredCheck> checks = this.registry.all();
		if (checks.isEmpty()) {
			logger.info("Cronbell: no scheduled jobs found to monitor (" + this.reporter.baseUrl() + ")");
			return;
		}
		String slugs = checks.stream().map(MonitoredCheck::slug).sorted().limit(10).collect(Collectors.joining(", "));
		logger.info("Cronbell: monitoring " + checks.size() + ((checks.size() == 1) ? " job" : " jobs") + " at "
				+ this.reporter.baseUrl() + ": " + slugs + ((checks.size() > 10) ? ", ..." : ""));
	}

	@Override
	public void close() {
		this.reporter.close();
	}

}
