package org.openmrs.module.sihsalusinterop.api.mapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.*;
import org.openmrs.Concept;
import org.openmrs.ConceptMap;
import org.openmrs.Diagnosis;
import org.openmrs.Obs;

/**
 * DyakuConditionMapper - Conversor de Conditions OpenMRS a FHIR R4 (Perfil ConditionPe) Perfil
 * peruano según: https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/ConditionPe Requisitos:
 * - Código CIE-10 obligatorio - System: http://hl7.org/fhir/sid/icd-10 - Onset Period obligatorio
 * Hospital Santa Clotilde - SIH.SALUS
 */
public class DyakuConditionMapper {
	
	private static final Log log = LogFactory.getLog(DyakuConditionMapper.class);
	
	public static final String PROFILE_CONDITION_PE = "https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/ConditionPe";
	
	public static final String SYSTEM_CIE10 = "http://hl7.org/fhir/sid/icd-10";
	
	public static final String VALUE_SET_CIE10 = "https://www.gob.pe/minsa/RENHICE/fhir/ValueSet/CIE10VS";
	
	/**
	 * Convierte un Diagnóstico de OpenMRS a Condition FHIR R4 (Perfil ConditionPe)
	 */
	public static org.hl7.fhir.r4.model.Condition toDyakuFhir(Diagnosis diagnosis, String patientReference) {
		if (diagnosis == null) {
			throw new IllegalArgumentException("Diagnosis no puede ser nulo");
		}
		
		log.info("Convirtiendo Diagnosis [" + diagnosis.getDiagnosisId() + "] a Condition FHIR R4 (Perfil ConditionPe)");
		
		org.hl7.fhir.r4.model.Condition condition = new org.hl7.fhir.r4.model.Condition();
		
		// Meta con perfil peruano
		Meta meta = new Meta();
		meta.addProfile(PROFILE_CONDITION_PE);
		condition.setMeta(meta);
		
		// Estado de verificación (obligatorio según perfil)
		CodeableConcept verificationStatus = new CodeableConcept();
		verificationStatus.addCoding().setSystem("http://terminology.hl7.org/CodeSystem/condition-ver-status")
		        .setCode("confirmed").setDisplay("Confirmado");
		condition.setVerificationStatus(verificationStatus);
		
		// Código CIE-10 (obligatorio según perfil)
		CodeableConcept code = mapCie10Code(diagnosis);
		condition.setCode(code);
		
		// Referencia al paciente (obligatorio según perfil)
		condition.getSubject().setReference(patientReference);
		
		// Inicio del período (obligatorio según perfil)
		if (diagnosis.getEncounter() != null && diagnosis.getEncounter().getEncounterDatetime() != null) {
			Period onsetPeriod = new Period();
			onsetPeriod.setStart(diagnosis.getEncounter().getEncounterDatetime());
			condition.setOnset(onsetPeriod);
		} else if (diagnosis.getDateCreated() != null) {
			Period onsetPeriod = new Period();
			onsetPeriod.setStart(diagnosis.getDateCreated());
			condition.setOnset(onsetPeriod);
		}
		
		// Notas (obligatorio según perfil, pero opcional en contenido)
		// OpenMRS Diagnosis puede tener comentarios en diferentes lugares
		if (diagnosis.getCertainty() != null) {
			condition.addNote().setText("Certeza: " + diagnosis.getCertainty());
		}
		
		log.info("✓ Condition convertido exitosamente");
		return condition;
	}
	
	/**
	 * Convierte un Obs de diagnóstico a Condition FHIR
	 */
	public static org.hl7.fhir.r4.model.Condition toDyakuFhir(Obs obs, String patientReference) {
		if (obs == null) {
			throw new IllegalArgumentException("Obs no puede ser nulo");
		}
		
		org.hl7.fhir.r4.model.Condition condition = new org.hl7.fhir.r4.model.Condition();
		
		// Meta con perfil peruano
		Meta meta = new Meta();
		meta.addProfile(PROFILE_CONDITION_PE);
		condition.setMeta(meta);
		
		// Estado de verificación
		CodeableConcept verificationStatus = new CodeableConcept();
		verificationStatus.addCoding().setSystem("http://terminology.hl7.org/CodeSystem/condition-ver-status")
		        .setCode("confirmed").setDisplay("Confirmado");
		condition.setVerificationStatus(verificationStatus);
		
		// Código CIE-10
		CodeableConcept code = mapCie10CodeFromObs(obs);
		condition.setCode(code);
		
		// Referencia al paciente
		condition.getSubject().setReference(patientReference);
		
		// Inicio del período
		if (obs.getObsDatetime() != null) {
			Period onsetPeriod = new Period();
			onsetPeriod.setStart(obs.getObsDatetime());
			condition.setOnset(onsetPeriod);
		}
		
		// Notas
		if (obs.getComment() != null) {
			condition.addNote().setText(obs.getComment());
		}
		
		return condition;
	}
	
	/**
	 * Mapea el código CIE-10 desde un Diagnosis
	 */
	private static CodeableConcept mapCie10Code(Diagnosis diagnosis) {
		CodeableConcept code = new CodeableConcept();
		
		Concept concept = diagnosis.getDiagnosis().getCoded();
		if (concept != null) {
			// Buscar mapeo CIE-10 en los ConceptMaps
			String cie10Code = findCie10Mapping(concept);
			
			if (cie10Code != null) {
				code.addCoding().setSystem(SYSTEM_CIE10).setCode(cie10Code).setDisplay(concept.getDisplayString());
			} else {
				// Si no hay mapeo CIE-10, usar el concepto directamente con warning
				log.warn("⚠ Concept [" + concept.getUuid() + "] no tiene mapeo CIE-10. Usando código OpenMRS.");
				code.addCoding().setSystem(SYSTEM_CIE10).setCode(concept.getUuid()) // Fallback
				        .setDisplay(concept.getDisplayString());
			}
		} else {
			// Si no hay concepto, usar texto libre
			log.warn("⚠ Diagnosis sin concepto codificado. Usando texto libre.");
			String nonCoded = diagnosis.getDiagnosis().getNonCoded() != null ? diagnosis.getDiagnosis().getNonCoded()
			        : "Sin diagnóstico codificado";
			code.addCoding().setSystem(SYSTEM_CIE10).setCode("UNKNOWN").setDisplay(nonCoded);
			code.setText(nonCoded);
		}
		
		// Texto (obligatorio según perfil)
		if (concept != null) {
			code.setText(concept.getDisplayString());
		}
		
		return code;
	}
	
	/**
	 * Mapea el código CIE-10 desde un Obs
	 */
	private static CodeableConcept mapCie10CodeFromObs(Obs obs) {
		CodeableConcept code = new CodeableConcept();
		
		Concept concept = obs.getValueCoded();
		if (concept != null) {
			String cie10Code = findCie10Mapping(concept);
			
			if (cie10Code != null) {
				code.addCoding().setSystem(SYSTEM_CIE10).setCode(cie10Code).setDisplay(concept.getDisplayString());
			} else {
				code.addCoding().setSystem(SYSTEM_CIE10).setCode(concept.getUuid()).setDisplay(concept.getDisplayString());
			}
			
			code.setText(concept.getDisplayString());
		} else {
			code.addCoding().setSystem(SYSTEM_CIE10).setCode("UNKNOWN").setDisplay(obs.getValueText());
			code.setText(obs.getValueText());
		}
		
		return code;
	}
	
	/**
	 * Busca un mapeo CIE-10 en los ConceptMaps de un Concept
	 */
	private static String findCie10Mapping(Concept concept) {
		if (concept == null || concept.getConceptMappings() == null) {
			return null;
		}
		
		for (ConceptMap map : concept.getConceptMappings()) {
			if (map.getConceptReferenceTerm() != null &&
			    map.getConceptReferenceTerm().getConceptSource() != null) {
				
				String sourceName = map.getConceptReferenceTerm().getConceptSource().getName();
				if (sourceName != null && 
				    (sourceName.toUpperCase().contains("CIE-10") || 
				     sourceName.toUpperCase().contains("ICD-10"))) {
					return map.getConceptReferenceTerm().getCode();
				}
			}
		}
		
		return null;
	}
}
