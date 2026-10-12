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

/**
 * Connects a {@code @Scheduled} run to the ShedLock lock it asks for on the same thread.
 *
 * <p>
 * For a method with {@code @SchedulerLock}, the {@code start} ping waits until the lock
 * is taken. When another node holds the lock, ShedLock skips the method: the run is
 * marked skipped and reports nothing, since the node that ran the job reports it.
 */
public final class LockedRuns {

	private static final ThreadLocal<Pending> CURRENT = new ThreadLocal<>();

	private LockedRuns() {
	}

	/** A run waiting for its lock. */
	static final class Pending {

		final Run run;

		final Reporter reporter;

		boolean decided;

		Pending(Run run, Reporter reporter) {
			this.run = run;
			this.reporter = reporter;
		}

	}

	static void await(Run run, Reporter reporter) {
		CURRENT.set(new Pending(run, reporter));
	}

	static void clear() {
		CURRENT.remove();
	}

	/**
	 * Called by the wrapped {@code LockProvider} after each {@code lock(...)} call. Only
	 * the first call of a run counts: that is the lock of the scheduled method itself.
	 * @param acquired whether the lock was taken
	 */
	public static void lockAttempted(boolean acquired) {
		Pending pending = CURRENT.get();
		if (pending == null || pending.decided) {
			return;
		}
		pending.decided = true;
		if (acquired) {
			pending.reporter.start(pending.run);
		}
		else {
			pending.run.skip();
		}
	}

}
