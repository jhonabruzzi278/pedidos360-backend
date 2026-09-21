#!/usr/bin/env bash
# Crea (si no existen) los dos buckets S3 que Terraform no puede gestionar en el Learner Lab. Idempotente.
#   jdv-tfstate-<cuenta>    estado remoto de Terraform (versionado)
#   jdv-artifacts-<cuenta>  JAR del backend y sitio del frontend
# Se crean con el AWS CLI porque el recurso aws_s3_bucket lee s3:GetBucketObjectLockConfiguration, que la
# politica de la organizacion deniega. Requiere credenciales AWS en el entorno y AWS_REGION.
set -euo pipefail

: "${AWS_REGION:?Defina AWS_REGION}"
PREFIX="${PREFIX:-jdv}"

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
STATE_BUCKET="${STATE_BUCKET:-${PREFIX}-tfstate-${ACCOUNT_ID}}"
ARTIFACTS_BUCKET="${ARTIFACTS_BUCKET:-${PREFIX}-artifacts-${ACCOUNT_ID}}"

# ensure_bucket <nombre> <sufijo del tag Name> <versionado: yes|no>
ensure_bucket() {
  local bucket="$1" tag_name="$2" versioned="$3"

  if aws s3api head-bucket --bucket "$bucket" 2>/dev/null; then
    echo "Bucket ya existe: $bucket"
  else
    echo "Creando bucket: $bucket ($AWS_REGION)"
    if [ "$AWS_REGION" = "us-east-1" ]; then
      aws s3api create-bucket --bucket "$bucket" --region "$AWS_REGION" >/dev/null
    else
      aws s3api create-bucket --bucket "$bucket" --region "$AWS_REGION" \
        --create-bucket-configuration "LocationConstraint=$AWS_REGION" >/dev/null
    fi
  fi

  # Se reaplica siempre: deja el bucket con la configuracion esperada aunque alguien la haya cambiado.
  if [ "$versioned" = "yes" ]; then
    aws s3api put-bucket-versioning --bucket "$bucket" --versioning-configuration Status=Enabled
  fi
  aws s3api put-public-access-block --bucket "$bucket" \
    --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
  aws s3api put-bucket-encryption --bucket "$bucket" \
    --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
  aws s3api put-bucket-tagging --bucket "$bucket" \
    --tagging "TagSet=[{Key=Name,Value=${PREFIX}-${tag_name}},{Key=Project,Value=${PREFIX}},{Key=Owner,Value=${PREFIX}},{Key=ManagedBy,Value=script}]"
}

ensure_bucket "$STATE_BUCKET" "tfstate" "yes"
ensure_bucket "$ARTIFACTS_BUCKET" "artifacts" "no"

echo "STATE_BUCKET=$STATE_BUCKET"
echo "ARTIFACTS_BUCKET=$ARTIFACTS_BUCKET"
if [ -n "${GITHUB_ENV:-}" ]; then
  {
    echo "STATE_BUCKET=$STATE_BUCKET"
    echo "ARTIFACTS_BUCKET=$ARTIFACTS_BUCKET"
  } >> "$GITHUB_ENV"
fi
