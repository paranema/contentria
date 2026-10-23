# Cloudflare 설정: 영상 변환 파이프라인

영상 기능이 쓰는 Cloudflare 쪽 설정 절차다. 원본이 R2에 올라오면 R2가 이벤트를 내고, `apps/worker`가 **Cloudflare Queue를 HTTP로 직접 가져와**(pull) 알게 된다. Cloudflare Worker와 웹훅은 없다.

```
프론트엔드 ──PUT (미리 서명된 주소)──▶ R2 (raw/{영상 ID}/original.{확장자})
                                         │  object-create 이벤트
                                         ▼
                                 Cloudflare Queue ──(3번 실패 → DLQ)
                                         │  HTTP pull (http_pull 소비자)
                                         ▼
                                   apps/worker ──변환──▶ R2 (hls/{영상 ID}/...)
```

**Worker 대신 pull을 쓰는 이유:** 자바스크립트 배포와, 보호해야 할 웹훅 엔드포인트가 없어진다. 재시도, DLQ, 처리 중에 죽은 작업의 재전달(가시성 타임아웃)은 큐가 맡는다. Cloudflare 의존은 R2와 Queues만 남는다.

영상의 상태와 규칙은 [contexts/media/01-rules.md](../contexts/media/01-rules.md)의 흐름 V2, 코드 배치는 [02-implementation.md](../contexts/media/02-implementation.md)에 있다.

---

## 1. 만들 자원

대시보드나 `wrangler`로 만든다. 이름은 예시다. 바꾸면 워커 설정(3절)도 맞춘다.

### 1.1 큐와 DLQ

| 자원 | 이름 | 설명 |
|---|---|---|
| 작업 큐 | `contentria-video-transcode` | R2의 object-create 이벤트를 받는다 |
| DLQ | `contentria-video-transcode-dlq` | 재시도를 다 쓴 메시지가 쌓인다 |

```bash
wrangler queues create contentria-video-transcode
wrangler queues create contentria-video-transcode-dlq
```

### 1.2 HTTP pull 소비자

워커는 **pull** 소비자다(Worker push 소비자가 아니다). 작업 큐에 `http_pull` 소비자를 붙이고, 재시도 3회와 DLQ를 지정한다.

```bash
wrangler queues consumer http add contentria-video-transcode \
  --message-retries 3 \
  --visibility-timeout-secs 1800 \
  --dead-letter-queue contentria-video-transcode-dlq
```

| 옵션 | 뜻 |
|---|---|
| `--message-retries 3` | 세 번 전달에 실패하면 DLQ로 간다. 옵션 이름은 `--message-retries`다. `--max-retries`는 없다 |
| `--visibility-timeout-secs 1800` | 소비자의 기본 가시성 타임아웃(30분). 워커도 가져올 때마다 `visibility_timeout_ms`를 보낸다(3절). 둘 다 30분이므로 하나가 빠져도 설계가 유지된다 |

### 1.3 R2 이벤트 알림

`raw/` 접두사에 새 객체가 생기면 큐에 알린다.

```bash
wrangler r2 bucket notification create <R2_BUCKET_NAME> \
  --event-type object-create \
  --prefix raw/ \
  --queue contentria-video-transcode
```

- **`object-create`** 는 `PutObject`(한 번에 올리기)와 `CompleteMultipartUpload`(나눠 올리기)를 포함한다. 종류는 메시지 본문의 `action`에 있다. 워커는 이 둘만 변환하고 나머지(`CopyObject` 등)는 받은 것으로 처리하고 넘긴다.
- **`raw/` 접두사는 반드시 건다.** 워커는 변환 결과를 **같은 버킷**의 `hls/`에 쓰고, 이것도 object-create 이벤트를 낸다. 거르지 않으면 워커가 자기 결과를 다시 변환하는 무한 반복이 생긴다. 워커도 `raw/`로 시작하지 않는 키는 변환하지 않고 넘기지만(`VideoQueuePoller`), 큐에 쌓이지 않게 R2에서 먼저 거른다. 결과를 다른 버킷에 쓰게 되면 이 필터는 없어도 된다.

### 1.4 워커의 API 토큰

워커가 큐를 가져오고 확인(ack)하려면 Cloudflare **API 토큰**이 필요하다.

- 권한: **Account → Queues → Edit**. 가져오기와 확인 모두 쓰기 권한이다.
- 이 계정으로만 범위를 좁히고 주기적으로 바꾼다.
- Kubernetes Secret이나 환경 변수로 넣는다. **커밋하지 않는다.**

### 1.5 R2 수명 규칙 (raw/ 안전망)

워커는 변환에 성공하면 원본을 직접 지운다. 실패했거나 끝나지 않은 작업의 원본을 위해 수명 규칙을 둔다.

- 접두사 `raw/`, **7일 뒤 만료**.
- 나눠 올리기를 도입하면 **끝나지 않은 multipart 업로드 중단**도 N일로 켠다. 남은 조각을 치운다.

---

## 2. 메시지 형태

큐에 들어오는 R2 이벤트 알림은 다음과 같다.

```json
{
  "account": "<account_id>",
  "bucket": "<R2_BUCKET_NAME>",
  "object": { "key": "raw/<uuid>/original.mp4", "size": 12345678, "eTag": "..." },
  "action": "PutObject",
  "eventTime": "2026-06-03T12:34:56.000Z"
}
```

워커는 `object.key`와 `action`만 읽는다. `object.key`로 `videos`의 `raw_key`를 찾는다(DB에서 고유하다). 키로 찾으므로 키에서 영상 ID를 해석할 필요가 없다.

---

## 3. 워커 설정

`apps/worker`의 `application.yml`이 환경 변수를 `contentria.media.worker`에 묶는다(`MediaWorkerProperties`).

| 환경 변수 | 속성 | 예 | 용도 |
|---|---|---|---|
| `CLOUDFLARE_ACCOUNT_ID` | `queue.account-id` | `<account_id>` | REST API 경로 |
| `CF_QUEUE_ID` | `queue.queue-id` | `<queue_id>` | 가져올 작업 큐 |
| `CF_API_TOKEN` | `queue.api-token` | `<token>` | Queues Edit 권한. 비밀값이다 |
| `WORKER_POLL_INTERVAL` | `poll-interval` | `5s`(기본) | 큐를 가져오는 간격 |
| `FFMPEG_PATH`, `FFPROBE_PATH` | `transcode.ffmpeg-path`, `transcode.ffprobe-path` | `ffmpeg`, `ffprobe`(기본) | 변환 도구 경로 |

코드의 기본값을 쓰는 값:

| 속성 | 기본값 | 이유 |
|---|---|---|
| `queue.batch-size` | `1` | 워커 하나가 한 번에 한 작업만 한다 |
| `queue.visibility-timeout` | 30분 | 가장 긴 변환보다 길어야 처리 중인 작업이 다시 전달되지 않는다 |

가져오기와 확인 엔드포인트:

```
POST https://api.cloudflare.com/client/v4/accounts/{account_id}/queues/{queue_id}/messages/pull
  본문: { "visibility_timeout_ms": 1800000, "batch_size": 1 }
  → 메시지마다 lease_id가 온다

POST .../messages/ack
  본문: { "acks": [ { "lease_id": "..." } ] }
```

| 결과 | 처리 |
|---|---|
| 성공 | `lease_id`를 확인한다 |
| 영구 실패(영상이 아니거나 5분을 넘음) | 영상을 "실패"로 바꾸고 확인한다. 같은 입력은 다시 해도 실패한다 |
| 일시 실패(네트워크, 저장소) | 확인하지 않는다. 가시성 타임아웃 뒤 다시 오고, 3번 실패하면 DLQ로 간다 |
| 변환 대상이 아님(`raw/` 밖의 키, 다른 `action`, 기록이 없는 원본, 이미 끝난 영상) | 확인하고 넘긴다 |

---

## 4. 확인 절차

1. 미리 서명된 주소로 작은 영상을 올린다. `raw/` 아래에 객체가 생기는지 본다.
2. `contentria-video-transcode`에 메시지가 들어오는지 본다(대시보드의 큐 지표, 또는 직접 `messages/pull`).
3. `hls/` 아래에 객체를 써도 메시지가 생기지 **않는지** 본다(접두사 필터가 동작한다).
4. 일부러 세 번 실패시켜 메시지가 DLQ로 가는지 본다.
