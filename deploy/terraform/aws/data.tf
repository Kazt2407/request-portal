# PostgreSQL. The master password is generated and kept in Secrets Manager by RDS. The per-service
# databases and roles (as in infra/postgres/init/01-databases.sql) are created by a one-off job.
resource "aws_db_instance" "postgres" {
  identifier     = local.name
  engine         = "postgres"
  engine_version = var.postgres_version
  instance_class = var.db_instance_class

  allocated_storage     = 20
  max_allocated_storage = 100
  storage_type          = "gp3"
  storage_encrypted     = true

  username                    = "portal"
  manage_master_user_password = true

  db_subnet_group_name   = module.vpc.database_subnet_group_name
  vpc_security_group_ids = [aws_security_group.data.id]
  multi_az               = var.db_multi_az

  backup_retention_period      = 7
  deletion_protection          = var.deletion_protection
  skip_final_snapshot          = !var.deletion_protection
  final_snapshot_identifier    = var.deletion_protection ? "${local.name}-final" : null
  performance_insights_enabled = true
}

# Kafka for workflow events: one broker per availability zone, TLS between clients and brokers.
resource "aws_msk_cluster" "kafka" {
  cluster_name           = local.name
  kafka_version          = var.kafka_version
  number_of_broker_nodes = 3

  broker_node_group_info {
    instance_type   = var.kafka_broker_instance_type
    client_subnets  = module.vpc.private_subnets
    security_groups = [aws_security_group.data.id]

    storage_info {
      ebs_storage_info {
        volume_size = 50
      }
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }
}

# Valkey (open-source Redis) for the gateway's rate limiter, with a replica for failover.
resource "aws_elasticache_subnet_group" "cache" {
  name       = local.name
  subnet_ids = module.vpc.private_subnets
}

resource "aws_elasticache_replication_group" "cache" {
  replication_group_id = local.name
  description          = "Gateway rate limiting"
  engine               = "valkey"
  engine_version       = "8.0"
  node_type            = var.cache_node_type
  num_cache_clusters   = 2

  automatic_failover_enabled = true
  multi_az_enabled           = true
  transit_encryption_enabled = true
  at_rest_encryption_enabled = true

  subnet_group_name  = aws_elasticache_subnet_group.cache.name
  security_group_ids = [aws_security_group.data.id]
}
