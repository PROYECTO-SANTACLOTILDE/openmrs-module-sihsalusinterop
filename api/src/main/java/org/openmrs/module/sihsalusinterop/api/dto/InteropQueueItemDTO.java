package org.openmrs.module.sihsalusinterop.api.dto;

import java.util.Date;

/**
 * DTO para InteropQueueItem - Evita recursión circular en serialización JSON
 */
public class InteropQueueItemDTO {
	
	private Integer queueId;
	
	private String messageType;
	
	private String status;
	
	private Integer attempts;
	
	private Integer maxAttempts;
	
	private Date queuedAt;
	
	private Date lastAttemptAt;
	
	private Date sentAt;
	
	private String errorMessage;
	
	private String targetEndpoint;
	
	private String externalResourceId;
	
	private String uuid;
	
	public InteropQueueItemDTO() {
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
	
	public String getUuid() {
		return uuid;
	}
	
	public void setUuid(String uuid) {
		this.uuid = uuid;
	}
	
	/**
	 * Convierte un InteropQueueItem a DTO
	 */
	public static InteropQueueItemDTO from(org.openmrs.module.sihsalusinterop.api.model.InteropQueueItem item) {
		if (item == null) {
			return null;
		}
		
		InteropQueueItemDTO dto = new InteropQueueItemDTO();
		dto.setQueueId(item.getQueueId());
		dto.setMessageType(item.getMessageType());
		dto.setStatus(item.getStatus());
		dto.setAttempts(item.getAttempts());
		dto.setMaxAttempts(item.getMaxAttempts());
		dto.setQueuedAt(item.getQueuedAt());
		dto.setLastAttemptAt(item.getLastAttemptAt());
		dto.setSentAt(item.getSentAt());
		dto.setErrorMessage(item.getErrorMessage());
		dto.setTargetEndpoint(item.getTargetEndpoint());
		dto.setExternalResourceId(item.getExternalResourceId());
		dto.setUuid(item.getUuid() != null ? item.getUuid() : null);
		
		return dto;
	}
}
