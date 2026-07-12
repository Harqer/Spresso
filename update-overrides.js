const fs = require('fs');
const pkg = JSON.parse(fs.readFileSync('./package.json', 'utf8'));
if (!pkg.pnpm) pkg.pnpm = {};
pkg.pnpm.overrides = {
  ...pkg.pnpm.overrides,
  "next": "^15.2.7",
  "postcss": "^8.5.10",
  "esbuild": "^0.28.1"
};
fs.writeFileSync('./package.json', JSON.stringify(pkg, null, 2) + '\n');
