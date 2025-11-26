package org.openmrs.module.sihsalusinterop.api.mapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.*;
import org.openmrs.Allergy;

import java.util.Date;

/**
 * DyakuAllergyIntoleranceMapper - Conversor de Allergy OpenMRS a FHIR R4 (Perfil AlergiaPe) Perfil
 * peruano según: https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/AlergiaPe Hospital Santa
 * Clotilde - SIH.SALUS
 */
public class DyakuAllergyIntoleranceMapper {
	
	private static final Log log = LogFactory.getLog(DyakuAllergyIntoleranceMapper.class);
	
	/**
	 * URL del perfil FHIR para alergias del MINSA (Dyaku)
	 */
	public static final String PROFILE_ALERGIA_PE = "https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/AlergiaPe";
	
	/**
	 * Convierte una Allergy de OpenMRS a AllergyIntolerance FHIR R4 (Perfil AlergiaPe)
	 * 
	 * @param allergy Alergia de OpenMRS a convertir
	 * @param patientReference Referencia al paciente (ej: "Patient/uuid")
	 * @return Recurso FHIR AllergyIntolerance compatible con RENHICE
	 */
	public static AllergyIntolerance toDyakuFhir(Allergy allergy, String patientReference) {
		if (allergy == null) {
			throw new IllegalArgumentException("Allergy no puede ser nula");
		}
		
		log.info("Convirtiendo Allergy [" + allergy.getAllergyId() + "] a AllergyIntolerance FHIR R4 (Perfil AlergiaPe)");
		
		AllergyIntolerance fhirAllergy = new AllergyIntolerance();
		
		// Meta con perfil peruano
		Meta meta = new Meta();
		meta.addProfile(PROFILE_ALERGIA_PE);
		meta.setLastUpdated(new Date());
		fhirAllergy.setMeta(meta);
		
		// Tipo (allergy | intolerance) - Por defecto ALLERGY
		fhirAllergy.setType(AllergyIntolerance.AllergyIntoleranceType.ALLERGY);
		
		// Categoría (obligatorio según perfil AlergiaPe)
		// Intentar determinar la categoría desde el alergeno
		if (allergy.getAllergen() != null) {
			String allergenDisplay = "";
			if (allergy.getAllergen().getCodedAllergen() != null) {
				allergenDisplay = allergy.getAllergen().getCodedAllergen().getDisplayString().toUpperCase();
			} else if (allergy.getAllergen().getNonCodedAllergen() != null) {
				allergenDisplay = allergy.getAllergen().getNonCodedAllergen().toUpperCase();
			}
			
			if (allergenDisplay.contains("FOOD") || allergenDisplay.contains("COMIDA")) {
				fhirAllergy.addCategory(AllergyIntolerance.AllergyIntoleranceCategory.FOOD);
			} else if (allergenDisplay.contains("DRUG") || allergenDisplay.contains("MEDICATION") || 
			          allergenDisplay.contains("MEDICAMENTO")) {
				fhirAllergy.addCategory(AllergyIntolerance.AllergyIntoleranceCategory.MEDICATION);
			} else if (allergenDisplay.contains("ENVIRONMENT") || allergenDisplay.contains("AMBIENTE")) {
				fhirAllergy.addCategory(AllergyIntolerance.AllergyIntoleranceCategory.ENVIRONMENT);
			} else {
				// Por defecto, usar MEDICATION si no se puede determinar
				fhirAllergy.addCategory(AllergyIntolerance.AllergyIntoleranceCategory.MEDICATION);
			}
		} else {
			// Por defecto, usar MEDICATION
			fhirAllergy.addCategory(AllergyIntolerance.AllergyIntoleranceCategory.MEDICATION);
		}
		
		// Criticality (obligatorio según perfil AlergiaPe)
		if (allergy.getSeverity() != null) {
			String severity = allergy.getSeverity().getDisplayString().toUpperCase();
			if (severity.contains("MILD") || severity.contains("LEVE") || severity.contains("LOW")) {
				fhirAllergy.setCriticality(AllergyIntolerance.AllergyIntoleranceCriticality.LOW);
			} else if (severity.contains("SEVERE") || severity.contains("SEVERO") || severity.contains("HIGH")) {
				fhirAllergy.setCriticality(AllergyIntolerance.AllergyIntoleranceCriticality.HIGH);
			} else {
				// Por defecto, usar unable-to-assess (que es null en el enum, usamos null)
				fhirAllergy.setCriticality(null); // unable-to-assess
			}
		} else {
			// Por defecto, usar unable-to-assess (null)
			fhirAllergy.setCriticality(null);
		}
		
		// Código (obligatorio según perfil - debe tener text)
		CodeableConcept code = new CodeableConcept();
		if (allergy.getAllergen() != null) {
			String allergenName = null;
			if (allergy.getAllergen().getCodedAllergen() != null) {
				allergenName = allergy.getAllergen().getCodedAllergen().getDisplayString();
			} else if (allergy.getAllergen().getNonCodedAllergen() != null) {
				allergenName = allergy.getAllergen().getNonCodedAllergen();
			}
			
			if (allergenName != null && !allergenName.isEmpty()) {
				code.setText(allergenName); // Obligatorio según perfil
				// También agregar el código si está disponible
				if (allergy.getAllergen().getCodedAllergen() != null) {
					code.addCoding()
						.setCode(allergy.getAllergen().getCodedAllergen().getUuid())
						.setDisplay(allergenName);
				}
			} else {
				code.setText("Alergia no especificada");
			}
		} else {
			code.setText("Alergia sin información");
		}
		fhirAllergy.setCode(code);
		
		// Referencia al paciente (obligatorio según perfil AlergiaPe)
		fhirAllergy.getPatient().setReference(patientReference);
		
		// Fecha de inicio (onsetDateTime según perfil)
		if (allergy.getDateLastUpdated() != null) {
			fhirAllergy.setOnset(new DateTimeType(allergy.getDateLastUpdated()));
		} else if (allergy.getDateCreated() != null) {
			fhirAllergy.setOnset(new DateTimeType(allergy.getDateCreated()));
		}
		
		// Reacciones (obligatorio según perfil AlergiaPe)
		AllergyIntolerance.AllergyIntoleranceReactionComponent reaction = 
			new AllergyIntolerance.AllergyIntoleranceReactionComponent();
		
		// Descripción de la reacción (obligatorio según perfil)
		if (allergy.getReactions() != null && !allergy.getReactions().isEmpty()) {
			StringBuilder reactionDesc = new StringBuilder();
			for (org.openmrs.AllergyReaction openmrsReaction : allergy.getReactions()) {
				if (reactionDesc.length() > 0) {
					reactionDesc.append(", ");
				}
				if (openmrsReaction.getReaction() != null) {
					reactionDesc.append(openmrsReaction.getReaction().getDisplayString());
				}
			}
			if (reactionDesc.length() > 0) {
				reaction.setDescription(reactionDesc.toString());
			} else {
				reaction.setDescription("Reacción no especificada");
			}
		} else {
			reaction.setDescription("Reacción no especificada");
		}
		
		// Severidad de la reacción
		if (allergy.getSeverity() != null) {
			String severity = allergy.getSeverity().getDisplayString().toUpperCase();
			if (severity.contains("MILD") || severity.contains("LEVE")) {
				reaction.setSeverity(AllergyIntolerance.AllergyIntoleranceSeverity.MILD);
			} else if (severity.contains("MODERATE") || severity.contains("MODERADO")) {
				reaction.setSeverity(AllergyIntolerance.AllergyIntoleranceSeverity.MODERATE);
			} else if (severity.contains("SEVERE") || severity.contains("SEVERO")) {
				reaction.setSeverity(AllergyIntolerance.AllergyIntoleranceSeverity.SEVERE);
			}
		}
		
		fhirAllergy.addReaction(reaction);
		
		// Estado clínico
		CodeableConcept clinicalStatus = new CodeableConcept();
		if (allergy.getVoided() != null && allergy.getVoided()) {
			clinicalStatus.addCoding()
				.setSystem("http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical")
				.setCode("inactive")
				.setDisplay("Inactivo");
		} else {
			clinicalStatus.addCoding()
				.setSystem("http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical")
				.setCode("active")
				.setDisplay("Activo");
		}
		fhirAllergy.setClinicalStatus(clinicalStatus);
		
		log.info("✓ AllergyIntolerance convertido exitosamente");
		return fhirAllergy;
	}
}
