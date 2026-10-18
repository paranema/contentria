# Contentria Backend

바운디드 컨텍스트를 최상위 축으로 둔 모듈러 모놀리스다. 컨텍스트 하나를 떼어 서비스로 만들 수 있게 구성한다.
설계 문서의 색인과 읽는 순서는 [docs/README.md](docs/README.md)에 있다.

레거시 모듈(`blog-*`)은 이 구조와 호환하지 않으며 저장소에서 지웠다. 실행 중인 레거시 컨테이너는 그대로 두고, 소스는 `legacy-final` 태그에 남겼다.
레거시를 고쳐야 하면 그 태그에서 브랜치를 딴다. 빌드와 배포 방법은 그 태그의 이 문서에 있다.

## 디렉터리 구조

```
backend/
├── build-logic/                 Gradle 컨벤션 플러그인
├── gradle/                      의존성 버전 카탈로그(libs.versions.toml), Gradle wrapper
├── shared-kernel/               모든 컨텍스트가 쓰는 공통 기반 (식별자, 예외, 영속성 기반)
├── platform/
│   └── architecture-rules/      아키텍처 규칙 (테스트 전용)
├── contexts/
│   └── member/
│       ├── member-api/          다른 컨텍스트에 공개하는 계약
│       └── member-core/         회원 컨텍스트의 전부
├── apps/                        컨텍스트를 묶어 프로세스로 띄우는 조립 모듈
│   ├── api/                     HTTP API 서버
│   └── batch/                   배치 잡
└── docs/                        설계 문서
```

목표 구조와 각 모듈의 현재 상태는 [docs/architecture/01-module-structure.md](docs/architecture/01-module-structure.md)에 있다.

## 모듈 의존 관계

| 모듈 | 종류 | 의존 모듈 |
|---|---|---|
| `shared-kernel` | 라이브러리 | 없음 |
| `platform:architecture-rules` | 테스트 전용 라이브러리 | 없음 (ArchUnit) |
| `contexts:member:member-api` | 라이브러리 | `shared-kernel` |
| `contexts:member:member-core` | 라이브러리 | `member-api`, `shared-kernel` |
| `apps:api` | 실행 (API 서버) | `member-core` |
| `apps:batch` | 실행 (배치) | 없음 (잡이 생기면 그 컨텍스트의 `-core`) |

`-core` 모듈끼리는 서로 의존하지 않는다. 다른 컨텍스트에는 `-api`로만 의존한다.

## 빌드와 실행

JDK 버전은 `mise.toml`을 따른다.

```bash
./gradlew build
```

```bash
./gradlew :apps:api:bootRun
```
