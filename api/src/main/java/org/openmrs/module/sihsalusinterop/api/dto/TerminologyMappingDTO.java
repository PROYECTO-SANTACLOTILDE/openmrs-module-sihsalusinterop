/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.sihsalusinterop.api.dto;

import java.io.Serializable;

/**
 * DTO para representar el estado de mapeo de terminologías (CIE-10/CPMS) de un Concept Hospital
 * Santa Clotilde - SIH.SALUS
 */
public class TerminologyMappingDTO implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private Integer conceptId;
	
	private String displayName;
	
	private String uuid;
	
	private Boolean hasMapping;
	
	private String mappingCode;
	
	private String mappingSource;
	
	private String mappingSourceName;
	
	public TerminologyMappingDTO() {
	}
	
	public TerminologyMappingDTO(Integer conceptId, String displayName, String uuid) {
		this.conceptId = conceptId;
		this.displayName = displayName;
		this.uuid = uuid;
		this.hasMapping = false;
	}
	
	public Integer getConceptId() {
		return conceptId;
	}
	
	public void setConceptId(Integer conceptId) {
		this.conceptId = conceptId;
	}
	
	public String getDisplayName() {
		return displayName;
	}
	
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}
	
	public String getUuid() {
		return uuid;
	}
	
	public void setUuid(String uuid) {
		this.uuid = uuid;
	}
	
	public Boolean getHasMapping() {
		return hasMapping;
	}
	
	public void setHasMapping(Boolean hasMapping) {
		this.hasMapping = hasMapping;
	}
	
	public String getMappingCode() {
		return mappingCode;
	}
	
	public void setMappingCode(String mappingCode) {
		this.mappingCode = mappingCode;
	}
	
	public String getMappingSource() {
		return mappingSource;
	}
	
	public void setMappingSource(String mappingSource) {
		this.mappingSource = mappingSource;
	}
	
	public String getMappingSourceName() {
		return mappingSourceName;
	}
	
	public void setMappingSourceName(String mappingSourceName) {
		this.mappingSourceName = mappingSourceName;
	}
}
