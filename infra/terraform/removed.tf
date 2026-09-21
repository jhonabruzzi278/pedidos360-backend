# Estos buckets se crearon con aws_s3_bucket en un intento anterior. El proveedor lee siempre
# s3:GetBucketObjectLockConfiguration, que la politica de la organizacion del Learner Lab deniega, asi
# que el recurso queda inutilizable. Ahora los buckets los crea infra/scripts/bootstrap-state.sh con el
# AWS CLI. Estos bloques hacen que Terraform olvide los recursos viejos del estado sin tocar los buckets.
removed {
  from = aws_s3_bucket.artifacts

  lifecycle {
    destroy = false
  }
}

removed {
  from = aws_s3_bucket.frontend

  lifecycle {
    destroy = false
  }
}
