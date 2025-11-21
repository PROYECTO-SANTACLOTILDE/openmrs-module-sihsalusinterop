# Guía Completa - Arquitectura de Persistencia Implementada

## 🎯 Resumen

Se ha implementado exitosamente la **arquitectura completa DAO/Service** siguiendo el patrón de OpenMRS para gestionar la cola de mensajes de interoperabilidad FHIR.

---

## 📁 Estructura de Archivos Implementados

### 1. **Capa de Modelo (Entidad)**

```
api/src/main/java/.../api/model/
└── InteropQueueItem.java          ✅ Ya existía (extiende BaseOpenmrsData)
```

**Campos principales:**
- `queueId` - ID de la cola
- `messageType` - Tipo: "FHIR_BUNDLE" o "FUA_DOCUMENT"
- `payload` - JSON/XML del mensaje
- `status` - Estado: PENDING, PROCESSING, SENT, ERROR, FAILED
- `attempts` / `maxAttempts` - Control de reintentos
- `queuedAt`, `lastAttemptAt`, `sentAt` - Timestamps
- `targetEndpoint` - URL del servidor FHIR
- `externalResourceId` - ID en sistema externo

### 2. **Capa de Acceso a Datos (DAO)**

```
api/src/main/java/.../api/dao/
└── InteropQueueDao.java            ✅ Ya existía
```

**Métodos implementados:**
- `save(InteropQueueItem)` - Guardar/actualizar
- `getById(Integer)` - Obtener por ID
- `getPendingItems()` - Items con status=PENDING
- `getAll()` - Todos los items
- `delete(InteropQueueItem)` - Eliminar

### 3. **Capa de Servicio**

```
api/src/main/java/.../api/
├── DyakuSenderService.java         ✅ Actualizado (agregado queuePatient)
└── impl/
    └── DyakuSenderServiceImpl.java ✅ Actualizado (implementado queuePatient)
```

**Métodos del servicio:**
- `queueMessage()` - Encolar mensaje genérico
- `queuePatient()` - 🆕 **Encolar paciente completo** (OpenMRS → FHIR)
- `processQueue()` - Procesar cola de mensajes pendientes
- `getAllQueueItems()` - Ver todos los items
- `getQueueItemsByStatus()` - Filtrar por estado
- `retryQueueItem()` - Reintentar envío
- `deleteQueueItem()` - Eliminar item

### 4. **Capa de Conversión (Mappers)**

```
api/src/main/java/.../api/mapper/
└── DyakuPatientMapper.java         ✅ Ya implementado
```

### 5. **Configuración Spring**

```
api/src/main/resources/
└── moduleApplicationContext.xml    🆕 NUEVO
```

---

## 🚀 Flujo Completo de Ejecución

### Escenario: Registrar un Paciente y Enviarlo a RENHICE

```java
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.sihsalusinterop.api.DyakuSenderService;
import org.openmrs.module.sihsalusinterop.api.model.InteropQueueItem;

public class EjemploEnviarPaciente {
    
    public static void enviarPacienteARenhice(Integer patientId) {
        
        // 1. Obtener servicios de OpenMRS
        Patient patient = Context.getPatientService().getPatient(patientId);
        DyakuSenderService senderService = Context.getService(DyakuSenderService.class);
        
        try {
            // 2. Encolar paciente (conversión automática OpenMRS → FHIR → JSON)
            InteropQueueItem queueItem = senderService.queuePatient(patient);
            
            System.out.println("✓ Paciente encolado exitosamente!");
            System.out.println("  ID de Cola: " + queueItem.getQueueId());
            System.out.println("  Estado: " + queueItem.getStatus());
            
        } catch (InteropException e) {
            // Error de validación (ej: paciente sin DNI)
            System.err.println("✗ Error: " + e.getMessage());
            System.err.println("  Código: " + e.getErrorCode());
        }
    }
}
```

### ¿Qué hace `queuePatient()` internamente?

1. **Convierte** el paciente OpenMRS a FHIR R4 (perfil MINSA) usando `DyakuPatientMapper`
2. **Crea un Bundle** FHIR tipo "transaction"
3. **Serializa** el Bundle a JSON usando HAPI FHIR
4. **Crea** un `InteropQueueItem` con:
   - `messageType` = "FHIR_BUNDLE"
   - `payload` = JSON del Bundle
   - `status` = "PENDING"
   - `targetEndpoint` = URL de RENHICE
5. **Persiste** en la base de datos usando el DAO
6. **Retorna** el item de cola creado

---

## 📊 Diagrama de Flujo

```
┌─────────────────────────────────────────────────────────────────┐
│                    FLUJO DE INTEROPERABILIDAD                   │
└─────────────────────────────────────────────────────────────────┘

1. REGISTRO DE PACIENTE
   ┌────────────────┐
   │ Formulario Web │
   │  (OpenMRS UI)  │
   └────────┬───────┘
            │
            ▼
   ┌────────────────┐
   │ Patient Service│ (OpenMRS Core)
   └────────┬───────┘
            │
            ▼
┌───────────────────────────────────────────────────────────────┐
│ 2. ENCOLAMIENTO (Controller o Service)                       │
│                                                               │
│   DyakuSenderService.queuePatient(patient)                   │
│                                                               │
│   ┌─────────────────────────────────────────────────────┐   │
│   │ A. DyakuPatientMapper.toDyakuFhir(patient)         │   │
│   │    → Convierte OpenMRS Patient a FHIR R4           │   │
│   │    → Valida DNI obligatorio                         │   │
│   │    → Aplica perfil MINSA                            │   │
│   └─────────────────────────────────────────────────────┘   │
│                         ▼                                     │
│   ┌─────────────────────────────────────────────────────┐   │
│   │ B. FhirContext.newJsonParser().encode(bundle)      │   │
│   │    → Serializa Bundle FHIR a JSON                   │   │
│   └─────────────────────────────────────────────────────┘   │
│                         ▼                                     │
│   ┌─────────────────────────────────────────────────────┐   │
│   │ C. InteropQueueDao.save(queueItem)                 │   │
│   │    → Persiste en sihsalus_interop_queue            │   │
│   └─────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
            │
            ▼
   ┌────────────────┐
   │   Base de      │
   │     Datos      │ sihsalus_interop_queue
   │   (MySQL)      │ status = "PENDING"
   └────────┬───────┘
            │
            ▼
┌───────────────────────────────────────────────────────────────┐
│ 3. PROCESAMIENTO (Scheduler o Manual)                        │
│                                                               │
│   DyakuSenderService.processQueue()                          │
│                                                               │
│   ┌─────────────────────────────────────────────────────┐   │
│   │ FOR cada item PENDING:                              │   │
│   │   • Parsear JSON → Bundle FHIR                      │   │
│   │   • Crear cliente HAPI FHIR                         │   │
│   │   • POST al servidor RENHICE                        │   │
│   │   • Actualizar status → SENT o ERROR                │   │
│   └─────────────────────────────────────────────────────┘   │
└───────────────────────────────────────────────────────────────┘
            │
            ▼
   ┌────────────────┐
   │    RENHICE     │
   │ (MINSA Server) │ http://renhice.minsa.gob.pe/fhir
   │   FHIR Server  │
   └────────────────┘
```

---

## 🔧 Configuración de Beans (moduleApplicationContext.xml)

```xml
<!-- DAO -->
<bean id="sihsalusinterop.InteropQueueDao" 
      class="org.openmrs.module.sihsalusinterop.api.dao.InteropQueueDao">
    <property name="sessionFactory" ref="sessionFactory"/>
</bean>

<!-- Service -->
<bean id="sihsalusinterop.DyakuSenderService" 
      class="org.openmrs.module.sihsalusinterop.api.impl.DyakuSenderServiceImpl"
      parent="serviceContext">
    <property name="dao" ref="sihsalusinterop.InteropQueueDao"/>
</bean>

<!-- Registrar en el contexto de OpenMRS -->
<bean parent="serviceContext">
    <property name="moduleService">
        <list>
            <value>org.openmrs.module.sihsalusinterop.api.DyakuSenderService</value>
            <ref bean="sihsalusinterop.DyakuSenderService"/>
        </list>
    </property>
</bean>
```

---

## 🧪 Ejemplos de Uso

### Ejemplo 1: Desde un Controlador REST

```java
package org.openmrs.module.sihsalusinterop.web.controller;

import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.sihsalusinterop.api.DyakuSenderService;
import org.openmrs.module.sihsalusinterop.api.exception.InteropException;
import org.openmrs.module.sihsalusinterop.api.model.InteropQueueItem;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/module/sihsalusinterop")
public class InteropQueueController {
    
    /**
     * POST /module/sihsalusinterop/queue/patient/123
     * Encola un paciente para envío a RENHICE
     */
    @RequestMapping(value = "/queue/patient/{patientId}", method = RequestMethod.POST)
    @ResponseBody
    public String queuePatient(@PathVariable("patientId") Integer patientId) {
        
        DyakuSenderService service = Context.getService(DyakuSenderService.class);
        Patient patient = Context.getPatientService().getPatient(patientId);
        
        if (patient == null) {
            return "{\"error\": \"Paciente no encontrado\"}";
        }
        
        try {
            InteropQueueItem item = service.queuePatient(patient);
            return "{\"success\": true, \"queueId\": " + item.getQueueId() + "}";
            
        } catch (InteropException e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
    
    /**
     * POST /module/sihsalusinterop/queue/process
     * Procesa la cola de mensajes pendientes
     */
    @RequestMapping(value = "/queue/process", method = RequestMethod.POST)
    @ResponseBody
    public String processQueue() {
        
        DyakuSenderService service = Context.getService(DyakuSenderService.class);
        int sentCount = service.processQueue();
        
        return "{\"success\": true, \"sent\": " + sentCount + "}";
    }
}
```

### Ejemplo 2: Desde un Event Listener

```java
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.event.Event;
import org.openmrs.event.EventListener;
import org.openmrs.module.sihsalusinterop.api.DyakuSenderService;

public class PatientCreatedListener implements EventListener {
    
    @Override
    public void onEvent(Event event) {
        
        // Solo procesar eventos de creación de pacientes
        if (!"patient.created".equals(event.getAction())) {
            return;
        }
        
        Patient patient = (Patient) event.getProperties().get("patient");
        
        // Encolar automáticamente para envío a RENHICE
        DyakuSenderService service = Context.getService(DyakuSenderService.class);
        
        try {
            service.queuePatient(patient);
            System.out.println("✓ Paciente encolado automáticamente: " + patient.getId());
            
        } catch (Exception e) {
            System.err.println("✗ Error al encolar paciente: " + e.getMessage());
        }
    }
}
```

### Ejemplo 3: Procesamiento Automático con Scheduler

```java
import org.openmrs.api.context.Context;
import org.openmrs.module.sihsalusinterop.api.DyakuSenderService;
import org.openmrs.scheduler.tasks.AbstractTask;

public class InteropQueueProcessorTask extends AbstractTask {
    
    @Override
    public void execute() {
        try {
            DyakuSenderService service = Context.getService(DyakuSenderService.class);
            
            int sentCount = service.processQueue();
            
            if (sentCount > 0) {
                System.out.println("✓ Scheduler: Enviados " + sentCount + " mensajes");
            }
            
        } catch (Exception e) {
            System.err.println("✗ Error en scheduler: " + e.getMessage());
        }
    }
}
```

**Configurar en OpenMRS:**
- Ir a **Administration → Scheduler → Manage Scheduler**
- Crear nueva tarea:
  - **Name:** Process Interop Queue
  - **Class:** `org.openmrs.module.sihsalusinterop.tasks.InteropQueueProcessorTask`
  - **Start Time:** Now
  - **Repeat Interval:** 300 seconds (5 minutos)

---

## 📊 Consultas SQL de Monitoreo

### Ver items en cola
```sql
SELECT 
    queue_id,
    message_type,
    status,
    attempts,
    queued_at,
    target_endpoint
FROM sihsalus_interop_queue
ORDER BY queued_at DESC
LIMIT 20;
```

### Contar por estado
```sql
SELECT 
    status,
    COUNT(*) as cantidad
FROM sihsalus_interop_queue
GROUP BY status;
```

### Ver errores recientes
```sql
SELECT 
    queue_id,
    message_type,
    error_message,
    attempts,
    max_attempts,
    last_attempt_at
FROM sihsalus_interop_queue
WHERE status IN ('ERROR', 'FAILED')
ORDER BY last_attempt_at DESC
LIMIT 10;
```

---

## ✅ Checklist de Implementación

- [x] Entidad `InteropQueueItem` con campos correctos
- [x] DAO `InteropQueueDao` con métodos CRUD
- [x] Servicio `DyakuSenderService` con interfaz completa
- [x] Implementación `DyakuSenderServiceImpl` con lógica de negocio
- [x] Método `queuePatient()` implementado
- [x] Mapper `DyakuPatientMapper` para conversión OpenMRS → FHIR
- [x] Archivo `moduleApplicationContext.xml` con beans registrados
- [x] Manejo de excepciones (`InteropException`)
- [x] Logging detallado en todos los métodos
- [x] Sin errores de linter

---

## 🎯 Próximos Pasos Sugeridos

1. **Crear un controlador REST completo** para gestionar la cola desde UI
2. **Implementar un Scheduler** para procesamiento automático cada 5 minutos
3. **Agregar Global Properties** para configurar:
   - `sihsalus.renhice.endpoint` (URL del servidor FHIR)
   - `sihsalus.queue.maxAttempts` (máximo de reintentos)
   - `sihsalus.queue.retryInterval` (intervalo entre reintentos)
4. **Crear una página de administración** en la UI de OpenMRS para:
   - Ver la cola de mensajes
   - Filtrar por estado
   - Reintentar mensajes manualmente
   - Ver logs de errores
5. **Implementar mappers adicionales**:
   - `DyakuEncounterMapper` (consultas médicas)
   - `DyakuObservationMapper` (resultados de laboratorio)
6. **Agregar tests unitarios** para cada componente

---

**Autor:** Hospital Santa Clotilde - SIH.SALUS Team  
**Fecha:** 21 de Noviembre, 2024  
**Versión:** 1.0.0

