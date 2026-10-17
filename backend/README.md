# Contentria Backend

## 디렉토리 구조

레이어드 아키텍처로 작성한 기존 모듈(`blog-*`)을 헥사고날 아키텍처 모듈(`core`, `app/*`)로 옮기는 중이다. 이전이 끝날 때까지 두 구조가 함께 있다. 현재 배포되는 모듈은 `blog-*`이다.

```
backend/
├── blog-common/          [레이어드] 공용 라이브러리. 여러 모듈이 함께 쓰는 엔티티, 설정, 예외
├── blog-api/             [레이어드] HTTP API 서버
├── blog-batch/           [레이어드] 배치 잡
├── blog-worker/          [레이어드] 메시지 컨슈머 (영상 변환)
│
├── core/                 [헥사고날] 도메인 로직 라이브러리. 실행 jar를 만들지 않는다
├── app/                  [헥사고날] 배포 단위 모듈을 묶는 폴더. 코드와 빌드 스크립트가 없다
│   ├── api/              HTTP API 서버
│   └── batch/            배치 잡
│
├── build-logic/          Gradle 컨벤션 플러그인
├── gradle/               의존성 버전 카탈로그(libs.versions.toml), Gradle wrapper
└── docs/                 설계 문서. 색인과 읽는 순서는 docs/README.md
```

### 모듈 의존 관계

| 모듈        | 구조     | 종류            | 의존 모듈         |
| ----------- | -------- | --------------- | ----------------- |
| blog-common | 레이어드 | 라이브러리      | core              |
| blog-api    | 레이어드 | 실행 (API 서버) | blog-common       |
| blog-batch  | 레이어드 | 실행 (배치)     | blog-common, core |
| blog-worker | 레이어드 | 실행 (컨슈머)   | blog-common       |
| core        | 헥사고날 | 라이브러리      | 없음              |
| app/api     | 헥사고날 | 실행 (API 서버) | core              |
| app/batch   | 헥사고날 | 실행 (배치)     | core              |

`core`는 다른 모듈을 의존하지 않는다. 의존 방향은 항상 실행 모듈에서 라이브러리 쪽이다.

### 레이어드 모듈 패키지

`blog-api`는 도메인별 패키지 안에 계층을 나눈다. 도메인마다 필요한 계층만 둔다.

```
com.contentria.api.<도메인>/
├── controller/           HTTP 요청, 응답 처리
├── application/          서비스 (유스케이스)
├── domain/               엔티티, 리포지토리 인터페이스
└── infrastructure/       리포지토리 구현, 외부 연동
```

### 헥사고날 모듈 패키지

최상위 패키지는 계층이 아니라 바운디드 컨텍스트다. 컨텍스트 목록은 [docs/strategic/03-bounded-contexts.md](docs/strategic/03-bounded-contexts.md)에 있다.

```
com.contentria.core.<컨텍스트>/
├── domain/               도메인 모델, 비즈니스 규칙
├── application/
│   ├── provided/         인바운드 포트. 이 컨텍스트가 외부에 제공하는 유스케이스
│   └── required/         아웃바운드 포트. 이 컨텍스트가 필요로 하는 외부 기능 (저장, 외부 API, 다른 컨텍스트)
└── adapter/
    └── required/         아웃바운드 포트 구현 (JPA, 외부 API 클라이언트)

com.contentria.core.shared/
├── exception/            공통 예외
├── id/                   식별자 생성
└── persistence/          엔티티 공통 필드, JPA 설정
```

`app/*`는 모듈 전체가 인바운드 어댑터다. 컨트롤러와 배치 잡이 `core`의 인바운드 포트를 호출한다. 패키지는 `com.contentria.api.<컨텍스트>`처럼 컨텍스트 단위로 나눈다.

## 빌드와 실행

```bash
# API 서버 실행
./gradlew :blog-api:bootRun

# 배치 실행
./gradlew :blog-batch:bootRun

# 전체 모듈 빌드
./gradlew build
```

## Docker 빌드와 배포 (레거시)

> GitOps 도입 전에 쓰던 방식이다. 이미지를 레지스트리를 거치지 않고 서버에 직접 옮긴다.

Dockerfile은 미리 빌드된 jar를 이미지에 담기만 하고, Gradle을 실행하지 않는다. 호스트에서 jar를 먼저 빌드한 뒤 이미지를 만들어 원격 서버로 옮긴다.

```bash
# 1. backend 루트로 이동
cd backend/

# 2. jar 빌드
./gradlew :blog-api:bootJar

# 3. Docker 이미지 빌드
docker build -t contentria/blog-api:1.0 -f blog-api/Dockerfile .

# 4. 이미지를 tar로 저장
docker save -o blog-api.tar contentria/blog-api:1.0

# 5. 원격 서버로 전송
scp blog-api.tar <username>@<remote-host>:~
```

> `blog-batch`, `blog-worker`도 같은 순서로 진행한다. 2~4단계의 모듈 이름만 바꾼다(`:blog-batch:bootJar`, `-f blog-batch/Dockerfile`, `blog-batch.tar` 등).

원격 서버에서 실행한다.

```bash
# 1. 서버 접속
ssh <username>@<remote-host>

# 2. 이미지 가져오기
sudo ctr -n k8s.io images import blog-api.tar

# 3. 확인
sudo crictl images
```
