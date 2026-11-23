# Secrets

수동으로 적용하는 Kubernetes 시크릿 매니페스트 모음입니다. GitOps(Argo CD)는 보안상 이 디렉터리를 직접 관리하지 않습니다.

## 개요

`README.md`와 `example/` 디렉터리만 Git 추적 대상이며, 실제 보안값이 입력된 환경별 디렉터리(`dev/`, `prod/` 등)는 루트 `.gitignore`로 제외합니다.

```text
infra/secrets/
├── README.md
├── example/                                  # 키 이름만 기록한 예시 템플릿 (Git 추적)
│   ├── database.yaml
│   ├── contentria-backend-migrator.yaml
│   ├── contentria-backend-api.yaml
│   ├── contentria-backend-batch.yaml
│   └── contentria-backend-worker.yaml
├── dev/                                      # 개발·테스트 환경 시크릿 (Git 제외)
└── prod/                                     # 운영 환경 시크릿 (Git 제외)
```

## 시크릿 구성

### 시크릿 목록

| 매니페스트 파일                    | 네임스페이스 | Secret 이름                                                                                                                                      | 사용처                                           |
| ---------------------------------- | ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------ |
| `database.yaml`                    | `database`   | `postgres-member-migrator`<br>`postgres-publishing-migrator`<br>`postgres-interaction-migrator`<br>`postgres-notification-migrator`<br>`postgres-media-migrator`<br>`postgres-analytics-migrator` | CloudNativePG 컨텍스트별 마이그레이션 DB 롤(6개) |
| `database.yaml`                    | `database`   | `postgres-api-app`<br>`postgres-batch-app`<br>`postgres-worker-app`                                                                              | CloudNativePG 프로세스별 런타임 DB 롤(3개)       |
| `contentria-backend-migrator.yaml` | `contentria` | `contentria-backend-migrator`                                                                                                                    | 스키마 마이그레이션 Job (`apps/migrator`)        |
| `contentria-backend-api.yaml`      | `contentria` | `contentria-backend-api`                                                                                                                         | 백엔드 HTTP API 서버 (`apps/api`)                |
| `contentria-backend-batch.yaml`    | `contentria` | `contentria-backend-batch`                                                                                                                       | 배치 CronJob (`apps/batch`)                      |
| `contentria-backend-worker.yaml`   | `contentria` | `contentria-backend-worker`                                                                                                                      | 영상 변환 워커 (`apps/worker`)                   |

### 공유 값 정합성

데이터베이스 계정 비밀번호와 Cloudflare R2 자격 증명은 여러 시크릿에서 동일한 값을 참조합니다. 값을 변경할 때 아래 대상도 함께 맞춰야 합니다.

| 기준 시크릿 (`database.yaml`)    | 동일한 비밀번호를 입력해야 하는 위치                               |
| -------------------------------- | ------------------------------------------------------------------ |
| `postgres-<컨텍스트>-migrator`   | `contentria-backend-migrator`의 `<CONTEXT>_MIGRATOR_PASSWORD`      |
| `postgres-api-app`               | `contentria-backend-api`의 `DATABASE_PASSWORD`                     |
| `postgres-batch-app`             | `contentria-backend-batch`의 `DATABASE_PASSWORD`                   |
| `postgres-worker-app`            | `contentria-backend-worker`의 `DATABASE_PASSWORD`                  |

## 적용 절차

1. `example/` 템플릿을 대상 환경 디렉터리(`dev` 또는 `prod`)로 복사하고 실제 값을 채웁니다. 각 애플리케이션 환경 변수의 상세 의미는 `backend/apps/<앱>/.env.sample`을 참고합니다.
   ```bash
   mkdir -p infra/secrets/prod
   cp infra/secrets/example/*.yaml infra/secrets/prod/
   ```
2. GitOps 부트스트랩 전에 클러스터에 시크릿을 적용합니다. 각 파일 상단에 `Namespace` 리소스가 포함되어 있어 네임스페이스가 함께 생성됩니다.
   ```bash
   kubectl apply -f infra/secrets/prod/
   ```
3. `make -C infra ansible-gitops` 실행 시 Ansible이 `required_secrets` 목록 전체의 존재 여부를 검증한 뒤 Root Application을 등록합니다.

## 운영 기준

- 시크릿 키를 추가하거나 제거할 때는 `example/` 템플릿을 함께 수정합니다.
- Secret 리소스를 새로 추가하거나 삭제할 때는 `infra/ansible/roles/gitops_root/defaults/main.yml`의 `required_secrets` 목록도 함께 갱신하고 `make -C infra ansible-verify`로 일치 여부를 검증합니다.
- 실제 시크릿 파일은 로컬에만 존재하므로 비밀번호 관리자에 별도 백업합니다.
