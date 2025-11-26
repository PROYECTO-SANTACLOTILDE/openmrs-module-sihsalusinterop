package org.openmrs.module.sihsalusinterop.api.advice;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Encounter;
import org.openmrs.module.sihsalusinterop.api.listener.EncounterSavedListener;

/**
 * EncounterSavedAdvice - Module Advice para interceptar saveEncounter
 * 
 * Intercepta las llamadas al método saveEncounter del EncounterService
 * para detectar automáticamente cuando se guarda un Encounter y construir
 * el Bundle FHIR correspondiente.
 * 
 * Hospital Santa Clotilde - SIH.SALUS
 */
public class EncounterSavedAdvice implements MethodInterceptor {
	
	private static final Log log = LogFactory.getLog(EncounterSavedAdvice.class);
	
	/**
	 * Intercepta la llamada al método y ejecuta el listener después de guardar
	 * Solo intercepta el método saveEncounter
	 */
	@Override
	public Object invoke(MethodInvocation invocation) throws Throwable {
		
		// Verificar que sea el método saveEncounter
		String methodName = invocation.getMethod().getName();
		if (!"saveEncounter".equals(methodName)) {
			// No es saveEncounter, ejecutar normalmente sin interceptar
			return invocation.proceed();
		}
		
		// Ejecutar el método original (saveEncounter)
		Object result = invocation.proceed();
		
		// Si el resultado es un Encounter, notificar al listener
		if (result instanceof Encounter) {
			Encounter savedEncounter = (Encounter) result;
			
			try {
				// Notificar al listener de forma asíncrona para no bloquear
				// El listener ejecuta en un hilo daemon
				EncounterSavedListener.onEncounterSaved(savedEncounter);
			} catch (Exception e) {
				// NO lanzar excepción para no bloquear el guardado del Encounter
				// Solo loggear el error
				log.error("Error al procesar Encounter guardado (no bloquea el guardado): " + savedEncounter.getId(), e);
			}
		}
		
		return result;
	}
}

