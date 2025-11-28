<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="/WEB-INF/template/include.jsp"%>
<%@ include file="/WEB-INF/template/header.jsp"%>

<openmrs:require privilege="View Interop Queue" otherwise="/login.htm" redirect="/module/sihsalusinterop/monitoreoInteroperabilidad.form" />

<openmrs:htmlInclude file="/scripts/jquery/jquery.min.js" />

<style type="text/css">
.interop-dashboard{margin:20px;font-family:Arial,sans-serif}.status-summary{display:flex;gap:20px;margin-bottom:30px;flex-wrap:wrap}.status-card{flex:1;min-width:150px;padding:20px;border-radius:8px;text-align:center;box-shadow:0 2px 4px rgba(0,0,0,0.1)}.status-card.pending{background-color:#FFF3CD;border-left:4px solid #FFC107}.status-card.sent{background-color:#D4EDDA;border-left:4px solid #28A745}.status-card.error{background-color:#F8D7DA;border-left:4px solid #DC3545}.status-card.failed{background-color:#E2E3E5;border-left:4px solid #6C757D}.status-count{font-size:36px;font-weight:bold;margin-bottom:8px}.status-label{font-size:14px;color:#666;text-transform:uppercase}.actions{margin-bottom:20px}.actions button{margin-right:10px}.btn{padding:8px 16px;border:none;border-radius:4px;cursor:pointer;font-size:14px}.btn-primary{background-color:#007BFF;color:white}.btn-primary:hover{background-color:#0056B3}.btn-success{background-color:#28A745;color:white}.btn-success:hover{background-color:#1E7E34}.btn-warning{background-color:#FFC107;color:black}.btn-warning:hover{background-color:#E0A800}.btn-sm{padding:4px 8px;font-size:12px}.queue-table{overflow-x:auto}#interop-queue-table{width:100%;border-collapse:collapse;background-color:white;box-shadow:0 2px 4px rgba(0,0,0,0.1)}#interop-queue-table th{background-color:#F8F9FA;padding:12px;text-align:left;border-bottom:2px solid #DEE2E6;font-weight:bold}#interop-queue-table td{padding:10px 12px;border-bottom:1px solid #DEE2E6}#interop-queue-table tbody tr:hover{background-color:#F8F9FA}.status-badge{padding:4px 12px;border-radius:12px;font-size:12px;font-weight:bold;text-transform:uppercase}.status-pending{background-color:#FFF3CD;color:#856404}.status-sent{background-color:#D4EDDA;color:#155724}.status-error{background-color:#F8D7DA;color:#721C24}.status-failed{background-color:#E2E3E5;color:#383D41}.status-processing{background-color:#D1ECF1;color:#0C5460}.error-cell{max-width:300px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.actions-cell{white-space:nowrap}.loading,.empty{text-align:center;padding:40px;color:#6C757D;font-style:italic}.message-details{margin-top:30px;padding:20px;border:1px solid #DEE2E6;border-radius:8px;background-color:#F8F9FA}.message-details h3{margin-top:0;color:#495057}.detail-content{margin-top:15px}.detail-row{margin-bottom:12px;padding-bottom:12px;border-bottom:1px solid #DEE2E6}.detail-row:last-child{border-bottom:none}.detail-row strong{display:inline-block;width:200px;color:#495057}.payload-pre{background-color:#FFFFFF;padding:15px;border:1px solid #DEE2E6;border-radius:4px;overflow-x:auto;font-family:'Courier New',monospace;font-size:12px;line-height:1.4;max-height:400px;overflow-y:auto}button:disabled{opacity:0.6;cursor:not-allowed}
</style>

<h2>Monitoreo de Interoperabilidad - SIH.SALUS</h2>

<div class="interop-dashboard">
    <div class="status-summary">
        <div class="status-card pending">
            <div class="status-count" id="count-pending">-</div>
            <div class="status-label">Pendientes</div>
        </div>
        <div class="status-card sent">
            <div class="status-count" id="count-sent">-</div>
            <div class="status-label">Enviados</div>
        </div>
        <div class="status-card error">
            <div class="status-count" id="count-error">-</div>
            <div class="status-label">Con Error</div>
        </div>
        <div class="status-card failed">
            <div class="status-count" id="count-failed">-</div>
            <div class="status-label">Fallidos</div>
        </div>
    </div>

    <div class="actions">
        <button type="button" id="btn-refresh" class="btn btn-primary">Actualizar</button>
        <button type="button" id="btn-process" class="btn btn-success">Procesar Cola</button>
    </div>

    <div class="queue-table">
        <table id="interop-queue-table">
            <thead>
                <tr>
                    <th>ID</th>
                    <th>Tipo</th>
                    <th>Estado</th>
                    <th>Fecha Creación</th>
                    <th>Intentos</th>
                    <th>Último Intento</th>
                    <th>Mensaje de Error</th>
                    <th>Acciones</th>
                </tr>
            </thead>
            <tbody id="queue-tbody">
                <tr><td colspan="8" class="loading">Cargando datos...</td></tr>
            </tbody>
        </table>
    </div>

    <div id="message-details" class="message-details" style="display:none;">
        <h3>Detalles del Mensaje <span id="detail-uuid"></span></h3>
        <button type="button" id="btn-close-details" class="btn btn-sm">Cerrar</button>
        <div class="detail-content">
            <div class="detail-row"><strong>Queue ID:</strong> <span id="detail-queueId"></span></div>
            <div class="detail-row"><strong>Tipo de Mensaje:</strong> <span id="detail-messageType"></span></div>
            <div class="detail-row"><strong>Estado:</strong> <span id="detail-status"></span></div>
            <div class="detail-row"><strong>Endpoint Destino:</strong> <span id="detail-targetEndpoint"></span></div>
            <div class="detail-row"><strong>ID Recurso Externo:</strong> <span id="detail-externalResourceId"></span></div>
            <div class="detail-row"><strong>Payload (JSON):</strong><pre id="detail-payload" class="payload-pre"></pre></div>
        </div>
    </div>
</div>

<script type="text/javascript">
    var baseUrl = '${pageContext.request.contextPath}/ws/rest/v1/interop';
    
    jQuery(document).ready(function() {
        console.log('Inicializando panel de monitoreo...');
        loadQueueData();
        
        jQuery('#btn-refresh').click(function() { 
            console.log('Botón refresh clickeado');
            loadQueueData(); 
        });
        
        jQuery('#btn-process').click(function() { 
            console.log('Botón procesar clickeado');
            processQueue(); 
        });
        
        jQuery('#btn-close-details').click(function() { 
            jQuery('#message-details').hide(); 
        });
        
        // Auto-refresh cada 30 segundos
        setInterval(loadQueueData, 30000);
    });
    
    function loadQueueData() {
        console.log('Cargando datos de la cola desde: ' + baseUrl + '/queue');
        
        jQuery.ajax({
            url: baseUrl + '/queue',
            type: 'GET',
            dataType: 'json',
            success: function(data) {
                console.log('Datos recibidos:', data);
                if (data && data.items) {
                    // Endpoint devuelve data.items
                    updateSummary(data.items);
                    renderTable(data.items);
                } else if (data && data.results) {
                    // Por si acaso devuelve data.results
                    updateSummary(data.results);
                    renderTable(data.results);
                } else if (data && Array.isArray(data)) {
                    // Si devuelve array directo
                    updateSummary(data);
                    renderTable(data);
                } else {
                    console.error('Formato de respuesta inesperado:', data);
                    showError('Formato de respuesta inesperado del servidor');
                }
            },
            error: function(xhr, status, error) {
                console.error('Error al cargar datos:', status, error);
                showError('Error al cargar datos: ' + error);
                jQuery('#queue-tbody').html('<tr><td colspan="8" class="empty">Error al cargar datos. Verifica que el módulo REST esté configurado.</td></tr>');
            }
        });
    }
    
    function updateSummary(items) {
        var counts = { 'PENDING': 0, 'SENT': 0, 'ERROR': 0, 'FAILED': 0 };
        
        if (!items || !Array.isArray(items)) {
            console.warn('Items no es un array:', items);
            items = [];
        }
        
        items.forEach(function(item) { 
            if (counts.hasOwnProperty(item.status)) {
                counts[item.status]++; 
            }
        });
        
        jQuery('#count-pending').text(counts.PENDING);
        jQuery('#count-sent').text(counts.SENT);
        jQuery('#count-error').text(counts.ERROR);
        jQuery('#count-failed').text(counts.FAILED);
    }
    
    function renderTable(items) {
        var tbody = jQuery('#queue-tbody');
        tbody.empty();
        
        if (!items || items.length === 0) {
            tbody.append('<tr><td colspan="8" class="empty">No hay mensajes en la cola</td></tr>');
            return;
        }
        
        items.forEach(function(item) {
            var row = jQuery('<tr>');
            row.append('<td>' + (item.queueId || item.id || '-') + '</td>');
            row.append('<td>' + (item.messageType || '-') + '</td>');
            row.append('<td><span class="status-badge status-' + (item.status || 'unknown').toLowerCase() + '">' + (item.status || 'UNKNOWN') + '</span></td>');
            row.append('<td>' + formatDate(item.queuedAt || item.dateCreated) + '</td>');
            row.append('<td>' + (item.attempts || 0) + '/' + (item.maxAttempts || 5) + '</td>');
            row.append('<td>' + (item.lastAttemptAt ? formatDate(item.lastAttemptAt) : '-') + '</td>');
            
            var errorMsg = item.errorMessage || item.lastError || '-';
            row.append('<td class="error-cell" title="' + errorMsg + '">' + truncate(errorMsg, 50) + '</td>');
            
            var actions = jQuery('<td class="actions-cell">');
            var btnView = jQuery('<button type="button" class="btn btn-sm">Ver</button>');
            btnView.click(function() { showDetails(item); });
            actions.append(btnView);
            
            if (item.status === 'ERROR' || item.status === 'FAILED') {
                var btnRetry = jQuery('<button type="button" class="btn btn-sm btn-warning" style="margin-left:5px;">Reintentar</button>');
                btnRetry.click(function() { retryMessage(item.queueId || item.id); });
                actions.append(btnRetry);
            }
            
            row.append(actions);
            tbody.append(row);
        });
    }
    
    function showDetails(item) {
        jQuery('#detail-queueId').text(item.queueId || item.id || '-');
        jQuery('#detail-uuid').text('(' + (item.uuid || 'N/A') + ')');
        jQuery('#detail-messageType').text(item.messageType || '-');
        jQuery('#detail-status').html('<span class="status-badge status-' + (item.status || 'unknown').toLowerCase() + '">' + (item.status || 'UNKNOWN') + '</span>');
        jQuery('#detail-targetEndpoint').text(item.targetEndpoint || '-');
        jQuery('#detail-externalResourceId').text(item.externalResourceId || '-');
        
        var payload = '-';
        if (item.payload) {
            try { 
                payload = JSON.stringify(JSON.parse(item.payload), null, 2); 
            } catch(e) { 
                payload = item.payload; 
            }
        }
        jQuery('#detail-payload').text(payload);
        jQuery('#message-details').show();
    }
    
    function processQueue() {
        jQuery('#btn-process').prop('disabled', true).text('Procesando...');
        
        jQuery.ajax({
            url: baseUrl + '/processQueue',
            type: 'POST',
            dataType: 'json',
            success: function(data) {
                console.log('Resultado del procesamiento:', data);
                if (data && data.success) {
                    alert('Procesamiento completado: ' + (data.sentCount || 0) + ' mensajes enviados.');
                    loadQueueData();
                } else {
                    alert('Procesamiento completado, pero no se recibió confirmación del servidor.');
                    loadQueueData();
                }
            },
            error: function(xhr, status, error) {
                console.error('Error al procesar cola:', status, error);
                alert('Error al procesar la cola: ' + error);
            },
            complete: function() { 
                jQuery('#btn-process').prop('disabled', false).text('Procesar Cola'); 
            }
        });
    }
    
    function retryMessage(queueId) {
        if (!queueId) {
            alert('ID de mensaje no válido');
            return;
        }
        
        if (!confirm('¿Reintentar envío del mensaje #' + queueId + '?')) return;
        
        jQuery.ajax({
            url: baseUrl + '/queue/' + queueId + '/retry',
            type: 'POST',
            dataType: 'json',
            success: function(data) {
                console.log('Resultado del reintento:', data);
                if (data && data.success) {
                    alert('Mensaje reintentado exitosamente');
                    loadQueueData();
                } else {
                    alert('Se envió la solicitud de reintento');
                    loadQueueData();
                }
            },
            error: function(xhr, status, error) {
                console.error('Error al reintentar mensaje:', status, error);
                alert('Error al reintentar el mensaje: ' + error);
            }
        });
    }
    
    function formatDate(timestamp) {
        if (!timestamp) return '-';
        try {
            var date = new Date(timestamp);
            return date.toLocaleString('es-PE', {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit'
            });
        } catch(e) {
            return timestamp;
        }
    }
    
    function truncate(str, maxLen) {
        if (!str) return '-';
        str = String(str);
        if (str.length <= maxLen) return str;
        return str.substring(0, maxLen) + '...';
    }
    
    function showError(message) {
        console.error(message);
        alert(message);
    }
</script>

<%@ include file="/WEB-INF/template/footer.jsp"%>
