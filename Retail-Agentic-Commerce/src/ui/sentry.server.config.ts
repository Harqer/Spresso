import * as Sentry from "@sentry/nextjs";

Sentry.init({
  dsn: process.env.SENTRY_DSN || "https://f91178c0ccc03bba73f7ca1a824ae463@o4511300849631232.ingest.us.sentry.io/4511300862345216",
  tracesSampleRate: 1.0,
  debug: false,
  environment: process.env.ENV || "production",
});
