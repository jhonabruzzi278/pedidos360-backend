locals {
  # Contrato del BFF (ver README del backend). El scope se exige ya en el gateway; el rol `admin`
  # del POST lo verifica el BFF (un JWT authorizer de HTTP API no evalua el claim `roles`).
  # Todas las rutas entran por el BFF, que reenvia al microservicio dueno de cada recurso: los
  # microservicios no autentican y por eso no se exponen (escuchan solo en 127.0.0.1).
  api_routes = {
    "GET /api/work-orders" = {
      method      = "GET"
      path        = "/api/work-orders"
      scopes      = ["orders.read"]
      description = "BFF -> orders-service GET /internal/work-orders"
    }
    "POST /api/work-orders" = {
      method      = "POST"
      path        = "/api/work-orders"
      scopes      = ["orders.write"]
      description = "BFF (rol admin) -> orders-service POST /internal/work-orders"
    }
    "GET /api/events" = {
      method      = "GET"
      path        = "/api/events"
      scopes      = ["events.read"]
      description = "BFF -> audit-service GET /internal/events"
    }
  }
}

resource "aws_apigatewayv2_api" "main" {
  name          = "${local.name}-api"
  description   = "Pedidos360: intermediario entre el frontend y el BFF, con validacion de JWT"
  protocol_type = "HTTP"

  # Origenes explicitos, solo los metodos y encabezados necesarios, sin comodines.
  cors_configuration {
    allow_origins = concat(["https://${aws_cloudfront_distribution.frontend.domain_name}"], var.extra_cors_origins)
    allow_methods = ["GET", "POST", "OPTIONS"]
    allow_headers = ["authorization", "content-type"]
    max_age       = 3600
  }

  tags = { Name = "${local.name}-api" }
}

resource "aws_apigatewayv2_authorizer" "jwt" {
  api_id           = aws_apigatewayv2_api.main.id
  name             = "${local.name}-entra-jwt"
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]

  # Verifica firma (JWKS del emisor), vigencia, iss y aud.
  jwt_configuration {
    issuer   = local.jwt_issuer_effective
    audience = [local.jwt_audience_effective]
  }
}

resource "aws_apigatewayv2_integration" "bff" {
  for_each = local.api_routes

  api_id             = aws_apigatewayv2_api.main.id
  integration_type   = "HTTP_PROXY"
  integration_method = each.value.method
  integration_uri    = "http://${aws_eip.backend.public_ip}:${local.bff_port}${each.value.path}"
  description        = each.value.description
}

resource "aws_apigatewayv2_route" "bff" {
  for_each = local.api_routes

  api_id               = aws_apigatewayv2_api.main.id
  route_key            = each.key
  target               = "integrations/${aws_apigatewayv2_integration.bff[each.key].id}"
  authorization_type   = "JWT"
  authorizer_id        = aws_apigatewayv2_authorizer.jwt.id
  authorization_scopes = each.value.scopes
}

resource "aws_cloudwatch_log_group" "api" {
  count = var.enable_api_access_logs ? 1 : 0

  name              = "/aws/apigateway/${local.name}-api"
  retention_in_days = 7

  tags = { Name = "${local.name}-api-logs" }
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.main.id
  name        = "$default"
  auto_deploy = true

  default_route_settings {
    throttling_burst_limit = 50
    throttling_rate_limit  = 25
  }

  dynamic "access_log_settings" {
    for_each = var.enable_api_access_logs ? [1] : []

    content {
      destination_arn = aws_cloudwatch_log_group.api[0].arn
      format = jsonencode({
        requestId        = "$context.requestId"
        requestTime      = "$context.requestTime"
        sourceIp         = "$context.identity.sourceIp"
        routeKey         = "$context.routeKey"
        status           = "$context.status"
        authorizerError  = "$context.authorizer.error"
        integrationError = "$context.integrationErrorMessage"
        responseLength   = "$context.responseLength"
      })
    }
  }

  tags = { Name = "${local.name}-api-default" }
}
