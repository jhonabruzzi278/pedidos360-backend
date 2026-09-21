output "api_endpoint" {
  description = "URL base del API Gateway: es el apiBaseUrl del frontend."
  value       = aws_apigatewayv2_api.main.api_endpoint
}

output "frontend_url" {
  description = "URL HTTPS del frontend (CloudFront)."
  value       = "https://${aws_cloudfront_distribution.frontend.domain_name}"
}

output "entra_redirect_uri" {
  description = "Registrar como URI de redireccion (plataforma SPA) en la aplicacion de Entra ID. Cambia si se recrea CloudFront."
  value       = "https://${aws_cloudfront_distribution.frontend.domain_name}"
}

output "cloudfront_distribution_id" {
  value = aws_cloudfront_distribution.frontend.id
}

output "frontend_bucket" {
  value = aws_s3_bucket.frontend.bucket
}

output "artifacts_bucket" {
  value = aws_s3_bucket.artifacts.bucket
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
