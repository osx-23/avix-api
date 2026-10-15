# AVIX API — funcionalidades completadas en develop

- Backend independiente de SIGO, Java 17 y Spring Boot.
- Seguridad: móvil con código (sin contraseña); web solo CONTROLADOR o SUPERVISOR con contraseña BCrypt.
- Gestión de cuentas: creación, consulta, edición de datos, cambio de contraseña, cambio de rol, desactivación y revocación de sesiones.
- Gestión de plazas asignadas: añadir, listar, activar o desactivar.
- Gestión de vías: crear, consultar (incluidas inactivas), editar y activar/desactivar.
- Registros: guardar con UUID idempotente, listar con filtros y paginación, detalle, edición, anulación lógica, consulta de auditoría.
- Permisos: AGENTE únicamente sus propios registros; CONTROLADOR limitado a plazas activamente asignadas; SUPERVISOR puede administrar el sistema.
- Auditoría en Supabase: trigger para CREACION; funciones transaccionales para EDICION y ANULACION; registra actor, motivo y valores anteriores/nuevos.
- Turno y fecha operativa según hora de Lima, con turno C de 22:00 a 06:00.
- Para modificar cuentas de manera atómica, función avix_administrar_usuario.

## Rutas añadidas

GET /api/v1/registros/{uuid}

PATCH /api/v1/registros/{uuid}
Ejemplo: {"cambios":{"placa":"ABC123","accion":"LIBERADO"},"motivo":"Corrección operativa"}

PATCH /api/v1/registros/{uuid}/anular
Ejemplo: {"motivo":"Registro duplicado"}

GET /api/v1/registros/{uuid}/auditoria

GET /api/v1/admin/usuarios?page=0&size=20
GET /api/v1/admin/usuarios/{uuid}
PATCH /api/v1/admin/usuarios/{uuid}
Ejemplo: {"activo":false,"contrasena":"una_contrasena_nueva"}

GET /api/v1/admin/usuarios/{uuid}/plazas
PATCH /api/v1/admin/usuarios/{uuid}/plazas/P1
Ejemplo: {"activa":false}

GET /api/v1/admin/vias?plaza=P1
PATCH /api/v1/admin/vias/{id}
Ejemplo: {"numero":151,"activa":false,"descripcion":"Vía de salida"}

## Configuración y pruebas

Configurar en Railway (NO GitHub, Android o Angular):
SUPABASE_URL=https://iccjcqausgunnekbiswy.supabase.co
SUPABASE_SECRET_KEY=sb_secret_TU_CLAVE_PRIVADA
AVIX_SETUP_SECRET=SECRETO_ALEATORIO_DE_32_O_MAS_CARACTERES
AVIX_CORS_ORIGINS=https://tu-avix-front.vercel.app

Después de crear el primer SUPERVISOR usando POST /api/v1/setup/supervisor, elimina AVIX_SETUP_SECRET de Railway.

Para pruebas localmente: mvn test
Para ejecutar: mvn spring-boot:run

Pendiente de configuración externa: claves reales, primer usuario SUPERVISOR, vías auténticas y asignaciones, despliegue Railway, integración Angular y Kotlin y prueba extremo-a-extremo contra datos reales.

Seguridad: el acceso móvil solamente con código no prueba identidad fuerte; la protección anti-fuerza bruta actual es por memoria de proceso y necesita Redis/WAF para infraestructura de múltiples instancias.
