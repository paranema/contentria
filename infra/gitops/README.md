# GitOps

ArgoCD가 감시하는 배포 매니페스트 모음이다. 클러스터 구축과 ArgoCD 설치는 [Ansible EE](../ansible/README.md)가 담당하고, 그 이후의 배포는 ArgoCD가 이 폴더를 기준으로 동기화한다.

## 배포 흐름

| 단계 | 도구                | 작업                                                                                         |
| ---- | ------------------- | -------------------------------------------------------------------------------------------- |
| 1    | Terraform           | AWS 테스트 VM과 네트워크 생성                                                                       |
| 2    | Ansible (kubespray) | 쿠버네티스 클러스터 구축, kube-vip 설정                                                      |
| 3    | Ansible             | ArgoCD 설치, 시크릿 존재 확인, 환경별 root 앱 적용                                           |
| 4    | ArgoCD              | root 앱이 `01-bootstrap/<env>/`를 감시하고, 그 안의 Application이 platform과 services를 배포 |

Ansible은 OS 준비, Kubespray, Argo CD 설치, root 앱 적용을 별도 플레이북으로 제공한다. root 앱을 적용하기 전에 시크릿과 스토리지, LoadBalancer IP 할당을 준비한다. 자세한 실행 순서는 [Ansible 문서](../ansible/README.md)를 따른다.

## 디렉토리 구조

```
gitops/
├── 01-bootstrap/                          # 환경별 Application 정의와 배포 순서
│   └── dev/                               # dev 클러스터의 root 앱이 감시
│       ├── 01-envoy-gateway.yaml
│       ├── 01-postgres-operator.yaml
│       ├── 02-cert-manager.yaml
│       ├── 02-postgres-cluster.yaml
│       ├── 03-keycloak.yaml
│       ├── 03-openfga.yaml
│       └── 04-services.yaml               # ApplicationSet: 03-services/*/overlays/dev마다 앱 생성
│
├── 02-platform/                           # 클러스터 공용 컴포넌트
│   ├── cert-manager/
│   │   ├── values.yaml
│   │   ├── values-dev.yaml
│   │   ├── base/
│   │   │   ├── kustomization.yaml
│   │   │   └── cluster-issuer.yaml        # Let's Encrypt 발급자
│   │   └── overlays/
│   │       └── dev/
│   │           └── kustomization.yaml
│   ├── envoy-gateway/
│   │   ├── values.yaml
│   │   ├── values-dev.yaml
│   │   ├── base/
│   │   │   ├── kustomization.yaml
│   │   │   └── gateway.yaml               # GatewayClass, Gateway(외부 진입점), HTTPS 리다이렉트
│   │   └── overlays/
│   │       └── dev/
│   │           ├── kustomization.yaml
│   │           └── gateway-patch.yaml     # 도메인, 인증서
│   ├── postgres/
│   │   ├── operator/
│   │   │   ├── values.yaml                # CNPG operator
│   │   │   └── values-dev.yaml
│   │   └── cluster/
│   │       ├── base/
│   │       │   ├── kustomization.yaml
│   │       │   ├── cluster.yaml           # Postgres 클러스터
│   │       │   ├── databases.yaml         # keycloak, openfga용 DB (블로그 DB는 cluster.yaml의 initdb)
│   │       │   └── scheduled-backup.yaml  # 백업 저장소 구성 전까지 미적용
│   │       └── overlays/
│   │           └── dev/
│   │               ├── kustomization.yaml
│   │               └── cluster-patch.yaml # 인스턴스 수, 스토리지 크기
│   ├── keycloak/
│   │   ├── values.yaml
│   │   └── values-dev.yaml
│   └── openfga/
│       ├── values.yaml
│       └── values-dev.yaml
│
└── 03-services/                           # 직접 개발한 배포 단위
    ├── blog-backend-api/
    │   ├── base/
    │   │   ├── kustomization.yaml
    │   │   ├── deployment.yaml
    │   │   ├── service.yaml
    │   │   ├── configmap.yaml
    │   │   └── httproute.yaml
    │   └── overlays/
    │       └── dev/
    │           ├── kustomization.yaml     # 이미지 태그, replicas
    │           ├── configmap-patch.yaml
    │           └── httproute-patch.yaml   # 도메인
    ├── blog-backend-batch/
    │   ├── base/
    │   │   ├── kustomization.yaml
    │   │   ├── configmap.yaml
    │   │   ├── daily-statistics-cronjob.yaml
    │   │   └── refresh-token-cleanup-cronjob.yaml
    │   └── overlays/
    │       └── dev/
    │           ├── kustomization.yaml
    │           └── configmap-patch.yaml
    └── blog-frontend-web/
        ├── base/
        │   ├── kustomization.yaml
        │   ├── deployment.yaml
        │   ├── service.yaml
        │   ├── configmap.yaml
        │   ├── httproute.yaml
        │   └── backend-traffic-policy.yaml # 쿠키 기반 고정 세션
        └── overlays/
            └── dev/
                ├── kustomization.yaml
                ├── configmap-patch.yaml
                └── httproute-patch.yaml
```

## 배포 순서

`01-bootstrap/<env>/`의 Application마다 `argocd.argoproj.io/sync-wave`를 지정해 순서를 정한다. root 앱은 앞 wave의 Application이 Healthy가 된 뒤 다음 wave를 배포한다.

| wave | Application                      | 의존 대상                       |
| ---- | -------------------------------- | ------------------------------- |
| 1    | envoy-gateway, postgres-operator | 없음                            |
| 2    | cert-manager                     | envoy-gateway (Gateway API CRD) |
| 2    | postgres-cluster                 | postgres-operator               |
| 3    | keycloak, openfga                | postgres-cluster                |
| 4    | services (ApplicationSet)        | platform 전체                   |

cert-manager가 Gateway를 감시하려면 Gateway API CRD가 먼저 있어야 한다. CRD는 envoy-gateway 차트가 설치하므로 envoy-gateway를 먼저 배포한다. Gateway는 cert-manager가 인증서를 발급하기 전까지 HTTPS 리스너만 준비되지 않은 상태로 대기한다.

파일명 규칙은 다음과 같다.

- 파일명 번호는 sync-wave 값과 같게 둔다.
- 같은 wave는 같은 번호를 쓴다. 같은 번호끼리는 동시에 배포된다.
- 순서를 바꿀 때는 파일명과 어노테이션을 함께 바꾼다.

`03-services/` 안의 서비스끼리는 배포 순서를 두지 않는다. 프론트엔드가 백엔드를 호출하는 것은 실행 중 의존이므로, 배포 순서 대신 API 하위 호환으로 맞춘다.

## platform 컴포넌트 구성

platform 컴포넌트는 두 종류의 리소스로 나뉜다.

| 구분           | 내용                                                  | 관리 방식                                 |
| -------------- | ----------------------------------------------------- | ----------------------------------------- |
| 설치           | 외부 차트가 제공하는 컨트롤러, CRD, webhook           | Helm (`values.yaml`, `values-<env>.yaml`) |
| 설치 후 리소스 | 설치된 CRD로 직접 만드는 리소스. 차트에 포함되지 않음 | Kustomize (`base/`, `overlays/<env>/`)    |

| 컴포넌트      | 설치 (Helm)                              | 설치 후 리소스 (Kustomize)                  | Application 수 |
| ------------- | ---------------------------------------- | ------------------------------------------- | -------------- |
| cert-manager  | cert-manager 컨트롤러, CRD               | `ClusterIssuer` (Let's Encrypt 발급자)      | 1              |
| envoy-gateway | Envoy Gateway 컨트롤러, Gateway API CRD  | `GatewayClass`, `Gateway`, HTTPS 리다이렉트 | 1              |
| postgres      | `operator/`: CloudNativePG operator, CRD | `cluster/`: Postgres 클러스터, DB           | 2              |

Application 수가 다른 이유는 다른 앱이 설치 후 리소스의 준비를 기다리는지 여부다.

- **cert-manager, envoy-gateway**: Helm 차트와 Kustomize 리소스를 Application 하나의 multi-source로 배포한다. Kustomize 리소스에 앱 내부 sync-wave `1`을 달아 차트보다 늦게 적용한다. 다른 앱이 이 리소스의 준비를 기다리지 않으므로 앱 내부 순서로 충분하다.
- **postgres**: keycloak, openfga가 Postgres 클러스터 준비를 기다려야 한다. 클러스터 생성은 몇 분이 걸리므로 operator와 클러스터를 별도 Application으로 나누고, wave로 health를 확인한 뒤 다음 단계로 넘어간다.

## 환경 분리

환경마다 클러스터와 ArgoCD를 따로 둔다. 각 클러스터의 root 앱은 자기 환경의 `01-bootstrap/<env>/`만 감시한다. 어느 환경인지는 Ansible이 root 앱을 적용할 때 인벤토리 변수 `gitops_env`로 정한다. root 앱의 `path`는 `infra/gitops/01-bootstrap/{{ gitops_env }}`이다.

| 대상                      | 공통          | 환경별                              |
| ------------------------- | ------------- | ----------------------------------- |
| Application 정의          | 없음          | `01-bootstrap/<env>/`               |
| Helm 차트                 | `values.yaml` | `values-<env>.yaml`                 |
| 일반 매니페스트 (CR 포함) | `base/`       | `overlays/<env>/`                   |
| Secret                    | 없음          | `infra/secrets/<env>/` (git 미추적) |

- Helm은 뒤에 지정한 values 파일이 앞의 값을 덮어쓴다. Application의 `valueFiles`에 `values.yaml`, `values-<env>.yaml` 순서로 지정한다.
- Secret은 이 폴더에 두지 않는다. [시크릿](#시크릿) 참고.
- 이미지 태그는 `overlays/<env>/kustomization.yaml`의 `images` 항목에서 관리한다.

prod 환경은 다음 순서로 추가한다.

1. `01-bootstrap/dev/`를 `01-bootstrap/prod/`로 복사하고, 파일 안의 `dev` 경로를 `prod`로 바꾼다.
2. Helm 차트마다 `values-prod.yaml`을 추가한다.
3. `base/`가 있는 곳마다 `overlays/prod/`를 추가한다.
4. `infra/secrets/prod/`에 시크릿 파일을 만든다.
5. Ansible prod 인벤토리에 `gitops_env: prod`와 `required_secrets`를 지정한다.

## 시크릿

시크릿은 GitOps 대상에서 제외하고, `infra/secrets/<env>/`에 모아 수동으로 적용한다. 실제 시크릿 파일은 git에 올리지 않고, 키 이름만 적은 `example/`만 git에 남긴다. 자세한 사용 방법은 `infra/secrets/README.md`에 있다.

```
infra/secrets/
├── README.md                    # 시크릿 목록과 적용 방법 (git 추적)
├── example/                     # 키 이름만 기록 (git 추적)
│   └── blog-backend-api.yaml
└── dev/                         # 실제 값 (git 미추적)
    └── blog-backend-api.yaml
```

```gitignore
infra/secrets/*
!infra/secrets/README.md
!infra/secrets/example/
```

- 예시를 `<env>/` 밖에 두는 이유는 `kubectl apply -f infra/secrets/dev/`로 적용할 때 예시 파일이 함께 적용되지 않게 하기 위해서다.
- 실제 파일은 `example/`의 파일을 복사해 값을 채운다. 키를 추가하거나 빼면 `example/`도 함께 고친다.
- 시크릿 파일에 `Namespace` 리소스를 함께 둔다. root 앱 적용 전에는 ArgoCD가 namespace를 아직 만들지 않았기 때문이다.
- 서비스뿐 아니라 platform 컴포넌트의 시크릿(keycloak 관리자 계정, DB 접속 정보, 백업 저장소 접근 키 등)도 포함한다.
- Ansible은 root 앱을 적용하기 전에 인벤토리 변수 `required_secrets`에 적힌 시크릿이 클러스터에 있는지 확인한다. 하나라도 없으면 누락 목록을 출력하고 중단한다.
- 원본이 로컬에만 있으므로 비밀번호 관리자 등 별도 위치에 백업한다.

## 설정 시 주의사항

- **Application health check**: ArgoCD 1.8부터 Application 리소스의 기본 health check가 빠졌다. `argocd-cm`에 `resource.customizations.health.argoproj.io_Application`을 추가하지 않으면 wave를 기다리지 않고 모두 동시에 배포된다.
- **CRD보다 먼저 적용되는 CR**: `cluster-issuer.yaml`, `gateway.yaml`은 같은 Application의 Helm 차트가 설치하는 CRD를 사용한다. 최초 배포 시 CRD가 없어 실패할 수 있으므로 리소스에 `SkipDryRunOnMissingResource=true`와 sync-wave `1`을 달아 차트보다 늦게 적용하고, Application에 `syncPolicy.retry`를 지정한다.
- **큰 CRD**: envoy-gateway, postgres-operator, cert-manager의 CRD는 client-side apply의 annotation 크기 제한을 넘을 수 있으므로 `ServerSideApply=true`로 동기화한다.
- **OCI 차트**: envoy-gateway 차트는 OCI 레지스트리(`docker.io/envoyproxy`)에서 받는다. ArgoCD 버전에 따라 레포지토리를 `enableOCI: "true"`로 등록해야 할 수 있다.
- **root 앱 위치**: root 앱 정의는 이 폴더가 아니라 Ansible role 템플릿에 둔다. `01-bootstrap/` 안에 두면 root 앱이 자기 자신을 관리하게 된다.
