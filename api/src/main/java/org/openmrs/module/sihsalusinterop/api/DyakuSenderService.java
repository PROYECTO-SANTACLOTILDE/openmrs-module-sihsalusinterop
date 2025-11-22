/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.sihsalusinterop.api;

import org.openmrs.annotation.Authorized;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.sihsalusinterop.api.model.InteropQueueItem;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * DyakuSenderService - Servicio de Interoperabilidad FHIR R4
 * 
 * Gestiona el envío de mensajes FHIR (Dyaku profiles) al servidor
 * HAPI FHIR simulado (RENHICE). Implementa arquitectura Offline-First
 * con cola de mensajes y reintentos automáticos.
 * 
 * Hospital Santa Clotilde, Loreto, Perú.
 */
@Transactional
public interface DyakuSenderService extends OpenmrsService {
	
	/**
	 * Encola un nuevo mensaje FHIR para envío asíncrono
	 * 
	 * @param messageType Tipo de mensaje: "FHIR_BUNDLE" o "FUA_DOCUMENT"
	 * @param payload Contenido del mensaje (JSON/XML serializado)
	 * @param targetEndpoint URL del endpoint de destino (ej: http://localhost:8080/fhir)
	 * @return El item de cola creado
	 */
	@Authorized({ "Manage Interop Queue" })
	InteropQueueItem queueMessage(String messageType, String payload, String targetEndpoint);
	
	/**
	 * Procesa la cola de mensajes pendientes
	 * Envía todos los mensajes con status=PENDING al servidor HAPI FHIR
	 * 
	 * @return Map con "sentCount" (enviados exitosamente) y "processedCount" (total procesados)
	 */
	@Authorized({ "Manage Interop Queue" })
	java.util.Map<String, Integer> processQueue();
	
	/**
	 * Obtiene todos los items de la cola
	 * 
	 * @return Lista de items en la cola
	 */
	@Authorized({ "View Interop Logs" })
	List<InteropQueueItem> getAllQueueItems();
	
	/**
	 * Obtiene items de la cola por estado
	 * 
	 * @param status Estado a filtrar (PENDING, SENT, ERROR, etc.)
	 * @return Lista de items con el estado especificado
	 */
	@Authorized({ "View Interop Logs" })
	List<InteropQueueItem> getQueueItemsByStatus(String status);
	
	/**
	 * Obtiene un item de la cola por ID
	 * 
	 * @param id ID del item
	 * @return El item de la cola o null si no existe
	 */
	@Authorized({ "View Interop Logs" })
	InteropQueueItem getQueueItemById(Integer id);
	
	/**
	 * Reintenta enviar un item específico de la cola
	 * 
	 * @param queueId ID del item a reintentar
	 * @return true si se envió exitosamente, false en caso contrario
	 */
	@Authorized({ "Manage Interop Queue" })
	boolean retryQueueItem(Integer queueId);
	
	/**
	 * Elimina un item de la cola
	 * 
	 * @param queueId ID del item a eliminar
	 */
	@Authorized({ "Manage Interop Queue" })
	void deleteQueueItem(Integer queueId);
	
	/**
	 * Encola un paciente completo para envío a RENHICE
	 * 
	 * Este método realiza la conversión completa del paciente OpenMRS a FHIR R4
	 * (perfil MINSA), serializa a JSON y lo encola para envío asíncrono.
	 * 
	 * @param patient Paciente de OpenMRS a enviar
	 * @return El item de cola creado
	 * @throws org.openmrs.module.sihsalusinterop.api.exception.InteropException 
	 *         Si el paciente no tiene DNI o hay error en la conversión
	 */
	@Authorized({ "Manage Interop Queue", "Send FHIR Messages" })
	InteropQueueItem queuePatient(org.openmrs.Patient patient);
}

