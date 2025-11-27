# Seguridad y Conformidad SGSI - Módulo de Interoperabilidad SIH.SALUS

## Hospital Santa Clotilde

---

## 1. Marco Normativo

### 1.1. Normativa Peruana
- **Ley N° 29733**: Ley de Protección de Datos Personales
- **Decreto Supremo N° 003-2013-JUS**: Reglamento de la Ley de Protección de Datos Personales
- **Ley N° 30024**: Ley que crea el Registro Nacional de Historias Clínicas Electrónicas (RENHICE)
- **Resolución Ministerial N° 659-2021/MINSA**: Norma Técnica de Salud para la Implementación del RENHICE

### 1.2. Estándares Internacionales
- **ISO/IEC 27001:2013**: Sistema de Gestión de Seguridad de la Información (SGSI)
- **HL7 FHIR Security**: Guías de seguridad para implementaciones FHIR
- **OWASP Top 10**: Principales riesgos de seguridad en aplicaciones web

---

## 2. Medidas de Seguridad Implementadas

### 2.1. Cifrado de Transporte (TLS 1.2+)

#### Responsabilidad del Servidor RENHICE
El módulo de interoperabilidad actúa como **cliente FHIR**, conectándose al servidor RENHICE/Dyaku. Por tanto:

- El **cifrado TLS 1.2 o superior** es responsabilidad del servidor RENHICE
- El módulo soporta conexiones HTTPS cuando el endpoint lo proporciona
- En producción, **DEBE** utilizarse el endpoint HTTPS oficial del MINSA

#### Configuración en Producción
```xml
<globalProperty>
    <property>sihsalusinterop.renhice.endpoint</property>
    <value>https://renhice.minsa.gob.pe/fhir</value>
</globalProperty>
```

**IMPORTANTE**: Nunca utilizar HTTP en producción. Solo usar HTTP en entornos de desarrollo/prueba locales.

#### Validación de Certificados
El cliente HAPI FHIR (utilizado por el módulo) valida automáticamente:
- Certificados SSL/TLS del servidor
- Cadena de confianza de la CA
- Revocación de certificados (OCSP/CRL)

### 2.2. Control de Acceso

#### Autenticación y Autorización
El módulo se integra con el sistema de privilegios de OpenMRS:

| Privilegio | Descripción | Usuarios Autorizados |
|------------|-------------|---------------------|
| `View Interop Queue` | Ver estado de la cola de mensajes | Administradores, Personal IT |
| `Manage Interop Queue` | Gestionar mensajes (reintentar, eliminar) | Administradores |
| `Send FHIR Messages` | Enviar mensajes FHIR a RENHICE | Médicos, Enfermeras |
| `Generate FUA` | Generar documentos FUA | Personal Administrativo |

#### Implementación
```java
@OpenmrsProfile(openmrsVersion = "1.11.6")
@Secured("View Interop Queue")
public class DyakuSubmissionController {
    // ...
}
```

### 2.3. Trazabilidad y Auditoría

#### Registro de Eventos
Todos los eventos de interoperabilidad son registrados:

- Creación de mensajes FHIR
- Intentos de envío (exitosos y fallidos)
- Consultas a RENHICE
- Modificaciones a la cola de mensajes

#### Metadatos de Auditoría
Cada mensaje en la cola (`InteropQueueItem`) almacena:
- `creator`: Usuario que creó el mensaje
- `dateCreated`: Fecha/hora de creación
- `changedBy`: Usuario que modificó el mensaje
- `dateChanged`: Fecha/hora de última modificación
- `attempts`: Número de intentos de envío
- `lastAttemptAt`: Fecha/hora del último intento
- `errorMessage`: Detalles de errores

### 2.4. Protección de Datos Personales

#### Minimización de Datos
El módulo solo envía datos **estrictamente necesarios** para la interoperabilidad clínica:
- Identificación del paciente (DNI, datos demográficos)
- Información clínica relevante (diagnósticos, medicamentos, alergias)
- NO se envían datos innecesarios o sensibles fuera del contexto clínico

#### Anonimización NO Aplicable
En el contexto de RENHICE, **no se aplica anonimización** ya que:
- RENHICE es el sistema nacional oficial de HCE
- Requiere identificación completa del paciente para cumplir su función
- La anonimización comprometería la integridad y utilidad de la HCE

Sin embargo, se implementan:
- **Pseudonimización**: Uso de UUIDs para referencias internas
- **Control de acceso estricto**: Solo personal autorizado accede a los datos
- **Cifrado en tránsito**: TLS 1.2+ en producción

### 2.5. Disponibilidad y Resiliencia

#### Arquitectura Offline-First
- Cola de mensajes local con reintentos automáticos
- Almacenamiento temporal en base de datos local
- Procesamiento asíncrono para no bloquear operaciones clínicas

#### Recuperación ante Fallos
- Máximo de reintentos configurable (`sihsalusinterop.queue.maxRetries`)
- Intervalo de reintento configurable (`sihsalusinterop.queue.retryInterval`)
- Estados de mensaje: PENDING, PROCESSING, SENT, ERROR, FAILED

### 2.6. Integridad de Datos

#### Validación de Recursos FHIR
- Validación contra perfiles Dyaku (PacientePe, ConditionPe, etc.)
- Verificación de códigos CIE-10 con formato válido
- Detección de mapeos faltantes con warnings en logs

#### Transacciones Atómicas
- Uso de `Bundle.type = TRANSACTION` para envíos a RENHICE
- Garantiza atomicidad: todas las operaciones se completan o ninguna

---

## 3. Gestión de Riesgos

### 3.1. Riesgos Identificados y Mitigaciones

| Riesgo | Impacto | Mitigación |
|--------|---------|-----------|
| Intercepción de datos en tránsito | ALTO | TLS 1.2+ obligatorio en producción |
| Acceso no autorizado a datos | ALTO | Sistema de privilegios de OpenMRS |
| Pérdida de mensajes | MEDIO | Cola persistente con reintentos |
| Duplicación de pacientes en RENHICE | MEDIO | Conditional creates (ifNoneExist) |
| Errores de mapeo CIE-10 | MEDIO | Validación y logs de advertencia |
| Caída del servidor RENHICE | BAJO | Cola offline-first con reintentos |

### 3.2. Plan de Respuesta a Incidentes

#### Incidente: Fallo en Conexión con RENHICE
1. El mensaje se marca como ERROR automáticamente
2. Se programa reintento automático según configuración
3. Personal IT es notificado si se alcanza el máximo de reintentos
4. Se investiga la causa (red, servidor RENHICE, credenciales)

#### Incidente: Duplicación de Datos
1. Uso de `ifNoneExist` previene duplicaciones
2. Si ocurre, identificar la causa (cambio en identificador, error en RENHICE)
3. Contactar con soporte técnico del MINSA si es necesario

---

## 4. Conformidad con ISO 27001

### 4.1. Controles Implementados

#### A.9: Control de Acceso
- **A.9.1.1**: Política de control de acceso mediante privilegios de OpenMRS
- **A.9.2.1**: Gestión de privilegios de usuario
- **A.9.4.1**: Restricción de acceso a información

#### A.10: Criptografía
- **A.10.1.1**: Política sobre uso de controles criptográficos (TLS 1.2+)
- **A.10.1.2**: Gestión de claves (certificados SSL/TLS)

#### A.12: Seguridad de las Operaciones
- **A.12.4.1**: Registro de eventos (logs de auditoría)
- **A.12.4.3**: Logs de administrador y operador

#### A.14: Adquisición, Desarrollo y Mantenimiento
- **A.14.2.5**: Principios de ingeniería de sistemas seguros
- **A.14.2.8**: Pruebas de seguridad del sistema

---

## 5. Recomendaciones para Producción

### 5.1. Configuración de Red
- Utilizar VPN o red privada para conexión con RENHICE
- Implementar firewall con reglas restrictivas (solo salida a RENHICE)
- Monitorear tráfico de red hacia endpoints externos

### 5.2. Gestión de Credenciales
- Almacenar tokens/credenciales en variables de entorno
- Rotar credenciales periódicamente
- Usar secretos de Docker/Kubernetes en contenedores

### 5.3. Monitoreo y Alertas
- Configurar alertas para mensajes en estado FAILED
- Monitorear tamaño de la cola de mensajes
- Establecer SLA para envío de mensajes (ej: 95% enviados en < 5 min)

### 5.4. Respaldo y Recuperación
- Respaldo diario de la base de datos (incluye cola de mensajes)
- Procedimiento documentado para recuperación ante desastres
- Pruebas periódicas de restauración

---

## 6. Cumplimiento de la Ley N° 29733

### 6.1. Principios de Protección de Datos Personales

#### Principio de Legalidad (Art. 4)
- El tratamiento de datos se realiza en cumplimiento de la Ley N° 30024 (RENHICE)
- Base legal: obligación legal del establecimiento de salud

#### Principio de Consentimiento (Art. 5)
- NO se requiere consentimiento expreso del paciente para envío a RENHICE
- Base: obligación legal establecida por norma con rango de ley

#### Principio de Finalidad (Art. 6)
- Finalidad: Interoperabilidad de HCE a nivel nacional
- Datos utilizados exclusivamente para fines de atención en salud

#### Principio de Proporcionalidad (Art. 7)
- Solo se envían datos necesarios para la HCE
- Perfiles FHIR Dyaku definen el conjunto mínimo de datos

#### Principio de Calidad (Art. 8)
- Datos exactos, actualizados y veraces
- Validación de formatos (CIE-10, DNI)

#### Principio de Seguridad (Art. 9)
- Medidas técnicas y organizativas implementadas (ver Sección 2)
- Cifrado TLS 1.2+, control de acceso, trazabilidad

#### Principio de Disposición de Recurso (Art. 10)
- El paciente puede ejercer derechos ARCO a través del establecimiento de salud
- Procedimiento definido en el Reglamento Interno

#### Principio de Nivel de Protección Adecuado (Art. 11)
- RENHICE (destinatario) cuenta con medidas de seguridad equivalentes
- Sistema del MINSA cumple con normativa sectorial

---

## 7. Contacto y Soporte

Para consultas sobre seguridad y conformidad:
- **Oficial de Seguridad de la Información**: [Nombre] - [email]
- **Responsable de Datos Personales**: [Nombre] - [email]
- **Soporte Técnico**: Hospital Santa Clotilde - IT Department

---

**Versión**: 1.0.0  
**Fecha**: Noviembre 2025  
**Próxima Revisión**: Anual o ante cambios normativos significativos

