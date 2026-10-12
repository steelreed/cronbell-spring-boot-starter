package com.example.demo;

import com.steelreed.cronbell.spring.CronbellCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Three jobs Cronbell discovers with no code changes besides one optional annotation. */
@Component
class Jobs {

	private static final Logger log = LoggerFactory.getLogger(Jobs.class);

	/** Every minute, Vietnam time. Reported as "jobs.send-reminders". */
	@Scheduled(cron = "0 * * * * *", zone = "Asia/Ho_Chi_Minh")
	void sendReminders() throws InterruptedException {
		Thread.sleep(800);
		log.info("Sent reminders");
	}

	/** Every 30 seconds, with a stable name chosen by the team. */
	@Scheduled(fixedRate = 30_000)
	@CronbellCheck("queue-drain")
	void drainQueue() {
		log.info("Drained the queue");
	}

	/** Fails every time: Cronbell marks it DOWN and stores the exception. */
	@Scheduled(fixedRate = 45_000)
	void syncInvoices() {
		throw new IllegalStateException("Payment provider returned 503");
	}

}
