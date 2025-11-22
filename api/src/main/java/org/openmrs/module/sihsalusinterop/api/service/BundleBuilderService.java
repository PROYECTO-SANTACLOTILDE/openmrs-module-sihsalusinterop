package org.openmrs.module.sihsalusinterop.api.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.*;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.sihsalusinterop.api.mapper.DyakuPatientMapper;
import org.openmrs.module.sihsalusinterop.api.mapper.DyakuOrganizationMapper;
import org.openmrs.module.sihsalusinterop.api.mapper.DyakuPractitionerMapper;
import org.openmrs.module.sihsalusinterop.api.mapper.DyakuEncounterMapper;
import org.openmrs.module.sihsalusinterop.api.mapper.DyakuConditionMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BundleBuilderService - Servicio para construir Bundles FHIR R4 según perfil BundlePe
 * 
 * Perfil peruano según: https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/BundlePe
 * Construye un Bundle tipo "document" con:
 * - Composition (opcional)
 * - Patient (obligatorio)
 * - Organization (obligatorio)
 * - Practitioner (obligatorio)
 * - Condition (diagnósticos, opcional)
 * - AllergyIntolerance (alergias, opcional)
 * - MedicationStatement (medicaciones, opcional)
 * 
 * Hospital Santa Clotilde - SIH.SALUS
 */
public class BundleBuilderService {
	
	private static final Log log = LogFactory.getLog(BundleBuilderService.class);
	
	public static final String PROFILE_BUNDLE_PE = "https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/BundlePe";
	
	/**
	 * Construye un Bundle FHIR R4 con resumen clínico completo de un Encounter
	 * 
	 * @param encounter Encounter de OpenMRS
	 * @return Bundle FHIR R4 según perfil BundlePe
	 */
	public Bundle buildClinicalSummaryBundle(Encounter encounter) {
		if (encounter == null) {
			throw new IllegalArgumentException("Encounter no puede ser nulo");
		}
		
		log.info("Construyendo Bundle FHIR R4 para Encounter: " + encounter.getId());
		
		Bundle bundle = new Bundle();
		
		// Meta con perfil peruano
		Meta meta = new Meta();
		meta.addProfile(PROFILE_BUNDLE_PE);
		bundle.setMeta(meta);
		
		// Tipo: document (según perfil BundlePe)
		bundle.setType(Bundle.BundleType.DOCUMENT);
		
		// Identificador único del Bundle
		Identifier bundleIdentifier = new Identifier();
		bundleIdentifier.setSystem("urn:uuid");
		bundleIdentifier.setValue(UUID.randomUUID().toString());
		bundle.setIdentifier(bundleIdentifier);
		
		// Timestamp (obligatorio según perfil)
		bundle.setTimestamp(java.util.Calendar.getInstance().getTime());
		
		// Obtener recursos relacionados
		Patient patient = encounter.getPatient();
		Location location = encounter.getLocation();
		User creator = encounter.getCreator();
		
		if (patient == null) {
			throw new IllegalArgumentException("Encounter sin paciente asociado");
		}
		
		// Referencias locales para usar en las entradas
		String patientRef = "Patient/" + patient.getUuid();
		String organizationRef = location != null ? "Organization/" + location.getUuid() : "Organization/hospital-santa-clotilde";
		String practitionerRef = creator != null ? "Practitioner/" + creator.getUuid() : "Practitioner/unknown";
		
		// 1. Patient (obligatorio según perfil)
		log.info(">>> Agregando Patient al Bundle...");
		org.hl7.fhir.r4.model.Patient fhirPatient = DyakuPatientMapper.toDyakuFhir(patient);
		addBundleEntry(bundle, fhirPatient, patientRef, Bundle.HTTPVerb.POST);
		
		// 2. Organization (obligatorio según perfil)
		if (location != null) {
			log.info(">>> Agregando Organization al Bundle...");
			Organization organization = DyakuOrganizationMapper.toDyakuFhir(location);
			addBundleEntry(bundle, organization, organizationRef, Bundle.HTTPVerb.POST);
		}
		
		// 3. Practitioner (obligatorio según perfil)
		if (creator != null) {
			log.info(">>> Agregando Practitioner al Bundle...");
			Practitioner practitioner = DyakuPractitionerMapper.toDyakuFhir(creator);
			addBundleEntry(bundle, practitioner, practitionerRef, Bundle.HTTPVerb.POST);
		}
		
		// 4. Encounter
		log.info(">>> Agregando Encounter al Bundle...");
		org.hl7.fhir.r4.model.Encounter fhirEncounter = 
			DyakuEncounterMapper.toDyakuFhir(encounter, patientRef, organizationRef);
		addBundleEntry(bundle, fhirEncounter, "Encounter/" + encounter.getUuid(), Bundle.HTTPVerb.POST);
		
		// 5. Conditions (Diagnósticos) - Opcional según perfil
		log.info(">>> Agregando Conditions (Diagnósticos) al Bundle...");
		List<org.hl7.fhir.r4.model.Condition> conditions = buildConditions(encounter, patientRef);
		for (org.hl7.fhir.r4.model.Condition condition : conditions) {
			addBundleEntry(bundle, condition, "Condition/" + condition.getId(), Bundle.HTTPVerb.POST);
		}
		
		// 6. Observations (Signos vitales, exámenes) - Opcional
		log.info(">>> Agregando Observations al Bundle...");
		// TODO: Mapear Observations si están disponibles
		
		// 7. Composition (opcional según perfil)
		// TODO: Crear Composition si se requiere según perfil CompositionPe
		
		log.info("✓ Bundle construido exitosamente con " + bundle.getEntry().size() + " recursos");
		return bundle;
	}
	
	/**
	 * Construye las Conditions (Diagnósticos) desde el Encounter
	 */
	private List<org.hl7.fhir.r4.model.Condition> buildConditions(Encounter encounter, String patientRef) {
		List<org.hl7.fhir.r4.model.Condition> conditions = new ArrayList<>();
		
		// Buscar diagnósticos en el Encounter
		// OpenMRS puede tener diagnósticos en Diagnosis o en Obs
		
		// Intentar obtener diagnósticos desde el módulo de diagnóstico si está disponible
		// Por ahora, buscar Obs de tipo diagnóstico
		for (Obs obs : encounter.getAllObs(true)) {
			if (obs.getConcept() != null) {
				String conceptName = obs.getConcept().getName().getName().toUpperCase();
				if (conceptName.contains("DIAGNOSIS") || 
				    conceptName.contains("DIAGNOSTICO") ||
				    obs.getConcept().getConceptClass() != null &&
				    obs.getConcept().getConceptClass().getName().equals("Diagnosis")) {
					
					try {
						org.hl7.fhir.r4.model.Condition condition = 
							DyakuConditionMapper.toDyakuFhir(obs, patientRef);
						conditions.add(condition);
					} catch (Exception e) {
						log.warn("Error al mapear Obs a Condition: " + obs.getId(), e);
					}
				}
			}
		}
		
		return conditions;
	}
	
	/**
	 * Agrega una entrada al Bundle
	 */
	private void addBundleEntry(Bundle bundle, Resource resource, String fullUrl, Bundle.HTTPVerb method) {
		Bundle.BundleEntryComponent entry = bundle.addEntry();
		
		// Full URL (referencia local)
		entry.setFullUrl(fullUrl);
		
		// Recurso
		entry.setResource(resource);
		
		// Request (para transacciones)
		Bundle.BundleEntryRequestComponent request = entry.getRequest();
		request.setMethod(method);
		
		// URL depende del tipo de recurso y método
		String resourceType = resource.getResourceType().name();
		if (method == Bundle.HTTPVerb.POST) {
			request.setUrl(resourceType);
		} else if (method == Bundle.HTTPVerb.PUT) {
			request.setUrl(resourceType + "/" + resource.getId());
		}
	}
}

