import { withSentryConfig } from "@sentry/nextjs";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Industrial Monorepo Support: Transpile local workspace packages
  transpilePackages: ["@kui/foundations-react-external", "@kui/foundations-design-tokens"],
  experimental: {
    clientTraceMetadata: ["x-genkit-trace-id"], // Propagate Genkit traces to client
  },
  // Enable standalone output for Docker deployment
  output: "standalone",
  images: {
    remotePatterns: [
      {
        protocol: "https",
        hostname: "placehold.co",
      },
      {
        protocol: "https",
        hostname: "images.unsplash.com",
      },
    ],
  },
};

export default withSentryConfig(nextConfig, {
  // For all available options, see:
  // https://github.com/getsentry/sentry-webpack-plugin#options

  org: "vaultier-retail",
  project: "spresso-5561f",

  // Only print logs for uploading source maps in CI
  silent: !process.env.CI,

  // For all available options, see:
  // https://docs.sentry.io/platforms/javascript/guides/nextjs/manual-setup/

  // Upload a larger set of source maps for better stack traces (increases build time)
  widenClientFileUpload: true,

  // Route browser requests to Sentry through a Next.js rewrite to circumvent ad-blockers.
  // See https://docs.sentry.io/platforms/javascript/guides/nextjs/manual-setup/#tunnel-browser-requests-to-sentry
  tunnelRoute: "/monitoring",
});
