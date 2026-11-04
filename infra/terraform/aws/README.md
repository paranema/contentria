# Terraform - AWS

Terraform을 사용하여 AWS 상에 Rocky Linux 10 기반의 Kubernetes 테스트 노드 환경을 프로비저닝합니다.
기본 구성은 서울 리전(`ap-northeast-2`)의 단일 가용 영역 내 EC2 인스턴스 2대입니다.

## 프로비저닝 리소스

- **네트워크**: 전용 VPC(`/16`), 퍼블릭 서브넷(`/24`), 인터넷 게이트웨이 및 기본 라우팅 테이블
- **보안**: 관리자 IP 전용 인바운드 보안 그룹 (SSH: 22, Kubernetes API: 6443), EC2 Key Pair
- **컴퓨팅**: `t3.medium` 인스턴스 2대 (Standard Credit 모드)
- **스토리지**: 노드별 암호화된 gp3 루트 볼륨 40 GiB
- **IP 할당**: 노드별 공인 IPv4 및 고정 사설 IPv4

> [!NOTE]
> NAT Gateway, EIP 및 로드밸런서는 배포하지 않습니다. 인바운드는 보안 그룹의 `admin_cidrs`에 정의된 IP로만 제한되며, 노드 간 통신은 사설 IP를 통해 모든 트래픽이 허용됩니다.

## 사전 요구사항

- Terraform 1.7 이상 (2.0 미만)
- AWS CLI v2 (`brew install awscli`)
- 유효한 AWS 자격 증명 (EC2 관리 권한)
- 공식 Rocky Linux 10 x86_64 AMI ID
- SSH 접속용 공개키 및 현재 공인 IP CIDR

## 환경 설정

### 자격 증명 구성

저장소 코드에는 자격 증명을 포함하지 않습니다. AWS CLI 로컬 프로필을 설정하고 `AWS_PROFILE` 환경 변수를 지정하여 실행합니다.

```bash
# 프로필 생성 (액세스 키, 시크릿 키, 리전 ap-northeast-2 입력)
aws configure --profile contentria-test
export AWS_PROFILE=contentria-test

# 자격 증명 연결 확인
aws sts get-caller-identity
```

### 리전 가용성 검증

조직의 서비스 제어 정책(SCP)으로 특정 리전이 차단될 수 있으므로, 실행 전 대상 리전의 가용성을 확인합니다.

```bash
# 가용 영역 조회 확인
aws ec2 describe-availability-zones --region ap-northeast-2 --query 'AvailabilityZones[].ZoneName'

# VPC 생성 권한 Dry-run 테스트
aws ec2 create-vpc --region ap-northeast-2 --cidr-block 10.80.0.0/16 --dry-run
```

### OS 이미지 선정

공식 배포처([Rocky Linux 공식 다운로드](https://rockylinux.org/download))에서 서울 리전의 최신 공식 x86_64 AMI 정보를 확인합니다.

```bash
# 공식 계정(792107900819)의 Rocky 10 AMI 검색
aws ec2 describe-images --region ap-northeast-2 --owners 792107900819 \
  --filters 'Name=name,Values=Rocky-10-EC2-Base*' 'Name=architecture,Values=x86_64' \
  --query 'sort_by(Images,&CreationDate)[].{ID:ImageId,Name:Name,Created:CreationDate}' \
  --output table
```

확인한 AMI ID와 소유 계정 ID를 `terraform.tfvars`에 지정합니다.

### 관리자 접근 제어

- **SSH 공개키**: `~/.ssh/id_ed25519.pub` 내용 한 줄을 `ssh_public_key`에 입력합니다.
- **관리자 IP CIDR**: 외부 접근을 허용할 현재 관리자의 공인 IP를 조회하여 `/32` 형식으로 `admin_cidrs`에 지정합니다.

```bash
curl -s https://checkip.amazonaws.com   # 예: 203.0.113.1 -> ["203.0.113.1/32"]
```

## 클러스터 배포

```bash
# 1. 변수 파일 복사 및 값 설정
cp terraform.tfvars.example terraform.tfvars

# 2. 초기화 및 코드 검증
terraform init
terraform fmt -check -recursive
terraform validate

# 3. 테스트 및 실행 계획 수립
terraform test
terraform plan -out=cluster.tfplan

# 4. 인프라 프로비저닝 적용
terraform apply cluster.tfplan

# 5. Ansible 인벤토리 파일 생성
terraform output -raw ansible_inventory > inventory.yaml
```

배포 완료 후 출력된 공인 IP로 SSH 접근을 확인합니다.

```bash
ssh -i ~/.ssh/id_ed25519 rocky@<공인IP>
```

## Ansible 연계 구성

생성된 `inventory.yaml` 파일은 Kubespray 클러스터 구축에 사용됩니다.

- **노드 역할 매핑**: 모든 인스턴스를 `kube_control_plane`과 `kube_node` 그룹에 공통 지정합니다.
- **etcd 토폴로지**: 짝수 etcd 구성을 방지하기 위해 1~2노드 환경에서는 첫 번째 노드만 etcd로 지정하며, 3노드 이상에서는 상위 3대를 etcd로 지정합니다.
- **후속 인계 작업**: OS 베이스 설정, CNI(Calico 등) 구성, Control Plane Taint 조정 및 Argo CD 설치는 Ansible 플레이북에서 이어 진행합니다.

## 상태 관리

### State 보관

- 기본적으로 로컬 State 파일(`terraform.tfstate`)을 사용합니다.
- 팀 협업이나 CI/CD 연동 시에는 원격 백엔드(S3 + DynamoDB 락)로 이전하여 사용합니다.
- `terraform.tfvars`, `terraform.tfstate`, `inventory.yaml` 파일은 Git 버전 관리에서 제외합니다.

### 리소스 삭제

테스트 완료 후 과금을 방지하기 위해 프로비저닝된 모든 리소스를 정리합니다.

```bash
terraform plan -destroy -out=destroy.tfplan
terraform apply destroy.tfplan
```
