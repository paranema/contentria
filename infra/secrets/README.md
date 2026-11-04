# Secrets

수동으로 적용하는 Kubernetes 시크릿 매니페스트 모음입니다. GitOps(Argo CD)는 보안상의 이유로 이 디렉터리를 직접 관리하지 않습니다.

`README.md`와 `example/` 디렉터리만 Git 버전 관리 대상이며, 실제 보안값이 입력된 환경별 디렉터리는 `.gitignore`에 의해 제외됩니다.

## 디렉터리 구조

```
infra/secrets/
├── README.md
├── example/                     # 키 이름만 기록한 예시 템플릿 (Git 추적)
│   ├── database.yaml
│   ├── keycloak.yaml
│   ├── openfga.yaml
│   ├── contentria-backend-api.yaml
│   └── contentria-backend-batch.yaml
└── dev/                         # 실제 값이 입력된 매니페스트 (Git 미추적, example/과 동일 구성)
```

## 시크릿 목록

| 매니페스트 파일 | 네임스페이스 | Secret 이름 | 사용처 |
| --- | --- | --- | --- |
| `database.yaml` | `database` | `postgres-contentria` | 서비스 데이터베이스 계정 (CloudNativePG 자동 생성) |
| `database.yaml` | `database` | `postgres-keycloak` | Keycloak DB 계정 (CloudNativePG 자동 생성) |
| `database.yaml` | `database` | `postgres-openfga` | OpenFGA DB 계정 (CloudNativePG 자동 생성) |
| `keycloak.yaml` | `keycloak` | `keycloak-admin` | Keycloak 최초 관리자 자격 증명 |
| `keycloak.yaml` | `keycloak` | `keycloak-db` | Keycloak DB 연결 비밀번호 |
| `openfga.yaml` | `openfga` | `openfga-datastore` | OpenFGA DB 연결 URI |
| `contentria-backend-api.yaml` | `blog` | `contentria-backend-api` | 백엔드 API 서비스 |
| `contentria-backend-batch.yaml` | `blog` | `contentria-backend-batch` | 배치 스케줄러 (CronJob) |

데이터베이스 비밀번호는 여러 시크릿 간에 공유됩니다. `database.yaml`의 값을 변경할 경우 아래 참조 위치도 동일하게 갱신해야 합니다.

| 원본 (`database.yaml`) | 동일 값을 참조하는 위치 |
| --- | --- |
| `postgres-contentria` | `contentria-backend-api`, `contentria-backend-batch`의 `DATABASE_PASSWORD` |
| `postgres-keycloak` | `keycloak-db`의 `password` |
| `postgres-openfga` | `openfga-datastore`의 `uri` |

## 적용 절차

1. `example/` 템플릿을 환경별 디렉터리(`<env>/`)로 복사하고 실제 값을 입력합니다.

   ```bash
   mkdir -p infra/secrets/dev
   cp infra/secrets/example/*.yaml infra/secrets/dev/
   ```

2. 클러스터에 시크릿을 직접 적용합니다. 각 매니페스트 상단에 `Namespace` 리소스가 포함되어 있어 사전에 네임스페이스가 생성됩니다.

   ```bash
   kubectl apply -f infra/secrets/dev/
   ```

3. Ansible 또는 GitOps 동기화 전 `required_secrets` 검증을 통해 시크릿 존재 여부를 확인합니다.

## 운영 규칙

- 템플릿 키를 추가하거나 제거할 때는 `example/` 매니페스트를 함께 수정합니다.
- 신규 시크릿을 추가하면 상기 목록 및 Ansible `required_secrets` 변수에 등록합니다.
- 실제 시크릿 파일은 로컬에만 존재하므로 1Password 등 팀 공용 비밀번호 관리자에 안전하게 백업합니다.
