"""EE 내부에서 실행한다. SSH 연결이나 실제 클러스터 변경은 하지 않는다."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

import yaml

ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)


def run(*args, success=True):
    result = subprocess.run(args, text=True, capture_output=True)
    if (result.returncode == 0) != success:
        raise AssertionError(result.stdout + result.stderr)
    return result.stdout


with tempfile.TemporaryDirectory() as temporary:
    tmp = Path(temporary)
    for environment in ("home", "cloud-test"):
        fixture = tmp / environment
        fixture.mkdir()
        shutil.copytree(ROOT / "inventories" / environment / "group_vars", fixture / "group_vars")
        hosts = yaml.safe_load((ROOT / "inventories/home/hosts.yml.example").read_text())
        hosts["all"]["vars"]["kube_vip_address"] = "192.168.1.10"
        path = fixture / "hosts.yml"
        path.write_text(yaml.safe_dump(hosts))
        inventory_args = ("-i", "inventories/common.yml", "-i", str(path))
        data = json.loads(run("ansible-inventory", *inventory_args, "--list"))
        variables = data["_meta"]["hostvars"]["home-01"]
        assert variables["kube_vip_enabled"] == (environment == "home")
        assert variables["kube_version"] == "1.35.4"
        assert variables["calico_network_backend"] == "vxlan"
        assert variables["helm_enabled"] and not variables["argocd_enabled"]
        assert data["etcd"]["hosts"] == ["home-01"]
        assert data["kube_control_plane"]["hosts"] == data["kube_node"]["hosts"]
        for playbook in sorted((ROOT / "playbooks").glob("*.yml")):
            run("ansible-playbook", *inventory_args, str(playbook), "--syntax-check")
        # 최초 play의 검증 task만 실행한다. Kubespray와 서버 작업은 실행하지 않는다.
        preflight = tmp / "preflight.yml"
        preflight.write_text(yaml.safe_dump([yaml.safe_load((ROOT / "playbooks/20-cluster.yml").read_text())[0]]))
        run("ansible-playbook", *inventory_args, str(preflight), "--check", "-c", "local")
        if environment == "home":
            run("ansible-playbook", *inventory_args, str(preflight), "--check", "-c", "local",
                "-e", '{"kube_vip_address": ""}', success=False)
        else:
            run("ansible-playbook", *inventory_args, str(preflight), "--check", "-c", "local",
                "-e", '{"kube_vip_enabled": true}', success=False)
        hosts["all"]["children"]["etcd"]["hosts"]["home-02"] = {}
        path.write_text(yaml.safe_dump(hosts))
        run("ansible-playbook", *inventory_args, str(preflight), "--check", "-c", "local", success=False)
        print(f"{environment}: inventory, 4 syntax checks, valid and invalid preflight passed")

required = yaml.safe_load((ROOT / "roles/gitops_root/defaults/main.yml").read_text())["required_secrets"]
examples = set()
for path in (ROOT.parent / "secrets/example").glob("*.yaml"):
    for resource in yaml.safe_load_all(path.read_text()):
        if resource and resource.get("kind") == "Secret":
            examples.add((resource["metadata"]["namespace"], resource["metadata"]["name"]))
assert {(item["namespace"], item["name"]) for item in required} == examples
print("GitOps required secrets match the repository examples")
