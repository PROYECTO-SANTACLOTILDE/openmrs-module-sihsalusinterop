# Pruebas de Interoperabilidad SIH.SALUS - RENHICE

Este documento contiene las pruebas completas del flujo de interoperabilidad entre SIH.SALUS (OpenMRS) y RENHICE (simulado con HAPI FHIR).

## Objetivo

Demostrar que el sistema puede:
1. Consultar pacientes desde RENHICE por DNI
2. Enviar automáticamente información de atenciones a RENHICE cuando se guarda un Encounter

---

## Pre-requisitos

### 1. Verificar que los contenedores estén corriendo

```powershell
docker ps --format "{{.Names}}\t{{.Ports}}"
```

Deberías ver:
- `peruHCE-backend` (OpenMRS) - puerto 8080
- `hapi-fhir-jpaserver-start` (RENHICE simulado) - puerto 8081

### 2. Verificar que estén en la misma red Docker

```powershell
docker network connect sihsalus-distro-referenceapplication_default hapi-fhir-jpaserver-start
```

### 3. Verificar que el módulo esté cargado

Ir a: http://localhost/openmrs/admin/modules/module.list

Buscar: SIH SALUS Interoperability Module 1.0.0

---

## PRUEBA 1: Health Check del Módulo

### Objetivo
Verificar que el módulo está funcionando correctamente.

### Endpoint
```
GET http://localhost/openmrs/ws/rest/v1/interop/status
```

### Comando PowerShell
```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/status"
$response | ConvertTo-Json -Depth 3
```

### Respuesta esperada
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

### Criterio de éxito
- `success` debe ser `true`
- El módulo debe responder correctamente

---

## PRUEBA 2: Consultar Paciente por DNI desde RENHICE

### Objetivo
Verificar que el sistema puede consultar la HCE de un paciente desde RENHICE usando su DNI.

### Pacientes de prueba disponibles en RENHICE
Los siguientes DNIs fueron cargados en RENHICE con historial clínico:

- DNI: 74176968 - José Fernández (1 atención)
- DNI: 82446268 - Miguel Quispe (2 atenciones)
- DNI: 80745861 - Rosa Flores (3 atenciones)
- DNI: 48213853 - Lucía Rodríguez (1 atención)
- DNI: 91530636 - Carmen Huamán (2 atenciones)
- DNI: 17135254 - Carmen Fernández (2 atenciones)
- DNI: 75976478 - Rosa Quispe (3 atenciones)
- DNI: 59363005 - Juan Rodríguez (1 atención)
- DNI: 49647649 - Miguel Fernández (1 atención)
- DNI: 54257302 - Lucía Huamán (3 atenciones)

### Endpoint
```
GET http://localhost/openmrs/ws/rest/v1/interop/patient/{DNI}
```

### Comando PowerShell (ejemplo con DNI 74176968)
```powershell
$dni = "74176968"
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/patient/$dni"
$response | ConvertTo-Json -Depth 5
```

### Respuesta esperada
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

### Criterio de éxito
- `success` debe ser `true`
- `total` debe ser mayor a 0
- Debe mostrar los datos del paciente con el DNI consultado

### Nota para integración con Frontend
Este endpoint puede ser usado desde el frontend O3 para:
- Buscar pacientes externos antes de registrarlos localmente
- Mostrar historial clínico de otras instituciones
- Verificar si un paciente ya existe en RENHICE

---

## PRUEBA 3: Envío Automático de Atención a RENHICE

### Objetivo
Verificar que cuando se guarda un Encounter en OpenMRS, el sistema automáticamente:
1. Construye un Bundle FHIR con los datos de la atención
2. Lo encola para envío asíncrono
3. Lo envía a RENHICE (HAPI FHIR)

### Pasos

#### 3.1. Crear un paciente en OpenMRS con DNI

Importante: El paciente DEBE tener un identificador de tipo DNI para que el sistema lo envíe a RENHICE.

1. Ir a: http://localhost/openmrs/
2. Login: admin / Admin123
3. Ir a: Register a Patient
4. Llenar los datos:
   - Given Name: Pedro
   - Family Name: García
   - Gender: Male
   - Birthdate: 01/01/1980
   - Identifier Type: DNI (si no existe, crearlo en la configuración)
   - Identifier Value: 12345678 (8 dígitos)
5. Save

#### 3.2. Crear un Encounter (Atención) para el paciente

1. Buscar el paciente Pedro García
2. Ir a Start Visit
3. Seleccionar tipo de visita: Outpatient
4. Start
5. Ir a Add Encounter
6. Llenar datos de la atención:
   - Fecha y hora
   - Diagnósticos (si es posible)
   - Signos vitales (opcionales)
7. Save Encounter

#### 3.3. Verificar que el mensaje se encoló automáticamente

Endpoint:
```
GET http://localhost/openmrs/ws/rest/v1/interop/queue
```

Comando PowerShell:
```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/queue"
$response | ConvertTo-Json -Depth 5
```

Respuesta esperada:
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
      "queuedAt": "2025-11-25T16:30:00",
      "targetEndpoint": "http://hapi-fhir-server:8080/fhir"
    }
  ]
}
```

#### 3.4. Procesar la cola manualmente (o esperar 5 minutos para el scheduler automático)

Endpoint:
```
POST http://localhost/openmrs/ws/rest/v1/interop/processQueue
```

Comando PowerShell:
```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/processQueue" -Method Post
$response | ConvertTo-Json -Depth 3
```

Respuesta esperada:
```json
{
  "success": true,
  "sentCount": 1,
  "processedCount": 1,
  "message": "Se procesaron 1 mensajes. 1 enviados exitosamente."
}
```

#### 3.5. Verificar el estado final del mensaje

Comando PowerShell:
```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/queue"
$item = $response.items[0]
Write-Host "Estado: $($item.status)"
Write-Host "Intentos: $($item.attempts)"
Write-Host "Enviado el: $($item.sentAt)"
Write-Host "ID Externo: $($item.externalResourceId)"
```

Resultado esperado:
```
Estado: SENT
Intentos: 1
Enviado el: 1764107214824
ID Externo: http://hapi-fhir-server:8080/fhir/Bundle/82a2ac0c-97f3-412f-8ef4-2a967500a809
```

### Criterio de éxito
- El mensaje debe cambiar de `PENDING` a `SENT`
- `externalResourceId` debe contener la URL del Bundle creado en RENHICE
- `sentAt` debe tener una fecha/hora

---

## PRUEBA 4: Verificar que el Bundle llegó a RENHICE

### Objetivo
Confirmar que el Bundle FHIR enviado desde OpenMRS está almacenado en RENHICE (HAPI FHIR).

### Pasos

#### 4.1. Consultar el Bundle directamente en HAPI FHIR

Si el `externalResourceId` del paso anterior es:
```
http://hapi-fhir-server:8080/fhir/Bundle/82a2ac0c-97f3-412f-8ef4-2a967500a809
```

Entonces el ID del Bundle es: `82a2ac0c-97f3-412f-8ef4-2a967500a809`

Comando PowerShell:
```powershell
# Obtener el ID del Bundle del mensaje enviado
$queue = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/queue"
$bundleUrl = $queue.items[0].externalResourceId
$bundleId = $bundleUrl.Split('/')[-1]

# Consultar el Bundle en HAPI FHIR
$bundle = Invoke-RestMethod -Uri "http://localhost:8081/fhir/Bundle/$bundleId"
Write-Host "Bundle Type: $($bundle.type)"
Write-Host "Número de recursos: $($bundle.entry.Count)"
Write-Host "Recursos incluidos:"
$bundle.entry | ForEach-Object { Write-Host "  - $($_.resource.resourceType)" }
```

Resultado esperado:
```
Bundle Type: document
Número de recursos: 8
Recursos incluidos:
  - Patient
  - Organization
  - Practitioner
  - Encounter
  - Condition
  - Observation
  - Observation
  - Observation
```

#### 4.2. Verificar que el paciente esté en RENHICE

Comando PowerShell:
```powershell
# Consultar el paciente por DNI en RENHICE
$dni = "12345678"
$system = "urn:oid:2.16.840.1.113883.4.904"
$patients = Invoke-RestMethod -Uri "http://localhost:8081/fhir/Patient?identifier=$system|$dni"
Write-Host "Total de pacientes encontrados: $($patients.total)"
if ($patients.total -gt 0) {
    $patient = $patients.entry[0].resource
    Write-Host "Paciente: $($patient.name[0].text)"
    Write-Host "DNI: $($patient.identifier[0].value)"
}
```

Resultado esperado:
```
Total de pacientes encontrados: 1
Paciente: Pedro García
DNI: 12345678
```

### Criterio de éxito
- El Bundle debe existir en RENHICE
- Debe contener al menos: Patient, Organization, Practitioner, Encounter
- El paciente debe ser consultable por su DNI en RENHICE

---

## PRUEBA 5: Flujo Completo - Offline-First

### Objetivo
Verificar que el sistema funciona correctamente en modo offline (sin conectividad a RENHICE).

### Pasos

#### 5.1. Simular que RENHICE no está disponible

Detener el contenedor HAPI FHIR:
```powershell
docker stop hapi-fhir-jpaserver-start
```

#### 5.2. Crear un Encounter en OpenMRS

Repetir los pasos de la PRUEBA 3.2 (crear un nuevo Encounter para cualquier paciente).

#### 5.3. Verificar que el mensaje se encoló con estado ERROR

Comando PowerShell:
```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/queue"
$lastItem = $response.items[-1]
Write-Host "Estado: $($lastItem.status)"
Write-Host "Error: $($lastItem.errorMessage)"
```

Resultado esperado:
```
Estado: ERROR
Error: Connection refused: connect
```

#### 5.4. Reiniciar RENHICE

Comando PowerShell:
```powershell
docker start hapi-fhir-jpaserver-start
Start-Sleep -Seconds 10
```

#### 5.5. Procesar la cola de nuevo (reintento automático)

Comando PowerShell:
```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/processQueue" -Method Post
$response | ConvertTo-Json -Depth 3
```

Resultado esperado:
```json
{
  "success": true,
  "sentCount": 1,
  "processedCount": 1,
  "message": "Se procesaron 1 mensajes. 1 enviados exitosamente."
}
```

#### 5.6. Verificar que el mensaje ahora está SENT

Comando PowerShell:
```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/queue"
$lastItem = $response.items[-1]
Write-Host "Estado: $($lastItem.status)"
Write-Host "Intentos: $($lastItem.attempts)"
```

Resultado esperado:
```
Estado: SENT
Intentos: 2
```

### Criterio de éxito
- El mensaje debe quedar en ERROR cuando RENHICE no está disponible
- El mensaje debe enviarse exitosamente cuando RENHICE vuelve a estar disponible
- El contador de intentos debe incrementarse
- Esto demuestra el comportamiento Offline-First requerido para zonas rurales

---

## PRUEBA 6: Scheduler Automático

### Objetivo
Verificar que el scheduler procesa la cola automáticamente cada 5 minutos sin intervención manual.

### Pasos

#### 6.1. Crear un Encounter

Crear un nuevo Encounter siguiendo los pasos de la PRUEBA 3.2.

#### 6.2. NO procesar la cola manualmente

Simplemente esperar.

#### 6.3. Verificar los logs del contenedor

Comando PowerShell:
```powershell
docker logs peruHCE-backend --tail 50 2>&1 | Select-String -Pattern "QueueProcessorTask|procesando cola"
```

Resultado esperado (después de aproximadamente 5 minutos):
```
INFO - QueueProcessorTask: Procesando cola automáticamente...
INFO - QueueProcessorTask: Se procesaron 1 mensajes. 1 enviados exitosamente.
```

#### 6.4. Verificar el estado del mensaje

Comando PowerShell:
```powershell
$response = Invoke-RestMethod -Uri "http://localhost/openmrs/ws/rest/v1/interop/queue"
$lastItem = $response.items[-1]
Write-Host "Estado: $($lastItem.status)"
```

Resultado esperado:
```
Estado: SENT
```

### Criterio de éxito
- El mensaje debe procesarse automáticamente sin intervención manual
- Los logs deben mostrar que el scheduler se ejecutó
- El mensaje debe cambiar a SENT automáticamente

---

## Resumen de Funcionalidades Probadas

| Funcionalidad | Estado | Descripción |
|---------------|--------|-------------|
| Health Check | Probado | El módulo responde correctamente |
| Consulta HCE por DNI | Probado | Se puede consultar pacientes desde RENHICE |
| Envío automático de atenciones | Probado | Los Encounters se envían automáticamente a RENHICE |
| Cola persistente | Probado | Los mensajes se guardan en BD |
| Manejo de errores | Probado | Los mensajes quedan en ERROR si RENHICE no está disponible |
| Reintentos automáticos | Probado | Los mensajes en ERROR se reintentan automáticamente |
| Scheduler automático | Probado | La cola se procesa cada 5 minutos sin intervención |
| Offline-First | Probado | El sistema funciona correctamente sin conexión |

---

## Conclusiones para la Tesis

El componente de interoperabilidad cumple con los requisitos funcionales establecidos:

### Requisitos Cumplidos

1. Gestión de Recursos FHIR R4
   - Patient, Encounter, Practitioner, Organization, Condition, Observation
   - Conformes a perfiles Dyaku (MINSA Perú)

2. Flujo de Interoperabilidad
   - Publicar Resumen Clínico: Implementado y probado
   - Consultar Resumen Clínico: Implementado y probado

3. Requisitos No Funcionales
   - Offline-First: Cola persistente con reintentos automáticos
   - Resiliencia: Manejo de errores y reintentos
   - Monitoreo: API REST para consultar estado de la cola

4. Estándares Técnicos
   - HL7 FHIR R4
   - Perfiles Dyaku (MINSA Perú)
   - Codificación CIE-10
   - Identificadores DNI (RENIEC)

### Arquitectura Validada

```
┌─────────────────┐      ┌──────────────────┐      ┌────────────────┐
│  OpenMRS O3     │      │  OMOD Interop    │      │  RENHICE       │
│  (Frontend)     │─────>│  (Backend)       │─────>│  (HAPI FHIR)   │
│                 │      │                  │      │                │
│  - Registro     │      │  - Mapeadores    │      │  - Pacientes   │
│  - Atenciones   │      │  - Cola          │      │  - HCE         │
│  - Consultas    │      │  - Scheduler     │      │  - Bundles     │
└─────────────────┘      └──────────────────┘      └────────────────┘
                              ▲
                              │ Offline-First
                              │ (Cola persistente)
                              ▼
                         ┌──────────┐
                         │  MySQL   │
                         │  (BD)    │
                         └──────────┘
```

### Ventajas del Sistema

1. Offline-First: El sistema funciona sin internet, ideal para zonas rurales
2. Automático: No requiere intervención manual del personal médico
3. Robusto: Manejo de errores y reintentos automáticos
4. Escalable: Cola persistente puede manejar miles de mensajes
5. Estándares: Conforme a FHIR R4 y perfiles Dyaku (MINSA Perú)

---

Autor: Johan Amador - SIH.SALUS  
Fecha: Noviembre 2025  
Hospital: Santa Clotilde, Loreto, Perú
