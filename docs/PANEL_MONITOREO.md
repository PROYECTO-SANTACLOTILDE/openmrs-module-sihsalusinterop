# Panel de Monitoreo de Interoperabilidad - SIH.SALUS

## Ubicación de los Archivos

### Backend (Java)
- **Controlador**: `omod/src/main/java/org/openmrs/module/sihsalusinterop/web/controller/MonitoreoInteroperabilidadController.java`
- **Extension**: `omod/src/main/java/org/openmrs/module/sihsalusinterop/extension/html/AdminList.java`

### Frontend (JSP)
- **Vista Principal**: `omod/src/main/webapp/WEB-INF/view/module/sihsalusinterop/monitoreoInteroperabilidad.jsp`
- **Estilos CSS**: `omod/src/main/webapp/moduleResources/sihsalusinterop/css/monitoreo.css`

### Configuración
- **Mensajes**: `omod/src/main/resources/messages.properties` y `messages_es.properties`
- **Config.xml**: `omod/src/main/resources/config.xml`

## Acceso al Panel

1. **URL**: `/openmrs/module/sihsalusinterop/monitoreoInteroperabilidad.form`
2. **Privilegio requerido**: `View Interop Queue`
3. **Menú**: Aparece en el panel de administración bajo "Interoperabilidad SIH.SALUS"

## Funcionalidades

- ✅ Visualización de mensajes en cola (PENDING, SENT, ERROR, FAILED)
- ✅ Resumen de estados con contadores
- ✅ Tabla con detalles de cada mensaje
- ✅ Ver detalles completos del mensaje (payload FHIR)
- ✅ Botón para reintentar mensajes fallidos
- ✅ Botón para procesar cola manualmente
- ✅ Auto-refresh cada 30 segundos
- ✅ Manejo de errores con logs en consola

## API REST Esperada

El panel consume los siguientes endpoints:

- `GET /ws/rest/v1/interop/queue` - Lista todos los mensajes
- `POST /ws/rest/v1/interop/processQueue` - Procesa la cola manualmente
- `POST /ws/rest/v1/interop/queue/{id}/retry` - Reintenta un mensaje específico

## Compilación y Despliegue

```bash
# Compilar el módulo
mvn clean install

# El archivo .omod se genera en:
# omod/target/sihsalusinterop-1.0.0.omod
```

## Solución de Problemas

### El panel no carga datos
1. Verificar que el módulo REST esté configurado
2. Abrir la consola del navegador (F12) y verificar errores
3. Verificar que la URL del API sea correcta

### Error 404
1. Verificar que el módulo esté instalado correctamente
2. Verificar que tengas el privilegio `View Interop Queue`
3. Limpiar caché del navegador

### Los estilos no se aplican
1. Verificar que `monitoreo.css` exista en `moduleResources/sihsalusinterop/css/`
2. Limpiar la carpeta `target/` y recompilar

## Próximos Pasos

- [ ] Implementar los endpoints REST en el backend
- [ ] Agregar paginación si hay muchos mensajes
- [ ] Agregar filtros por estado y tipo de mensaje
- [ ] Agregar gráficos de estadísticas
