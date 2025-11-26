package org.openmrs.module.sihsalusinterop.api.mapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.*;
import org.openmrs.Obs;

import java.util.Date;

/**
 * DyakuObservationMapper - Conversor de Obs OpenMRS a FHIR R4 Observation En OpenMRS, todo lo
 * clínico que no es Order ni Allergy = Obs, y se mapea a Observation FHIR. No hay perfil específico
 * Dyaku para Observation, se usa FHIR estándar. Hospital Santa Clotilde - SIH.SALUS
 */
public class DyakuObservationMapper {
	
	private static final Log log = LogFactory.getLog(DyakuObservationMapper.class);
	
	/**
	 * Convierte un Obs de OpenMRS a Observation FHIR R4
	 * 
	 * @param obs Obs de OpenMRS a convertir
	 * @param patientReference Referencia al paciente (ej: "Patient/uuid")
	 * @param encounterReference Referencia al encuentro (ej: "Encounter/uuid")
	 * @return Recurso FHIR Observation
	 */
	public static Observation toDyakuFhir(Obs obs, String patientReference, String encounterReference) {
		if (obs == null) {
			throw new IllegalArgumentException("Obs no puede ser nulo");
		}
		
		log.debug("Convirtiendo Obs [" + obs.getId() + "] a Observation FHIR R4");
		
		Observation observation = new Observation();
		
		// Meta
		Meta meta = new Meta();
		meta.setLastUpdated(new Date());
		observation.setMeta(meta);
		
		// Estado
		if (obs.getVoided() != null && obs.getVoided()) {
			observation.setStatus(Observation.ObservationStatus.CANCELLED);
		} else {
			observation.setStatus(Observation.ObservationStatus.FINAL);
		}
		
		// Categoría según ConceptClass
		if (obs.getConcept() != null && obs.getConcept().getConceptClass() != null) {
			String conceptClass = obs.getConcept().getConceptClass().getName().toUpperCase();
			
			CodeableConcept category = new CodeableConcept();
			if (conceptClass.contains("VITALS") || conceptClass.contains("SIGNO") || conceptClass.contains("VITAL")
			        || conceptClass.contains("SIGN")) {
				category.addCoding().setSystem("http://terminology.hl7.org/CodeSystem/observation-category")
				        .setCode("vital-signs").setDisplay("Vital Signs");
				observation.addCategory(category);
			} else if (conceptClass.contains("LAB") || conceptClass.contains("TEST") || conceptClass.contains("LABORATORIO")
			        || conceptClass.contains("EXAMEN")) {
				category.addCoding().setSystem("http://terminology.hl7.org/CodeSystem/observation-category")
				        .setCode("laboratory").setDisplay("Laboratory");
				observation.addCategory(category);
			} else if (conceptClass.contains("FINDING") || conceptClass.contains("HALLAZGO")) {
				category.addCoding().setSystem("http://terminology.hl7.org/CodeSystem/observation-category").setCode("exam")
				        .setDisplay("Exam");
				observation.addCategory(category);
			}
		}
		
		// Código (Concept)
		if (obs.getConcept() != null) {
			CodeableConcept code = new CodeableConcept();
			code.addCoding().setSystem("http://openmrs.org/concepts").setCode(obs.getConcept().getUuid())
			        .setDisplay(obs.getConcept().getDisplayString());
			code.setText(obs.getConcept().getDisplayString());
			observation.setCode(code);
		}
		
		// Referencia al paciente
		if (patientReference != null) {
			observation.getSubject().setReference(patientReference);
		}
		
		// Referencia al encuentro
		if (encounterReference != null) {
			observation.getEncounter().setReference(encounterReference);
		}
		
		// Fecha de la observación
		if (obs.getObsDatetime() != null) {
			observation.setEffective(new DateTimeType(obs.getObsDatetime()));
		}
		
		// Valor según el tipo de Obs
		if (obs.getValueCoded() != null) {
			CodeableConcept value = new CodeableConcept();
			value.addCoding().setSystem("http://openmrs.org/concepts").setCode(obs.getValueCoded().getUuid())
			        .setDisplay(obs.getValueCoded().getDisplayString());
			value.setText(obs.getValueCoded().getDisplayString());
			observation.setValue(value);
		} else if (obs.getValueNumeric() != null) {
			Quantity quantity = new Quantity();
			quantity.setValue(obs.getValueNumeric());
			// Intentar obtener unidades desde el concepto
			if (obs.getConcept() != null) {
				// El método puede ser getUnits() o puede estar en un atributo
				// Por ahora, si hay unidades en el concepto, usarlas
				String units = null;
				try {
					java.lang.reflect.Method getUnits = obs.getConcept().getClass().getMethod("getUnits");
					units = (String) getUnits.invoke(obs.getConcept());
				}
				catch (Exception e) {
					// Si no existe el método, continuar sin unidades
				}
				if (units != null && !units.isEmpty()) {
					quantity.setUnit(units);
				}
			}
			observation.setValue(quantity);
		} else if (obs.getValueText() != null) {
			observation.setValue(new StringType(obs.getValueText()));
		} else if (obs.getValueDatetime() != null) {
			observation.setValue(new DateTimeType(obs.getValueDatetime()));
		} else if (obs.getValueBoolean() != null) {
			observation.setValue(new BooleanType(obs.getValueBoolean()));
		}
		
		// Interpretación si está disponible
		if (obs.getInterpretation() != null) {
			CodeableConcept interpretation = new CodeableConcept();
			// Usar el enum o nombre del interpretation
			interpretation.setText(obs.getInterpretation().toString());
			observation.addInterpretation(interpretation);
		}
		
		// Comentarios
		if (obs.getComment() != null && !obs.getComment().isEmpty()) {
			observation.addNote().setText(obs.getComment());
		}
		
		log.debug("✓ Observation convertido exitosamente");
		return observation;
	}
}
