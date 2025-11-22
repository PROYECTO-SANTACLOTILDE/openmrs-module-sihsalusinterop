# SIH SALUS Interoperability Module - Guía Completa

## Descripción

Módulo de Interoperabilidad para OpenMRS desarrollado para el Hospital Santa Clotilde (Loreto, Perú). Implementa conectividad FHIR R4 con los sistemas nacionales de salud del Perú (RENHICE, SETI-SIS).

El módulo implementa una arquitectura **Offline-First** con cola de mensajes persistente, diseñada para entornos con conectividad intermitente.

## Arquitectura

### Componentes Principales

- **API Module**: Lógica de negocio, servicios, DAOs y modelos
- **OMOD Module**: Controladores REST y componentes web
- **Cola Persistente**: Mensajes almacenados en base de datos para reintentos
- **Mapeo FHIR**: Conversión de recursos OpenMRS a FHIR R4 con perfiles MINSA

### Tecnologías

- OpenMRS 2.6.x
- HAPI FHIR 5.7.0 (Cliente FHIR R4)
- Spring Framework (IoC, Transactions)
- Hibernate (Persistencia mediante XML mapping)
- Liquibase (Migraciones de BD)

## Instalación y Configuración

### Compilación

```bash
mvn clean package -DskipTests
```

El archivo `.omod` se genera en `omod/target/sihsalusinterop-1.0.0.omod`

### Instalación en Docker

```bash
# Copiar el módulo al contenedor
docker cp omod/target/sihsalusinterop-1.0.0.omod peruHCE-backend:/openmrs/data/modules/

# Limpiar caché si es necesario
docker exec peruHCE-backend rm -rf /openmrs/data/.openmrs-lib-cache/sihsalusinterop

# Reiniciar el contenedor
docker restart peruHCE-backend
```

### Verificación de Instalación

Verificar que el módulo se haya cargado correctamente:

```bash
docker logs peruHCE-backend 2>&1 | Select-String "sihsalusinterop" | Select-Object -Last 10
```

## Endpoints REST

Base URL: `http://localhost:8080/openmrs/ws/rest/v1/interop`

### Health Check / Status

**GET** `/status`

Verifica que el módulo esté funcionando y muestra estadísticas de la cola.

```bash
curl http://localhost:8080/openmrs/ws/rest/v1/interop/status
```

Respuesta esperada:
```json
{
  "success": true,
  "module": "SIH SALUS Interoperability Module",
  "version": "1.0.0",
  "queue": {
    "pending": 0,
    "sent": 0,
    "error": 0,
    "failed": 0
  }
}
```

### Encolar Mensaje

**POST** `/send`

Encola un mensaje FHIR para envío asíncrono.

```bash
curl -X POST http://localhost:8080/openmrs/ws/rest/v1/interop/send \
  -H "Content-Type: application/json" \
  -d '{
    "messageType": "FHIR_BUNDLE",
    "payload": "{\"resourceType\":\"Bundle\",\"type\":\"transaction\"}",
    "targetEndpoint": "http://localhost:8080/fhir"
  }'
```

### Procesar Cola

**POST** `/processQueue`

Procesa manualmente todos los mensajes pendientes en la cola.

```bash
curl -X POST http://localhost:8080/openmrs/ws/rest/v1/interop/processQueue
```

### Consultar Cola

**GET** `/queue`

Obtiene todos los items de la cola.

**GET** `/queue/{id}`

Obtiene un item específico por ID.

**POST** `/queue/{id}/retry`

Reintenta enviar un item específico.

**DELETE** `/queue/{id}`

Elimina un item de la cola.

## Base de Datos

### Tabla: sihsalus_interop_queue

La tabla se crea automáticamente mediante Liquibase al iniciar el módulo.

Campos principales:
- `queue_id`: ID único del item
- `message_type`: Tipo de mensaje (FHIR_BUNDLE, FUA_DOCUMENT)
- `payload`: JSON/XML del mensaje
- `status`: Estado (PENDING, PROCESSING, SENT, ERROR, FAILED)
- `attempts`: Número de intentos realizados
- `max_attempts`: Máximo de reintentos (default: 5)
- `queued_at`: Fecha de encolamiento
- `sent_at`: Fecha de envío exitoso
- `target_endpoint`: URL del servidor FHIR destino

## Problema con FHIR2 y Solución Aplicada

### Problema

El módulo FHIR2 de OpenMRS no se inicia correctamente y requiere un bean `fhirR4` que no está disponible. Esto causaba errores de `NoSuchBeanDefinitionException` durante el inicio de OpenMRS.

### Solución Aplicada

1. **Bean fhirR4 definido**: Se agregó la definición del bean `fhirR4` en `moduleApplicationContext.xml` y `webModuleApplicationContext.xml`:

```xml
<bean id="fhirR4" class="ca.uhn.fhir.context.FhirContext" factory-method="forR4" scope="singleton"/>
```

2. **FHIR2 deshabilitado temporalmente**: Como FHIR2 tiene problemas de configuración más profundos, se deshabilitó temporalmente removiendo los módulos del contenedor:

```bash
docker exec peruHCE-backend rm /openmrs/data/modules/fhir2-2.2.0.omod
docker exec peruHCE-backend rm /openmrs/data/modules/referencedemodata-2.4.0.omod
docker exec peruHCE-backend rm -rf /openmrs/data/.openmrs-lib-cache/fhir2
docker exec peruHCE-backend rm -rf /openmrs/data/.openmrs-lib-cache/referencedemodata
docker restart peruHCE-backend
```

**Nota**: Si necesitas FHIR2 más adelante, primero debes resolver sus problemas de configuración antes de reactivarlo.

### Correcciones de Configuración Realizadas

1. **Mapeo Hibernate**: Eliminadas anotaciones JPA de `InteropQueueItem.java` para usar solo mapeo XML Hibernate, resolviendo el error de `PropertyNotFoundException: retired`

2. **Liquibase**: Movido `liquibase.xml` de `api/src/main/resources/` a `omod/src/main/resources/` para evitar duplicación de archivos

3. **Spring Beans**: Removido `parent="serviceContext"` del bean `sihsalusinterop.DyakuSenderService` para permitir instanciación directa

## Pruebas con Postman

### Prueba 1: Verificar que el Módulo Esté Activo

**Método:** GET  
**URL:** `http://localhost:8080/openmrs/ws/rest/v1/interop/status`

**Resultado esperado:**
```json
{
    "success": true,
    "module": "SIH SALUS Interoperability Module",
    "version": "1.0.0",
    "queue": {
        "failed": 0,
        "pending": 0,
        "sent": 0,
        "error": 0
    }
}
```

**Qué verifica:** El módulo se cargó correctamente, los beans de Spring están configurados, y la base de datos tiene la tabla creada.

### Prueba 2: Encolar un Mensaje FHIR

**Método:** POST  
**URL:** `http://localhost:8080/openmrs/ws/rest/v1/interop/send`  
**Headers:** `Content-Type: application/json`  
**Body (JSON):**
```json
{
    "messageType": "FHIR_BUNDLE",
    "payload": "{\"resourceType\":\"Bundle\",\"type\":\"transaction\",\"entry\":[]}",
    "targetEndpoint": "http://localhost:8080/fhir"
}
```

**Resultado esperado:**
```json
{
    "queueId": 1,
    "success": true,
    "message": "Mensaje encolado exitosamente. Será enviado en el próximo ciclo de procesamiento.",
    "status": "PENDING"
}
```

**Qué verifica:** La persistencia funciona (el mensaje se guardó en la base de datos), el servicio procesó el request, y el DAO guardó el item.

### Prueba 3: Consultar la Cola

**Método:** GET  
**URL:** `http://localhost:8080/openmrs/ws/rest/v1/interop/queue`

**Resultado esperado:**
```json
{
    "success": true,
    "count": 1,
    "items": [
        {
            "queueId": 1,
            "messageType": "FHIR_BUNDLE",
            "status": "PENDING",
            "attempts": 0,
            "maxAttempts": 5,
            "queuedAt": "2025-11-21T16:30:00",
            "lastAttemptAt": null,
            "sentAt": null,
            "errorMessage": null,
            "targetEndpoint": "http://localhost:8080/fhir",
            "externalResourceId": null,
            "uuid": "65f48dc2-c2da-41e7-9edd-610f5029f2ba"
        }
    ]
}
```

**Nota:** La respuesta ahora usa DTOs para evitar recursión circular con los objetos de OpenMRS (creator, person, etc.). Las respuestas son limpias y no tienen recursión infinita.

**Qué verifica:** El DAO puede leer de la base de datos, el mapeo Hibernate funciona, y los endpoints REST responden correctamente sin recursión infinita.

### Prueba 4: Procesar la Cola Manualmente

**Método:** POST  
**URL:** `http://localhost:8080/openmrs/ws/rest/v1/interop/processQueue`

**Resultado esperado (sin servidor FHIR):**
```json
{
    "success": true,
    "message": "Se procesaron 0 mensajes exitosamente",
    "sentCount": 0
}
```

**Qué significa:** Si devuelve `sentCount: 0`, significa que:
- No hay servidor FHIR disponible en el `targetEndpoint` especificado
- El item cambió a estado `ERROR` (se puede verificar en Prueba 5)
- Es normal si no has levantado un servidor HAPI FHIR local

**Para probar con servidor FHIR:** 

1. Levantar servidor HAPI FHIR:
```bash
docker run -p 8081:8080 --name hapi-fhir-server hapiproject/hapi:latest
```

2. **IMPORTANTE:** Conectarlo a la misma red Docker que OpenMRS:
```bash
docker network connect sihsalus-distro-referenceapplication_default hapi-fhir-server
docker restart hapi-fhir-server
```

3. Usar el nombre del contenedor en el `targetEndpoint` (NO usar localhost):
```json
{
    "targetEndpoint": "http://hapi-fhir-server:8080/fhir"
}
```

**Nota:** Los contenedores Docker no pueden comunicarse usando `localhost`. Deben usar el nombre del contenedor. El puerto 8080 es el interno del contenedor, no el 8081 mapeado al host.

**Qué verifica:** El procesador de cola funciona, el cliente HAPI FHIR se conecta al servidor, y el estado se actualiza después del intento.

### Prueba 5: Verificar el Estado Final del Item

**Método:** GET  
**URL:** `http://localhost:8080/openmrs/ws/rest/v1/interop/queue`

**Resultado esperado (sin servidor FHIR):**
```json
{
    "success": true,
    "count": 1,
    "items": [
        {
            "queueId": 1,
            "status": "ERROR",
            "attempts": 1,
            "errorMessage": "Connection refused...",
            ...
        }
    ]
}
```

**Resultado esperado (con servidor FHIR funcionando):**
```json
{
    "items": [
        {
            "queueId": 5,
            "status": "SENT",
            "sentAt": 1763786839000,
            "targetEndpoint": "http://hapi-fhir-server:8080/fhir",
            "externalResourceId": "http://hapi-fhir-server:8080/fhir/Bundle/82a2ac0c-97f3-412f-8ef4-2a967500a809",
            ...
        }
    ]
}
```

**Nota:** El campo `externalResourceId` contiene la URL del recurso creado en el servidor FHIR. Esto confirma que el mensaje fue procesado exitosamente.

**Qué verifica:** La actualización de estados funciona, los timestamps se registran, y los errores se capturan correctamente.

## Corrección de Recursión Circular en Respuestas JSON

### Problema Resuelto

El endpoint `/queue` devolvía respuestas con recursión infinita debido a las referencias circulares de OpenMRS (`creator -> person -> creator -> person...`).

### Solución Implementada

Se creó un **DTO (Data Transfer Object)** `InteropQueueItemDTO` que solo contiene los campos necesarios para la API, sin las referencias circulares de OpenMRS. El controlador ahora convierte automáticamente los objetos `InteropQueueItem` a DTOs antes de devolverlos.

**Archivos modificados:**
- `api/src/main/java/.../dto/InteropQueueItemDTO.java` (nuevo)
- `omod/src/main/java/.../controller/DyakuSubmissionController.java` (actualizado)

Las respuestas del endpoint `/queue` ahora son limpias y no tienen recursión infinita.

## Troubleshooting

### Error: Módulo no inicia

Verificar logs del contenedor:

```bash
docker logs peruHCE-backend 2>&1 | Select-String "sihsalusinterop|ERROR" | Select-Object -Last 30
```

Verificar que el módulo esté en el directorio correcto:

```bash
docker exec peruHCE-backend ls -la /openmrs/data/modules/ | Select-String "sihsalusinterop"
```

### Error: 404 Not Found en endpoints

El módulo puede no haberse cargado. Verificar en los logs que no haya errores durante el inicio. Limpiar caché y reiniciar:

```bash
docker exec peruHCE-backend rm -rf /openmrs/data/.openmrs-lib-cache/sihsalusinterop
docker restart peruHCE-backend
```

### Error: NoSuchBeanDefinitionException

Si aparece un error de bean no encontrado, verificar que el archivo `moduleApplicationContext.xml` tenga la definición correcta de los beans. El problema de `fhirR4` ya está resuelto agregando el bean manualmente.

### Error: Liquibase duplicado

Si aparece error de `liquibase.xml` duplicado, verificar que el archivo esté solo en `omod/src/main/resources/liquibase.xml` y no en `api/src/main/resources/`.

### Respuesta JSON con recursión infinita

Este problema ya está resuelto. Si aún aparece, recompila el módulo con las últimas correcciones:

```bash
mvn clean package -DskipTests
docker cp omod/target/sihsalusinterop-1.0.0.omod peruHCE-backend:/openmrs/data/modules/
docker exec peruHCE-backend rm -rf /openmrs/data/.openmrs-lib-cache/sihsalusinterop
docker restart peruHCE-backend
```

### processQueue retorna sentCount: 0

Esto es normal si:
- No hay un servidor FHIR levantado en el `targetEndpoint` especificado
- Los items quedan en estado `ERROR` (se pueden reintentar después)
- **IMPORTANTE:** Los contenedores Docker no pueden comunicarse usando `localhost`

**Configuración correcta del servidor HAPI FHIR:**

1. Levantar servidor HAPI FHIR:
```bash
docker run -p 8081:8080 --name hapi-fhir-server hapiproject/hapi:latest
```

2. Conectarlo a la misma red Docker que OpenMRS:
```bash
docker network connect sihsalus-distro-referenceapplication_default hapi-fhir-server
docker restart hapi-fhir-server
```

3. **Usar el nombre del contenedor** en el `targetEndpoint` (NO usar localhost):
```json
{
    "targetEndpoint": "http://hapi-fhir-server:8080/fhir"
}
```

**Nota:** El puerto 8080 es el interno del contenedor. El puerto 8081 es solo el mapeo al host para acceder desde tu máquina. Entre contenedores, usar siempre el nombre del contenedor.

## Consultas SQL Útiles

Ver items en cola:
```sql
SELECT queue_id, message_type, status, attempts, queued_at, target_endpoint
FROM sihsalus_interop_queue
ORDER BY queued_at DESC
LIMIT 20;
```

Contar por estado:
```sql
SELECT status, COUNT(*) as cantidad
FROM sihsalus_interop_queue
GROUP BY status;
```

Ver errores recientes:
```sql
SELECT queue_id, message_type, error_message, attempts, max_attempts, last_attempt_at
FROM sihsalus_interop_queue
WHERE status IN ('ERROR', 'FAILED')
ORDER BY last_attempt_at DESC
LIMIT 10;
```

## Próximos Pasos

1. **Scheduler Automático**: Configurar un scheduler para procesar la cola automáticamente cada X minutos
2. **Mappers Adicionales**: Implementar conversores para otros recursos (Encounter, Observation, etc.)
3. **Listener de Eventos**: Implementar listeners para encolar automáticamente cuando se crean/actualizan pacientes
4. **Interfaz de Monitoreo**: Crear una página web para visualizar el estado de la cola

---

**Autor**: Johan Amador - SIH.SALUS Fase 2  
**Fecha**: Noviembre 2025  
**Versión del Módulo**: 1.0.0
