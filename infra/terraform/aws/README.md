# AWS 테스트 노드

Rocky Linux 10 VM을 만들어 Ansible/Kubespray를 시험한다. 기본값은 서울 리전의 같은 가용 영역에 있는 노드 2대다. Kubernetes나 Argo CD를 설치하는 코드는 아직 없다.

## 만드는 리소스

- 전용 VPC(`/16`), 퍼블릭 서브넷(`/24`), 인터넷 게이트웨이, 라우팅
- SSH 공개키를 등록한 EC2 key pair와 노드 보안 그룹
- 기본 `t3.medium` 2대, 노드별 암호화 gp3 루트 디스크 40 GiB
- 공인 IPv4와 고정 사설 IPv4. SSH는 공인 IP로, 노드 간 통신은 사설 IP로 한다

NAT Gateway, EIP, 로드밸런서는 만들지 않는다. EC2, EBS, 공인 IPv4와 데이터 전송에는 AWS 비용이 발생한다. T3는 standard credit 모드로 두어 추가 CPU credit 과금을 피하지만, credit 소진 시 성능이 제한된다. 다른 인스턴스 타입의 과금과 CPU 호환성은 별도로 확인한다.

노드 보안 그룹 안에서는 모든 프로토콜을 허용한다. 외부에서는 `admin_cidrs`에 지정한 IPv4 대역만 SSH(22)와 Kubernetes API(6443)에 접근할 수 있다. HTTP, HTTPS, NodePort는 열지 않는다. 아웃바운드는 패키지와 컨테이너 다운로드를 위해 허용한다.

## 준비

- Terraform 1.7 이상, 2.0 미만. mock provider 테스트에도 1.7 이상이 필요하다
- AWS CLI 인증 프로필 또는 AWS provider의 표준 인증 환경. 자격 증명은 코드나 tfvars에 넣지 않는다
- 해당 리전에서 사용할 **공식 Rocky Linux 10 x86_64** AMI ID와 소유 계정 ID
- SSH 공개키와 관리자 공인 IPv4 CIDR

[Rocky Linux 다운로드](https://rockylinux.org/download)나 [공식 AWS Marketplace 이미지](https://aws.amazon.com/marketplace/pp/prodview-z7hzo3jvdgrmi)에서 리전과 이미지를 선택한다. Marketplace를 이용하면 먼저 해당 계정에서 사용 조건에 동의해야 할 수 있다. 이름이 비슷한 타사 유료 이미지와 구분한다. 기본 로그인 계정은 `rocky`이며 sudo 권한이 있다.

콘솔에서 선택한 이미지의 메타데이터를 확인한다.

```bash
aws ec2 describe-images --region ap-northeast-2 \
  --image-ids ami-선택한이미지ID \
  --query 'Images[].{ID:ImageId,Owner:OwnerId,Name:Name,Architecture:Architecture}'
```

출력된 소유 계정이 공식 배포 경로의 값과 일치하는지 확인한 뒤 `ami_id`, `ami_owner_id`를 입력한다. 단순히 검색 결과에서 첫 이미지를 고르지 않는다. Terraform은 ID, 소유 계정, x86_64, EBS/HVM, available 상태와 Rocky 10 이름을 확인한다. 소유 계정 입력 자체가 공식 공급자를 자동 인증하는 것은 아니다.

AMI는 리전마다 다르므로 예제에 실제 ID를 하드코딩하지 않았다. `most_recent`도 사용하지 않는다. OS 이미지를 올릴 때 명시적으로 ID를 바꾸고 노드 교체 계획을 검토한다. Rocky 10 x86_64는 x86-64-v3 CPU가 필요하므로 홈 서버의 CPU도 별도로 확인한다.

## 실행

이 디렉터리에서 실행한다. `AWS_PROFILE`은 자신의 프로필로 바꾼다.

```bash
export AWS_PROFILE=contentria-test
cp terraform.tfvars.example terraform.tfvars
# terraform.tfvars의 AMI, 소유 계정, 관리자 CIDR, SSH 공개키를 채운다.
terraform init
terraform fmt -check -recursive
terraform validate
terraform test
terraform plan -out=cluster.tfplan
terraform apply cluster.tfplan
terraform output nodes
terraform output -raw ansible_inventory > inventory.yaml
```

이 PR의 테스트는 mock provider를 써서 AWS API 호출이나 리소스 생성 없이 계획을 검사한다. 실제 `plan`은 AWS 인증과 이미지 조회 권한이 필요하며, `apply`는 VPC·EC2 등의 생성 권한과 비용을 수반한다. apply는 VM 생성까지 기다리지만 OS의 SSH 준비 완료까지 보장하지는 않는다.

출력의 공인 IP에 대응하는 개인키로 접속한다.

```bash
ssh -i ~/.ssh/id_ed25519 rocky@공인IP
```

개인키는 로컬 SSH agent나 Ansible 실행 옵션으로 전달한다. Terraform은 개인키를 생성하거나 state에 보관하지 않는다. 공인 IP는 stop/start 시 바뀔 수 있으므로 다시 `terraform apply -refresh-only`로 상태를 갱신하고 인벤토리를 출력한다. `inventory.yaml`과 state, tfvars, 저장된 plan은 Git에서 제외한다. provider lock 파일은 커밋한다.

## Ansible과의 경계

생성된 YAML은 Kubespray의 `kube_control_plane`, `kube_node`, `etcd`, `k8s_cluster` 그룹을 포함한다. 모든 노드를 세 역할에 넣으며 노드 수는 1~10대로 변경할 수 있다. 기본 2멤버 etcd는 한 대 장애를 허용하지 않는다. 3대로 늘려도 모두 같은 AZ이므로 AZ 장애를 견디는 운영 구성이 아니다.

인벤토리를 만들었다고 클러스터가 설치되거나 control plane taint가 제거되지는 않는다. 다음 작업은 Ansible에서 한다.

- SSH 준비 대기, Python 등 실행 전제 확인, 공통 패키지·OS 설정
- 고정 버전 Kubespray로 containerd, Kubernetes, CNI 구성
- control plane에서도 워크로드를 실행하도록 taint 정책 설정
- Argo CD 설치와 root Application 적용

Kubespray가 관리하는 OS 설정과 별도 플레이북의 설정이 겹치지 않게 한다. [Kubespray](https://github.com/kubernetes-sigs/kubespray)는 작성 시점에 Rocky Linux 10을 experimental로 표시한다. 선택한 릴리스의 지원 조건과 설치·재실행·재부팅을 검증한 후 홈 서버에 적용한다.

AWS에서는 홈 LAN의 kube-vip ARP 구성을 그대로 사용하지 않는다. 이 단계는 단일 API endpoint나 HA 로드밸런서를 제공하지 않는다. 클러스터 설치 단계에서 Kubespray의 노드 로컬 API 프록시 등 endpoint 방식을 정하고, 외부 관리자는 선택한 control plane 주소를 사용한다. 운영 이전 시에는 별도 API LB/DNS, 클라우드 컨트롤러와 CSI, 데이터 이전을 설계한다. CNI는 이 네트워크에서 동작하는 overlay 등의 모드를 선택한다. `vpc_cidr`은 홈·Pod·Service 대역과 겹치지 않게 정한다.

## state와 정리

초기에는 로컬 state를 사용한다. 다른 디렉터리나 장비에서 같은 클러스터를 관리하려면 state를 함께 이전하거나 잠금과 암호화를 구성한 원격 backend로 옮겨야 한다. state를 잃으면 이 구성으로 리소스를 추적·삭제할 수 없다. 서로 다른 클러스터의 state는 분리한다.

테스트가 끝난 뒤 리소스를 삭제한다. 루트 디스크의 데이터도 같이 지워지므로 유지할 데이터는 먼저 별도로 보관한다.

```bash
terraform plan -destroy -out=destroy.tfplan
terraform apply destroy.tfplan
```

VM만 중지해도 EBS 비용은 남는다. Terraform이 만든 전체 테스트 환경을 정리하려면 destroy 계획을 적용한다.
