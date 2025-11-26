package org.openmrs.module.sihsalusinterop.api.listener;

import ca.uhn.fhir.context.FhirContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.Bundle;
import org.openmrs.Encounter;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.module.sihsalusinterop.api.DyakuSenderService;
import org.openmrs.module.sihsalusinterop.api.exception.InteropException;
import org.openmrs.module.sihsalusinterop.api.service.BundleBuilderService;

/**
 * EncounterSavedListener - Escucha eventos de guardado de Encounter Cuando se guarda un Encounter,
 * construye automáticamente un Bundle FHIR y lo encola para envío a RENHICE. Hospital Santa
 * Clotilde - SIH.SALUS
 */
public class EncounterSavedListener {
	
	private static final Log log = LogFactory.getLog(EncounterSavedListener.class);
	
	/**
	 * Endpoint por defecto de RENHICE (simulador HAPI FHIR)
	 */
	private static final String DEFAULT_RENHICE_ENDPOINT = "http://hapi-fhir-server:8080/fhir";
	
	/**
	 * Global Property para el endpoint de RENHICE
	 */
	private static final String GP_RENHICE_ENDPOINT = "sihsalusinterop.renhice.endpoint";
	
	/**
	 * Global Property para habilitar/deshabilitar el listener
	 */
	private static final String GP_ENABLED = "sihsalusinterop.renhice.enabled";
	
	/**
	 * Método llamado cuando se guarda un Encounter Este método es llamado automáticamente por
	 * EncounterSavedAdvice después de guardar un Encounter en el EncounterService.
	 * 
	 * @param encounter El Encounter que se acaba de guardar
	 */
	public static void onEncounterSaved(Encounter encounter) {
		if (encounter == null || encounter.getVoided()) {
			return;
		}
		
		// Verificar si el módulo está habilitado
		if (!isEnabled()) {
			log.debug(">>> [EVENT] Listener deshabilitado (Global Property sihsalusinterop.renhice.enabled = false)");
			return;
		}
		
		// Ejecutar en un hilo separado para no bloquear el guardado del Encounter
		// El código se ejecuta de forma asíncrona para no afectar el rendimiento
		new Thread(() -> {
			try {
				// Abrir sesión de OpenMRS para el hilo daemon
				Context.openSession();
				
				log.info(">>> [EVENT] Encounter guardado detectado: " + encounter.getId());
				
				// Obtener BundleBuilderService como bean de Spring
				BundleBuilderService bundleService = getBundleBuilderService();
				if (bundleService == null) {
					log.error(">>> [EVENT] BundleBuilderService no disponible. No se puede construir Bundle FHIR.");
					return;
				}
				
				// Construir Bundle FHIR
				Bundle bundle;
				try {
					bundle = bundleService.buildClinicalSummaryBundle(encounter);
				} catch (InteropException e) {
					// Si el paciente no tiene DNI u otro error de interoperabilidad, loguear y salir
					if (e.getErrorCode() != null && e.getErrorCode().equals("DNI_NOT_FOUND")) {
						log.warn(">>> [EVENT] Encounter " + encounter.getId() + " no procesado: " + e.getMessage());
						log.warn(">>> [EVENT] El paciente debe tener DNI para enviar a RENHICE. Agregar DNI al paciente en OpenMRS.");
					} else {
						log.error(">>> [EVENT] Error de interoperabilidad al construir Bundle: " + e.getMessage(), e);
					}
					return;
				}
				
				// Serializar a JSON
				FhirContext ctx = FhirContext.forR4();
				String jsonPayload = ctx.newJsonParser()
					.setPrettyPrint(false)
					.encodeResourceToString(bundle);
				
				// Encolar para envío asíncrono
				DyakuSenderService senderService = Context.getService(DyakuSenderService.class);
				if (senderService == null) {
					log.error(">>> [EVENT] DyakuSenderService no disponible. No se puede encolar Bundle FHIR.");
					return;
				}
				
				String endpoint = getRenhiceEndpoint(); // Configurable desde Global Properties
				
				senderService.queueMessage("FHIR_BUNDLE", jsonPayload, endpoint);
				
				log.info(">>> [EVENT] Bundle FHIR encolado exitosamente para Encounter: " + encounter.getId());
				
			} catch (Exception e) {
				log.error(">>> [EVENT] Error al procesar Encounter guardado: " + encounter.getId(), e);
			} finally {
				// Cerrar sesión de OpenMRS para evitar memory leaks
				try {
					Context.closeSession();
				} catch (Exception e) {
					log.warn(">>> [EVENT] Error al cerrar sesión: " + e.getMessage());
				}
			}
		}).start();
	}
	
	/**
	 * Obtiene el BundleBuilderService como bean de Spring
	 */
	private static BundleBuilderService getBundleBuilderService() {
		try {
			// Intentar obtener el bean desde el ApplicationContext del módulo usando ModuleFactory
			org.openmrs.module.Module mod = org.openmrs.module.ModuleFactory.getModuleById("sihsalusinterop");
			if (mod != null) {
				// Obtener el ApplicationContext del módulo
				Object appContextObj = mod.getClass().getMethod("getApplicationContext").invoke(mod);
				if (appContextObj instanceof org.springframework.context.ApplicationContext) {
					org.springframework.context.ApplicationContext appContext = (org.springframework.context.ApplicationContext) appContextObj;
					return appContext.getBean("sihsalusinterop.BundleBuilderService", BundleBuilderService.class);
				}
			}
			
			// Fallback: instanciar manualmente (no recomendado, pero funcional)
			log.warn(">>> No se pudo obtener BundleBuilderService desde Spring. Usando instancia manual.");
			return new BundleBuilderService();
		}
		catch (Exception e) {
			log.warn(">>> Error al obtener BundleBuilderService desde Spring: " + e.getMessage());
			// Fallback: instanciar manualmente
			return new BundleBuilderService();
		}
	}
	
	/**
	 * Verifica si el listener está habilitado mediante Global Property
	 */
	private static boolean isEnabled() {
		try {
			AdministrationService adminService = Context.getAdministrationService();
			if (adminService != null) {
				String enabled = adminService.getGlobalProperty(GP_ENABLED, "true");
				return Boolean.parseBoolean(enabled);
			}
		}
		catch (Exception e) {
			log.warn("Error al leer Global Property " + GP_ENABLED + ". Usando valor por defecto: true", e);
		}
		// Por defecto, está habilitado
		return true;
	}
	
	/**
	 * Obtiene el endpoint de RENHICE desde Global Properties de OpenMRS
	 * 
	 * @return URL del endpoint de RENHICE (por defecto: http://hapi-fhir-server:8080/fhir)
	 */
	private static String getRenhiceEndpoint() {
		try {
			AdministrationService adminService = Context.getAdministrationService();
			if (adminService != null) {
				String endpoint = adminService.getGlobalProperty(GP_RENHICE_ENDPOINT, DEFAULT_RENHICE_ENDPOINT);
				if (endpoint != null && !endpoint.trim().isEmpty()) {
					return endpoint.trim();
				}
			}
		}
		catch (Exception e) {
			log.warn("Error al leer Global Property " + GP_RENHICE_ENDPOINT + ". Usando valor por defecto", e);
		}
		// Retornar valor por defecto si no se puede leer la Global Property
		return DEFAULT_RENHICE_ENDPOINT;
	}
}
