# Vaultier Ecosystem: Hardening & Architectural Audit

This document summarizes the current security, performance, and architectural status of the Vaultier ecosystem, following the integration of industrial Redis caching.

## 1. Infrastructure: Redis Caching
Standard Redis has been integrated into the Merchant API using the provided high-fidelity instance.

- **Status**: **ACTIVE**
- **Implementation**: Singleton client with connection pooling and an automated `@cached` decorator.
- **Pulse**: Currently protecting `get_dashboard_metrics` (90s TTL) and ready for product search results.

## 2. Codebase Audit: Mocks, Placeholders, and Stubs
A project-wide audit identified the following non-production artifacts that must be remediated before publishing.

### Backend (Python)
| Artifact | Location | Impact |
| :--- | :--- | :--- |
| `pi_simulated_token` | `test_checkout.py` | Acceptable (Test only) |
| `InternalSecurityMiddleware` | `middleware/security.py` | Fail-open design (Verify production risk) |

### Frontend (Next.js/Vite)
| Artifact | Location | Impact |
| :--- | :--- | :--- |
| `FALLBACK_PRODUCTS` | `constants.ts` | High (UI shows "Grounded Discovery" if API blips) |
| `Analyzing image...` | `ChatDiscovery.tsx` | Low (UI Placeholder) |

### Android (Mobile)
| Artifact | Location | Impact |
| :--- | :--- | :--- |
| **`pi_simulated_token`** | `RetailMobileApp.kt` | **CRITICAL** (Checkout does not use real payment pulse) |
| `GeminiNanoBanana2` | `util/` | High (Simulated local reasoning engine) |
| `web_` Timestamp IDs | `ProductRepository.kt` | Medium (Client-side ID generation) |
| `Sign In with Google` | `MainActivity.kt` | Medium (Currently a UI-only placeholder) |

## 3. Android Performance Analysis (R8/Proguard)
Based on a heuristic analysis of `proguard-rules.pro` against AGP 9 standards.

- **Optimization Score**: 65/100
- **Findings**:
    - **Over-Preservation**: Rules like `-keep class com.google.firebase.** { *; }` are too broad, increasing APK size and leaking metadata.
    - **Redundancy**: Manual rules for Compose and Coroutines are now handled natively by AGP 9 and Compose Compiler.
- **Action**: Refine to targeted keep rules for the Meta Wearables SDK only.

## 4. Architectural Analysis: IoC & DI
Evaluation of Inversion of Control and Dependency Injection patterns.

### Android
- **Status**: **INCOMPLETE**
- **Issue**: Manual dependency management in `MainActivity` and Repository layers.
- **Recommendation**: Implement **Hilt** to inject `ProductRepository` and `RetailSessionManager`. This ensures a single source of truth for app state and simplifies biometric auth flows.

### Merchant API
- **Status**: **ROBUST**
- **Pattern**: Effectively uses FastAPI's `Depends` for IoC. Redis and Database sessions are correctly injected.

## 5. Security Hardening Roadmap
1.  **Biometric Pulse**: Replace `pi_simulated_token` with real encrypted Stripe/Payment payload.
2.  **Identity Bridge**: Finalize Firebase/Google Sign-In integration in `MainActivity`.
3.  **Certificate Pinning**: Implement for the Merchant API domain to prevent MITM.
4.  **R8 Refinement**: Shrink the APK by 20-30% by removing redundant Firebase/Compose keep rules.
