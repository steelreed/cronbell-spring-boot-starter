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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import com.steelreed.cronbell.spring.CronbellProperties;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.CronTrigger;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.EverythingMatcher;
import org.quartz.impl.matchers.GroupMatcher;

/**
 * Monitors the triggers of Quartz schedulers: {@code CronTrigger}s and endlessly
 * repeating {@code SimpleTrigger}s, slug {@code <group>.<name>} of the trigger. Runs are
 * reported by a {@link JobListener}; a vetoed run reports nothing.
 */
public class QuartzMonitor implements JobListener, RescannableJobSource {

	private static final Log logger = LogFactory.getLog(QuartzMonitor.class);

	static final String LISTENER_NAME = "cronbell";

	private static final String RUN = Run.class.getName();

	private final CronbellMonitor monitor;

	private final SlugFilter filter;

	private final List<Scheduler> schedulers = new ArrayList<>();

	public QuartzMonitor(CronbellMonitor monitor) {
		this.monitor = monitor;
		CronbellProperties properties = monitor.properties();
		this.filter = new SlugFilter(properties.getInclude(), properties.getExclude());
	}

	/** Identifies a trigger across schedulers. */
	record QuartzKey(String scheduler, TriggerKey trigger) {
	}

	/**
	 * Listens to a scheduler's jobs and monitors its triggers.
	 * @param scheduler the scheduler
	 */
	public synchronized void attach(Scheduler scheduler) {
		try {
			if (!this.schedulers.contains(scheduler)) {
				this.schedulers.add(scheduler);
				if (scheduler.getListenerManager().getJobListener(LISTENER_NAME) == null) {
					scheduler.getListenerManager().addJobListener(this, EverythingMatcher.allJobs());
				}
			}
			discover(scheduler);
		}
		catch (SchedulerException | RuntimeException ex) {
			logger.warn("Cronbell: cannot read the Quartz triggers: " + ex.getMessage());
		}
	}

	/**
	 * Looks for triggers added since the last scan.
	 */
	@Override
	public synchronized void rescan() {
		for (Scheduler scheduler : this.schedulers) {
			try {
				discover(scheduler);
			}
			catch (SchedulerException | RuntimeException ex) {
				logger.warn("Cronbell: cannot read the Quartz triggers: " + ex.getMessage());
			}
		}
	}

	private void discover(Scheduler scheduler) throws SchedulerException {
		String schedulerName = scheduler.getSchedulerName();
		for (String group : scheduler.getTriggerGroupNames()) {
			for (TriggerKey key : scheduler.getTriggerKeys(GroupMatcher.triggerGroupEquals(group))) {
				QuartzKey runtimeKey = new QuartzKey(schedulerName, key);
				if (this.monitor.registry().contains(runtimeKey)) {
					continue;
				}
				Trigger trigger = scheduler.getTrigger(key);
				MonitoredCheck check = (trigger != null) ? toCheck(trigger) : null;
				if (check != null) {
					this.monitor.add(runtimeKey, check);
				}
			}
		}
	}

	MonitoredCheck toCheck(Trigger trigger) {
		TriggerKey key = trigger.getKey();
		String source = "Quartz trigger " + key;
		String cron = null;
		String zone = null;
		Long period = null;
		if (trigger instanceof CronTrigger cronTrigger) {
			cron = toSpringCron(cronTrigger.getCronExpression());
			if (cron == null) {
				logger.info("Cronbell: " + source + " is not monitored: its cron expression '"
						+ cronTrigger.getCronExpression() + "' uses a year field");
				return null;
			}
			TimeZone timeZone = cronTrigger.getTimeZone();
			zone = ((timeZone != null) ? timeZone : TimeZone.getDefault()).toZoneId().getId();
		}
		else if (trigger instanceof SimpleTrigger simple && simple.getRepeatCount() == SimpleTrigger.REPEAT_INDEFINITELY
				&& simple.getRepeatInterval() > 0) {
			long seconds = Durations.toSecondsCeil(Duration.ofMillis(simple.getRepeatInterval()));
			period = Math.max(seconds, CheckDefinition.MIN_PERIOD_SECONDS);
		}
		else {
			logger.debug("Cronbell: " + source + " is not monitored: only cron and endlessly repeating simple"
					+ " triggers are");
			return null;
		}
		String slug = SlugNaming.forQuartz(key.getGroup(), key.getName());
		if (!this.filter.accepts(slug)) {
			return null;
		}
		CronbellProperties properties = this.monitor.properties();
		String name = (trigger.getDescription() != null && !trigger.getDescription().isBlank())
				? trigger.getDescription() : key.getGroup() + "." + key.getName();
		CheckDefinition definition = new CheckDefinition(SlugNaming.withEnvironment(properties.getEnvironment(), slug),
				name, cron, zone, period, Durations.toSecondsCeil(properties.getGrace()), null);
		return new MonitoredCheck(definition, source, false);
	}

	/**
	 * Quartz cron to Spring cron, which the server reads: drops a {@code *} year field
	 * and shifts numeric days of week (Quartz counts 1 = Sunday, Spring 0 = Sunday).
	 * @param quartz the Quartz expression
	 * @return the Spring expression, or {@code null} if it restricts the year
	 */
	static String toSpringCron(String quartz) {
		String[] fields = quartz.trim().split("\\s+");
		if (fields.length == 7) {
			if (!fields[6].equals("*") && !fields[6].equals("?")) {
				return null;
			}
		}
		else if (fields.length != 6) {
			return quartz.trim();
		}
		fields[5] = shiftDaysOfWeek(fields[5]);
		return String.join(" ", fields[0], fields[1], fields[2], fields[3], fields[4], fields[5]);
	}

	private static String shiftDaysOfWeek(String field) {
		List<String> parts = new ArrayList<>();
		for (String part : field.split(",")) {
			String step = "";
			int slash = part.indexOf('/');
			if (slash >= 0) {
				step = part.substring(slash);
				part = part.substring(0, slash);
			}
			String suffix = "";
			int hash = part.indexOf('#');
			if (hash >= 0) {
				suffix = part.substring(hash);
				part = part.substring(0, hash);
			}
			else if (part.toUpperCase(Locale.ROOT).endsWith("L") && part.length() > 1) {
				suffix = part.substring(part.length() - 1);
				part = part.substring(0, part.length() - 1);
			}
			StringBuilder shifted = new StringBuilder();
			for (String bound : part.split("-", -1)) {
				if (shifted.length() > 0) {
					shifted.append('-');
				}
				shifted.append(bound.matches("\\d+") ? String.valueOf(Integer.parseInt(bound) - 1) : bound);
			}
			parts.add(shifted + suffix + step);
		}
		return String.join(",", parts);
	}

	// ---- JobListener --------------------------------------------------------------

	@Override
	public String getName() {
		return LISTENER_NAME;
	}

	@Override
	public void jobToBeExecuted(JobExecutionContext context) {
		try {
			MonitoredCheck check = this.monitor.registry()
				.find(new QuartzKey(context.getScheduler().getSchedulerName(), context.getTrigger().getKey()));
			if (check == null) {
				return;
			}
			Run run = this.monitor.reporter().begin(check);
			context.put(RUN, run);
			this.monitor.reporter().start(run);
		}
		catch (Throwable ex) {
			logger.debug("Cronbell: could not report the start of a Quartz job", ex);
		}
	}

	@Override
	public void jobExecutionVetoed(JobExecutionContext context) {
		// The job did not run; nothing to report.
	}

	@Override
	public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
		try {
			if (context.get(RUN) instanceof Run run) {
				Throwable error = jobException;
				if (jobException != null && jobException.getCause() != null) {
					error = jobException.getCause();
				}
				this.monitor.reporter().finish(run, error);
			}
		}
		catch (Throwable ex) {
			logger.debug("Cronbell: could not report the end of a Quartz job", ex);
		}
	}

}
