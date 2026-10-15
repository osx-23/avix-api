# Seguridad de AVIX API

## Arquitectura implantada

- Spring Security (starter oficial) configura un SecurityFilterChain **deny-by-default**.
- La API no crea sesiones HTTP de servidor: SessionCreationPolicy.STATELESS, sin Basic, formulario ni logout de Spring Security.
- AvixBearerFilter valida Bearer de AVIX contra el hash SHA-256 de la tabla sesiones, revocación y expiración; el principal y las autoridades se cargan al SecurityContext.
- La verificación de sesión se reutiliza por los controladores: siguen aplicando las reglas de plaza, propietario y edición existentes.
- /api/v1/status, /api/v1/auth/web, /api/v1/auth/movil y el bootstrap protegido por secreto son los únicos endpoints públicos de la aplicación (y OPTIONS para preflight).
- /api/v1/admin/** requiere **simultáneamente** ROLE_SUPERVISOR y CLIENT_WEB.
- POST /api/v1/registros requiere CLIENT_MOVIL; PATCH /api/v1/registros/** requiere CLIENT_WEB.
- Las rutas restantes /api/v1/** exigen una sesión validada; cualquier otra ruta no prevista se deniega.
- Los errores 401/403 son JSON consistente; las respuestas de autenticación no se almacenan en caché.
- Las contraseñas web continúan con BCrypt coste 12; no se introducen JWT porque los tokens opacos criptográficamente aleatorios permiten revocación inmediata.
- No se modifican tablas, funciones SQL o la firma pública de los endpoints de Angular y Android.

## Advertencia pendiente: acceso Android

El acceso actual a Android por **código únicamente** sigue siendo vulnerable a suplantación y no debe utilizarse en una API públicamente expuesta como mecanismo definitivo. La protección de endpoints y los tokens no corrigen este problema de identidad.

Para permitir una transición operativa se añadió AVIX_MOBILE_CODE_LOGIN_ENABLED=true por defecto. En una instancia pública que aún no deba aceptar login móvil, establecer AVIX_MOBILE_CODE_LOGIN_ENABLED=false para denegar la creación de nuevas sesiones móviles de código único. Este flag **no invalida** las sesiones móviles existentes: para ello revocar dichas sesiones en base de datos y esperar el vencimiento de tokens anteriores.

Antes de producción es obligatorio implementar y probar un segundo factor o secreto por agente/dispositivo en la app Android y backend, sin almacenar credenciales maestras ni secretos de servicio dentro del APK.

## Configuración y operación

- Mantener SUPABASE_SECRET_KEY, AVIX_SETUP_SECRET y credenciales de despliegue fuera del repositorio.
- Eliminar AVIX_SETUP_SECRET después de crear el supervisor inicial; el endpoint de bootstrap es protegido además por un secreto fuerte y bloqueo cuando ya existe supervisor.
- Configurar AVIX_CORS_ORIGINS exclusivamente con el dominio de AVIX Web.
- Servir API y Web solo por HTTPS y endurecer proxy/WAF y limitación distribuida de intentos; el throttle actual opera en memoria, no entre instancias.
- No habilitar logging de credenciales ni del Authorization bearer.
- Revisar restricciones RLS/GRANT de Supabase, rotación de secreto y permisos mínimos para la clave de servicio. El servidor usa clave privada con acceso amplio.
- Implementar CSP para el frontend y gestión de sesiones segura frente a XSS.
- Ejecutar mvn verify y pruebas de integración antes de implementar en producción.

## Estrategia de migración

1. Validar en develop los nuevos permisos y que Android y Angular sigan autenticándose con sus tokens.
2. Definir solución de identidad móvil, implementar cambios coordinados en Android y el API y crear pruebas de suplantación.
3. Probar con una instancia de staging y tokens existentes; verificar logs y CORS.
4. Pasar al main únicamente después de aprobar los escenarios.
