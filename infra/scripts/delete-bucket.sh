#!/usr/bin/env bash
# Vacia (incluidas versiones y marcadores de borrado) y elimina un bucket S3. Si no existe, no hace nada.
# Uso: delete-bucket.sh <bucket>
set -euo pipefail

BUCKET="${1:?Uso: delete-bucket.sh <bucket>}"

if ! aws s3api head-bucket --bucket "$BUCKET" 2>/dev/null; then
  echo "Bucket $BUCKET no existe: nada que borrar"
  exit 0
fi

# Se borra por lotes de 1000 (el maximo de delete-objects).
while true; do
  OBJECTS="$(aws s3api list-object-versions --bucket "$BUCKET" --max-items 1000 --output json \
    | jq -c '[(.Versions // [])[], (.DeleteMarkers // [])[]] | map({Key, VersionId})')"
  [ "$OBJECTS" = "[]" ] && break
  aws s3api delete-objects --bucket "$BUCKET" --delete "{\"Objects\": $OBJECTS, \"Quiet\": true}" >/dev/null
done

aws s3api delete-bucket --bucket "$BUCKET"
echo "Bucket $BUCKET eliminado"
