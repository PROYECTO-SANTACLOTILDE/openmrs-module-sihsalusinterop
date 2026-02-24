package org.openmrs.module.sihsalusinterop.extension.html;

import java.util.LinkedHashMap;
import java.util.Map;

import org.openmrs.module.web.extension.AdministrationSectionExt;

/**
 * Extension para agregar enlace al monitoreo de interoperabilidad en el menú de administración.
 * Hospital Santa Clotilde - SIH.SALUS
 */
public class AdminList extends AdministrationSectionExt {

	@Override
	public String getTitle() {
		return "sihsalusinterop.title";
	}

	@Override
	public String getRequiredPrivilege() {
		return "View Interop Queue";
	}

	@Override
	public Map<String, String> getLinks() {
		Map<String, String> map = new LinkedHashMap<String, String>();
		map.put("module/sihsalusinterop/monitoreoInteroperabilidad.form",
		        "sihsalusinterop.monitor");
		return map;
	}
}

