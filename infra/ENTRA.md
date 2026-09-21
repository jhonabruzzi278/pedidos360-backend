# Configuracion de Microsoft Entra ID para Pedidos360

Guia paso a paso para el tenant, las dos aplicaciones, los roles y los usuarios de prueba. Cubre los indicadores de la presentacion sobre tenant (10%), aplicacion (10%), registro e inicio de sesion (10%) y Authorization Code con PKCE (15%). Los nombres de menu del portal cambian de vez en cuando: si alguno no coincide, busque el equivalente.

Convencion: los recursos llevan el prefijo `jdv`.

## 0. Antes de empezar

1. El tenant se crea como indica la guia 1.2.3 del curso: desde la suscripcion Azure for Students, recurso **Microsoft Entra ID**. Dominio inicial sugerido: `jdvpedidos360.onmicrosoft.com` (debe ser unico).
2. Entre a <https://entra.microsoft.com> y **cambie al tenant nuevo** (icono de engranaje > *Cambiar directorio*). Compruebe arriba a la derecha que aparece el tenant `jdv...` y no el de Duoc: en el directorio de Duoc no tendra permisos para registrar aplicaciones.
3. Anote el **Id. de directorio (inquilino)** de *Entra ID > Informacion general*. Es el `TENANT_ID`.

## 1. Aplicacion de la API: `jdv-pedidos360-api`

Representa al BFF/API Gateway: es el recurso cuyo `aud` deben tener los tokens.

**1.1 Registro.** *Entra ID > Registros de aplicaciones > Nuevo registro*
- Nombre: `jdv-pedidos360-api`
- Tipos de cuenta: *Solo cuentas de este directorio organizativo* (un solo inquilino)
- URI de redireccion: vacio
- Anote el **Id. de aplicacion (cliente)**: es el `API_CLIENT_ID`.

**1.2 Exponer una API.** *Exponer una API*
- *Identificador URI de id. de aplicacion* > Agregar > acepte el valor por defecto `api://<API_CLIENT_ID>`.
- *Agregar un ambito*, tres veces (estado *Habilitado*, quien puede dar consentimiento: *Administradores y usuarios*):

| Nombre del ambito | Nombre para mostrar (admin) | Descripcion |
|---|---|---|
| `orders.read` | Leer ordenes de trabajo | Permite consultar las ordenes de trabajo |
| `orders.write` | Crear ordenes de trabajo | Permite crear ordenes de trabajo |
| `events.read` | Leer eventos de auditoria | Permite consultar los eventos de auditoria |

Repita el texto de la descripcion en los campos de usuario si el portal los pide.

**1.3 Tokens v2 (importante).** *Manifiesto*
- Busque `requestedAccessTokenVersion` (segun el formato del editor esta dentro de `api`; en el formato heredado se llama `accessTokenAcceptedVersion`). Por defecto es `null`, que equivale a la version 1.
- Cambielo a `2` y guarde.

Sin este cambio los tokens salen en version 1: el `iss` es `https://sts.windows.net/<TENANT_ID>/` y el `aud` es `api://<API_CLIENT_ID>`, y ni API Gateway ni el BFF (configurados con el emisor `.../v2.0` y el `aud` como GUID) los aceptan: todo responde 401.

**1.4 Rol de aplicacion `admin`.** *Roles de aplicacion > Crear rol de aplicacion*
- Nombre para mostrar: `Administrador`
- Tipos de miembros permitidos: *Usuarios y grupos*
- Valor: `admin` (exactamente asi: lo lee el BFF en el claim `roles`)
- Descripcion: `Puede crear ordenes de trabajo`
- Habilitado: si

## 2. Aplicacion del frontend: `jdv-pedidos360-spa`

**2.1 Registro.** *Registros de aplicaciones > Nuevo registro*
- Nombre: `jdv-pedidos360-spa`
- Tipos de cuenta: *Solo cuentas de este directorio organizativo*
- URI de redireccion: plataforma **Aplicacion de pagina unica (SPA)**, valor `http://localhost:4200`
- Anote el **Id. de aplicacion (cliente)**: es el `SPA_CLIENT_ID`.

Con la plataforma SPA, Entra ID solo permite el flujo *Authorization Code con PKCE*: el frontend no maneja secretos.

**2.2 Autenticacion.** *Autenticacion*
- **No** marque ninguna casilla de *Concesion implicita e flujos hibridos* (ni tokens de acceso ni tokens de id). Es lo que la rubrica evalua como "PKCE correcto, sin Implicit".
- Mas adelante, con la infraestructura creada, agregue tambien como URI de redireccion SPA el dominio de CloudFront (salida `entra_redirect_uri` del workflow `infra-deploy`, del tipo `https://dxxxxxxxx.cloudfront.net`). **Sin barra final.** Esa misma URI se usa al cerrar sesion.

**2.3 Permisos de la API.** *Permisos de API > Agregar un permiso > Mis API > `jdv-pedidos360-api` > Permisos delegados*
- Marque `orders.read`, `orders.write` y `events.read` > *Agregar permisos*.
- Pulse **Conceder consentimiento de administrador para `<su tenant>`** y confirme que las tres filas quedan en verde. Asi ningun usuario ve una pantalla de consentimiento durante la demostracion.

## 3. Usuarios de prueba y roles

*Entra ID > Usuarios > Nuevo usuario > Crear usuario*

| Usuario | Nombre | Rol de aplicacion |
|---|---|---|
| `admin.jdv@<dominio>.onmicrosoft.com` | Admin JDV | `admin` |
| `viewer.jdv@<dominio>.onmicrosoft.com` | Viewer JDV | ninguno |

Anote las contrasenas en un gestor de contrasenas: **no las escriba en el repositorio**.

**Asignar el rol.** *Aplicaciones empresariales > `jdv-pedidos360-api` > Usuarios y grupos > Agregar usuario o grupo*: seleccione `admin.jdv...` y el rol `Administrador`. El usuario `viewer` no lleva rol.

Deje *Asignacion requerida* en **No** (valor por defecto) en ambas aplicaciones: asi el usuario `viewer` puede iniciar sesion sin rol y obtiene un token sin el claim `roles`.

**Antes de la presentacion, inicie sesion una vez con cada usuario** en una ventana privada (<https://myapps.microsoft.com>):
- Con contrasena inicial, Entra ID obliga a cambiarla en el primer acceso: no quiere que eso ocurra frente al docente.
- Un tenant nuevo suele traer *valores predeterminados de seguridad* activados: piden registrar el metodo MFA (Microsoft Authenticator) en el primer acceso y pueden pedirlo de nuevo en inicios de sesion posteriores. O bien registra MFA en ambos usuarios de antemano, o bien desactiva los valores predeterminados en *Entra ID > Propiedades > Administrar valores predeterminados de seguridad* y lo explica como decision del laboratorio. Cualquiera de las dos cuenta como "politica" que puede mostrar.

## 4. Flujo de registro (los usuarios crean su cuenta desde el frontend)

La rubrica (10%) pide que los usuarios puedan crear su cuenta desde el frontend y luego iniciar sesion. Hay dos caminos; **confirme con el docente cual espera**.

**Opcion A: tenant workforce con *user flow* de auto-registro (el que ya tiene).** Microsoft Entra permite auto-registro en tenants workforce mediante flujos de usuario de colaboracion B2B: el usuario se registra con su correo (u otra identidad) y se crea como **invitado (guest)** del tenant.
1. *Entra ID > Identidades externas > Configuracion de colaboracion externa*: habilite *Permitir el registro de autoservicio de invitados mediante flujos de usuario*.
2. *Identidades externas > Flujos de usuario > Nuevo flujo de usuario*: elija los proveedores de identidad (correo con codigo de un solo uso) y los atributos a pedir (nombre).
3. Dentro del flujo, *Aplicaciones > Agregar* `jdv-pedidos360-spa`.
4. Pruebe primero con el boton **Iniciar sesion** normal del frontend en una ventana privada y un correo nuevo. Si Entra ofrece el registro, no necesita nada mas. Solo si no lo ofrece, defina la variable `ENTRA_SIGNUP_ENABLED=true` del repo frontend para mostrar el boton **Crear cuenta** (`prompt=create`).

   Limitaciones: los usuarios son invitados y nacen sin rol; para asignarles `admin` hay que hacerlo desde *Aplicaciones empresariales*. El comportamiento exacto del registro depende del tenant: verifiquelo antes de la presentacion.

**Opcion B: tenant External ID (externo).** Es la solucion pensada para clientes: registro, inicio de sesion y restablecimiento de contrasena propios de la aplicacion. El nivel base es gratuito hasta 50.000 usuarios activos mensuales. Es un tenant **distinto**: hay que repetir las secciones 1 a 3 en el y, ademas, crear el flujo *Registro e inicio de sesion* y asociarle la aplicacion SPA. Cambia lo siguiente:
- `JWT_ISSUER` = `https://<TENANT_ID>.ciamlogin.com/<TENANT_ID>/v2.0`
- `ENTRA_AUTHORITY` = `https://<subdominio>.ciamlogin.com/` (reemplaza a `ENTRA_TENANT_ID`)
- `ENTRA_SIGNUP_ENABLED` = `true`

El resto (API Gateway, EC2, frontend) no cambia. Recomendacion: avance con la opcion A y pase a la B solo si el docente exige el registro de clientes.

## 5. Valores que debe copiar y adonde van

| Valor | Origen | Variable de GitHub |
|---|---|---|
| `https://login.microsoftonline.com/<TENANT_ID>/v2.0` | Tenant ID (paso 0) | repo **backend**: `JWT_ISSUER` |
| `<API_CLIENT_ID>` | Paso 1.1 | repo **backend**: `JWT_AUDIENCE` |
| `<TENANT_ID>` | Paso 0 | repo **frontend**: `ENTRA_TENANT_ID` |
| `<API_CLIENT_ID>` | Paso 1.1 | repo **frontend**: `ENTRA_API_CLIENT_ID` |
| `<SPA_CLIENT_ID>` | Paso 2.1 | repo **frontend**: `ENTRA_CLIENT_ID` |

Con GitHub CLI (son identificadores publicos, no secretos):

```bash
gh variable set JWT_ISSUER          --repo jhonabruzzi278/pedidos360-backend  --body "https://login.microsoftonline.com/<TENANT_ID>/v2.0"
gh variable set JWT_AUDIENCE        --repo jhonabruzzi278/pedidos360-backend  --body "<API_CLIENT_ID>"
gh variable set ENTRA_TENANT_ID     --repo jhonabruzzi278/pedidos360-frontend --body "<TENANT_ID>"
gh variable set ENTRA_API_CLIENT_ID --repo jhonabruzzi278/pedidos360-frontend --body "<API_CLIENT_ID>"
gh variable set ENTRA_CLIENT_ID     --repo jhonabruzzi278/pedidos360-frontend --body "<SPA_CLIENT_ID>"
```

**Orden recomendado** (asi la instancia no se recrea):
1. Secciones 1 a 3 de esta guia (la URI de CloudFront todavia no existe: use solo `http://localhost:4200`).
2. Cargue las cinco variables anteriores.
3. Ejecute `infra-deploy` (repo backend). Copie `entra_redirect_uri` del resumen.
4. Agregue esa URI a `jdv-pedidos360-spa` (paso 2.2).
5. Ejecute `backend` y luego `frontend` (*Run workflow*).

Prueba local opcional del login: edite temporalmente `src/environments/environment.ts` (`authMode: 'entra'` y los IDs). Solo sirve para comprobar el inicio de sesion y la pagina *Perfil y token*: las llamadas al API necesitan la nube. No suba ese cambio.

## 6. Verificacion y evidencia para la presentacion

Con la pagina **Perfil y token** del frontend desplegado, inicie sesion con cada usuario y compruebe los claims del access token:

| Claim | Valor esperado |
|---|---|
| `ver` | `2.0` |
| `iss` | `https://login.microsoftonline.com/<TENANT_ID>/v2.0` |
| `aud` | `<API_CLIENT_ID>` |
| `scp` | `orders.read orders.write events.read` |
| `roles` | `["admin"]` solo para `admin.jdv`; ausente para `viewer.jdv` |

Los roles nuevos aparecen solo en tokens emitidos despues de asignarlos: cierre sesion y vuelva a entrar.

Capturas y demostraciones sugeridas, por indicador de la rubrica:

| Indicador | Que mostrar |
|---|---|
| Tenant (10%) | *Informacion general* del tenant, lista de usuarios, valores predeterminados de seguridad |
| Aplicacion (10%) | Las dos aplicaciones: ambitos expuestos, rol `admin`, URIs de redireccion, permisos con consentimiento concedido |
| Registro e inicio de sesion (10%) | Alta de un usuario nuevo desde el frontend y su primer inicio de sesion (seccion 4) |
| PKCE (15%) | DevTools > Red, filtrar por `authorize`: `response_type=code`, `code_challenge`, `code_challenge_method=S256`, `state`, `nonce`. Luego la peticion `token`: lleva `code_verifier`. Sin `id_token`/`token` en la URL de retorno |
| JWT en todas las rutas (20%) | Panel de ordenes: `200` con token, `401` con *Llamar sin token*, `403` en el POST con `viewer.jdv`. En la consola de API Gateway: autorizador `jdv-entra-jwt` con emisor y audiencia, asociado a las tres rutas |
| Evidencia por ruta (15%) | Las tres rutas del panel con su codigo, y el JSON de `/api/work-orders` y `/api/events` |

Nota sobre el 403: todos los usuarios reciben los tres ambitos (consentimiento de administrador), asi que el `403` de `viewer.jdv` en el POST lo produce el BFF por **rol** (`admin`), no el gateway por ambito. El gateway responde `403` cuando el token no trae el ambito de la ruta.

## 7. Problemas frecuentes

| Sintoma | Causa probable | Solucion |
|---|---|---|
| `AADSTS50011` (URI de respuesta no coincide) | La URI de CloudFront no esta registrada, o tiene barra final, o esta en la plataforma equivocada | Registrela en *Autenticacion* como plataforma SPA, sin barra final |
| `AADSTS65001` (falta consentimiento) | No se concedio el consentimiento de administrador | Paso 2.3 |
| `AADSTS90009`/`AADSTS500011` al pedir el token | El ambito no existe o el URI de id. de aplicacion no es `api://<API_CLIENT_ID>` | Revise el paso 1.2 y las tres variables `ENTRA_*` |
| API Gateway responde 401 con sesion iniciada | Token v1 (paso 1.3), `JWT_AUDIENCE` o `JWT_ISSUER` incorrectos, o `infra-deploy` sin ejecutar tras definir las variables | Decodifique el token en la pagina *Perfil y token* y compare `ver`, `iss` y `aud` con la tabla de la seccion 6 |
| El POST responde 403 con `admin.jdv` | El token es anterior a la asignacion del rol | Cierre sesion y vuelva a entrar; confirme `roles` en *Perfil y token* |
| Error de CORS en el navegador | El origen del frontend no es el de CloudFront que conoce el gateway | Use la URL de CloudFront exacta; si se recreo CloudFront, ejecute `infra-deploy` otra vez |
| `interaction_in_progress` | Un inicio de sesion anterior quedo a medias | Cierre la pestana, borre el almacenamiento de sesion del sitio y reintente |
| `AADSTS50105` (usuario no asignado) | *Asignacion requerida* esta en **Si** | Pongala en **No** o asigne al usuario |
