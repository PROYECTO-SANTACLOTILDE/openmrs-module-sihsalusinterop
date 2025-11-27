package org.openmrs.module.sihsalusinterop.api.tasks;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.module.sihsalusinterop.api.DyakuSenderService;
import org.openmrs.scheduler.tasks.AbstractTask;

import java.util.Map;

/**
 * QueueProcessorTask - Tarea programada para procesar la cola de interoperabilidad
 * 
 * Se ejecuta automáticamente cada 5 minutos para enviar mensajes pendientes.
 * Hospital Santa Clotilde - SIH.SALUS
 */
public class QueueProcessorTask extends AbstractTask {
	
	private static final Log log = LogFactory.getLog(QueueProcessorTask.class);
	
	@Override
	public void execute() {
		try {
			log.info(">>> [SCHEDULER] Iniciando procesamiento automático de cola...");
			
			DyakuSenderService service = Context.getService(DyakuSenderService.class);
			
			if (service == null) {
				log.error(">>> [SCHEDULER] No se pudo obtener el servicio DyakuSenderService");
				return;
			}
			
			Map<String, Integer> result = service.processQueue();
			int sentCount = result.getOrDefault("sentCount", 0);
			int processedCount = result.getOrDefault("processedCount", 0);
			
			log.info(">>> [SCHEDULER] Procesamiento completado. Procesados: " + processedCount + ", Enviados: " + sentCount);
			
		} catch (Exception e) {
			log.error(">>> [SCHEDULER] Error al procesar cola automáticamente", e);
		}
	}
}






