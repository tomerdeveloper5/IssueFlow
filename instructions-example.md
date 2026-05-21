---
name: Richer API Error Responses
overview: Standardize and enrich error payloads across the API so clients get clearer explanations, including security failures that currently return default responses.
todos:
  - id: define-error-schema
    content: Define enriched error response contract (codes + explanation fields) in ApiErrorResponse
    status: completed
  - id: update-global-handler
    content: Refactor GlobalExceptionHandler mappings to populate the richer payload consistently
    status: completed
  - id: wire-security-errors
    content: Add and configure JSON AuthenticationEntryPoint/AccessDeniedHandler in SecurityConfig
    status: completed
  - id: add-tests
    content: Add/adjust integration tests for 401/403/400/404 enriched responses
    status: completed
isProject: false
---

# Add richer API error explanations

## Goal
Return consistent, more descriptive error bodies for all API failures (validation, not found, conflict, bad request, auth/authz, unexpected), while keeping status codes correct.

## Proposed changes
- Extend the error DTO in [`c:/studies/coding/issueflow-java/src/main/java/com/att/tdp/issueflow/api/dto/ApiErrorResponse.java`](c:/studies/coding/issueflow-java/src/main/java/com/att/tdp/issueflow/api/dto/ApiErrorResponse.java) to include clearer explanatory fields (for example: `errorCode` and a client-friendly `explanation`), while preserving current fields used by clients.
- Update [`c:/studies/coding/issueflow-java/src/main/java/com/att/tdp/issueflow/api/GlobalExceptionHandler.java`](c:/studies/coding/issueflow-java/src/main/java/com/att/tdp/issueflow/api/GlobalExceptionHandler.java) so every mapped exception returns the richer payload with:
  - stable, machine-readable code (e.g., `AUTH_INVALID_TOKEN`, `VALIDATION_FAILED`, `RESOURCE_NOT_FOUND`)
  - concise human explanation and actionable hint
  - validation errors map when relevant
- Add explicit Spring Security JSON handlers for authentication/authorization failures (new classes under `security`) and wire them in [`c:/studies/coding/issueflow-java/src/main/java/com/att/tdp/issueflow/security/SecurityConfig.java`](c:/studies/coding/issueflow-java/src/main/java/com/att/tdp/issueflow/security/SecurityConfig.java) via `exceptionHandling(...)` so invalid/missing/expired token paths also return the same enriched schema.
- Keep semantic status behavior:
  - `401` for unauthenticated/invalid token
  - `403` for authenticated but forbidden
  - existing statuses for domain/validation/server errors

## Verification
- Add/update integration tests to assert both status and enriched body shape/messages for representative cases (401, 403, 404, 400 validation).
- Manually verify in Postman that all error responses share one schema and include meaningful explanation text.

## Notes
- Maintain backward compatibility as much as possible by retaining existing fields (`status`, `error`, `message`, `path`, etc.) and adding new fields rather than replacing immediately.