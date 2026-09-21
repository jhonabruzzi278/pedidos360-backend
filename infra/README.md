# Infraestructura de Pedidos360 (Terraform + GitHub Actions)

Todo el entorno cloud se crea y se destruye desde GitHub Actions con Terraform. Los recursos y tags llevan el prefijo **`jdv`** (Jonathan, Darlette y Victor): nombres `jdv-*` y tags `Project=jdv`, `Owner=jdv`, `Environment=lab`, `ManagedBy=terraform`.

## Arquitectura

```text
Navegador
   |  https                                   (login OIDC + PKCE contra Entra ID: JWT)
   v
CloudFront (jdv-frontend-cdn) --> S3 privado jdv-frontend-<cuenta>      Angular (SPA)
   |
   |  fetch + Authorization: Bearer <JWT>
   v
API Gateway HTTP API (jdv-api)   CORS + JWT authorizer (issuer, audience, scope por ruta)
   |  HTTP_PROXY :8080
   v
EC2 jdv-backend (IP elastica, sin SSH)
   |- pedidos360-bff              :8080  (valida el JWT otra vez, rol `admin` en el POST)
   |- pedidos360-orders-service   :8081  (solo 127.0.0.1)
   '- pedidos360-audit-service    :8082  (solo 127.0.0.1)

S3 jdv-artifacts-<cuenta>  : JAR que compila el CI y que la instancia descarga
S3 jdv-tfstate-<cuenta>    : estado remoto de Terraform (versionado, cifrado, con lock nativo)
```

Rutas del API Gateway (todas exigen JWT valido y el scope indicado):

| Ruta | Scope en el gateway | Rol (lo valida el BFF) | Microservicio que la atiende (via BFF) |
|---|---|---|---|
| `GET /api/work-orders` | `orders.read` | cualquiera | `orders-service` `GET /internal/work-orders` |
| `POST /api/work-orders` | `orders.write` | `admin` | `orders-service` `POST /internal/work-orders` |
| `GET /api/events` | `events.read` | cualquiera | `audit-service` `GET /internal/events` |

Las tres rutas entran por el BFF y no directamente a los microservicios: estos no autentican (`/internal/*`), asi que no se exponen fuera de la instancia, y el rol `admin` del POST solo lo puede verificar el BFF.

Sin token o con token invalido el gateway responde `401`; con token valido pero sin el scope, `403`. CORS permite solo el dominio de CloudFront (el mismo origen que acepta el BFF), con los metodos `GET, POST, OPTIONS` y los encabezados `authorization` y `content-type`. El desarrollo local usa el BFF local (perfil `local`), no este API Gateway.

## Workflows

| Repo | Workflow | Que hace |
|---|---|---|
| backend | `infra-deploy` | Crea el bucket del estado (si falta), `terraform plan` y `apply`. Manual. |
| backend | `infra-destroy` | Destruye todo. Pide escribir `destroy`; opcionalmente borra tambien el bucket del estado. |
| backend | `backend` | CI (compila y prueba). Manual o con `AUTO_DEPLOY=true`: sube los JAR a S3 y reinicia los servicios en EC2 via SSM. |
| frontend | `frontend` | CI (pruebas y build). Manual o con `AUTO_DEPLOY=true`: compila con la configuracion real, publica en S3 e invalida CloudFront. |

## Puesta en marcha

1. **Credenciales.** Inicie el Learner Lab, copie el bloque *AWS Details > AWS CLI* en `~/.aws/credentials` (perfil `[default]`) y ejecute desde este repo:

   ```powershell
   .\infra\scripts\sync-aws-secrets.ps1
   ```

   Carga `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` y `AWS_SESSION_TOKEN` como secretos de **ambos** repos sin mostrarlos ni escribirlos en disco. **Las credenciales del laboratorio cambian en cada inicio y caducan al terminar la sesion: repita este paso cada vez.**

2. **Infraestructura.** En GitHub, *Actions > infra-deploy > Run workflow*. El resumen del job muestra `api_endpoint`, `frontend_url` y la URI de redireccion para Entra ID. Lo ideal es tener ya el tenant y definir `JWT_ISSUER` y `JWT_AUDIENCE` (paso 4) **antes** de este paso: [`ENTRA.md`](ENTRA.md) explica como y en que orden, y asi la instancia no se recrea. Sin tenant todavia se puede ejecutar igual: se usan marcadores de issuer/audience y el BFF no arranca hasta configurarlos.

3. **Entra ID.** Registre `entra_redirect_uri` (plataforma *Aplicacion de pagina unica*) en la aplicacion del frontend.

4. **JWT.** Defina en el repo backend las variables `JWT_ISSUER` y `JWT_AUDIENCE` (*Settings > Secrets and variables > Actions > Variables*) y vuelva a ejecutar `infra-deploy`: actualiza el authorizer y recrea la instancia con la configuracion del BFF.

   | Tenant | `JWT_ISSUER` |
   |---|---|
   | Workforce | `https://login.microsoftonline.com/<TENANT_ID>/v2.0` |
   | External ID | `https://<TENANT_ID>.ciamlogin.com/<TENANT_ID>/v2.0` |

   `JWT_AUDIENCE` es el client id de la aplicacion de la API (o `api://<client id>`, segun la version del token).

5. **Backend.** *Actions > backend > Run workflow*.

6. **Frontend.** En el repo frontend defina las variables `ENTRA_CLIENT_ID` (aplicacion SPA), `ENTRA_API_CLIENT_ID` (aplicacion de la API) y `ENTRA_TENANT_ID` (o `ENTRA_AUTHORITY` para External ID) y ejecute *Actions > frontend > Run workflow*. La URL del API y el dominio de CloudFront los descubre solo. Con `ENTRA_SIGNUP_ENABLED=true` la pantalla de inicio muestra "Crear cuenta" (`prompt=create`); ver la seccion 4 de [`ENTRA.md`](ENTRA.md) para saber cuando conviene.

Variables opcionales en ambos repos: `AWS_REGION` (por defecto `us-east-1`) y `AUTO_DEPLOY=true` para desplegar en cada push a `main`.

### Lo que el frontend espera de Entra ID

- Aplicacion de la API: Application ID URI `api://<client id>`, scopes `orders.read`, `orders.write` y `events.read`, app role `admin`, y `requestedAccessTokenVersion: 2` en el manifiesto (con la version 1 el `iss` y el `aud` del token no coinciden con lo que valida el gateway). Paso a paso, usuarios de prueba, registro de usuarios y evidencia para la presentacion: [`ENTRA.md`](ENTRA.md).
- Aplicacion SPA: URI de redireccion (plataforma *Aplicacion de pagina unica*) igual al dominio de CloudFront, que tambien se usa como URI de cierre de sesion; permisos delegados sobre los tres scopes.
- Los roles se leen del claim `roles` y los scopes del claim `scp` del **access token** de la API; la pagina *Perfil y token* los muestra junto con todos los claims.

## Destruir

*Actions > infra-destroy*, escribir `destroy`. Conserva el bucket del estado para poder recrear; marque `delete_state_bucket` para eliminarlo tambien. CloudFront tarda varios minutos en darse de baja: es la parte lenta.

Tras recrear, **CloudFront tiene un dominio nuevo**: hay que actualizar la URI de redireccion en Entra ID y volver a ejecutar `backend` y `frontend`.

## Notas y limites (AWS Academy Learner Lab)

- No se pueden crear roles IAM: la instancia usa el perfil existente `LabInstanceProfile`.
- El puerto 8080 del BFF esta abierto a Internet porque API Gateway (HTTP API) sale desde IPs no fijas. Es defensa en profundidad, no aislamiento: el BFF valida por su cuenta firma, vigencia, issuer, audience, scope y rol. Los microservicios internos escuchan solo en `127.0.0.1`.
- Si el laboratorio bloquea algo, los interruptores son: `enable_api_access_logs=false` (logs del API Gateway) y `instance_profile_name`. Si SSM no esta disponible, el despliegue del backend falla en el paso *Esperar a que la instancia se registre en SSM*.
- La base de datos cloud aun no forma parte de este Terraform: los servicios usan H2 en memoria (ver README del backend). La VPC ya tiene dos subredes en zonas distintas para poder agregar RDS.
- Cambiar `jwt_issuer`/`jwt_audience` recrea la instancia (`user_data_replace_on_change`); la IP elastica y los JAR en S3 hacen que vuelva sola al servicio.
- Si un job de Terraform se cancela y deja el lock (`.tflock` en el bucket del estado), elimine ese objeto desde S3 o ejecute `terraform force-unlock`.
