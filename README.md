# Pedidos360 - backend

Backend de Pedidos360 (DSY1107, Desarrollo Cloud Native I): un BFF y dos microservicios Spring Boot, con validacion de JWT emitido por un IDaaS (Microsoft Entra ID). El frontend Angular vive en su propio repositorio.

## Arquitectura

```text
Angular :4200
   |  Authorization: Bearer <JWT>
   v
BFF :8080  -- valida firma, vigencia, issuer y audience; autoriza por scope y rol
   |--------------------|
   v                    v
orders-service :8081   audit-service :8082
   v                    v
H2 en memoria          H2 en memoria      (local; en la nube, perfil `rds`: Amazon RDS PostgreSQL)
```

En la nube el BFF queda detras de AWS API Gateway, que tambien valida el JWT, y los dos microservicios usan Amazon RDS PostgreSQL (ver "Base de datos"). El perfil por defecto no crea ni conecta recursos AWS/Azure.

| Modulo | Puerto | Responsabilidad |
|---|---|---|
| `bff` | 8080 | Punto de entrada: seguridad, CORS y reenvio a los microservicios |
| `orders-service` | 8081 | Ordenes de trabajo y sus items (entidades JPA `OT`, `OT_ITEM`) |
| `audit-service` | 8082 | Eventos de auditoria (entidad JPA `OT_EVENT`) |

## Requisitos

- Java 17 o superior. No hace falta instalar Maven: se incluye Maven Wrapper.
- PowerShell en Windows para los scripts (los comandos `mvnw` funcionan en cualquier sistema).

## Ejecutar en local

```powershell
.\scripts\start-local.ps1   # compila y levanta los tres servicios (BFF con perfil local)
.\scripts\stop-local.ps1    # los detiene
```

Los logs quedan en `.runtime/` (ignorado por Git). Luego levanta el frontend desde su repositorio (`npm start`).

## Pruebas

```powershell
.\mvnw.cmd test
```

## Seguridad

### Rutas del BFF, roles y scopes

Cada ruta exige un scope; la de escritura exige ademas un rol. Todo lo demas se deniega.

| Ruta | Scope | Rol |
|---|---|---|
| `GET /api/work-orders` | `orders.read` | cualquiera |
| `GET /api/events` | `events.read` | cualquiera |
| `POST /api/work-orders` | `orders.write` | `admin` |

Respuestas (todas con cuerpo JSON `{status, error, message}` de texto fijo):

| Codigo | Cuando |
|---|---|
| `401` | Sin token, o token invalido: firma, `exp` ausente o vencido, issuer o audience |
| `403` | Token valido pero sin el scope o el rol requerido, o ruta no declarada |
| `400` / `404` / `409` / `422` | El microservicio rechazo la entrada; el BFF lo propaga con un cuerpo propio (nunca el del microservicio) |
| `413` / `415` / `406` | Cuerpo de mas de 64 KB / no es JSON / `Accept` no soportado |
| `502` | El microservicio respondio con cualquier otro estado (incluidos sus 401/403 internos) |
| `503` | El microservicio no responde o excede el timeout (`services.connect-timeout` 2 s, `services.read-timeout` 10 s) |

Los scopes se leen del claim `scope` o `scp` (el de Entra) y los roles del claim `roles`. Los mismos nombres deben crearse en Entra ID: scopes `orders.read`, `orders.write`, `events.read` y un app role `admin`. Los encabezados `WWW-Authenticate` solo anuncian el esquema (`Bearer`, con `error="invalid_token"` o `"insufficient_scope"` segun el caso), sin el motivo del rechazo.

### Perfil por defecto (nube) y perfil `local`

Sin perfil explicito el BFF esta en modo seguro: valida la firma RS256 con las claves publicas del emisor (`JWT_ISSUER`) y exige `JWT_AUDIENCE`. Ambas variables son obligatorias y no tienen valor por defecto: si faltan, o el emisor no responde, la aplicacion no arranca.

El perfil `local` (`SPRING_PROFILES_ACTIVE=local`) es solo para desarrollo: firma y valida tokens HMAC y expone `POST /dev/token` sin autenticacion (`?role=viewer` por defecto, o `?role=admin`). **Nunca debe activarse en nube.** No reemplaza la evidencia de Entra ID ni de PKCE. Como red de seguridad ante una activacion accidental:

- El BFF escucha **solo en `127.0.0.1`** (`server.address` en `application-local.yml`), asi que no queda accesible desde la red.
- No hay ningun secreto en el repositorio: sin `JWT_LOCAL_SECRET` se genera una clave aleatoria en cada arranque, por lo que los tokens dejan de valer al reiniciar el BFF (hay que volver a iniciar sesion).

Las clases del perfil local siguen empaquetadas en el JAR; solo el perfil las activa.

Las variables de entorno estan documentadas en `.env.example`. `FRONTEND_ORIGIN` debe ser un origen explicito: un comodin (`*`) o un valor vacio impiden arrancar.

### Riesgos conocidos

- **Los microservicios internos no autentican** ni reciben la identidad del usuario: son tan seguros como el aislamiento de red. En nube solo deben ser accesibles desde el BFF (grupos de seguridad de EC2).
- **`POST /api/work-orders` no es idempotente:** si el microservicio confirma la orden pero la conexion cae antes de responder, el BFF devuelve `503` y un reintento del cliente crea una segunda orden.
- **Codificacion en Oracle:** el script de referencia declara `VARCHAR2(n)` sin `CHAR`; con la semantica por defecto (BYTE) un texto con acentos puede exceder la columna aunque cumpla el limite de caracteres validado por el servicio.
- El emisor (`JWT_ISSUER`) no se obliga a ser `https`; en nube debe serlo.

## Base de datos

Dos perfiles, con las mismas entidades y repositorios JPA:

| Perfil | Base de datos | Cuando |
|---|---|---|
| por defecto | H2 en memoria (modo Oracle), `ddl-auto: create-drop` | desarrollo local y pruebas; los datos se pierden al reiniciar |
| `rds` | **Amazon RDS PostgreSQL 16**, `ddl-auto: update` | la nube (EC2); `SPRING_PROFILES_ACTIVE=rds` |

Con el perfil `rds` cada servicio lee la conexion de `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` (`application-rds.yml`), **sin valores por defecto**: si falta alguna, el servicio no arranca en lugar de caer en H2 y perder los datos en silencio. En la EC2 las escribe `user_data`/`deploy.sh` y la contrasena sale de SSM Parameter Store; no hay ninguna credencial en el repositorio. Detalle de la red, el secreto y las concesiones del laboratorio en [`infra/README.md`](infra/README.md#base-de-datos-rds-postgresql).

Ambos servicios arrancan con datos de ejemplo de un taller mecanico (seis ordenes con sus repuestos y mano de obra, y el historial de auditoria correspondiente) **solo si la tabla esta vacia**: en H2 se recargan en cada reinicio; en RDS se cargan una vez y las ordenes creadas desde el frontend se conservan. Las pruebas `WorkOrderPostgresTest` y `AuditEventPostgresTest` arrancan cada servicio con el perfil `rds` contra un PostgreSQL 16 real en Docker (Testcontainers) y se omiten si no hay Docker.

El esquema Oracle de referencia esta en `database/Script.corrected.sql`: es el script original con una unica correccion, un trigger para `OT_ITEM.ITEM_ID` (`SEQ_OT_ITEM` se creaba pero nunca se usaba, y el primer `INSERT` fallaba con `ORA-01400`). El script crea un usuario con una clave de ejemplo (`ChangeMe_2025!`) que debe cambiarse antes de ejecutarlo en un entorno real. La nube usa PostgreSQL porque las entidades no dependen de Oracle y RDS PostgreSQL es mas ligero para el laboratorio; el script no se ejecuta en ningun entorno.

## Alcance y pendientes

Implementado: microservicios, BFF con validacion de JWT, autorizacion por rol y scope, CORS, y 114 pruebas automatizadas (incluidas pruebas sobre servidor real y una verificacion RS256 contra un IdP falso local). Desplegado en la nube: tenant y aplicaciones en Entra ID, API Gateway con JWT authorizer y EC2 (ver la seccion siguiente). Pendiente: el flujo de registro de usuarios desde el frontend (guia en [`infra/ENTRA.md`](infra/ENTRA.md), seccion 4) y que las ordenes nuevas generen su evento de auditoria (hoy el servicio de auditoria solo expone el historial de ejemplo).

## Infraestructura cloud

La infraestructura (VPC, EC2 con nginx, dos API Gateway con JWT authorizer y S3) esta definida como codigo en `infra/` con Terraform y se crea o destruye con GitHub Actions (`infra-deploy`, `infra-destroy`, `backend`). Recursos y tags llevan el prefijo `jdv`. Guia de puesta en marcha, rutas del gateway y limites del Learner Lab en [`infra/README.md`](infra/README.md).
