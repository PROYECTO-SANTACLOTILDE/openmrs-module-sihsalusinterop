/**
 * Mappers de Conversión OpenMRS ↔ FHIR R4
 * 
 * Este paquete contiene clases que convierten entre modelos de datos de OpenMRS
 * y recursos FHIR R4 compatibles con los perfiles peruanos (MINSA/RENHICE).
 * 
 * <h2>Mappers Disponibles:</h2>
 * <ul>
 *   <li>{@link org.openmrs.module.sihsalusinterop.api.mapper.DyakuPatientMapper} - 
 *       Convierte org.openmrs.Patient a org.hl7.fhir.r4.model.Patient</li>
 * </ul>
 * 
 * <h2>Ejemplo de Uso:</h2>
 * <pre>{@code
 * // Obtener paciente de OpenMRS
 * Patient openmrsPatient = Context.getPatientService().getPatient(123);
 * 
 * // Convertir a FHIR R4 (Perfil MINSA)
 * org.hl7.fhir.r4.model.Patient fhirPatient = DyakuPatientMapper.toDyakuFhir(openmrsPatient);
 * 
 * // Serializar a JSON
 * FhirContext ctx = FhirContext.forR4();
 * String json = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(fhirPatient);
 * 
 * // Encolar para envío a RENHICE
 * DyakuSenderService service = Context.getService(DyakuSenderService.class);
 * service.queueMessage("FHIR_BUNDLE", json, "http://renhice.minsa.gob.pe/fhir");
 * }</pre>
 * 
 * @author Hospital Santa Clotilde - SIH.SALUS Team
 * @version 1.0.0
 */
package org.openmrs.module.sihsalusinterop.api.mapper;

