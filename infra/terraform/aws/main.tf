data "aws_ami" "rocky" {
  owners = [var.ami_owner_id]

  filter {
    name   = "image-id"
    values = [var.ami_id]
  }
  filter {
    name   = "architecture"
    values = ["x86_64"]
  }
  filter {
    name   = "root-device-type"
    values = ["ebs"]
  }
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
  filter {
    name   = "state"
    values = ["available"]
  }

  lifecycle {
    postcondition {
      condition     = can(regex("(?i)rocky[-_ ]?(linux[-_ ]?)?10([._ -]|$)", self.name))
      error_message = "Rocky Linux 10 공식 AMI를 지정해야 한다."
    }
  }
}

data "aws_availability_zones" "available" {
  state = "available"
  filter {
    name   = "opt-in-status"
    values = ["opt-in-not-required"]
  }
}

locals {
  nodes = { for index in range(var.node_count) : format("node-%02d", index + 1) => index }
}

resource "aws_vpc" "cluster" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true
}

resource "aws_subnet" "nodes" {
  vpc_id            = aws_vpc.cluster.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, 0)
  availability_zone = data.aws_availability_zones.available.names[0]
}

resource "aws_internet_gateway" "cluster" {
  vpc_id = aws_vpc.cluster.id
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.cluster.id
}

resource "aws_route" "internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.cluster.id
}

resource "aws_route_table_association" "nodes" {
  subnet_id      = aws_subnet.nodes.id
  route_table_id = aws_route_table.public.id
}

resource "aws_security_group" "nodes" {
  name_prefix = "${var.name}-nodes-"
  description = "Cluster peers and explicitly allowed administrators"
  vpc_id      = aws_vpc.cluster.id
}

# 같은 보안 그룹의 노드끼리 CNI, etcd, kubelet 통신을 허용한다.
resource "aws_vpc_security_group_ingress_rule" "peers" {
  security_group_id            = aws_security_group.nodes.id
  referenced_security_group_id = aws_security_group.nodes.id
  ip_protocol                  = "-1"
}

resource "aws_vpc_security_group_ingress_rule" "ssh" {
  for_each          = var.admin_cidrs
  security_group_id = aws_security_group.nodes.id
  cidr_ipv4         = each.value
  ip_protocol       = "tcp"
  from_port         = 22
  to_port           = 22
}

resource "aws_vpc_security_group_ingress_rule" "api" {
  for_each          = var.admin_cidrs
  security_group_id = aws_security_group.nodes.id
  cidr_ipv4         = each.value
  ip_protocol       = "tcp"
  from_port         = 6443
  to_port           = 6443
}

resource "aws_vpc_security_group_egress_rule" "outbound" {
  security_group_id = aws_security_group.nodes.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_key_pair" "admin" {
  key_name_prefix = "${var.name}-"
  public_key      = trimspace(var.ssh_public_key)
}

resource "aws_instance" "node" {
  for_each = local.nodes

  ami                         = data.aws_ami.rocky.id
  instance_type               = var.instance_type
  subnet_id                   = aws_subnet.nodes.id
  private_ip                  = cidrhost(aws_subnet.nodes.cidr_block, each.value + 10)
  associate_public_ip_address = true
  vpc_security_group_ids      = [aws_security_group.nodes.id]
  key_name                    = aws_key_pair.admin.key_name
  # CNI가 Pod 트래픽을 중계할 수 있도록 source/destination 검사를 끈다.
  source_dest_check = false

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = var.root_volume_size
    encrypted             = true
    delete_on_termination = true
  }

  dynamic "credit_specification" {
    for_each = startswith(var.instance_type, "t3.") ? [1] : []
    content {
      cpu_credits = "standard"
    }
  }

  tags = { Name = "${var.name}-${each.key}" }

  depends_on = [aws_route.internet, aws_route_table_association.nodes]
}
