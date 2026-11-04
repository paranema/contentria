SHELL := /bin/bash
.DEFAULT_GOAL := help

# --- 기본 변수 정의 ---
ENV ?= dev
AWS_PROFILE ?= contentria-test
TF_AWS_DIR := terraform/aws
ANSIBLE_DIR := ansible
SSH_DIR ?= $(HOME)/.ssh/contentria

VAR_FILE := terraform.$(ENV).tfvars
PLAN_FILE := $(ENV).tfplan

# ANSI 색상 코드
CYAN  := \033[36m
GREEN := \033[32m
RESET := \033[0m

.PHONY: help \
	tf-init tf-fmt tf-validate tf-test tf-workspace tf-plan tf-apply tf-destroy tf-inventory \
	ansible-verify ansible-prepare ansible-cluster ansible-argocd ansible-gitops ansible-all

help: ## 사용 가능한 명령어 목록을 표시합니다
	@echo -e "$(GREEN)Contentria 인프라 자동화 도구 (Terraform & Ansible)$(RESET)"
	@echo -e "기본 환경(ENV): $(CYAN)$(ENV)$(RESET) (변경 시: make <명령어> ENV=dev)\n"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(CYAN)%-20s$(RESET) %s\n", $$1, $$2}'

# ==============================================================================
# Terraform (AWS)
# ==============================================================================

tf-init: ## Terraform 프로바이더 및 백엔드를 초기화합니다
	AWS_PROFILE=$(AWS_PROFILE) terraform -chdir=$(TF_AWS_DIR) init

tf-fmt: ## Terraform 코드 포맷을 검사합니다
	terraform -chdir=$(TF_AWS_DIR) fmt -check -recursive

tf-validate: ## Terraform 구문 유효성을 검사합니다
	terraform -chdir=$(TF_AWS_DIR) validate

tf-test: ## Terraform 단위 테스트(mock provider)를 실행합니다
	terraform -chdir=$(TF_AWS_DIR) test

tf-workspace: ## 대상 Workspace($(ENV))를 선택하거나 신규 생성합니다
	@echo -e "Terraform workspace: $(CYAN)$(ENV)$(RESET)"
	@terraform -chdir=$(TF_AWS_DIR) workspace select $(ENV) 2>/dev/null || terraform -chdir=$(TF_AWS_DIR) workspace new $(ENV)

tf-plan: tf-workspace ## 지정된 환경의 변수로 실행 계획을 수립합니다 (예: make tf-plan ENV=dev)
	@if [ ! -f "$(TF_AWS_DIR)/$(VAR_FILE)" ]; then \
		echo -e "오류: 변수 파일 '$(TF_AWS_DIR)/$(VAR_FILE)'이 존재하지 않습니다."; \
		echo -e "  cp $(TF_AWS_DIR)/terraform.example.tfvars $(TF_AWS_DIR)/$(VAR_FILE)"; \
		exit 1; \
	fi
	AWS_PROFILE=$(AWS_PROFILE) terraform -chdir=$(TF_AWS_DIR) plan -var-file=$(VAR_FILE) -out=$(PLAN_FILE)

tf-apply: tf-workspace ## 수립된 실행 계획을 기반으로 인프라를 배포합니다
	@if [ ! -f "$(TF_AWS_DIR)/$(PLAN_FILE)" ]; then \
		echo -e "오류: 실행 계획 파일 '$(TF_AWS_DIR)/$(PLAN_FILE)'이 없습니다. 먼저 'make tf-plan ENV=$(ENV)'를 실행하세요."; \
		exit 1; \
	fi
	AWS_PROFILE=$(AWS_PROFILE) terraform -chdir=$(TF_AWS_DIR) apply $(PLAN_FILE)

tf-destroy: tf-workspace ## 지정된 환경의 인프라 리소스를 삭제합니다
	@if [ ! -f "$(TF_AWS_DIR)/$(VAR_FILE)" ]; then \
		echo -e "오류: 변수 파일 '$(TF_AWS_DIR)/$(VAR_FILE)'이 존재하지 않습니다."; \
		exit 1; \
	fi
	AWS_PROFILE=$(AWS_PROFILE) terraform -chdir=$(TF_AWS_DIR) destroy -var-file=$(VAR_FILE)

tf-inventory: ## Terraform 배포 결과에서 Ansible hosts.yml 인벤토리를 생성합니다
	@echo -e "인벤토리 추출: $(CYAN)$(ANSIBLE_DIR)/inventories/cloud-test/hosts.yml$(RESET)"
	@terraform -chdir=$(TF_AWS_DIR) output -raw ansible_inventory > $(ANSIBLE_DIR)/inventories/cloud-test/hosts.yml

# ==============================================================================
# Ansible (Kubespray & Playbooks)
# ==============================================================================

ansible-verify: ## Ansible 인벤토리 및 플레이북 구문 검증 테스트를 실행합니다
	docker run --rm \
		-v "$$(cd .. && pwd):/workspace:ro" \
		-w /workspace/infra/ansible \
		contentria-ansible:ks2.31.0-1 \
		/usr/bin/python3.11 tests/verify.py

ansible-prepare: ## 1단계: OS 베이스 설정 및 전제 패키지 준비
	cd $(ANSIBLE_DIR) && ansible-navigator run playbooks/10-prepare.yml \
		-i inventories/common.yml -i inventories/cloud-test/hosts.yml \
		--eev "$(SSH_DIR):/run/contentria-ssh:ro" \
		--private-key /run/contentria-ssh/id_ed25519 \
		--ssh-common-args='-o UserKnownHostsFile=/run/contentria-ssh/known_hosts'

ansible-cluster: ## 2단계: Kubespray 기반 Kubernetes 클러스터 구축
	cd $(ANSIBLE_DIR) && ansible-navigator run playbooks/20-cluster.yml \
		-i inventories/common.yml -i inventories/cloud-test/hosts.yml \
		--eev "$(SSH_DIR):/run/contentria-ssh:ro" \
		--private-key /run/contentria-ssh/id_ed25519 \
		--ssh-common-args='-o UserKnownHostsFile=/run/contentria-ssh/known_hosts'

ansible-argocd: ## 3단계: Argo CD 설치 및 초기 설정
	cd $(ANSIBLE_DIR) && ansible-navigator run playbooks/30-argocd.yml \
		-i inventories/common.yml -i inventories/cloud-test/hosts.yml \
		--eev "$(SSH_DIR):/run/contentria-ssh:ro" \
		--private-key /run/contentria-ssh/id_ed25519 \
		--ssh-common-args='-o UserKnownHostsFile=/run/contentria-ssh/known_hosts'

ansible-gitops: ## 4단계: GitOps Root Application 등록 및 배포
	cd $(ANSIBLE_DIR) && ansible-navigator run playbooks/40-gitops.yml \
		-i inventories/common.yml -i inventories/cloud-test/hosts.yml \
		--eev "$(SSH_DIR):/run/contentria-ssh:ro" \
		--private-key /run/contentria-ssh/id_ed25519 \
		--ssh-common-args='-o UserKnownHostsFile=/run/contentria-ssh/known_hosts'

ansible-all: ansible-prepare ansible-cluster ansible-argocd ansible-gitops ## Ansible 1~4단계를 순차적으로 실행합니다
