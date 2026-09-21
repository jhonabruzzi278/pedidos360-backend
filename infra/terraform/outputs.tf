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

output "db_endpoint" {
  description = "Endpoint de RDS PostgreSQL (solo alcanzable desde el backend, dentro de la VPC)."
  value       = "${aws_db_instance.main.address}:${aws_db_instance.main.port}"
}

output "db_name" {
  description = "Nombre de la base de datos."
  value       = aws_db_instance.main.db_name
}

output "db_password_parameter" {
  description = "Nombre del parametro SSM (SecureString) con la contrasena de la base. Solo el nombre, nunca el valor."
  value       = aws_ssm_parameter.db_password.name
}
