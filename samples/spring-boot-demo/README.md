# Cronbell demo

A plain Spring Boot application with three `@Scheduled` jobs and the Cronbell starter. It is not
part of the starter's build.

```bash
# 1. a Cronbell server (from the cronbell repository: make docker)
docker run -d -p 8080:8080 -e CRONBELL_API_KEY=cb_demo_key_0123456789abcdef ghcr.io/steelreed/cronbell:dev
# 2. the starter in your local Maven repository
(cd ../.. && ./mvnw -q install -DskipTests)
# 3. this app
CRONBELL_URL=http://localhost:8080 CRONBELL_API_KEY=cb_demo_key_0123456789abcdef ../../mvnw spring-boot:run
```

Within a minute Cronbell shows three checks: `jobs.send-reminders` (cron, Asia/Ho_Chi_Minh, UP),
`queue-drain` (named with `@CronbellCheck`, UP) and `jobs.sync-invoices` (DOWN, failed). Stop the
server and the jobs keep running; the app logs one warning a minute.
