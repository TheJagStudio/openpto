# OpenPTO on AWS — reference infrastructure. NOT applied by this project (local-only);
# it documents exactly how each local component maps to its AWS service.
#   gateway        -> Amazon API Gateway (REST) usage plans + API keys + throttling
#   odp/fee/ingest -> ECS Fargate services behind an internal ALB (VPC link)
#   PostgreSQL     -> Aurora PostgreSQL Serverless v2
#   local buckets  -> S3 (raw + processed), ObjectCreated -> transform Lambda
#   web            -> S3 + CloudFront
terraform {
  required_version = ">= 1.8"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 6.0" }
  }
}

provider "aws" {
  region = var.region
  default_tags { tags = { Project = "openpto", Env = var.env } }
}

locals { name = "openpto-${var.env}" }

# ---------------------------------------------------------------- Aurora PostgreSQL
resource "aws_db_subnet_group" "db" {
  name       = local.name
  subnet_ids = var.private_subnet_ids
}

resource "aws_rds_cluster" "db" {
  cluster_identifier          = local.name
  engine                      = "aurora-postgresql"
  engine_mode                 = "provisioned"
  engine_version              = "17.4"
  database_name               = "openpto"
  master_username             = "openpto"
  manage_master_user_password = true # secret lives in Secrets Manager, rotated
  db_subnet_group_name        = aws_db_subnet_group.db.name
  vpc_security_group_ids      = [var.db_security_group_id]
  storage_encrypted           = true
  deletion_protection         = var.env == "prod"
  backup_retention_period     = 7
  serverlessv2_scaling_configuration {
    min_capacity = 0.5
    max_capacity = 8
  }
}

resource "aws_rds_cluster_instance" "db" {
  count              = var.env == "prod" ? 2 : 1 # Multi-AZ writer + reader in prod
  identifier         = "${local.name}-${count.index}"
  cluster_identifier = aws_rds_cluster.db.id
  instance_class     = "db.serverless"
  engine             = aws_rds_cluster.db.engine
}

# ---------------------------------------------------------------- S3 + transform Lambda
resource "aws_s3_bucket" "raw" { bucket = "${local.name}-raw" }
resource "aws_s3_bucket" "processed" { bucket = "${local.name}-processed" }

resource "aws_s3_bucket_public_access_block" "all" {
  for_each                = { raw = aws_s3_bucket.raw.id, processed = aws_s3_bucket.processed.id }
  bucket                  = each.value
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_iam_role" "lambda" {
  name = "${local.name}-transform"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { Service = "lambda.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
}

resource "aws_iam_role_policy" "lambda" {
  role = aws_iam_role.lambda.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Effect = "Allow", Action = ["s3:GetObject"], Resource = "${aws_s3_bucket.raw.arn}/*" },
      { Effect = "Allow", Action = ["s3:PutObject"], Resource = "${aws_s3_bucket.processed.arn}/*" },
      { Effect = "Allow", Action = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"], Resource = "*" },
      { Effect = "Allow", Action = ["ec2:CreateNetworkInterface", "ec2:DescribeNetworkInterfaces", "ec2:DeleteNetworkInterface"], Resource = "*" }
    ]
  })
}

resource "aws_lambda_function" "transform" {
  function_name = "${local.name}-xml-transform"
  role          = aws_iam_role.lambda.arn
  runtime       = "java21"
  handler       = "gov.openpto.transform.LambdaHandler::handleRequest"
  filename      = var.lambda_zip_path # built by: ingest-service> .\gradlew.bat :transform-lambda:lambdaZip
  memory_size   = 1024
  timeout       = 300
  snap_start { apply_on = "PublishedVersions" }
  vpc_config {
    subnet_ids         = var.private_subnet_ids
    security_group_ids = [var.app_security_group_id]
  }
  environment {
    variables = {
      PROCESSED_BUCKET = aws_s3_bucket.processed.bucket
      ODP_BASE_URL     = var.odp_internal_url
    }
  }
}

resource "aws_lambda_permission" "s3" {
  statement_id  = "AllowS3Invoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.transform.function_name
  principal     = "s3.amazonaws.com"
  source_arn    = aws_s3_bucket.raw.arn
}

resource "aws_s3_bucket_notification" "raw" {
  bucket = aws_s3_bucket.raw.id
  lambda_function {
    lambda_function_arn = aws_lambda_function.transform.arn
    events              = ["s3:ObjectCreated:*"]
    filter_suffix       = ".xml"
  }
  depends_on = [aws_lambda_permission.s3]
}

# ---------------------------------------------------------------- API Gateway (keys, usage plans, throttling)
resource "aws_api_gateway_rest_api" "odp" {
  name = local.name
  body = templatefile("${path.module}/openapi-proxy.yaml.tftpl", { vpc_link_id = var.vpc_link_id, alb_dns = var.internal_alb_dns })
}

resource "aws_api_gateway_deployment" "odp" {
  rest_api_id = aws_api_gateway_rest_api.odp.id
  triggers    = { redeploy = sha1(aws_api_gateway_rest_api.odp.body) }
  lifecycle { create_before_destroy = true }
}

resource "aws_api_gateway_stage" "v1" {
  rest_api_id   = aws_api_gateway_rest_api.odp.id
  deployment_id = aws_api_gateway_deployment.odp.id
  stage_name    = "v1"
}

# Same tiers as gateway/ locally: FREE 120/min (≈2 rps, burst 20) & 20k/day
resource "aws_api_gateway_usage_plan" "free" {
  name = "${local.name}-free"
  api_stages {
    api_id = aws_api_gateway_rest_api.odp.id
    stage  = aws_api_gateway_stage.v1.stage_name
  }
  throttle_settings {
    rate_limit  = 2
    burst_limit = 20
  }
  quota_settings {
    limit  = 20000
    period = "DAY"
  }
}

# ---------------------------------------------------------------- Web (S3 + CloudFront)
resource "aws_s3_bucket" "web" { bucket = "${local.name}-web" }

resource "aws_cloudfront_origin_access_control" "web" {
  name                              = "${local.name}-web"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_distribution" "web" {
  enabled             = true
  default_root_object = "index.html"
  origin {
    domain_name              = aws_s3_bucket.web.bucket_regional_domain_name
    origin_id                = "web"
    origin_access_control_id = aws_cloudfront_origin_access_control.web.id
  }
  default_cache_behavior {
    target_origin_id       = "web"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = "658327ea-f89d-4fab-a63d-7e88639e58f6" # Managed-CachingOptimized
  }
  custom_error_response { # SPA deep links
    error_code         = 403
    response_code      = 200
    response_page_path = "/index.html"
  }
  restrictions {
    geo_restriction { restriction_type = "none" }
  }
  viewer_certificate { cloudfront_default_certificate = true }
}
