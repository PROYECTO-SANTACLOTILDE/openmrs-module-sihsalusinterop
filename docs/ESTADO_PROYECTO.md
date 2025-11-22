# Estado del Proyecto: SIH SALUS Interoperability Module

**Fecha de Actualización:** Noviembre 2025  
**Autor:** Johan Amador - SIH.SALUS Fase 2  
**Versión del Módulo:** 1.0.0  
**Estado:** ✅ Compilación Exitosa | 🟡 Funcionalidad Parcial

---

## 📋 Resumen Ejecutivo

El módulo `sihsalusinterop` para OpenMRS está **compilando correctamente** y tiene implementada la **infraestructura base** para interoperabilidad FHIR R4 con sistemas nacionales peruanos (RENHICE, simulados por HAPI FHIR Server).

### ✅ Lo que Funciona

1. **Compilación exitosa** con Maven (`mvn clean package`)
2. **Infraestructura de cola persistente** (tabla `interop_queue_item` en BD)
3. **REST API básica** para encolar mensajes y consultar estado
4. **Sistema de reintentos automáticos** con scheduler cada 5 minutos
5. **Consultas de pacientes** desde servidor FHIR simulado
6. **Mapeadores FHIR parciales** (Patient, Organization, Practitioner, Encounter, Condition)

### ⚠️ Lo que Falta

1. **Mapeadores FHIR completos** con perfiles peruanos (Dyaku)
2. **Bundle Builder funcional** (construcción automática de Bundles)
3. **Event Listener activo** (detección automática de Encounter guardados)
4. **Validación de perfiles Dyaku** (StructureDefinition)
5. **Mapeo de terminologías nacionales** (CIE-10, CPMS, UBIGEO)
6. **Frontend O3** para visualizar resultados

---

## 🏗️ Arquitectura Implementada

### Estructura del Módulo

```
openmrs-module-sihsalusinterop/
├── api/                          # Módulo API (lógica de negocio)
│   ├── dao/                      # Data Access Objects
│   ├── dto/                      # Data Transfer Objects (evitar recursión JSON)
│   ├── mapper/                   # Mapeadores OpenMRS → FHIR
│   ├── service/                  # Servicios de negocio
│   ├── model/                    # Entidades JPA
│   ├── listener/                 # Event Listeners (parcial)
│   └── tasks/                    # Scheduled Tasks
├── omod/                         # Módulo OMOD (controladores REST)
│   └── web/controller/           # REST Controllers
└── docs/                         # Documentación
```

### Componentes Principales Implementados

#### 1. **InteropQueueItem** (Modelo de Datos)
- ✅ Tabla creada con Liquibase
- ✅ Persistencia con Hibernate XML mapping
- ✅ Estados: PENDING, PROCESSING, SENT, ERROR, FAILED
- ✅ Sistema de reintentos (maxAttempts)

#### 2. **DyakuSenderService** (Servicio Principal)
- ✅ Encolar mensajes (`queueMessage()`)
- ✅ Procesar cola (`processQueue()`)
- ✅ Envío individual de mensajes
- ✅ Manejo de errores y reintentos
- ✅ Retorna estadísticas (sentCount, processedCount)

#### 3. **REST API Controllers**
- ✅ `DyakuSubmissionController`: Endpoints principales
  - `POST /send` - Encolar mensaje
  - `POST /processQueue` - Procesar cola manualmente
  - `GET /queue` - Consultar cola completa
  - `GET /queue/{id}` - Consultar item específico
  - `POST /queue/{id}/retry` - Reintentar item
  - `DELETE /queue/{id}` - Eliminar item
  - `GET /patient/{identifier}` - Consultar paciente desde RENHICE
  - `GET /status` - Health check

- ✅ `InteropQueueController`: Controlador alternativo para cola

#### 4. **Scheduled Tasks**
- ✅ `QueueProcessorTask`: Procesa cola automáticamente cada 5 minutos
- ✅ Registrado automáticamente en `SihSalusInteropActivator`

#### 5. **Mapeadores FHIR** (Parciales)
- ✅ `DyakuPatientMapper`: OpenMRS Patient → FHIR Patient (básico)
- ✅ `DyakuOrganizationMapper`: OpenMRS Location → FHIR Organization (básico)
- ✅ `DyakuPractitionerMapper`: OpenMRS User → FHIR Practitioner (básico)
- ✅ `DyakuEncounterMapper`: OpenMRS Encounter → FHIR Encounter (básico)
- ✅ `DyakuConditionMapper`: OpenMRS Obs → FHIR Condition (básico)

#### 6. **BundleBuilderService** (Parcial)
- ✅ Estructura básica implementada
- ⚠️ No está completamente funcional
- ⚠️ No se registra como servicio en Spring (instanciación manual)

#### 7. **Event Listeners** (No Activo)
- ⚠️ `EncounterSavedListener`: Creado pero no registrado
- ⚠️ No se detectan automáticamente los Encounter guardados

---

## ❌ Lo que Falta Implementar

### 1. Mapeadores FHIR Completos con Perfiles Dyaku

**Problema:** Los mapeadores actuales generan recursos FHIR genéricos, no conforman a los perfiles peruanos (Dyaku).

**Pendiente:**
- [ ] Agregar `Meta.profile` con URLs de perfiles peruanos:
  - `PacientePe`: `https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/PacientePe`
  - `OrganizacionPe`: `https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/OrganizacionPe`
  - `PractitionerPe`: `https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/PractitionerPe`
  - `ConditionPe`: `https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/ConditionPe`
  - `BundlePe`: `https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/BundlePe`
  - `CompositionPe`: `https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/CompositionPe`

- [ ] **DyakuPatientMapper:**
  - [ ] Identificadores con OID correcto: `urn:oid:2.16.840.1.113883.4.904` (DNI)
  - [ ] Extensión `pe-tercerapellido` si aplica
  - [ ] Mapeo de dirección con extensión `pe-ubigeo`
  - [ ] Mapeo de etnia y otros atributos culturales

- [ ] **DyakuOrganizationMapper:**
  - [ ] Identificador RENIPRESS obligatorio
  - [ ] Tipo de organización desde CodeSystem IPRESSCS
  - [ ] Dirección con UBIGEO (extensión `pe-ubigeo`)

- [ ] **DyakuPractitionerMapper:**
  - [ ] Identificador DNI obligatorio
  - [ ] Calificaciones (CMP, RNE) desde CodeSystem ColegiosProfesionalesSaludCS
  - [ ] Mapeo desde Provider/PersonAttributes

- [ ] **DyakuConditionMapper:**
  - [ ] Código CIE-10 obligatorio (no solo placeholder)
  - [ ] Mapeo real de Concept de OpenMRS a códigos CIE-10
  - [ ] Verificación de ConceptSource/Mapping

- [ ] **Mapeadores Faltantes:**
  - [ ] `DyakuAllergyIntoleranceMapper`: Obs → AllergyIntolerance (perfil AlergiaPe)
  - [ ] `DyakuMedicationStatementMapper`: DrugOrder → MedicationStatement (perfil MedicationStatementPe)
  - [ ] `DyakuObservationMapper`: Obs → Observation (para signos vitales, laboratorios)
  - [ ] `DyakuProcedureMapper`: Procedure → Procedure (con CPMS)
  - [ ] `DyakuCoverageMapper`: Para seguros/SIS

### 2. BundleBuilderService Funcional

**Problema:** El servicio existe pero no está completamente implementado ni registrado correctamente.

**Pendiente:**
- [ ] Completar `buildClinicalSummaryBundle(Encounter)`:
  - [ ] Construir Bundle tipo "document" con perfil BundlePe
  - [ ] Incluir CompositionPe como recurso raíz
  - [ ] Agregar todos los recursos requeridos:
    - Patient (PacientePe) ✅
    - Organization (OrganizacionPe) ✅
    - Practitioner (PractitionerPe) ✅
  - [ ] Encounter ✅
  - [ ] Conditions (diagnósticos CIE-10) ✅
  - [ ] Observations (signos vitales, laboratorios) ❌
  - [ ] Procedures (CPMS) ❌
  - [ ] AllergyIntolerance ❌
  - [ ] MedicationStatement ❌
  - [ ] Coverage (seguros) ❌

- [ ] Registrar `BundleBuilderService` como bean de Spring en `moduleApplicationContext.xml`:
  ```xml
  <bean id="sihsalusinterop.BundleBuilderService"
        class="org.openmrs.module.sihsalusinterop.api.service.BundleBuilderService"/>
  ```

- [ ] Agregar método para construir Bundle de resumen completo (no solo desde Encounter)

### 3. Event Listener Activo

**Problema:** El `EncounterSavedListener` existe pero no está registrado en OpenMRS.

**Pendiente:**
- [ ] Registrar el listener en el sistema de eventos de OpenMRS
- [ ] Opción 1: Usar Spring AOP (@Aspect) en el EncounterService
- [ ] Opción 2: Registrar en `SihSalusInteropActivator.started()` usando el `Context.addAdvice()`
- [ ] Opción 3: Usar Module Advice (recomendado para OpenMRS)
  ```java
  // Ejemplo en SihSalusInteropActivator
  Context.addAdvice(new EncounterSavedAdvice());
  ```

- [ ] Validar que el listener no bloquee el guardado del Encounter (usar hilos asíncronos)

### 4. Mapeo de Terminologías Nacionales

**Problema:** Los mapeadores usan placeholders o UUIDs en lugar de códigos reales CIE-10, CPMS, etc.

**Pendiente:**
- [ ] **CIE-10 (Diagnósticos):**
  - [ ] Crear ConceptSource con source="ICD-10" o "CIE-10"
  - [ ] Crear ConceptMap para mapear Concept de OpenMRS → CIE-10
  - [ ] O usar atributos/campos personalizados en Concept
  - [ ] Implementar lógica en `DyakuConditionMapper` para obtener código CIE-10

- [ ] **CPMS (Procedimientos):**
  - [ ] Similar a CIE-10, crear ConceptSource/ConceptMap
  - [ ] Implementar en `DyakuProcedureMapper`

- [ ] **UBIGEO:**
  - [ ] Mapear desde LocationAttribute "UBIGEO" (ya implementado parcialmente)
  - [ ] Validar formato de 6 dígitos
  - [ ] Agregar extensión `pe-ubigeo` en Address

- [ ] **Colegios Profesionales:**
  - [ ] Mapear desde PersonAttribute o Provider
  - [ ] Usar códigos del CodeSystem ColegiosProfesionalesSaludCS

### 5. Validación de Perfiles Dyaku

**Problema:** No hay validación de que los recursos FHIR generados cumplan con los perfiles peruanos.

**Pendiente:**
- [ ] Cargar StructureDefinitions de Dyaku en el módulo (desde carpeta `dyaku/`)
- [ ] Usar `FhirValidator` de HAPI para validar antes de enviar:
  ```java
  FhirValidator validator = fhirContext.newValidator();
  ValidationResult result = validator.validateWithResult(bundle);
  if (!result.isSuccessful()) {
      // Log warnings/errors
  }
  ```
- [ ] Validar contra perfiles específicos:
  - `StructureDefinition/PacientePe`
  - `StructureDefinition/OrganizacionPe`
  - `StructureDefinition/PractitionerPe`
  - `StructureDefinition/ConditionPe`
  - `StructureDefinition/BundlePe`

### 6. Configuración y Global Properties

**Pendiente:**
- [ ] Agregar Global Properties de OpenMRS:
  - `sihsalusinterop.renhice.endpoint` (default: `http://hapi-fhir-server:8080/fhir`)
  - `sihsalusinterop.renhice.enabled` (true/false)
  - `sihsalusinterop.queue.maxRetries` (default: 5)
  - `sihsalusinterop.queue.retryInterval` (milliseconds)
- [ ] Leer desde `Context.getAdministrationService().getGlobalProperty()`

### 7. Frontend O3 (Opcional pero Recomendado)

**Pendiente:**
- [ ] Crear widgets O3 para:
  - [ ] Ver estado de la cola de interoperabilidad
  - [ ] Consultar pacientes desde RENHICE
  - [ ] Ver historial de mensajes enviados/recibidos
- [ ] Integrar en formularios de Encounter para mostrar datos de RENHICE

---

## 🔧 Problemas Conocidos y Soluciones

### 1. Docker Networking

**Problema:** El contenedor `peruHCE-backend` no puede comunicarse con `hapi-fhir-server` usando `localhost`.

**Solución:** Usar el nombre del contenedor y conectarlos en la misma red Docker:
```bash
# Conectar hapi-fhir-server a la red de OpenMRS
docker network connect sihsalus-distro-referenceapplication_default hapi-fhir-server

# Usar en targetEndpoint:
http://hapi-fhir-server:8080/fhir
```

**Estado:** ✅ Resuelto

### 2. Múltiples liquibase.xml

**Problema:** Liquibase encontraba 2 archivos `liquibase.xml` (uno en omod, otro en api).

**Solución:** Eliminar `api/src/main/resources/liquibase.xml` y dejar solo `omod/src/main/resources/liquibase.xml`.

**Estado:** ✅ Resuelto

### 3. BundleBuilderService no registrado como Spring Bean

**Problema:** Se instanciaba manualmente con `new BundleBuilderService()`.

**Solución:** Agregar bean en `moduleApplicationContext.xml`:
```xml
<bean id="sihsalusinterop.BundleBuilderService"
      class="org.openmrs.module.sihsalusinterop.api.service.BundleBuilderService"/>
```

**Estado:** ⚠️ Pendiente (el bean existe pero falta verificar uso correcto)

### 4. Mapeadores incompletos (Placeholders)

**Problema:** Los mapeadores usan UUIDs o valores placeholder en lugar de códigos reales.

**Estado:** ❌ Pendiente (ver sección "Lo que Falta Implementar")

---

## 📝 Estructura de Archivos Clave

### Archivos Importantes para Continuar

```
api/src/main/java/org/openmrs/module/sihsalusinterop/
├── api/
│   ├── mapper/                           # Mapeadores FHIR (completar)
│   │   ├── DyakuPatientMapper.java       # ⚠️ Completar perfiles
│   │   ├── DyakuOrganizationMapper.java  # ⚠️ Completar perfiles
│   │   ├── DyakuPractitionerMapper.java  # ⚠️ Completar perfiles
│   │   ├── DyakuConditionMapper.java     # ⚠️ CIE-10 real
│   │   └── ...                           # Faltan: Allergy, Medication, Observation, Procedure
│   ├── service/
│   │   └── BundleBuilderService.java     # ⚠️ Completar implementación
│   ├── listener/
│   │   └── EncounterSavedListener.java   # ⚠️ Registrar en OpenMRS
│   └── DyakuSenderService.java           # ✅ Funcional

omod/src/main/java/org/openmrs/module/sihsalusinterop/
└── web/controller/
    └── DyakuSubmissionController.java    # ✅ Funcional

api/src/main/resources/
├── moduleApplicationContext.xml          # ⚠️ Agregar BundleBuilderService bean
└── liquibase.xml                         # ✅ OK (solo en omod/)

dyaku/                                     # ✅ Perfiles Dyaku cargados
├── StructureDefinition.json
├── CodeSystem.json
└── ValueSet.json

docs/
├── GUIA_COMPLETA.md                      # ✅ Documentación principal
└── ESTADO_PROYECTO.md                    # 📄 Este archivo
```

---

## 🚀 Próximos Pasos Recomendados

### Fase 1: Completar Mapeadores FHIR (Prioridad Alta)

1. **Completar DyakuPatientMapper:**
   - Agregar `Meta.profile` con URL PacientePe
   - Implementar mapeo de identificadores con OID correcto
   - Agregar extensiones peruanas (tercer apellido, ubigeo)

2. **Completar DyakuOrganizationMapper:**
   - Agregar perfil OrganizacionPe
   - Mapear RENIPRESS desde LocationAttribute
   - Agregar UBIGEO en dirección

3. **Completar DyakuConditionMapper:**
   - Implementar mapeo real de Concept → CIE-10
   - Agregar perfil ConditionPe
   - Validar que el código CIE-10 existe

### Fase 2: BundleBuilderService Funcional (Prioridad Alta)

1. Registrar BundleBuilderService como bean de Spring
2. Completar `buildClinicalSummaryBundle()` con todos los recursos
3. Agregar CompositionPe como recurso raíz
4. Validar que el Bundle cumple con perfil BundlePe

### Fase 3: Event Listener Activo (Prioridad Media)

1. Implementar Module Advice para registrar listener
2. Probar que detecta Encounter guardados
3. Validar que no bloquea el guardado
4. Integrar con BundleBuilderService

### Fase 4: Mapeo de Terminologías (Prioridad Media)

1. Crear ConceptSource/ConceptMap para CIE-10
2. Implementar lógica de búsqueda en mapeadores
3. Hacer lo mismo para CPMS (procedimientos)

### Fase 5: Validación de Perfiles (Prioridad Baja)

1. Cargar StructureDefinitions desde carpeta `dyaku/`
2. Implementar validación con FhirValidator
3. Log warnings/errors antes de enviar

---

## 📚 Referencias y Contexto

### Documentos de Contexto

- `context/contexto_tesis_sihsalus.md`: Visión general del proyecto y requisitos
- `context/DYAKU_FHIR_CONTEXT.md`: Documentación completa sobre Dyaku FHIR R4
- `docs/GUIA_COMPLETA.md`: Guía de instalación y uso del módulo

### Endpoints Importantes

- **OpenMRS Local:** `http://localhost:8080/openmrs`
- **HAPI FHIR Server (Simulador RENHICE):** `http://localhost:8081/fhir` (externo) o `http://hapi-fhir-server:8080/fhir` (interno Docker)
- **Dyaku Oficial (Referencia):** `https://dyaku.minsa.gob.pe/fhir`

### Comandos Útiles

Ver `docs/commands.txt` para comandos Docker y Maven comunes.

---

## ✅ Checklist para Nueva Conversación

Cuando continúes en una nueva conversación, verifica:

- [ ] ¿Compila correctamente? (`mvn clean package`)
- [ ] ¿El módulo se carga en OpenMRS? (verificar logs)
- [ ] ¿Los endpoints REST funcionan? (probar `/status` en Postman)
- [ ] ¿La cola de mensajes funciona? (encolar un mensaje de prueba)
- [ ] ¿El scheduler está activo? (verificar en logs cada 5 minutos)
- [ ] ¿Se puede consultar pacientes desde RENHICE? (`GET /patient/{dni}`)

---

**Última actualización:** Noviembre 2025  
**Siguiente paso recomendado:** Completar mapeadores FHIR con perfiles Dyaku

