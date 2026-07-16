# Spresso Retail: Coding Standards & Production Readiness
*Last Updated: July 2026*

This document serves as the authoritative guide for coding standards, security guardrails, and production readiness for the Spresso Retail monorepo.

---

# JavaScript/TypeScript Coding Standards

> Philosophy: consistency beats cleverness. These rules exist so you don't have to re-decide the same thing every time you open the editor. If a rule ever fights the goal of shipping working code, the goal wins — update the doc instead of arguing with yourself.

## 1. Tooling (set once, then forget)

| Tool | Purpose | Config |
|---|---|---|
| **TypeScript** | Type safety | `strict: true` in `tsconfig.json`, no exceptions |
| **ESLint** | Linting | `@typescript-eslint/recommended` + `eslint-plugin-import` |
| **Prettier** | Formatting | Defaults, 2-space indent, semicolons on, single quotes |
| **Vitest / Jest** | Testing | Pick one, don't mix |
| **Husky + lint-staged** | Pre-commit hooks | Run lint + format on staged files only |

**Rule:** Formatting is never a judgment call — Prettier decides, you don't argue with it. If Prettier and your instinct disagree, Prettier wins; change the config once if it's really wrong, don't hand-fix output.

## 2. TypeScript specifics

- **`strict: true`** always. No `any` unless it's genuinely unknown external data — and even then, prefer `unknown` + narrowing.
- **Types vs Interfaces:** use `interface` for object shapes that might be extended (props, entities); use `type` for unions, tuples, and utility compositions. Don't agonize past that.
- **No implicit `any`** — if TS can't infer it, annotate it.
- **Avoid `enum`** — prefer `as const` object maps or union string literals; they're more tree-shakeable and easier to serialize.
- **Null vs undefined:** pick `undefined` for "not set yet" and reserve `null` for "explicitly empty" (e.g., API returned no value). Don't use both interchangeably.
- **Non-null assertion (`!`)** is a last resort, not a shortcut — if you need it more than rarely, the types are wrong somewhere upstream.

## 3. Naming conventions

| What | Convention | Example |
|---|---|---|
| Variables, functions | `camelCase` | `getUserById` |
| Classes, types, interfaces | `PascalCase` | `UserProfile` |
| Constants (true constants) | `UPPER_SNAKE_CASE` | `MAX_RETRIES` |
| Files (non-component) | `kebab-case.ts` | `user-service.ts` |
| React components | `PascalCase.tsx` | `UserCard.tsx` |
| Booleans | prefix `is/has/should/can` | `isLoading`, `hasError` |
| Private class members | `#field` (real private) | `#cache` |

**Rule of thumb:** name things for what they *are*, not how they're implemented. `activeUsers` not `filteredArr2`.

## 4. File & folder structure

```
src/
  features/            # group by feature, not by file type
    auth/
      auth.service.ts
      auth.types.ts
      login-form.tsx
      auth.test.ts
    users/
  shared/
    components/
    hooks/
    utils/
  lib/                  # third-party wrappers/config (e.g. api client)
```

- **Colocate tests** next to the file they test (`thing.ts` + `thing.test.ts`), not in a mirrored `__tests__` tree.
- **One default export per file max**, and prefer named exports overall — easier to refactor and grep.
- **Barrel files (`index.ts`)** only at feature boundaries, not everywhere — they slow down builds and obscure import paths if overused.

## 5. Function & code style

- **Functions do one thing.** If you need "and" to describe it, split it.
- **Prefer pure functions** where possible — same input, same output, no hidden state mutation.
- **Early returns over nested conditionals:**
  ```ts
  // Good
  function process(user?: User) {
    if (!user) return null;
    if (!user.isActive) return null;
    return doWork(user);
  }
  ```
- **Arrow functions** for callbacks/inline logic; **named function declarations** for top-level functions (better stack traces, hoisting is fine here).
- **No magic numbers/strings** — extract to a named constant if it means something.
- **Max function length ~40 lines** as a smell-detector, not a hard rule — if you blow past it, ask whether it should be two functions.

## 6. Async & error handling

- **Always `async/await`** over raw `.then()` chains.
- **Never swallow errors silently.** A bare `catch {}` is a bug waiting to happen — at minimum log it, ideally handle or rethrow with context.
- **Custom error classes** for domain errors so callers can `instanceof` check:
  ```ts
  class NotFoundError extends Error {
    constructor(resource: string) {
      super(`${resource} not found`);
      this.name = 'NotFoundError';
    }
  }
  ```
- **Wrap external calls** (fetch, DB, third-party SDKs) in a try/catch at the boundary — don't let raw network errors leak into UI code.

## 7. Imports

Order, top to bottom, blank line between groups:
1. Node/external packages (`react`, `zod`, etc.)
2. Internal absolute imports (`@/features/...`)
3. Relative imports (`./`, `../`)
4. Types (can mix in or use `import type` explicitly — be consistent)

Use `import type { Foo } from './foo'` for type-only imports so they're erased at build time.

## 8. Comments & documentation

- **Comment the *why*, not the *what*.** Code already says what it does; comments explain the non-obvious reasoning, trade-off, or gotcha.
- **JSDoc on exported functions** that aren't self-explanatory from name + types alone — skip it for trivial getters.
- **No commented-out code** left in commits — delete it, git remembers.
- **TODO comments** must include context: `// TODO(you): revisit once API v2 ships pagination`

## 9. Git & commits

- **Conventional commits:** `feat:`, `fix:`, `refactor:`, `chore:`, `docs:`, `test:`
- **One logical change per commit.** Small, revertible, readable in `git log`.
- **Present tense, imperative mood:** `fix login redirect bug`, not `fixed` or `fixes`.
- **Never commit** `.env`, secrets, or `node_modules` — `.gitignore` set up front.

## 10. Testing

- **Test behavior, not implementation.** If a refactor with the same output breaks your tests, the tests were too coupled.
- **Naming:** `describe('UserService')` → `it('throws NotFoundError when user does not exist')` — reads like a sentence.
- **Coverage isn't the goal** — meaningful coverage of edge cases and error paths is. 100% coverage of trivial getters is wasted effort.
- **Mock at the boundary** (network, filesystem, time) — not your own internal logic.

## 11. Quick pre-commit checklist

- [ ] `tsc --noEmit` passes with no errors
- [ ] ESLint passes with no warnings ignored
- [ ] Prettier formatted (should be automatic via hook)
- [ ] No `console.log` left in (use a real logger or remove)
- [ ] No commented-out code
- [ ] Tests pass locally
- [ ] Commit message follows convention

## 12. When to break these rules

These are defaults, not laws. Break a rule when:
- Following it would make the code *harder* to understand, not easier
- You're prototyping and will clean up before merging (say so in the commit)
- A third-party library forces a different pattern

Write a one-line comment explaining the deviation so future-you isn't confused.

---

# Production Readiness Checklist
*Android • Firebase • Gemini Lyria • Meta Wearables (camera + real-time voice) • Play Store*

## 1. Security
- [ ] **No API keys in the client.** GEMINI_API_KEY, any Meta/third-party keys — none should be bundled in the APK.
- [ ] **Firebase App Check enabled** — prevents unauthorized clients.
- [ ] **Firestore/RTDB security rules reviewed line-by-line** — default-deny.
- [ ] **Auth tokens refreshed properly** — verify `onIdTokenChanged`.
- [ ] **Camera/mic data in transit is encrypted** (WSS/TLS).
- [ ] **No raw camera frames or audio persisted** unless required.
- [ ] **PII minimization**.
- [ ] **Rate limit Cloud Function endpoints**.
- [ ] **Meta Wearables permission flow** uses "Allow once" vs "Allow always" correctly.
- [ ] **Secrets in Cloud Functions use Firebase Secret Manager**.

## 2. Database schema (Firestore/RTDB)
- [ ] **No unbounded arrays in documents**.
- [ ] **Avoid hot documents**.
- [ ] **Composite indexes created**.
- [ ] **Real-time listeners are scoped tightly**.
- [ ] **Session/vibe history modeled as its own collection**.
- [ ] **RTDB vs Firestore choice matches the workload**.
- [ ] **TTL / cleanup policy** for ephemeral data.
- [ ] **Backups configured**.

## 3. Real-time voice & camera streaming
- [ ] **Reconnect logic tested**.
- [ ] **Jitter buffer / backpressure handling** for audio.
- [ ] **Meta Wearables session lifecycle handled for all states**.
- [ ] **Battery impact tested** on real hardware.
- [ ] **Background/foreground service behavior** correctly declared.
- [ ] **Graceful degradation** if Gemini Lyria is slow.

## 4. Caching
- [ ] **Cache generated tracks by input signature**.
- [ ] **CDN/Firebase Hosting cache headers set correctly**.
- [ ] **Client-side memory cache for recently generated tracks**.
- [ ] **Cache invalidation policy is explicit**.
- [ ] **Firestore read caching** using `cache-first` settings.

## 5. Batching
- [ ] **Camera frames batched/throttled before inference**.
- [ ] **Firestore writes batched** using `WriteBatch`.
- [ ] **Analytics/usage events batched and flushed periodically**.
- [ ] **Gemini API calls batched or debounced**.

## 6. Cost effectiveness
- [ ] **Per-user and per-day quota/rate limits** enforced server-side.
- [ ] **Budget alerts set** in console.
- [ ] **Firestore read/write cost modeled**.
- [ ] **Cloud Functions cold start / invocation cost checked**.
- [ ] **Egress bandwidth accounted for**.
- [ ] **Fallback/degraded mode defined** for when quotas are hit.

## 7. Observability & monitoring
- [ ] **Crashlytics integrated**.
- [ ] **Structured logging** on Cloud Functions.
- [ ] **Alerting on Gemini API error rate**.
- [ ] **Session success rate tracked**.

## 8. Security/permissions review specific to Meta Wearables
- [ ] **Production registration completed** with the Meta AI app.
- [ ] **Permission rationale strings are clear and honest**.
- [ ] **Tested with real hardware** before submission.

## 9. Play Store submission readiness
- [ ] **Privacy Policy published and linked**.
- [ ] **Data Safety form completed accurately**.
- [ ] **Sensitive permissions justified**.
- [ ] **Target API level meets Play Store current minimum**.
- [ ] **Content rating questionnaire completed**.
- [ ] **Bystander privacy addressed**.
- [ ] **Foreground service declaration matches actual behavior**.
- [ ] **Staged rollout percentage set**.

## 10. Final go/no-go checklist
- [ ] All security checks passed.
- [ ] Cost alerts live and tested.
- [ ] Privacy Policy + Data Safety form consistent.
- [ ] Full real-device test successful.
- [ ] rollback plan exists.
