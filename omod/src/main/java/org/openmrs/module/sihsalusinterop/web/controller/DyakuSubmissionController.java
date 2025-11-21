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
import org.openmrs.api.context.Context;
import org.openmrs.module.sihsalusinterop.api.DyakuSenderService;
import org.openmrs.module.sihsalusinterop.api.model.InteropQueueItem;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DyakuSubmissionController - REST API para Interoperabilidad
 * 
 * Endpoints REST para encolar mensajes FHIR, consultar el estado de la cola
 * y procesar mensajes pendientes.
 * 
 * Base URL: /openmrs/ws/rest/v1/interop/
 * 
 * Hospital Santa Clotilde, Loreto, Perú.
 */
@Controller
@RequestMapping("/rest/v1/interop")
public class DyakuSubmissionController {
	
	protected final Log log = LogFactory.getLog(getClass());
	
	/**
	 * POST /openmrs/ws/rest/v1/interop/send
	 * 
	 * Encola un mensaje FHIR para envío asíncrono al servidor HAPI FHIR
	 * 
	 * Payload JSON esperado:
	 * {
	 *   "messageType": "FHIR_BUNDLE",
	 *   "payload": "{...JSON del Bundle FHIR...}",
	 *   "targetEndpoint": "http://localhost:8080/fhir"
	 * }
	 * 
	 * Respuesta:
	 * {
	 *   "success": true,
	 *   "queueId": 123,
	 *   "message": "Mensaje encolado exitosamente"
	 * }
	 */
	@RequestMapping(value = "/send", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> sendMessage(@RequestBody Map<String, String> request) {
		Map<String, Object> response = new HashMap<>();
		
		try {
			log.info(">>> REST API: Recibida solicitud de envío de mensaje FHIR");
			
			// Extraer parámetros del request
			String messageType = request.get("messageType");
			String payload = request.get("payload");
			String targetEndpoint = request.getOrDefault("targetEndpoint", "http://localhost:8080/fhir");
			
			// Validaciones básicas
			if (messageType == null || messageType.isEmpty()) {
				response.put("success", false);
				response.put("message", "El campo 'messageType' es requerido");
				return ResponseEntity.badRequest().body(response);
			}
			
			if (payload == null || payload.isEmpty()) {
				response.put("success", false);
				response.put("message", "El campo 'payload' es requerido");
				return ResponseEntity.badRequest().body(response);
			}
			
			// Obtener el servicio de interoperabilidad
			DyakuSenderService service = Context.getService(DyakuSenderService.class);
			
			// Encolar el mensaje
			InteropQueueItem queueItem = service.queueMessage(messageType, payload, targetEndpoint);
			
			response.put("success", true);
			response.put("queueId", queueItem.getQueueId());
			response.put("status", queueItem.getStatus());
			response.put("message", "Mensaje encolado exitosamente. Será enviado en el próximo ciclo de procesamiento.");
			
			log.info(">>> REST API: Mensaje encolado con ID " + queueItem.getQueueId());
			
			return ResponseEntity.ok(response);
			
		} catch (Exception ex) {
			log.error(">>> REST API: Error al encolar mensaje", ex);
			response.put("success", false);
			response.put("message", "Error: " + ex.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}
	
	/**
	 * POST /openmrs/ws/rest/v1/interop/processQueue
	 * 
	 * Procesa manualmente la cola de mensajes pendientes
	 * (Normalmente esto se hace automáticamente via Scheduled Task)
	 * 
	 * Respuesta:
	 * {
	 *   "success": true,
	 *   "sentCount": 5,
	 *   "message": "Se procesaron 5 mensajes exitosamente"
	 * }
	 */
	@RequestMapping(value = "/processQueue", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> processQueue() {
		Map<String, Object> response = new HashMap<>();
		
		try {
			log.info(">>> REST API: Procesamiento manual de cola solicitado");
			
			DyakuSenderService service = Context.getService(DyakuSenderService.class);
			int sentCount = service.processQueue();
			
			response.put("success", true);
			response.put("sentCount", sentCount);
			response.put("message", "Se procesaron " + sentCount + " mensajes exitosamente");
			
			return ResponseEntity.ok(response);
			
		} catch (Exception ex) {
			log.error(">>> REST API: Error al procesar cola", ex);
			response.put("success", false);
			response.put("message", "Error: " + ex.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}
	
	/**
	 * GET /openmrs/ws/rest/v1/interop/queue
	 * 
	 * Obtiene todos los items de la cola
	 * 
	 * Respuesta:
	 * {
	 *   "success": true,
	 *   "count": 10,
	 *   "items": [...]
	 * }
	 */
	@RequestMapping(value = "/queue", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> getQueue() {
		Map<String, Object> response = new HashMap<>();
		
		try {
			DyakuSenderService service = Context.getService(DyakuSenderService.class);
			List<InteropQueueItem> items = service.getAllQueueItems();
			
			response.put("success", true);
			response.put("count", items.size());
			response.put("items", items);
			
			return ResponseEntity.ok(response);
			
		} catch (Exception ex) {
			log.error(">>> REST API: Error al obtener cola", ex);
			response.put("success", false);
			response.put("message", "Error: " + ex.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}
	
	/**
	 * GET /openmrs/ws/rest/v1/interop/queue/{id}
	 * 
	 * Obtiene un item específico de la cola por ID
	 */
	@RequestMapping(value = "/queue/{id}", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> getQueueItem(@PathVariable("id") Integer id) {
		Map<String, Object> response = new HashMap<>();
		
		try {
			DyakuSenderService service = Context.getService(DyakuSenderService.class);
			InteropQueueItem item = service.getQueueItemById(id);
			
			if (item == null) {
				response.put("success", false);
				response.put("message", "Item de cola no encontrado");
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
			}
			
			response.put("success", true);
			response.put("item", item);
			
			return ResponseEntity.ok(response);
			
		} catch (Exception ex) {
			log.error(">>> REST API: Error al obtener item de cola", ex);
			response.put("success", false);
			response.put("message", "Error: " + ex.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}
	
	/**
	 * POST /openmrs/ws/rest/v1/interop/queue/{id}/retry
	 * 
	 * Reintenta enviar un item específico de la cola
	 */
	@RequestMapping(value = "/queue/{id}/retry", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> retryQueueItem(@PathVariable("id") Integer id) {
		Map<String, Object> response = new HashMap<>();
		
		try {
			log.info(">>> REST API: Reintento solicitado para item " + id);
			
			DyakuSenderService service = Context.getService(DyakuSenderService.class);
			boolean success = service.retryQueueItem(id);
			
			response.put("success", success);
			response.put("message", success ? "Mensaje reenviado exitosamente" : "Error al reenviar mensaje");
			
			return ResponseEntity.ok(response);
			
		} catch (Exception ex) {
			log.error(">>> REST API: Error al reintentar item de cola", ex);
			response.put("success", false);
			response.put("message", "Error: " + ex.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}
	
	/**
	 * DELETE /openmrs/ws/rest/v1/interop/queue/{id}
	 * 
	 * Elimina un item de la cola
	 */
	@RequestMapping(value = "/queue/{id}", method = RequestMethod.DELETE)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> deleteQueueItem(@PathVariable("id") Integer id) {
		Map<String, Object> response = new HashMap<>();
		
		try {
			log.info(">>> REST API: Eliminación solicitada para item " + id);
			
			DyakuSenderService service = Context.getService(DyakuSenderService.class);
			service.deleteQueueItem(id);
			
			response.put("success", true);
			response.put("message", "Item eliminado exitosamente");
			
			return ResponseEntity.ok(response);
			
		} catch (Exception ex) {
			log.error(">>> REST API: Error al eliminar item de cola", ex);
			response.put("success", false);
			response.put("message", "Error: " + ex.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}
	
	/**
	 * GET /openmrs/ws/rest/v1/interop/status
	 * 
	 * Endpoint de health check / status
	 */
	@RequestMapping(value = "/status", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> getStatus() {
		Map<String, Object> response = new HashMap<>();
		
		try {
			DyakuSenderService service = Context.getService(DyakuSenderService.class);
			
			List<InteropQueueItem> pending = service.getQueueItemsByStatus("PENDING");
			List<InteropQueueItem> sent = service.getQueueItemsByStatus("SENT");
			List<InteropQueueItem> error = service.getQueueItemsByStatus("ERROR");
			List<InteropQueueItem> failed = service.getQueueItemsByStatus("FAILED");
			
			response.put("success", true);
			response.put("module", "SIH SALUS Interoperability Module");
			response.put("version", "1.0.0");
			response.put("queue", Map.of(
					"pending", pending.size(),
					"sent", sent.size(),
					"error", error.size(),
					"failed", failed.size()
			));
			
			return ResponseEntity.ok(response);
			
		} catch (Exception ex) {
			log.error(">>> REST API: Error al obtener status", ex);
			response.put("success", false);
			response.put("message", "Error: " + ex.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}
}

