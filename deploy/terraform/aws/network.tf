data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name = "request-portal-${var.environment}"
  azs  = slice(data.aws_availability_zones.available.names, 0, 3)
}

# Public subnets hold the load balancer; everything else lives in private subnets.
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 6.7"

  name = local.name
  cidr = var.vpc_cidr
  azs  = local.azs

  public_subnets   = [for i in range(3) : cidrsubnet(var.vpc_cidr, 8, i)]
  private_subnets  = [for i in range(3) : cidrsubnet(var.vpc_cidr, 4, i + 1)]
  database_subnets = [for i in range(3) : cidrsubnet(var.vpc_cidr, 8, i + 100)]

  create_database_subnet_group = true
  enable_nat_gateway           = true
  # One NAT gateway keeps staging cheap; use one per AZ in production.
  single_nat_gateway = var.environment != "production"

  public_subnet_tags  = { "kubernetes.io/role/elb" = 1 }
  private_subnet_tags = { "kubernetes.io/role/internal-elb" = 1 }
}

# Data stores accept connections only from the Kubernetes nodes.
resource "aws_security_group" "data" {
  name        = "${local.name}-data"
  description = "PostgreSQL, Kafka and Valkey, reachable from the EKS nodes only"
  vpc_id      = module.vpc.vpc_id
}

resource "aws_vpc_security_group_ingress_rule" "data_from_nodes" {
  for_each = {
    postgres = 5432
    kafka    = 9094
    valkey   = 6379
  }

  security_group_id            = aws_security_group.data.id
  referenced_security_group_id = module.eks.node_security_group_id
  ip_protocol                  = "tcp"
  from_port                    = each.value
  to_port                      = each.value
  description                  = "${each.key} from EKS nodes"
}
