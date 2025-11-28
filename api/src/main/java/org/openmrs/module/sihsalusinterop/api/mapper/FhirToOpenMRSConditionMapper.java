/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.sihsalusinterop.api.mapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.openmrs.Concept;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;

import java.util.Date;

/**
 * Mapper para convertir recursos FHIR Condition a Obs (diagnósticos) en OpenMRS
 */
public class FhirToOpenMRSConditionMapper {
	
	protected static final Log log = LogFactory.getLog(FhirToOpenMRSConditionMapper.class);
	
	/**
	 * Convierte un Condition FHIR a Obs (diagnóstico) en OpenMRS
	 * 
	 * @param fhirCondition Condition desde RENHICE
	 * @param patient Patient OpenMRS
	 * @param encounter Encounter OpenMRS (puede ser null)
	 * @return Obs con el diagnóstico
	 */
	public static Obs mapToOpenMRS(org.hl7.fhir.r4.model.Condition fhirCondition, Patient patient, Encounter encounter) {
		Obs obs = new Obs();
		
		try {
			obs.setPerson(patient);
			
			// Fecha del diagnóstico
			if (fhirCondition.hasRecordedDate()) {
				obs.setObsDatetime(fhirCondition.getRecordedDate());
			} else {
				obs.setObsDatetime(new Date());
			}
			
			// Encounter asociado (si existe)
			if (encounter != null) {
				obs.setEncounter(encounter);
			}
			
			// Concepto de diagnóstico
			// Buscar concepto "Visit Diagnoses" o crear uno genérico
			Concept diagnosisConcept = Context.getConceptService().getConceptByName("Visit Diagnoses");
			if (diagnosisConcept == null) {
				diagnosisConcept = Context.getConceptService().getConceptByName("Diagnosis");
			}
			if (diagnosisConcept == null) {
				// Buscar por ID si existe
				diagnosisConcept = Context.getConceptService().getConcept(159947); // Visit Diagnoses concept ID común
			}
			
			if (diagnosisConcept != null) {
				obs.setConcept(diagnosisConcept);
			} else {
				log.warn("No se encontró concepto de diagnóstico. Obs puede no guardarse correctamente.");
			}
			
			// Valor del diagnóstico desde código CIE-10
			if (fhirCondition.hasCode()) {
				CodeableConcept code = fhirCondition.getCode();
				
				// Buscar código CIE-10
				String cie10Code = null;
				String displayText = null;
				
				for (Coding coding : code.getCoding()) {
					if (coding.hasSystem() && coding.getSystem().contains("icd-10")) {
						cie10Code = coding.getCode();
						displayText = coding.getDisplay();
						break;
					}
				}
				
				if (code.hasText()) {
					displayText = code.getText();
				}
				
				// Buscar concepto por código CIE-10
				if (cie10Code != null) {
					Concept valueConcept = findConceptByICD10(cie10Code);
					if (valueConcept != null) {
						obs.setValueCoded(valueConcept);
						log.info("Diagnóstico mapeado con CIE-10: " + cie10Code);
					} else {
						// Si no se encuentra, guardar como texto
						if (displayText != null) {
							obs.setValueText(displayText + " (CIE-10: " + cie10Code + ")");
							log.info("Diagnóstico guardado como texto: " + displayText);
						}
					}
				} else if (displayText != null) {
					obs.setValueText(displayText);
					log.info("Diagnóstico guardado como texto: " + displayText);
				}
			}
			
			// Location (requerido por OpenMRS)
			if (Context.getLocationService().getAllLocations(false).size() > 0) {
				obs.setLocation(Context.getLocationService().getAllLocations(false).get(0));
			}
			
		} catch (Exception e) {
			log.error("Error al mapear Condition FHIR a Obs OpenMRS", e);
		}
		
		return obs;
	}
	
	/**
	 * Busca un concepto en OpenMRS por código CIE-10
	 */
	private static Concept findConceptByICD10(String cie10Code) {
		try {
			// Buscar concepto con mapeo CIE-10
			for (Concept concept : Context.getConceptService().getAllConcepts()) {
				if (concept.getConceptMappings() != null) {
					for (ConceptMap mapping : concept.getConceptMappings()) {
						ConceptReferenceTerm term = mapping.getConceptReferenceTerm();
						if (term != null && term.getConceptSource() != null) {
							String sourceName = term.getConceptSource().getName();
							if (sourceName != null && 
							    (sourceName.contains("ICD-10") || sourceName.contains("ICD10"))) {
								if (cie10Code.equals(term.getCode())) {
									return concept;
								}
							}
						}
					}
				}
			}
		} catch (Exception e) {
			log.error("Error buscando concepto por CIE-10: " + cie10Code, e);
		}
		
		return null;
	}
}
