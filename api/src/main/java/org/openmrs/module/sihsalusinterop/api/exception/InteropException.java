/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.sihsalusinterop.api.exception;

/**
 * InteropException - Excepción para errores de interoperabilidad Se lanza cuando hay problemas en
 * la conversión de datos OpenMRS a FHIR o en el envío de mensajes a sistemas externos
 * (RENHICE/SETI-SIS). Hospital Santa Clotilde - SIH.SALUS Team
 */
public class InteropException extends RuntimeException {
	
	private static final long serialVersionUID = 1L;
	
	private String errorCode;
	
	public InteropException(String message) {
		super(message);
	}
	
	public InteropException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public InteropException(String errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}
	
	public InteropException(String errorCode, String message, Throwable cause) {
		super(message, cause);
		this.errorCode = errorCode;
	}
	
	public String getErrorCode() {
		return errorCode;
	}
}
