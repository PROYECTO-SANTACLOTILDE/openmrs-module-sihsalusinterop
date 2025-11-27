<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ include file="/WEB-INF/template/include.jsp"%>
<%@ include file="/WEB-INF/template/header.jsp"%>

<openmrs:require privilege="View Interop Queue" otherwise="/login.htm" redirect="/module/sihsalusinterop/monitoreoInteroperabilidad.form" />

<openmrs:htmlInclude file="/moduleResources/sihsalusinterop/css/monitoreo.css" />
<openmrs:htmlInclude file="/scripts/jquery/jquery.min.js" />

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
                if (data && data.results) {
                    // REST API devuelve data.results
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



