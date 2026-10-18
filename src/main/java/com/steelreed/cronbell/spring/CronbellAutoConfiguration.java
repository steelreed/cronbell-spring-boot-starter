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

import com.steelreed.cronbell.spring.internal.CronbellActivation;
import com.steelreed.cronbell.spring.internal.CronbellClient;
import com.steelreed.cronbell.spring.internal.CronbellMonitor;
import com.steelreed.cronbell.spring.internal.CronbellSchedulingConfigurer;
import com.steelreed.cronbell.spring.internal.Reporter;
import com.steelreed.cronbell.spring.internal.RescannableJobSource;
import com.steelreed.cronbell.spring.internal.ScheduledRunObservationHandler;
import com.steelreed.cronbell.spring.internal.ScheduledTaskDiscovery;
import com.steelreed.cronbell.spring.internal.actuator.CronbellInfoContributor;
import com.steelreed.cronbell.spring.internal.quartz.QuartzMonitor;
import com.steelreed.cronbell.spring.internal.shedlock.LockProviderPostProcessor;
import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.LogFactory;
import org.quartz.Scheduler;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/**
 * Auto-configuration of Cronbell monitoring. Active when {@code cronbell.url} and
 * {@code cronbell.api-key} are set; otherwise it logs one INFO line saying why it is off.
 *
 * @author Nguyen Quang Vinh
 */
@AutoConfiguration
@EnableConfigurationProperties(CronbellProperties.class)
public class CronbellAutoConfiguration {

	@Configuration(proxyBeanMethods = false)
	@Conditional(CronbellActivation.On.class)
	static class Monitoring {

		@Bean(destroyMethod = "close")
		CronbellMonitor cronbellMonitor(CronbellProperties properties) {
			CronbellClient client = new CronbellClient(properties.getUrl(), properties.getApiKey(),
					properties.getTimeout().getConnect(), properties.getTimeout().getRead());
			Reporter reporter = new Reporter(client, properties.isRegister(), properties.getQueueCapacity());
			return new CronbellMonitor(properties, reporter);
		}

		@Bean
		ScheduledRunObservationHandler cronbellScheduledRunObservationHandler(CronbellMonitor monitor,
				ObjectProvider<LockProviderPostProcessor> lockProviders) {
			LockProviderPostProcessor lockProvider = lockProviders.getIfAvailable();
			return new ScheduledRunObservationHandler(monitor.registry(), monitor.reporter(),
					() -> lockProvider != null && lockProvider.isActive());
		}

		@Bean
		CronbellSchedulingConfigurer cronbellSchedulingConfigurer(CronbellMonitor monitor,
				ConfigurableListableBeanFactory beanFactory, CronbellProperties properties,
				ScheduledRunObservationHandler handler, ObjectProvider<ObservationRegistry> registries) {
			return new CronbellSchedulingConfigurer(monitor, new ScheduledTaskDiscovery(beanFactory, properties),
					handler, registries);
		}

		@Bean
		ApplicationListener<ApplicationReadyEvent> cronbellReadyListener(CronbellMonitor monitor,
				ConfigurableListableBeanFactory beanFactory, CronbellProperties properties,
				ObjectProvider<ScheduledTaskHolder> holders, ObjectProvider<RescannableJobSource> sources) {
			ScheduledTaskDiscovery discovery = new ScheduledTaskDiscovery(beanFactory, properties);
			return (event) -> {
				holders.orderedStream()
					.forEach((holder) -> monitor.addScheduled(discovery,
							CronbellMonitor.tasksOf(holder.getScheduledTasks())));
				sources.orderedStream().forEach(RescannableJobSource::rescan);
				monitor.logSummary();
			};
		}

	}

	/**
	 * Separate class so that applications without Actuator never load a method whose
	 * signature names an Actuator type: a {@code @ConditionalOnClass} on the bean method
	 * alone is too late, because Spring introspects every method of the enclosing class.
	 */
	@Configuration(proxyBeanMethods = false)
	@Conditional(CronbellActivation.On.class)
	@ConditionalOnClass(name = "org.springframework.boot.actuate.info.InfoContributor")
	static class ActuatorInfo {

		@Bean
		CronbellInfoContributor cronbellInfoContributor(CronbellMonitor monitor) {
			return new CronbellInfoContributor(monitor);
		}

	}

	@Configuration(proxyBeanMethods = false)
	@Conditional(CronbellActivation.On.class)
	@ConditionalOnClass(name = "org.quartz.Scheduler")
	static class QuartzMonitoring {

		@Bean
		QuartzMonitor cronbellQuartzMonitor(CronbellMonitor monitor) {
			return new QuartzMonitor(monitor);
		}

		@Bean
		SmartInitializingSingleton cronbellQuartzAttacher(QuartzMonitor quartz, ObjectProvider<Scheduler> schedulers) {
			// Before the schedulers start (SmartLifecycle), so no run is missed.
			return () -> schedulers.orderedStream().forEach(quartz::attach);
		}

	}

	/**
	 * Observes ShedLock lock providers, so that a run skipped by its lock reports
	 * nothing. Present only with ShedLock; the post-processor itself looks ShedLock types
	 * up by name.
	 */
	@Configuration(proxyBeanMethods = false)
	@Conditional(CronbellActivation.On.class)
	@ConditionalOnClass(name = "net.javacrumbs.shedlock.core.LockProvider")
	static class ShedLockMonitoring {

		@Bean
		static LockProviderPostProcessor cronbellLockProviderPostProcessor() {
			return new LockProviderPostProcessor();
		}

	}

	@Configuration(proxyBeanMethods = false)
	@Conditional(CronbellActivation.Off.class)
	static class Off {

		@Bean
		SmartInitializingSingleton cronbellOffNotice(Environment environment) {
			return () -> LogFactory.getLog(CronbellAutoConfiguration.class)
				.info(CronbellActivation.offReason(environment));
		}

	}

}
