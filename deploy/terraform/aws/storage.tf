# Uploaded files and generated documents (document-service, phase 6). Private, encrypted, versioned.
resource "aws_s3_bucket" "documents" {
  bucket_prefix = "${local.name}-documents-"
}

resource "aws_s3_bucket_public_access_block" "documents" {
  bucket                  = aws_s3_bucket.documents.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "aws:kms"
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_versioning" "documents" {
  bucket = aws_s3_bucket.documents.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "documents" {
  bucket = aws_s3_bucket.documents.id

  # Bulk ZIP downloads are temporary: they expire after three days, as in the feature specification.
  rule {
    id     = "expire-bulk-exports"
    status = "Enabled"

    filter {
      prefix = "bulk-exports/"
    }

    expiration {
      days = 3
    }

    noncurrent_version_expiration {
      noncurrent_days = 1
    }
  }
}

# document-service reaches the bucket through EKS Pod Identity: no access keys in the cluster.
data "aws_iam_policy_document" "document_service_trust" {
  statement {
    actions = ["sts:AssumeRole", "sts:TagSession"]

    principals {
      type        = "Service"
      identifiers = ["pods.eks.amazonaws.com"]
    }
  }
}

data "aws_iam_policy_document" "documents_access" {
  statement {
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.documents.arn}/*"]
  }

  statement {
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.documents.arn]
  }
}

resource "aws_iam_role" "document_service" {
  name               = "${local.name}-document-service"
  assume_role_policy = data.aws_iam_policy_document.document_service_trust.json
}

resource "aws_iam_role_policy" "document_service" {
  name   = "documents-bucket"
  role   = aws_iam_role.document_service.id
  policy = data.aws_iam_policy_document.documents_access.json
}

# The Helm chart names the service account <release>-document-service; the release is request-portal.
resource "aws_eks_pod_identity_association" "document_service" {
  cluster_name    = module.eks.cluster_name
  namespace       = "request-portal"
  service_account = "request-portal-document-service"
  role_arn        = aws_iam_role.document_service.arn
}

# Notification emails go out through SES; publish the DKIM records it outputs in the domain's DNS.
resource "aws_sesv2_email_identity" "mail" {
  email_identity = var.mail_domain
}
