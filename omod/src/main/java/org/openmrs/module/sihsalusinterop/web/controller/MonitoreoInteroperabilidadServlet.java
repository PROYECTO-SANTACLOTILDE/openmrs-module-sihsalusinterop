package org.openmrs.module.sihsalusinterop.web.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Servlet simple para servir la página de monitoreo de interoperabilidad
 * 
 * Hospital Santa Clotilde - SIH.SALUS
 */
public class MonitoreoInteroperabilidadServlet extends HttpServlet {
	
	private static final Log log = LogFactory.getLog(MonitoreoInteroperabilidadServlet.class);
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) 
			throws ServletException, IOException {
		
		log.info(">>> Accediendo a página de monitoreo de interoperabilidad");
		log.info(">>> Request URI: " + request.getRequestURI());
		log.info(">>> Context Path: " + request.getContextPath());
		
		// OpenMRS expone JSPs de módulos en /module/{moduleId}/...
		// El JSP está en web/module/pages/ entonces la ruta es /module/sihsalusinterop/pages/
		String jspPath = "/module/sihsalusinterop/pages/monitoreoInteroperabilidad.jsp";
		
		log.info(">>> Intentando forward a: " + jspPath);
		
		try {
			request.getRequestDispatcher(jspPath).forward(request, response);
		} catch (Exception e) {
			log.error(">>> Error al hacer forward: " + e.getMessage(), e);
			response.sendError(HttpServletResponse.SC_NOT_FOUND, "JSP no encontrado: " + jspPath);
		}
	}
}

