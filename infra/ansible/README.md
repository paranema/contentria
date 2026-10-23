# Ansible

홈 서버와 AWS Rocky Linux 10 노드에 같은 플레이북을 실행한다. Ansible은 로컬에 설치하지 않고 `ansible-navigator`가 실행하는 EE에 넣는다. EE는 `ansible-builder`로 만든다.

## 맡는 일

| 단계 | 플레이북 | 작업 |
|---|---|---|
| OS 준비 | `10-prepare.yml` | Python 실행 기반, 공통 패키지, 현재 커널의 추가 모듈, UTC 시간대, firewalld 정리 |
| 클러스터 | `20-cluster.yml` | 인벤토리 검사, Kubespray 실행, control plane·worker 겸용 노드의 NoSchedule taint 제거 |
| Argo CD | `30-argocd.yml` | Helm으로 설치, Application health check와 Envoy OCI 저장소 설정 |
| GitOps | `40-gitops.yml` | 필요한 시크릿 존재 확인 후 root Application 적용 |

Kubespray가 containerd, Kubernetes, Calico, swap, sysctl, SELinux, 시간 동기화를 관리한다. 별도 OS role에서 같은 설정을 다시 관리하지 않는다. Terraform은 VM·네트워크·SSH까지 담당한다.

모든 플레이북은 전용 Kubernetes 노드를 대상으로 한다. OS 준비는 firewalld를 중지하므로 홈 네트워크의 라우터·방화벽과 AWS 보안 그룹에서 접근을 제한해야 한다. SELinux는 Kubespray의 permissive 설정을 사용한다. 패키지 전체 업그레이드, 커널 업그레이드, 자동 재부팅은 수행하지 않는다.

## 버전과 두 노드 구성

- Kubespray `v2.31.0`, commit `1c9add48975060f45396b34d8e022c30d7f80dab`을 EE 빌드에서 검증한다
- Ansible `11.13.0`, ansible-core `2.18.12`. Python 의존성은 해당 Kubespray 릴리스와 맞춘다
- Kubernetes `1.35.4`, containerd와 Calico 세부 버전은 고정한 Kubespray 릴리스의 기본값을 따른다
- Argo CD Helm chart `10.10.5`

EE의 Rocky 9/Python 3.11은 실행 컨트롤러 환경이다. 대상 노드의 Rocky 10과 같은 버전일 필요는 없다. 베이스 OS 패키지까지 완전히 고정한 이미지는 아니므로 검증한 EE를 레지스트리에 보관하고 digest로 실행하면 환경을 유지하기 쉽다.

두 서버 모두 control plane·worker로 사용한다. **Kubespray는 짝수 etcd 멤버 수를 거부하므로 etcd는 첫 서버 한 대에 둔다.** 첫 서버 장애 시 기존 Pod가 일부 살아 있어도 클러스터 관리·재스케줄링은 중단된다. 서버 3대 이상에서 etcd 3멤버로 확장한다. 검사를 끄는 `ignore_assert_errors`는 사용하지 않는다.

## EE 빌드

이하 명령은 `infra/ansible`에서 실행한다. Docker가 실행 중이어야 한다.

```bash
ansible-builder build --container-runtime docker --tag contentria-ansible:ks2.31.0-1
```

플레이북이나 인벤토리만 바뀌면 다시 빌드하지 않아도 된다. EE 의존성이나 Kubespray를 바꾸면 태그도 올리고 재검증한다. 런타임은 이미지를 자동 pull하지 않는다. Podman을 쓰면 builder와 navigator 양쪽의 container engine을 변경한다.

Colima/Docker에서 runner 산출물 경로를 쓸 수 있도록 EE 내부 사용자는 root로 실행한다. 원격 노드 접속 계정은 별도의 `ansible_user`이며 sudo는 `become`으로 처리한다. SSH 키나 실제 인벤토리는 이미지에 넣지 않는다. SSH 개인키와 검증한 known_hosts를 별도의 로컬 폴더에 준비하고 읽기 전용으로 마운트한다. 예를 들어 `$HOME/.ssh/contentria`에 `id_ed25519`, `known_hosts`를 둔다. 키 경로는 인벤토리에 넣지 않고 실행 옵션으로 전달한다. 암호화된 키는 interactive 실행이나 SSH agent 연동이 필요할 수 있다.

SSH 호스트 키 검증은 켜져 있다. 처음에는 콘솔 등 신뢰 가능한 경로로 fingerprint를 확인해 known_hosts에 등록한다. VM 재생성 후에는 키가 바뀌므로 새 fingerprint를 확인한 뒤 해당 항목을 갱신한다.

## 인벤토리

공통 설정 파일을 먼저, 환경별 hosts 파일을 뒤에 지정한다. 환경의 `group_vars/all/`이 공통 값을 덮어쓴다.

홈 서버:

```bash
cp inventories/home/hosts.yml.example inventories/home/hosts.yml
cp inventories/home/local.yml.example inventories/home/group_vars/all/local.yml
```

`hosts.yml`의 SSH 계정·주소·실제 NIC 이름과 `local.yml`의 VIP를 수정한다. VIP는 노드와 같은 L2 대역의 미사용 주소이며 DHCP 할당 범위에서 제외해야 한다. 홈에서는 control plane에 kube-vip ARP를 사용한다. 기본 kube-vip는 Service의 LoadBalancer 주소를 할당하지 않는다.

AWS 테스트:

```bash
terraform -chdir=../terraform/aws output -raw ansible_inventory > inventories/cloud-test/hosts.yml
```

Terraform을 apply한 상태에서 실행한다. 이번 구성부터 두 노드의 Terraform 인벤토리도 etcd를 첫 노드에만 둔다. 이전에 생성한 인벤토리는 다시 출력하고 etcd 그룹을 확인한다. AWS에서는 kube-vip를 끄고 Kubespray의 노드 로컬 API 프록시를 사용한다. EC2 공인 IP는 API 인증서 SAN에 포함한다. IP가 바뀌면 인벤토리와 인증서도 갱신해야 한다.

`hosts.yml`, `local.yml`, 실제 host_vars, 실행 산출물은 Git에서 제외한다. 홈·VPC·Pod·Service CIDR이 서로 겹치지 않게 설정한다. 공통 CNI는 두 환경에서 사용할 수 있는 Calico VXLAN이다.

## 실행

홈 서버 예시다. AWS는 `inventories/home/hosts.yml`을 `inventories/cloud-test/hosts.yml`로 바꾼다. `--private-key`와 known_hosts 경로는 **EE 안의 경로**다.

```bash
ansible-navigator run playbooks/10-prepare.yml \
  -i inventories/common.yml -i inventories/home/hosts.yml \
  --eev "$HOME/.ssh/contentria:/run/contentria-ssh:ro" \
  --private-key /run/contentria-ssh/id_ed25519 \
  --ssh-common-args='-o UserKnownHostsFile=/run/contentria-ssh/known_hosts'
```

같은 옵션으로 `20-cluster.yml`, `30-argocd.yml` 순서로 실행한다. sudo에 비밀번호가 필요한 홈 서버는 `--ask-become-pass`를 추가한다. Ansible EE가 먼저 SSH로 접근할 수 있어야 하므로 베어메탈의 OS·네트워크·SSH 계정·sudo 준비까지는 설치 단계에서 한다.

Rocky 10 공식 클라우드 이미지에는 추가 커널 모듈이 없을 수 있다. `10-prepare.yml`은 실행 중인 커널과 정확히 일치하는 `kernel-modules-extra`를 설치한다. 해당 버전이 저장소에 없다면 커널과 모듈을 함께 갱신하고 재부팅한 뒤 다시 실행한다. 이를 플레이북이 자동으로 처리하지는 않는다. [Kubespray Rocky 10 주의사항](https://github.com/kubernetes-sigs/kubespray/blob/v2.31.0/docs/operating_systems/rhel.md#rocky-linux-10)을 따른다.

Argo CD는 첫 control plane에서 `/etc/kubernetes/admin.conf`를 이용해 설치한다. 관리자 kubeconfig를 EE나 로컬에 복사하지 않는다. 서버는 ClusterIP이며, SSH 터널이나 kubectl port-forward로 접근한다. 초기 관리자 비밀번호를 플레이북 출력에 남기지 않는다.

## root 앱 적용 전

`40-gitops.yml`은 실행 즉시 기존 `dev` GitOps의 자동 동기화를 시작한다. 현재 GitOps는 레거시 blog 서비스 이미지와 dev 도메인을 사용한다. 테스트 AWS 클러스터에 그대로 배포하면 홈 운영 설정을 재현하는 것이므로, 먼저 사용할 경로·도메인·데이터 설정을 확인한다.

1. [시크릿 문서](../secrets/README.md)에 따라 실제 값을 만들어 대상 클러스터에 적용한다. 플레이북은 값 대신 시크릿 이름의 존재만 조회한다.
2. 현재 PostgreSQL PVC가 사용할 default StorageClass 또는 명시적 StorageClass를 준비한다. 이 플레이북은 스토리지 드라이버를 설치하지 않는다.
3. Envoy Gateway의 LoadBalancer Service에 IP를 제공할 구성을 준비한다. 홈의 Service용 VIP/주소 풀이나 AWS 클라우드 LB 연동은 control plane용 kube-vip와 별개다.
4. 도메인·라우터 포트 전달·인증서 발급 조건을 준비한다.
5. 같은 navigator 옵션으로 `playbooks/40-gitops.yml`을 실행한다.

시크릿이 누락되면 목록을 보여주고 root 앱 적용 전에 중단한다. root 앱 생성은 하위 애플리케이션 전체의 Healthy를 보장하지 않는다. 이후 진행은 Argo CD에서 확인한다. root 앱 삭제가 모든 자식을 연쇄 삭제하지 않도록 cascade finalizer는 넣지 않았다.

## 검증

아래 검증은 서버에 접속하지 않는다. 저장소 루트를 마운트해야 시크릿 예제와의 일치도 확인한다.

```bash
docker run --rm \
  -v "$(cd ../.. && pwd):/workspace:ro" \
  -w /workspace/infra/ansible \
  contentria-ansible:ks2.31.0-1 \
  /usr/bin/python3.11 tests/verify.py
```

홈·AWS 인벤토리 변수 우선순위, 환경별 VIP, etcd 역할, 네 플레이북과 Kubespray의 syntax check, 정상·잘못된 인벤토리 preflight, 시크릿 예제 목록을 확인한다. 로컬 파일에 실제 주소를 입력하지 않아도 된다.

실제 서버에서는 새 노드 설치 후 재실행의 변경 내역, 재부팅 후 노드 Ready, DNS·Pod 간 통신, API VIP 이동, Argo CD 상태를 추가 검증해야 한다. `--check`는 기존 클러스터 조회를 수행할 수 있고, 처음 설치를 완전히 모사하지 못한다. Kubespray의 업그레이드·노드 추가·삭제는 별도 운영 절차이며 이 설치 플레이북의 재실행으로 대신하지 않는다.
