# Spresso Architectural Alignment Checklist

This checklist is used to evaluate project development against core Design Principles and Spresso's specific project goals. It is intended for both human architects and LLM-driven automated audits.

## 1. Design for Scalability
- [ ] **On-Device Offloading**: Does the feature leverage Gemini Nano (Banana 2) for local visual reasoning before hitting server APIs?
- [ ] **Stateless Backend**: Are all FastAPI endpoints in `Merchant` and `PSP` services stateless to allow horizontal scaling?
- [ ] **Connection Pooling**: Is the Neon Postgres connection managed via pooling (e.g., PgBouncer) for high-concurrency?
- [ ] **Vector Elasticity**: Can the Milvus vector index scale to millions of product SKUs without O(n) latency growth?

## 2. Design for Resiliency
- [ ] **NAT Failover**: If a specific NAT Agent (e.g., Promotion) fails, is there a deterministic fallback (e.g., 0% discount)?
- [ ] **Circuit Breakers**: Are calls to external services like Stripe or Higgsfield wrapped in circuit breakers with appropriate timeouts?
- [ ] **Autonomic Repair**: Does the CI/CD pipeline include self-healing scripts for environment drift or linting errors?
- [ ] **Redundant Gateway**: Is the Go Gateway configured for high availability across multiple availability zones?

## 3. Design for Efficiency
- [ ] **Edge Acceleration**: Are all static assets and try-on models served via Cloudflare Edge with optimized TTLs?
- [ ] **Adaptive Sampling**: Does the Go Gateway implement adaptive frame sampling (e.g., 2fps) to reduce multimodal token costs?
- [ ] **Payload Optimization**: Are API responses filtered to return only necessary fields for the specific platform (Mobile vs. Web)?
- [ ] **Cache Coherence**: Are semantic search results cached in Milvus/Redis with invalidation triggers on SKU updates?

## 4. Design for Disaster Recovery
- [ ] **Immutable Infrastructure**: Are all services defined as Infrastructure as Code (IaC) via Wrangler, Docker, or Terraform?
- [ ] **Point-in-Time Recovery**: Is Neon DB configured for PITR, and are backup restoration tests performed monthly?
- [ ] **Mirror Integrity**: Does the GitLab CI successfully mirror the repository and tags for multi-cloud redundancy?
- [ ] **Rapid Scaffolding**: Can the entire backend stack be stood up in a fresh environment in under 15 minutes?

## 5. Design for Modularity
- [ ] **Domain Isolation**: Is there a strict separation between the `Merchant API`, `PSP Service`, and `NAT Agents`?
- [ ] **Protocol Decoupling**: Is the core business logic independent of the transport layer (REST for ACP, JSON-RPC for UCP)?
- [ ] **Modular Actions**: Does the CI/CD use reusable GitHub/GitLab Actions instead of monolithic script blocks?
- [ ] **Interchangeable Models**: Can the LLM reasoning layer switch from Gemini Flash to a local NIM without code changes?

## 6. Design for Interoperability
- [ ] **ACP/UCP Compliance**: Do the session and order endpoints strictly follow the Agentic Commerce Protocol specifications?
- [ ] **A2A JSON-RPC**: Does the `a2a` endpoint correctly handle cross-agent messaging and task status updates?
- [ ] **OpenAPI Spec**: Is there an up-to-date OpenAPI schema for all services to facilitate third-party agent integration?
- [ ] **Standard Data Formats**: Are ISO 8601 for dates and minor units (cents) for currency used across the entire stack?

## 7. Design for Observability
- [ ] **Distributed Tracing**: Does every request carry a `Request-Id` that propagates from Frontend → Gateway → Backend → Agents?
- [ ] **Identity Pulse**: Are all authentication events and vault interactions logged to Sentry with user context?
- [ ] **Agent Activity Panels**: Can the internal protocol inspector visualize real-time agent reasoning steps and tool calls?
- [ ] **Performance KPI Metrics**: Are latency, token usage, and conversion rates tracked per agent interaction?

## 8. Design for Simplicity
- [ ] **Single Responsibility**: Does each microservice and NAT Agent have one clearly defined goal?
- [ ] **Zero-Mock Verification**: Does the testing strategy favor integration with real dependencies over complex mocking?
- [ ] **Minimal Glimmer UI**: Is the projected Android UI free of unnecessary animations and complex hierarchies?
- [ ] **Clear Intent Detection**: Is the prompt planning logic simple enough to be debugged by a human without specialized tools?

---

## Spresso Project Specific Goals
- [ ] **Meta Wearables DAT Pulse**: Verified identity registration and authentication via DAT SDK.
- [ ] **Glimmer UI Layout**: Jetpack Compose Glimmer components meet display glasses accessibility standards (contrast, font size).
- [ ] **Higgsfield Motion Fit**: Virtual try-on generation output verified for spatial accuracy and lighting consistency.
- [ ] **Industrial Secret Injection**: Secrets are exclusively managed via Infisical and never stored in `.env` or source.
