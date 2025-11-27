# Módulo de Interoperabilidad SIH.SALUS

Módulo OpenMRS para interoperabilidad con RENHICE (Registro Nacional de Historias Clínicas Electrónicas) del MINSA Perú, utilizando HL7 FHIR R4 con perfiles Dyaku.

## Hospital Santa Clotilde - Loreto, Perú

---

## Descripción

El Módulo de Interoperabilidad SIH.SALUS permite:
- Envío automático de resúmenes clínicos a RENHICE
- Consulta de información de pacientes desde RENHICE
- Arquitectura offline-first con cola de mensajes y reintentos automáticos
- Mapeo completo de recursos según International Patient Summary (IPS)
- Interfaz web para monitoreo de cola de interoperabilidad

## Características Principales

- **Estándar HL7 FHIR R4**: Implementación completa con perfiles peruanos Dyaku
- **Offline-First**: Cola de mensajes persistente con reintentos automáticos para zonas rurales
- **Mapeo Completo IPS**: Medicamentos, Alergias, Diagnósticos, Procedimientos, Inmunizaciones, Observaciones
- **Seguridad**: TLS 1.2+, control de acceso por privilegios, trazabilidad completa
- **Monitoreo**: Interfaz web para supervisión de mensajes y diagnóstico de errores

## Requisitos

- **OpenMRS Core**: 1.11.6 o superior
- **Java**: JDK 8
- **Maven**: 3.x
- **Base de Datos**: MySQL 5.7+ o compatible
- **Servidor FHIR**: HAPI FHIR R4 (RENHICE/Dyaku)

## Instalación

### 1. Compilar el Módulo

```bash
cd openmrs-module-sihsalusinterop
mvn clean install
```

### 2. Instalar en OpenMRS

Copiar el archivo `omod/target/sihsalusinterop-1.0.0.omod` a OpenMRS:

- **Vía UI**: Administración → Gestionar Módulos → Agregar o Actualizar Módulo
- **Vía Docker**: 
  ```bash
  docker cp sihsalusinterop-1.0.0.omod peruHCE-backend:/openmrs/data/modules/
  docker restart peruHCE-backend
  ```

### 3. Configurar Propiedades Globales

Acceder a: Administración → Mantenimiento → Configuración → Propiedades Globales

Configurar:
- `sihsalusinterop.renhice.endpoint`: URL del servidor FHIR (usar HTTPS en producción)
- `sihsalusinterop.renhice.enabled`: `true` para habilitar envío automático
- `sihsalusinterop.queue.maxRetries`: Número máximo de reintentos (default: 5)
- `sihsalusinterop.queue.retryInterval`: Intervalo entre reintentos en ms (default: 300000)

## Uso

### Envío Automático a RENHICE

1. Médico registra atención de paciente en OpenMRS
2. Al guardar el Encounter, el módulo automáticamente:
   - Construye un Bundle FHIR R4 con todos los recursos clínicos
   - Lo encola para envío a RENHICE
   - Intenta enviarlo en segundo plano

### Consulta de Paciente desde RENHICE

#### Vía API REST

```bash
curl http://localhost/openmrs/ws/rest/v1/interop/patient/12345678
```

#### Vía Interfaz Web

(Próximamente: integración con búsqueda de pacientes de OpenMRS O3)

### Monitoreo de Cola

Acceder a: **Administración → Interoperabilidad SIH.SALUS → Monitoreo de Interoperabilidad**

Permite:
- Ver estado de mensajes (Pendientes, Enviados, Con Error, Fallidos)
- Reintentar mensajes fallidos manualmente
- Inspeccionar payload FHIR completo
- Procesar cola manualmente

## API REST

Base URL: `http://localhost/openmrs/ws/rest/v1/interop`

### Endpoints Principales

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/status` | Estado del módulo y cola |
| GET | `/queue` | Listar mensajes en cola |
| POST | `/processQueue` | Procesar mensajes pendientes |
| POST | `/retry/{id}` | Reintentar mensaje específico |
| GET | `/patient/{dni}` | Consultar paciente en RENHICE |

Ver documentación completa en: [`docs/MANUAL_TECNICO.md`](docs/MANUAL_TECNICO.md)

## Arquitectura

### Componentes

- **Mappers**: Conversión OpenMRS → FHIR R4 (Dyaku profiles)
- **BundleBuilderService**: Construcción de Bundles FHIR
- **DyakuSenderService**: Envío de mensajes a RENHICE
- **EncounterSavedListener**: Detección de eventos de OpenMRS
- **InteropQueue**: Cola persistente en BD local
- **Scheduled Task**: Procesamiento periódico de cola

### Flujo de Datos

```
Encounter guardado → EventListener → BundleBuilder → Cola BD → Scheduled Task → RENHICE
                                                          ↓
                                                       (Retry si falla)
```

## Mapeo de Recursos FHIR

| Recurso FHIR | Perfil Dyaku | Origen OpenMRS |
|--------------|--------------|----------------|
| Patient | PacientePe | Patient |
| Organization | OrganizacionPe | Location |
| Practitioner | PractitionerPe | User |
| Encounter | EncounterPe | Encounter |
| Condition | ConditionPe | Obs (Diagnosis) |
| AllergyIntolerance | AlergiaPe | Allergy |
| MedicationStatement | MedicationStatementPe | DrugOrder |
| Procedure | ProcedurePe | Order |
| Observation | ObservationPe | Obs |
| Immunization | InmunizacionPe | Obs (Vaccine) |

## Seguridad y Conformidad

El módulo cumple con:
- **Ley N° 29733**: Protección de Datos Personales (Perú)
- **ISO/IEC 27001**: Sistema de Gestión de Seguridad de la Información
- **HL7 FHIR Security Guidelines**: Seguridad en implementaciones FHIR

Medidas implementadas:
- Cifrado TLS 1.2+ en tránsito
- Control de acceso basado en privilegios
- Trazabilidad completa de operaciones
- Validación de recursos FHIR

Ver: [`docs/SEGURIDAD_Y_CONFORMIDAD.md`](docs/SEGURIDAD_Y_CONFORMIDAD.md)

## Documentación

- [Manual Técnico](docs/MANUAL_TECNICO.md): Guía completa de instalación, configuración y uso
- [Pruebas de Interoperabilidad](docs/PRUEBAS_INTEROPERABILIDAD.md): Procedimientos de testing
- [Seguridad y Conformidad](docs/SEGURIDAD_Y_CONFORMIDAD.md): Normativa y medidas de seguridad

## Troubleshooting

### Error: "Failed to retrieve server metadata"

**Solución**: Verificar que:
1. El servidor HAPI FHIR esté ejecutándose
2. La propiedad global `sihsalusinterop.renhice.endpoint` sea correcta
3. Si usan Docker, usar nombre de contenedor (no `localhost`)

### Mensajes quedan en estado ERROR

**Solución**: 
1. Ver `errorMessage` en interfaz de monitoreo
2. Revisar logs: `docker logs peruHCE-backend`
3. Reintentar manualmente desde interfaz

Ver sección completa de Troubleshooting en: [`docs/MANUAL_TECNICO.md`](docs/MANUAL_TECNICO.md#9-mantenimiento-y-troubleshooting)

## Pruebas

### Ambiente de Pruebas

Se incluye configuración de HAPI FHIR JPA Server como simulador de RENHICE/Dyaku en: `../hapi-fhir-jpaserver-starter/`

### Poblar Datos de Prueba

```bash
cd ../hapi-fhir-jpaserver-starter
py populate_test_data.py
```

### Ejecutar Pruebas

Ver: [`docs/PRUEBAS_INTEROPERABILIDAD.md`](docs/PRUEBAS_INTEROPERABILIDAD.md)

## Desarrollo

### Estructura del Proyecto

```
openmrs-module-sihsalusinterop/
├── api/                    # Lógica de negocio
│   ├── mapper/            # Mappers OpenMRS → FHIR
│   ├── model/             # InteropQueueItem
│   ├── service/           # Servicios principales
│   ├── listener/          # Event listeners
│   └── advice/            # AOP interceptors
├── omod/                   # Módulo web
│   ├── web/controller/    # REST controllers
│   ├── resources/         # CSS, JS
│   └── pages/             # JSP
└── docs/                   # Documentación
```

### Compilación

```bash
mvn clean install
```

### Ejecutar Tests

```bash
mvn test
```

## Licencia

Este módulo es parte del proyecto SIH.SALUS del Hospital Santa Clotilde y se desarrolla como trabajo de tesis de Ingeniería Informática.

## Contacto

- **Hospital**: Hospital Santa Clotilde, Loreto, Perú
- **Equipo**: SIH.SALUS Development Team
- **Email**: [Contacto Institucional]

## Referencias

- [HL7 FHIR R4 Documentation](https://hl7.org/fhir/R4/)
- [Dyaku MINSA Perú](https://dyaku.minsa.gob.pe/fhir)
- [OpenMRS Developer Guide](https://guide.openmrs.org/)
- [Open Concept Lab](https://openconceptlab.org/)

---

**Versión**: 1.0.0  
**Última Actualización**: Noviembre 2025
