output "eip_public_ip" {
  description = "도메인 A레코드 대상 / GH secret EC2_HOST"
  value       = aws_eip.app.public_ip
}

output "security_group_id" {
  description = "GH secret AWS_SG_ID"
  value       = aws_security_group.app.id
}

output "ecr_repository_url" {
  description = "백엔드 이미지 리포지토리 URL"
  value       = aws_ecr_repository.backend.repository_url
}

output "github_actions_user" {
  description = "액세스 키 발급 대상 IAM 사용자"
  value       = aws_iam_user.github_actions.name
}

output "instance_id" {
  value = aws_instance.app.id
}
