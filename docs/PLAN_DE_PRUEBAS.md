# Plan de Pruebas - Componente de Interoperabilidad SIH.SALUS

## Hospital Santa Clotilde - Tesis de Grado

---

## 1. Información General

### 1.1. Propósito

Este documento establece el plan formal de pruebas para el Componente de Interoperabilidad SIH.SALUS, que permite la comunicación bidireccional entre el Sistema de Información Hospitalario (OpenMRS) y el Registro Nacional de Historias Clínicas Electrónicas (RENHICE) del MINSA, utilizando el estándar HL7 FHIR R4 con perfiles Dyaku.

### 1.2. Alcance

El plan cubre las siguientes áreas:
- Pruebas funcionales de interoperabilidad
- Pruebas de integración con sistema externo (HAPI FHIR)
- Pruebas de resiliencia y manejo de errores
- Pruebas de conformidad con estándares FHIR R4
- Pruebas de seguridad básica

### 1.3. Criterios de Aceptación

Según los objetivos de la tesis:
- El componente debe alcanzar al menos un 90% de casos de prueba exitosos
- El componente debe superar las pruebas de integración con al menos un sistema externo simulado o real

### 1.4. Ambiente de Pruebas

- OpenMRS Core 1.11.6 (contenedor Docker: peruHCE-backend)
- Módulo SIH.SALUS Interoperability v1.0.0
- HAPI FHIR JPA Server (contenedor Docker: hapi-fhir-jpaserver-start)
- Base de datos MySQL 5.7
- Sistema Operativo: Windows 10/11
- Herramientas: PowerShell, Docker Desktop

---

## 2. Casos de Prueba

### CP-001: Verificación de Instalación del Módulo

**Objetivo**: Verificar que el módulo se instala correctamente en OpenMRS

**Precondiciones**:
- OpenMRS Core ejecutándose
- Archivo .omod compilado disponible

**Pasos**:
1. Acceder a Administración → Gestionar Módulos
2. Cargar el archivo sihsalusinterop-1.0.0.omod
3. Verificar que el módulo aparece en la lista de módulos instalados
4. Verificar que el estado del módulo es "Iniciado"

**Resultado Esperado**:
- El módulo aparece como "SIH SALUS Interoperability Module 1.0.0"
- Estado: Iniciado
- No hay errores en los logs

**Criterio de Éxito**: El módulo se instala e inicia correctamente

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-002: Health Check del Módulo

**Objetivo**: Verificar que el API REST del módulo responde correctamente

**Precondiciones**:
- Módulo instalado e iniciado
- OpenMRS ejecutándose

**Pasos**:
```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/status"
$response | ConvertTo-Json -Depth 3
```

**Resultado Esperado**:
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

**Criterio de Éxito**: success = true y el módulo responde con la información correcta

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-003: Configuración de Propiedades Globales

**Objetivo**: Verificar que las propiedades globales se configuran correctamente

**Precondiciones**:
- Módulo instalado

**Pasos**:
1. Acceder a Administración → Configuración → Propiedades Globales
2. Verificar existencia de:
   - sihsalusinterop.renhice.endpoint
   - sihsalusinterop.renhice.enabled
   - sihsalusinterop.queue.maxRetries
   - sihsalusinterop.queue.retryInterval
3. Configurar endpoint: http://hapi-fhir-server:8080/fhir
4. Habilitar envío automático: true

**Resultado Esperado**:
- Todas las propiedades existen
- Los valores se guardan correctamente
- No hay errores al guardar

**Criterio de Éxito**: Propiedades configuradas y persistidas correctamente

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-004: Conectividad con Servidor FHIR Externo

**Objetivo**: Verificar que el módulo puede conectarse al servidor HAPI FHIR

**Precondiciones**:
- HAPI FHIR ejecutándose en puerto 8081
- Propiedades globales configuradas
- Contenedores en la misma red Docker

**Pasos**:
```powershell
# Verificar acceso al servidor FHIR
$response = Invoke-RestMethod -Uri "http://localhost:8081/fhir/metadata"
$response.fhirVersion
```

**Resultado Esperado**:
- Respuesta exitosa con metadata del servidor
- fhirVersion: "4.0.1"

**Criterio de Éxito**: Conectividad establecida correctamente

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-005: Consulta de Paciente por DNI desde RENHICE

**Objetivo**: Verificar que el sistema puede consultar pacientes desde RENHICE usando DNI

**Precondiciones**:
- HAPI FHIR con datos de prueba cargados
- DNI de prueba: 74176968

**Pasos**:
```powershell
$dni = "74176968"
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/patient/$dni"
$response | ConvertTo-Json -Depth 5
```

**Resultado Esperado**:
```json
{
  "success": true,
  "total": 1,
  "patients": [
    {
      "id": "3",
      "name": "José Fernández",
      "birthDate": "1957-11-25",
      "gender": "Male",
      "identifiers": [
        {
          "system": "urn:oid:2.16.840.1.113883.4.904",
          "value": "74176968"
        }
      ]
    }
  ]
}
```

**Criterio de Éxito**: 
- success = true
- total >= 1
- Datos del paciente correctos

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-006: Consulta de Paciente No Existente

**Objetivo**: Verificar el comportamiento cuando se consulta un DNI que no existe

**Precondiciones**:
- HAPI FHIR ejecutándose

**Pasos**:
```powershell
$dni = "99999999"
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/patient/$dni"
$response | ConvertTo-Json -Depth 5
```

**Resultado Esperado**:
```json
{
  "success": true,
  "total": 0,
  "patients": []
}
```

**Criterio de Éxito**: 
- success = true
- total = 0
- No se produce error

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-007: Registro de Paciente con DNI en OpenMRS

**Objetivo**: Verificar que se puede registrar un paciente con identificador DNI

**Precondiciones**:
- OpenMRS ejecutándose
- Usuario con permisos de registro

**Pasos**:
1. Acceder a http://localhost/openmrs/
2. Login: admin / Admin123
3. Ir a Register a Patient
4. Llenar datos:
   - Given Name: Pedro
   - Family Name: García
   - Gender: Male
   - Birthdate: 01/01/1980
   - Identifier Type: DNI
   - Identifier Value: 12345678
5. Save

**Resultado Esperado**:
- Paciente creado exitosamente
- DNI guardado en el sistema
- Se puede buscar el paciente por DNI

**Criterio de Éxito**: Paciente registrado con DNI válido

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-008: Creación de Encounter (Atención Médica)

**Objetivo**: Verificar que se puede crear un encuentro médico

**Precondiciones**:
- Paciente registrado con DNI (CP-007)

**Pasos**:
1. Buscar paciente Pedro García
2. Start Visit → Outpatient
3. Add Encounter
4. Registrar:
   - Fecha y hora actual
   - Al menos un diagnóstico
   - Al menos un signo vital
5. Save Encounter

**Resultado Esperado**:
- Encounter guardado exitosamente
- Datos visibles en el historial del paciente

**Criterio de Éxito**: Encounter creado correctamente

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-009: Encolamiento Automático de Mensaje FHIR

**Objetivo**: Verificar que al guardar un Encounter, se crea automáticamente un mensaje en la cola

**Precondiciones**:
- CP-008 completado (Encounter creado)

**Pasos**:
```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/queue"
$response | ConvertTo-Json -Depth 5
```

**Resultado Esperado**:
- count >= 1
- Último mensaje tiene status: "PENDING"
- messageType: "FHIR_BUNDLE"
- attempts: 0

**Criterio de Éxito**: Mensaje encolado automáticamente con estado PENDING

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-010: Procesamiento Manual de Cola

**Objetivo**: Verificar que la cola se puede procesar manualmente

**Precondiciones**:
- CP-009 completado (mensaje en cola)
- HAPI FHIR ejecutándose

**Pasos**:
```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/processQueue" -Method Post
$response | ConvertTo-Json -Depth 3
```

**Resultado Esperado**:
```json
{
  "success": true,
  "sentCount": 1,
  "processedCount": 1,
  "message": "Se procesaron 1 mensajes. 1 enviados exitosamente."
}
```

**Criterio de Éxito**: 
- success = true
- sentCount >= 1

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-011: Verificación de Estado de Mensaje Enviado

**Objetivo**: Verificar que el mensaje cambia a estado SENT después de enviarse

**Precondiciones**:
- CP-010 completado (cola procesada)

**Pasos**:
```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/queue"
$lastItem = $response.items[-1]
Write-Host "Estado: $($lastItem.status)"
Write-Host "Intentos: $($lastItem.attempts)"
Write-Host "ID Externo: $($lastItem.externalResourceId)"
```

**Resultado Esperado**:
- Estado: SENT
- Intentos: 1
- externalResourceId contiene URL del Bundle en HAPI FHIR

**Criterio de Éxito**: Mensaje en estado SENT con ID externo válido

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-012: Verificación de Bundle en RENHICE

**Objetivo**: Verificar que el Bundle FHIR llegó correctamente a RENHICE

**Precondiciones**:
- CP-011 completado (mensaje enviado)

**Pasos**:
```powershell
# Obtener ID del Bundle
$queue = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/queue"
$bundleUrl = $queue.items[-1].externalResourceId
$bundleId = $bundleUrl.Split('/')[-1]

# Consultar Bundle en HAPI FHIR
$bundle = Invoke-RestMethod -Uri "http://localhost:8081/fhir/Bundle/$bundleId"
Write-Host "Bundle Type: $($bundle.type)"
Write-Host "Número de recursos: $($bundle.entry.Count)"
```

**Resultado Esperado**:
- Bundle existe en RENHICE
- Bundle Type: document o transaction
- Número de recursos >= 4 (Patient, Organization, Practitioner, Encounter)

**Criterio de Éxito**: Bundle completo en RENHICE con todos los recursos

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-013: Validación de Recursos FHIR en Bundle

**Objetivo**: Verificar que los recursos FHIR cumplen con los perfiles Dyaku

**Precondiciones**:
- CP-012 completado (Bundle en RENHICE)

**Pasos**:
```powershell
$queue = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/queue"
$bundleUrl = $queue.items[-1].externalResourceId
$bundleId = $bundleUrl.Split('/')[-1]
$bundle = Invoke-RestMethod -Uri "http://localhost:8081/fhir/Bundle/$bundleId"

# Verificar recursos
$bundle.entry | ForEach-Object {
    $resource = $_.resource
    Write-Host "$($resource.resourceType): $($resource.meta.profile)"
}
```

**Resultado Esperado**:
- Patient tiene perfil PacientePe
- Practitioner tiene perfil PractitionerPe
- Organization tiene perfil OrganizacionPe
- Condition tiene perfil ConditionPe (si aplica)

**Criterio de Éxito**: Todos los recursos tienen perfiles Dyaku correctos

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-014: Validación de Identificador DNI en Patient

**Objetivo**: Verificar que el Patient FHIR contiene el DNI con el sistema OID correcto

**Precondiciones**:
- CP-012 completado

**Pasos**:
```powershell
# Consultar paciente por DNI en RENHICE
$dni = "12345678"
$system = "urn:oid:2.16.840.1.113883.4.904"
$patients = Invoke-RestMethod -Uri "http://localhost:8081/fhir/Patient?identifier=$system|$dni"
Write-Host "Total encontrados: $($patients.total)"
if ($patients.total -gt 0) {
    $patient = $patients.entry[0].resource
    Write-Host "Nombre: $($patient.name[0].text)"
    Write-Host "DNI: $($patient.identifier[0].value)"
    Write-Host "Sistema: $($patient.identifier[0].system)"
}
```

**Resultado Esperado**:
- total = 1
- DNI: 12345678
- Sistema: urn:oid:2.16.840.1.113883.4.904 (RENIEC)

**Criterio de Éxito**: Patient consultable por DNI con sistema RENIEC correcto

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-015: Manejo de Error - RENHICE No Disponible

**Objetivo**: Verificar que el sistema maneja correctamente cuando RENHICE no está disponible

**Precondiciones**:
- Sistema funcionando normalmente

**Pasos**:
1. Detener HAPI FHIR:
```powershell
docker stop hapi-fhir-jpaserver-start
```
2. Crear un nuevo Encounter en OpenMRS
3. Verificar estado del mensaje:
```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/queue"
$lastItem = $response.items[-1]
Write-Host "Estado: $($lastItem.status)"
Write-Host "Error: $($lastItem.errorMessage)"
```

**Resultado Esperado**:
- Estado: ERROR
- errorMessage contiene información del error (Connection refused o similar)
- Sistema no se bloquea ni genera errores críticos

**Criterio de Éxito**: Mensaje en estado ERROR, sistema sigue operativo

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-016: Reintento Automático Después de Error

**Objetivo**: Verificar que los mensajes en ERROR se reintentan automáticamente cuando RENHICE vuelve a estar disponible

**Precondiciones**:
- CP-015 completado (mensaje en ERROR)

**Pasos**:
1. Reiniciar HAPI FHIR:
```powershell
docker start hapi-fhir-jpaserver-start
Start-Sleep -Seconds 10
```
2. Procesar cola:
```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/processQueue" -Method Post
$response | ConvertTo-Json
```
3. Verificar estado:
```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/queue"
$lastItem = $response.items[-1]
Write-Host "Estado: $($lastItem.status)"
Write-Host "Intentos: $($lastItem.attempts)"
```

**Resultado Esperado**:
- Estado: SENT
- Intentos: 2
- Mensaje enviado exitosamente en el reintento

**Criterio de Éxito**: Mensaje reenviado exitosamente, contador de intentos incrementado

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-017: Scheduler Automático

**Objetivo**: Verificar que el scheduler procesa la cola automáticamente cada 5 minutos

**Precondiciones**:
- Sistema funcionando
- HAPI FHIR ejecutándose

**Pasos**:
1. Crear un Encounter
2. NO procesar la cola manualmente
3. Esperar 5 minutos
4. Verificar logs:
```powershell
docker logs peruHCE-backend --tail 50 2>&1 | Select-String -Pattern "QueueProcessorTask|procesando cola"
```
5. Verificar estado del mensaje:
```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/queue"
$lastItem = $response.items[-1]
Write-Host "Estado: $($lastItem.status)"
```

**Resultado Esperado**:
- Logs muestran ejecución del QueueProcessorTask
- Estado del mensaje: SENT
- Procesamiento automático sin intervención manual

**Criterio de Éxito**: Cola procesada automáticamente por el scheduler

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-018: Límite de Reintentos

**Objetivo**: Verificar que los mensajes pasan a estado FAILED después de alcanzar el máximo de reintentos

**Precondiciones**:
- maxRetries configurado a 3

**Pasos**:
1. Detener HAPI FHIR
2. Crear un Encounter
3. Procesar cola manualmente 4 veces
4. Verificar estado:
```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/queue"
$lastItem = $response.items[-1]
Write-Host "Estado: $($lastItem.status)"
Write-Host "Intentos: $($lastItem.attempts)"
```

**Resultado Esperado**:
- Estado: FAILED
- Intentos: 3 (o el valor de maxRetries)
- No se intenta enviar más veces

**Criterio de Éxito**: Mensaje pasa a FAILED después de agotar reintentos

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-019: Interfaz de Monitoreo - Visualización de Cola

**Objetivo**: Verificar que la interfaz web de monitoreo muestra correctamente los mensajes

**Precondiciones**:
- Al menos 5 mensajes en la cola con diferentes estados

**Pasos**:
1. Acceder a: http://localhost/openmrs/module/sihsalusinterop/monitoreoInteroperabilidad.form
2. Verificar que se muestra:
   - Panel de resumen con contadores
   - Tabla con lista de mensajes
   - Columnas: ID, Tipo, Estado, Intentos, Fecha
3. Verificar filtrado por estado
4. Verificar botón "Ver Detalles"

**Resultado Esperado**:
- Interfaz carga correctamente
- Contadores coinciden con los datos reales
- Tabla muestra todos los mensajes
- Botones funcionan correctamente

**Criterio de Éxito**: Interfaz de monitoreo funcional y precisa

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-020: Interfaz de Monitoreo - Ver Detalles de Mensaje

**Objetivo**: Verificar que se puede inspeccionar el payload FHIR completo

**Precondiciones**:
- CP-019 completado

**Pasos**:
1. En la interfaz de monitoreo, hacer clic en "Ver Detalles" de un mensaje
2. Verificar que se muestra modal con:
   - Payload FHIR JSON formateado
   - Información de metadata
   - Botón para cerrar

**Resultado Esperado**:
- Modal se abre correctamente
- JSON está formateado y legible
- Se muestra el Bundle completo

**Criterio de Éxito**: Detalles del mensaje visibles y correctos

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-021: Interfaz de Monitoreo - Reintentar Mensaje

**Objetivo**: Verificar que se puede reintentar un mensaje manualmente desde la interfaz

**Precondiciones**:
- Al menos un mensaje en estado ERROR

**Pasos**:
1. En la interfaz de monitoreo, localizar mensaje en ERROR
2. Hacer clic en botón "Reintentar"
3. Esperar respuesta
4. Verificar que el estado se actualiza

**Resultado Esperado**:
- Botón "Reintentar" ejecuta la acción
- Se muestra mensaje de confirmación
- Estado del mensaje se actualiza

**Criterio de Éxito**: Reintento manual funciona correctamente

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-022: Validación de Codificación CIE-10

**Objetivo**: Verificar que los diagnósticos se mapean correctamente a códigos CIE-10

**Precondiciones**:
- Conceptos en OpenMRS con mapeo CIE-10

**Pasos**:
1. Crear Encounter con diagnóstico (ej: Hipertensión)
2. Procesar cola
3. Consultar Bundle en RENHICE
4. Verificar recurso Condition:
```powershell
$bundle = Invoke-RestMethod -Uri "http://localhost:8081/fhir/Bundle/$bundleId"
$condition = $bundle.entry | Where-Object { $_.resource.resourceType -eq "Condition" } | Select-Object -First 1
Write-Host "Código: $($condition.resource.code.coding[0].code)"
Write-Host "Sistema: $($condition.resource.code.coding[0].system)"
```

**Resultado Esperado**:
- Código CIE-10 válido (ej: I10 para Hipertensión)
- Sistema: http://hl7.org/fhir/sid/icd-10

**Criterio de Éxito**: Diagnósticos mapeados correctamente a CIE-10

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-023: Control de Acceso - Privilegios de Usuario

**Objetivo**: Verificar que solo usuarios con privilegios adecuados pueden acceder a las funciones

**Precondiciones**:
- Usuario sin privilegio "View Interop Queue"

**Pasos**:
1. Intentar acceder a la interfaz de monitoreo
2. Intentar llamar al API REST /interop/queue

**Resultado Esperado**:
- Acceso denegado (403 Forbidden)
- Mensaje de error indicando falta de privilegios

**Criterio de Éxito**: Control de acceso funciona correctamente

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-024: Prevención de Duplicados - Conditional Creates

**Objetivo**: Verificar que no se duplican recursos en RENHICE

**Precondiciones**:
- Paciente ya existe en RENHICE con DNI 12345678

**Pasos**:
1. Crear dos Encounters para el mismo paciente
2. Procesar ambos mensajes
3. Consultar pacientes en RENHICE por DNI:
```powershell
$dni = "12345678"
$system = "urn:oid:2.16.840.1.113883.4.904"
$patients = Invoke-RestMethod -Uri "http://localhost:8081/fhir/Patient?identifier=$system|$dni"
Write-Host "Total de pacientes: $($patients.total)"
```

**Resultado Esperado**:
- total = 1 (solo un paciente con ese DNI)
- No se crean duplicados

**Criterio de Éxito**: Conditional creates previenen duplicación de Patient

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-025: Rendimiento - Procesamiento de Cola Grande

**Objetivo**: Verificar que el sistema puede procesar múltiples mensajes eficientemente

**Precondiciones**:
- Al menos 10 mensajes en cola con estado PENDING

**Pasos**:
1. Crear 10 Encounters para diferentes pacientes
2. Medir tiempo de procesamiento:
```powershell
$start = Get-Date
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/processQueue" -Method Post
$end = Get-Date
$duration = ($end - $start).TotalSeconds
Write-Host "Tiempo: $duration segundos"
Write-Host "Procesados: $($response.processedCount)"
Write-Host "Exitosos: $($response.sentCount)"
```

**Resultado Esperado**:
- Todos los mensajes procesados exitosamente
- Tiempo razonable (< 30 segundos para 10 mensajes)
- Sin errores de memoria o timeout

**Criterio de Éxito**: Cola procesada eficientemente

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-026: Integración - Flujo Completo End-to-End

**Objetivo**: Validar el flujo completo desde registro hasta consulta

**Precondiciones**:
- Sistema limpio sin datos previos de prueba

**Pasos**:
1. Registrar paciente nuevo con DNI 87654321
2. Crear visita y encounter con diagnóstico
3. Esperar procesamiento automático (5 min)
4. Consultar paciente desde RENHICE:
```powershell
$dni = "87654321"
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/patient/$dni"
$response | ConvertTo-Json -Depth 5
```

**Resultado Esperado**:
- Paciente consultable desde RENHICE
- Todos los datos correctos
- Flujo completo sin intervención manual

**Criterio de Éxito**: Flujo end-to-end funciona automáticamente

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-027: Seguridad - Validación de Endpoint HTTPS

**Objetivo**: Verificar que el sistema soporta conexiones HTTPS

**Precondiciones**:
- Servidor FHIR con soporte HTTPS disponible

**Pasos**:
1. Configurar propiedad global con endpoint HTTPS
2. Crear un Encounter
3. Procesar cola
4. Verificar que la conexión usa TLS

**Resultado Esperado**:
- Conexión HTTPS exitosa
- Mensaje enviado correctamente
- No se aceptan certificados autofirmados sin configuración explícita

**Criterio de Éxito**: Soporte HTTPS funcional

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-028: Persistencia - Verificación de Base de Datos

**Objetivo**: Verificar que los mensajes persisten correctamente en la base de datos

**Precondiciones**:
- Acceso a la base de datos MySQL

**Pasos**:
```sql
-- Conectar a la BD
mysql -h localhost -P 3307 -u openmrs -p openmrs

-- Consultar tabla de cola
SELECT queue_id, message_type, status, attempts, DATE_FORMAT(queued_at, '%Y-%m-%d %H:%i:%s') as queued_at
FROM sihsalus_interop_queue
ORDER BY queue_id DESC
LIMIT 10;
```

**Resultado Esperado**:
- Tabla existe y contiene registros
- Todos los campos poblados correctamente
- Estados consistentes con lo mostrado en la interfaz

**Criterio de Éxito**: Persistencia de datos correcta

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-029: Logs y Auditoría

**Objetivo**: Verificar que se registran eventos de auditoría

**Precondiciones**:
- Sistema funcionando

**Pasos**:
```powershell
docker logs peruHCE-backend --tail 100 2>&1 | Select-String -Pattern "sihsalus|FHIR|Dyaku"
```

**Resultado Esperado**:
- Logs contienen información de:
  - Eventos de Encounter guardado
  - Construcción de Bundles
  - Envíos a RENHICE
  - Errores con stack trace completo

**Criterio de Éxito**: Trazabilidad completa en logs

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

### CP-030: Documentación y Configuración

**Objetivo**: Verificar que la documentación es precisa y completa

**Precondiciones**:
- Documentación disponible en carpeta docs/

**Pasos**:
1. Revisar README.md
2. Revisar MANUAL_TECNICO.md
3. Seguir pasos de instalación
4. Verificar que todos los endpoints documentados existen
5. Verificar que los ejemplos de código funcionan

**Resultado Esperado**:
- Documentación completa y actualizada
- Sin errores en ejemplos de código
- Instrucciones claras y verificables

**Criterio de Éxito**: Documentación precisa y completa

**Estado**: [ ] Pendiente [ ] Aprobado [ ] Fallido

---

## 3. Matriz de Trazabilidad

| Requisito | Casos de Prueba Relacionados |
|-----------|------------------------------|
| Instalación del módulo | CP-001, CP-002, CP-003 |
| Consulta desde RENHICE | CP-004, CP-005, CP-006, CP-014 |
| Envío a RENHICE | CP-007, CP-008, CP-009, CP-010, CP-011, CP-012 |
| Conformidad FHIR R4 | CP-013, CP-022 |
| Manejo de errores | CP-015, CP-016, CP-018 |
| Offline-First | CP-009, CP-015, CP-016, CP-017 |
| Interfaz de monitoreo | CP-019, CP-020, CP-021 |
| Seguridad | CP-023, CP-027 |
| Prevención de duplicados | CP-024 |
| Rendimiento | CP-025 |
| Integración completa | CP-026 |
| Persistencia | CP-028 |
| Auditoría | CP-029 |
| Documentación | CP-030 |

---

## 4. Criterios de Aceptación Final

### 4.1. Métrica de Éxito

Para cumplir con el objetivo de la tesis:

**Total de Casos de Prueba**: 30

**Casos Exitosos Requeridos**: 27 (90%)

**Fórmula de Cumplimiento**:
```
Porcentaje de Éxito = (Casos Aprobados / Total de Casos) × 100
```

### 4.2. Categorías de Resultados

- **Aprobado**: El caso de prueba cumple completamente con el criterio de éxito
- **Fallido**: El caso de prueba no cumple con el criterio de éxito
- **Pendiente**: El caso de prueba no se ha ejecutado

### 4.3. Reporte Final

El reporte final debe incluir:
- Total de casos ejecutados
- Casos aprobados, fallidos y pendientes
- Porcentaje de éxito
- Descripción de casos fallidos y acciones correctivas
- Evidencia (capturas de pantalla, logs, respuestas de API)

---

## 5. Cronograma de Ejecución

| Fase | Casos de Prueba | Duración Estimada |
|------|-----------------|-------------------|
| Fase 1: Instalación y Configuración | CP-001 a CP-004 | 1 hora |
| Fase 2: Funcionalidad Básica | CP-005 a CP-012 | 2 horas |
| Fase 3: Validación FHIR | CP-013, CP-014, CP-022 | 1 hora |
| Fase 4: Resiliencia y Errores | CP-015 a CP-018 | 1.5 horas |
| Fase 5: Interfaz de Usuario | CP-019 a CP-021 | 1 hora |
| Fase 6: Seguridad y Calidad | CP-023, CP-024, CP-027 | 1 hora |
| Fase 7: Rendimiento e Integración | CP-025, CP-026, CP-028, CP-029 | 2 horas |
| Fase 8: Documentación | CP-030 | 0.5 horas |

**Total Estimado**: 10 horas

---

## 6. Riesgos y Mitigaciones

| Riesgo | Impacto | Probabilidad | Mitigación |
|--------|---------|--------------|------------|
| HAPI FHIR no inicia | Alto | Baja | Verificar Docker Compose, logs, y conectividad de red |
| Falta de datos de prueba | Medio | Media | Script de población de datos incluido |
| Timeout en procesamiento | Medio | Baja | Configurar timeouts adecuados, verificar recursos del sistema |
| Errores de mapeo CIE-10 | Medio | Media | Usar conceptos con mapeo validado de OCL |
| Problemas de privilegios | Bajo | Baja | Documentar privilegios necesarios claramente |

---

## 7. Responsables

- **Ejecutor de Pruebas**: Johan Amador
- **Revisor Técnico**: [Asesor de Tesis]
- **Aprobador Final**: [Jurado de Tesis]

---

## 8. Anexos

### Anexo A: Script de Datos de Prueba

Ver: `hapi-fhir-jpaserver-starter/populate_test_data.py`

### Anexo B: Comandos PowerShell para Pruebas

Ver: `test_interoperability.ps1`

### Anexo C: Estructura de Base de Datos

Ver: MANUAL_TECNICO.md - Anexo A

---

**Versión**: 1.0.0  
**Fecha**: Noviembre 2025  
**Estado**: En Ejecución

---

## Registro de Cambios

| Fecha | Versión | Cambios | Autor |
|-------|---------|---------|-------|
| 2025-11-27 | 1.0.0 | Versión inicial del plan de pruebas | Johan Amador |

