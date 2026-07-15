import type { Metadata } from "next";
import { ThemeProvider } from "@kui/foundations-react-external";
import { AuthProvider } from "@/components/AuthProvider";
import { PHProvider } from "./providers/posthog";
import "./globals.css";

export const metadata: Metadata = {
  title: "Spresso | A smarter way to shop.",
  description: "Industrial Client Agent Simulator for Spresso Protocol",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="en"
      className="nv-dark"
      style={{ backgroundColor: "#0c0c0c" }}
      suppressHydrationWarning
    >
      <body style={{ backgroundColor: "var(--background-color-surface-base)" }}>
        <PHProvider>
          <AuthProvider>
            <ThemeProvider theme="dark" density="standard" global target="html">
              {children}
            </ThemeProvider>
          </AuthProvider>
        </PHProvider>
      </body>
    </html>
  );
}
