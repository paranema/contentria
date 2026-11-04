# 대상 리전의 공식 Rocky Linux 10 x86_64 AMI 정보를 입력합니다.
region       = "ap-northeast-2"
ami_id       = "ami-REPLACE_ME"
ami_owner_id = "REPLACE_ME"

# 관리자 공인 IPv4 CIDR을 입력합니다. 이 대역에서만 SSH와 Kubernetes API에 접근할 수 있습니다.
admin_cidrs = ["203.0.113.10/32"]

# SSH 공개키 문자열(~/.ssh/*.pub)을 입력합니다. 개인키는 포함하지 않습니다.
ssh_public_key = "ssh-ed25519 REPLACE_ME"

node_count       = 2
instance_type    = "t3.medium"
root_volume_size = 40
