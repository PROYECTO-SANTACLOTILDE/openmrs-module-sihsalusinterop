# Endpoints REST - SIH SALUS Interop API

## 🚀 Base URL
```
http://localhost:8080/openmrs/module/sihsalusinterop/api
```

---

## 📋 Endpoints Disponibles

### 1. Encolar Paciente

**Endpoint:** `POST /queue/patient/{patientId}`

**Descripción:** Convierte un paciente OpenMRS a FHIR R4 y lo encola para envío a RENHICE.

**Ejemplo cURL:**
```bash
curl -X POST \
  http://localhost:8080/openmrs/module/sihsalusinterop/api/queue/patient/123 \
  -u admin:Admin123
```

**Respuesta Exitosa:**
```json
{
  "success": true,
  "message": "Paciente encolado exitosamente para envío a RENHICE",
  "queueId": 1,
  "status": "PENDING",
  "patientName": "Maria Elena RODRIGUEZ GONZALES",
  "targetEndpoint": "http://localhost:8080/fhir"
}
```

**Respuesta Error (sin DNI):**
```json
{
  "success": false,
  "error": "El paciente no tiene DNI. Es obligatorio para envío a RENHICE. ID OpenMRS: 123",
  "errorCode": "DNI_NOT_FOUND"
}
```

---

### 2. Procesar Cola

**Endpoint:** `POST /queue/process`

**Descripción:** Procesa todos los mensajes con estado PENDING y los envía al servidor FHIR.

**Ejemplo cURL:**
```bash
curl -X POST \
  http://localhost:8080/openmrs/module/sihsalusinterop/api/queue/process \
  -u admin:Admin123
```

**Respuesta:**
```json
{
  "success": true,
  "message": "Cola procesada exitosamente",
  "sentCount": 5
}
```

---

### 3. Ver Todos los Items

**Endpoint:** `GET /queue/items`

**Descripción:** Obtiene todos los items de la cola.

**Ejemplo cURL:**
```bash
curl -X GET \
  http://localhost:8080/openmrs/module/sihsalusinterop/api/queue/items \
  -u admin:Admin123
```

**Respuesta:**
```json
{
  "success": true,
  "count": 10,
  "items": [
    {
      "queueId": 1,
      "messageType": "FHIR_BUNDLE",
      "status": "SENT",
      "attempts": 1,
      "queuedAt": "2024-11-21T15:30:00",
      "sentAt": "2024-11-21T15:32:00",
      "targetEndpoint": "http://localhost:8080/fhir"
    },
    ...
  ]
}
```

---

### 4. Filtrar por Estado

**Endpoint:** `GET /queue/items/status/{status}`

**Descripción:** Filtra items por estado (PENDING, SENT, ERROR, FAILED).

**Ejemplo cURL (items pendientes):**
```bash
curl -X GET \
  http://localhost:8080/openmrs/module/sihsalusinterop/api/queue/items/status/PENDING \
  -u admin:Admin123
```

**Ejemplo cURL (items con error):**
```bash
curl -X GET \
  http://localhost:8080/openmrs/module/sihsalusinterop/api/queue/items/status/ERROR \
  -u admin:Admin123
```

**Respuesta:**
```json
{
  "success": true,
  "status": "PENDING",
  "count": 3,
  "items": [...]
}
```

---

### 5. Reintentar Envío

**Endpoint:** `POST /queue/retry/{queueId}`

**Descripción:** Reintenta enviar un mensaje específico de la cola.

**Ejemplo cURL:**
```bash
curl -X POST \
  http://localhost:8080/openmrs/module/sihsalusinterop/api/queue/retry/5 \
  -u admin:Admin123
```

**Respuesta:**
```json
{
  "success": true,
  "queueId": 5,
  "message": "Mensaje enviado exitosamente"
}
```

---

### 6. Estadísticas de la Cola

**Endpoint:** `GET /queue/stats`

**Descripción:** Obtiene estadísticas agregadas de la cola.

**Ejemplo cURL:**
```bash
curl -X GET \
  http://localhost:8080/openmrs/module/sihsalusinterop/api/queue/stats \
  -u admin:Admin123
```

**Respuesta:**
```json
{
  "success": true,
  "total": 25,
  "pending": 5,
  "sent": 18,
  "error": 1,
  "failed": 1
}
```

---

## 🔐 Autenticación

Todos los endpoints requieren autenticación básica de OpenMRS:

```bash
-u username:password
```

Credenciales por defecto:
- **Usuario:** admin
- **Contraseña:** Admin123

---

## 📊 Estados de la Cola

| Estado | Descripción |
|--------|-------------|
| `PENDING` | Mensaje en cola, esperando envío |
| `PROCESSING` | Siendo enviado en este momento |
| `SENT` | Enviado exitosamente |
| `ERROR` | Error al enviar (se reintentará) |
| `FAILED` | Error permanente (agotados reintentos) |

---

## 🧪 Flujo de Prueba Completo

```bash
# 1. Encolar un paciente
curl -X POST \
  http://localhost:8080/openmrs/module/sihsalusinterop/api/queue/patient/2 \
  -u admin:Admin123

# 2. Ver items pendientes
curl -X GET \
  http://localhost:8080/openmrs/module/sihsalusinterop/api/queue/items/status/PENDING \
  -u admin:Admin123

# 3. Procesar la cola
curl -X POST \
  http://localhost:8080/openmrs/module/sihsalusinterop/api/queue/process \
  -u admin:Admin123

# 4. Ver estadísticas
curl -X GET \
  http://localhost:8080/openmrs/module/sihsalusinterop/api/queue/stats \
  -u admin:Admin123

# 5. Si hay errores, reintentar
curl -X POST \
  http://localhost:8080/openmrs/module/sihsalusinterop/api/queue/retry/1 \
  -u admin:Admin123
```

---

## 🔍 Troubleshooting

### Error: 401 Unauthorized
```bash
# Verificar credenciales
curl -X GET http://localhost:8080/openmrs/ws/rest/v1/session -u admin:Admin123
```

### Error: 404 Not Found
```bash
# Verificar que el módulo esté instalado y activo
curl -X GET http://localhost:8080/openmrs/ws/rest/v1/module -u admin:Admin123
```

### Error: 500 Internal Server Error
```bash
# Revisar logs de OpenMRS
docker logs -f <container_id> | grep -i "sihsalus\|error"
```

---

## 📝 Notas Adicionales

- **Timeout:** Por defecto, HAPI FHIR tiene un timeout de 30 segundos
- **Reintentos:** Máximo 5 intentos por defecto
- **Intervalo:** El scheduler puede procesar la cola cada 5 minutos
- **Payload:** El JSON FHIR puede ser de varios KB dependiendo del paciente

---

**Autor:** Hospital Santa Clotilde - SIH.SALUS Team  
**Fecha:** 21 de Noviembre, 2024

