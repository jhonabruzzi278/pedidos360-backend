output "api_endpoint" {
  description = "URL base del API Gateway jdv-api: es el apiBaseUrl del frontend."
  value       = aws_apigatewayv2_api.main.api_endpoint
}

output "frontend_url" {
  description = "URL HTTPS del frontend (API Gateway jdv-web -> nginx en la EC2)."
  value       = aws_apigatewayv2_api.web.api_endpoint
}

output "entra_redirect_uri" {
  description = "Registrar como URI de redireccion (plataforma SPA) en la aplicacion de Entra ID. Cambia si se recrea jdv-web."
  value       = aws_apigatewayv2_api.web.api_endpoint
}

output "artifacts_bucket" {
  description = "Bucket con los JAR y el sitio del frontend (lo crea bootstrap-state.sh)."
  value       = local.artifacts_bucket
}

output "backend_instance_id" {
  value = aws_instance.backend.id
}

output "backend_public_ip" {
  value = aws_eip.backend.public_ip
}

output "jwt_configured" {
  description = "false: se usan marcadores de issuer/audience; el BFF no arranca hasta definir JWT_ISSUER y JWT_AUDIENCE."
  value       = local.jwt_configured
}
