# JAR compilados por el workflow backend-deploy; la instancia los descarga con deploy.sh.
# force_destroy: el destroy rapido no debe fallar por objetos dentro del bucket.
resource "aws_s3_bucket" "artifacts" {
  bucket        = "${local.name}-artifacts-${local.account_id}"
  force_destroy = true

  tags = { Name = "${local.name}-artifacts" }
}

resource "aws_s3_bucket_public_access_block" "artifacts" {
  bucket = aws_s3_bucket.artifacts.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
