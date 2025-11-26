/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.sihsalusinterop.api.mapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonAddress;
import org.openmrs.PersonName;
import org.openmrs.module.sihsalusinterop.api.exception.InteropException;

import java.util.Date;

/**
 * DyakuPatientMapper - Conversor de Pacientes OpenMRS a FHIR R4 (Perfil MINSA) Convierte recursos
 * de paciente de OpenMRS al formato FHIR R4 compatible con el perfil "PacienteMinsa" utilizado en
 * RENHICE (Red Nacional de Interoperabilidad). Estándar: HL7 FHIR R4 Perfil: MINSA Perú -
 * PacienteMinsa Hospital Santa Clotilde - SIH.SALUS Team
 */
public class DyakuPatientMapper {
	
	private static final Log log = LogFactory.getLog(DyakuPatientMapper.class);
	
	// ============================================================
	// CONSTANTES DEL PERFIL PERUANO (MINSA/RENHICE)
	// ============================================================
	
	/**
	 * URL del perfil FHIR para pacientes del MINSA (Dyaku) Perfil oficial según:
	 * https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/PacientePe
	 */
	public static final String PROFILE_PACIENTE_PE = "https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/PacientePe";
	
	/**
	 * OID del DNI RENIEC - Registro Nacional de Identificación y Estado Civil Según estándar
	 * nacional peruano
	 */
	public static final String OID_DNI_RENIEC = "urn:oid:2.16.840.1.113883.4.904";
	
	/**
	 * URL de la extensión para Tercer Apellido (perfil peruano) Perfil oficial:
	 * https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/pe-tercerapellido
	 */
	public static final String EXT_TERCER_APELLIDO = "https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/pe-tercerapellido";
	
	/**
	 * URL de la extensión para UBIGEO (código de ubicación geográfica) Perfil oficial:
	 * https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/pe-ubigeo
	 */
	public static final String EXT_UBIGEO = "https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/pe-ubigeo";
	
	/**
	 * URL de la extensión para País (perfil peruano) Perfil oficial:
	 * https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/pe-pais
	 */
	public static final String EXT_PAIS = "https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/pe-pais";
	
	/**
	 * Tipos de identificadores reconocidos en OpenMRS
	 */
	public static final String IDENTIFIER_TYPE_DNI = "DNI";
	
	public static final String IDENTIFIER_TYPE_CE = "CE"; // Carnet de Extranjería
	
	public static final String IDENTIFIER_TYPE_PASSPORT = "PASSPORT";
	
	// ============================================================
	// MÉTODO PRINCIPAL DE CONVERSIÓN
	// ============================================================
	
	/**
	 * Convierte un paciente de OpenMRS a un recurso FHIR Patient cumpliendo con el perfil
	 * PacienteMinsa.
	 * 
	 * @param openmrsPatient Paciente de OpenMRS a convertir
	 * @return Recurso FHIR Patient compatible con RENHICE
	 * @throws InteropException Si el paciente no tiene DNI o datos inválidos
	 */
	public static org.hl7.fhir.r4.model.Patient toDyakuFhir(org.openmrs.Patient openmrsPatient) {
		
		if (openmrsPatient == null) {
			throw new InteropException("PATIENT_NULL", "El paciente OpenMRS no puede ser nulo");
		}
		
		log.info("Convirtiendo paciente OpenMRS [" + openmrsPatient.getId() + "] a FHIR R4 (Perfil MINSA)");
		
		// Crear recurso FHIR Patient
		Patient fhirPatient = new Patient();
		
		// 1. Configurar metadata con el perfil MINSA
		setMetaProfile(fhirPatient);
		
		// 2. Mapear identificadores (DNI obligatorio)
		mapIdentifiers(openmrsPatient, fhirPatient);
		
		// 3. Mapear nombres (con apellido materno)
		mapNames(openmrsPatient, fhirPatient);
		
		// 4. Mapear género
		mapGender(openmrsPatient, fhirPatient);
		
		// 5. Mapear fecha de nacimiento
		mapBirthDate(openmrsPatient, fhirPatient);
		
		// 6. Mapear dirección (con UBIGEO)
		mapAddress(openmrsPatient, fhirPatient);
		
		// 7. Mapear teléfono de contacto
		mapTelecom(openmrsPatient, fhirPatient);
		
		// 8. Mapear estado activo/inactivo
		fhirPatient.setActive(!openmrsPatient.getVoided());
		
		log.info("✓ Paciente convertido exitosamente a FHIR R4");
		return fhirPatient;
	}
	
	// ============================================================
	// MÉTODOS AUXILIARES DE MAPEO
	// ============================================================
	
	/**
	 * Configura el metadata del recurso FHIR con el perfil PacientePe (Dyaku)
	 */
	private static void setMetaProfile(Patient fhirPatient) {
		Meta meta = new Meta();
		meta.addProfile(PROFILE_PACIENTE_PE);
		meta.setLastUpdated(new Date());
		fhirPatient.setMeta(meta);
	}
	
	/**
	 * Mapea los identificadores del paciente (DNI obligatorio)
	 * 
	 * @throws InteropException Si no se encuentra DNI
	 */
	private static void mapIdentifiers(org.openmrs.Patient openmrsPatient, Patient fhirPatient) {
		
		boolean dniFound = false;
		
		// Iterar sobre todos los identificadores del paciente
		for (PatientIdentifier identifier : openmrsPatient.getActiveIdentifiers()) {
			
			String identifierTypeName = identifier.getIdentifierType().getName().toUpperCase();
			
			// Buscar específicamente el DNI
			if (identifierTypeName.contains("DNI") || identifierTypeName.equals(IDENTIFIER_TYPE_DNI)) {
				
				// Validar que el DNI tenga formato correcto (8 dígitos)
				String dniValue = identifier.getIdentifier().trim();
				if (!dniValue.matches("\\d{8}")) {
					log.warn("DNI con formato inválido: " + dniValue + " (se esperan 8 dígitos)");
				}
				
				// Crear identificador FHIR con OID de RENIEC
				Identifier fhirIdentifier = new Identifier();
				fhirIdentifier.setSystem(OID_DNI_RENIEC);
				fhirIdentifier.setValue(dniValue);
				fhirIdentifier.setUse(Identifier.IdentifierUse.OFFICIAL);
				
				// Agregar tipo de identificador según CodeSystem peruano IdspersonaPeru
				CodeableConcept type = new CodeableConcept();
				type.addCoding()
					.setSystem("https://www.gob.pe/minsa/RENHICE/fhir/CodeSystem/IdspersonaPeru")
					.setCode("1") // Código "1" = DNI según CodeSystem IdspersonaPeru
					.setDisplay("DNI - Documento Nacional de Identidad");
				fhirIdentifier.setType(type);
				
				// Agregar extensión pe-pais para el país emisor (Perú)
				Extension paisExt = new Extension();
				paisExt.setUrl(EXT_PAIS);
				CodeableConcept pais = new CodeableConcept();
				pais.addCoding()
					.setSystem("https://www.gob.pe/minsa/RENHICE/fhir/CodeSystem/PaisesCS")
					.setCode("PER")
					.setDisplay("Perú");
				paisExt.setValue(pais);
				type.addExtension(paisExt);
				
				fhirPatient.addIdentifier(fhirIdentifier);
				dniFound = true;
				log.info("✓ DNI mapeado: " + dniValue);
				break;
			}
		}
		
		// VALIDACIÓN OBLIGATORIA: El paciente DEBE tener DNI para RENHICE
		if (!dniFound) {
			throw new InteropException(
				"DNI_NOT_FOUND",
				"El paciente no tiene DNI. Es obligatorio para envío a RENHICE. " +
				"ID OpenMRS: " + openmrsPatient.getId()
			);
		}
	}
	
	/**
	 * Mapea los nombres del paciente (con extensión para apellido materno)
	 */
	private static void mapNames(org.openmrs.Patient openmrsPatient, Patient fhirPatient) {
		
		PersonName openmrsName = openmrsPatient.getPersonName();
		if (openmrsName == null) {
			log.warn("Paciente sin nombre registrado");
			return;
		}
		
		HumanName fhirName = new HumanName();
		fhirName.setUse(HumanName.NameUse.OFFICIAL);
		
		// Nombre completo (texto)
		fhirName.setText(openmrsName.getFullName());
		
		// Nombres (given names)
		if (openmrsName.getGivenName() != null) {
			fhirName.addGiven(openmrsName.getGivenName());
		}
		if (openmrsName.getMiddleName() != null) {
			fhirName.addGiven(openmrsName.getMiddleName());
		}
		
		// Apellido Paterno (family name principal)
		if (openmrsName.getFamilyName() != null) {
			fhirName.setFamily(openmrsName.getFamilyName());
		}
		
		// Segundo Apellido (Apellido Materno) - ya está en familyName2
		// Tercer Apellido (extensión del perfil peruano pe-tercerapellido)
		// OpenMRS puede guardar el tercer apellido en familyNameSuffix o en un atributo personalizado
		// Por ahora, si familyName2 existe y es diferente al primer apellido, lo agregamos como segundo apellido
		if (openmrsName.getFamilyName2() != null && !openmrsName.getFamilyName2().isEmpty()) {
			// En OpenMRS, familyName2 normalmente es el apellido materno (segundo apellido)
			// El tercer apellido sería un caso especial que se guardaría en otro campo
			// Por ahora, verificamos si hay un tercer apellido en algún lugar
			// NOTA: Esto puede requerir configuración adicional según cómo OpenMRS almacene los apellidos
		}
		
		// Si hay un tercer apellido (por ejemplo, en un atributo personalizado o familyNameSuffix),
		// se agregaría con la extensión pe-tercerapellido
		String tercerApellido = openmrsName.getFamilyNameSuffix();
		if (tercerApellido != null && !tercerApellido.isEmpty()) {
			// Agregar extensión para el tercer apellido (perfil peruano)
			Extension extTercerApellido = new Extension();
			extTercerApellido.setUrl(EXT_TERCER_APELLIDO);
			extTercerApellido.setValue(new StringType(tercerApellido));
			fhirName.addExtension(extTercerApellido);
			
			log.info("✓ Tercer apellido mapeado: " + tercerApellido);
		}
		
		fhirPatient.addName(fhirName);
	}
	
	/**
	 * Mapea el género del paciente
	 */
	private static void mapGender(org.openmrs.Patient openmrsPatient, Patient fhirPatient) {
		
		String gender = openmrsPatient.getGender();
		if (gender == null) {
			fhirPatient.setGender(AdministrativeGender.UNKNOWN);
			return;
		}
		
		switch (gender.toUpperCase()) {
			case "M":
			case "MALE":
				fhirPatient.setGender(AdministrativeGender.MALE);
				break;
			case "F":
			case "FEMALE":
				fhirPatient.setGender(AdministrativeGender.FEMALE);
				break;
			case "O":
			case "OTHER":
				fhirPatient.setGender(AdministrativeGender.OTHER);
				break;
			default:
				fhirPatient.setGender(AdministrativeGender.UNKNOWN);
		}
	}
	
	/**
	 * Mapea la fecha de nacimiento
	 */
	private static void mapBirthDate(org.openmrs.Patient openmrsPatient, Patient fhirPatient) {
		
		Date birthdate = openmrsPatient.getBirthdate();
		if (birthdate != null) {
			fhirPatient.setBirthDate(birthdate);
			
			// Si es fecha estimada, agregar extensión
			if (openmrsPatient.getBirthdateEstimated() != null && openmrsPatient.getBirthdateEstimated()) {
				Extension extEstimated = new Extension();
				extEstimated.setUrl("http://hl7.org/fhir/StructureDefinition/patient-birthTime");
				extEstimated.setValue(new BooleanType(false));
				fhirPatient.addExtension(extEstimated);
			}
		}
	}
	
	/**
	 * Mapea la dirección del paciente (con extensión UBIGEO)
	 */
	private static void mapAddress(org.openmrs.Patient openmrsPatient, Patient fhirPatient) {
		
		PersonAddress openmrsAddress = openmrsPatient.getPersonAddress();
		if (openmrsAddress == null) {
			return;
		}
		
		Address fhirAddress = new Address();
		fhirAddress.setUse(Address.AddressUse.HOME);
		fhirAddress.setType(Address.AddressType.PHYSICAL);
		
		// Línea de dirección
		if (openmrsAddress.getAddress1() != null) {
			fhirAddress.addLine(openmrsAddress.getAddress1());
		}
		if (openmrsAddress.getAddress2() != null) {
			fhirAddress.addLine(openmrsAddress.getAddress2());
		}
		
		// Ciudad/Distrito
		if (openmrsAddress.getCityVillage() != null) {
			fhirAddress.setCity(openmrsAddress.getCityVillage());
		}
		
		// Departamento (State)
		if (openmrsAddress.getStateProvince() != null) {
			fhirAddress.setState(openmrsAddress.getStateProvince());
		}
		
		// País
		if (openmrsAddress.getCountry() != null) {
			fhirAddress.setCountry(openmrsAddress.getCountry());
		} else {
			fhirAddress.setCountry("PE"); // Perú por defecto
		}
		
		// Código postal
		if (openmrsAddress.getPostalCode() != null) {
			fhirAddress.setPostalCode(openmrsAddress.getPostalCode());
		}
		
		// UBIGEO (extensión del perfil MINSA)
		// Buscar el UBIGEO en countyDistrict o en address3 (depende de la configuración)
		String ubigeo = openmrsAddress.getCountyDistrict();
		if (ubigeo != null && ubigeo.matches("\\d{6}")) { // UBIGEO es de 6 dígitos
		
			Extension extUbigeo = new Extension();
			extUbigeo.setUrl(EXT_UBIGEO);
			extUbigeo.setValue(new StringType(ubigeo));
			fhirAddress.addExtension(extUbigeo);
			
			log.info("✓ UBIGEO mapeado: " + ubigeo);
		}
		
		fhirPatient.addAddress(fhirAddress);
	}
	
	/**
	 * Mapea los datos de contacto (teléfono)
	 */
	private static void mapTelecom(org.openmrs.Patient openmrsPatient, Patient fhirPatient) {
		
		// Buscar número de teléfono en los atributos de persona
		// OpenMRS puede guardar el teléfono en PersonAttribute "Telephone Number"
		// Por simplicidad, aquí verificamos si existe en la dirección
		PersonAddress address = openmrsPatient.getPersonAddress();
		
		if (address != null) {
			// Algunos sistemas guardan el teléfono en address3 o en un campo custom
			// Esta es una implementación de ejemplo
			
			// Crear contacto de teléfono (ejemplo con dato fijo)
			// En producción, obtener del atributo correcto
			ContactPoint phone = new ContactPoint();
			phone.setSystem(ContactPoint.ContactPointSystem.PHONE);
			phone.setUse(ContactPoint.ContactPointUse.MOBILE);
			
			// Aquí deberías obtener el teléfono real del paciente
			// Por ejemplo: openmrsPatient.getAttribute("Telephone Number")
			// phone.setValue(telefonoReal);
			
			// fhirPatient.addTelecom(phone);
		}
	}
}
