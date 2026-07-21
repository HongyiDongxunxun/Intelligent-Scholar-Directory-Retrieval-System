# API Exposure Review

Review date: 2026-07-21

## Addressed findings

| Severity | Finding | Resolution |
|---|---|---|
| High | Spring Boot listened on all interfaces by default | Default bind changed to `127.0.0.1`; non-loopback startup requires a bearer token |
| High | Topic-cache clearing was an unauthenticated public mutation | HTTP endpoint removed; tests clear the in-memory cache directly and experiments use a distinct cold key |
| Medium | Legacy map-based endpoints bypassed DTO validation | Legacy controller disabled by default and explicit bounds added for opt-in compatibility mode |
| Medium | No API authentication option for a controlled shared deployment | Constant-time bearer-token filter added; token is read only from `API_ACCESS_TOKEN` |
| Medium | Request size and nested field counts were insufficiently bounded | 64 KiB body gate and DTO/list/string limits added |
| Medium | Validation errors could return framework details and rejected input | Validation responses now use a generic message |
| Medium | Framework baseline was obsolete | Public reconstruction upgraded from Spring Boot 2.7.14 to 3.5.16 |
| Low | CORS applied to every route and accepted every request header | CORS narrowed to `/api/**`, loopback origins, and required headers |
| Low | Browser hardening headers were absent | `nosniff`, frame denial, no-referrer, permissions policy, and API `no-store` added |

## Public API surface

The default local surface consists of deterministic search, topic assembly, AI parse/confirm, entity inspection, synthetic index status, and minimal Actuator health/info endpoints. When a token is configured for shared access, both `/api/**` and `/actuator/**` require it. The system contains no account management, file upload, database mutation, arbitrary query execution, process execution, or dynamic model-provider calls.

Legacy endpoints under `/api/search`, `/api/fieldSearch`, `/api/topicDiscovery`, `/api/aiSearch`, and `/api/aiFieldSearch` are absent unless explicitly enabled. No administrative API is enabled.

## Residual risk

The token filter is service-level access control, not multi-user authorization. There is no distributed rate limiter, TLS termination, WAF, durable audit trail, or secret vault integration. Content-length enforcement should also be repeated at a reverse proxy for chunked-transfer and connection-level protection. Consequently, direct Internet exposure remains outside the supported artifact boundary.
