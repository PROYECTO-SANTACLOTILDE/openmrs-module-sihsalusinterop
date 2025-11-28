package org.openmrs.module.sihsalusinterop.web.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * Controlador Spring MVC para la página de monitoreo de interoperabilidad
 * 
 * Hospital Santa Clotilde - SIH.SALUS
 */
@Controller
public class MonitoreoInteroperabilidadController {
	
	protected final Log log = LogFactory.getLog(getClass());
	
	/**
	 * GET /openmrs/module/sihsalusinterop/monitoreoInteroperabilidad.form
	 * 
	 * Muestra la interfaz web de monitoreo de interoperabilidad
	 */
	@RequestMapping(value = {"/module/sihsalusinterop/monitoreoInteroperabilidad.form", 
	                          "/module/sihsalusinterop/monitoreoInteroperabilidad"}, 
	                 method = RequestMethod.GET)
	public String showMonitoringPage() {
		log.info(">>> Accediendo a página de monitoreo de interoperabilidad via Controller");
		// Devuelve la ruta al JSP (OpenMRS busca en /WEB-INF/view/)
		return "/module/sihsalusinterop/pages/monitoreoInteroperabilidad";
	}
}
