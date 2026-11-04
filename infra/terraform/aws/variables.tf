variable "region" {
  description = "VM을 생성할 AWS 리전입니다. AMI ID도 해당 리전의 값으로 지정합니다."
  type        = string
  default     = "ap-northeast-2"
}

variable "name" {
  description = "리소스 이름 접두사입니다. 별도 클러스터는 별도 state에서 관리합니다."
  type        = string
  default     = "contentria-cloud-test"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{0,39}$", var.name))
    error_message = "name은 영문 소문자로 시작하는 40자 이하의 소문자, 숫자, 하이픈이어야 합니다."
  }
}

variable "ami_id" {
  description = "공식 Rocky Linux 10 x86_64 AMI ID입니다. 검증된 버전을 고정하여 사용합니다."
  type        = string

  validation {
    condition     = can(regex("^ami-[0-9a-f]{8}([0-9a-f]{9})?$", var.ami_id))
    error_message = "ami_id에는 해당 리전의 올바른 AMI ID 형식을 입력해야 합니다."
  }
}

variable "ami_owner_id" {
  description = "공식 이미지 배포 경로에서 확인한 AMI 소유 AWS 계정 ID입니다. 타사 이미지를 제외하기 위해 검증합니다."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.ami_owner_id))
    error_message = "ami_owner_id는 12자리 AWS 계정 ID여야 합니다."
  }
}

variable "ssh_public_key" {
  description = "rocky 계정에 등록할 SSH 공개키입니다. 개인키는 전달하지 않습니다."
  type        = string

  validation {
    condition     = can(regex("^(ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp(256|384|521)) [A-Za-z0-9+/=]+", trimspace(var.ssh_public_key)))
    error_message = "OpenSSH 형식의 올바른 공개키를 입력해야 합니다."
  }
}

variable "admin_cidrs" {
  description = "SSH 및 Kubernetes API 접근을 허용할 관리자 공인 IPv4 CIDR 목록입니다."
  type        = set(string)

  validation {
    condition = length(var.admin_cidrs) > 0 && alltrue([
      for cidr in var.admin_cidrs : try(cidrnetmask(cidr) != "0.0.0.0", false)
    ])
    error_message = "admin_cidrs에는 IPv4 CIDR을 하나 이상 입력해야 합니다. 전체 인터넷(/0)은 허용되지 않습니다."
  }
}

variable "instance_type" {
  description = "Rocky 10 x86-64-v3를 지원하는 EC2 인스턴스 타입입니다. 기본값은 2 vCPU, 4 GiB 테스트 노드입니다."
  type        = string
  default     = "t3.medium"
}

variable "node_count" {
  description = "생성할 노드 수입니다. 기본 2대는 테스트용 구성이며 etcd 고가용성(HA) 구성은 아닙니다."
  type        = number
  default     = 2

  validation {
    condition     = var.node_count >= 1 && var.node_count <= 10 && floor(var.node_count) == var.node_count
    error_message = "node_count는 1~10 사이의 정수여야 합니다."
  }
}

variable "root_volume_size" {
  description = "노드별 gp3 루트 볼륨 크기(GiB)입니다. 인스턴스 삭제 시 함께 삭제됩니다."
  type        = number
  default     = 40

  validation {
    condition     = var.root_volume_size >= 30 && var.root_volume_size <= 16384 && floor(var.root_volume_size) == var.root_volume_size
    error_message = "root_volume_size는 30~16384 사이의 정수여야 합니다."
  }
}

variable "vpc_cidr" {
  description = "기타 네트워크(Pod/Service) 대역과 중복되지 않는 IPv4 /16 VPC 대역입니다."
  type        = string
  default     = "10.80.0.0/16"

  validation {
    condition     = try(cidrnetmask(var.vpc_cidr) == "255.255.0.0" && cidrhost(var.vpc_cidr, 0) == split("/", var.vpc_cidr)[0], false)
    error_message = "vpc_cidr은 네트워크 주소 형식의 IPv4 /16 대역이어야 합니다."
  }
}
