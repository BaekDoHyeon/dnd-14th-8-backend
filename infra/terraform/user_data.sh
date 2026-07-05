#!/bin/bash
# EC2 최초 부팅 시 1회 실행
set -euxo pipefail
export DEBIAN_FRONTEND=noninteractive

apt-get update -y
apt-get install -y docker.io docker-compose-v2
systemctl enable --now docker
usermod -aG docker ubuntu

# ECR 로그인용 AWS CLI (자격증명은 인스턴스 프로파일)
snap install aws-cli --classic

# t4g.small(2GB) 메모리 보완용 스왑 2GB
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# 앱 디렉터리
mkdir -p /app/moyeolak/logs
chown -R ubuntu:ubuntu /app/moyeolak
