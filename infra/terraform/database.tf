# Base de datos de Pedidos360: Amazon RDS PostgreSQL en subredes privadas. Solo el backend puede conectarse
# (puerto 5432, desde su grupo de seguridad). orders-service y audit-service comparten la instancia y la base
# (cada uno usa sus propias tablas); es una concesion del laboratorio.

locals {
  db_port = 5432
}

# Sin ruta a Internet: no se asocian a ninguna tabla de rutas, asi que usan la principal de la VPC (solo trafico
# local). RDS exige un grupo de subredes con al menos dos zonas de disponibilidad.
resource "aws_subnet" "private" {
  count = 2

  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index + 10)
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = { Name = "${local.name}-private-${count.index + 1}" }
}

resource "aws_db_subnet_group" "main" {
  name        = "${local.name}-db-subnets"
  description = "Subredes privadas de la base de datos de Pedidos360"
  subnet_ids  = aws_subnet.private[*].id

  tags = { Name = "${local.name}-db-subnets" }
}

resource "aws_security_group" "db" {
  name        = "${local.name}-sg-db"
  description = "Pedidos360: PostgreSQL (5432) solo desde el backend."
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${local.name}-sg-db" }
}

resource "aws_vpc_security_group_ingress_rule" "db_from_backend" {
  security_group_id            = aws_security_group.db.id
  description                  = "PostgreSQL desde el backend"
  ip_protocol                  = "tcp"
  from_port                    = local.db_port
  to_port                      = local.db_port
  referenced_security_group_id = aws_security_group.backend.id
}

# Solo alfanumerica: viaja en un archivo de variables de systemd y en una URL de conexion sin escapes.
# Queda tambien en el estado de Terraform (bucket privado, cifrado y versionado).
resource "random_password" "db" {
  length  = 24
  special = false
}

resource "aws_db_instance" "main" {
  identifier = "${local.name}-db"

  engine         = "postgres"
  engine_version = var.db_engine_version
  instance_class = var.db_instance_class

  allocated_storage = var.db_allocated_storage
  storage_type      = "gp2"
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result
  port     = local.db_port

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false

  # Laboratorio: sin copias de seguridad ni instantanea final para que infra-destroy la elimine rapido.
  backup_retention_period = 0
  skip_final_snapshot     = true
  deletion_protection     = false
  apply_immediately       = true

  tags = { Name = "${local.name}-db" }

  lifecycle {
    # AWS aplica las versiones menores solo: no debe recrear ni modificar la instancia.
    ignore_changes = [engine_version]
  }
}

# La EC2 lee la contrasena de aqui al desplegar (deploy.sh): no viaja en user_data ni en el repositorio.
resource "aws_ssm_parameter" "db_password" {
  name        = "/${local.name}/db/password"
  description = "Contrasena del usuario de la base de datos de Pedidos360"
  type        = "SecureString"
  value       = random_password.db.result

  tags = { Name = "${local.name}-db-password" }
}
