variable "aws_region" {
  description = "Region AWS. El Learner Lab solo permite us-east-1 y us-west-2."
  type        = string
  default     = "us-east-1"
}

variable "prefix" {
  description = "Prefijo de todos los nombres y valor del tag Project (iniciales del grupo)."
  type        = string
  default     = "jdv"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,10}$", var.prefix))
    error_message = "El prefijo debe ser minusculas, digitos o guiones (2 a 11 caracteres): forma parte de nombres de buckets S3."
  }
}

variable "instance_type" {
  description = "Tipo de instancia EC2 del backend (tres JVM Spring Boot). El Learner Lab admite hasta large."
  type        = string
  default     = "t3.small"
}

variable "instance_profile_name" {
  description = "Perfil de instancia existente. El Learner Lab no permite crear roles IAM: se reutiliza LabInstanceProfile."
  type        = string
  default     = "LabInstanceProfile"
}

variable "vpc_cidr" {
  description = "CIDR de la VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "jwt_issuer" {
  description = "Emisor (iss) de los tokens del IDaaS, p. ej. https://login.microsoftonline.com/<TENANT_ID>/v2.0. Vacio: se usa un marcador y el BFF no arranca."
  type        = string
  default     = ""
}

variable "jwt_audience" {
  description = "Audiencia (aud) esperada: el client id de la API en Entra ID (o api://<client id>). Vacio: se usa un marcador."
  type        = string
  default     = ""
}

variable "extra_cors_origins" {
  description = "Origenes adicionales permitidos por CORS en API Gateway (ademas del dominio de CloudFront). El BFF solo acepta un origen (CloudFront), asi que uno extra pasaria el gateway y el BFF lo rechazaria con 403."
  type        = list(string)
  default     = []
}

variable "enable_api_access_logs" {
  description = "Registra las peticiones del API Gateway en CloudWatch Logs (util como evidencia de 200/401/403)."
  type        = bool
  default     = true
}

variable "db_engine_version" {
  description = "Version mayor de PostgreSQL en RDS. AWS elige la menor y el ciclo de vida ignora sus actualizaciones automaticas."
  type        = string
  default     = "16"
}

variable "db_instance_class" {
  description = "Clase de la instancia RDS. db.t3.micro basta para el laboratorio (dos servicios, pocos datos)."
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "Almacenamiento de la base de datos en GB (minimo de RDS para gp2)."
  type        = number
  default     = 20
}

variable "db_name" {
  description = "Nombre de la base de datos. Contiene las tablas de orders-service (OT, OT_ITEM) y audit-service (OT_EVENT)."
  type        = string
  default     = "pedidos360"
}

variable "db_username" {
  description = "Usuario de la base de datos. La contrasena se genera y queda en SSM Parameter Store."
  type        = string
  default     = "pedidos360"
}
