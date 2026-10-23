# 로깅 규약

백엔드 로그의 레벨, 남기지 않는 값, 메시지 형식을 정한다.

## 로거

SLF4J를 쓴다. 클래스마다 로거를 하나 둔다.

```kotlin
private val log = LoggerFactory.getLogger(javaClass)
```

값은 자리표시자 `{}`로 넣고, 예외는 마지막 인자로 넘긴다. 문자열을 이어 붙이지 않는다.

## 레벨

| 레벨 | 쓰는 때 | 예 |
|---|---|---|
| error | 시스템이 정상으로 동작할 수 없다. 사람이 조사해야 한다 | DB 연결 실패, 필수 설정 누락 |
| warn | 비정상이지만 계속 동작한다. 재시도나 다음 실행이 다시 처리한다 | 큐 가져오기 실패, 파일 정리 실패, 안내 메일 발송 실패 |
| info | 운영에서 알아야 할 일. 드물게 남긴다 | 배치 작업의 실행 결과 |
| debug | 개발 중 흐름 추적. 운영에서는 끈다 | 내부 단계 완료, 조회 조건 |

운영 기본 레벨은 `INFO`다(`apps/api/src/main/resources/application.yml`의 `logging.level.root`).

지금 코드가 직접 남기는 로그는 warn과 info뿐이다. 처리하지 못한 예외는 Spring이 error로 남긴다.

### 고르는 순서

```
시스템이 고장 났거나 더 진행할 수 없는가        → error
비정상이지만 재시도나 다음 실행이 처리하는가   → warn
운영에서 알아야 할 업무 결과인가              → info
그 밖의 흐름 추적                            → debug
```

### 흔한 실수

| 실수 | 바로잡기 |
|---|---|
| 모든 요청을 info로 남긴다 | debug로 남긴다. info는 하루치를 몇 분 안에 읽을 수 있을 만큼만 남긴다 |
| 내부 단계의 성공(메일 발송, 토큰 회전)을 info로 남긴다 | debug로 남긴다. 업무 결과는 그 위 계층이 한 번 남긴다 |
| 설정 누락을 warn으로 남긴다 | 시스템이 동작할 수 없으면 error다 |

## 남기지 않는 값

어느 레벨에서도 남기지 않는다.

| 값 | 이유 | 대신 남길 것 |
|---|---|---|
| 토큰(액세스, 리프레시, 비밀번호 링크, API 키) | 로그가 새면 그 사람으로 행세할 수 있다 | 남기지 않는다 |
| 인증 코드 | 토큰과 같다 | 남기지 않는다 |
| 비밀번호(원문과 해시) | 원문은 그대로 쓰이고, 해시는 대입 공격의 재료가 된다 | 남기지 않는다 |
| 이메일 주소 | 개인정보다 | 계정 ID(UUID) |
| 요청과 응답 본문 | 위의 값을 모두 담을 수 있고, 저장 공간을 낭비한다 | 글 ID, 블로그 ID 같은 식별자 |
| 쿠키 값 | 토큰과 같다 | 필요하면 쿠키 이름만 |

### 이메일을 남기지 않는 이유

| 이유 | 내용 |
|---|---|
| 연결 | 이메일이 있는 로그는 실제 사람과 그 사람의 행동(로그인 시각, 쓴 글, 본 화면)을 잇는다 |
| 법 | 개인정보 보호법, GDPR은 이메일을 개인정보로 본다. 이유 없이 남기면 위반이 될 수 있다 |
| 유출 | 로그가 새면 기술 사고가 개인정보 유출 사고가 되고, 이용자에게 알려야 한다 |

로그의 식별자는 계정 ID(UUID)로 통일한다. UUID는 DB 없이는 아무 뜻이 없어 로그가 새도 피해가 작다.

### 예외 메시지에 주의한다

예외 메시지가 이메일을 담을 수 있으면 예외 대신 예외 클래스 이름만 남긴다.

```kotlin
// AccountNotices.kt: 메일 발송 예외의 메시지에는 받는 주소가 들어갈 수 있다
log.warn("Account notice {} was not sent: {}", event.notice::class.simpleName, e.javaClass.name)
```

### 예외: 로컬 메일

`contentria.member.mail.mode=log`이면 메일을 보내지 않고 코드와 링크 토큰을 warn으로 남긴다(`adapter/required/mail`의 로깅 발송기). 실제 주소가 오가지 않는 로컬 개발 전용이다. 배포 환경에서는 켜지 않는다.

### 남겨도 되는 값

| 종류 | 예 |
|---|---|
| UUID | 계정 ID, 글 ID, 블로그 ID, 이미지와 영상 ID |
| 열거값 | 상태, 오류 코드, 공급자 종류 |
| 개수와 수치 | 지운 수, 실패한 수, 점수 |
| 경로와 키 | API 경로, 저장소 키(`raw/{영상 ID}/original.mp4`) |
| 시간 | 걸린 시간, 시각 |

## 메시지 형식

영어로 한 줄에 쓴다. 일어난 일로 시작하고, 식별자는 자리표시자로 넣는다. 그다음 무엇이 일어나는지(재시도, 다음 실행)를 붙이면 운영자가 손을 대야 하는지 바로 안다.

```kotlin
// 좋다
log.warn("Video queue pull failed; trying again on the next tick", e)
log.warn("Image {} not cleaned up; its record stays for the next run", id, e)
log.info("Image cleanup: removed {}, failed {}", report.removed, report.failed)

// 나쁘다: 맥락이 없다
log.info("Success")

// 나쁘다: 문장이 길고, 개인정보와 글 내용을 담는다
log.info("The user with email john@example.com created a post titled 'Hello World'")

// 나쁘다: 민감한 값
log.info("Token refreshed: {}", newAccessToken)
```

| 규칙 | 이유 |
|---|---|
| 영어로 쓴다 | 정규식 검색과 모니터링 도구에서 다루기 쉽다 |
| 한 줄로 쓴다 | 여러 줄 메시지는 로그 수집기가 나눠 버린다 |
| 호출한 쪽이 아니라 일어난 일로 시작한다 | 검색할 때 사건 이름으로 찾는다 |
| 예외는 마지막 인자로 넘기고, `e.message`를 메시지에 다시 넣지 않는다 | 스택 트레이스에 이미 있다 |

## 계층별 기준

| 계층 | 레벨 | 남기는 것 |
|---|---|---|
| 웹 어댑터(`adapter/provided/web`, `presentation/web`) | 남기지 않는다. 필요하면 debug | 요청 본문은 남기지 않는다. 실패는 컨텍스트의 `*WebExceptionHandler`가 응답으로 바꾼다 |
| 애플리케이션(`application/service`) | 업무 결과는 info, 내부 단계는 debug | 실패를 삼키고 계속 진행할 때는 warn(안내 메일 발송 실패 등) |
| 외부 연동 어댑터(`adapter/required`, `infra`) | 성공은 debug, 외부 서비스 이상은 warn | 연동 결과와 식별자 |
| 예약 작업과 소비자(배치 작업, 영상 큐 폴러, 아웃박스 릴레이) | 실행 결과 한 줄은 info, 항목별 실패는 warn | 처리한 수와 실패한 수. 실패한 항목은 다음 실행이 다시 처리한다고 적는다 |

요청마다 로그를 남기는 AOP는 두지 않는다. 모든 요청이 운영 로그를 채우기 때문이다.
