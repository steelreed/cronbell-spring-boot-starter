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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.config.Task;

/**
 * Hooks into {@code @EnableScheduling} before any task is scheduled: discovers the
 * {@code @Scheduled} methods (so their checks are registered before their first run) and
 * makes sure the observation registry of the task registrar reaches the
 * {@link ScheduledRunObservationHandler}.
 *
 * <p>
 * When the registrar has no observation registry (no Actuator, or scheduled task
 * observations turned off), Spring records no observations for scheduled tasks; the
 * starter then gives the registrar a private registry holding only its handler, so the
 * application's metrics do not change. The handler is also added to the application's
 * registries, in case Actuator sets one on the registrar after this configurer.
 */
public class CronbellSchedulingConfigurer implements SchedulingConfigurer, Ordered {

	private final CronbellMonitor monitor;

	private final ScheduledTaskDiscovery discovery;

	private final ScheduledRunObservationHandler handler;

	private final ObjectProvider<ObservationRegistry> applicationRegistries;

	private final Set<ObservationRegistry> handled = Collections.newSetFromMap(new IdentityHashMap<>());

	private ObservationRegistry privateRegistry;

	public CronbellSchedulingConfigurer(CronbellMonitor monitor, ScheduledTaskDiscovery discovery,
			ScheduledRunObservationHandler handler, ObjectProvider<ObservationRegistry> applicationRegistries) {
		this.monitor = monitor;
		this.discovery = discovery;
		this.handler = handler;
		this.applicationRegistries = applicationRegistries;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public void configureTasks(ScheduledTaskRegistrar registrar) {
		List<Task> tasks = new ArrayList<>();
		tasks.addAll(registrar.getCronTaskList());
		tasks.addAll(registrar.getFixedRateTaskList());
		tasks.addAll(registrar.getFixedDelayTaskList());
		tasks.addAll(registrar.getTriggerTaskList());
		tasks.addAll(CronbellMonitor.tasksOf(registrar.getScheduledTasks()));
		this.monitor.addScheduled(this.discovery, tasks);
		attachHandler(registrar);
	}

	synchronized void attachHandler(ScheduledTaskRegistrar registrar) {
		this.applicationRegistries.orderedStream().forEach(this::addHandler);
		ObservationRegistry current = registrar.getObservationRegistry();
		if (current != null && !current.isNoop()) {
			addHandler(current);
			return;
		}
		if (this.privateRegistry == null) {
			// A registry without handlers reports isNoop(), so the handler is added
			// directly rather than through addHandler.
			this.privateRegistry = ObservationRegistry.create();
			this.privateRegistry.observationConfig().observationHandler(this.handler);
			this.handled.add(this.privateRegistry);
		}
		registrar.setObservationRegistry(this.privateRegistry);
	}

	private void addHandler(ObservationRegistry registry) {
		if (!registry.isNoop() && this.handled.add(registry)) {
			registry.observationConfig().observationHandler(this.handler);
		}
	}

}
