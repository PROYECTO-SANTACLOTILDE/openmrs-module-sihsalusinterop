/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.sihsalusinterop.api.impl;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.sihsalusinterop.api.DyakuSenderService;
import org.openmrs.module.sihsalusinterop.api.dao.InteropQueueDao;
import org.openmrs.module.sihsalusinterop.api.exception.InteropException;
import org.openmrs.module.sihsalusinterop.api.mapper.DyakuPatientMapper;
import org.openmrs.module.sihsalusinterop.api.model.InteropQueueItem;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DyakuSenderServiceImpl - Implementación del Motor de Interoperabilidad
 * 
 * Esta clase implementa la lógica de envío de mensajes FHIR R4 al servidor
 * HAPI FHIR simulado (RENHICE). Utiliza HAPI FHIR Client para la comunicación.
 * 
 * Características:
 * - Envío asíncrono con cola persistente
 * - Reintentos automáticos en caso de fallo
 * - Manejo de conectividad intermitente (Offline-First)
 */
public class DyakuSenderServiceImpl extends BaseOpenmrsService implements DyakuSenderService {
	
	protected final Log log = LogFactory.getLog(this.getClass());
	
	private InteropQueueDao dao;
	
	// HAPI FHIR Context (R4)
	private FhirContext fhirContext;
	
	public void setDao(InteropQueueDao dao) {
		this.dao = dao;
	}
	
	public DyakuSenderServiceImpl() {
		// Inicializar el contexto FHIR R4
		this.fhirContext = FhirContext.forR4();
	}
	
	@Override
	public InteropQueueItem queueMessage(String messageType, String payload, String targetEndpoint) {
		log.info(">>> Encolando mensaje de tipo: " + messageType);
		
		InteropQueueItem item = new InteropQueueItem();
		item.setMessageType(messageType);
		item.setPayload(payload);
		item.setTargetEndpoint(targetEndpoint);
		item.setStatus("PENDING");
		item.setQueuedAt(new Date());
		
		InteropQueueItem saved = dao.save(item);
		log.info(">>> Mensaje encolado con ID: " + saved.getQueueId());
		
		return saved;
	}
	
	@Override
	public Map<String, Integer> processQueue() {
		log.info("========================================");
		log.info(">>> Procesando cola de interoperabilidad...");
		
		List<InteropQueueItem> pendingItems = dao.getPendingItems();
		log.info(">>> Items pendientes en cola: " + pendingItems.size());
		
		int sentCount = 0;
		int processedCount = 0;
		
		for (InteropQueueItem item : pendingItems) {
			// Verificar si no ha excedido el máximo de intentos
			if (item.getAttempts() >= item.getMaxAttempts()) {
				log.warn(">>> Item " + item.getQueueId() + " ha excedido reintentos. Marcando como FAILED.");
				item.setStatus("FAILED");
				item.setErrorMessage("Máximo número de reintentos alcanzado (" + item.getMaxAttempts() + ")");
				dao.save(item);
				processedCount++;
				continue;
			}
			
			// Intentar enviar el mensaje
			processedCount++;
			boolean success = sendMessage(item);
			
			if (success) {
				sentCount++;
			}
		}
		
		log.info(">>> Procesados: " + processedCount + " | Exitosos: " + sentCount + " | Fallidos: " + (processedCount - sentCount));
		log.info("========================================");
		
		Map<String, Integer> result = new HashMap<>();
		result.put("sentCount", sentCount);
		result.put("processedCount", processedCount);
		return result;
	}
	
	/**
	 * Envía un mensaje individual al servidor FHIR
	 * 
	 * @param item Item de la cola a enviar
	 * @return true si se envió exitosamente, false en caso contrario
	 */
	private boolean sendMessage(InteropQueueItem item) {
		log.info(">>> Intentando enviar mensaje ID: " + item.getQueueId() + " (Intento #" + (item.getAttempts() + 1) + ")");
		
		// Actualizar estado a PROCESSING
		item.setStatus("PROCESSING");
		item.setAttempts(item.getAttempts() + 1);
		item.setLastAttemptAt(new Date());
		dao.save(item);
		
		// Obtener endpoint (fuera del try para usar en catch)
		String endpoint = item.getTargetEndpoint();
		if (endpoint == null || endpoint.isEmpty()) {
			endpoint = "http://localhost:8080/fhir"; // Default
		}
		
		try {
			log.info(">>> Conectando a endpoint FHIR: " + endpoint);
			IGenericClient client = fhirContext.newRestfulGenericClient(endpoint);
			
			// Configurar timeout más largo para Bundles grandes (60 segundos)
			// HAPI FHIR puede tardar en procesar Bundles grandes
			ca.uhn.fhir.rest.client.api.IRestfulClientFactory clientFactory = fhirContext.getRestfulClientFactory();
			clientFactory.setConnectionRequestTimeout(60000); // 60 segundos
			clientFactory.setSocketTimeout(60000); // 60 segundos
			clientFactory.setConnectTimeout(10000); // 10 segundos para conexión inicial
			
			// Parsear el payload JSON a Bundle FHIR
			Bundle bundle = fhirContext.newJsonParser().parseResource(Bundle.class, item.getPayload());
			
			log.info(">>> Bundle parseado. Número de recursos: " + (bundle.getEntry() != null ? bundle.getEntry().size() : 0));
			
			// Enviar el Bundle mediante transacción (POST /fhir)
			log.info(">>> Enviando Bundle FHIR al servidor (timeout: 60s)...");
			Bundle response = client.transaction().withBundle(bundle).execute();
			
			// Si llegamos aquí, el envío fue exitoso
			log.info(">>> ¡Mensaje enviado exitosamente! ID externo: " + response.getId());
			
			item.setStatus("SENT");
			item.setSentAt(new Date());
			item.setExternalResourceId(response.getId());
			item.setErrorMessage(null);
			dao.save(item);
			
			return true;
			
		} catch (FhirClientConnectionException connEx) {
			// Error de conexión (sin internet, servidor caído, etc.)
			log.error(">>> ERROR DE CONEXIÓN: " + connEx.getMessage());
			item.setStatus("PENDING"); // Volver a PENDING para reintento posterior
			item.setErrorMessage("Error de conexión: " + connEx.getMessage());
			dao.save(item);
			return false;
			
		} catch (Exception ex) {
			// Otro tipo de error (payload inválido, error del servidor, etc.)
			String errorMsg = ex.getMessage();
			
			// Mejorar mensaje de error HTTP 404
			if (errorMsg != null && errorMsg.contains("HTTP 404")) {
				errorMsg = "HTTP 404 - El servidor FHIR no está disponible en: " + endpoint + ". Verifica que el servidor esté levantado.";
			}
			
			log.error(">>> ERROR AL ENVIAR MENSAJE: " + errorMsg, ex);
			item.setStatus("ERROR");
			item.setErrorMessage("Error: " + errorMsg);
			dao.save(item);
			return false;
		}
	}
	
	@Override
	public List<InteropQueueItem> getAllQueueItems() {
		return dao.getAll();
	}
	
	@Override
	public List<InteropQueueItem> getQueueItemsByStatus(String status) {
		return dao.getAll().stream()
				.filter(item -> item.getStatus().equals(status))
				.collect(Collectors.toList());
	}
	
	@Override
	public InteropQueueItem getQueueItemById(Integer id) {
		return dao.getById(id);
	}
	
	@Override
	public boolean retryQueueItem(Integer queueId) {
		InteropQueueItem item = dao.getById(queueId);
		
		if (item == null) {
			log.warn(">>> Item de cola no encontrado: " + queueId);
			return false;
		}
		
		// Resetear el estado a PENDING para que sea procesado en el próximo ciclo
		item.setStatus("PENDING");
		item.setErrorMessage(null);
		dao.save(item);
		
		log.info(">>> Item " + queueId + " marcado para reintento");
		
		// Intentar enviar inmediatamente
		return sendMessage(item);
	}
	
	@Override
	public void deleteQueueItem(Integer queueId) {
		InteropQueueItem item = dao.getById(queueId);
		if (item != null) {
			dao.delete(item);
			log.info(">>> Item de cola eliminado: " + queueId);
		}
	}
	
	@Override
	public InteropQueueItem queuePatient(org.openmrs.Patient patient) {
		log.info("========================================");
		log.info(">>> Encolando paciente para envío a RENHICE");
		log.info(">>> ID OpenMRS: " + patient.getId());
		log.info(">>> Nombre: " + patient.getPersonName().getFullName());
		
		try {
			// 1. Convertir paciente OpenMRS a FHIR R4 (Perfil MINSA)
			log.info(">>> Paso 1: Convirtiendo a FHIR R4...");
			Patient fhirPatient = DyakuPatientMapper.toDyakuFhir(patient);
			
			// 2. Crear un Bundle tipo "transaction" para enviar al servidor FHIR
			log.info(">>> Paso 2: Creando Bundle FHIR...");
			Bundle bundle = new Bundle();
			bundle.setType(Bundle.BundleType.TRANSACTION);
			
			// Agregar el paciente al bundle como entrada POST
			Bundle.BundleEntryComponent entry = bundle.addEntry();
			entry.setResource(fhirPatient);
			entry.getRequest()
				.setMethod(Bundle.HTTPVerb.POST)
				.setUrl("Patient");
			
			// 3. Serializar Bundle a JSON usando HAPI FHIR
			log.info(">>> Paso 3: Serializando a JSON...");
			String jsonPayload = fhirContext.newJsonParser()
				.setPrettyPrint(false)
				.encodeResourceToString(bundle);
			
			log.info(">>> Tamaño del payload: " + jsonPayload.length() + " caracteres");
			
			// 4. Crear item de cola
			log.info(">>> Paso 4: Creando item de cola...");
			InteropQueueItem queueItem = new InteropQueueItem();
			queueItem.setMessageType("FHIR_BUNDLE");
			queueItem.setPayload(jsonPayload);
			queueItem.setStatus("PENDING");
			queueItem.setTargetEndpoint(getDefaultRenhiceEndpoint());
			queueItem.setQueuedAt(new Date());
			
			// 5. Guardar en la base de datos
			log.info(">>> Paso 5: Guardando en base de datos...");
			InteropQueueItem saved = dao.save(queueItem);
			
			log.info("========================================");
			log.info("✓ Paciente encolado exitosamente!");
			log.info("✓ ID de Cola: " + saved.getQueueId());
			log.info("✓ Estado: " + saved.getStatus());
			log.info("========================================");
			
			return saved;
			
		} catch (InteropException e) {
			// Error de validación (ej: sin DNI)
			log.error("✗ ERROR DE VALIDACIÓN: " + e.getMessage());
			log.error("✗ Código: " + e.getErrorCode());
			throw e;
			
		} catch (Exception e) {
			// Error inesperado
			log.error("✗ ERROR INESPERADO al encolar paciente", e);
			throw new InteropException(
				"QUEUE_ERROR",
				"Error al encolar paciente para envío a RENHICE: " + e.getMessage(),
				e
			);
		}
	}
	
	/**
	 * Obtiene el endpoint por defecto de RENHICE
	 * TODO: Esto debería venir de Global Properties de OpenMRS
	 */
	private String getDefaultRenhiceEndpoint() {
		// Por ahora retornamos el servidor local
		// En producción esto debería ser: "https://renhice.minsa.gob.pe/fhir"
		return "http://localhost:8080/fhir";
	}
}

