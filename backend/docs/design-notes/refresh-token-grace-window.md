# Refresh Token Grace Window

How `POST /auth/refresh` handles concurrent rotation attempts on the same token.

Related code: `RefreshTokenService.rotate`, `RefreshTokenGraceCache`, `AuthFacade.refreshTokens`.

## Problem

Users were occasionally logged out without an explicit logout action, particularly during multi-fetch page loads in development. The frontend would redirect to `/login?alert=session_expired` and clear cookies even though the refresh token was still well within its 7-day TTL.

The trigger was concurrent calls to `POST /auth/refresh` carrying the same old refresh token. Multiple sources can produce this concurrency:

- A single page render firing several parallel server actions via `apiServer.ts` (Next.js BFF). All return 401 simultaneously, all attempt refresh.
- The Next.js middleware (`proxy.ts`) running alongside an in-flight server action that also detects 401.
- Multiple browser tabs that each independently detect access-token expiry.

## Analysis

The original implementation split the operation into two reads followed by a write:

```kotlin
// AuthFacade.refreshTokens (original)
val validRefreshToken = refreshTokenService.findValidToken(oldToken)   // findByToken
val user = userService.getActiveUserInfo(validRefreshToken.userId)
val (access, refresh) = generateTokens(user)                           // upsertRefreshToken: findByUserId + mutate + save
```

`refresh_tokens` carries one row per user (single-session policy). Both reads are unsynchronized and the write has no row-level lock. Under PostgreSQL's default `READ_COMMITTED` isolation, two race scenarios appear.

### Scenario A — strictly serialized (deterministic logout)

```
T1: findByToken("A")        → row found
T1: upsertRefreshToken(U)   → mutates row.token = "B", flushes
T1: COMMIT                  → DB now holds token = "B"
─────────────────────────────────────────────────────
T2: findByToken("A")        → NULL (DB has "B" only)
T2: throws REFRESH_TOKEN_NOT_FOUND  → HTTP 401
T2 (frontend apiServer.ts): caught 401 from refresh path
                            redirect('/login?alert=session_expired')
                            cookies cleared → user logged out
```

T2 reliably fails. This is the cleanest path to the observed symptom.

### Scenario B — overlapping (probabilistic logout)

When T1 and T2 begin transactions that overlap, both initial `findByToken("A")` calls succeed (each sees a `READ_COMMITTED` snapshot taken before the other commits). Each then enters its own write path:

```
T1: BEGIN; SELECT token=A → ok
T2: BEGIN; SELECT token=A → ok (T1 not yet committed)
T1: UPDATE row SET token = "B"     [row lock acquired]
T2: UPDATE row SET token = "C"     [waits for T1's row lock]
T1: COMMIT  → row lock released
T2: re-reads, applies UPDATE → token = "C"
T2: COMMIT
```

Both API calls return 200. T1's response carries refresh token `B`; T2's carries `C`. The DB holds `C`. The frontend writes both `Set-Cookie` headers in arrival order; whichever response arrives last determines the persisted cookie. If the browser ends up with `B`, the next API call will fail because `B` does not exist in the DB. The user observes a delayed logout on the next interaction. The probability depends on response ordering and is non-zero.

### Why this manifests in development specifically

- Local dev fires more parallel calls per render: HMR/Fast Refresh re-runs effects, and dashboard pages aggregate multiple widgets each fetching independently.
- Lower latency between client and server makes the race window proportionally larger.
- Production cache layers and CDN buffering can mask some of the timing.

## Solution candidates considered

### 1. Application-level mutex keyed by old token

A `ConcurrentHashMap<String, ReentrantLock>` in the auth service. First requester takes the lock, performs the rotation, releases. Followers wait, then re-read and either return cached output or fall through.

- **Pros**: No DB or cache infra changes. Cheap.
- **Cons**: Per-instance only. Two requests routed to different replicas would still race. Lock cleanup (preventing unbounded map growth) is fiddly. Harder to reason about than DB-level guarantees.

### 2. Optimistic locking via `@Version` on `RefreshToken`

Add a JPA version column. Concurrent UPDATEs detect conflict; the loser retries.

- **Pros**: Standard JPA pattern.
- **Cons**: Loser still fails the user-facing call unless we add retry with backoff. Doesn't address Scenario A (the loser hits a not-found, not a version conflict). Schema migration required.

### 3. Distributed lock via Redis (`SETNX` keyed by user or token)

External lock service.

- **Pros**: Works across replicas. Clear primitive.
- **Cons**: Introduces Redis dependency, which is currently not deployed. Heavy machinery for a single-instance setup. Lock acquisition latency on every refresh.

### 4. DB exclusive lock (`PESSIMISTIC_WRITE`) only

Add `SELECT FOR UPDATE` to the find step; rotation happens under the row lock.

- **Pros**: Closes Scenario B fully — concurrent rotators on the same row are serialized at the DB level.
- **Cons**: Does not close Scenario A. T2 acquires the lock after T1 commits and finds no row matching the old token, still throwing `NOT_FOUND`.

### 5. Grace cache via `@TransactionalEventListener(AFTER_COMMIT)`

Combine the DB lock with a Caffeine cache mapping `oldToken → newToken`. The cache write is published as a transactional event and processed after commit.

- **Pros**: Coherence with DB guaranteed (cache only reflects committed state).
- **Cons**: `AFTER_COMMIT` runs **after the row lock is released**. A concurrent thread can acquire the lock between commit and listener execution, see no row, then miss the cache because the listener has not yet fired. The race is reduced but not closed.

### 6. Grace cache, written **inside** the same `@Transactional` method (chosen)

Combine `PESSIMISTIC_WRITE` with a Caffeine cache, but place `cache.put` inside the transactional method, after entity mutation, before commit. The row lock is still held when the cache is written.

```
T1: SELECT FOR UPDATE WHERE token = "A"   [row lock]
T1: row.token = "B"                       [in-memory mutation]
T1: graceCache.put("A", (newToken="B", userId=U))
T1: COMMIT                                [flush UPDATE, lock released]
─────────────────────────────────────────────────────
T2: SELECT FOR UPDATE WHERE token = "A"   [waits for T1's lock]
                                          [T1 commits, lock released]
T2: SELECT returns NULL (row has "B" now)
T2: graceCache.get("A") → ("B", U)        [populated before lock release]
T2: returns same refresh token "B" + freshly issued access token
```

The lock guarantees T2 cannot reach the cache lookup until T1's `cache.put` has already executed. There is no window in which T2 sees neither the row nor the cache.

## Final solution

Implementation lives in three places:

1. **Repository: `findByTokenForUpdate`** (`RefreshTokenJpaRepository.kt`)
   ```kotlin
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   @Query("SELECT r FROM RefreshToken r WHERE r.token = :token")
   fun findByTokenForUpdate(@Param("token") token: String): RefreshToken?
   ```

2. **Cache: `RefreshTokenGraceCache`** (`auth/infrastructure/`)
   - Caffeine `Cache<String, RotatedTokenInfo>` used directly (not via Spring `Cache` abstraction) for typed access.
   - `expireAfterWrite = 10 seconds`. `maximumSize = 10_000`.

3. **Service: `RefreshTokenService.rotate`** (`auth/application/`)
   ```kotlin
   @Transactional
   fun rotate(oldToken: String): RotatedTokenInfo? {
       val row = refreshTokenRepository.findByTokenForUpdate(oldToken) ?: return null
       if (row.expiryDate.isBefore(Instant.now())) {
           refreshTokenRepository.delete(row)
           throw ContentriaException(ErrorCode.REFRESH_TOKEN_EXPIRED)
       }
       val newValue = UUID.randomUUID().toString()
       row.token = newValue
       row.expiryDate = Instant.now().plus(jwtProperties.refreshTokenExpiration)
       val rotated = RotatedTokenInfo(newValue, row.userId)
       graceCache.put(oldToken, rotated)   // inside @Transactional, lock still held
       return rotated
   }
   ```

4. **Facade: `AuthFacade.refreshTokens`** falls back to the cache when rotation returns null:
   ```kotlin
   val rotated = refreshTokenService.rotate(oldToken)
       ?: refreshTokenService.lookupGrace(oldToken)
       ?: throw ContentriaException(ErrorCode.REFRESH_TOKEN_NOT_FOUND)
   ```

The access token is **always** generated fresh from the user's current claims — never cached — so its `iat` and `exp` reflect the actual issue time of each response.

## Why this solution

- **Closes both scenarios with one mechanism.** The DB lock serializes concurrent rotators (Scenario B); the in-transaction cache write makes the rotated value visible to followers before the lock is released (Scenario A).

- **No infra dependency.** Caffeine ships in-process; the codebase already pulls `caffeine:jcache` for rate limiting. Redis was deferred until horizontal scaling actually requires it.

- **Service layer untouched on future scale-out.** When the deployment moves to N>1 instances, only `RefreshTokenGraceCache` is replaced (Redis-backed implementation behind the same interface). `RefreshTokenService.rotate`, `lookupGrace`, and `AuthFacade` remain identical.

- **No schema migration.** Avoids the operational cost of an `@Version` column and downstream entity mapping changes.

- **Bounded blast radius on rollback.** The cache may briefly hold an entry pointing to a token that did not commit. Mitigations: (a) 10-second TTL caps exposure, (b) the next legitimate request with the same old token re-rotates and overwrites the entry, (c) a stale grace hit produces an immediate failure on the client's next API call — same UX as the pre-fix logout, but rarer. The trade-off is judged acceptable against the alternative (`AFTER_COMMIT`) which leaves a wider, more frequent race window.

- **Access tokens not cached.** Two reasons: JWT `iat`/`exp` claims must reflect issue time; HMAC signing is microsecond-cheap per call. Caching access tokens would create stale-claim problems for negligible savings.

## Out of scope

- **Refresh token reuse detection** (security feature, not race fix): treat presentation of an old token *after* the grace window as evidence of theft and invalidate the user's session(s). Tracked separately.

- **Frontend single-flight lock** in `apiServer.ts`: would reduce duplicate `/auth/refresh` traffic and log noise but is not required for correctness now that the backend handles concurrency. Tracked separately.

- **Multi-device sessions**: the single-row-per-user policy in `refresh_tokens` is unchanged. Logging in on a second device still invalidates the first.
