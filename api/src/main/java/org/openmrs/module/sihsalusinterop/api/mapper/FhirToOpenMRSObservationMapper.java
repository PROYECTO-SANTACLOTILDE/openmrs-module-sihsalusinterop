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
import org.hl7.fhir.r4.model.Quantity;
import org.openmrs.Concept;
import org.openmrs.ConceptNumeric;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.context.Context;

import java.util.Date;

/**
 * Mapper para convertir recursos FHIR Observation a Obs en OpenMRS
 */
public class FhirToOpenMRSObservationMapper {
	
	protected static final Log log = LogFactory.getLog(FhirToOpenMRSObservationMapper.class);
	
	/**
	 * Convierte un Observation FHIR a Obs en OpenMRS
	 * 
	 * @param fhirObservation Observation desde RENHICE
	 * @param patient Patient OpenMRS
	 * @param encounter Encounter OpenMRS (puede ser null)
	 * @return Obs con la observación
	 */
	public static Obs mapToOpenMRS(org.hl7.fhir.r4.model.Observation fhirObservation, Patient patient, Encounter encounter) {
		Obs obs = new Obs();
		
		try {
			obs.setPerson(patient);
			
			// Fecha de la observación
			if (fhirObservation.hasEffectiveDateTimeType()) {
				obs.setObsDatetime(fhirObservation.getEffectiveDateTimeType().getValue());
			} else {
				obs.setObsDatetime(new Date());
			}
			
			// Encounter asociado
			if (encounter != null) {
				obs.setEncounter(encounter);
			}
			
			// Concepto (tipo de observación)
			if (fhirObservation.hasCode()) {
				CodeableConcept code = fhirObservation.getCode();
				String conceptName = null;
				String loincCode = null;
				
				// Buscar código LOINC o texto
				for (Coding coding : code.getCoding()) {
					if (coding.hasSystem() && coding.getSystem().contains("loinc")) {
						loincCode = coding.getCode();
						if (coding.hasDisplay()) {
							conceptName = coding.getDisplay();
						}
						break;
					}
				}
				
				if (code.hasText()) {
					conceptName = code.getText();
				}
				
				// Buscar concepto en OpenMRS
				Concept concept = findConceptByName(conceptName, loincCode);
				if (concept != null) {
					obs.setConcept(concept);
				} else {
					log.warn("No se encontró concepto para: " + conceptName);
					// Usar concepto genérico de observación
					concept = Context.getConceptService().getConceptByName("Observations");
					if (concept != null) {
						obs.setConcept(concept);
					}
				}
			}
			
			// Valor de la observación
			if (fhirObservation.hasValueQuantity()) {
				Quantity quantity = fhirObservation.getValueQuantity();
				if (quantity.hasValue()) {
					obs.setValueNumeric(quantity.getValue().doubleValue());
					
					// Unidades (como comentario si es necesario)
					if (quantity.hasUnit()) {
						obs.setComment("Unidad: " + quantity.getUnit());
					}
				}
			} else if (fhirObservation.hasValueStringType()) {
				obs.setValueText(fhirObservation.getValueStringType().getValue());
			} else if (fhirObservation.hasValueCodeableConcept()) {
				CodeableConcept valueCode = fhirObservation.getValueCodeableConcept();
				if (valueCode.hasText()) {
					obs.setValueText(valueCode.getText());
				}
			}
			
			// Location
			if (Context.getLocationService().getAllLocations(false).size() > 0) {
				obs.setLocation(Context.getLocationService().getAllLocations(false).get(0));
			}
			
		} catch (Exception e) {
			log.error("Error al mapear Observation FHIR a Obs OpenMRS", e);
		}
		
		return obs;
	}
	
	/**
	 * Busca un concepto por nombre o código LOINC
	 */
	private static Concept findConceptByName(String conceptName, String loincCode) {
		if (conceptName == null && loincCode == null) {
			return null;
		}
		
		try {
			// Mapeo de nombres comunes de signos vitales
			if (conceptName != null) {
				conceptName = conceptName.toLowerCase();
				
				if (conceptName.contains("blood pressure") || conceptName.contains("presión arterial")) {
					Concept bp = Context.getConceptService().getConceptByName("Blood Pressure");
					if (bp != null)
						return bp;
				}
				
				if (conceptName.contains("temperature") || conceptName.contains("temperatura")) {
					Concept temp = Context.getConceptService().getConceptByName("Temperature (C)");
					if (temp != null)
						return temp;
				}
				
				if (conceptName.contains("pulse") || conceptName.contains("heart rate") || conceptName.contains("pulso")) {
					Concept pulse = Context.getConceptService().getConceptByName("Pulse");
					if (pulse != null)
						return pulse;
				}
				
				if (conceptName.contains("respiratory rate") || conceptName.contains("frecuencia respiratoria")) {
					Concept resp = Context.getConceptService().getConceptByName("Respiratory Rate");
					if (resp != null)
						return resp;
				}
				
				if (conceptName.contains("weight") || conceptName.contains("peso")) {
					Concept weight = Context.getConceptService().getConceptByName("Weight (kg)");
					if (weight != null)
						return weight;
				}
				
				if (conceptName.contains("height") || conceptName.contains("altura") || conceptName.contains("talla")) {
					Concept height = Context.getConceptService().getConceptByName("Height (cm)");
					if (height != null)
						return height;
				}
			}
			
			// Buscar por nombre directo
			if (conceptName != null) {
				Concept concept = Context.getConceptService().getConceptByName(conceptName);
				if (concept != null) {
					return concept;
				}
			}
			
		}
		catch (Exception e) {
			log.error("Error buscando concepto: " + conceptName, e);
		}
		
		return null;
	}
}
