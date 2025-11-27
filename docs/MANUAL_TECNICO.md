# Manual Técnico - Módulo de Interoperabilidad SIH.SALUS

## Hospital Santa Clotilde - Tesis de Grado

---

## Tabla de Contenidos

1. [Introducción](#1-introducción)
2. [Arquitectura del Sistema](#2-arquitectura-del-sistema)
3. [Instalación y Configuración](#3-instalación-y-configuración)
4. [Mapeo de Recursos FHIR](#4-mapeo-de-recursos-fhir)
5. [Flujos de Interoperabilidad](#5-flujos-de-interoperabilidad)
6. [API REST](#6-api-rest)
7. [Interfaz de Monitoreo](#7-interfaz-de-monitoreo)
8. [Pruebas y Validación](#8-pruebas-y-validación)
9. [Mantenimiento y Troubleshooting](#9-mantenimiento-y-troubleshooting)

---

## 1. Introducción

### 1.1. Propósito

El Módulo de Interoperabilidad SIH.SALUS permite la comunicación bidireccional entre el Sistema de Información Hospitalario (SIH) del Hospital Santa Clotilde y el Registro Nacional de Historias Clínicas Electrónicas (RENHICE) del MINSA, utilizando el estándar HL7 FHIR R4 con perfiles peruanos Dyaku.

### 1.2. Alcance

El módulo implementa:
- Envío de resúmenes clínicos a RENHICE (Bundles FHIR R4)
- Consulta de información de pacientes desde RENHICE
- Arquitectura offline-first con cola de mensajes y reintentos automáticos
- Mapeo completo de recursos según el International Patient Summary (IPS)
- Interfaz web para monitoreo de interoperabilidad

### 1.3. Requisitos Previos

- OpenMRS Core 1.11.6 o superior
- Java JDK 8
- Maven 3.x
- Conexión al servidor HAPI FHIR (RENHICE/Dyaku)
- Base de datos MySQL 5.7+

---

## 2. Arquitectura del Sistema

### 2.1. Componentes Principales

```
+-------------------+
|   OpenMRS UI      |
|   (O3 Frontend)   |
+--------+----------+
         |
         | HTTP/REST
         |
+--------v----------+
| OpenMRS Backend   |
| + SIH.SALUS       |
| Interop Module    |
+--------+----------+
         |
         | FHIR R4
         | over HTTPS
         |
+--------v----------+
|   RENHICE/Dyaku   |
|  (HAPI FHIR R4)   |
+-------------------+
```

### 2.2. Arquitectura Offline-First

```
Encounter guardado
       |
       v
[EventListener] ---> [BundleBuilder] ---> [Cola BD Local]
                                                 |
                                                 v
                                          [Scheduled Task]
                                                 |
                                                 v
                                            [Reintento]
                                                 |
                                             ¿Éxito?
                                           /         \
                                        Sí           No
                                       /               \
                                   [SENT]          [ERROR/RETRY]
```

### 2.3. Módulos y Paquetes

```
org.openmrs.module.sihsalusinterop
├── api
│   ├── mapper           # Mappers OpenMRS → FHIR
│   ├── model            # InteropQueueItem
│   ├── service          # BundleBuilderService, DyakuSenderService
│   ├── listener         # EncounterSavedListener
│   └── advice           # EncounterSavedAdvice (AOP)
└── omod
    ├── web.controller   # DyakuSubmissionController (REST API)
    ├── resources        # CSS, JS
    └── pages            # JSP (Interfaz de monitoreo)
```

---

## 3. Instalación y Configuración

### 3.1. Compilación del Módulo

```bash
cd openmrs-module-sihsalusinterop
mvn clean install
```

Resultado: `omod/target/sihsalusinterop-1.0.0.omod`

### 3.2. Instalación en OpenMRS

#### Opción A: Manual
1. Acceder a `http://localhost/openmrs/admin/modules/module.list`
2. Click en "Agregar o Actualizar Módulo"
3. Seleccionar `sihsalusinterop-1.0.0.omod`
4. Click en "Cargar"

#### Opción B: Docker
Copiar el OMOD en el directorio de módulos del contenedor:

```bash
docker cp sihsalusinterop-1.0.0.omod peruHCE-backend:/openmrs/data/modules/
docker restart peruHCE-backend
```

### 3.3. Configuración de Propiedades Globales

Acceder a: `http://localhost/openmrs/admin/maintenance/globalProps.list`

| Propiedad | Valor por Defecto | Descripción |
|-----------|-------------------|-------------|
| `sihsalusinterop.renhice.endpoint` | `http://hapi-fhir-server:8080/fhir` | URL del servidor FHIR de RENHICE |
| `sihsalusinterop.renhice.enabled` | `true` | Habilitar envío automático |
| `sihsalusinterop.queue.maxRetries` | `5` | Máximo de reintentos |
| `sihsalusinterop.queue.retryInterval` | `300000` | Intervalo de reintento (ms) |

**IMPORTANTE para PRODUCCIÓN**: Cambiar endpoint a HTTPS:
```
sihsalusinterop.renhice.endpoint = https://renhice.minsa.gob.pe/fhir
```

### 3.4. Privilegios de Usuario

Asignar privilegios necesarios a roles:
- **Administradores**: `Manage Interop Queue`, `View Interop Queue`
- **Médicos/Enfermeras**: `Send FHIR Messages`
- **Personal IT**: `View Interop Logs`, `View Interop Queue`

---

## 4. Mapeo de Recursos FHIR

### 4.1. Mappers Implementados

| Recurso FHIR | Mapper | Perfil Dyaku | Origen OpenMRS |
|--------------|--------|--------------|----------------|
| Patient | DyakuPatientMapper | PacientePe | org.openmrs.Patient |
| Organization | DyakuOrganizationMapper | OrganizacionPe | org.openmrs.Location |
| Practitioner | DyakuPractitionerMapper | PractitionerPe | org.openmrs.User |
| Encounter | DyakuEncounterMapper | EncounterPe | org.openmrs.Encounter |
| Condition | DyakuConditionMapper | ConditionPe | org.openmrs.Obs (Diagnosis) |
| AllergyIntolerance | DyakuAllergyIntoleranceMapper | AlergiaPe | org.openmrs.Allergy |
| MedicationStatement | DyakuMedicationStatementMapper | MedicationStatementPe | org.openmrs.DrugOrder |
| Procedure | DyakuProcedureMapper | ProcedurePe | org.openmrs.Order |
| Observation | DyakuObservationMapper | ObservationPe | org.openmrs.Obs |
| Immunization | DyakuImmunizationMapper | InmunizacionPe | org.openmrs.Obs (Vaccine) |

### 4.2. Extensiones Peruanas

- **pe-tercerapellido**: Tercer apellido del paciente
- **pe-ubigeo**: Código UBIGEO (INEI) para ubicación geográfica
- **pe-pais**: País emisor del documento de identidad

### 4.3. Sistemas de Codificación

- **CIE-10**: Diagnósticos (`http://hl7.org/fhir/sid/icd-10`)
- **CPMS**: Procedimientos médicos
- **DNI RENIEC**: `urn:oid:2.16.840.1.113883.4.904`
- **OpenMRS Concepts**: Descargados desde Open Concept Lab (OCL)

---

## 5. Flujos de Interoperabilidad

### 5.1. Flujo de Envío a RENHICE

1. **Evento**: Médico guarda un Encounter en OpenMRS O3
2. **Detección**: `EncounterSavedAdvice` intercepta el evento
3. **Procesamiento Asíncrono**: `EncounterSavedListener` ejecuta en thread separado
4. **Construcción de Bundle**: `BundleBuilderService` crea Bundle FHIR R4
5. **Encolado**: Mensaje guardado en tabla `sihsalus_interop_queue` (estado: PENDING)
6. **Envío**: Scheduled task procesa la cola cada 5 minutos
7. **Resultado**:
   - Éxito: Estado → SENT
   - Error: Estado → ERROR, se programa reintento

### 5.2. Flujo de Consulta desde RENHICE

1. **Solicitud**: Personal de salud busca paciente por DNI
2. **API**: Llamada GET a `/ws/rest/v1/interop/patient/{dni}`
3. **Búsqueda FHIR**: Cliente HAPI FHIR consulta RENHICE
4. **Respuesta**: JSON con datos del paciente (si existe)

### 5.3. Prevención de Duplicados

El módulo implementa **conditional creates** (ifNoneExist) para evitar duplicación de recursos:

- **Patient**: `identifier={system}|{dni}`
- **Organization**: `identifier={valor}`
- **Practitioner**: `identifier={system}|{dni}`

---

## 6. API REST

### 6.1. Base URL

```
http://localhost/openmrs/ws/rest/v1/interop
```

### 6.2. Endpoints Principales

#### 6.2.1. Estado del Módulo

**GET** `/status`

**Respuesta**:
```json
{
  "success": true,
  "module": "SIH SALUS Interoperability Module",
  "version": "1.0.0",
  "queue": {
    "pending": 5,
    "sent": 120,
    "error": 2,
    "failed": 0
  }
}
```

#### 6.2.2. Consultar Cola

**GET** `/queue`

**Respuesta**:
```json
{
  "success": true,
  "count": 7,
  "items": [
    {
      "queueId": 1,
      "uuid": "abc-123",
      "messageType": "FHIR_BUNDLE",
      "status": "PENDING",
      "attempts": 0,
      "maxAttempts": 5,
      "queuedAt": 1700000000000,
      "targetEndpoint": "http://hapi-fhir-server:8080/fhir"
    }
  ]
}
```

#### 6.2.3. Procesar Cola

**POST** `/processQueue`

**Respuesta**:
```json
{
  "success": true,
  "processedCount": 5,
  "sentCount": 5,
  "message": "Se procesaron 5 mensajes. 5 enviados exitosamente."
}
```

#### 6.2.4. Reintentar Mensaje

**POST** `/retry/{queueId}`

**Respuesta**:
```json
{
  "success": true,
  "message": "Mensaje reenviado exitosamente"
}
```

#### 6.2.5. Consultar Paciente

**GET** `/patient/{dni}`

**Parámetros Query**:
- `system` (opcional): Sistema de identificación (default: OID RENIEC)
- `endpoint` (opcional): Endpoint FHIR (default: configurado en global property)

**Respuesta**:
```json
{
  "success": true,
  "total": 1,
  "patients": [
    {
      "id": "145",
      "name": "José Fernández López",
      "birthDate": "1980-05-15",
      "gender": "male",
      "identifiers": [
        {
          "system": "urn:oid:2.16.840.1.113883.4.904",
          "value": "12345678"
        }
      ]
    }
  ]
}
```

---

## 7. Interfaz de Monitoreo

### 7.1. Acceso

URL: `http://localhost/openmrs/module/sihsalusinterop/monitoreoInteroperabilidad.form`

También accesible desde: **Administración → Interoperabilidad SIH.SALUS → Monitoreo de Interoperabilidad**

### 7.2. Funcionalidades

1. **Panel de Resumen**: Contadores de mensajes por estado
2. **Tabla de Cola**: Lista completa de mensajes con filtros
3. **Ver Detalles**: Inspeccionar payload FHIR completo
4. **Reintentar**: Reenviar mensajes fallidos manualmente
5. **Auto-refresh**: Actualización automática cada 30 segundos

### 7.3. Captura de Pantalla

La interfaz muestra:
- Tarjetas de estado (Pendientes, Enviados, Con Error, Fallidos)
- Botones de acción (Actualizar, Procesar Cola)
- Tabla con historial completo
- Modal de detalles con JSON formateado

---

## 8. Pruebas y Validación

### 8.1. Configuración de Ambiente de Pruebas

#### Verificar Contenedores Docker

```powershell
docker ps --format "{{.Names}}\t{{.Ports}}"
```

Resultado esperado:
```
hapi-fhir-jpaserver-start    0.0.0.0:8081->8080/tcp
peruHCE-backend              0.0.0.0:8080->8080/tcp
peruHCE-db-master            0.0.0.0:3307->3306/tcp
peruHCE-gateway              0.0.0.0:80->80/tcp, 0.0.0.0:443->443/tcp
```

#### Verificar Conectividad de Red

```powershell
docker network inspect sihsalus-distro-referenceapplication_default
```

Confirmar que `hapi-fhir-jpaserver-start` está en la misma red que `peruHCE-backend`.

### 8.2. Pruebas Funcionales

#### Prueba 1: Health Check

```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/status"
$response | ConvertTo-Json
```

**Resultado esperado**: success=true, módulo activo

#### Prueba 2: Consulta de Paciente

```powershell
$dni = "74176968"
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/patient/$dni"
$response | ConvertTo-Json -Depth 5
```

**Resultado esperado**: JSON con datos del paciente si existe en RENHICE

#### Prueba 3: Envío de Encounter

1. Acceder a `http://localhost/openmrs/`
2. Buscar paciente con DNI existente en RENHICE
3. Iniciar visita (Start Visit)
4. Registrar atención:
   - Agregar diagnóstico (ej: "Hipertensión")
   - Registrar signos vitales
   - Guardar encuentro
5. Verificar cola:

```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/queue"
$response | ConvertTo-Json -Depth 3
```

**Resultado esperado**: 1 mensaje PENDING en la cola

#### Prueba 4: Procesamiento de Cola

```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/processQueue" -Method Post
$response | ConvertTo-Json
```

**Resultado esperado**: success=true, sentCount=1

#### Prueba 5: Verificación en HAPI FHIR

```powershell
$response = Invoke-RestMethod -Uri "http://localhost:8081/fhir/Patient?identifier=74176968"
$response.total
```

**Resultado esperado**: total >= 1 (paciente existe)

### 8.3. Validación de Datos

#### Verificar Mapeo CIE-10

```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/terminology/check?type=CIE10"
$response.conceptsWithMapping
```

**Resultado esperado**: Número de conceptos con mapeo CIE-10 > 0

#### Verificar Estructura del Bundle

Revisar el payload del mensaje en la interfaz de monitoreo:
- Debe contener recursos: Patient, Organization, Practitioner, Encounter
- Todos los recursos deben tener meta.profile con perfiles Dyaku
- Códigos CIE-10 deben tener formato válido (ej: A00.1)

---

## 9. Mantenimiento y Troubleshooting

### 9.1. Problemas Comunes

#### Error: "Failed to retrieve server metadata"

**Causa**: No se puede conectar al servidor HAPI FHIR

**Solución**:
1. Verificar que el contenedor `hapi-fhir-jpaserver-start` esté corriendo
2. Verificar conectividad de red entre contenedores
3. Revisar propiedad global `sihsalusinterop.renhice.endpoint`
4. Usar nombre de contenedor, no `localhost`

#### Error: "LazyInitializationException"

**Causa**: Acceso a propiedades lazy-loaded fuera de sesión Hibernate

**Solución**: Ya corregido en `EncounterSavedListener` con `Context.openSession()`

#### Mensajes en Estado ERROR

**Causa**: Timeout, servidor HAPI FHIR caído, o error de validación

**Solución**:
1. Revisar `errorMessage` en la interfaz de monitoreo
2. Verificar logs de HAPI FHIR: `docker logs hapi-fhir-jpaserver-start`
3. Si es timeout, aumentar timeout en `DyakuSenderServiceImpl`
4. Reintentar mensaje manualmente desde interfaz

#### Duplicación de Pacientes

**Causa**: Identificador cambiado o no se usa ifNoneExist

**Solución**: Ya corregido con conditional creates en `BundleBuilderService`

### 9.2. Logs y Diagnóstico

#### Ver Logs de OpenMRS

```powershell
docker logs peruHCE-backend --tail 100 | Select-String "sihsalus|EVENT|ERROR"
```

#### Ver Logs de HAPI FHIR

```powershell
docker logs hapi-fhir-jpaserver-start --tail 50 | Select-String "POST|ERROR|Exception"
```

#### Habilitar DEBUG

Agregar en `log4j.xml` de OpenMRS:
```xml
<logger name="org.openmrs.module.sihsalusinterop">
    <level value="DEBUG"/>
</logger>
```

### 9.3. Backup y Recuperación

#### Backup de Cola de Mensajes

```sql
mysqldump -h localhost -P 3307 -u openmrs -p openmrs sihsalus_interop_queue > interop_queue_backup.sql
```

#### Restauración

```sql
mysql -h localhost -P 3307 -u openmrs -p openmrs < interop_queue_backup.sql
```

### 9.4. Actualización del Módulo

1. Compilar nueva versión del OMOD
2. Detener módulo desde OpenMRS Admin
3. Eliminar OMOD antiguo
4. Cargar OMOD nuevo
5. Iniciar módulo
6. Verificar que Liquibase ejecute cambios de BD si aplica

---

## Anexos

### A. Estructura de Base de Datos

**Tabla: sihsalus_interop_queue**

| Campo | Tipo | Descripción |
|-------|------|-------------|
| queue_id | INT | Clave primaria (auto-increment) |
| uuid | VARCHAR(38) | UUID único del mensaje |
| message_type | VARCHAR(50) | Tipo: FHIR_BUNDLE o FUA_DOCUMENT |
| payload | LONGTEXT | JSON/XML del mensaje |
| status | VARCHAR(20) | PENDING, PROCESSING, SENT, ERROR, FAILED |
| attempts | INT | Número de intentos realizados |
| max_attempts | INT | Máximo de reintentos permitidos |
| queued_at | DATETIME | Fecha/hora de creación |
| last_attempt_at | DATETIME | Fecha/hora del último intento |
| sent_at | DATETIME | Fecha/hora de envío exitoso |
| error_message | TEXT | Detalles del último error |
| target_endpoint | VARCHAR(255) | URL del servidor destino |
| external_resource_id | VARCHAR(255) | ID del recurso en sistema externo |
| creator | INT | Usuario que creó el mensaje (FK users) |
| date_created | DATETIME | Fecha de creación (auditoría) |
| changed_by | INT | Usuario que modificó (FK users) |
| date_changed | DATETIME | Fecha de última modificación |

### B. Referencias

- HL7 FHIR R4: https://hl7.org/fhir/R4/
- Dyaku MINSA: https://dyaku.minsa.gob.pe/fhir
- Open Concept Lab: https://openconceptlab.org/
- OpenMRS Developer Guide: https://guide.openmrs.org/

---

**Versión**: 1.0.0  
**Fecha**: Noviembre 2025  
**Autor**: Hospital Santa Clotilde - SIH.SALUS Team  
**Tesis de Grado**: Ingeniería Informática

