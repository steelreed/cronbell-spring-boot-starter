# Cronbell Spring Boot starter

[![CI](https://github.com/steelreed/cronbell-spring-boot-starter/actions/workflows/ci.yml/badge.svg)](https://github.com/steelreed/cronbell-spring-boot-starter/actions/workflows/ci.yml)
[![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue)](LICENSE)

**Know when a Spring `@Scheduled` job stops running.** Add one dependency and two properties: the
starter finds every scheduled job in your application, registers it with
[Cronbell](https://github.com/steelreed/cronbell) with its real schedule, and reports each run.
Cronbell alerts you when a run is missed, fails or takes too long.

> **Status: beta.** `0.1.0-beta.1` is on Maven Central; `0.1.0` comes with Cronbell's first
> release on October 27, 2026. Try the server at [steelreed.com/cronbell](https://steelreed.com/cronbell/).

## Quick start

```xml
<dependency>
  <groupId>com.steelreed</groupId>
  <artifactId>cronbell-spring-boot-starter</artifactId>
  <version>0.1.0-beta.1</version>
</dependency>
```

```properties
cronbell.url=https://cronbell.example.com
cronbell.api-key=${CRONBELL_API_KEY}
```

That is all. No annotations and no changes to your jobs. Without both properties the starter stays
off and logs one line saying why.

## What it finds

| Source | Schedule sent to Cronbell | Check name (slug) |
|---|---|---|
| `@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Ho_Chi_Minh")` | the cron expression and its zone | `<bean>.<method>`, e.g. `report-job.send-daily` |
| `@Scheduled(fixedRate = …)` / `fixedDelay` | a period (at least 60 s, the server's minimum); `initialDelay` is ignored | `<bean>.<method>` |
| Quartz `CronTrigger` / `SimpleTrigger` (if Quartz is on the classpath) | the trigger's cron and zone, or its interval | `<group>.<name>` |
| ShedLock `@SchedulerLock` (if ShedLock is on the classpath) | as above | as above |

Each run is reported as `start`, then `success` or `fail`. A failure carries the exception class,
its message and the top stack frames. The exception still propagates to Spring exactly as before.

With ShedLock, a run skipped because another node holds the lock reports nothing: the node that
runs the job reports it, so the check does not go down.

### Stable names

Slugs come from bean and method names, so renaming a method creates a new check. To keep a name:

```java
@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Ho_Chi_Minh")
@CronbellCheck("billing-close")
void closeMonth() { … }
```

`@CronbellCheck` also takes `name`, `grace` (e.g. `"15m"`), `maxRuntime` and `enabled = false` to
leave a job out.

## Guarantees

**Monitoring must never slow down or break a job.**

- Pings are sent from one background thread through a bounded queue. The job thread only enqueues.
- If Cronbell is slow or unreachable, pings are dropped and a warning is logged at most once a
  minute: `Cronbell: cannot reach … dropping pings. Jobs run as usual.`
- If registration fails at startup, the application starts anyway and retries in the background.
- Optional libraries (Actuator, Quartz, ShedLock) are only touched when they are on the classpath.
  A test runs a plain `spring-boot-starter` application without them on every build.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `cronbell.url` | – | Base URL of the Cronbell server. Required. |
| `cronbell.api-key` | – | Project API key (`cb_…`). Required. Keep it out of source control. |
| `cronbell.enabled` | `true` | Turn monitoring off without removing the dependency. |
| `cronbell.environment` | – | Prefix for every slug, e.g. `prod` → `prod.report-job.send-daily`, so environments do not share checks. |
| `cronbell.include` | all | Glob patterns (`*`, `?`) of slugs to monitor, without the environment prefix. |
| `cronbell.exclude` | none | Glob patterns of slugs to leave out. Wins over `include`. |
| `cronbell.grace` | server default | How late a run may be before its check goes down. |
| `cronbell.register` | `true` | Create or update the checks at startup. When `false`, they must already exist. |
| `cronbell.queue-capacity` | `1000` | Pings waiting to be sent; more are dropped. |
| `cronbell.timeout.connect` / `.read` | `5s` / `10s` | HTTP timeouts. |

With Actuator, `/actuator/info` gets a `cronbell` entry: server, number of checks, pings sent and
dropped, and the last error.

## Compatibility

| | Tested |
|---|---|
| Java | 17, 21, 25 (compiled for 17) |
| Spring Boot | 3.3, 3.5, 4.1 |
| Schedulers | `ThreadPoolTaskScheduler`, `SimpleAsyncTaskScheduler` (virtual threads) |

## Try it

[`samples/spring-boot-demo`](samples/spring-boot-demo) has three jobs: a cron job in
`Asia/Ho_Chi_Minh`, a `fixedRate` job with a stable name, and one that fails. Run a Cronbell server,
then:

```bash
cd samples/spring-boot-demo
CRONBELL_URL=http://localhost:8080 CRONBELL_API_KEY=cb_... ../../mvnw spring-boot:run
```

## Build

```bash
./mvnw verify               # Spring Boot 3.5 (default)
./mvnw verify -Pboot33      # Spring Boot 3.3
./mvnw verify -Pboot4       # Spring Boot 4.1
```

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[Apache-2.0](LICENSE). The Cronbell server is AGPL-3.0; this client library is Apache-2.0 so it
never affects your application's licence.

Made by [Steelreed](https://steelreed.com), open-source tools for the parts of your backend nobody
is watching.
