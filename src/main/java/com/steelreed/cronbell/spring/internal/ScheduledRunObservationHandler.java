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

import java.util.function.BooleanSupplier;

import com.steelreed.cronbell.spring.internal.shedlock.LockedRuns;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.scheduling.support.ScheduledTaskObservationContext;

/**
 * Reports {@code @Scheduled} runs. Spring Framework 6.1+ wraps every {@code @Scheduled}
 * invocation in an observation, on the thread that runs it and whatever the
 * {@code TaskScheduler}; this handler turns its start into a {@code start} ping and its
 * stop into {@code success} or {@code fail}. The job's exception is untouched: Spring
 * rethrows it after the handlers ran.
 *
 * <p>
 * Never throws: a reporting problem must not reach the job.
 */
public class ScheduledRunObservationHandler implements ObservationHandler<ScheduledTaskObservationContext> {

	private static final Log logger = LogFactory.getLog(ScheduledRunObservationHandler.class);

	private static final String RUN = Run.class.getName();

	private final CheckRegistry registry;

	private final Reporter reporter;

	private final BooleanSupplier lockAware;

	/**
	 * Creates the handler.
	 * @param registry the monitored checks
	 * @param reporter sends the pings
	 * @param lockAware whether ShedLock lock attempts are observed, so that a
	 * {@code @SchedulerLock} run can wait for its lock before reporting {@code start}
	 */
	public ScheduledRunObservationHandler(CheckRegistry registry, Reporter reporter, BooleanSupplier lockAware) {
		this.registry = registry;
		this.reporter = reporter;
		this.lockAware = lockAware;
	}

	@Override
	public boolean supportsContext(Observation.Context context) {
		return context instanceof ScheduledTaskObservationContext;
	}

	@Override
	public void onStart(ScheduledTaskObservationContext context) {
		try {
			MonitoredCheck check = this.registry.find(new ScheduledKey(context.getTargetClass(), context.getMethod()));
			if (check == null) {
				return;
			}
			Run run = this.reporter.begin(check);
			context.put(RUN, run);
			if (check.lockGuarded() && this.lockAware.getAsBoolean()) {
				LockedRuns.await(run, this.reporter);
			}
			else {
				this.reporter.start(run);
			}
		}
		catch (Throwable ex) {
			logger.debug("Cronbell: could not report the start of a run", ex);
		}
	}

	@Override
	public void onStop(ScheduledTaskObservationContext context) {
		try {
			Run run = context.get(RUN);
			if (run == null) {
				return;
			}
			if (run.check().lockGuarded()) {
				LockedRuns.clear();
			}
			this.reporter.finish(run, context.getError());
		}
		catch (Throwable ex) {
			logger.debug("Cronbell: could not report the end of a run", ex);
		}
	}

}
