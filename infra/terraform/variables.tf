variable "region" {
  type    = string
  default = "us-east-1"
}
variable "env" {
  type    = string
  default = "dev"
}
variable "private_subnet_ids" { type = list(string) }
variable "db_security_group_id" { type = string }
variable "app_security_group_id" { type = string }
variable "vpc_link_id" { type = string }
variable "internal_alb_dns" { type = string }
variable "odp_internal_url" { type = string }
variable "lambda_zip_path" {
  type    = string
  default = "../../ingest-service/transform-lambda/build/distributions/transform-lambda.zip"
}
