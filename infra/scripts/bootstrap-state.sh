#!/usr/bin/env bash
# Crea (si no existe) el bucket S3 donde Terraform guarda su estado. Idempotente.
# Nombre: jdv-tfstate-<id de cuenta>. Requiere credenciales AWS en el entorno y AWS_REGION.
set -euo pipefail

: "${AWS_REGION:?Defina AWS_REGION}"
PREFIX="${PREFIX:-jdv}"

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
BUCKET="${STATE_BUCKET:-${PREFIX}-tfstate-${ACCOUNT_ID}}"

if aws s3api head-bucket --bucket "$BUCKET" 2>/dev/null; then
  echo "Bucket de estado ya existe: $BUCKET"
else
  echo "Creando bucket de estado: $BUCKET ($AWS_REGION)"
  if [ "$AWS_REGION" = "us-east-1" ]; then
    aws s3api create-bucket --bucket "$BUCKET" --region "$AWS_REGION" >/dev/null
  else
    aws s3api create-bucket --bucket "$BUCKET" --region "$AWS_REGION" \
      --create-bucket-configuration "LocationConstraint=$AWS_REGION" >/dev/null
  fi
fi

# Se reaplica siempre: deja el bucket con la configuracion esperada aunque alguien la haya cambiado.
aws s3api put-bucket-versioning --bucket "$BUCKET" \
  --versioning-configuration Status=Enabled
aws s3api put-public-access-block --bucket "$BUCKET" \
  --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
aws s3api put-bucket-encryption --bucket "$BUCKET" \
  --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
aws s3api put-bucket-tagging --bucket "$BUCKET" \
  --tagging "TagSet=[{Key=Name,Value=${PREFIX}-tfstate},{Key=Project,Value=${PREFIX}},{Key=Owner,Value=${PREFIX}},{Key=ManagedBy,Value=script}]"

echo "STATE_BUCKET=$BUCKET"
if [ -n "${GITHUB_ENV:-}" ]; then
  echo "STATE_BUCKET=$BUCKET" >> "$GITHUB_ENV"
fi
