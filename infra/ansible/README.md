# Ansible

Rocky Linux 10 노드에 Kubernetes 클러스터와 Argo CD를 설치하고 GitOps 진입점을 등록하는 Ansible 구성입니다.

## 개요

### 담당 범위

베어메탈 서버와 AWS 노드에 동일한 플레이북을 실행합니다. 컨테이너 런타임(containerd), Kubernetes(`1.35.4`), CNI(Calico VXLAN), swap, sysctl, SELinux(permissive), 시간 동기화는 Kubespray(`v2.31.0`)가 관리하며 별도 역할에서 중복 설정하지 않습니다.

모든 플레이북은 전용 Kubernetes 노드를 전제로 합니다. OS 준비 단계에서 `firewalld`를 중지하므로 접근 제어는 상위 라우터 방화벽이나 AWS 보안 그룹에서 처리합니다. 패키지 전체 업그레이드, 커널 업그레이드, 자동 재부팅은 수행하지 않습니다.

### 디렉터리 구성

| 경로                  | 역할                                                               |
| --------------------- | ------------------------------------------------------------------ |
| [`ee/`](ee/README.md) | `ansible-navigator`용 Execution Environment 컨테이너 이미지 정의   |
| `inventories/`        | 공통 변수(`common.yml`)와 플랫폼별 인벤토리(`bare-metal`, `aws`)   |
| `playbooks/`          | 단계별 실행 플레이북 (`10` ~ `40`)                                 |
| `roles/`              | OS 사전 준비, Argo CD 설치(`10.10.5`), GitOps Root 앱 배포 역할    |
| `tests/`              | 서버 접속 없이 수행하는 정적 검증 스크립트                         |

### 노드 토폴로지

모든 노드는 Control Plane과 Worker 역할을 겸합니다. Kubespray는 짝수 `etcd` 멤버 구성을 거부하므로 노드 3대 미만에서는 첫 번째 노드 1대에만 `etcd`를 배치합니다. 단일 `etcd` 노드가 중단되면 클러스터 관리와 재스케줄링이 멈추며, 노드를 3대 이상으로 구성할 때 `etcd`를 3멤버로 확장합니다.

## 사전 준비

### 실행 환경

로컬에 Ansible을 직접 설치하지 않고 Docker와 `ansible-navigator`로 Execution Environment(`contentria-ansible:ks2.31.0-1`) 컨테이너를 실행합니다. 최초 실행 전 이미지 빌드가 필요하며, 상세 절차는 [Execution Environment 가이드](ee/README.md)를 참고합니다.

```bash
make -C infra ansible-ee
```

### SSH 설정

호스트 키 검증(`host_key_checking = True`)이 활성화되어 있습니다. 로컬의 별도 디렉터리(기본 `~/.ssh/contentria`)에 개인키(`id_ed25519`)와 지문을 확인한 `known_hosts` 파일을 준비하여 컨테이너에 읽기 전용으로 마운트합니다. 노드를 재생성해 호스트 키가 바뀌면 새 지문을 확인한 뒤 `known_hosts`를 갱신합니다.

### 인벤토리 구성

실행 시 공통 설정(`inventories/common.yml`)을 먼저 지정하고 플랫폼별 `hosts.yml`을 뒤에 지정하여 환경 변수(`group_vars/all/`)가 공통 값을 덮어쓰도록 합니다. 로컬 네트워크, VPC, Pod, Service CIDR 대역은 서로 겹치지 않아야 하며, 실제 `hosts.yml`과 `local.yml`은 Git 추적에서 제외합니다.

- **베어메탈 (`inventories/bare-metal/`)**: 예시 파일을 복사한 뒤 실제 노드 주소, SSH 계정, NIC 이름과 미사용 VIP(DHCP 제외 대역)를 입력합니다. Control Plane 엔드포인트에는 `kube-vip` ARP 모드를 사용합니다.
  ```bash
  cp inventories/bare-metal/hosts.yml.example inventories/bare-metal/hosts.yml
  cp inventories/bare-metal/local.yml.example inventories/bare-metal/group_vars/all/local.yml
  ```
- **AWS (`inventories/aws/`)**: Terraform 배포 결과에서 인벤토리를 추출합니다. `kube-vip` 대신 노드 로컬 API 프록시를 사용하며, EC2 공인 IP를 API 인증서 SAN에 포함합니다. 공인 IP가 변경되면 인벤토리와 인증서를 함께 갱신해야 합니다.
  ```bash
  make -C infra tf-inventory
  ```

## 실행 절차

### 플레이북 구성

| 단계        | 플레이북         | 작업 내용                                                                                           |
| ----------- | ---------------- | --------------------------------------------------------------------------------------------------- |
| 1. OS 준비  | `10-prepare.yml` | Python 실행 기반, 필수 패키지 및 `bash-completion`, 현재 커널의 `kernel-modules-extra`, UTC 시간대, `firewalld` 비활성화 |
| 2. 클러스터 | `20-cluster.yml` | 인벤토리 사전 검증, Kubespray 클러스터 설치(`k` 별칭 포함), 겸용 노드의 `NoSchedule` taint 제거, 접속 계정 kubeconfig 배치 |
| 3. Argo CD  | `30-argocd.yml`  | Helm 기반 Argo CD 설치, Application health check, Envoy OCI 저장소 설정                             |
| 4. GitOps   | `40-gitops.yml`  | 필수 시크릿 존재 여부 확인 후 Root Application 등록                                                 |

### 클러스터 구축

AWS 테스트 환경은 `infra/Makefile` 단축 명령어로 1~2단계를 순서대로 실행합니다.

```bash
cd infra
make ansible-prepare
make ansible-cluster
```

베어메탈 환경 또는 `ansible-navigator`를 직접 실행할 때는 `infra/ansible` 디렉터리에서 대상 인벤토리를 지정합니다. `--private-key`와 `known_hosts`는 컨테이너 내부 마운트 경로 기준이며, `sudo` 비밀번호가 필요한 환경에서는 `--ask-become-pass`를 추가합니다.

```bash
ansible-navigator run playbooks/10-prepare.yml \
  -i inventories/common.yml -i inventories/bare-metal/hosts.yml \
  --eev "$HOME/.ssh/contentria:/run/contentria-ssh:ro" \
  --private-key /run/contentria-ssh/id_ed25519 \
  --ssh-common-args='-o UserKnownHostsFile=/run/contentria-ssh/known_hosts'
```

Rocky 10 클라우드 이미지에서 실행 중인 커널과 일치하는 `kernel-modules-extra` 패키지가 저장소에 없으면 1단계가 실패합니다. 이 경우 노드의 커널과 모듈을 함께 업데이트하고 재부팅한 뒤 다시 실행합니다.

### Argo CD 설치

클러스터 구축이 완료되면 첫 번째 Control Plane 노드의 `/etc/kubernetes/admin.conf`를 이용해 Argo CD를 설치합니다. 관리자 kubeconfig를 로컬이나 컨테이너로 복사하지 않으며, 서비스 타입은 `ClusterIP`이므로 SSH 터널이나 `kubectl port-forward`로 접근합니다.

```bash
make -C infra ansible-argocd
```

### GitOps 부트스트랩

`40-gitops.yml`은 실행 즉시 `dev` 환경 매니페스트의 자동 동기화를 시작합니다. 실행 전 아래 항목을 먼저 준비합니다.

1. [Secrets 가이드](../secrets/README.md)에 따라 필수 시크릿을 클러스터에 적용합니다. 플레이북은 시크릿 값은 읽지 않고 이름 존재 여부만 확인하며, 누락 시 적용 전에 중단합니다.
2. PostgreSQL PVC가 사용할 기본 StorageClass(또는 명시적 StorageClass)를 준비합니다. 본 플레이북은 스토리지 드라이버를 설치하지 않습니다.
3. Envoy Gateway의 `LoadBalancer` Service에 외부 IP를 할당할 구성을 준비합니다.
4. 사용할 도메인, 포트 전달, 인증서 발급 경로를 확인합니다.

사전 조건이 충족되면 4단계를 실행하고 Argo CD에서 하위 Application 동기화 상태를 확인합니다.

```bash
make -C infra ansible-gitops
```

## 검증

### 정적 검증

원격 노드에 접속하지 않고 EE 컨테이너 내부에서 인벤토리 변수 우선순위, 환경별 VIP 및 `etcd` 규칙, 4개 플레이북과 Kubespray 구문, preflight 어설션, 시크릿 예제 일치 여부를 검사합니다.

```bash
make -C infra ansible-verify
```

### 동작 검증

실제 노드 설치 후에는 플레이북 재실행 시 변경 유무, 재부팅 후 노드 `Ready` 복구, CoreDNS 및 Pod 간 통신, 베어메탈 환경의 API VIP 이동, Argo CD 상태를 추가로 확인합니다. `--check` 모드는 초기 클러스터 미설치 상태를 온전히 모사하지 못하며, 클러스터 업그레이드나 노드 추가·제거는 설치 플레이북 재실행이 아닌 별도 Kubespray 운영 절차를 따릅니다.
