# Security Policy

## Supported artifact

Security fixes are applied to the current `main` branch. The reconstruction is intended for local academic review and controlled demonstrations, not direct Internet-facing production deployment.

## Reporting a vulnerability

Do not place credentials, private database material, identity mappings, exploit payloads, or unpublished personal data in a public GitHub issue. Report sensitive findings privately through GitHub's private vulnerability-reporting channel when available, or contact the corresponding authors through the institutional contact information in the associated paper.

Include the affected commit, endpoint, reproduction conditions, impact, and the minimum evidence needed to confirm the issue. Do not include confidential source records.

## Secure defaults

- The backend binds to `127.0.0.1` by default.
- A non-loopback bind fails at startup unless `API_ACCESS_TOKEN` is set.
- When configured, the token is required as `Authorization: Bearer <token>` for `/api/**`.
- When configured, the same token also protects `/actuator/**`.
- Legacy compatibility endpoints are disabled unless `LEGACY_API_ENABLED=true`.
- Administrative cache-clearing HTTP endpoints are not published.
- API request bodies are limited to 64 KiB by default; DTO collections and text fields have explicit bounds.
- Actuator exposes only `health` and `info`, with health details disabled.
- Error responses suppress stack traces, binding details, and raw validation values.
- CORS is limited to loopback frontend origins.

## Shared deployment

For a controlled shared demonstration, use a TLS reverse proxy, generate a high-entropy API token, keep Actuator inaccessible from untrusted networks, apply proxy-level rate and body-size limits, and avoid embedding bearer tokens in a public frontend bundle. The application does not provide user accounts, authorization roles, audit retention, distributed rate limiting, or production-grade secret management.

## Private materials

The original database schema, environment-specific configurations, and private build descriptor are stored only in the authenticated encrypted bundle under `private-materials/`. Its password is never committed. Public build files are sanitized reproducibility templates and contain no operational credentials.
