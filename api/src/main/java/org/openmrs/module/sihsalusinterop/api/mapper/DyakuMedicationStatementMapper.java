package org.openmrs.module.sihsalusinterop.api.mapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.*;
import org.openmrs.DrugOrder;

import java.util.Date;

/**
 * DyakuMedicationStatementMapper - Conversor de DrugOrder OpenMRS a FHIR R4 (Perfil
 * MedicationStatementPe) Perfil peruano según:
 * https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/MedicationStatementPe Hospital Santa
 * Clotilde - SIH.SALUS
 */
public class DyakuMedicationStatementMapper {
	
	private static final Log log = LogFactory.getLog(DyakuMedicationStatementMapper.class);
	
	/**
	 * URL del perfil FHIR para medicaciones del MINSA (Dyaku)
	 */
	public static final String PROFILE_MEDICATION_STATEMENT_PE = "https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/MedicationStatementPe";
	
	/**
	 * Convierte un DrugOrder de OpenMRS a MedicationStatement FHIR R4 (Perfil
	 * MedicationStatementPe)
	 * 
	 * @param drugOrder DrugOrder de OpenMRS a convertir
	 * @param patientReference Referencia al paciente (ej: "Patient/uuid")
	 * @return Recurso FHIR MedicationStatement compatible con RENHICE
	 */
	public static MedicationStatement toDyakuFhir(DrugOrder drugOrder, String patientReference) {
		if (drugOrder == null) {
			throw new IllegalArgumentException("DrugOrder no puede ser nulo");
		}
		
		log.info("Convirtiendo DrugOrder [" + drugOrder.getOrderId()
		        + "] a MedicationStatement FHIR R4 (Perfil MedicationStatementPe)");
		
		MedicationStatement medicationStatement = new MedicationStatement();
		
		// Meta con perfil peruano
		Meta meta = new Meta();
		meta.addProfile(PROFILE_MEDICATION_STATEMENT_PE);
		meta.setLastUpdated(new Date());
		medicationStatement.setMeta(meta);
		
		// Estado (obligatorio según perfil MedicationStatementPe)
		if (drugOrder.getVoided() != null && drugOrder.getVoided()) {
			medicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.STOPPED);
		} else if (drugOrder.getAction() != null) {
			switch (drugOrder.getAction()) {
				case NEW:
					medicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.ACTIVE);
					break;
				case DISCONTINUE:
					medicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.STOPPED);
					break;
				case REVISE:
					medicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.ACTIVE);
					break;
				case RENEW:
					medicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.ACTIVE);
					break;
				default:
					medicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.UNKNOWN);
			}
		} else {
			// Por defecto, verificar si está activo por fechas
			Date now = new Date();
			if (drugOrder.getScheduledDate() != null && drugOrder.getScheduledDate().before(now)) {
				if (drugOrder.getAutoExpireDate() == null || drugOrder.getAutoExpireDate().after(now)) {
					medicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.ACTIVE);
				} else {
					medicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.COMPLETED);
				}
			} else {
				medicationStatement.setStatus(MedicationStatement.MedicationStatementStatus.INTENDED);
			}
		}
		
		// Medicación (medicationCodeableConcept según perfil - debe tener text obligatorio)
		CodeableConcept medication = new CodeableConcept();
		if (drugOrder.getDrug() != null && drugOrder.getDrug().getName() != null) {
			String drugName = drugOrder.getDrug().getName();
			medication.setText(drugName); // Obligatorio según perfil
			
			// También agregar código si está disponible
			if (drugOrder.getConcept() != null) {
				medication.addCoding().setCode(drugOrder.getConcept().getUuid())
				        .setDisplay(drugOrder.getConcept().getDisplayString());
			}
		} else if (drugOrder.getConcept() != null) {
			String conceptName = drugOrder.getConcept().getDisplayString();
			medication.setText(conceptName);
			medication.addCoding().setCode(drugOrder.getConcept().getUuid()).setDisplay(conceptName);
		} else {
			medication.setText("Medicamento no especificado");
		}
		medicationStatement.setMedication(medication);
		
		// Referencia al paciente (obligatorio según perfil MedicationStatementPe)
		medicationStatement.getSubject().setReference(patientReference);
		
		// Fecha de prescripción (effectiveDateTime según perfil)
		if (drugOrder.getScheduledDate() != null) {
			medicationStatement.setEffective(new DateTimeType(drugOrder.getScheduledDate()));
		} else if (drugOrder.getDateActivated() != null) {
			medicationStatement.setEffective(new DateTimeType(drugOrder.getDateActivated()));
		} else if (drugOrder.getDateCreated() != null) {
			medicationStatement.setEffective(new DateTimeType(drugOrder.getDateCreated()));
		}
		
		// Dosificación (obligatorio según perfil MedicationStatementPe - debe tener text)
		org.hl7.fhir.r4.model.Dosage dosage = new org.hl7.fhir.r4.model.Dosage();
		
		// Construir texto de dosis
		StringBuilder dosageText = new StringBuilder();
		if (drugOrder.getDose() != null) {
			dosageText.append(drugOrder.getDose().toString());
			if (drugOrder.getDoseUnits() != null) {
				dosageText.append(" ").append(drugOrder.getDoseUnits().getDisplayString());
			}
		}
		if (drugOrder.getFrequency() != null) {
			if (dosageText.length() > 0) {
				dosageText.append(", ");
			}
			// OrderFrequency puede tener getName() o toString()
			String frequencyStr = drugOrder.getFrequency().toString();
			if (drugOrder.getFrequency().getName() != null) {
				frequencyStr = drugOrder.getFrequency().getName();
			}
			dosageText.append(frequencyStr);
		}
		if (drugOrder.getQuantity() != null) {
			if (dosageText.length() > 0) {
				dosageText.append(", ");
			}
			dosageText.append("Cantidad: ").append(drugOrder.getQuantity());
			if (drugOrder.getQuantityUnits() != null) {
				dosageText.append(" ").append(drugOrder.getQuantityUnits().getDisplayString());
			}
		}
		if (dosageText.length() == 0) {
			dosageText.append("Dosis no especificada");
		}
		dosage.setText(dosageText.toString()); // Obligatorio según perfil
		
		// Vía de administración (dosage.route.text obligatorio según perfil)
		if (drugOrder.getRoute() != null) {
			CodeableConcept route = new CodeableConcept();
			route.setText(drugOrder.getRoute().getDisplayString()); // Obligatorio según perfil
			route.addCoding().setCode(drugOrder.getRoute().getUuid()).setDisplay(drugOrder.getRoute().getDisplayString());
			dosage.setRoute(route);
		} else {
			// Si no hay ruta, agregar texto por defecto
			CodeableConcept route = new CodeableConcept();
			route.setText("Vía no especificada");
			dosage.setRoute(route);
		}
		
		// Duración si está disponible
		if (drugOrder.getDuration() != null && drugOrder.getDurationUnits() != null) {
			Duration duration = new Duration();
			duration.setValue(drugOrder.getDuration());
			String unit = drugOrder.getDurationUnits().getDisplayString();
			if (unit != null) {
				if (unit.contains("día") || unit.contains("day")) {
					duration.setUnit("d");
				} else if (unit.contains("hora") || unit.contains("hour")) {
					duration.setUnit("h");
				} else if (unit.contains("semana") || unit.contains("week")) {
					duration.setUnit("wk");
				}
				if (duration.getUnit() != null) {
					try {
						dosage.getTiming().getRepeat().setDuration(duration.getValue());
						dosage.getTiming().getRepeat()
						        .setDurationUnit(org.hl7.fhir.r4.model.Timing.UnitsOfTime.fromCode(duration.getUnit()));
					}
					catch (Exception e) {
						log.warn("Error al mapear duración: " + e.getMessage());
					}
				}
			}
		}
		
		medicationStatement.addDosage(dosage);
		
		// Información adicional
		if (drugOrder.getInstructions() != null && !drugOrder.getInstructions().isEmpty()) {
			medicationStatement.addNote().setText(drugOrder.getInstructions());
		}
		
		log.info("✓ MedicationStatement convertido exitosamente");
		return medicationStatement;
	}
}
