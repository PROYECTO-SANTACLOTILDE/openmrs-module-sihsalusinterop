package org.openmrs.module.sihsalusinterop.api.listener;

import ca.uhn.fhir.context.FhirContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.Bundle;
import org.openmrs.Encounter;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.Daemon;
import org.openmrs.module.sihsalusinterop.api.DyakuSenderService;
import org.openmrs.module.sihsalusinterop.api.service.BundleBuilderService;

/**
 * EncounterSavedListener - Escucha eventos de guardado de Encounter Cuando se guarda un Encounter,
 * construye automáticamente un Bundle FHIR y lo encola para envío a RENHICE. Hospital Santa
 * Clotilde - SIH.SALUS
 */
public class EncounterSavedListener {
	
	private static final Log log = LogFactory.getLog(EncounterSavedListener.class);
	
	private static final String DEFAULT_RENHICE_ENDPOINT = "http://hapi-fhir-server:8080/fhir";
	
	/**
	 * Método llamado cuando se guarda un Encounter Este método debe ser llamado desde un Advice o
	 * interceptor en el EncounterService o registrado como listener en el Activator.
	 */
	public static void onEncounterSaved(Encounter encounter) {
		if (encounter == null || encounter.getVoided()) {
			return;
		}
		
		// Ejecutar en un hilo de daemon para no bloquear
		Daemon.runInDaemonThread(() -> {
			try {
				log.info(">>> [EVENT] Encounter guardado detectado: " + encounter.getId());
				
				// Construir Bundle FHIR
				BundleBuilderService bundleService = new BundleBuilderService();
				Bundle bundle = bundleService.buildClinicalSummaryBundle(encounter);
				
				// Serializar a JSON
				FhirContext ctx = FhirContext.forR4();
				String jsonPayload = ctx.newJsonParser()
					.setPrettyPrint(false)
					.encodeResourceToString(bundle);
				
				// Encolar para envío asíncrono
				DyakuSenderService senderService = Context.getService(DyakuSenderService.class);
				String endpoint = getRenhiceEndpoint(); // Configurable
				
				senderService.queueMessage("FHIR_BUNDLE", jsonPayload, endpoint);
				
				log.info(">>> [EVENT] Bundle FHIR encolado exitosamente para Encounter: " + encounter.getId());
				
			} catch (Exception e) {
				log.error(">>> [EVENT] Error al procesar Encounter guardado: " + encounter.getId(), e);
			}
		}, null);
	}
	
	/**
	 * Obtiene el endpoint de RENHICE desde Global Properties TODO: Leer desde Global Properties de
	 * OpenMRS
	 */
	private static String getRenhiceEndpoint() {
		// Por ahora retornar el valor por defecto
		// En producción: Context.getAdministrationService().getGlobalProperty("sihsalusinterop.renhice.endpoint")
		return DEFAULT_RENHICE_ENDPOINT;
	}
}
