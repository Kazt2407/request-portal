variable "region" {
  description = "AWS region. Singapore is the closest region to Vietnam."
  type        = string
  default     = "ap-southeast-1"
}

variable "environment" {
  description = "Environment name, used in resource names and tags."
  type        = string
  default     = "staging"
}

variable "vpc_cidr" {
  type    = string
  default = "10.40.0.0/16"
}

variable "kubernetes_version" {
  type    = string
  default = "1.34"
}

variable "node_instance_types" {
  description = "EKS node group instance types. Five JVM services fit on two m7i.large nodes with room for surges."
  type        = list(string)
  default     = ["m7i.large"]
}

variable "node_count" {
  type = object({ min = number, desired = number, max = number })
  default = {
    min     = 2
    desired = 2
    max     = 5
  }
}

variable "postgres_version" {
  type    = string
  default = "18"
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.medium"
}

variable "db_multi_az" {
  description = "Standby in a second availability zone. Turn on for production."
  type        = bool
  default     = false
}

variable "kafka_version" {
  type    = string
  default = "3.9.x"
}

variable "kafka_broker_instance_type" {
  type    = string
  default = "kafka.t3.small"
}

variable "cache_node_type" {
  type    = string
  default = "cache.t4g.small"
}

variable "portal_url" {
  description = "Public URL of the web app; browsers upload to and download from the bucket from this origin."
  type        = string
  default     = "https://portal.example.edu"
}

variable "mail_domain" {
  description = "Domain the notification emails are sent from; SES needs its DKIM records in DNS."
  type        = string
  default     = "request-portal.example.edu"
}

variable "deletion_protection" {
  description = "Protect the database from accidental deletion. Keep on outside throwaway environments."
  type        = bool
  default     = true
}
