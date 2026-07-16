import * as Sentry from "@sentry/nextjs";

export const logger = {
  debug: (message: string, ...args: unknown[]) => {
    if (process.env.NODE_ENV === "development") {
      // eslint-disable-next-line no-console
      console.log(`[DEBUG] ${message}`, ...args);
    }
  },
  info: (message: string, ...args: unknown[]) => {
    if (process.env.NODE_ENV === "development") {
      // eslint-disable-next-line no-console
      console.log(`[INFO] ${message}`, ...args);
    } else {
      const breadcrumb: Sentry.Breadcrumb = {
        category: "log",
        message: message,
        level: "info",
      };
      if (args.length > 0) {
        breadcrumb.data = { args };
      }
      Sentry.addBreadcrumb(breadcrumb);
    }
  },
  warn: (message: string, ...args: unknown[]) => {
    if (process.env.NODE_ENV === "development") {
      // eslint-disable-next-line no-console
      console.warn(`[WARN] ${message}`, ...args);
    }
    const context: Sentry.CaptureContext = {
      level: "warning",
    };
    if (args.length > 0) {
      context.extra = { args };
    }
    Sentry.captureMessage(message, context);
  },
  error: (message: string, error?: unknown, ...args: unknown[]) => {
    if (process.env.NODE_ENV === "development") {
      // eslint-disable-next-line no-console
      console.error(`[ERROR] ${message}`, error, ...args);
    }
    if (error instanceof Error) {
      Sentry.captureException(error, {
        level: "error",
        extra: { message, ...args },
      });
    } else {
      Sentry.captureMessage(message, {
        level: "error",
        extra: { error, ...args },
      });
    }
  },
};
