# 선택한 리전의 공식 Rocky Linux 10 x86_64 AMI로 바꾼다.
region       = "ap-northeast-2"
ami_id       = "ami-REPLACE_ME"
ami_owner_id = "REPLACE_ME"

# 내 공인 IPv4로 바꾼다. 이 주소에서만 SSH와 Kubernetes API에 접근한다.
admin_cidrs = ["203.0.113.10/32"]

# ~/.ssh/id_ed25519.pub 등의 공개키 내용. 개인키는 넣지 않는다.
ssh_public_key = "ssh-ed25519 REPLACE_ME"

node_count       = 2
instance_type    = "t3.medium"
root_volume_size = 40
