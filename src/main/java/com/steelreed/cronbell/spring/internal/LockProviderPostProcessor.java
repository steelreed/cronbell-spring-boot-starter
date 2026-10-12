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

import java.util.Optional;

import org.aopalliance.intercept.MethodInterceptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.util.ClassUtils;

/**
 * Wraps ShedLock {@code LockProvider} beans to learn whether a run got its lock (see
 * {@link LockedRuns}). The wrapper is a class-based proxy, so the bean keeps its type; a
 * provider that cannot be proxied is left alone, and its skipped runs are then reported
 * as short successes.
 */
public class LockProviderPostProcessor implements BeanPostProcessor, BeanClassLoaderAware, Ordered {

	private static final Log logger = LogFactory.getLog(LockProviderPostProcessor.class);

	static final String LOCK_PROVIDER = "net.javacrumbs.shedlock.core.LockProvider";

	private ClassLoader classLoader = ClassUtils.getDefaultClassLoader();

	private volatile boolean wrappedAny;

	@Override
	public void setBeanClassLoader(ClassLoader classLoader) {
		this.classLoader = classLoader;
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	/**
	 * Whether at least one lock provider is observed.
	 * @return {@code true} if lock attempts are seen
	 */
	public boolean isActive() {
		return this.wrappedAny;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (!isLockProvider(bean)) {
			return bean;
		}
		try {
			ProxyFactory factory = new ProxyFactory(bean);
			factory.setProxyTargetClass(true);
			factory.addAdvice((MethodInterceptor) (invocation) -> {
				Object result = invocation.proceed();
				if ("lock".equals(invocation.getMethod().getName()) && invocation.getArguments().length == 1) {
					LockedRuns.lockAttempted(result instanceof Optional<?> lock && lock.isPresent());
				}
				return result;
			});
			Object proxy = factory.getProxy(this.classLoader);
			this.wrappedAny = true;
			return proxy;
		}
		catch (RuntimeException | LinkageError ex) {
			logger.debug("Cronbell: cannot observe ShedLock provider '" + beanName + "'; runs skipped by its lock"
					+ " are reported as successes", ex);
			return bean;
		}
	}

	private boolean isLockProvider(Object bean) {
		for (Class<?> type : ClassUtils.getAllInterfacesForClassAsSet(bean.getClass())) {
			if (LOCK_PROVIDER.equals(type.getName())) {
				return true;
			}
		}
		return false;
	}

}
