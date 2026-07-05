variable "aws_region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "project" {
  description = "리소스 이름 접두사"
  type        = string
  default     = "moyeolak"
}

variable "instance_type" {
  description = "EC2 인스턴스 타입 (모니터링 활성화 시 t4g.medium으로 변경)"
  type        = string
  default     = "t4g.small"
}

variable "my_ip" {
  description = "SSH(22) 허용 CIDR. 예: 1.2.3.4/32"
  type        = string
}

variable "ssh_public_key_path" {
  description = "EC2 key pair로 등록할 SSH 공개키 경로"
  type        = string
  default     = "~/.ssh/moyeolak-aws.pub"
}

variable "root_volume_size" {
  description = "루트 EBS(gp3) 크기 GB"
  type        = number
  default     = 30
}
