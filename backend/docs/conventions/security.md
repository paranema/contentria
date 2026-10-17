# Security Conventions

This document defines security practices for the Contentria backend. These rules apply to authentication, cookie handling, token management, and data protection.

## Authentication Architecture

### Token Strategy

| Token | Type | Storage | Expiry | Validation |
|-------|------|---------|--------|-----------|
| Access Token | JWT (signed) | HttpOnly cookie + Authorization header | 15 minutes | Stateless (signature check) |
| Refresh Token | Opaque UUID | HttpOnly cookie + DB | 7 days | Stateful (DB lookup) |

### Why Two Token Types?

- **JWT for access**: No DB lookup required per request. Fast validation via signature. Short-lived to limit damage if leaked.
- **Opaque for refresh**: Stored in DB, enabling server-side revocation. Can be rotated on each use (Refresh Token Rotation) to detect token theft.

### Refresh Token Rotation (RTR)

On every token refresh:
1. Validate the old refresh token against DB
2. Delete/update the old token
3. Issue a new refresh token
4. Return both new access + refresh tokens

If an attacker uses a stolen refresh token after the legitimate user has already rotated it, the token won't be found in DB, and the refresh fails. This limits the window of token theft.

## Cookie Security

### Required Attributes

Every authentication cookie **must** have:

| Attribute | Value | Why |
|-----------|-------|-----|
| `HttpOnly` | `true` | Prevents JavaScript access. Mitigates XSS-based token theft |
| `Secure` | `true` (in production) | Cookie only sent over HTTPS. Prevents interception over HTTP |
| `SameSite` | `Lax` or `Strict` | CSRF protection. Prevents cookies from being sent with cross-origin requests |
| `Path` | `/` or scoped | Limits which paths receive the cookie |
| `Max-Age` | Match token expiry | Cookie expires when the token does |

### Common Pitfalls

#### Conditional Secure Flag

```kotlin
// Dangerous - Secure flag depends on request
secure = request.isSecure

// Problem: Behind a reverse proxy (nginx, ALB), the backend sees HTTP,
// not HTTPS. Even with forward-headers-strategy: native, misconfigured
// proxies will result in cookies without the Secure flag.

// Safer - use environment-based configuration
secure = appProperties.auth.cookie.secure  // true in prod, false in dev
```

### SameSite Attribute

#### What Is SameSite?

`SameSite` is a cookie attribute that controls **when the browser attaches cookies to cross-site requests**. It is the browser-enforced defense against CSRF (Cross-Site Request Forgery) attacks.

| Value | Behavior |
|-------|----------|
| `Strict` | Cookie is only sent on same-site requests. Never sent on cross-site navigation (e.g., clicking a link from another site to your site). |
| `Lax` | Cookie is sent on same-site requests **and** top-level navigations (GET only) from other sites. Blocks cross-site POST, iframe, and AJAX. |
| `None` | Cookie is always sent, including on cross-site requests. **Requires `Secure` flag.** |

**Lax is the recommended default** for authentication cookies. `Strict` breaks OAuth redirect flows because the browser would not attach cookies when the OAuth provider redirects back to your site.

#### Browser Default Behavior

Since Chrome 80 (February 2020), if a server does not explicitly set `SameSite`, Chrome treats the cookie as `SameSite=Lax`. Other modern browsers (Edge, Firefox, Safari) have adopted similar defaults. However:

- Older browsers may default to `None` (no restriction), leaving the cookie unprotected.
- Relying on browser defaults makes the security posture **implicit** — a reader of the code cannot tell whether SameSite was intentionally omitted or accidentally forgotten.

**Always set `SameSite` explicitly** to ensure consistent behavior across all browsers and to make the security intent clear.

#### Why SameSite Matters in This Project

This project disables Spring Security's CSRF protection (`csrf.disable()`) because:
- The API is stateless (JWT-based), not session-based
- Cookies use `SameSite` attribute as the primary CSRF defense

**This means `SameSite` is the only CSRF protection.** If `SameSite` is missing, the API is vulnerable to CSRF attacks.

#### SameSite in the Contentria Architecture

The request flow involves multiple hops, and SameSite applies differently at each:

```
Browser  ──(1)──>  Next.js (Server Actions)  ──(2)──>  Nginx  ──(3)──>  Spring Boot
                   (sets/reads cookies)                (proxy)           (sets cookies)
```

**Hop (1): Browser → Next.js** — SameSite is enforced here. The browser checks the `SameSite` attribute before attaching cookies to the request. This is the critical boundary.

**Hop (2): Next.js → Nginx** — Server-to-server. The browser is not involved, so SameSite has no effect. Next.js manually forwards cookies in the `Cookie` header.

**Hop (3): Nginx → Spring Boot** — Server-to-server. Same as above — SameSite is irrelevant at this hop.

All cookie-setting code points (regardless of hop) must include `SameSite=Lax` because the cookie will eventually be stored in the browser and evaluated against the SameSite policy on subsequent requests.

| Layer | Where cookies are set | Files |
|-------|-----------------------|-------|
| Spring Boot | Token refresh, OAuth2 login response | `CookieUtil.kt` |
| Middleware | Token refresh via redirect | `middleware.ts` |
| Server Actions | Login, OTP verification | `actions/auth.ts` |
| apiServer | Token refresh during API calls | `lib/apiServer.ts` |

#### SameSite and Google OAuth2 (OIDC)

The Google OAuth flow **bypasses the BFF (Next.js) layer** — the browser communicates directly with Spring Boot during the redirect:

```
Browser  ──>  Google Authorization Server  ──>  Spring Boot (/login/oauth2/code/google)
                                                     │
                                                     ├── Sets accessToken + refreshToken cookies
                                                     └── Redirects to frontend (Next.js)
```

After Spring Boot processes the OAuth callback, it sets cookies and redirects the browser to the frontend. At this point, the browser stores the cookies with their `SameSite` attribute. On subsequent requests to the frontend (same site), the browser attaches these cookies normally.

If `SameSite` were set to `Strict` instead of `Lax`, the cookies would **not** be sent on the redirect from Google → Spring Boot → Frontend, because the browser would consider the navigation as originating from a cross-site context (Google). `Lax` allows cookies on top-level GET navigations, which is why it works with OAuth redirect flows.

#### Summary

- Set `SameSite=Lax` on every authentication cookie, in every layer.
- `Lax` is preferred over `Strict` to support OAuth2 redirect flows.
- Never omit `SameSite` — it is the sole CSRF defense since Spring Security CSRF is disabled.

## Sensitive Data Handling

### Never Expose in Logs or Responses

See [Logging Conventions](logging.md) for the full list. Summary:

- Token values (JWT, refresh token, API keys)
- Passwords (raw or hashed)
- Email addresses (PII)
- Full request/response bodies

### Error Responses

Error messages returned to clients should be generic. Internal details (stack traces, SQL errors, internal service names) must never leak to the client.

```kotlin
// Good - generic message from ErrorCode enum
ErrorCode.INVALID_CREDENTIALS → "Invalid email or password."

// Bad - reveals internal implementation
"User not found in credentials table for email: john@example.com"
```

### OAuth2 Error Handling

When OAuth2 authentication fails, **do not pass the raw exception message to the frontend URL**:

```kotlin
// Dangerous - exception.message could contain anything
val url = "https://example.com/login?error=${exception.message}"

// Safe - use a predefined error code
val url = "https://example.com/login?error=oauth_failed"
```

Raw exception messages may contain internal details, and inserting them into URLs can enable open redirect or reflected XSS attacks.

## Input Validation

### Controller Layer

- Always use `@Valid` on `@RequestBody` parameters
- Define constraints on DTO fields (`@Email`, `@NotBlank`, `@Size`, etc.)
- Validate path variables and query parameters for expected formats

### Authorization Checks

Every write operation must verify ownership:

```kotlin
// Pattern: Facade validates ownership before delegating
blogService.validateBlogOwner(blogId, userId)  // throws if not owner
categoryService.validateCategoryBelongsToBlog(categoryId, blogId)
postInternalService.createPost(...)
```

### URL Path Variables vs Request Body

When both a path variable and request body contain the same ID (e.g., `postId`), **use the path variable** or verify they match. Otherwise, a client could send a different ID in the body to modify another user's resource:

```kotlin
// Vulnerable - path variable ignored, body's postId used
@PostMapping("/posts/{postId}")
fun updatePost(@PathVariable postId: UUID, @RequestBody request: UpdatePostRequest) {
    postFacade.updatePost(userId, request.toCommand())  // uses request.postId, not path postId
}

// Safe - explicitly use path variable or verify match
@PostMapping("/posts/{postId}")
fun updatePost(@PathVariable postId: UUID, @RequestBody request: UpdatePostRequest) {
    require(postId == request.postId) { "Path and body postId mismatch" }
    postFacade.updatePost(userId, request.toCommand())
}
```

## Password Handling

### Design Context

This project supports **two authentication paths**:

1. **Email + Password login**: User sets a password during signup and uses it to log in.
2. **Email OTP login**: User signs up without a password and authenticates via a one-time verification code sent to their email.

Because of this, `password` is **intentionally nullable** in both `LoginRequest` and `Credential`. A null password is not a bug — it means the user registered via OTP-only flow.

### Rules

- Always use `PasswordEncoder` (BCrypt) for hashing
- Never log raw or hashed passwords
- **Check for null before calling `passwordEncoder.matches()`** — passing null may throw or behave unpredictably depending on the encoder implementation. This also prevents OTP-only users (who have no password) from accidentally passing through password-based authentication.

```kotlin
// Safe: null password is rejected before reaching the encoder
fun authenticate(email: String, rawPassword: String?): Credential {
    val credential = credentialRepository.findByEmail(email)
        ?: throw ContentriaException(ErrorCode.INVALID_CREDENTIALS)

    if (rawPassword == null || !passwordEncoder.matches(rawPassword, credential.password)) {
        throw ContentriaException(ErrorCode.INVALID_CREDENTIALS)
    }
    return credential
}
```

## Rate Limiting

The API uses Bucket4j for rate limiting on authentication endpoints (`/api/auth/**`):
- 10 requests per minute per IP
- Only successful responses (HTTP 200) consume tokens

This protects against brute-force login attempts and OTP abuse.
