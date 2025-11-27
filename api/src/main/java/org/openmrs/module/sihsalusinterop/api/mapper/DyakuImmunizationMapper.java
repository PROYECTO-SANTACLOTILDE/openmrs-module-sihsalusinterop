package org.openmrs.module.sihsalusinterop.api.mapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.*;
import org.openmrs.Obs;
import org.openmrs.Concept;

/**
 * DyakuImmunizationMapper - Mapea Obs de vacunación de OpenMRS a FHIR Immunization R4 Implementa el
 * perfil peruano InmunizacionPe según estándares de MINSA/RENHICE Hospital Santa Clotilde -
 * SIH.SALUS
 */
public class DyakuImmunizationMapper {
	
	private static final Log log = LogFactory.getLog(DyakuImmunizationMapper.class);
	
	// Perfil peruano
	public static final String PROFILE_IMMUNIZATION_PE = "https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/InmunizacionPe";
	
	// Sistemas de codificación
	public static final String SYSTEM_CVX = "http://hl7.org/fhir/sid/cvx"; // CDC Vaccine Codes
	
	public static final String SYSTEM_ATC = "http://www.whocc.no/atc"; // WHO ATC
	
	/**
	 * Mapea un Obs de vacunación de OpenMRS a FHIR Immunization R4
	 * 
	 * @param obs Obs que representa una vacunación en OpenMRS
	 * @param patientRef Referencia al Patient FHIR
	 * @param encounterRef Referencia al Encounter FHIR (opcional)
	 * @return Immunization FHIR R4 según perfil InmunizacionPe
	 */
	public static Immunization toDyakuFhir(Obs obs, String patientRef, String encounterRef) {
		if (obs == null) {
			throw new IllegalArgumentException("Obs no puede ser null");
		}
		
		Immunization immunization = new Immunization();
		
		// Meta: perfil peruano
		Meta meta = new Meta();
		meta.addProfile(PROFILE_IMMUNIZATION_PE);
		immunization.setMeta(meta);
		
		// ID basado en UUID de OpenMRS
		immunization.setId(obs.getUuid());
		
		// Status: completed (asumimos que si está registrado, fue administrado)
		immunization.setStatus(Immunization.ImmunizationStatus.COMPLETED);
		
		// Código de la vacuna
		CodeableConcept vaccineCode = mapVaccineCode(obs);
		immunization.setVaccineCode(vaccineCode);
		
		// Referencia al paciente (obligatorio)
		Reference patRef = new Reference(patientRef);
		immunization.setPatient(patRef);
		
		// Fecha de administración (occurrenceDateTime)
		if (obs.getObsDatetime() != null) {
			immunization.setOccurrence(new DateTimeType(obs.getObsDatetime()));
		} else {
			// Fallback a fecha de creación
			immunization.setOccurrence(new DateTimeType(obs.getDateCreated()));
		}
		
		// Referencia al Encounter si está disponible
		if (encounterRef != null && !encounterRef.isEmpty()) {
			Reference encRef = new Reference(encounterRef);
			immunization.setEncounter(encRef);
		}
		
		// primarySource: true (asumimos que es información de primera mano)
		immunization.setPrimarySource(true);
		
		// location: usar location del encounter si está disponible
		if (obs.getLocation() != null) {
			Reference locRef = new Reference();
			locRef.setReference("Location/" + obs.getLocation().getUuid());
			locRef.setDisplay(obs.getLocation().getName());
			immunization.setLocation(locRef);
		}
		
		// performer: quién administró la vacuna
		if (obs.getCreator() != null) {
			Immunization.ImmunizationPerformerComponent performer = new Immunization.ImmunizationPerformerComponent();
			Reference practitionerRef = new Reference();
			practitionerRef.setReference("Practitioner/" + obs.getCreator().getUuid());
			if (obs.getCreator().getPerson() != null) {
				practitionerRef.setDisplay(obs.getCreator().getPerson().getPersonName().getFullName());
			}
			performer.setActor(practitionerRef);
			immunization.addPerformer(performer);
		}
		
		// reasonCode: razón de la vacunación si está disponible como comentario
		if (obs.getComment() != null && !obs.getComment().isEmpty()) {
			CodeableConcept reasonCode = new CodeableConcept();
			reasonCode.setText(obs.getComment());
			immunization.addReasonCode(reasonCode);
		}
		
		// doseQuantity: cantidad administrada (si está disponible como valor numérico)
		if (obs.getValueNumeric() != null) {
			Quantity dose = new Quantity();
			dose.setValue(obs.getValueNumeric());
			dose.setUnit("ml");
			dose.setSystem("http://unitsofmeasure.org");
			dose.setCode("ml");
			immunization.setDoseQuantity(dose);
		}
		
		// note: observaciones adicionales
		if (obs.getValueText() != null && !obs.getValueText().isEmpty()) {
			Annotation note = new Annotation();
			note.setText(obs.getValueText());
			immunization.addNote(note);
		}
		
		log.debug("Immunization mapeado: " + vaccineCode.getText());
		
		return immunization;
	}
	
	/**
	 * Mapea el código de la vacuna desde el concepto de OpenMRS
	 */
	private static CodeableConcept mapVaccineCode(Obs obs) {
		CodeableConcept vaccineCode = new CodeableConcept();
		
		Concept concept = obs.getConcept();
		if (concept == null) {
			vaccineCode.setText("Vacuna no especificada");
			return vaccineCode;
		}
		
		// Usar el nombre del concepto como texto descriptivo
		String vaccineName = concept.getDisplayString();
		if (vaccineName != null && !vaccineName.isEmpty()) {
			vaccineCode.setText(vaccineName);
		}
		
		// Si el valueCoded está presente, usarlo
		if (obs.getValueCoded() != null) {
			Concept valueConcept = obs.getValueCoded();
			String valueConceptName = valueConcept.getDisplayString();
			if (valueConceptName != null && !valueConceptName.isEmpty()) {
				vaccineCode.setText(valueConceptName);
			}
			
			// Intentar mapear a CVX si hay mappings
			String cvxCode = findConceptMapping(valueConcept, SYSTEM_CVX);
			if (cvxCode != null) {
				vaccineCode.addCoding().setSystem(SYSTEM_CVX).setCode(cvxCode).setDisplay(valueConceptName);
			} else {
				// Usar UUID como código si no hay mapping
				vaccineCode.addCoding().setSystem("urn:oid:2.16.840.1.113883.3.7201") // OpenMRS Concept Dictionary
				        .setCode(valueConcept.getUuid()).setDisplay(valueConceptName);
			}
		} else {
			// Usar el concepto principal si no hay valueCoded
			String cvxCode = findConceptMapping(concept, SYSTEM_CVX);
			if (cvxCode != null) {
				vaccineCode.addCoding().setSystem(SYSTEM_CVX).setCode(cvxCode).setDisplay(vaccineName);
			} else {
				// Fallback: usar UUID
				vaccineCode.addCoding().setSystem("urn:oid:2.16.840.1.113883.3.7201").setCode(concept.getUuid())
				        .setDisplay(vaccineName);
			}
		}
		
		return vaccineCode;
	}
	
	/**
	 * Busca un mapeo del concepto a un sistema externo
	 */
	private static String findConceptMapping(Concept concept, String targetSystem) {
		if (concept == null || concept.getConceptMappings() == null) {
			return null;
		}
		
		// Buscar en los mappings del concepto
		for (org.openmrs.ConceptMap mapping : concept.getConceptMappings()) {
			if (mapping.getConceptReferenceTerm() != null && 
			    mapping.getConceptReferenceTerm().getConceptSource() != null) {
				
				String sourceHl7Code = mapping.getConceptReferenceTerm().getConceptSource().getHl7Code();
				String sourceName = mapping.getConceptReferenceTerm().getConceptSource().getName();
				
				// Comparar con sistema objetivo (por HL7 code o nombre)
				if (targetSystem.contains(sourceHl7Code) || 
				    (sourceName != null && targetSystem.toLowerCase().contains(sourceName.toLowerCase()))) {
					return mapping.getConceptReferenceTerm().getCode();
				}
			}
		}
		
		return null;
	}
	
	/**
	 * Verifica si un Obs representa una vacunación
	 * 
	 * @param obs Obs a verificar
	 * @return true si el Obs representa una vacunación
	 */
	public static boolean isImmunizationObs(Obs obs) {
		if (obs == null || obs.getConcept() == null) {
			return false;
		}
		
		// Verificar por clase de concepto
		if (obs.getConcept().getConceptClass() != null) {
			String className = obs.getConcept().getConceptClass().getName();
			if (className != null
			        && (className.equalsIgnoreCase("Immunization") || className.equalsIgnoreCase("Vaccine") || className
			                .equalsIgnoreCase("Vacunacion"))) {
				return true;
			}
		}
		
		// Verificar por nombre del concepto (búsqueda de palabras clave)
		String conceptName = obs.getConcept().getDisplayString();
		if (conceptName != null) {
			String lowerName = conceptName.toLowerCase();
			return lowerName.contains("vacun") || lowerName.contains("inmuniz") || lowerName.contains("vaccine")
			        || lowerName.contains("immuniz");
		}
		
		return false;
	}
}
