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
import org.openmrs.Concept;
import org.openmrs.ConceptMap;
import org.openmrs.api.ConceptService;
import org.openmrs.module.sihsalusinterop.api.DyakuSenderService;
import org.openmrs.module.sihsalusinterop.api.dto.InteropQueueItemDTO;
import org.openmrs.module.sihsalusinterop.api.dto.TerminologyMappingDTO;
import org.openmrs.module.sihsalusinterop.api.model.InteropQueueItem;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
			
			Map<String, Integer> processResult = service.processQueue();
			int sentCount = processResult.getOrDefault("sentCount", 0);
			int processedCount = processResult.getOrDefault("processedCount", 0);
			
			response.put("success", true);
			response.put("sentCount", sentCount);
			response.put("processedCount", processedCount);
			response.put("message", "Se procesaron " + processedCount + " mensajes. " + sentCount + " enviados exitosamente.");
			
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
			
			// Convertir a DTOs para evitar recursión circular
			List<InteropQueueItemDTO> itemDTOs = items.stream()
					.map(InteropQueueItemDTO::from)
					.collect(Collectors.toList());
			
			response.put("success", true);
			response.put("count", itemDTOs.size());
			response.put("items", itemDTOs);
			
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
			
			// Convertir a DTO para evitar recursión circular
			InteropQueueItemDTO itemDTO = InteropQueueItemDTO.from(item);
			
			response.put("success", true);
			response.put("item", itemDTO);
			
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
	 * GET /openmrs/ws/rest/v1/interop/patient/{identifier}
	 * 
	 * Consulta un paciente desde RENHICE (HAPI FHIR) por identificador (DNI)
	 * 
	 * @param identifier Identificador del paciente (ej: DNI)
	 * @param system Sistema de identificación (default: OID RENIEC)
	 * @param endpoint Endpoint del servidor FHIR (default: http://hapi-fhir-server:8080/fhir)
	 */
	@RequestMapping(value = "/patient/{identifier}", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> getPatientFromRenhice(
			@PathVariable("identifier") String identifier,
			@RequestParam(value = "system", required = false, defaultValue = "urn:oid:2.16.840.1.113883.4.904") String system,
			@RequestParam(value = "endpoint", required = false, defaultValue = "http://hapi-fhir-server:8080/fhir") String endpoint) {
		
		Map<String, Object> response = new HashMap<>();
		
		try {
			log.info(">>> REST API: Consultando paciente desde RENHICE. Identifier: " + identifier + ", System: " + system);
			
			// Usar HAPI FHIR Client para consultar
			ca.uhn.fhir.context.FhirContext ctx = ca.uhn.fhir.context.FhirContext.forR4();
			ca.uhn.fhir.rest.client.api.IGenericClient client = ctx.newRestfulGenericClient(endpoint);
			
			// Construir búsqueda: Patient?identifier=system|identifier
			org.hl7.fhir.r4.model.Bundle bundle = client.search()
				.forResource(org.hl7.fhir.r4.model.Patient.class)
				.where(org.hl7.fhir.r4.model.Patient.IDENTIFIER.exactly().systemAndCode(system, identifier))
				.returnBundle(org.hl7.fhir.r4.model.Bundle.class)
				.execute();
			
			// Procesar resultados
			List<Map<String, Object>> patients = new java.util.ArrayList<>();
			if (bundle.getEntry() != null) {
				for (org.hl7.fhir.r4.model.Bundle.BundleEntryComponent entry : bundle.getEntry()) {
					if (entry.getResource() instanceof org.hl7.fhir.r4.model.Patient) {
						org.hl7.fhir.r4.model.Patient patient = (org.hl7.fhir.r4.model.Patient) entry.getResource();
						Map<String, Object> patientData = new HashMap<>();
						
						patientData.put("id", patient.getIdElement().getIdPart());
						
						if (patient.getNameFirstRep() != null) {
							patientData.put("name", patient.getNameFirstRep().getNameAsSingleString());
						}
						
						if (patient.getBirthDate() != null) {
							patientData.put("birthDate", patient.getBirthDate().toString());
						}
						
						if (patient.getGender() != null) {
							patientData.put("gender", patient.getGender().getDisplay());
						}
						
						// Identificadores
						List<Map<String, String>> identifiers = new java.util.ArrayList<>();
						for (org.hl7.fhir.r4.model.Identifier ident : patient.getIdentifier()) {
							Map<String, String> identData = new HashMap<>();
							identData.put("system", ident.getSystem());
							identData.put("value", ident.getValue());
							identifiers.add(identData);
						}
						patientData.put("identifiers", identifiers);
						
						patients.add(patientData);
					}
				}
			}
			
			response.put("success", true);
			int total = patients.size();
			if (bundle.getTotalElement() != null && bundle.getTotalElement().getValue() != null) {
				total = bundle.getTotalElement().getValue().intValue();
			}
			response.put("total", total);
			response.put("patients", patients);
			
			if (patients.isEmpty()) {
				response.put("message", "No se encontraron pacientes con el identificador especificado");
			} else {
				response.put("message", "Se encontraron " + patients.size() + " paciente(s)");
			}
			
			return ResponseEntity.ok(response);
			
		} catch (Exception ex) {
			log.error(">>> REST API: Error al consultar paciente desde RENHICE", ex);
			response.put("success", false);
			response.put("message", "Error: " + ex.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}
	
	/**
	 * POST /openmrs/ws/rest/v1/interop/patient/import/{identifier}
	 * 
	 * Importa el IPS completo de un paciente desde RENHICE y lo guarda en OpenMRS local
	 * 
	 * @param identifier Identificador del paciente (DNI)
	 * @param system Sistema de identificación (default: OID RENIEC)
	 * @param endpoint Endpoint del servidor FHIR (default: http://hapi-fhir-server:8080/fhir)
	 */
	@RequestMapping(value = "/patient/import/{identifier}", method = RequestMethod.POST)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> importPatientFromRenhice(
			@PathVariable("identifier") String identifier,
			@RequestParam(value = "system", required = false, defaultValue = "urn:oid:2.16.840.1.113883.4.904") String system,
			@RequestParam(value = "endpoint", required = false, defaultValue = "http://hapi-fhir-server:8080/fhir") String endpoint) {
		
		Map<String, Object> response = new HashMap<>();
		
		try {
			log.info(">>> REST API: Importando paciente completo desde RENHICE. DNI: " + identifier);
			
			// Usar HAPI FHIR Client
			ca.uhn.fhir.context.FhirContext ctx = ca.uhn.fhir.context.FhirContext.forR4();
			ca.uhn.fhir.rest.client.api.IGenericClient client = ctx.newRestfulGenericClient(endpoint);
			
			// 1. Buscar paciente en RENHICE
			org.hl7.fhir.r4.model.Bundle patientBundle = client.search()
				.forResource(org.hl7.fhir.r4.model.Patient.class)
				.where(org.hl7.fhir.r4.model.Patient.IDENTIFIER.exactly().systemAndCode(system, identifier))
				.returnBundle(org.hl7.fhir.r4.model.Bundle.class)
				.execute();
			
			if (patientBundle.getEntry() == null || patientBundle.getEntry().isEmpty()) {
				response.put("success", false);
				response.put("message", "Paciente no encontrado en RENHICE con DNI: " + identifier);
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
			}
			
			org.hl7.fhir.r4.model.Patient fhirPatient = (org.hl7.fhir.r4.model.Patient) patientBundle.getEntryFirstRep().getResource();
			String fhirPatientId = fhirPatient.getIdElement().getIdPart();
			
			log.info(">>> Paciente encontrado en RENHICE. ID: " + fhirPatientId);
			
			// 2. Verificar si paciente existe localmente por DNI
			org.openmrs.api.PatientService patientService = Context.getPatientService();
			java.util.List<org.openmrs.Patient> localPatients = patientService.getPatients(null, identifier, null, true);
			
			org.openmrs.Patient localPatient = null;
			boolean isNewPatient = localPatients.isEmpty();
			
			if (isNewPatient) {
				log.info(">>> Paciente NO existe localmente. Creando nuevo paciente...");
				// Crear nuevo paciente usando mapper
				localPatient = org.openmrs.module.sihsalusinterop.api.mapper.FhirToOpenMRSPatientMapper.mapToOpenMRS(fhirPatient);
				localPatient = patientService.savePatient(localPatient);
				log.info(">>> Paciente creado localmente. ID: " + localPatient.getPatientId());
			} else {
				log.info(">>> Paciente ya existe localmente. Actualizando datos...");
				localPatient = localPatients.get(0);
				localPatient = org.openmrs.module.sihsalusinterop.api.mapper.FhirToOpenMRSPatientMapper.updateOpenMRSPatient(localPatient, fhirPatient);
				localPatient = patientService.savePatient(localPatient);
				log.info(">>> Paciente actualizado. ID: " + localPatient.getPatientId());
			}
			
			// 3. Importar recursos clínicos del IPS
			int conditionsImported = importConditionsFromRenhice(client, fhirPatientId, localPatient);
			int observationsImported = importObservationsFromRenhice(client, fhirPatientId, localPatient);
			
			// 4. Respuesta
			response.put("success", true);
			response.put("patientId", localPatient.getPatientId());
			response.put("patientUuid", localPatient.getUuid());
			response.put("isNewPatient", isNewPatient);
			response.put("message", isNewPatient ? "Paciente importado exitosamente desde RENHICE" : "Paciente actualizado exitosamente con datos de RENHICE");
			
			Map<String, Object> imported = new HashMap<>();
			imported.put("conditions", conditionsImported);
			imported.put("observations", observationsImported);
			response.put("imported", imported);
			
			log.info(">>> Importación completada. Conditions: " + conditionsImported + ", Observations: " + observationsImported);
			
			return ResponseEntity.ok(response);
			
		} catch (Exception ex) {
			log.error(">>> REST API: Error al importar paciente desde RENHICE", ex);
			response.put("success", false);
			response.put("message", "Error al importar: " + ex.getMessage());
			response.put("error", ex.getClass().getSimpleName());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}
	
	/**
	 * Importa Conditions (diagnósticos) desde RENHICE para un paciente
	 */
	private int importConditionsFromRenhice(ca.uhn.fhir.rest.client.api.IGenericClient client, String fhirPatientId, org.openmrs.Patient localPatient) {
		int imported = 0;
		
		try {
			log.info(">>> Importando Conditions para paciente FHIR ID: " + fhirPatientId);
			
			// Buscar Conditions del paciente en RENHICE
			org.hl7.fhir.r4.model.Bundle conditionsBundle = client.search()
				.forResource(org.hl7.fhir.r4.model.Condition.class)
				.where(org.hl7.fhir.r4.model.Condition.PATIENT.hasId(fhirPatientId))
				.returnBundle(org.hl7.fhir.r4.model.Bundle.class)
				.execute();
			
			if (conditionsBundle.getEntry() != null && !conditionsBundle.getEntry().isEmpty()) {
				org.openmrs.api.ObsService obsService = Context.getObsService();
				
				for (org.hl7.fhir.r4.model.Bundle.BundleEntryComponent entry : conditionsBundle.getEntry()) {
					if (entry.getResource() instanceof org.hl7.fhir.r4.model.Condition) {
						org.hl7.fhir.r4.model.Condition fhirCondition = (org.hl7.fhir.r4.model.Condition) entry.getResource();
						
						// Mapear a Obs
						org.openmrs.Obs obs = org.openmrs.module.sihsalusinterop.api.mapper.FhirToOpenMRSConditionMapper.mapToOpenMRS(fhirCondition, localPatient, null);
						
						if (obs.getConcept() != null && (obs.getValueCoded() != null || obs.getValueText() != null)) {
							obsService.saveObs(obs, "Imported from RENHICE");
							imported++;
							log.info(">>> Condition importado: " + (obs.getValueText() != null ? obs.getValueText() : obs.getValueCoded().getName()));
						}
					}
				}
			}
			
			log.info(">>> Total Conditions importados: " + imported);
			
		} catch (Exception e) {
			log.error(">>> Error al importar Conditions", e);
		}
		
		return imported;
	}
	
	/**
	 * Importa Observations (signos vitales, etc.) desde RENHICE para un paciente
	 */
	private int importObservationsFromRenhice(ca.uhn.fhir.rest.client.api.IGenericClient client, String fhirPatientId, org.openmrs.Patient localPatient) {
		int imported = 0;
		
		try {
			log.info(">>> Importando Observations para paciente FHIR ID: " + fhirPatientId);
			
			// Buscar Observations del paciente en RENHICE
			org.hl7.fhir.r4.model.Bundle observationsBundle = client.search()
				.forResource(org.hl7.fhir.r4.model.Observation.class)
				.where(org.hl7.fhir.r4.model.Observation.PATIENT.hasId(fhirPatientId))
				.returnBundle(org.hl7.fhir.r4.model.Bundle.class)
				.execute();
			
			if (observationsBundle.getEntry() != null && !observationsBundle.getEntry().isEmpty()) {
				org.openmrs.api.ObsService obsService = Context.getObsService();
				
				for (org.hl7.fhir.r4.model.Bundle.BundleEntryComponent entry : observationsBundle.getEntry()) {
					if (entry.getResource() instanceof org.hl7.fhir.r4.model.Observation) {
						org.hl7.fhir.r4.model.Observation fhirObservation = (org.hl7.fhir.r4.model.Observation) entry.getResource();
						
						// Mapear a Obs
						org.openmrs.Obs obs = org.openmrs.module.sihsalusinterop.api.mapper.FhirToOpenMRSObservationMapper.mapToOpenMRS(fhirObservation, localPatient, null);
						
						if (obs.getConcept() != null) {
							obsService.saveObs(obs, "Imported from RENHICE");
							imported++;
							log.info(">>> Observation importado: " + obs.getConcept().getName());
						}
					}
				}
			}
			
			log.info(">>> Total Observations importados: " + imported);
			
		} catch (Exception e) {
			log.error(">>> Error al importar Observations", e);
		}
		
		return imported;
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
	
	/**
	 * GET /openmrs/ws/rest/v1/interop/terminology/check
	 * 
	 * Verifica qué Concepts tienen mapeos CIE-10/CPMS configurados
	 * 
	 * Parámetros:
	 * - type (opcional): "CIE10" o "CPMS" (si no se especifica, verifica ambos)
	 * - conceptId (opcional): ID del Concept específico a verificar
	 * 
	 * Respuesta:
	 * {
	 *   "success": true,
	 *   "type": "CIE10",
	 *   "totalConcepts": 150,
	 *   "conceptsWithMapping": 120,
	 *   "conceptsWithoutMapping": 30,
	 *   "concepts": [...]
	 * }
	 */
	@RequestMapping(value = "/terminology/check", method = RequestMethod.GET)
	@ResponseBody
	public ResponseEntity<Map<String, Object>> checkTerminologyMappings(
			@RequestParam(value = "type", required = false) String type,
			@RequestParam(value = "conceptId", required = false) Integer conceptId) {
		
		Map<String, Object> response = new HashMap<>();
		
		try {
			log.info(">>> REST API: Verificación de mapeos de terminología solicitada. Type: " + type + ", ConceptId: " + conceptId);
			
			ConceptService conceptService = Context.getConceptService();
			
			// Variantes de nombres para CIE-10
			String[] cie10Variants = {
				"CIE-10", "CIE10", "ICD-10", "ICD10", 
				"CIE 10", "ICD 10", "CLASIFICACION INTERNACIONAL",
				"INTERNATIONAL CLASSIFICATION", "WHO ICD-10"
			};
			
			// Variantes de nombres para CPMS
			String[] cpmsVariants = {
				"CPMS", "CATALOGO PROCEDIMIENTOS", "PROCEDIMIENTOS MEDICOS",
				"CATALOGO PROCEDIMIENTOS MEDICOS", "PROCEDIMIENTOS SANITARIOS"
			};
			
			List<TerminologyMappingDTO> concepts = new java.util.ArrayList<>();
			int conceptsWithMapping = 0;
			int conceptsWithoutMapping = 0;
			
			// Si se especifica un Concept ID, verificar solo ese
			if (conceptId != null) {
				Concept concept = conceptService.getConcept(conceptId);
				if (concept != null) {
					concepts.add(checkConceptMapping(concept, type, cie10Variants, cpmsVariants));
				} else {
					response.put("success", false);
					response.put("message", "Concept con ID " + conceptId + " no encontrado");
					return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
				}
			} else {
				// Obtener todos los Concepts (limitado para evitar problemas de rendimiento)
				// Nota: En producción, considerar usar paginación
				// getAllConcepts(String, boolean, boolean) - name, includeRetired, includeVoided
				List<Concept> allConcepts = conceptService.getAllConcepts(null, false, false);
				
				for (Concept concept : allConcepts) {
					TerminologyMappingDTO dto = checkConceptMapping(concept, type, cie10Variants, cpmsVariants);
					concepts.add(dto);
					
					if (dto.getHasMapping()) {
						conceptsWithMapping++;
					} else {
						conceptsWithoutMapping++;
					}
				}
			}
			
			// Contar si no se contó antes (caso de conceptId específico)
			if (conceptId != null) {
				if (concepts.get(0).getHasMapping()) {
					conceptsWithMapping = 1;
				} else {
					conceptsWithoutMapping = 1;
				}
			}
			
			response.put("success", true);
			if (type != null) {
				response.put("type", type);
			} else {
				response.put("type", "ALL");
			}
			response.put("totalConcepts", concepts.size());
			response.put("conceptsWithMapping", conceptsWithMapping);
			response.put("conceptsWithoutMapping", conceptsWithoutMapping);
			response.put("concepts", concepts);
			
			log.info(">>> REST API: Verificación completada. " + conceptsWithMapping + " con mapeo, " + conceptsWithoutMapping + " sin mapeo");
			
			return ResponseEntity.ok(response);
			
		} catch (Exception ex) {
			log.error(">>> REST API: Error al verificar mapeos de terminología", ex);
			response.put("success", false);
			response.put("message", "Error: " + ex.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
		}
	}
	
	/**
	 * Verifica si un Concept tiene mapeo CIE-10 o CPMS
	 */
	private TerminologyMappingDTO checkConceptMapping(Concept concept, String type, String[] cie10Variants, String[] cpmsVariants) {
		TerminologyMappingDTO dto = new TerminologyMappingDTO();
		dto.setConceptId(concept.getConceptId());
		dto.setDisplayName(concept.getDisplayString());
		dto.setUuid(concept.getUuid());
		dto.setHasMapping(false);
		
		if (concept.getConceptMappings() == null || concept.getConceptMappings().isEmpty()) {
			return dto;
		}
		
		// Verificar CIE-10
		if (type == null || "CIE10".equalsIgnoreCase(type)) {
			for (ConceptMap map : concept.getConceptMappings()) {
				if (map.getConceptReferenceTerm() != null &&
				    map.getConceptReferenceTerm().getConceptSource() != null) {
					
					org.openmrs.ConceptSource source = map.getConceptReferenceTerm().getConceptSource();
					String sourceName = source.getName();
					String sourceHl7Code = source.getHl7Code();
					
					boolean isCie10 = false;
					if (sourceName != null) {
						String sourceNameUpper = sourceName.toUpperCase();
						for (String variant : cie10Variants) {
							if (sourceNameUpper.contains(variant.toUpperCase())) {
								isCie10 = true;
								break;
							}
						}
					}
					
					if (!isCie10 && sourceHl7Code != null) {
						String hl7CodeUpper = sourceHl7Code.toUpperCase();
						for (String variant : cie10Variants) {
							if (hl7CodeUpper.contains(variant.toUpperCase())) {
								isCie10 = true;
								break;
							}
						}
					}
					
					if (isCie10 && map.getConceptReferenceTerm().getCode() != null) {
						dto.setHasMapping(true);
						dto.setMappingCode(map.getConceptReferenceTerm().getCode());
						dto.setMappingSource(source.getUuid());
						dto.setMappingSourceName(source.getName());
						if (type != null && "CIE10".equalsIgnoreCase(type)) {
							return dto;
						}
					}
				}
			}
		}
		
		// Verificar CPMS
		if (type == null || "CPMS".equalsIgnoreCase(type)) {
			for (ConceptMap map : concept.getConceptMappings()) {
				if (map.getConceptReferenceTerm() != null &&
				    map.getConceptReferenceTerm().getConceptSource() != null) {
					
					org.openmrs.ConceptSource source = map.getConceptReferenceTerm().getConceptSource();
					String sourceName = source.getName();
					String sourceHl7Code = source.getHl7Code();
					
					boolean isCpms = false;
					if (sourceName != null) {
						String sourceNameUpper = sourceName.toUpperCase();
						for (String variant : cpmsVariants) {
							if (sourceNameUpper.contains(variant.toUpperCase())) {
								isCpms = true;
								break;
							}
						}
					}
					
					if (!isCpms && sourceHl7Code != null) {
						String hl7CodeUpper = sourceHl7Code.toUpperCase();
						for (String variant : cpmsVariants) {
							if (hl7CodeUpper.contains(variant.toUpperCase())) {
								isCpms = true;
								break;
							}
						}
					}
					
					if (isCpms && map.getConceptReferenceTerm().getCode() != null) {
						dto.setHasMapping(true);
						dto.setMappingCode(map.getConceptReferenceTerm().getCode());
						dto.setMappingSource(source.getUuid());
						dto.setMappingSourceName(source.getName());
						return dto;
					}
				}
			}
		}
		
		return dto;
	}
}
