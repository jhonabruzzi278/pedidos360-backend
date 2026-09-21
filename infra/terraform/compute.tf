data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# Una sola instancia con los tres modulos (bff, orders-service, audit-service), un servicio systemd cada
# uno, y nginx sirviendo el frontend.
resource "aws_instance" "backend" {
  ami                         = data.aws_ami.al2023.id
  instance_type               = var.instance_type
  subnet_id                   = aws_subnet.public[0].id
  vpc_security_group_ids      = [aws_security_group.backend.id]
  iam_instance_profile        = var.instance_profile_name
  associate_public_ip_address = true

  user_data = templatefile("${path.module}/templates/user_data.sh.tftpl", {
    region           = var.aws_region
    artifacts_bucket = local.artifacts_bucket
    frontend_origin  = aws_apigatewayv2_api.web.api_endpoint
    jwt_issuer       = local.jwt_issuer_effective
    jwt_audience     = local.jwt_audience_effective
    jwt_configured   = local.jwt_configured

    # Base de datos: la instancia se crea despues de RDS. La contrasena no viaja aqui: deploy.sh la lee de SSM.
    db_host               = aws_db_instance.main.address
    db_port               = local.db_port
    db_name               = var.db_name
    db_username           = var.db_username
    db_password_parameter = aws_ssm_parameter.db_password.name
  })
  # La configuracion vive en user_data: cambiarla (p. ej. al crear el tenant) recrea la instancia.
  # La IP elastica se reasocia sola y los JAR y el sitio se vuelven a bajar de S3 al arrancar.
  user_data_replace_on_change = true

  metadata_options {
    http_endpoint               = "enabled"
    http_tokens                 = "required"
    http_put_response_hop_limit = 1
  }

  root_block_device {
    volume_type           = "gp3"
    volume_size           = 16
    encrypted             = true
    delete_on_termination = true
  }

  tags        = { Name = "${local.name}-backend", Role = "backend" }
  volume_tags = { Name = "${local.name}-backend-root" }

  lifecycle {
    # Una AMI nueva no debe recrear la instancia.
    ignore_changes = [ami]
  }
}

# IP fija: es el destino de la integracion de API Gateway y sobrevive a la recreacion de la instancia.
resource "aws_eip" "backend" {
  domain     = "vpc"
  depends_on = [aws_internet_gateway.main]

  tags = { Name = "${local.name}-backend-eip" }
}

resource "aws_eip_association" "backend" {
  instance_id         = aws_instance.backend.id
  allocation_id       = aws_eip.backend.id
  allow_reassociation = true
}
