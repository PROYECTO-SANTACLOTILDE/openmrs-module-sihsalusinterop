/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.sihsalusinterop.web.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;
import org.openmrs.module.sihsalusinterop.api.DyakuSenderService;
import org.openmrs.module.sihsalusinterop.api.exception.InteropException;
import org.openmrs.module.sihsalusinterop.api.model.InteropQueueItem;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * InteropQueueController - Controlador REST para gestión de la Cola de Interoperabilidad
 * 
 * Endpoints disponibles:
 * - POST /queue/patient/{id} - Encolar paciente para envío
 * - POST /queue/process - Procesar cola pendiente
 * - GET /queue/items - Ver todos los items
 * - GET /queue/items/status/{status} - Filtrar por estado
 * - POST /queue/retry/{id} - Reintentar envío
 * 
 * Hospital Santa Clotilde - SIH.SALUS Team
 */
@Controller
@RequestMapping("/module/sihsalusinterop/api")
public class InteropQueueController {
	
	protected final Log log = LogFactory.getLog(getClass());
	
	/**
	 * POST /module/sihsalusinterop/api/queue/patient/{patientId}
	 * 
	 * Encola un paciente para envío a RENHICE
	 * 
	 * Ejemplo: curl -X POST http://localhost:8080/openmrs/module/sihsalusinterop/api/queue/patient/123
	 */
	@RequestMapping(value = "/queue/patient/{patientId}", method = RequestMethod.POST)
	@ResponseBody
	public Map<String, Object> queuePatient(@PathVariable("patientId") Integer patientId) {
		
		Map<String, Object> response = new HashMap<String, Object>();
		
		try {
			// Obtener servicios
			DyakuSenderService service = Context.getService(DyakuSenderService.class);
			Patient patient = Context.getPatientService().getPatient(patientId);
			
			// Validar que el paciente existe
			if (patient == null) {
				response.put("success", false);
				response.put("error", "Paciente no encontrado con ID: " + patientId);
				return response;
			}
			
			// Encolar paciente (conversión automática OpenMRS → FHIR → JSON)
			InteropQueueItem item = service.queuePatient(patient);
			
			// Respuesta exitosa
			response.put("success", true);
			response.put("message", "Paciente encolado exitosamente para envío a RENHICE");
			response.put("queueId", item.getQueueId());
			response.put("status", item.getStatus());
			response.put("patientName", patient.getPersonName().getFullName());
			response.put("targetEndpoint", item.getTargetEndpoint());
			
			log.info("✓ API: Paciente " + patientId + " encolado con ID " + item.getQueueId());
			
		} catch (InteropException e) {
			// Error de validación (ej: paciente sin DNI)
			log.error("✗ API: Error de validación - " + e.getMessage());
			response.put("success", false);
			response.put("error", e.getMessage());
			response.put("errorCode", e.getErrorCode());
			
		} catch (Exception e) {
			// Error inesperado
			log.error("✗ API: Error inesperado", e);
			response.put("success", false);
			response.put("error", "Error interno del servidor: " + e.getMessage());
		}
		
		return response;
	}
	
	/**
	 * POST /module/sihsalusinterop/api/queue/process
	 * 
	 * Procesa la cola de mensajes pendientes
	 * 
	 * Ejemplo: curl -X POST http://localhost:8080/openmrs/module/sihsalusinterop/api/queue/process
	 */
	@RequestMapping(value = "/queue/process", method = RequestMethod.POST)
	@ResponseBody
	public Map<String, Object> processQueue() {
		
		Map<String, Object> response = new HashMap<String, Object>();
		
		try {
			DyakuSenderService service = Context.getService(DyakuSenderService.class);
			
			// Procesar todos los mensajes pendientes
			java.util.Map<String, Integer> result = service.processQueue();
			int sentCount = result.getOrDefault("sentCount", 0);
			int processedCount = result.getOrDefault("processedCount", 0);
			
			response.put("success", true);
			response.put("message", "Se procesaron " + processedCount + " mensajes. " + sentCount + " enviados exitosamente.");
			response.put("sentCount", sentCount);
			response.put("processedCount", processedCount);
			
			log.info("✓ API: Cola procesada - " + sentCount + " mensajes enviados");
			
		} catch (Exception e) {
			log.error("✗ API: Error al procesar cola", e);
			response.put("success", false);
			response.put("error", e.getMessage());
		}
		
		return response;
	}
	
	/**
	 * GET /module/sihsalusinterop/api/queue/items
	 * 
	 * Obtiene todos los items de la cola
	 * 
	 * Ejemplo: curl http://localhost:8080/openmrs/module/sihsalusinterop/api/queue/items
	 */
	@RequestMapping(value = "/queue/items", method = RequestMethod.GET)
	@ResponseBody
	public Map<String, Object> getAllItems() {
		
		Map<String, Object> response = new HashMap<String, Object>();
		
		try {
			DyakuSenderService service = Context.getService(DyakuSenderService.class);
			List<InteropQueueItem> items = service.getAllQueueItems();
			
			response.put("success", true);
			response.put("count", items.size());
			response.put("items", items);
			
		} catch (Exception e) {
			log.error("✗ API: Error al obtener items", e);
			response.put("success", false);
			response.put("error", e.getMessage());
		}
		
		return response;
	}
	
	/**
	 * GET /module/sihsalusinterop/api/queue/items/status/{status}
	 * 
	 * Filtra items por estado (PENDING, SENT, ERROR, etc.)
	 * 
	 * Ejemplo: curl http://localhost:8080/openmrs/module/sihsalusinterop/api/queue/items/status/PENDING
	 */
	@RequestMapping(value = "/queue/items/status/{status}", method = RequestMethod.GET)
	@ResponseBody
	public Map<String, Object> getItemsByStatus(@PathVariable("status") String status) {
		
		Map<String, Object> response = new HashMap<String, Object>();
		
		try {
			DyakuSenderService service = Context.getService(DyakuSenderService.class);
			List<InteropQueueItem> items = service.getQueueItemsByStatus(status);
			
			response.put("success", true);
			response.put("status", status);
			response.put("count", items.size());
			response.put("items", items);
			
		} catch (Exception e) {
			log.error("✗ API: Error al filtrar items", e);
			response.put("success", false);
			response.put("error", e.getMessage());
		}
		
		return response;
	}
	
	/**
	 * POST /module/sihsalusinterop/api/queue/retry/{queueId}
	 * 
	 * Reintenta enviar un mensaje específico
	 * 
	 * Ejemplo: curl -X POST http://localhost:8080/openmrs/module/sihsalusinterop/api/queue/retry/5
	 */
	@RequestMapping(value = "/queue/retry/{queueId}", method = RequestMethod.POST)
	@ResponseBody
	public Map<String, Object> retryItem(@PathVariable("queueId") Integer queueId) {
		
		Map<String, Object> response = new HashMap<String, Object>();
		
		try {
			DyakuSenderService service = Context.getService(DyakuSenderService.class);
			
			// Reintentar envío
			boolean success = service.retryQueueItem(queueId);
			
			response.put("success", success);
			response.put("queueId", queueId);
			
			if (success) {
				response.put("message", "Mensaje enviado exitosamente");
			} else {
				response.put("message", "No se pudo enviar el mensaje (ver logs)");
			}
			
		} catch (Exception e) {
			log.error("✗ API: Error al reintentar item", e);
			response.put("success", false);
			response.put("error", e.getMessage());
		}
		
		return response;
	}
	
	/**
	 * GET /module/sihsalusinterop/api/queue/stats
	 * 
	 * Obtiene estadísticas de la cola
	 * 
	 * Ejemplo: curl http://localhost:8080/openmrs/module/sihsalusinterop/api/queue/stats
	 */
	@RequestMapping(value = "/queue/stats", method = RequestMethod.GET)
	@ResponseBody
	public Map<String, Object> getStats() {
		
		Map<String, Object> response = new HashMap<String, Object>();
		
		try {
			DyakuSenderService service = Context.getService(DyakuSenderService.class);
			
			int totalItems = service.getAllQueueItems().size();
			int pendingItems = service.getQueueItemsByStatus("PENDING").size();
			int sentItems = service.getQueueItemsByStatus("SENT").size();
			int errorItems = service.getQueueItemsByStatus("ERROR").size();
			int failedItems = service.getQueueItemsByStatus("FAILED").size();
			
			response.put("success", true);
			response.put("total", totalItems);
			response.put("pending", pendingItems);
			response.put("sent", sentItems);
			response.put("error", errorItems);
			response.put("failed", failedItems);
			
		} catch (Exception e) {
			log.error("✗ API: Error al obtener estadísticas", e);
			response.put("success", false);
			response.put("error", e.getMessage());
		}
		
		return response;
	}
}
