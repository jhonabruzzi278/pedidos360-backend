data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "${local.name}-vpc" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "${local.name}-igw" }
}

# Dos subredes en AZ distintas: hoy solo se usa la primera, pero una base de datos RDS
# exige un grupo de subredes en al menos dos zonas.
resource "aws_subnet" "public" {
  count = 2

  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index)
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true

  tags = { Name = "${local.name}-public-${count.index + 1}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "${local.name}-public-rt" }
}

resource "aws_route_table_association" "public" {
  count = length(aws_subnet.public)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# API Gateway (HTTP API) invoca la integracion desde IPs publicas no fijas, por eso el puerto del BFF
# no se puede acotar a un origen. El BFF valida igualmente firma, vigencia, issuer y audience del JWT.
resource "aws_security_group" "backend" {
  name        = "${local.name}-sg-backend"
  description = "Pedidos360: BFF (8080) y nginx (80). Sin SSH (acceso por SSM)."
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${local.name}-sg-backend" }
}

resource "aws_vpc_security_group_ingress_rule" "bff" {
  security_group_id = aws_security_group.backend.id
  description       = "BFF desde API Gateway"
  ip_protocol       = "tcp"
  from_port         = local.bff_port
  to_port           = local.bff_port
  cidr_ipv4         = "0.0.0.0/0"
}

# El segundo API Gateway (jdv-web) entrega el frontend desde nginx; tambien sale desde IPs no fijas.
resource "aws_vpc_security_group_ingress_rule" "web" {
  security_group_id = aws_security_group.backend.id
  description       = "nginx (frontend) desde API Gateway"
  ip_protocol       = "tcp"
  from_port         = local.web_port
  to_port           = local.web_port
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.backend.id
  description       = "Salida libre (S3, SSM, JWKS del IDaaS, paquetes)"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}
