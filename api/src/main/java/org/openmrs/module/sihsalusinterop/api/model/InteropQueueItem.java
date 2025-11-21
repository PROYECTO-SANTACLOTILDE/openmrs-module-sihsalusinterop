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

import javax.persistence.*;
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
@Entity
@Table(name = "sihsalus_interop_queue")
public class InteropQueueItem extends BaseOpenmrsData {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "queue_id")
	private Integer queueId;
	
	/**
	 * Tipo de mensaje: "FHIR_BUNDLE" o "FUA_DOCUMENT"
	 */
	@Column(name = "message_type", nullable = false, length = 50)
	private String messageType;
	
	/**
	 * Payload del mensaje (JSON o XML serializado)
	 */
	@Lob
	@Column(name = "payload", nullable = false, columnDefinition = "TEXT")
	private String payload;
	
	/**
	 * Estado actual: PENDING, PROCESSING, SENT, ERROR, FAILED
	 */
	@Column(name = "status", nullable = false, length = 20)
	private String status = "PENDING";
	
	/**
	 * Número de intentos de envío realizados
	 */
	@Column(name = "attempts", nullable = false)
	private Integer attempts = 0;
	
	/**
	 * Máximo número de reintentos permitidos (default: 5)
	 */
	@Column(name = "max_attempts", nullable = false)
	private Integer maxAttempts = 5;
	
	/**
	 * Fecha/hora de creación del item en la cola
	 */
	@Column(name = "queued_at", nullable = false)
	@Temporal(TemporalType.TIMESTAMP)
	private Date queuedAt = new Date();
	
	/**
	 * Fecha/hora del último intento de envío
	 */
	@Column(name = "last_attempt_at")
	@Temporal(TemporalType.TIMESTAMP)
	private Date lastAttemptAt;
	
	/**
	 * Fecha/hora de envío exitoso
	 */
	@Column(name = "sent_at")
	@Temporal(TemporalType.TIMESTAMP)
	private Date sentAt;
	
	/**
	 * Mensaje de error del último intento (si aplica)
	 */
	@Lob
	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;
	
	/**
	 * URL del endpoint de destino (ej: http://localhost:8080/fhir)
	 */
	@Column(name = "target_endpoint", length = 500)
	private String targetEndpoint;
	
	/**
	 * ID del recurso en el sistema externo (después de enviado)
	 */
	@Column(name = "external_resource_id", length = 255)
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

