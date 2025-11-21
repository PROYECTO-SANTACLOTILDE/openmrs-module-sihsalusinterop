# SIH SALUS Interoperability Module

## Descripción

Módulo de Interoperabilidad para **OpenMRS** desarrollado para el **Hospital Santa Clotilde** (Loreto, Perú). 

Este módulo implementa conectividad **FHIR R4** con los sistemas nacionales de salud del Perú:
- **RENHICE** (Registro Nacional de Historias Clínicas Electrónicas) - Vía FHIR R4 con perfiles Dyaku
- **SETI-SIS** (Sistema de Intercambio Telemático) - Para envío de Fichas Únicas de Atención (FUA)

## Contexto del Proyecto

El Hospital Santa Clotilde se encuentra en una zona rural de la selva amazónica peruana, con conectividad satelital **intermitente**. Por esta razón, el módulo implementa una arquitectura **Offline-First** con:

- **Cola de Mensajes Persistente**: Los mensajes se guardan localmente en PostgreSQL
- **Reintentos Automáticos**: Sistema de reintentos configurable (default: 5 intentos)
- **Manejo Robusto de Errores**: Diferencia entre errores temporales (sin conexión) y permanentes
- **REST API**: Endpoints para encolar, consultar y gestionar mensajes

## Arquitectura

### Módulos:
1. **API** (`sihsalusinterop-api`): Lógica de negocio, servicios, DAOs, y modelos
2. **OMOD** (`sihsalusinterop-omod`): Controladores REST y componentes web

### Tecnologías:
- **OpenMRS 2.4.x**
- **HAPI FHIR 6.2.4** (Cliente FHIR R4)
- **Spring Framework** (IoC, Transactions)
- **Hibernate/JPA** (Persistencia)
- **Liquibase** (Migraciones de BD)

## Instalación

### Prerrequisitos:
- OpenMRS 2.4.2+
- Java 8+
- Maven 3.6+
- PostgreSQL 12+ (recomendado)

### Construcción:

```bash
# Clonar el repositorio
git clone https://github.com/PROYECTO-SANTACLOTILDE/openmrs-module-sihsalusinterop.git
cd openmrs-module-sihsalusinterop

# Compilar el módulo
mvn clean package

# El archivo .omod se generará en:
# omod/target/sihsalusinterop-1.0.0.omod
```

### Instalación en OpenMRS:

1. Copiar el archivo `.omod` generado a la carpeta de módulos de OpenMRS
2. Reiniciar OpenMRS
3. El módulo creará automáticamente la tabla `sihsalus_interop_queue` en la BD
4. Verificar en los logs: `"SIH SALUS Interoperability Module STARTED"`

## 📡 Endpoints REST API

Base URL: `/openmrs/ws/rest/v1/interop/`

### 1. Encolar Mensaje FHIR

**POST** `/openmrs/ws/rest/v1/interop/send`

```json
{
  "messageType": "FHIR_BUNDLE",
  "payload": "{...JSON del Bundle FHIR...}",
  "targetEndpoint": "http://localhost:8080/fhir"
}
```

**Respuesta:**
```json
{
  "success": true,
  "queueId": 123,
  "status": "PENDING",
  "message": "Mensaje encolado exitosamente"
}
```

### 2. Procesar Cola Manualmente

**POST** `/openmrs/ws/rest/v1/interop/processQueue`

```json
{
  "success": true,
  "sentCount": 5,
  "message": "Se procesaron 5 mensajes exitosamente"
}
```

### 3. Consultar Estado de la Cola

**GET** `/openmrs/ws/rest/v1/interop/queue`

**GET** `/openmrs/ws/rest/v1/interop/queue/{id}`

### 4. Reintentar Envío

**POST** `/openmrs/ws/rest/v1/interop/queue/{id}/retry`

### 5. Eliminar Item de Cola

**DELETE** `/openmrs/ws/rest/v1/interop/queue/{id}`

### 6. Health Check

**GET** `/openmrs/ws/rest/v1/interop/status`

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

## Entorno de Desarrollo

### Servidor HAPI FHIR (Simulador)

Para desarrollo local, levanta un servidor HAPI FHIR que simula RENHICE:

```bash
docker run -p 8080:8080 hapiproject/hapi:latest
```

El módulo se conectará por defecto a `http://localhost:8080/fhir`.

### Ejemplo de Uso:

```bash
# 1. Encolar un Bundle FHIR
curl -X POST http://localhost:8080/openmrs/ws/rest/v1/interop/send \
  -H "Content-Type: application/json" \
  -d '{
    "messageType": "FHIR_BUNDLE",
    "payload": "{\"resourceType\":\"Bundle\",\"type\":\"transaction\"}",
    "targetEndpoint": "http://localhost:8080/fhir"
  }'

# 2. Procesar la cola manualmente
curl -X POST http://localhost:8080/openmrs/ws/rest/v1/interop/processQueue

# 3. Verificar estado
curl http://localhost:8080/openmrs/ws/rest/v1/interop/status
```

## Estructura del Proyecto

```
openmrs-module-sihsalusinterop/
├── api/                                    # Módulo API (Backend)
│   ├── src/main/java/
│   │   └── org/openmrs/module/sihsalusinterop/
│   │       ├── SihSalusInteropActivator.java
│   │       └── api/
│   │           ├── DyakuSenderService.java          # Interface del servicio
│   │           ├── dao/
│   │           │   └── InteropQueueDao.java         # DAO para cola
│   │           ├── impl/
│   │           │   └── DyakuSenderServiceImpl.java  # Implementación (FHIR Client)
│   │           └── model/
│   │               └── InteropQueueItem.java        # Entidad JPA
│   └── src/main/resources/
│       ├── liquibase.xml                   # Schema BD
│       ├── moduleApplicationContext.xml    # Configuración Spring
│       └── messages_es.properties          # i18n Español
├── omod/                                   # Módulo OMOD (Web/REST)
│   ├── src/main/java/
│   │   └── org/openmrs/module/sihsalusinterop/web/
│   │       └── controller/
│   │           └── DyakuSubmissionController.java   # REST Controller
│   └── src/main/resources/
│       ├── config.xml                      # Configuración del módulo
│       └── webModuleApplicationContext.xml # Configuración Spring Web
└── pom.xml                                 # POM raíz
```

## Permisos/Privilegios

El módulo define los siguientes privilegios:

- **SIH SALUS Interop Privilege**: Acceso general al módulo
- **Manage Interop Queue**: Encolar y gestionar mensajes
- **Send FHIR Messages**: Enviar mensajes FHIR a RENHICE
- **Generate FUA**: Generar y enviar documentos FUA
- **View Interop Logs**: Ver logs y estado de la cola

## Base de Datos

### Tabla: `sihsalus_interop_queue`

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `queue_id` | INT (PK) | ID del item en cola |
| `message_type` | VARCHAR(50) | FHIR_BUNDLE o FUA_DOCUMENT |
| `payload` | MEDIUMTEXT | JSON/XML del mensaje |
| `status` | VARCHAR(20) | PENDING, PROCESSING, SENT, ERROR, FAILED |
| `attempts` | INT | Número de intentos realizados |
| `max_attempts` | INT | Máximo de reintentos (default: 5) |
| `queued_at` | DATETIME | Fecha de encolamiento |
| `last_attempt_at` | DATETIME | Fecha del último intento |
| `sent_at` | DATETIME | Fecha de envío exitoso |
| `error_message` | TEXT | Mensaje de error (si aplica) |
| `target_endpoint` | VARCHAR(500) | URL del endpoint de destino |
| `external_resource_id` | VARCHAR(255) | ID del recurso en sistema externo |

## Contribuir

Este proyecto es parte de la tesis de grado del **Sistema de Información Hospitalario SIH.SALUS**.

Para contribuir:
1. Fork el repositorio
2. Crea una rama feature (`git checkout -b feature/nueva-funcionalidad`)
3. Commit tus cambios (`git commit -am 'Agregar funcionalidad X'`)
4. Push a la rama (`git push origin feature/nueva-funcionalidad`)
5. Abre un Pull Request

## Licencia

Este proyecto está licenciado bajo **Mozilla Public License 2.0** (MPL-2.0).

## Equipo

- **Hospital Santa Clotilde** - Loreto, Perú
- **Proyecto SIH.SALUS** - Sistema de Información Hospitalario

## Soporte

Para consultas o soporte, contactar al equipo del proyecto SIH.SALUS.

---

**Versión:** 1.0.0  
**Última Actualización:** Noviembre 2025  
**Ubicación:** Hospital Santa Clotilde, Nauta, Loreto, Perú 🇵🇪
