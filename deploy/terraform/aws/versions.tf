terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.70"
    }
  }

  # Configure remote state before the first apply, for example:
  # backend "s3" {
  #   bucket       = "request-portal-terraform-state"
  #   key          = "aws/terraform.tfstate"
  #   region       = "ap-southeast-1"
  #   use_lockfile = true
  # }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = "request-portal"
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
