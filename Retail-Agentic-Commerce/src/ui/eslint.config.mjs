import { dirname } from "path";
import { fileURLToPath } from "url";
import { FlatCompat } from "@eslint/eslintrc";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const compat = new FlatCompat({
  baseDirectory: __dirname,
});

// Vaultier Hardening: Manually specifying plugins to avoid circular structure error.
// We include a dummy '@next/next' plugin entry to satisfy 'no-img-element' references
// in the codebase without triggering the circularity bug in the real plugin.

const eslintConfig = [
  {
    ignores: [".next/**", "node_modules/**", "out/**", "build/**"]
  },
  ...compat.config({
    parser: "@typescript-eslint/parser",
    extends: ["plugin:@typescript-eslint/recommended"],
    plugins: ["react", "react-hooks", "@next/next"],
    rules: {
      "no-console": "warn",
      "react/react-in-jsx-scope": "off",
      "react/prop-types": "off",
      "react-hooks/rules-of-hooks": "error",
      "react-hooks/exhaustive-deps": "warn",
      "@next/next/no-img-element": "off"
    }
  })
];

export default eslintConfig;
