# AWS environment (Terraform)

Creates what the services need in AWS; the services themselves are deployed with the Helm chart in
[`deploy/helm`](../../helm/request-portal).

| Local (docker compose) | AWS |
|---|---|
| k3d | EKS, managed node group (2 × m7i.large, autoscaling to 5), metrics-server |
| PostgreSQL | RDS for PostgreSQL, encrypted, master password in Secrets Manager |
| Kafka | MSK, 3 brokers (one per zone), TLS |
| Redis | ElastiCache for Valkey with a replica and automatic failover |
| Mailpit | SES, with DKIM |
| (phase 6) | S3 bucket for documents; bulk exports expire after 3 days; access through EKS Pod Identity |

Networking: a VPC across three availability zones; data stores sit in private subnets and accept
connections only from the cluster nodes.

> **Status: validated, not applied.** `terraform validate` passes against the real AWS provider and
> modules, but this has never been applied to an AWS account, so expect small fixes on a first apply.
> Applying creates billable resources (EKS, NAT gateway, RDS, MSK and ElastiCache run around the clock).

## Use

```bash
terraform init            # after configuring the S3 backend in versions.tf
terraform plan -out tfplan
terraform apply tfplan
$(terraform output -raw configure_kubectl)
terraform output -raw helm_infrastructure_values > values-aws.yaml   # then merge into the Helm values
```

## Not covered here

- The per-service PostgreSQL databases and roles, created once by a job running
  [`01-databases.sql`](../../../infra/postgres/init/01-databases.sql) with the master credentials.
- Keycloak: run it on the cluster (Keycloak Operator) or use a managed identity provider, behind a TLS
  ingress with a public hostname, and import [the realm](../../../infra/keycloak/realm-request-portal.json).
- TLS settings in the services for MSK and Valkey (`spring.kafka.security.protocol=SSL`,
  `spring.data.redis.ssl.enabled=true`), set through environment variables.
