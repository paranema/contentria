# Logging Conventions

This document defines the logging standards for the Contentria backend. All contributors should follow these guidelines to maintain consistent, secure, and useful logs.

## Log Level Guidelines

| Level | When to Use | Examples |
|-------|------------|---------|
| **error** | System cannot function normally. Requires investigation | DB connection failure, external API down, missing required config |
| **warn** | Abnormal but system continues. Potential security signal | Expired token used, invalid credentials, reCAPTCHA failure, rate limit hit |
| **info** | Significant business event. Should be rare and meaningful | User signup, blog created, post published, batch job started |
| **debug** | Flow tracing for development. Disabled in production by default | Controller entry/exit, internal step completion, query parameters |

### Decision Flowchart

```
Is the system broken or unable to proceed?
  └── Yes → error

Is it abnormal access or suspicious behavior?
  └── Yes → warn

Is it a business event worth knowing about in production?
  └── Yes → info

Everything else (tracing, internal steps)
  └── debug
```

### Common Mistakes

- **Logging every request at info level**: Use debug. Info logs should be sparse enough that you can read a day's worth in a few minutes.
- **Logging success of internal steps at info**: Email sent, verification code cached, token rotated — these are internal steps, not business events. Use debug. The business event (e.g., "user signed up") is logged at a higher layer.
- **Logging at warn when it's actually an error**: If the system is misconfigured (missing secret key, missing cache), that's error, not warn.

## Sensitive Data Rules

### Never Log These (at any level)

| Data | Why | What to Log Instead |
|------|-----|-------------------|
| **Tokens** (JWT, refresh, access, API keys) | Can be used to impersonate users if leaked from logs | Nothing, or `tokenRefreshed=true` |
| **Passwords** (raw or hashed) | Obvious security risk | Nothing |
| **Email addresses** | PII under GDPR/privacy regulations. Enables tracking of user behavior across log entries | `userId=<uuid>` |
| **Full request/response bodies** | May contain any of the above. Also wastes log storage | Key identifiers only: `postId`, `blogId` |
| **Cookie values** | Equivalent to token exposure | Cookie name only if needed |

### Why Email is PII

Email addresses are **Personally Identifiable Information (PII)**. Even in internal logs:

1. **Linkability**: Logs with emails allow correlating a real person's identity with their actions across the system (login times, posts created, pages visited).
2. **Regulatory risk**: GDPR (EU), PIPA (Korea), and similar laws treat email as personal data. Logging it without justification can be a compliance violation.
3. **Breach amplification**: If logs are leaked or accessed by unauthorized parties, emails turn a technical incident into a personal data breach requiring user notification.

**Rule**: Use `userId` (UUID) as the universal identifier in logs. UUIDs are meaningless without database access, limiting damage from log exposure.

### Safe to Log

- UUIDs: `userId`, `postId`, `blogId`, `categoryId`
- Enum values: status codes, error codes, provider types
- Counts and metrics: `count=3`, `score=0.7`
- Resource paths: `/api/auth/login`, `classpath:samples/backend-post.md`
- Timing: duration, timestamps

## Message Format

### Structure

Follow the pattern: **"What happened: key=value, key=value"**

```kotlin
// Good
log.info { "Post created: postId=${post.id}, blogId=${blog.id}, userId=$userId" }
log.warn { "Expired refresh token deleted: userId=${token.userId}" }
log.error(e) { "Failed to send email" }

// Bad - no context
log.info { "Success" }
log.info { "Done." }

// Bad - sentence style, hard to parse/grep
log.info { "The user with email john@example.com has successfully created a new post titled 'Hello World'" }

// Bad - logging sensitive data
log.info { "Token refreshed: ${newAccessToken}" }
log.info { "Login for email: ${user.email}" }
```

### Guidelines

1. **Use key=value format** for structured data. This makes logs grep-friendly and compatible with log aggregation tools (ELK, Loki, CloudWatch Insights).
2. **Start with what happened**, not who called the method.
3. **Keep messages on one line**. Multi-line log messages break log parsers.
4. **Don't duplicate information** that's already in the stack trace. When using `log.error(e) { ... }`, the exception message and stack trace are automatically included — don't repeat `e.message` in the log message.
5. **Use English** for log messages. Logs may be read by monitoring tools, searched with regex, or shared with international team members.

## Layer-Specific Guidelines

### Controller Layer

- **Level**: debug (flow tracing)
- **What to log**: Entry point with identifying parameters only
- **Don't log**: Request bodies (may contain markdown content, passwords, tokens)

```kotlin
// Good
log.debug { "Creating post: userId=${userDetails.userId}, blogId=${request.blogId}" }

// Bad - serializes entire request body including markdown content
log.info { "Creating post: request=$request" }
```

### Facade / Application Layer

- **Level**: info for business events, debug for internal steps
- **What to log**: Completed business operations with relevant IDs

```kotlin
// Business event - info
log.info { "Post created: postId=${post.id}, blogId=$blogId, userId=$userId" }

// Internal step - debug
log.debug { "Refresh token rotated: userId=$userId" }
```

### Infrastructure Layer

- **Level**: debug for success, error for failures, warn for external service issues
- **What to log**: Integration outcomes

```kotlin
// Success is routine - debug
log.debug { "Email sent successfully" }

// Failure needs attention - error
log.error(e) { "Failed to send email" }

// External service misbehavior - warn
log.warn { "reCAPTCHA verification failed. ErrorCodes: ${response.errorCodes}" }
```

### AOP / Cross-Cutting

- **Level**: debug
- **Rationale**: AOP logging (like `@ApiLog`) fires on every annotated method. At info level, this floods production logs. Keep at debug and enable selectively when investigating issues.
