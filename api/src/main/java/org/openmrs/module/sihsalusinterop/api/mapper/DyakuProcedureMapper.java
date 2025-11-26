package org.openmrs.module.sihsalusinterop.api.mapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.*;
import org.openmrs.Concept;
import org.openmrs.ConceptMap;
import org.openmrs.Order;

import java.util.Date;

/**
 * DyakuProcedureMapper - Conversor de ProcedureOrder OpenMRS a FHIR R4 Procedure Los procedimientos
 * clínicos en OpenMRS se registran como Order tipo ProcedureOrder. El Concept del procedimiento
 * debe tener mapeo CPMS (ConceptMap). Hospital Santa Clotilde - SIH.SALUS
 */
public class DyakuProcedureMapper {
	
	private static final Log log = LogFactory.getLog(DyakuProcedureMapper.class);
	
	/**
	 * CodeSystem CPMS (Catálogo de Procedimientos Médicos y Sanitarios)
	 */
	public static final String SYSTEM_CPMS = "https://www.gob.pe/minsa/RENHICE/fhir/CodeSystem/CPMSCS";
	
	/**
	 * Convierte un Order de tipo ProcedureOrder a Procedure FHIR R4
	 * 
	 * @param procedureOrder Order de tipo ProcedureOrder de OpenMRS a convertir
	 * @param patientReference Referencia al paciente (ej: "Patient/uuid")
	 * @param encounterReference Referencia al encuentro (ej: "Encounter/uuid")
	 * @return Recurso FHIR Procedure con código CPMS
	 */
	public static Procedure toDyakuFhir(Order procedureOrder, String patientReference, String encounterReference) {
		if (procedureOrder == null) {
			throw new IllegalArgumentException("ProcedureOrder no puede ser nulo");
		}
		
		log.info("Convirtiendo ProcedureOrder [" + procedureOrder.getOrderId() + "] a Procedure FHIR R4");
		
		Procedure procedure = new Procedure();
		
		// Meta
		Meta meta = new Meta();
		meta.setLastUpdated(new Date());
		procedure.setMeta(meta);
		
		// Estado
		if (procedureOrder.getVoided() != null && procedureOrder.getVoided()) {
			procedure.setStatus(Procedure.ProcedureStatus.STOPPED);
		} else if (procedureOrder.getAction() != null) {
			switch (procedureOrder.getAction()) {
				case NEW:
				case REVISE:
				case RENEW:
					procedure.setStatus(Procedure.ProcedureStatus.INPROGRESS);
					break;
				case DISCONTINUE:
					procedure.setStatus(Procedure.ProcedureStatus.STOPPED);
					break;
				default:
					procedure.setStatus(Procedure.ProcedureStatus.UNKNOWN);
			}
		} else {
			// Por defecto, verificar fechas
			Date now = new Date();
			if (procedureOrder.getScheduledDate() != null && procedureOrder.getScheduledDate().before(now)) {
				if (procedureOrder.getAutoExpireDate() == null || procedureOrder.getAutoExpireDate().after(now)) {
					procedure.setStatus(Procedure.ProcedureStatus.INPROGRESS);
				} else {
					procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
				}
			} else {
				procedure.setStatus(Procedure.ProcedureStatus.PREPARATION);
			}
		}
		
		// Código del procedimiento (usar CPMS si está disponible)
		CodeableConcept code = mapCpmCode(procedureOrder.getConcept());
		procedure.setCode(code);
		
		// Referencia al paciente
		procedure.getSubject().setReference(patientReference);
		
		// Referencia al encuentro
		if (encounterReference != null) {
			procedure.getEncounter().setReference(encounterReference);
		}
		
		// Período del procedimiento
		if (procedureOrder.getScheduledDate() != null) {
			Period period = new Period();
			period.setStart(procedureOrder.getScheduledDate());
			if (procedureOrder.getAutoExpireDate() != null) {
				period.setEnd(procedureOrder.getAutoExpireDate());
			}
			procedure.setPerformed(period);
		} else if (procedureOrder.getDateActivated() != null) {
			procedure.setPerformed(new DateTimeType(procedureOrder.getDateActivated()));
		}
		
		// Notas/Instrucciones
		if (procedureOrder.getInstructions() != null && !procedureOrder.getInstructions().isEmpty()) {
			procedure.addNote().setText(procedureOrder.getInstructions());
		}
		
		log.info("✓ Procedure convertido exitosamente");
		return procedure;
	}
	
	/**
	 * Mapea el código CPMS desde el Concept del procedimiento Busca el mapeo CPMS en los
	 * ConceptMaps del Concept
	 */
	private static CodeableConcept mapCpmCode(Concept concept) {
		CodeableConcept code = new CodeableConcept();
		
		if (concept == null) {
			code.setText("Procedimiento no especificado");
			return code;
		}
		
		// Buscar mapeo CPMS en los ConceptMaps
		String cpmsCode = findCpmsMapping(concept);
		
		if (cpmsCode != null && !cpmsCode.isEmpty()) {
			// Agregar código CPMS
			code.addCoding().setSystem(SYSTEM_CPMS).setCode(cpmsCode).setDisplay(concept.getDisplayString());
			code.setText(concept.getDisplayString());
			log.info("✓ Código CPMS mapeado: " + cpmsCode + " para Concept: " + concept.getDisplayString());
		} else {
			// Si no hay mapeo CPMS, usar el concepto directamente con warning
			log.warn("⚠ Concept [" + concept.getUuid() + " - " + concept.getDisplayString() + "] no tiene mapeo CPMS.");
			log.warn("  → Para corregir: Agregar ConceptMap con ConceptSource 'CPMS' en OpenMRS.");
			log.warn("  → Usando UUID de Concept como fallback (NO es un código CPMS válido).");
			code.addCoding().setSystem("http://openmrs.org/concepts").setCode(concept.getUuid())
			        .setDisplay(concept.getDisplayString());
			code.setText(concept.getDisplayString());
		}
		
		return code;
	}
	
	/**
	 * Busca un mapeo CPMS en los ConceptMaps de un Concept Mejora: Busca por múltiples variantes de
	 * nombres y valida formato
	 */
	private static String findCpmsMapping(Concept concept) {
		if (concept == null || concept.getConceptMappings() == null) {
			return null;
		}
		
		// Variantes de nombres comunes para CPMS
		String[] cpmsVariants = {
			"CPMS", "CATALOGO PROCEDIMIENTOS", "PROCEDIMIENTOS MEDICOS",
			"CATALOGO PROCEDIMIENTOS MEDICOS", "PROCEDIMIENTOS SANITARIOS"
		};
		
		for (ConceptMap map : concept.getConceptMappings()) {
			if (map.getConceptReferenceTerm() != null &&
			    map.getConceptReferenceTerm().getConceptSource() != null) {
				
				org.openmrs.ConceptSource source = map.getConceptReferenceTerm().getConceptSource();
				String sourceName = source.getName();
				String sourceHl7Code = source.getHl7Code();
				
				// Buscar por nombre
				boolean isCpms = false;
				if (sourceName != null) {
					String sourceNameUpper = sourceName.toUpperCase();
					for (String variant : cpmsVariants) {
						if (sourceNameUpper.contains(variant.toUpperCase())) {
							isCpms = true;
							break;
						}
					}
				}
				
				// También buscar por HL7 Code
				if (!isCpms && sourceHl7Code != null) {
					String hl7CodeUpper = sourceHl7Code.toUpperCase();
					for (String variant : cpmsVariants) {
						if (hl7CodeUpper.contains(variant.toUpperCase())) {
							isCpms = true;
							break;
						}
					}
				}
				
				if (isCpms) {
					String code = map.getConceptReferenceTerm().getCode();
					if (code != null && !code.isEmpty()) {
						// Validar formato básico de CPMS (generalmente numérico o alfanumérico)
						if (isValidCpmsFormat(code)) {
							log.debug("✓ Mapeo CPMS encontrado: " + code + " para Concept: " + concept.getUuid());
							return code;
						} else {
							log.warn("⚠ Código CPMS con formato sospechoso: " + code + " (Concept: " + concept.getUuid() + ")");
							// Aún así retornarlo, puede ser válido
							return code;
						}
					}
				}
			}
		}
		
		// Si no se encontró, intentar buscar usando ConceptService
		return findCpmsMappingViaService(concept);
	}
	
	/**
	 * Valida el formato básico de un código CPMS CPMS generalmente tiene formato alfanumérico o
	 * numérico Ejemplos: "001", "A001", "001.01", etc.
	 */
	private static boolean isValidCpmsFormat(String code) {
		if (code == null || code.isEmpty()) {
			return false;
		}
		
		// Patrón básico: Al menos 3 caracteres alfanuméricos, opcionalmente con punto
		// Ejemplos: 001, A001, 001.01, etc.
		return code.matches("^[A-Z0-9]{3,}(\\.[A-Z0-9]{1,})?$");
	}
	
	/**
	 * Busca mapeo CPMS usando ConceptService (método alternativo)
	 */
	private static String findCpmsMappingViaService(Concept concept) {
		try {
			org.openmrs.api.ConceptService conceptService = 
				org.openmrs.api.context.Context.getConceptService();
			
			if (conceptService != null) {
				// Buscar ConceptSource de CPMS
				org.openmrs.ConceptSource cpmsSource = null;
				
				// Buscar por nombre
				String[] cpmsNames = {"CPMS", "CATALOGO PROCEDIMIENTOS MEDICOS", "PROCEDIMIENTOS MEDICOS"};
				for (String name : cpmsNames) {
					try {
						cpmsSource = conceptService.getConceptSourceByName(name);
						if (cpmsSource != null) {
							break;
						}
					} catch (Exception e) {
						// Continuar buscando
					}
				}
				
				if (cpmsSource != null) {
					// Buscar ConceptReferenceTerm para este Concept y Source
					// Usar reflexión para mayor compatibilidad
					try {
						java.lang.reflect.Method getConceptReferenceTerms = 
							conceptService.getClass().getMethod("getConceptReferenceTerms", Concept.class, org.openmrs.ConceptSource.class);
						Object result = getConceptReferenceTerms.invoke(conceptService, concept, cpmsSource);
						
						if (result instanceof java.util.Collection) {
							java.util.Collection<?> terms = (java.util.Collection<?>) result;
							if (!terms.isEmpty()) {
								Object term = terms.iterator().next();
								if (term != null) {
									java.lang.reflect.Method getCode = term.getClass().getMethod("getCode");
									Object codeObj = getCode.invoke(term);
									if (codeObj != null) {
										String code = codeObj.toString();
										log.debug("✓ Mapeo CPMS encontrado vía ConceptService: " + code);
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
			log.debug("No se pudo buscar CPMS vía ConceptService: " + e.getMessage());
		}
		
		return null;
	}
}
