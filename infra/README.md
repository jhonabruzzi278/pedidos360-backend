# Infraestructura de Pedidos360 (Terraform + GitHub Actions)

Todo el entorno cloud se crea y se destruye desde GitHub Actions con Terraform. Los recursos y tags llevan el prefijo **`jdv`** (Jonathan, Darlette y Victor): nombres `jdv-*` y tags `Project=jdv`, `Owner=jdv`, `Environment=lab`, `ManagedBy=terraform`.

## Arquitectura

```text
Navegador
   |  https                                   (login OIDC + PKCE contra Entra ID: JWT)
   v
API Gateway jdv-web (HTTPS, sin auth)  --HTTP_PROXY :80-->  nginx en la EC2       Angular (SPA)
   |
   |  fetch + Authorization: Bearer <JWT>
   v
API Gateway jdv-api (API Manager)   CORS + JWT authorizer (issuer, audience, scope por ruta)
   |  HTTP_PROXY :8080
   v
EC2 jdv-backend (IP elastica, sin SSH)
   |- nginx                       :80    (frontend Angular)
   |- pedidos360-bff              :8080  (valida el JWT otra vez, rol `admin` en el POST)
   |- pedidos360-orders-service   :8081  (solo 127.0.0.1)
   '- pedidos360-audit-service    :8082  (solo 127.0.0.1)
        |  JDBC :5432 (SSL), solo desde el SG del backend
        v
RDS PostgreSQL jdv-db (subredes privadas, sin acceso publico)   tablas OT, OT_ITEM (orders) y OT_EVENT (audit)

SSM /jdv/db/password       : contrasena de la base (SecureString), la lee la EC2 al desplegar
S3 jdv-artifacts-<cuenta>  : JAR y sitio del frontend que compila el CI y que la instancia descarga
S3 jdv-tfstate-<cuenta>    : estado remoto de Terraform (versionado, cifrado, con lock nativo)
```

**Por que el frontend sale por un segundo API Gateway.** MSAL exige HTTPS (PKCE necesita un contexto seguro) y Entra ID no acepta redirecciones `http://` que no sean `localhost`. CloudFront esta bloqueado en el Learner Lab y no hay dominio propio para un certificado, asi que el dominio `https://<id>.execute-api.<region>.amazonaws.com` de `jdv-web` (certificado de AWS) es la salida HTTPS. Es una API aparte a proposito: **`jdv-api` solo tiene las tres rutas protegidas con JWT**.

Rutas de `jdv-api` (todas exigen JWT valido y el scope indicado):

| Ruta | Scope en el gateway | Rol (lo valida el BFF) | Microservicio que la atiende (via BFF) |
|---|---|---|---|
| `GET /api/work-orders` | `orders.read` | cualquiera | `orders-service` `GET /internal/work-orders` |
| `POST /api/work-orders` | `orders.write` | `admin` | `orders-service` `POST /internal/work-orders` |
| `GET /api/events` | `events.read` | cualquiera | `audit-service` `GET /internal/events` |

Las tres rutas entran por el BFF y no directamente a los microservicios: estos no autentican (`/internal/*`), asi que no se exponen fuera de la instancia, y el rol `admin` del POST solo lo puede verificar el BFF.

Sin token o con token invalido el gateway responde `401`; con token valido pero sin el scope, `403`. CORS permite solo el origen de `jdv-web` (el mismo que acepta el BFF), con los metodos `GET, POST, OPTIONS` y los encabezados `authorization` y `content-type`. El desarrollo local usa el BFF local (perfil `local`), no este API Gateway.

## Base de datos (RDS PostgreSQL)

`orders-service` y `audit-service` guardan sus datos en **Amazon RDS PostgreSQL 16** (`jdv-db`, `db.t3.micro`, 20 GB), definida en `terraform/database.tf`.

- **Red.** Dos subredes privadas nuevas, sin ruta a Internet. `publicly_accessible = false` y un grupo de seguridad que solo admite el puerto 5432 **desde el grupo de seguridad del backend**.
- **Contrasena.** La genera Terraform (`random_password`) y queda en un parametro **SSM SecureString** (`/jdv/db/password`). `deploy.sh` la lee al desplegar y la escribe en `/etc/pedidos360/db-secret.env` (modo 600); no esta en `user_data`, ni en el repositorio, ni en variables de GitHub. Tambien queda en el estado de Terraform (bucket privado, cifrado y versionado), como en cualquier despliegue que cree la base con Terraform.
- **Conexion.** Perfil Spring `rds` (`application-rds.yml` de cada servicio) con `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`, sin valores por defecto: si falta alguna el servicio no arranca en vez de caer en H2. RDS PostgreSQL 15 o superior obliga a SSL; se usa `sslmode=require` (cifra el canal sin verificar el certificado; el trafico no sale de la VPC).
- **Esquema.** `ddl-auto: update`: Hibernate crea las tablas la primera vez y conserva los datos. Los datos de ejemplo se cargan solo si la tabla esta vacia, asi que sobreviven a los reinicios.
- **Concesiones del laboratorio.** Una sola instancia y una sola base para los dos servicios (lo ideal seria una por servicio), sin copias de seguridad ni instantanea final, para que `infra-destroy` la elimine rapido. Costo aproximado: US$0,02 por hora mas el almacenamiento.
- **Local y pruebas** siguen con H2 en memoria. Las pruebas `*PostgresTest` arrancan cada servicio con el perfil `rds` contra un PostgreSQL 16 real en Docker (Testcontainers); sin Docker se omiten.

## Workflows

| Repo | Workflow | Que hace |
|---|---|---|
| backend | `infra-deploy` | Crea los buckets de estado y de artefactos (si faltan), `terraform plan` y `apply`. Manual. |
| backend | `infra-destroy` | Destruye todo y borra el bucket de artefactos. Pide escribir `destroy`; opcionalmente borra tambien el bucket del estado. |
| backend | `backend` | CI (compila y prueba). Manual o con `AUTO_DEPLOY=true`: sube los JAR a S3 y reinicia los servicios en EC2 via SSM. |
| frontend | `frontend` | CI (pruebas y build). Manual o con `AUTO_DEPLOY=true`: compila con la configuracion real, sube el sitio a S3 y lo publica en nginx via SSM. |

## Puesta en marcha

1. **Credenciales.** Inicie el Learner Lab, copie el bloque *AWS Details > AWS CLI* en `~/.aws/credentials` (perfil `[default]`) y ejecute desde este repo:

   ```powershell
   powershell -ExecutionPolicy Bypass -File .\infra\scripts\sync-aws-secrets.ps1
   ```

   Carga `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` y `AWS_SESSION_TOKEN` como secretos de **ambos** repos sin mostrarlos ni escribirlos en disco. **Las credenciales del laboratorio cambian en cada inicio y caducan al terminar la sesion: repita este paso cada vez.**

2. **Entra ID y variables.** Cree el tenant y las dos aplicaciones y cargue las variables de GitHub siguiendo [`ENTRA.md`](ENTRA.md). Lo ideal es hacerlo **antes** del paso 3: asi la instancia arranca con el BFF configurado y no se recrea.

   | Tenant | `JWT_ISSUER` |
   |---|---|
   | Workforce | `https://login.microsoftonline.com/<TENANT_ID>/v2.0` |
   | External ID | `https://<TENANT_ID>.ciamlogin.com/<TENANT_ID>/v2.0` |

   `JWT_AUDIENCE` es el client id de la aplicacion de la API (con `requestedAccessTokenVersion: 2` el `aud` del token es ese GUID).

3. **Infraestructura.** *Actions > infra-deploy > Run workflow* (o `gh workflow run infra-deploy.yml`). Tarda unos 15 a 20 minutos, casi todo la creacion de RDS. Con la opcion `plan_only` solo planifica. El resumen del job muestra `api_endpoint`, `frontend_url`, `db_endpoint` y la URI de redireccion para Entra ID. **Orden recomendado:** ejecute antes `backend` y `frontend` para que los JAR y el sitio ya esten en S3: la instancia nueva los descarga sola al arrancar y no hace falta otro despliegue. Sin tenant todavia se puede ejecutar igual: se usan marcadores de issuer/audience y el BFF no arranca hasta configurarlos.

4. **URI de redireccion.** Registre `entra_redirect_uri` (la URL de `jdv-web`, sin barra final) como plataforma *Aplicacion de pagina unica* en la aplicacion del frontend.

5. **Backend.** *Actions > backend > Run workflow*.

6. **Frontend.** En el repo frontend defina las variables `ENTRA_CLIENT_ID` (aplicacion SPA), `ENTRA_API_CLIENT_ID` (aplicacion de la API) y `ENTRA_TENANT_ID` (o `ENTRA_AUTHORITY` para External ID) y ejecute *Actions > frontend > Run workflow*. Las URL de `jdv-api` y `jdv-web` y la instancia las descubre solo. Con `ENTRA_SIGNUP_ENABLED=true` la pantalla de inicio muestra "Crear cuenta" (en External ID abre el alta con `prompt=create`; en el tenant workforce hace un inicio de sesion normal); ver la seccion 4 de [`ENTRA.md`](ENTRA.md) para saber cuando conviene.

Variables opcionales en ambos repos: `AWS_REGION` (por defecto `us-east-1`) y `AUTO_DEPLOY=true` para desplegar en cada push a `main`.

### Lo que el frontend espera de Entra ID

- Aplicacion de la API: Application ID URI `api://<client id>`, scopes `orders.read`, `orders.write` y `events.read`, app role `admin`, y `requestedAccessTokenVersion: 2` en el manifiesto (con la version 1 el `iss` y el `aud` del token no coinciden con lo que valida el gateway). Paso a paso, usuarios de prueba, registro de usuarios y evidencia para la presentacion: [`ENTRA.md`](ENTRA.md).
- Aplicacion SPA: URI de redireccion (plataforma *Aplicacion de pagina unica*) igual a la URL de `jdv-web`, que tambien se usa como URI de cierre de sesion; permisos delegados sobre los tres scopes con consentimiento de administrador.
- Los roles se leen del claim `roles` y los scopes del claim `scp` del **access token** de la API; la pagina *Perfil y token* los muestra junto con todos los claims.

## Destruir

*Actions > infra-destroy*, escribir `destroy`. Conserva el bucket del estado para poder recrear; marque `delete_state_bucket` para eliminarlo tambien.

Tras recrear, **`jdv-web` tiene un dominio nuevo**: hay que actualizar la URI de redireccion en Entra ID y volver a ejecutar `backend` y `frontend`.

## Notas y limites (AWS Academy Learner Lab)

Limites que se descubrieron al desplegar y que explican el diseno:

- **CloudFront esta bloqueado por completo** (`CreateDistribution`, `CreateOriginAccessControl`, `CreateCloudFrontOriginAccessIdentity` y `ListDistributions` dan `AccessDenied`). De ahi el frontend en nginx detras de `jdv-web`.
- **El recurso `aws_s3_bucket` de Terraform no funciona**: una politica de la organizacion deniega `s3:GetBucketObjectLockConfiguration`, que el proveedor lee siempre. Los buckets los crea `scripts/bootstrap-state.sh` con el AWS CLI y `scripts/delete-bucket.sh` los borra; `removed.tf` limpia del estado los recursos viejos.
- No se pueden crear roles IAM: la instancia usa el perfil existente `LabInstanceProfile`.
- No se pueden listar las politicas administradas de CloudFront: por eso no se usan `data` sources de ese tipo.

Otras notas:

- Los puertos 80 (nginx) y 8080 (BFF) de la instancia estan abiertos a Internet porque API Gateway (HTTP API) sale desde IPs no fijas. Es defensa en profundidad, no aislamiento: el BFF valida por su cuenta firma, vigencia, issuer, audience, scope y rol, y nginx solo sirve contenido publico. Los microservicios internos escuchan solo en `127.0.0.1`.
- Interruptores por si el laboratorio bloquea algo: `enable_api_access_logs=false` (logs del API Gateway) y `instance_profile_name`. Si SSM no esta disponible, los despliegues fallan en el paso *Esperar a que la instancia se registre en SSM*.
- Cambiar `jwt_issuer`/`jwt_audience` o cualquier dato de la base (host, usuario, nombre) recrea la instancia (`user_data_replace_on_change`); la IP elastica y los artefactos en S3 hacen que vuelva sola al servicio. Son unos minutos sin servicio: no lo haga durante una presentacion.
- La instancia lee la contrasena de la base de SSM con `LabInstanceProfile`. Si el laboratorio le negara `ssm:GetParameter` o `kms:Decrypt`, `deploy.sh` falla con ese mensaje (revise el paso de despliegue en el job `backend`).
- `infra-destroy` elimina tambien la base de datos y todos sus datos (`skip_final_snapshot`). El parametro SSM y la contrasena se destruyen con ella.
- Si un job de Terraform se cancela y deja el lock (`.tflock` en el bucket del estado), elimine ese objeto desde S3 o ejecute `terraform force-unlock`.
