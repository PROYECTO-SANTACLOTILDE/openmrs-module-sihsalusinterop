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
			
			if (cie10Code != null && !cie10Code.isEmpty()) {
				code.addCoding().setSystem(SYSTEM_CIE10).setCode(cie10Code).setDisplay(concept.getDisplayString());
				log.info("✓ Código CIE-10 mapeado: " + cie10Code + " para Concept: " + concept.getDisplayString());
			} else {
				// Si no hay mapeo CIE-10, usar el concepto directamente con warning
				log.warn("⚠ Concept [" + concept.getUuid() + " - " + concept.getDisplayString() + "] no tiene mapeo CIE-10.");
				log.warn("  → Para corregir: Agregar ConceptMap con ConceptSource 'CIE-10' o 'ICD-10' en OpenMRS.");
				log.warn("  → Usando UUID de Concept como fallback (NO es un código CIE-10 válido).");
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
			
			if (cie10Code != null && !cie10Code.isEmpty()) {
				code.addCoding().setSystem(SYSTEM_CIE10).setCode(cie10Code).setDisplay(concept.getDisplayString());
				log.debug("✓ Código CIE-10 mapeado desde Obs: " + cie10Code);
			} else {
				log.warn("⚠ Obs [" + obs.getId() + "] - Concept [" + concept.getDisplayString() + "] sin mapeo CIE-10.");
				code.addCoding().setSystem(SYSTEM_CIE10).setCode(concept.getUuid()).setDisplay(concept.getDisplayString());
			}
			
			code.setText(concept.getDisplayString());
		} else {
			log.warn("⚠ Obs [" + obs.getId() + "] sin Concept codificado. Usando texto libre.");
			code.addCoding().setSystem(SYSTEM_CIE10).setCode("UNKNOWN").setDisplay(obs.getValueText());
			code.setText(obs.getValueText());
		}
		
		return code;
	}
	
	/**
	 * Busca un mapeo CIE-10 en los ConceptMaps de un Concept Mejora: Busca por múltiples variantes
	 * de nombres y valida formato
	 */
	private static String findCie10Mapping(Concept concept) {
		if (concept == null || concept.getConceptMappings() == null) {
			return null;
		}
		
		// Variantes de nombres comunes para CIE-10
		String[] cie10Variants = {
			"CIE-10", "CIE10", "ICD-10", "ICD10", 
			"CIE 10", "ICD 10", "CLASIFICACION INTERNACIONAL",
			"INTERNATIONAL CLASSIFICATION", "WHO ICD-10"
		};
		
		for (ConceptMap map : concept.getConceptMappings()) {
			if (map.getConceptReferenceTerm() != null &&
			    map.getConceptReferenceTerm().getConceptSource() != null) {
				
				org.openmrs.ConceptSource source = map.getConceptReferenceTerm().getConceptSource();
				String sourceName = source.getName();
				String sourceHl7Code = source.getHl7Code();
				
				// Buscar por nombre
				boolean isCie10 = false;
				if (sourceName != null) {
					String sourceNameUpper = sourceName.toUpperCase();
					for (String variant : cie10Variants) {
						if (sourceNameUpper.contains(variant.toUpperCase())) {
							isCie10 = true;
							break;
						}
					}
				}
				
				// También buscar por HL7 Code (puede ser "ICD10" o similar)
				if (!isCie10 && sourceHl7Code != null) {
					String hl7CodeUpper = sourceHl7Code.toUpperCase();
					for (String variant : cie10Variants) {
						if (hl7CodeUpper.contains(variant.toUpperCase())) {
							isCie10 = true;
							break;
						}
					}
				}
				
				if (isCie10) {
					String code = map.getConceptReferenceTerm().getCode();
					if (code != null && !code.isEmpty()) {
						// Validar formato básico de CIE-10 (letra seguida de números, opcional punto)
						// Ejemplos válidos: A00, A00.0, Z99.9, etc.
						if (isValidCie10Format(code)) {
							log.debug("✓ Mapeo CIE-10 encontrado: " + code + " para Concept: " + concept.getUuid());
							return code;
						} else {
							log.warn("⚠ Código CIE-10 con formato sospechoso: " + code + " (Concept: " + concept.getUuid() + ")");
							// Aún así retornarlo, puede ser válido
							return code;
						}
					}
				}
			}
		}
		
		// Si no se encontró, intentar buscar usando ConceptService
		return findCie10MappingViaService(concept);
	}
	
	/**
	 * Valida el formato básico de un código CIE-10 Formato esperado: Letra seguida de 2-3 dígitos,
	 * opcionalmente seguido de punto y más dígitos Ejemplos válidos: A00, A00.0, Z99.9, A15.1, etc.
	 */
	private static boolean isValidCie10Format(String code) {
		if (code == null || code.isEmpty()) {
			return false;
		}
		
		// Patrón básico: Letra + 2-3 dígitos + (opcional: punto + 1-2 dígitos)
		// Ejemplos: A00, A00.0, Z99.9, A15.1
		return code.matches("^[A-Z][0-9]{2,3}(\\.[0-9]{1,2})?$");
	}
	
	/**
	 * Busca mapeo CIE-10 usando ConceptService (método alternativo) Útil si los ConceptMappings no
	 * están cargados en memoria
	 */
	private static String findCie10MappingViaService(Concept concept) {
		try {
			org.openmrs.api.ConceptService conceptService = 
				org.openmrs.api.context.Context.getConceptService();
			
			if (conceptService != null) {
				// Buscar ConceptSource de CIE-10
				org.openmrs.ConceptSource cie10Source = null;
				
				// Buscar por nombre
				String[] cie10Names = {"CIE-10", "ICD-10", "CIE10", "ICD10"};
				for (String name : cie10Names) {
					try {
						cie10Source = conceptService.getConceptSourceByName(name);
						if (cie10Source != null) {
							break;
						}
					} catch (Exception e) {
						// Continuar buscando
					}
				}
				
				if (cie10Source != null) {
					// Buscar ConceptReferenceTerm para este Concept y Source
					// Usar reflexión para mayor compatibilidad
					try {
						java.lang.reflect.Method getConceptReferenceTerms = 
							conceptService.getClass().getMethod("getConceptReferenceTerms", Concept.class, org.openmrs.ConceptSource.class);
						Object result = getConceptReferenceTerms.invoke(conceptService, concept, cie10Source);
						
						if (result instanceof java.util.Collection) {
							java.util.Collection<?> terms = (java.util.Collection<?>) result;
							if (!terms.isEmpty()) {
								Object term = terms.iterator().next();
								if (term != null) {
									java.lang.reflect.Method getCode = term.getClass().getMethod("getCode");
									Object codeObj = getCode.invoke(term);
									if (codeObj != null) {
										String code = codeObj.toString();
										log.debug("✓ Mapeo CIE-10 encontrado vía ConceptService: " + code);
										return code;
									}
								}
							}
						}
					} catch (Exception e) {
						log.debug("No se pudo usar getConceptReferenceTerms: " + e.getMessage());
					}
				}
			}
		} catch (Exception e) {
			log.debug("No se pudo buscar CIE-10 vía ConceptService: " + e.getMessage());
		}
		
		return null;
	}
}
