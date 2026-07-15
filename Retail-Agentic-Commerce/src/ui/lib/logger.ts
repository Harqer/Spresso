import * as Sentry from "@sentry/nextjs";

type LogLevel = "debug" | "info" | "warn" | "error";

export const logger = {
  debug: (message: string, ...args: any[]) => {
    if (process.env.NODE_ENV === "development") {
      // eslint-disable-next-line no-console
      console.log(`[DEBUG] ${message}`, ...args);
    }
  },
  info: (message: string, ...args: any[]) => {
    if (process.env.NODE_ENV === "development") {
      // eslint-disable-next-line no-console
      console.log(`[INFO] ${message}`, ...args);
    } else {
      Sentry.addBreadcrumb({
        category: "log",
        message: message,
        level: "info",
        data: args.length > 0 ? { args } : undefined,
      });
    }
  },
  warn: (message: string, ...args: any[]) => {
    if (process.env.NODE_ENV === "development") {
      // eslint-disable-next-line no-console
      console.warn(`[WARN] ${message}`, ...args);
    }
    Sentry.captureMessage(message, {
      level: "warning",
      extra: args.length > 0 ? { args } : undefined,
    });
  },
  error: (message: string, error?: any, ...args: any[]) => {
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
