# Script de Pruebas de Interoperabilidad SIH.SALUS
# Autor: Johan Amador
# Hospital Santa Clotilde, Loreto, Peru

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  PRUEBAS DE INTEROPERABILIDAD SIH.SALUS - RENHICE" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""

$OPENMRS_BASE = "http://localhost/openmrs/ws/rest/v1/interop"
$HAPI_BASE = "http://localhost:8081/fhir"

# PRUEBA 1: Health Check
Write-Host "PRUEBA 1: Health Check del Modulo" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$OPENMRS_BASE/status" -ErrorAction Stop
    if ($response.success -eq $true) {
        Write-Host "[OK] El modulo esta funcionando" -ForegroundColor Green
        Write-Host "  Modulo: $($response.module)" -ForegroundColor Gray
        Write-Host "  Version: $($response.version)" -ForegroundColor Gray
    }
} catch {
    Write-Host "[ERROR] No se pudo conectar: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# PRUEBA 2: Consultar Paciente por DNI
Write-Host "PRUEBA 2: Consultar Paciente por DNI desde RENHICE" -ForegroundColor Yellow
$dni = "74176968"
try {
    $response = Invoke-RestMethod -Uri "$OPENMRS_BASE/patient/$dni" -ErrorAction Stop
    if ($response.success -eq $true -and $response.total -gt 0) {
        Write-Host "[OK] Paciente encontrado" -ForegroundColor Green
        $patient = $response.patients[0]
        Write-Host "  Nombre: $($patient.name)" -ForegroundColor Gray
        Write-Host "  DNI: $($patient.identifiers[0].value)" -ForegroundColor Gray
    }
} catch {
    Write-Host "[ERROR] No se pudo consultar: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# PRUEBA 3: Ver Cola
Write-Host "PRUEBA 3: Verificar Cola de Mensajes" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$OPENMRS_BASE/queue" -ErrorAction Stop
    if ($response.success -eq $true) {
        Write-Host "[OK] Total de mensajes: $($response.count)" -ForegroundColor Green
        $pending = ($response.items | Where-Object { $_.status -eq "PENDING" }).Count
        $sent = ($response.items | Where-Object { $_.status -eq "SENT" }).Count
        $error = ($response.items | Where-Object { $_.status -eq "ERROR" }).Count
        Write-Host "  PENDING: $pending" -ForegroundColor Gray
        Write-Host "  SENT: $sent" -ForegroundColor Gray
        Write-Host "  ERROR: $error" -ForegroundColor Gray
    }
} catch {
    Write-Host "[ERROR] No se pudo consultar la cola: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# PRUEBA 4: Procesar Cola
Write-Host "PRUEBA 4: Procesar Cola Manualmente" -ForegroundColor Yellow
try {
    $queueResponse = Invoke-RestMethod -Uri "$OPENMRS_BASE/queue" -ErrorAction Stop
    $pendingCount = ($queueResponse.items | Where-Object { $_.status -eq "PENDING" -or $_.status -eq "ERROR" }).Count
    
    if ($pendingCount -eq 0) {
        Write-Host "[INFO] No hay mensajes pendientes" -ForegroundColor Yellow
    } else {
        $response = Invoke-RestMethod -Uri "$OPENMRS_BASE/processQueue" -Method Post -ErrorAction Stop
        if ($response.success -eq $true) {
            Write-Host "[OK] Procesados: $($response.processedCount), Enviados: $($response.sentCount)" -ForegroundColor Green
        }
    }
} catch {
    Write-Host "[ERROR] No se pudo procesar: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# PRUEBA 5: Conectividad HAPI FHIR
Write-Host "PRUEBA 5: Verificar RENHICE (HAPI FHIR)" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$HAPI_BASE/metadata" -ErrorAction Stop
    if ($response.resourceType -eq "CapabilityStatement") {
        Write-Host "[OK] RENHICE accesible" -ForegroundColor Green
        Write-Host "  Version FHIR: $($response.fhirVersion)" -ForegroundColor Gray
    }
} catch {
    Write-Host "[ERROR] RENHICE no accesible: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# PRUEBA 6: Contar Recursos
Write-Host "PRUEBA 6: Contar Recursos en RENHICE" -ForegroundColor Yellow
$resourceTypes = @("Patient", "Organization", "Practitioner", "Encounter", "Condition", "Observation")
foreach ($resourceType in $resourceTypes) {
    try {
        $response = Invoke-RestMethod -Uri "$HAPI_BASE/$resourceType`?_summary=count" -ErrorAction Stop
        Write-Host "  $resourceType`: $($response.total)" -ForegroundColor Gray
    } catch {
        Write-Host "  $resourceType`: Error" -ForegroundColor Red
    }
}
Write-Host ""

# RESUMEN
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  RESUMEN" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Las pruebas han finalizado" -ForegroundColor Green
Write-Host ""
Write-Host "PROXIMOS PASOS:" -ForegroundColor Yellow
Write-Host "1. Crear un paciente en OpenMRS con DNI (http://localhost/openmrs/)" -ForegroundColor White
Write-Host "2. Crear un Encounter para ese paciente" -ForegroundColor White
Write-Host "3. Verificar que se encolo automaticamente:" -ForegroundColor White
Write-Host "   GET $OPENMRS_BASE/queue" -ForegroundColor Gray
Write-Host "4. Procesar cola:" -ForegroundColor White
Write-Host "   POST $OPENMRS_BASE/processQueue" -ForegroundColor Gray
Write-Host ""
Write-Host "Ver: docs/PRUEBAS_INTEROPERABILIDAD.md" -ForegroundColor Cyan
Write-Host ""

