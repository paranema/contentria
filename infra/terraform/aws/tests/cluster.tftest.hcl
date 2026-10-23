mock_provider "aws" {
  mock_data "aws_ami" {
    defaults = {
      id   = "ami-0123456789abcdef0"
      name = "Rocky-10-EC2-Base-10.1-test.x86_64"
    }
  }
  mock_data "aws_availability_zones" {
    defaults = { names = ["ap-northeast-2a"] }
  }
}

variables {
  ami_id         = "ami-0123456789abcdef0"
  ami_owner_id   = "123456789012"
  ssh_public_key = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIMockKeyForPlanOnly"
  admin_cidrs    = ["203.0.113.10/32"]
}

run "two_nodes" {
  command = plan

  assert {
    condition     = length(aws_instance.node) == 2 && aws_instance.node["node-01"].private_ip == "10.80.0.10" && aws_instance.node["node-02"].private_ip == "10.80.0.11"
    error_message = "기본 노드는 2대이며 서로 다른 고정 사설 IP를 사용해야 한다."
  }
  assert {
    condition = alltrue([for node in aws_instance.node :
      node.metadata_options[0].http_tokens == "required" &&
      node.root_block_device[0].encrypted && !node.source_dest_check
    ])
    error_message = "IMDSv2, 암호화 디스크, 노드 라우팅 설정을 유지해야 한다."
  }
  assert {
    condition     = aws_vpc_security_group_ingress_rule.ssh["203.0.113.10/32"].cidr_ipv4 == "203.0.113.10/32" && aws_vpc_security_group_ingress_rule.api["203.0.113.10/32"].from_port == 6443
    error_message = "SSH와 API는 지정한 관리자 대역에만 열려야 한다."
  }
}

run "reject_public_admin_access" {
  command = plan
  variables { admin_cidrs = ["0.0.0.0/0"] }
  expect_failures = [var.admin_cidrs]
}

run "reject_ipv6_admin_access" {
  command = plan
  variables { admin_cidrs = ["::/0"] }
  expect_failures = [var.admin_cidrs]
}

run "reject_wrong_os" {
  command = plan
  override_data {
    target = data.aws_ami.rocky
    values = { name = "Rocky-9-EC2-Base-9.7.x86_64" }
  }
  expect_failures = [data.aws_ami.rocky]
}

run "three_nodes" {
  command = plan
  variables { node_count = 3 }
  assert {
    condition     = length(aws_instance.node) == 3 && aws_instance.node["node-03"].private_ip == "10.80.0.12"
    error_message = "노드 수를 늘려도 기존 노드의 키와 사설 IP가 유지되어야 한다."
  }
}

run "reject_empty_admin_access" {
  command = plan
  variables { admin_cidrs = [] }
  expect_failures = [var.admin_cidrs]
}

# mock provider의 apply이며 실제 AWS API나 리소스를 사용하지 않는다.
run "inventory" {
  command = apply
  assert {
    condition     = keys(yamldecode(output.ansible_inventory).all.children.etcd.hosts) == ["node-01"]
    error_message = "2노드에서는 Kubespray의 홀수 멤버 조건에 맞춰 etcd를 첫 노드에 둔다."
  }

  assert {
    condition = (
      yamldecode(output.ansible_inventory).all.vars.ansible_user == "rocky" &&
      yamldecode(output.ansible_inventory).all.vars.ansible_become &&
      yamldecode(output.ansible_inventory).all.hosts["node-01"].ansible_host == aws_instance.node["node-01"].public_ip &&
      yamldecode(output.ansible_inventory).all.hosts["node-01"].ip == "10.80.0.10" &&
      yamldecode(output.ansible_inventory).all.hosts["node-02"].access_ip == "10.80.0.11"
    )
    error_message = "SSH 접속은 공인 IP, 클러스터 통신은 사설 IP를 사용해야 한다."
  }
  assert {
    condition = alltrue([
      for group in ["kube_control_plane", "kube_node"] :
      keys(yamldecode(output.ansible_inventory).all.children[group].hosts) == ["node-01", "node-02"]
    ])
    error_message = "두 노드 모두 control plane, worker 그룹에 속해야 한다."
  }
}

run "etcd_three" {
  command = apply
  variables { node_count = 3 }
  assert {
    condition     = keys(yamldecode(output.ansible_inventory).all.children.etcd.hosts) == ["node-01", "node-02", "node-03"]
    error_message = "3노드부터는 etcd 3멤버를 구성해야 한다."
  }
}
