# Frontend Angular: nginx en la EC2 (puerto 80) detras de un segundo API Gateway que le da HTTPS.
#
# MSAL exige un contexto seguro (HTTPS) y Entra ID no acepta redirecciones http:// que no sean localhost.
# CloudFront esta bloqueado en el Learner Lab y no hay dominio propio para un certificado, asi que el
# dominio https://<id>.execute-api.<region>.amazonaws.com (certificado de AWS) es la salida HTTPS.
#
# Es una API aparte de jdv-api a proposito: jdv-api solo lleva rutas protegidas con JWT.
resource "aws_apigatewayv2_api" "web" {
  name          = "${local.name}-web"
  description   = "Pedidos360: entrega HTTPS del frontend Angular (nginx en EC2). Sin autenticacion."
  protocol_type = "HTTP"

  tags = { Name = "${local.name}-web" }
}

resource "aws_apigatewayv2_integration" "web_root" {
  api_id             = aws_apigatewayv2_api.web.id
  integration_type   = "HTTP_PROXY"
  integration_method = "GET"
  integration_uri    = "http://${aws_eip.backend.public_ip}:${local.web_port}/"
  description        = "nginx (EC2) GET /"
}

resource "aws_apigatewayv2_integration" "web_files" {
  api_id             = aws_apigatewayv2_api.web.id
  integration_type   = "HTTP_PROXY"
  integration_method = "GET"
  integration_uri    = "http://${aws_eip.backend.public_ip}:${local.web_port}/{proxy}"
  description        = "nginx (EC2) archivos estaticos y rutas de Angular"
}

resource "aws_apigatewayv2_route" "web_root" {
  api_id    = aws_apigatewayv2_api.web.id
  route_key = "GET /"
  target    = "integrations/${aws_apigatewayv2_integration.web_root.id}"
}

resource "aws_apigatewayv2_route" "web_files" {
  api_id    = aws_apigatewayv2_api.web.id
  route_key = "GET /{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.web_files.id}"
}

resource "aws_apigatewayv2_stage" "web" {
  api_id      = aws_apigatewayv2_api.web.id
  name        = "$default"
  auto_deploy = true

  default_route_settings {
    throttling_burst_limit = 100
    throttling_rate_limit  = 50
  }

  tags = { Name = "${local.name}-web-default" }
}
