package org.openmrs.module.sihsalusinterop.api.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.*;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.User;
import org.openmrs.module.sihsalusinterop.api.mapper.DyakuPatientMapper;
import org.openmrs.module.sihsalusinterop.api.mapper.DyakuOrganizationMapper;
import org.openmrs.module.sihsalusinterop.api.mapper.DyakuPractitionerMapper;
import org.openmrs.module.sihsalusinterop.api.mapper.DyakuEncounterMapper;
import org.openmrs.module.sihsalusinterop.api.mapper.DyakuConditionMapper;
import org.openmrs.module.sihsalusinterop.api.mapper.DyakuAllergyIntoleranceMapper;
import org.openmrs.module.sihsalusinterop.api.mapper.DyakuMedicationStatementMapper;
import org.openmrs.module.sihsalusinterop.api.mapper.DyakuProcedureMapper;
import org.openmrs.module.sihsalusinterop.api.mapper.DyakuObservationMapper;
import org.openmrs.module.sihsalusinterop.api.mapper.DyakuImmunizationMapper;
import org.openmrs.Allergy;
import org.openmrs.DrugOrder;
import org.openmrs.Order;

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
		
		// Tipo: transaction (para que HAPI FHIR pueda procesarlo)
		// Nota: Aunque el perfil BundlePe puede requerir "document", 
		// HAPI FHIR necesita "transaction" o "batch" para procesar el Bundle
		bundle.setType(Bundle.BundleType.TRANSACTION);
		
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
		String locationRef = location != null ? "Location/" + location.getUuid() : null;
		
		// 1. Patient (obligatorio según perfil)
		log.info(">>> Agregando Patient al Bundle...");
		org.hl7.fhir.r4.model.Patient fhirPatient = DyakuPatientMapper.toDyakuFhir(patient);
		
		// Obtener DNI del paciente para conditional create
		String dniIdentifier = null;
		if (fhirPatient.getIdentifier() != null && !fhirPatient.getIdentifier().isEmpty()) {
			Identifier dni = fhirPatient.getIdentifier().get(0);
			if (dni.getSystem() != null && dni.getValue() != null) {
				dniIdentifier = "identifier=" + dni.getSystem() + "|" + dni.getValue();
			}
		}
		
		// Usar conditional create para evitar duplicados
		addBundleEntry(bundle, fhirPatient, patientRef, Bundle.HTTPVerb.POST, dniIdentifier);
		
		// 2. Organization (obligatorio según perfil)
		if (location != null) {
			log.info(">>> Agregando Organization al Bundle...");
			Organization organization = DyakuOrganizationMapper.toDyakuFhir(location);
			
			// Usar identifier para conditional create (evitar duplicados)
			String orgCondition = null;
			if (organization.getIdentifier() != null && !organization.getIdentifier().isEmpty()) {
				Identifier orgId = organization.getIdentifier().get(0);
				if (orgId.getValue() != null) {
					orgCondition = "identifier=" + orgId.getValue();
				}
			}
			addBundleEntry(bundle, organization, organizationRef, Bundle.HTTPVerb.POST, orgCondition);
		}
		
		// 3. Location (necesario para Encounter.location)
		if (location != null && locationRef != null) {
			log.info(">>> Agregando Location al Bundle...");
			org.hl7.fhir.r4.model.Location fhirLocation = new org.hl7.fhir.r4.model.Location();
			fhirLocation.setId(location.getUuid());
			fhirLocation.setStatus(org.hl7.fhir.r4.model.Location.LocationStatus.ACTIVE);
			fhirLocation.setName(location.getName());
			if (location.getDescription() != null) {
				fhirLocation.setDescription(location.getDescription());
			}
			// Referencia a la Organization
			fhirLocation.getManagingOrganization().setReference(organizationRef);
			addBundleEntry(bundle, fhirLocation, locationRef, Bundle.HTTPVerb.POST);
		}
		
		// 4. Practitioner (obligatorio según perfil)
		if (creator != null) {
			log.info(">>> Agregando Practitioner al Bundle...");
			Practitioner practitioner = DyakuPractitionerMapper.toDyakuFhir(creator);
			
			// Usar DNI para conditional create (evitar duplicados)
			String pracCondition = null;
			if (practitioner.getIdentifier() != null && !practitioner.getIdentifier().isEmpty()) {
				Identifier pracId = practitioner.getIdentifier().get(0);
				if (pracId.getSystem() != null && pracId.getValue() != null) {
					pracCondition = "identifier=" + pracId.getSystem() + "|" + pracId.getValue();
				}
			}
			addBundleEntry(bundle, practitioner, practitionerRef, Bundle.HTTPVerb.POST, pracCondition);
		}
		
		// 5. Encounter
		log.info(">>> Agregando Encounter al Bundle...");
		org.hl7.fhir.r4.model.Encounter fhirEncounter = 
			DyakuEncounterMapper.toDyakuFhir(encounter, patientRef, organizationRef);
		addBundleEntry(bundle, fhirEncounter, "Encounter/" + encounter.getUuid(), Bundle.HTTPVerb.POST);
		
		// 6. Conditions (Diagnósticos) - Opcional según perfil
		log.info(">>> Agregando Conditions (Diagnósticos) al Bundle...");
		List<org.hl7.fhir.r4.model.Condition> conditions = buildConditions(encounter, patientRef);
		for (org.hl7.fhir.r4.model.Condition condition : conditions) {
			addBundleEntry(bundle, condition, "Condition/" + condition.getId(), Bundle.HTTPVerb.POST);
		}
		
		// 7. AllergyIntolerance (Alergias) - Opcional según perfil
		log.info(">>> Agregando AllergyIntolerance (Alergias) al Bundle...");
		List<AllergyIntolerance> allergies = buildAllergies(patient, patientRef);
		for (AllergyIntolerance allergy : allergies) {
			addBundleEntry(bundle, allergy, "AllergyIntolerance/" + allergy.getId(), Bundle.HTTPVerb.POST);
		}
		
		// 8. MedicationStatement (Medicaciones) - Opcional según perfil
		log.info(">>> Agregando MedicationStatement (Medicaciones) al Bundle...");
		List<MedicationStatement> medications = buildMedications(encounter, patientRef);
		for (MedicationStatement medication : medications) {
			addBundleEntry(bundle, medication, "MedicationStatement/" + medication.getId(), Bundle.HTTPVerb.POST);
		}
		
		// 9. Procedures (Procedimientos) - Opcional
		log.info(">>> Agregando Procedures (Procedimientos) al Bundle...");
		List<Procedure> procedures = buildProcedures(encounter, patientRef, "Encounter/" + encounter.getUuid());
		for (Procedure procedure : procedures) {
			addBundleEntry(bundle, procedure, "Procedure/" + procedure.getId(), Bundle.HTTPVerb.POST);
		}
		
		// 10. Observations (Signos vitales, exámenes) - Opcional
		log.info(">>> Agregando Observations (Signos vitales, laboratorios) al Bundle...");
		List<Observation> observations = buildObservations(encounter, patientRef, "Encounter/" + encounter.getUuid());
		for (Observation observation : observations) {
			addBundleEntry(bundle, observation, "Observation/" + observation.getId(), Bundle.HTTPVerb.POST);
		}
		
		// 11. Immunizations (Vacunas) - Opcional
		log.info(">>> Agregando Immunizations (Vacunas) al Bundle...");
		List<Immunization> immunizations = buildImmunizations(encounter, patientRef, "Encounter/" + encounter.getUuid());
		for (Immunization immunization : immunizations) {
			addBundleEntry(bundle, immunization, "Immunization/" + immunization.getId(), Bundle.HTTPVerb.POST);
		}
		
		// 12. Composition (opcional según perfil)
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
	 * Construye las AllergyIntolerance (Alergias) desde el Patient
	 */
	private List<AllergyIntolerance> buildAllergies(Patient patient, String patientRef) {
		List<AllergyIntolerance> allergies = new ArrayList<>();
		
		if (patient == null) {
			return allergies;
		}
		
		// Obtener alergias del paciente
		try {
			// OpenMRS puede tener alergias accesibles a través del Patient.getAllergies()
			// O puede requerir usar el servicio de alergias
			// Intentar obtener alergias usando reflexión para mayor compatibilidad
			java.util.Collection<Allergy> openmrsAllergies = null;
			
			try {
				// Método 1: Intentar getAllergies() en Patient
				java.lang.reflect.Method getAllergies = patient.getClass().getMethod("getAllergies");
				Object result = getAllergies.invoke(patient);
				if (result instanceof java.util.Collection) {
					openmrsAllergies = (java.util.Collection<Allergy>) result;
				}
			} catch (Exception e1) {
				// Método 2: Intentar usar AllergyService
				try {
					Object allergyService = org.openmrs.api.context.Context.getService(
						org.openmrs.api.context.Context.loadClass("org.openmrs.api.AllergyService"));
					if (allergyService != null) {
						java.lang.reflect.Method getAllergies = allergyService.getClass().getMethod("getAllergies", Patient.class);
						Object result = getAllergies.invoke(allergyService, patient);
						if (result instanceof java.util.Collection) {
							openmrsAllergies = (java.util.Collection<Allergy>) result;
						}
					}
				} catch (Exception e2) {
					log.warn("No se pudo obtener alergias del paciente. AllergyService puede no estar disponible.");
				}
			}
			
			if (openmrsAllergies != null) {
				for (Allergy allergy : openmrsAllergies) {
					if (allergy != null && (allergy.getVoided() == null || !allergy.getVoided())) {
						try {
							AllergyIntolerance fhirAllergy = 
								DyakuAllergyIntoleranceMapper.toDyakuFhir(allergy, patientRef);
							allergies.add(fhirAllergy);
						} catch (Exception e) {
							log.warn("Error al mapear Allergy a AllergyIntolerance: " + allergy.getAllergyId(), e);
						}
					}
				}
			}
		} catch (Exception e) {
			log.warn("Error al obtener alergias del paciente: " + e.getMessage());
		}
		
		return allergies;
	}
	
	/**
	 * Construye las MedicationStatement (Medicaciones) desde el Encounter
	 */
	private List<MedicationStatement> buildMedications(Encounter encounter, String patientRef) {
		List<MedicationStatement> medications = new ArrayList<>();
		
		if (encounter == null || encounter.getEncounterId() == null) {
			return medications;
		}
		
		// Buscar DrugOrders en el Encounter
		try {
			org.openmrs.api.OrderService orderService = 
				org.openmrs.api.context.Context.getOrderService();
			
			if (orderService != null) {
				// Obtener órdenes del encounter usando el método correcto
				// OrderService puede tener diferentes métodos según la versión
				List<Order> orders = null;
				
				try {
					// Intentar método getOrdersByEncounter
					java.lang.reflect.Method getOrdersByEncounter = 
						orderService.getClass().getMethod("getOrdersByEncounter", Encounter.class);
					Object result = getOrdersByEncounter.invoke(orderService, encounter);
					if (result instanceof java.util.List) {
						orders = (java.util.List<Order>) result;
					}
				} catch (Exception e1) {
					// Intentar método alternativo: getOrders del Patient
					try {
						Patient patient = encounter.getPatient();
						if (patient != null) {
							orders = orderService.getOrders(patient, null, null, false);
							// Filtrar solo las del encounter
							if (orders != null) {
								orders.removeIf(order -> !encounter.equals(order.getEncounter()));
							}
						}
					} catch (Exception e2) {
						log.warn("No se pudo obtener órdenes del Encounter: " + e2.getMessage());
					}
				}
				
				if (orders != null) {
					for (Order order : orders) {
						if (order instanceof DrugOrder && (order.getVoided() == null || !order.getVoided())) {
							try {
								DrugOrder drugOrder = (DrugOrder) order;
								MedicationStatement medication = 
									DyakuMedicationStatementMapper.toDyakuFhir(drugOrder, patientRef);
								medications.add(medication);
							} catch (Exception e) {
								log.warn("Error al mapear DrugOrder a MedicationStatement: " + order.getOrderId(), e);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			log.warn("Error al obtener medicaciones del Encounter: " + e.getMessage());
		}
		
		return medications;
	}
	
	/**
	 * Construye las Procedures (Procedimientos) desde el Encounter
	 */
	private List<Procedure> buildProcedures(Encounter encounter, String patientRef, String encounterRef) {
		List<Procedure> procedures = new ArrayList<>();
		
		if (encounter == null || encounter.getEncounterId() == null) {
			return procedures;
		}
		
		// Buscar Orders tipo ProcedureOrder en el Encounter
		try {
			org.openmrs.api.OrderService orderService = 
				org.openmrs.api.context.Context.getOrderService();
			
			if (orderService != null) {
				// Obtener órdenes del encounter usando el método correcto
				List<Order> orders = null;
				
				try {
					// Intentar método getOrdersByEncounter
					java.lang.reflect.Method getOrdersByEncounter = 
						orderService.getClass().getMethod("getOrdersByEncounter", Encounter.class);
					Object result = getOrdersByEncounter.invoke(orderService, encounter);
					if (result instanceof java.util.List) {
						orders = (java.util.List<Order>) result;
					}
				} catch (Exception e1) {
					// Intentar método alternativo: getOrders del Patient
					try {
						Patient patient = encounter.getPatient();
						if (patient != null) {
							orders = orderService.getOrders(patient, null, null, false);
							// Filtrar solo las del encounter
							if (orders != null) {
								orders.removeIf(order -> !encounter.equals(order.getEncounter()));
							}
						}
					} catch (Exception e2) {
						log.warn("No se pudo obtener órdenes del Encounter: " + e2.getMessage());
					}
				}
				
				if (orders != null) {
					for (Order order : orders) {
						// Verificar si es un ProcedureOrder
						// En OpenMRS, los procedimientos pueden ser Order con orderType = "ProcedureOrder"
						if ((order.getVoided() == null || !order.getVoided()) && 
						    order.getOrderType() != null &&
						    order.getOrderType().getName() != null &&
						    (order.getOrderType().getName().toUpperCase().contains("PROCEDURE") ||
						     order.getOrderType().getName().toUpperCase().contains("PROCEDIMIENTO"))) {
							try {
								Procedure procedure = 
									DyakuProcedureMapper.toDyakuFhir(order, patientRef, encounterRef);
								procedures.add(procedure);
							} catch (Exception e) {
								log.warn("Error al mapear Order a Procedure: " + order.getOrderId(), e);
							}
						}
					}
				}
			}
		} catch (Exception e) {
			log.warn("Error al obtener procedimientos del Encounter: " + e.getMessage());
		}
		
		return procedures;
	}
	
	/**
	 * Construye las Observations (Signos vitales, laboratorios) desde el Encounter
	 */
	private List<Observation> buildObservations(Encounter encounter, String patientRef, String encounterRef) {
		List<Observation> observations = new ArrayList<>();
		
		if (encounter == null) {
			return observations;
		}
		
		// Obtener todos los Obs del Encounter que no sean diagnósticos ni inmunizaciones
		for (Obs obs : encounter.getAllObs(true)) {
			// Excluir diagnósticos (ya se mapearon como Conditions)
			if (obs.getConcept() != null) {
				String conceptName = obs.getConcept().getName().getName().toUpperCase();
				boolean isDiagnosis = conceptName.contains("DIAGNOSIS") || 
				                     conceptName.contains("DIAGNOSTICO") ||
				                     (obs.getConcept().getConceptClass() != null &&
				                      obs.getConcept().getConceptClass().getName().equals("Diagnosis"));
				
				// Excluir inmunizaciones (ya se mapearon como Immunizations)
				boolean isImmunization = DyakuImmunizationMapper.isImmunizationObs(obs);
				
				if (!isDiagnosis && !isImmunization && !obs.getVoided()) {
					try {
						Observation observation = 
							DyakuObservationMapper.toDyakuFhir(obs, patientRef, encounterRef);
						observations.add(observation);
					} catch (Exception e) {
						log.warn("Error al mapear Obs a Observation: " + obs.getId(), e);
					}
				}
			}
		}
		
		return observations;
	}
	
	/**
	 * Construye las Immunizations (Vacunas) desde el Encounter
	 */
	private List<Immunization> buildImmunizations(Encounter encounter, String patientRef, String encounterRef) {
		List<Immunization> immunizations = new ArrayList<>();
		
		if (encounter == null) {
			return immunizations;
		}
		
		// Buscar Obs que representen vacunas
		for (Obs obs : encounter.getAllObs(true)) {
			if (obs.getConcept() != null && !obs.getVoided()) {
				// Verificar si es una inmunización
				if (DyakuImmunizationMapper.isImmunizationObs(obs)) {
					try {
						Immunization immunization = 
							DyakuImmunizationMapper.toDyakuFhir(obs, patientRef, encounterRef);
						immunizations.add(immunization);
					} catch (Exception e) {
						log.warn("Error al mapear Obs a Immunization: " + obs.getId(), e);
					}
				}
			}
		}
		
		return immunizations;
	}
	
	/**
	 * Agrega una entrada al Bundle
	 */
	private void addBundleEntry(Bundle bundle, Resource resource, String fullUrl, Bundle.HTTPVerb method) {
		addBundleEntry(bundle, resource, fullUrl, method, null);
	}
	
	/**
	 * Agrega una entrada al Bundle con conditional create (ifNoneExist)
	 * 
	 * @param bundle Bundle al que agregar la entrada
	 * @param resource Recurso FHIR a agregar
	 * @param fullUrl URL completa del recurso (para referencias internas)
	 * @param method Método HTTP (POST, PUT, etc.)
	 * @param ifNoneExist Condición para crear solo si no existe (ej: "identifier=system|value")
	 */
	private void addBundleEntry(Bundle bundle, Resource resource, String fullUrl, Bundle.HTTPVerb method, String ifNoneExist) {
		Bundle.BundleEntryComponent entry = bundle.addEntry();
		
		// Full URL (referencia local) - Usar URN UUID para referencias temporales
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
			
			// Si hay condición ifNoneExist, agregar para evitar duplicados
			if (ifNoneExist != null && !ifNoneExist.isEmpty()) {
				request.setIfNoneExist(ifNoneExist);
			}
		} else if (method == Bundle.HTTPVerb.PUT) {
			request.setUrl(resourceType + "/" + resource.getId());
		}
	}
}

