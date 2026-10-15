# AVIX API

API independiente de SIGO para **AVIX Android y AVIX Web**.

- Java 17 + Spring Boot 3.5.16
- Supabase PostgreSQL proyecto **avix** de la cuenta **osx**: `iccjcqausgunnekbiswy`
- Rama de trabajo: `develop`
- `main` queda como rama de referencia hasta que la API esté probada

## Estructura

```text
src/main/java/com/avix/api/
  AvixApiApplication.java
  config/AvixCors.java
  data/SupabaseData.java
  security/AvixAuth.java
  service/RegistroService.java
  web/AuthController.java
  web/AdminController.java
  web/CatalogController.java
  web/RegistroController.java
  web/StatusController.java
  web/ApiExceptionHandler.java
```

## Configuración local o en Railway

Configura variables de entorno **en el servidor** (no las subas al repositorio):

```dotenv
SUPABASE_URL=https://iccjcqausgunnekbiswy.supabase.co
SUPABASE_SECRET_KEY=sb_secret_TU_CLAVE_REAL
AVIX_SETUP_SECRET=SECRETO_ALEATORIO_DE_32_O_MAS_CARACTERES
AVIX_CORS_ORIGINS=https://tu-frontend.vercel.app
PORT=8080
```

La clave `sb_secret_` se obtiene en Supabase > Project Settings > API Keys. **Nunca debe ir en Angular o Kotlin**; sirve para operaciones con privilegios elevados y omite RLS. El backend comprueba roles y plazas asignadas.

```bash
mvn test
mvn spring-boot:run
```

También incluye Dockerfile multi-stage para Railway.

## Primera configuración

1. Las tablas de Supabase ya existen; P1-P10, acciones y turnos están cargados. Falta registrar vías reales por plaza.
2. Configura las variables de entorno.
3. Crea el supervisor inicial una sola vez:
   ```http
   POST /api/v1/setup/supervisor
   X-Setup-Secret: <AVIX_SETUP_SECRET>
   Content-Type: application/json

   {"codigo":"SUP001","contrasena":"TU_CLAVE_WEB_SEGURA"}
   ```
   Tras crear el supervisor, **elimina AVIX_SETUP_SECRET del despliegue y reinicia**.
4. Inicia sesión en web como supervisor y registra los usuarios, asignaciones y vías.

## Endpoints

| Método | Ruta | Acceso |
|---|---|---|
| GET | `/api/v1/status` | Público (solo estado) |
| POST | `/api/v1/auth/movil` | Código |
| POST | `/api/v1/auth/web` | Código + contraseña |
| GET | `/api/v1/auth/yo` | Bearer |
| POST | `/api/v1/auth/salir` | Bearer |
| POST | `/api/v1/setup/supervisor` | Secreto inicial |
| POST | `/api/v1/admin/usuarios` | Supervisor web |
| POST | `/api/v1/admin/usuarios/{id}/plazas` | Supervisor web |
| POST | `/api/v1/admin/vias` | Supervisor web |
| GET | `/api/v1/catalogos/plazas` | Bearer |
| GET | `/api/v1/catalogos/vias?plaza=P1` | Bearer |
| GET | `/api/v1/catalogos/acciones` | Bearer |
| GET | `/api/v1/catalogos/turnos` | Bearer |
| POST | `/api/v1/registros` | Sesión móvil |
| GET | `/api/v1/registros?page=0&size=20&plaza=P1&accion=FUGA&fecha=2026-10-14` | Bearer; agentes solo ven los suyos |

**Android:** `POST /api/v1/auth/movil` con `{"codigo":"AG001"}`. AGENTE, CONTROLADOR y SUPERVISOR pueden entrar, según estado de la cuenta.

**Web:** `POST /api/v1/auth/web` con `{"codigo":"CTRL001","contrasena":"..."}`. Solo CONTROLADOR y SUPERVISOR, con contraseña almacenada como hash BCrypt. El agente no tiene contraseña.

**Crear usuario:** `POST /api/v1/admin/usuarios` con `{"codigo":"AG001","nombres":"Agente","apellidos":"","rol":"AGENTE"}`, o con `"rol":"CONTROLADOR","contrasena":"..."`. Se requiere token web del supervisor.

**Configurar vía:** `POST /api/v1/admin/vias` con `{"plaza":"P1","numero":151}`.

**Asignar plaza:** `POST /api/v1/admin/usuarios/{id}/plazas` con `{"plaza":"P1"}`.

**Crear registro:**

```json
{
  "idempotenciaId": "9e7060a9-bc19-4f2b-ae85-4d193efffe61",
  "plaza": "P1",
  "via": 151,
  "accion": "FUGA",
  "placa": "X1Z505",
  "ocurridoEn": "2026-10-14T18:30:00-05:00",
  "origen": "VOZ"
}
```

El servidor identifica al usuario mediante el Bearer token, valida plaza asignada y vía en catálogo, y calcula turno según **America/Lima** (A 06:00-14:00, B 14:00-22:00, C 22:00-06:00). Hasta las 05:59 del turno C la fecha operativa es la del día anterior. El `idempotenciaId` permite reintentos desde la cola offline sin insertar registros duplicados.

## Alcance y seguridad

- **Sin confianza de dispositivo**: Android solo con código es el requerimiento actual; no es una autenticación fuerte. Cualquiera que conozca un código válido podría suplantarlo.
- Límite básico de 5 intentos por IP+código+tipo en 15 minutos, solo en memoria; para producción multinstancia se necesita Redis o limitación a nivel de proxy.
- Sesiones opacas aleatorias, guardadas solo como SHA-256; caducidad 12 h Android y 8 h Web. Permite revocación.
- Todas las tablas tienen RLS habilitado y se accede con clave secreta **solo en el servidor**.
- No hay integración con SIGO; son proyectos distintos.
- Pendiente: edición/anulación con auditoría atómica; control global contra enumeración de códigos; métricas, despliegue y pruebas de integración reales; migración del parser de voz de la app Android si se necesita en el backend.
