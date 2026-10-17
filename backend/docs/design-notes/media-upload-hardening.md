# Media Upload Hardening

> **Hardening** is a security term meaning "to reduce the attack surface and add defensive layers to a system." A feature may work correctly in its default state, but hardening ensures it remains secure against intentional misuse or unexpected input.

| Before (default) | After (hardened) |
|---|---|
| Client-declared `Content-Type` trusted as-is | Magic number validation verifies actual file content (#10) |
| No upload rate or volume limits | Bucket4j rate limiting + daily byte quota (#9) |
| EXIF metadata (GPS, device info) served to every visitor | ImageIO re-encoding strips all metadata (#11) |
| CDN serves default response headers | `X-Content-Type-Options: nosniff` prevents MIME sniffing (#12) |

This document records the security problems identified in the image upload pipeline, the analysis behind each decision, the actions taken, and their results. Covers issues #9 through #12, all follow-ups to the initial R2 presigned URL upload feature (#7/#8).

---

## 1. Rate Limiting and Per-User Quotas (#9)

### Problem

The presigned URL endpoint (`POST /api/media/presigned-url`) had no rate limiting. A single authenticated user could:

- Flood the endpoint to generate unlimited presigned URLs, consuming R2 storage.
- Upload an unbounded total volume of data per day.
- Attach an unlimited number of images to a single post, inflating storage and rendering costs.

### Analysis

| Approach | Pros | Cons |
|----------|------|------|
| Nginx/Cloudflare IP-based rate limit | Simple, no code changes | Cannot distinguish per-user; shared IPs (NAT, VPN) cause false positives |
| Spring `HandlerInterceptor` with in-memory counter | Per-user, low latency | Resets on restart; no cluster-safe option |
| **Bucket4j + JCache (Caffeine)** | Per-user token-bucket, Spring Boot auto-configuration, proven library | Additional dependency |
| Redis-based distributed rate limiter | Cluster-safe | Overkill for single-instance deployment; adds infrastructure |

**Decision:** Bucket4j with Caffeine JCache. The blog runs as a single instance, so in-memory caching is sufficient and avoids external infrastructure. Bucket4j's token-bucket algorithm allows both burst (30 req/min) and sustained (100 req/day) limits in a single filter chain. Per-user keying is done via `SecurityContextHolder` through a custom `UserIdResolver`.

Daily byte quota (100 MB/day) and per-post image limit (20 images) are enforced at the application layer in `MediaService`, not in the filter, because they require DB queries (`SUM(fileSize)` and `COUNT(*)`).

### Action

- Added Bucket4j + JCache (Caffeine) dependency to `blog-api`.
- Configured `mediaRateLimitingFilter` in `application.yml` (30/min burst + 100/day sustained, filter-order 90).
- Created `UserIdResolver` to extract userId from `SecurityContextHolder` for per-user bucket keys.
- Added `dailyUploadLimitBytes` (100 MB) and `maxImagesPerPost` (20) to `AppProperties.R2Properties`.
- Added `validateDailyUploadQuota()` and `validatePostImageLimit()` in `MediaService`.
- Added error codes: `ME0004` (daily quota exceeded), `ME0005` (post image limit exceeded).

### Result

- Presigned URL endpoint is protected by two-tier token-bucket rate limiting (burst + daily).
- Application-layer quotas prevent storage abuse even if the rate limiter is bypassed.
- Error responses include specific error codes so the frontend can display meaningful messages.

---

## 2. Magic Number (File Signature) Validation (#10)

### Problem

The upload flow trusted the client-declared `Content-Type` without verification. An attacker could upload a malicious file (e.g., HTML containing XSS payload) with `Content-Type: image/jpeg`, and it would be served from the CDN as-is.

### Analysis

| Approach | Pros | Cons |
|----------|------|------|
| Extension-only check | Trivial to implement | Trivial to bypass (rename `.html` to `.jpg`) |
| Full file decode (ImageIO.read) | Proves the file is a valid image | Requires downloading the full file; slow for large files |
| **Magic number (header bytes) check** | Fast (only reads first 12 bytes via Range request); catches the most common spoofing | Cannot catch every edge case (e.g., polyglot files) |
| External library (Apache Tika) | Comprehensive MIME detection | Heavy dependency for a narrow use case |

**Decision:** Magic number validation on first 12 bytes, implemented on both client and server side. This is the best cost/accuracy trade-off — a 12-byte `Range` GET is near-free on R2, and it catches all common spoofing vectors. Combined with CDN-level `X-Content-Type-Options: nosniff` (#12), polyglot attacks are also mitigated.

**Client-side validation** was added to `uploadImage.ts` to reject mismatched files instantly before any network request, providing better UX. Server-side validation in `promoteTemporaryMedia` acts as the authoritative gate.

**On validation failure**, the server does NOT delete the R2 object or DB record. The `tmp/` lifecycle rule (24h expiry) handles cleanup. Deleting immediately would leave a broken `<img>` URL in the editor with no way for the user to recover — they would see a broken image but not understand why saving failed.

### Action

- Created `MediaValidator` in `application/` layer with magic number signatures for JPEG, PNG, GIF, WebP (including dual-offset RIFF+WEBP check).
- Added `getObjectHeadBytes()` to `R2StorageClient` for 12-byte Range GET.
- Added `validateMediaContent()` in `MediaService.promoteTemporaryMedia`.
- Added browser-side `validateMagicNumber()` in `uploadImage.ts` using `FileReader` + `ArrayBuffer`.
- Added error code `ME0006` (content type mismatch).
- Updated `PostEditorClient.tsx` to show backend error messages instead of a generic failure toast.

**Why `MediaValidator` lives in `application/`, not `domain/`:** It contains technical file-format knowledge (byte signatures, offsets), not business rules. It is analogous to `MarkdownService` — a utility class used within a single bounded context. Per the project's architecture rules, this is the correct placement.

### Result

- Dual-layer validation: browser catches most mismatches instantly; server catches anything that bypasses the browser.
- Graceful failure: user sees a specific error message and can remove/replace the invalid image in the editor.
- Minimal R2 cost: only 12 bytes per validation via Range request.

---

## 3. EXIF Metadata Stripping (#11)

### Problem

JPEG images contain EXIF metadata that may include personally identifiable information:

- **GPS coordinates** — exact location where the photo was taken.
- **Camera/device info** — device model, serial number.
- **Timestamps** — original capture date/time.
- **Thumbnails** — may contain the original uncropped image.

Serving user-uploaded images with EXIF intact leaks this data to every visitor.

### Analysis

| Approach | Pros | Cons |
|----------|------|------|
| External library (metadata-extractor + manual byte rewrite) | Preserves original quality exactly | Complex implementation; must handle all APP1/APP13 segments; risk of corrupted output |
| Cloudflare Image Resizing (Polish) | Zero backend code | Paid add-on; not available on all R2 plans; opaque processing |
| **JDK ImageIO re-encoding** | Zero external dependencies; strips ALL metadata by nature (pixel-only re-encode); built into every JVM | Lossy re-encoding (quality loss at any setting < 1.0); slightly larger/smaller file size |
| Client-side only (canvas re-encoding) | No server cost | Can be bypassed; not authoritative |

**Decision:** JDK ImageIO re-encoding at 0.9 quality on the server, combined with client-side `browser-image-compression` canvas re-encoding.

- **Server-side (authoritative):** During `tmp/` → `media/` promotion, JPEG files are downloaded, decoded via `ImageIO.read()` (which discards all non-pixel data), and re-encoded with `ImageWriteParam.compressionQuality = 0.9f`. The result is a clean JPEG with no EXIF, APP1, or APP13 segments. Quality 0.9 is visually indistinguishable from the original for web-sized images.
- **Client-side (defense in depth):** `browser-image-compression` already re-encodes via HTML Canvas, which inherently strips EXIF. No additional frontend code was needed.
- **Non-JPEG formats:** PNG, WebP, and GIF do not carry EXIF in the same way (PNG uses tEXt chunks which are less sensitive; WebP/GIF rarely contain PII metadata). These continue using the existing `copyObject` path without re-encoding.

### Action

- Added `getObjectBytes()` and `putObject()` to `R2StorageClient` for full object download/upload.
- Added `stripExifMetadata()` private method in `MediaService` using `ImageIO` + `ImageWriteParam`.
- Modified `promoteTemporaryMedia` loop: JPEG → download, strip, putObject; others → copyObject (unchanged).
- Added `JPEG_REENCODING_QUALITY = 0.9f` constant.

### Result

- All JPEG images served from `media/` are guaranteed EXIF-free.
- Zero external dependencies — uses only JDK's `javax.imageio`.
- Client-side stripping provides defense in depth but is not relied upon as the sole protection.
- Non-JPEG images are unaffected (no unnecessary re-encoding).

---

## 4. CDN Response Headers Hardening (#12)

### Problem

User-uploaded images are served from `images.contentria.com` (Cloudflare R2 custom domain) with default response headers. Without explicit security headers:

- **MIME sniffing:** A browser may ignore the `Content-Type` and "sniff" the content, potentially executing a disguised HTML/JS payload as a script.
- **XSS surface:** If a malicious file bypasses validation, the absence of security headers gives the browser maximum latitude to execute it.

### Analysis

| Header | Purpose | Trade-off |
|--------|---------|-----------|
| `X-Content-Type-Options: nosniff` | Prevents browsers from MIME-sniffing; forces trust in declared Content-Type | None for image-only domains; universally recommended |
| `Content-Security-Policy` on image pages | Restricts what scripts/styles can execute on pages rendering user images | Must be carefully scoped to avoid breaking legitimate functionality |
| `Content-Disposition: attachment` | Forces download instead of inline rendering | Breaks `<img>` tags; only appropriate for non-image file types |

**Decision:** Apply `X-Content-Type-Options: nosniff` in two places:

1. **Cloudflare Transform Rules** on `images.contentria.com` — covers all R2-served image responses.
2. **Next.js `headers()` config** — covers all responses served from the Next.js app itself (pages, API routes, static assets).

This is the browser-side complement to server-side magic number validation (#10). Even if a polyglot file bypasses server validation, the browser will not re-interpret `image/jpeg` as `text/html`.

**Trust model note:** `nosniff` does not defend against network-layer MITM header tampering — that concern is already handled by HTTPS/TLS integrity guarantees. `nosniff` defends against the browser's own "helpful" MIME sniffing behavior when a server-declared `Content-Type` looks suspicious to the browser.

`Referrer-Policy: strict-origin-when-cross-origin` was also added to prevent leaking post slugs and query parameters to third-party resources via the `Referer` header.

CSP tightening on the Next.js side (beyond `frame-ancestors`) is deferred — it is a broader concern than media uploads and requires per-page evaluation to avoid breaking legitimate functionality.

`Content-Disposition: attachment` is not applicable today since only image formats are served inline via `<img>` tags.

### Action

- **Cloudflare Dashboard** (manual): Configure a Transform Rule adding `X-Content-Type-Options: nosniff` to all responses from `images.contentria.com`.
- **`frontend/next.config.ts`**: Add `X-Content-Type-Options: nosniff` and `Referrer-Policy: strict-origin-when-cross-origin` to the global `headers()` configuration.

### Result

- Browser MIME sniffing is disabled for both CDN-served assets and Next.js app responses.
- Cross-origin `Referer` leakage is limited to origin-only on cross-origin navigations.
- Combined with magic number validation (#10), the attack surface for file-type spoofing is closed at both server and browser layers.

---

## Summary: Defense-in-Depth Layers

```
Upload Request
  │
  ├─ [Browser]   Type / size / magic number validation (instant feedback)
  ├─ [Browser]   Canvas re-encoding strips EXIF (browser-image-compression)
  │
  ├─ [Server]    Bucket4j rate limiting (30/min + 100/day per user)
  ├─ [Server]    File type + size validation on presigned URL creation
  ├─ [Server]    Daily byte quota (100 MB) + per-post image limit (20)
  │
  ├─ [Promotion] Magic number validation (12-byte Range GET)
  ├─ [Promotion] EXIF stripping via ImageIO re-encoding (JPEG only)
  │
  ├─ [CDN]       X-Content-Type-Options: nosniff
  ├─ [CDN]       R2 Object Lifecycle Rule (24h expiry on tmp/)
  │
  └─ [Served]    Clean image, no metadata, correct Content-Type, no MIME sniffing
```

| Layer | Threat Mitigated | Issue |
|-------|-----------------|-------|
| Rate limiting + quotas | Resource exhaustion, storage abuse | #9 |
| Magic number validation | File type spoofing, XSS via disguised files | #10 |
| EXIF stripping | PII leakage (GPS, device info, timestamps) | #11 |
| CDN `nosniff` header | Browser MIME sniffing, polyglot file execution | #12 |
| tmp/ lifecycle rule | Orphaned object accumulation | #7 (R2 dashboard) |
