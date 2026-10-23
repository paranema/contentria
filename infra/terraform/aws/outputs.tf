output "nodes" {
  description = "SSH에는 공인 IP, 노드 간 통신에는 사설 IP를 사용한다."
  value = {
    for name, node in aws_instance.node : name => {
      instance_id = node.id
      public_ip   = node.public_ip
      private_ip  = node.private_ip
      ssh_user    = "rocky"
    }
  }
}

output "ansible_inventory" {
  description = "Kubespray용 YAML 인벤토리. 모든 노드가 control plane·worker이며 etcd는 1대 또는 3대다."
  value = yamlencode({
    all = {
      hosts = {
        for name, node in aws_instance.node : name => {
          ansible_host = node.public_ip
          ip           = node.private_ip
          access_ip    = node.private_ip
        }
      }
      vars = {
        ansible_user   = "rocky"
        ansible_become = true
      }
      children = {
        kube_control_plane = { hosts = { for name in keys(local.nodes) : name => {} } }
        kube_node          = { hosts = { for name in keys(local.nodes) : name => {} } }
        etcd               = { hosts = { for name in slice(keys(local.nodes), 0, var.node_count < 3 ? 1 : 3) : name => {} } }
        k8s_cluster = {
          children = {
            kube_control_plane = {}
            kube_node          = {}
          }
        }
      }
    }
  })
}
