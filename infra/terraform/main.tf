data "aws_caller_identity" "current" {}

locals {
  name       = var.prefix
  account_id = data.aws_caller_identity.current.account_id

  common_tags = {
    Project     = var.prefix
    Owner       = var.prefix
    Environment = "lab"
    ManagedBy   = "terraform"
  }

  # Puerto del BFF: unico puerto de aplicacion expuesto. orders-service (8081) y audit-service (8082)
  # escuchan solo en 127.0.0.1 dentro de la instancia.
  bff_port = 8080

  # Hasta que exista el tenant se usan marcadores para poder crear el resto de la infraestructura.
  jwt_configured         = var.jwt_issuer != "" && var.jwt_audience != ""
  jwt_issuer_effective   = local.jwt_configured ? var.jwt_issuer : "https://login.microsoftonline.com/00000000-0000-0000-0000-000000000000/v2.0"
  jwt_audience_effective = local.jwt_configured ? var.jwt_audience : "pendiente-configurar-jwt-audience"
}
