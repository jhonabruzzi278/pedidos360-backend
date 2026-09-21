terraform {
  required_version = ">= 1.10.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Configuracion parcial: bucket, key y region los inyecta el workflow con -backend-config
  # (el nombre del bucket lleva el id de la cuenta, que solo se conoce en ejecucion).
  backend "s3" {}
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = local.common_tags
  }
}
