const fs = require('fs');
const path = './Retail-Agentic-Commerce/package.json';
const pkg = JSON.parse(fs.readFileSync(path, 'utf8'));
if (!pkg.pnpm) pkg.pnpm = {};
pkg.pnpm.overrides = {
  ...pkg.pnpm.overrides,
  "postcss": "^8.5.10",
  "esbuild": "^0.28.1"
};
fs.writeFileSync(path, JSON.stringify(pkg, null, 2) + '\n');
