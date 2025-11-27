package org.openmrs.module.sihsalusinterop.api.mapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.*;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;

/**
 * DyakuEncounterMapper - Conversor de Encounters OpenMRS a FHIR R4 Hospital Santa Clotilde -
 * SIH.SALUS
 */
public class DyakuEncounterMapper {
	
	private static final Log log = LogFactory.getLog(DyakuEncounterMapper.class);
	
	/**
	 * Convierte un Encounter de OpenMRS a Encounter FHIR R4
	 */
	public static org.hl7.fhir.r4.model.Encounter toDyakuFhir(Encounter encounter, String patientReference,
	        String organizationReference) {
		if (encounter == null) {
			throw new IllegalArgumentException("Encounter no puede ser nulo");
		}
		
		log.info("Convirtiendo Encounter [" + encounter.getId() + "] a FHIR R4");
		
		org.hl7.fhir.r4.model.Encounter fhirEncounter = new org.hl7.fhir.r4.model.Encounter();
		
		// Estado
		fhirEncounter.setStatus(org.hl7.fhir.r4.model.Encounter.EncounterStatus.FINISHED);
		
		// Clase del encuentro
		Coding classCoding = new Coding();
		classCoding.setSystem("http://terminology.hl7.org/CodeSystem/v3-ActCode");
		
		EncounterType encounterType = encounter.getEncounterType();
		if (encounterType != null) {
			String typeName = encounterType.getName().toUpperCase();
			if (typeName.contains("EMERGENCY") || typeName.contains("EMERGENCIA")) {
				classCoding.setCode("EMER");
				classCoding.setDisplay("Emergency");
			} else if (typeName.contains("AMBULATORY") || typeName.contains("AMBULATORIO")) {
				classCoding.setCode("AMB");
				classCoding.setDisplay("Ambulatory");
			} else if (typeName.contains("INPATIENT") || typeName.contains("HOSPITALIZACION")) {
				classCoding.setCode("IMP");
				classCoding.setDisplay("Inpatient");
			} else {
				classCoding.setCode("AMB");
				classCoding.setDisplay("Ambulatory");
			}
		} else {
			classCoding.setCode("AMB");
			classCoding.setDisplay("Ambulatory");
		}
		
		// En FHIR R4, Encounter.class_ es un Coding directo, no CodeableConcept
		fhirEncounter.getClass_().setSystem(classCoding.getSystem());
		fhirEncounter.getClass_().setCode(classCoding.getCode());
		fhirEncounter.getClass_().setDisplay(classCoding.getDisplay());
		
		// Tipo de encuentro
		if (encounterType != null) {
			CodeableConcept type = new CodeableConcept();
			type.addCoding().setCode(encounterType.getUuid()).setDisplay(encounterType.getName());
			fhirEncounter.addType(type);
		}
		
		// Referencia al paciente
		fhirEncounter.getSubject().setReference(patientReference);
		
		// Período del encuentro
		Period period = new Period();
		if (encounter.getEncounterDatetime() != null) {
			period.setStart(encounter.getEncounterDatetime());
		}
		if (encounter.getEncounterDatetime() != null && encounter.getDateCreated() != null) {
			// Asumir que la duración es desde el inicio hasta la fecha de creación
			period.setEnd(encounter.getDateCreated());
		}
		fhirEncounter.setPeriod(period);
		
		// Ubicación (Location) - Debe ser un recurso Location, no Organization
		// Si hay Location en OpenMRS, crear referencia a Location
		if (encounter.getLocation() != null) {
			org.hl7.fhir.r4.model.Encounter.EncounterLocationComponent location = new org.hl7.fhir.r4.model.Encounter.EncounterLocationComponent();
			// Location debe referenciar a un recurso Location, no Organization
			// Por ahora, usar el UUID del Location de OpenMRS
			location.getLocation().setReference("Location/" + encounter.getLocation().getUuid());
			fhirEncounter.addLocation(location);
		}
		
		// Service Provider (Organization) - La organización va aquí, no en location
		if (organizationReference != null) {
			fhirEncounter.getServiceProvider().setReference(organizationReference);
		}
		
		// Servicio (se puede mapear desde Location o atributos)
		// TODO: Mapear servicio si está disponible
		
		log.info("✓ Encounter convertido exitosamente");
		return fhirEncounter;
	}
}
