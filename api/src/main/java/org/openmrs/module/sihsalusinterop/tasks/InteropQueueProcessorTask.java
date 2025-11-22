/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.sihsalusinterop.tasks;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.module.sihsalusinterop.api.DyakuSenderService;
import org.openmrs.scheduler.tasks.AbstractTask;

/**
 * InteropQueueProcessorTask - Scheduler Automático para Procesar Cola de Mensajes
 * 
 * Esta tarea se ejecuta periódicamente para procesar automáticamente los mensajes
 * con estado PENDING en la cola y enviarlos al servidor FHIR (RENHICE).
 * 
 * Configuración en OpenMRS:
 * - Intervalo recomendado: 300 segundos (5 minutos)
 * - Se ejecuta automáticamente cada X segundos
 * 
 * Hospital Santa Clotilde - SIH.SALUS Team
 */
public class InteropQueueProcessorTask extends AbstractTask {
	
	private static final Log log = LogFactory.getLog(InteropQueueProcessorTask.class);
	
	/**
	 * Método principal que se ejecuta cada vez que el scheduler dispara esta tarea
	 */
	@Override
	public void execute() {
		
		try {
			log.info("========================================");
			log.info("▶ SCHEDULER: Iniciando procesamiento automático de cola...");
			
			// Obtener el servicio de interoperabilidad
			DyakuSenderService service = Context.getService(DyakuSenderService.class);
			
			if (service == null) {
				log.error("✗ SCHEDULER: Servicio DyakuSenderService no encontrado");
				return;
			}
			
			// Procesar todos los mensajes pendientes
			java.util.Map<String, Integer> result = service.processQueue();
			int sentCount = result.getOrDefault("sentCount", 0);
			int processedCount = result.getOrDefault("processedCount", 0);
			
			if (sentCount > 0) {
				log.info("✅ SCHEDULER: " + sentCount + " mensaje(s) enviado(s) exitosamente de " + processedCount + " procesados");
			} else {
				log.info("✓ SCHEDULER: Procesados: " + processedCount + ". No hay mensajes pendientes para enviar");
			}
			
			log.info("========================================");
			
		} catch (Exception e) {
			log.error("✗ SCHEDULER: Error al procesar cola automáticamente", e);
		}
	}
}

