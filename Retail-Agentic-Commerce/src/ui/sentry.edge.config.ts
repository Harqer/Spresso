import * as Sentry from "@sentry/nextjs";

Sentry.init({
  dsn: process.env.SENTRY_DSN || "https://REDACTED_SENTRY_KEY@o***REDACTED_SENTRY_ORG_ID***.ingest.us.sentry.io/***REDACTED_SENTRY_ID***",
  tracesSampleRate: 1.0,
  debug: false,
  environment: process.env.ENV || "production",
});
