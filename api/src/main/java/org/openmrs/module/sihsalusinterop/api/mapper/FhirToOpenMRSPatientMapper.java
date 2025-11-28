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
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAddress;
import org.openmrs.PersonName;
import org.openmrs.api.context.Context;

import java.text.SimpleDateFormat;

/**
 * Mapper para convertir recursos FHIR Patient a entidades OpenMRS Patient Importa datos desde
 * RENHICE al sistema local
 */
public class FhirToOpenMRSPatientMapper {
	
	protected static final Log log = LogFactory.getLog(FhirToOpenMRSPatientMapper.class);
	
	/**
	 * Convierte un Patient FHIR a Patient OpenMRS
	 * 
	 * @param fhirPatient Patient FHIR desde RENHICE
	 * @return Patient OpenMRS
	 */
	public static Patient mapToOpenMRS(org.hl7.fhir.r4.model.Patient fhirPatient) {
		Patient patient = new Patient();
		
		try {
			// Nombre
			if (fhirPatient.hasName() && !fhirPatient.getName().isEmpty()) {
				HumanName fhirName = fhirPatient.getNameFirstRep();
				PersonName personName = new PersonName();
				
				if (fhirName.hasGiven()) {
					personName.setGivenName(fhirName.getGivenAsSingleString());
				}
				
				if (fhirName.hasFamily()) {
					personName.setFamilyName(fhirName.getFamily());
				}
				
				personName.setPreferred(true);
				patient.addName(personName);
			}
			
			// Género
			if (fhirPatient.hasGender()) {
				String gender = fhirPatient.getGender().toCode();
				if ("male".equals(gender)) {
					patient.setGender("M");
				} else if ("female".equals(gender)) {
					patient.setGender("F");
				} else {
					patient.setGender("U"); // Unknown
				}
			}
			
			// Fecha de nacimiento
			if (fhirPatient.hasBirthDate()) {
				patient.setBirthdate(fhirPatient.getBirthDate());
			}
			
			// Identificadores (DNI, etc.)
			boolean hasPrimaryIdentifier = false;
			String dniValue = null;
			
			if (fhirPatient.hasIdentifier()) {
				for (Identifier identifier : fhirPatient.getIdentifier()) {
					if (identifier.hasValue()) {
						PatientIdentifier patientIdentifier = new PatientIdentifier();
						patientIdentifier.setIdentifier(identifier.getValue());
						
						// Buscar o crear PatientIdentifierType
						PatientIdentifierType identifierType = null;
						
						// Si es DNI (OID RENIEC)
						if (identifier.hasSystem() && identifier.getSystem().contains("2.16.840.1.113883.4.904")) {
							dniValue = identifier.getValue();
							identifierType = Context.getPatientService().getPatientIdentifierTypeByName("DNI");
							if (identifierType == null) {
								// Intentar con Old Identification Number
								identifierType = Context.getPatientService().getPatientIdentifierTypeByName("Old Identification Number");
							}
							hasPrimaryIdentifier = true;
						} else {
							// Otro tipo de identificador
							identifierType = Context.getPatientService().getPatientIdentifierTypeByName("Old Identification Number");
						}
						
						if (identifierType != null) {
							patientIdentifier.setIdentifierType(identifierType);
							patientIdentifier.setPreferred(true);
							
							// Asignar location (requerido por OpenMRS)
							if (Context.getLocationService().getAllLocations(false).size() > 0) {
								patientIdentifier.setLocation(Context.getLocationService().getAllLocations(false).get(0));
							}
							
							patient.addIdentifier(patientIdentifier);
							log.info("Identificador agregado: " + identifier.getValue());
						} else {
							log.warn("No se encontró PatientIdentifierType para agregar identificador");
						}
					}
				}
			}
			
			// Agregar identificador obligatorio de Historia Clínica si no existe
			if (!hasPrimaryIdentifier || patient.getPatientIdentifier() == null) {
				PatientIdentifierType hcType = Context.getPatientService().getPatientIdentifierTypeByName("N° Historia Clínica");
				if (hcType == null) {
					// Intentar con otros nombres comunes
					hcType = Context.getPatientService().getPatientIdentifierTypeByName("OpenMRS ID");
				}
				
				if (hcType != null) {
					PatientIdentifier hcIdentifier = new PatientIdentifier();
					// Generar ID único basado en DNI o timestamp
					String hcValue = dniValue != null ? "HC-" + dniValue : "HC-" + System.currentTimeMillis();
					hcIdentifier.setIdentifier(hcValue);
					hcIdentifier.setIdentifierType(hcType);
					hcIdentifier.setPreferred(false);
					
					if (Context.getLocationService().getAllLocations(false).size() > 0) {
						hcIdentifier.setLocation(Context.getLocationService().getAllLocations(false).get(0));
					}
					
					patient.addIdentifier(hcIdentifier);
					log.info("Historia Clínica generada: " + hcValue);
				}
			}
			
			// Dirección
			if (fhirPatient.hasAddress() && !fhirPatient.getAddress().isEmpty()) {
				org.hl7.fhir.r4.model.Address fhirAddress = fhirPatient.getAddressFirstRep();
				PersonAddress personAddress = new PersonAddress();
				
				if (fhirAddress.hasCity()) {
					personAddress.setCityVillage(fhirAddress.getCity());
				}
				
				if (fhirAddress.hasState()) {
					personAddress.setStateProvince(fhirAddress.getState());
				}
				
				if (fhirAddress.hasCountry()) {
					personAddress.setCountry(fhirAddress.getCountry());
				}
				
				if (fhirAddress.hasPostalCode()) {
					personAddress.setPostalCode(fhirAddress.getPostalCode());
				}
				
				if (fhirAddress.hasLine() && !fhirAddress.getLine().isEmpty()) {
					personAddress.setAddress1(fhirAddress.getLine().get(0).getValue());
				}
				
				personAddress.setPreferred(true);
				patient.addAddress(personAddress);
			}
			
		} catch (Exception e) {
			log.error("Error al mapear Patient FHIR a OpenMRS", e);
		}
		
		return patient;
	}
	
	/**
	 * Actualiza un Patient OpenMRS existente con datos de un Patient FHIR
	 * 
	 * @param localPatient Patient existente en OpenMRS
	 * @param fhirPatient Patient FHIR desde RENHICE
	 * @return Patient OpenMRS actualizado
	 */
	public static Patient updateOpenMRSPatient(Patient localPatient, org.hl7.fhir.r4.model.Patient fhirPatient) {
		try {
			// Actualizar nombre si cambió
			if (fhirPatient.hasName() && !fhirPatient.getName().isEmpty()) {
				HumanName fhirName = fhirPatient.getNameFirstRep();
				PersonName personName = localPatient.getPersonName();
				
				if (personName == null) {
					personName = new PersonName();
					localPatient.addName(personName);
				}
				
				if (fhirName.hasGiven()) {
					personName.setGivenName(fhirName.getGivenAsSingleString());
				}
				
				if (fhirName.hasFamily()) {
					personName.setFamilyName(fhirName.getFamily());
				}
			}
			
			// Actualizar género
			if (fhirPatient.hasGender()) {
				String gender = fhirPatient.getGender().toCode();
				if ("male".equals(gender)) {
					localPatient.setGender("M");
				} else if ("female".equals(gender)) {
					localPatient.setGender("F");
				}
			}
			
			// Actualizar fecha de nacimiento
			if (fhirPatient.hasBirthDate()) {
				localPatient.setBirthdate(fhirPatient.getBirthDate());
			}
			
			log.info("Patient local actualizado con datos de RENHICE");
			
		}
		catch (Exception e) {
			log.error("Error al actualizar Patient OpenMRS", e);
		}
		
		return localPatient;
	}
}
