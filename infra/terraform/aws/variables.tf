variable "region" {
  description = "VM을 만들 AWS 리전. AMI도 이 리전의 ID를 지정한다."
  type        = string
  default     = "ap-northeast-2"
}

variable "name" {
  description = "리소스 이름 접두사. 별도 클러스터는 별도 state에서 관리한다."
  type        = string
  default     = "contentria-cloud-test"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{0,39}$", var.name))
    error_message = "name은 영문 소문자로 시작하는 40자 이하의 소문자, 숫자, 하이픈이어야 한다."
  }
}

variable "ami_id" {
  description = "공식 Rocky Linux 10 x86_64 AMI ID. 자동 최신 선택 대신 검증한 버전을 고정한다."
  type        = string

  validation {
    condition     = can(regex("^ami-[0-9a-f]{8}([0-9a-f]{9})?$", var.ami_id))
    error_message = "ami_id에는 해당 리전의 AMI ID를 입력한다."
  }
}

variable "ami_owner_id" {
  description = "공식 이미지 배포 경로에서 확인한 AMI 소유 AWS 계정 ID. 이름이 같은 타사 이미지를 제외한다."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.ami_owner_id))
    error_message = "ami_owner_id는 12자리 AWS 계정 ID여야 한다."
  }
}

variable "ssh_public_key" {
  description = "rocky 계정에 등록할 SSH 공개키. 개인키는 Terraform에 전달하지 않는다."
  type        = string

  validation {
    condition     = can(regex("^(ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp(256|384|521)) [A-Za-z0-9+/=]+", trimspace(var.ssh_public_key)))
    error_message = "OpenSSH 형식의 공개키를 입력한다."
  }
}

variable "admin_cidrs" {
  description = "SSH와 Kubernetes API 접근을 허용할 관리자 공인 IPv4 CIDR 목록."
  type        = set(string)

  validation {
    condition = length(var.admin_cidrs) > 0 && alltrue([
      for cidr in var.admin_cidrs : try(cidrnetmask(cidr) != "0.0.0.0", false)
    ])
    error_message = "admin_cidrs에는 IPv4 CIDR을 하나 이상 입력한다. 전체 인터넷(/0)은 허용하지 않는다."
  }
}

variable "instance_type" {
  description = "Rocky 10의 x86-64-v3를 지원하는 EC2 타입. 기본값은 Intel 기반 2 vCPU, 4 GiB 테스트 노드다."
  type        = string
  default     = "t3.medium"
}

variable "node_count" {
  description = "생성할 노드 수. 기본 2대는 홈 서버 구성 재현용이며 etcd HA 구성이 아니다."
  type        = number
  default     = 2

  validation {
    condition     = var.node_count >= 1 && var.node_count <= 10 && floor(var.node_count) == var.node_count
    error_message = "node_count는 1~10의 정수여야 한다."
  }
}

variable "root_volume_size" {
  description = "노드별 gp3 루트 디스크 크기(GiB). VM 삭제 시 같이 삭제된다."
  type        = number
  default     = 40

  validation {
    condition     = var.root_volume_size >= 30 && var.root_volume_size <= 16384 && floor(var.root_volume_size) == var.root_volume_size
    error_message = "root_volume_size는 30~16384의 정수여야 한다."
  }
}

variable "vpc_cidr" {
  description = "홈 네트워크, Pod 및 Service CIDR과 겹치지 않는 IPv4 /16 VPC 대역."
  type        = string
  default     = "10.80.0.0/16"

  validation {
    condition     = try(cidrnetmask(var.vpc_cidr) == "255.255.0.0" && cidrhost(var.vpc_cidr, 0) == split("/", var.vpc_cidr)[0], false)
    error_message = "vpc_cidr은 네트워크 주소로 지정한 IPv4 /16이어야 한다."
  }
}
