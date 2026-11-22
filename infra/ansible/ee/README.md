# Ansible Execution Environment

로컬 머신에 Ansible과 Python 의존성을 설치하지 않고, 고정된 버전의 실행 환경에서 플레이북과 Kubespray를 구동하기 위한 컨테이너 이미지 정의입니다.

## 개요

### 버전 구성

| 구성 요소         | 기준 버전                                                         |
| ----------------- | ----------------------------------------------------------------- |
| 베이스 이미지     | `quay.io/rockylinux/rockylinux:9` (Python `3.11`)                 |
| Ansible           | `ansible 11.13.0`, `ansible-core 2.18.12`, `ansible-runner 2.4.1` |
| Kubespray         | `v2.31.0` (`1c9add48975060f45396b34d8e022c30d7f80dab`)            |
| Kubernetes 기본값 | `1.35.4` (containerd, Calico 버전은 Kubespray 릴리스를 따름)      |

컨트롤러 컨테이너의 OS(Rocky Linux 9)는 원격 관리 대상 노드의 OS(Rocky Linux 10)와 동일할 필요가 없습니다. Colima 및 Docker 볼륨 마운트 경로에 실행 산출물을 기록할 수 있도록 컨테이너 내부 사용자는 `root`(`0`)로 설정되어 있습니다.

### 파일 구성

| 파일                        | 역할                                                                               |
| --------------------------- | ---------------------------------------------------------------------------------- |
| `execution-environment.yml` | `ansible-builder`용 이미지 빌드 명세 및 Kubespray 커밋 검증                        |
| `requirements.txt`          | Kubespray `v2.31.0` 호환 Python 패키지 목록                                        |
| `bindep.txt`                | 컨테이너 내부 필수 RPM 시스템 패키지(`git`, `openssh-clients`, `rsync`, `sshpass`) |

SSH 개인키와 실제 호스트 인벤토리는 이미지에 포함하지 않으며, 런타임에 외부 볼륨과 인자로 주입합니다.

## 빌드 절차

### 사전 요구사항

- Docker(또는 Podman) 실행 상태
- `ansible-builder` 설치

### 이미지 빌드

`infra/` 디렉터리에서 `Makefile`을 사용하거나 `ee/` 디렉터리에서 직접 빌드합니다.

```bash
# 방법 1. Makefile 사용 (infra/ 디렉터리)
make ansible-ee

# 방법 2. 직접 실행 (infra/ansible/ee/ 디렉터리)
ansible-builder build --container-runtime docker --tag contentria-ansible:ks2.31.0-1
```

Podman을 사용하는 경우 `ansible-builder`의 `--container-runtime`과 상위 `ansible-navigator.yml`의 `container-engine` 값을 함께 변경합니다.

## 관리 기준

- 플레이북, 역할, 인벤토리 파일만 수정한 경우에는 이미지를 다시 빌드하지 않습니다.
- 상위 `ansible-navigator.yml`의 이미지 pull 정책은 `never`이므로 로컬에 빌드된 이미지를 사용합니다.
- Kubespray 버전이나 패키지 의존성을 변경할 때는 이미지 태그를 올리고, `ansible-navigator.yml`과 `infra/Makefile`의 이미지 태그를 함께 갱신한 뒤 `make ansible-verify`로 검증합니다.
- 동일한 이미지를 여러 환경에서 재현해야 하는 경우 레지스트리에 보관하고 이미지 digest 기준으로 고정할 수 있습니다.
