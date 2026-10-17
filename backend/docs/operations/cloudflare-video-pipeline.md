# Cloudflare Setup — Video Transcoding Pipeline

This is a self-contained runbook for the Cloudflare-side infrastructure of the video
feature. The original upload lands in R2, R2 emits an event, and `blog-worker` learns
about it by **pulling a Cloudflare Queue directly** over HTTP — there is no Cloudflare
Worker and no inbound webhook.

```
Frontend ──PUT (presigned)──▶ R2 (raw/{id}/original.ext)
                                  │  object-create event
                                  ▼
                          Cloudflare Queue ──(retries 3 → DLQ)
                                  │  HTTP pull (http_pull consumer)
                                  ▼
                             blog-worker ──transcode──▶ R2 (hls/{id}/...)
```

**Why pull instead of a Worker:** removes the Worker component (no JS deploy, no inbound
webhook endpoint to secure) and lets the queue own retries, dead-lettering, and
visibility-timeout crash recovery. The remaining Cloudflare dependency is R2 + Queues.

---

## 1. Resources to create

All of the following can be done in the Cloudflare dashboard or via `wrangler`. Names are
suggestions — keep them in sync with the worker config (§3).

### 1.1 Queue + dead-letter queue

| Resource | Name | Notes |
|---|---|---|
| Work queue | `contentria-video-transcode` | Receives R2 object-create events. |
| Dead-letter queue | `contentria-video-transcode-dlq` | Poison messages land here after max retries. |

```bash
wrangler queues create contentria-video-transcode
wrangler queues create contentria-video-transcode-dlq
```

### 1.2 HTTP pull consumer

The worker is a **pull** consumer (not a Worker push consumer). Configure the work queue
with an `http_pull` consumer, message retries = 3, and the DLQ as the dead-letter target.

```bash
wrangler queues consumer http add contentria-video-transcode \
  --message-retries 3 \
  --visibility-timeout-secs 1800 \
  --dead-letter-queue contentria-video-transcode-dlq
```

- **`--message-retries 3`** → after 3 failed deliveries the message goes to the DLQ.
  (The flag is `--message-retries`; `--max-retries` does not exist.)
- **`--visibility-timeout-secs 1800`** sets the consumer-level default (30 min). The
  worker also sends `visibility_timeout_ms` on every pull (see §3); keeping both at
  30 min means the design holds even if one of them is omitted.

### 1.3 R2 event notification

Notify the queue when a new object is created under the `raw/` prefix.

```bash
wrangler r2 bucket notification create <R2_BUCKET_NAME> \
  --event-type object-create \
  --prefix raw/ \
  --queue contentria-video-transcode
```

- **Event type `object-create`** covers `PutObject` (single upload) and
  `CompleteMultipartUpload` (multipart, when added later). The action is in the message
  body (`action`); the worker should act on those two and may ignore others (e.g.
  `CopyObject`).
- **Prefix `raw/` is mandatory.** ⚠️ The worker writes transcoded outputs to `hls/` in
  the **same bucket**, which also emits `object-create` events. Without the `raw/`
  filter, the worker would re-transcode its own outputs (infinite feedback loop). If a
  separate output bucket is ever used instead, this filter becomes optional.

### 1.4 API token (for the worker)

The worker needs a Cloudflare **API token** to pull/ack the queue:

- Permission: **Account → Queues → Edit** (pull + ack require write).
- Scope it to this account only; rotate periodically.
- Store it as a Kubernetes Secret / env var for `blog-worker` — **never commit it**.

### 1.5 R2 lifecycle rule (raw/ backstop)

The worker deletes the original explicitly after a successful transcode. As a backstop
for failed/incomplete jobs, add a lifecycle rule:

- Prefix `raw/`, **expire after 7 days**.
- (When multipart upload is added later) also enable **abort incomplete multipart
  uploads** after N days to clean orphaned parts.

---

## 2. Message shape

An R2 event-notification message delivered to the queue looks like:

```json
{
  "account": "<account_id>",
  "bucket": "<R2_BUCKET_NAME>",
  "object": { "key": "raw/<uuid>/original.mp4", "size": 12345678, "eTag": "..." },
  "action": "PutObject",
  "eventTime": "2026-06-03T12:34:56.000Z"
}
```

The worker uses `object.key` to find the `videos` row via `raw_key` (the DB has a unique
constraint on `raw_key`). Lookup is by `raw_key`, so the key does not need to embed the
video id.

---

## 3. Config the worker (#4) will consume

`blog-worker` reads these (names illustrative; wire via K8s Secret / env):

| Key | Example | Purpose |
|---|---|---|
| `CLOUDFLARE_ACCOUNT_ID` | `<account_id>` | REST API path. |
| `CF_QUEUE_ID` | `<queue_id>` | The work queue to pull. |
| `CF_API_TOKEN` | `<token>` | Queues edit (pull/ack). Secret. |
| pull batch size | `1` | One job at a time per pod (concurrency 1/pod). |
| pull visibility timeout | `1800000` ms (30 min) | Must exceed worst-case transcode (~10 min+) so a live job is never redelivered. |

Pull / ack endpoints:

```
POST https://api.cloudflare.com/client/v4/accounts/{account_id}/queues/{queue_id}/messages/pull
  body: { "visibility_timeout_ms": 1800000, "batch_size": 1 }
  → returns messages, each with a lease_id

POST .../messages/ack
  body: { "acks": [ { "lease_id": "..." } ], "retries": [ ... ] }
```

- **Success** → ack the `lease_id`.
- **Transient failure** → do not ack; the message is redelivered after the visibility
  timeout, and after `max_retries` (3) it goes to the DLQ.
- **Permanent failure** (e.g. ffprobe rejects a >5 min or non-video input) → mark the
  `videos` row `FAILED` and ack (no point retrying a bad input).

---

## 4. Verification

1. Upload a small video via the presigned flow → confirm an object appears under `raw/`.
2. Confirm a message lands in `contentria-video-transcode` (dashboard → queue → metrics,
   or a manual `messages/pull`).
3. Confirm writing an object under `hls/` does **not** enqueue a message (prefix filter
   works — no feedback loop).
4. Force a failure 3× → confirm the message lands in the DLQ.
