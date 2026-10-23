output "configure_kubectl" {
  description = "Command that points kubectl at the new cluster."
  value       = "aws eks update-kubeconfig --region ${var.region} --name ${module.eks.cluster_name}"
}

output "postgres_endpoint" {
  value = aws_db_instance.postgres.address
}

output "postgres_master_secret_arn" {
  description = "Secrets Manager secret holding the generated master password."
  value       = aws_db_instance.postgres.master_user_secret[0].secret_arn
}

output "kafka_bootstrap_brokers_tls" {
  value = aws_msk_cluster.kafka.bootstrap_brokers_tls
}

output "valkey_endpoint" {
  value = aws_elasticache_replication_group.cache.primary_endpoint_address
}

output "documents_bucket" {
  value = aws_s3_bucket.documents.bucket
}

output "ses_dkim_tokens" {
  description = "Publish each as a CNAME <token>._domainkey.<mail_domain> -> <token>.dkim.amazonses.com."
  value       = aws_sesv2_email_identity.mail.dkim_signing_attributes[0].tokens
}

# The infrastructure part of the Helm values (deploy/helm/request-portal) for this environment.
output "helm_infrastructure_values" {
  value = yamlencode({
    infrastructure = {
      postgres              = { host = aws_db_instance.postgres.address, port = 5432 }
      kafkaBootstrapServers = aws_msk_cluster.kafka.bootstrap_brokers_tls
      redis                 = { host = aws_elasticache_replication_group.cache.primary_endpoint_address, port = 6379 }
      mail                  = { host = "email-smtp.${var.region}.amazonaws.com", port = 587 }
      storage               = { region = var.region, bucket = aws_s3_bucket.documents.bucket }
    }
  })
}
