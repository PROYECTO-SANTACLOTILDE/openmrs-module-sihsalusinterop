# Script de Pruebas de Interoperabilidad SIH.SALUS
# Autor: Johan Amador - SIH.SALUS Fase 2
# Hospital Santa Clotilde, Loreto, Perú

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  PRUEBAS DE INTEROPERABILIDAD SIH.SALUS - RENHICE" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

# Configuración
$OPENMRS_BASE = "http://localhost/openmrs/ws/rest/v1/interop"
$HAPI_BASE = "http://localhost:8081/fhir"

# ============================================================
# PRUEBA 1: Health Check del Módulo
# ============================================================
Write-Host "PRUEBA 1: Health Check del Módulo" -ForegroundColor Yellow
Write-Host "Endpoint: GET $OPENMRS_BASE/status"

try {
    $response = Invoke-RestMethod -Uri "$OPENMRS_BASE/status" -ErrorAction Stop
    
    if ($response.success -eq $true) {
        Write-Host "✓ ÉXITO: El módulo está funcionando correctamente" -ForegroundColor Green
        Write-Host "  Módulo: $($response.module)" -ForegroundColor Gray
        Write-Host "  Versión: $($response.version)" -ForegroundColor Gray
        Write-Host "  Cola - Pendientes: $($response.queue.pending)" -ForegroundColor Gray
        Write-Host "  Cola - Enviados: $($response.queue.sent)" -ForegroundColor Gray
        Write-Host "  Cola - Errores: $($response.queue.error)" -ForegroundColor Gray
    } else {
        Write-Host "✗ FALLO: El módulo no responde correctamente" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ ERROR: No se pudo conectar al módulo" -ForegroundColor Red
    Write-Host "  $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Start-Sleep -Seconds 2

# ============================================================
# PRUEBA 2: Consultar Paciente por DNI desde RENHICE
# ============================================================
Write-Host "PRUEBA 2: Consultar Paciente por DNI desde RENHICE" -ForegroundColor Yellow

# DNIs de prueba disponibles
$dnisPrueba = @("74176968", "82446268", "80745861", "48213853", "91530636")
$dniAleatorio = Get-Random -InputObject $dnisPrueba

Write-Host "DNI a consultar: $dniAleatorio"
Write-Host "Endpoint: GET $OPENMRS_BASE/patient/$dniAleatorio"

try {
    $response = Invoke-RestMethod -Uri "$OPENMRS_BASE/patient/$dniAleatorio" -ErrorAction Stop
    
    if ($response.success -eq $true -and $response.total -gt 0) {
        Write-Host "✓ ÉXITO: Se encontró el paciente en RENHICE" -ForegroundColor Green
        $patient = $response.patients[0]
        Write-Host "  Nombre: $($patient.name)" -ForegroundColor Gray
        Write-Host "  Fecha de nacimiento: $($patient.birthDate)" -ForegroundColor Gray
        Write-Host "  Género: $($patient.gender)" -ForegroundColor Gray
        Write-Host "  DNI: $($patient.identifiers[0].value)" -ForegroundColor Gray
    } else {
        Write-Host "✗ FALLO: No se encontró el paciente" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ ERROR: No se pudo consultar el paciente" -ForegroundColor Red
    Write-Host "  $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Start-Sleep -Seconds 2

# ============================================================
# PRUEBA 3: Verificar Cola de Mensajes
# ============================================================
Write-Host "PRUEBA 3: Verificar Cola de Mensajes" -ForegroundColor Yellow
Write-Host "Endpoint: GET $OPENMRS_BASE/queue"

try {
    $response = Invoke-RestMethod -Uri "$OPENMRS_BASE/queue" -ErrorAction Stop
    
    if ($response.success -eq $true) {
        Write-Host "✓ ÉXITO: Se obtuvo la cola de mensajes" -ForegroundColor Green
        Write-Host "  Total de mensajes: $($response.count)" -ForegroundColor Gray
        
        if ($response.count -gt 0) {
            Write-Host "  Últimos mensajes:" -ForegroundColor Gray
            
            # Mostrar últimos 3 mensajes
            $lastMessages = $response.items | Select-Object -Last 3
            foreach ($item in $lastMessages) {
                Write-Host "    - ID: $($item.queueId) | Estado: $($item.status) | Tipo: $($item.messageType) | Intentos: $($item.attempts)" -ForegroundColor Gray
            }
            
            # Contar por estado
            $pending = ($response.items | Where-Object { $_.status -eq "PENDING" }).Count
            $sent = ($response.items | Where-Object { $_.status -eq "SENT" }).Count
            $error = ($response.items | Where-Object { $_.status -eq "ERROR" }).Count
            
            Write-Host "  Resumen por estado:" -ForegroundColor Gray
            Write-Host "    - PENDING: $pending" -ForegroundColor Gray
            Write-Host "    - SENT: $sent" -ForegroundColor Gray
            Write-Host "    - ERROR: $error" -ForegroundColor Gray
        }
    } else {
        Write-Host "✗ FALLO: No se pudo obtener la cola" -ForegroundColor Red
    }
} catch {
    Write-Host "✗ ERROR: No se pudo consultar la cola" -ForegroundColor Red
    Write-Host "  $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Start-Sleep -Seconds 2

# ============================================================
# PRUEBA 4: Procesar Cola Manualmente
# ============================================================
Write-Host "PRUEBA 4: Procesar Cola Manualmente" -ForegroundColor Yellow
Write-Host "Endpoint: POST $OPENMRS_BASE/processQueue"

# Verificar si hay mensajes pendientes
try {
    $queueResponse = Invoke-RestMethod -Uri "$OPENMRS_BASE/queue" -ErrorAction Stop
    $pendingCount = ($queueResponse.items | Where-Object { $_.status -eq "PENDING" -or $_.status -eq "ERROR" }).Count
    
    if ($pendingCount -eq 0) {
        Write-Host "⚠ INFO: No hay mensajes pendientes para procesar" -ForegroundColor Yellow
    } else {
        Write-Host "Mensajes pendientes/error: $pendingCount"
        Write-Host "Procesando..."
        
        $response = Invoke-RestMethod -Uri "$OPENMRS_BASE/processQueue" -Method Post -ErrorAction Stop
        
        if ($response.success -eq $true) {
            Write-Host "✓ ÉXITO: Cola procesada correctamente" -ForegroundColor Green
            Write-Host "  Mensajes procesados: $($response.processedCount)" -ForegroundColor Gray
            Write-Host "  Mensajes enviados: $($response.sentCount)" -ForegroundColor Gray
        } else {
            Write-Host "✗ FALLO: Error al procesar la cola" -ForegroundColor Red
        }
    }
} catch {
    Write-Host "✗ ERROR: No se pudo procesar la cola" -ForegroundColor Red
    Write-Host "  $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Start-Sleep -Seconds 2

# ============================================================
# PRUEBA 5: Verificar Conectividad con RENHICE (HAPI FHIR)
# ============================================================
Write-Host "PRUEBA 5: Verificar Conectividad con RENHICE (HAPI FHIR)" -ForegroundColor Yellow
Write-Host "Endpoint: GET $HAPI_BASE/metadata"

try {
    $response = Invoke-RestMethod -Uri "$HAPI_BASE/metadata" -ErrorAction Stop
    
    if ($response.resourceType -eq "CapabilityStatement") {
        Write-Host "OK: RENHICE (HAPI FHIR) esta accesible" -ForegroundColor Green
        Write-Host "  Software: $($response.software.name)" -ForegroundColor Gray
        Write-Host "  Version FHIR: $($response.fhirVersion)" -ForegroundColor Gray
    } else {
        Write-Host "FALLO: Respuesta inesperada de RENHICE" -ForegroundColor Red
    }
} catch {
    Write-Host "ERROR: No se pudo conectar a RENHICE" -ForegroundColor Red
    Write-Host "  $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "  NOTA: Verificar que el contenedor hapi-fhir-jpaserver-start este corriendo" -ForegroundColor Yellow
}

Write-Host ""
Start-Sleep -Seconds 2

# ============================================================
# PRUEBA 6: Contar Recursos en RENHICE
# ============================================================
Write-Host "PRUEBA 6: Contar Recursos en RENHICE" -ForegroundColor Yellow

$resourceTypes = @("Patient", "Organization", "Practitioner", "Encounter", "Condition", "Observation")

Write-Host "Contando recursos en RENHICE..."

foreach ($resourceType in $resourceTypes) {
    try {
        $response = Invoke-RestMethod -Uri "$HAPI_BASE/$resourceType`?_summary=count" -ErrorAction Stop
        Write-Host "  $resourceType`: $($response.total)" -ForegroundColor Gray
    } catch {
        Write-Host "  $resourceType`: Error al contar" -ForegroundColor Red
    }
}

Write-Host ""

# ============================================================
# RESUMEN FINAL
# ============================================================
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  RESUMEN DE PRUEBAS" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "✓ Las pruebas han finalizado" -ForegroundColor Green
Write-Host ""
Write-Host "PRÓXIMOS PASOS:" -ForegroundColor Yellow
Write-Host "1. Crear un paciente en OpenMRS con DNI (http://localhost/openmrs/)" -ForegroundColor White
Write-Host "2. Crear un Encounter (atención) para ese paciente" -ForegroundColor White
Write-Host "3. Verificar que el mensaje se encoló automáticamente:" -ForegroundColor White
Write-Host "   GET $OPENMRS_BASE/queue" -ForegroundColor Gray
Write-Host "4. Esperar 5 minutos (scheduler automático) o procesar manualmente:" -ForegroundColor White
Write-Host "   POST $OPENMRS_BASE/processQueue" -ForegroundColor Gray
Write-Host "5. Verificar que el Bundle llegó a RENHICE" -ForegroundColor White
Write-Host ""
Write-Host "Documentacion completa: docs/PRUEBAS_INTEROPERABILIDAD.md" -ForegroundColor Cyan
Write-Host ""

