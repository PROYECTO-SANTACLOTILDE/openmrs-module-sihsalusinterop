/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.sihsalusinterop.api.model;

import org.openmrs.BaseOpenmrsData;

import java.util.Date;

/**
 * InteropQueueItem - Entidad para la Cola de Mensajes de Interoperabilidad
 * 
 * Esta entidad almacena los mensajes FHIR/FUA que están pendientes de envío
 * a los sistemas externos (RENHICE/SETI-SIS). Arquitectura Offline-First.
 * 
 * Estados Posibles:
 * - PENDING: En cola, esperando envío
 * - PROCESSING: Siendo enviado en este momento
 * - SENT: Enviado exitosamente
 * - ERROR: Error al enviar (se reintentará)
 * - FAILED: Error permanente (agotados reintentos)
 */
public class InteropQueueItem extends BaseOpenmrsData {
	
	private Integer queueId;
	
	/**
	 * Tipo de mensaje: "FHIR_BUNDLE" o "FUA_DOCUMENT"
	 */
	private String messageType;
	
	/**
	 * Payload del mensaje (JSON o XML serializado)
	 */
	private String payload;
	
	/**
	 * Estado actual: PENDING, PROCESSING, SENT, ERROR, FAILED
	 */
	private String status = "PENDING";
	
	/**
	 * Número de intentos de envío realizados
	 */
	private Integer attempts = 0;
	
	/**
	 * Máximo número de reintentos permitidos (default: 5)
	 */
	private Integer maxAttempts = 5;
	
	/**
	 * Fecha/hora de creación del item en la cola
	 */
	private Date queuedAt = new Date();
	
	/**
	 * Fecha/hora del último intento de envío
	 */
	private Date lastAttemptAt;
	
	/**
	 * Fecha/hora de envío exitoso
	 */
	private Date sentAt;
	
	/**
	 * Mensaje de error del último intento (si aplica)
	 */
	private String errorMessage;
	
	/**
	 * URL del endpoint de destino (ej: http://localhost:8080/fhir)
	 */
	private String targetEndpoint;
	
	/**
	 * ID del recurso en el sistema externo (después de enviado)
	 */
	private String externalResourceId;
	
	// ============================================================
	// GETTERS Y SETTERS
	// ============================================================
	
	@Override
	public Integer getId() {
		return queueId;
	}
	
	@Override
	public void setId(Integer id) {
		this.queueId = id;
	}
	
	public Integer getQueueId() {
		return queueId;
	}
	
	public void setQueueId(Integer queueId) {
		this.queueId = queueId;
	}
	
	public String getMessageType() {
		return messageType;
	}
	
	public void setMessageType(String messageType) {
		this.messageType = messageType;
	}
	
	public String getPayload() {
		return payload;
	}
	
	public void setPayload(String payload) {
		this.payload = payload;
	}
	
	public String getStatus() {
		return status;
	}
	
	public void setStatus(String status) {
		this.status = status;
	}
	
	public Integer getAttempts() {
		return attempts;
	}
	
	public void setAttempts(Integer attempts) {
		this.attempts = attempts;
	}
	
	public Integer getMaxAttempts() {
		return maxAttempts;
	}
	
	public void setMaxAttempts(Integer maxAttempts) {
		this.maxAttempts = maxAttempts;
	}
	
	public Date getQueuedAt() {
		return queuedAt;
	}
	
	public void setQueuedAt(Date queuedAt) {
		this.queuedAt = queuedAt;
	}
	
	public Date getLastAttemptAt() {
		return lastAttemptAt;
	}
	
	public void setLastAttemptAt(Date lastAttemptAt) {
		this.lastAttemptAt = lastAttemptAt;
	}
	
	public Date getSentAt() {
		return sentAt;
	}
	
	public void setSentAt(Date sentAt) {
		this.sentAt = sentAt;
	}
	
	public String getErrorMessage() {
		return errorMessage;
	}
	
	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}
	
	public String getTargetEndpoint() {
		return targetEndpoint;
	}
	
	public void setTargetEndpoint(String targetEndpoint) {
		this.targetEndpoint = targetEndpoint;
	}
	
	public String getExternalResourceId() {
		return externalResourceId;
	}
	
	public void setExternalResourceId(String externalResourceId) {
		this.externalResourceId = externalResourceId;
	}
}

