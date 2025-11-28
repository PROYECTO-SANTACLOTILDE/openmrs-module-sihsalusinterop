# Manual de Uso - Módulo de Interoperabilidad SIH.SALUS

## Hospital Santa Clotilde

---

## 1. Instalación Rápida

### Requisitos Previos
- OpenMRS Core 1.11.6 o superior ejecutándose
- Java JDK 8
- Maven 3.x
- Conexión a servidor HAPI FHIR (RENHICE)

### Pasos de Instalación

1. **Compilar el módulo**:
```bash
cd openmrs-module-sihsalusinterop
mvn clean install
```

2. **Instalar en OpenMRS**:
   - Vía UI: Administración → Gestionar Módulos → Agregar o Actualizar Módulo
   - Seleccionar `omod/target/sihsalusinterop-1.0.0.omod`

3. **Verificar instalación**:
   - El módulo debe aparecer como "Iniciado" en la lista de módulos
   - No debe haber errores en los logs

---

## 2. Configuración Inicial

### Propiedades Globales

Ir a: **Administración → Configuración → Propiedades Globales**

Configurar las siguientes propiedades:

| Propiedad | Valor de Prueba | Valor de Producción |
|-----------|-----------------|---------------------|
| `sihsalusinterop.renhice.endpoint` | `http://hapi-fhir-server:8080/fhir` | `https://renhice.minsa.gob.pe/fhir` |
| `sihsalusinterop.renhice.enabled` | `true` | `true` |
| `sihsalusinterop.queue.maxRetries` | `5` | `10` |
| `sihsalusinterop.queue.retryInterval` | `300000` (5 min) | `600000` (10 min) |

### Privilegios de Usuario

Asignar privilegios según el rol:

- **Administradores**: `Manage Interop Queue`, `View Interop Queue`
- **Médicos/Enfermeras**: `Send FHIR Messages`
- **Personal IT**: `View Interop Queue`

---

## 3. Uso Diario

### 3.1. Envío Automático a RENHICE

El envío es **completamente automático**. No requiere acción del personal de salud.

**Flujo**:
1. El médico registra la atención del paciente en OpenMRS
2. Al guardar el Encounter, el sistema automáticamente:
   - Construye un Bundle FHIR con todos los datos clínicos
   - Lo encola para envío
   - Lo envía cada 5 minutos (o manualmente)

El paciente **debe tener DNI** registrado para que se envíe a RENHICE.

### 3.2. Consultar Paciente desde RENHICE

#### Vía API REST:

```bash
curl http://localhost/openmrs/ws/rest/v1/interop/patient/12345678
```

#### Vía Frontend (Futuro):

En la página del paciente habrá un botón "Consultar RENHICE" que mostrará su historial nacional.

### 3.3. Importar Datos desde RENHICE

Para actualizar la HCE local con información de RENHICE:

```bash
curl -X POST http://localhost/openmrs/ws/rest/v1/interop/patient/import/12345678 \
  -u admin:Admin123
```

Esto importará:
- Datos demográficos actualizados
- Diagnósticos de otras instituciones
- Signos vitales registrados externamente

### 3.4. Monitorear Cola de Interoperabilidad

Acceder a: **Administración → Interoperabilidad SIH.SALUS → Monitoreo de Interoperabilidad**

La interfaz permite:
- Ver estado de mensajes (Pendientes, Enviados, Con Error)
- Reintentar mensajes fallidos manualmente
- Inspeccionar payload FHIR completo
- Procesar cola manualmente

---

## 4. API REST Disponible

Base URL: `http://localhost/openmrs/ws/rest/v1/interop`

| Endpoint | Método | Descripción |
|----------|--------|-------------|
| `/status` | GET | Estado del módulo |
| `/queue` | GET | Ver cola de mensajes |
| `/processQueue` | POST | Procesar cola manualmente |
| `/queue/{id}/retry` | POST | Reintentar mensaje |
| `/patient/{dni}` | GET | Consultar paciente en RENHICE |
| `/patient/import/{dni}` | POST | Importar paciente desde RENHICE |

---

## 5. Solución de Problemas

### Problema: Mensajes quedan en estado ERROR

**Causas posibles**:
- Servidor RENHICE no disponible
- Error de red
- Datos inválidos

**Solución**:
1. Ver mensaje de error en la interfaz de monitoreo
2. Si es error de red, los reintentos automáticos lo resolverán
3. Si es error de datos, revisar logs: `docker logs peruHCE-backend`
4. Reintentar manualmente desde la interfaz

### Problema: "Failed to retrieve server metadata"

**Causa**: No puede conectarse al servidor FHIR

**Solución**:
1. Verificar que el servidor HAPI FHIR esté ejecutándose
2. Verificar la propiedad `sihsalusinterop.renhice.endpoint`
3. Si usan Docker, usar nombre de contenedor (no `localhost`)

### Problema: Mensaje "Paciente no encontrado en RENHICE"

**Causa**: El paciente con ese DNI no existe en RENHICE

**Solución**: Es normal. Significa que el paciente aún no ha sido registrado en el sistema nacional.

---

## 6. Comandos Útiles

### Verificar estado del módulo:
```powershell
Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/status"
```

### Ver cola de mensajes:
```powershell
Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/queue"
```

### Procesar cola manualmente:
```powershell
Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/processQueue" -Method Post
```

### Ver logs de OpenMRS:
```powershell
docker logs peruHCE-backend --tail 100 | Select-String "sihsalus|Dyaku"
```

---

## 7. Mantenimiento

### Backup de Cola de Mensajes

La cola se guarda en la tabla `sihsalus_interop_queue`. Incluirla en el backup diario de MySQL:

```bash
mysqldump -h localhost -P 3307 -u openmrs -p openmrs sihsalus_interop_queue > backup_cola.sql
```

### Limpieza de Mensajes Antiguos

Los mensajes enviados exitosamente pueden eliminarse después de 30 días para liberar espacio:

```sql
DELETE FROM sihsalus_interop_queue 
WHERE status = 'SENT' AND sent_at < DATE_SUB(NOW(), INTERVAL 30 DAY);
```

---

## 8. Contacto y Soporte

- **Soporte Técnico**: Hospital Santa Clotilde - IT Department
- **Documentación Completa**: Ver carpeta `docs/`
- **Problemas o Bugs**: Reportar a equipo de desarrollo

---

**Versión**: 1.0.0  
**Última Actualización**: Noviembre 2025

