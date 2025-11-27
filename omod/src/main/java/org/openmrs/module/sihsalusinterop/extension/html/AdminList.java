package org.openmrs.module.sihsalusinterop.extension.html;

import java.util.LinkedHashMap;
import java.util.Map;

import org.openmrs.module.Extension;

/**
 * Extension para agregar enlace al monitoreo de interoperabilidad en el menú de administración
 * 
 * Hospital Santa Clotilde - SIH.SALUS
 * 
 * IMPORTANTE: Para OpenMRS 1.11.x usamos Extension básica.
 * El enlace se agrega directamente en la sección de administración.
 */
public class AdminList extends Extension {
	
	public Extension.MEDIA_TYPE getMediaType() {
		return Extension.MEDIA_TYPE.html;
	}
	
	public String getTitle() {
		return "sihsalusinterop.title";
	}
	
	public String getRequiredPrivilege() {
		return "View Interop Queue";
	}
	
	public Map<String, String> getLinks() {
		Map<String, String> map = new LinkedHashMap<String, String>();
		// Ruta relativa sin la barra inicial
		map.put("module/sihsalusinterop/monitoreoInteroperabilidad.form", 
		        "sihsalusinterop.monitor");
		return map;
	}
}

