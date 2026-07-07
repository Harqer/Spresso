import * as Sentry from "@sentry/nextjs";

Sentry.init({
  dsn: process.env.NEXT_PUBLIC_SENTRY_DSN || "https://REDACTED_SENTRY_KEY@o***REDACTED_SENTRY_ORG_ID***.ingest.us.sentry.io/***REDACTED_SENTRY_ID***",
  tracesSampleRate: 1.0,
  debug: false,
  replaysOnErrorSampleRate: 1.0,
  replaysSessionSampleRate: 0.1,
  environment: process.env.NEXT_PUBLIC_ENV || "production",
  integrations: [
    Sentry.replayIntegration({
      maskAllText: true,
      blockAllMedia: true,
    }),
  ],
});
