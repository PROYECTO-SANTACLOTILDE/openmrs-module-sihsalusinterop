/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.sihsalusinterop;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAddress;
import org.openmrs.PersonName;
import org.openmrs.module.sihsalusinterop.api.mapper.DyakuPatientMapper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * ManualTester - Clase de Prueba Manual para Interoperabilidad FHIR Esta clase permite probar el
 * flujo completo de conversión de pacientes OpenMRS a FHIR R4 sin necesidad de levantar toda la
 * infraestructura de OpenMRS. Ejecutar: Clic derecho → Run 'ManualTester.main()' Hospital Santa
 * Clotilde - SIH.SALUS Team
 */
public class ManualTester {
	
	// Configuración del servidor HAPI FHIR simulado
	private static final String HAPI_FHIR_URL = "http://localhost:8081/fhir";
	
	public static void main(String[] args) {
		
		System.out.println("╔════════════════════════════════════════════════════════════════╗");
		System.out.println("║   PRUEBA MANUAL - MÓDULO DE INTEROPERABILIDAD SIH.SALUS       ║");
		System.out.println("║   Hospital Santa Clotilde, Loreto, Perú                       ║");
		System.out.println("╚════════════════════════════════════════════════════════════════╝");
		System.out.println();
		
		try {
			// PASO 1: Crear paciente mock
			System.out.println("▶ PASO 1: Creando paciente OpenMRS ficticio...");
			org.openmrs.Patient mockPatient = createMockPatient();
			System.out.println("✅ Paciente creado:");
			System.out.println("   • Nombre: " + mockPatient.getPersonName().getFullName());
			System.out.println("   • DNI: " + getDNI(mockPatient));
			System.out.println("   • Género: " + mockPatient.getGender());
			System.out.println("   • Fecha Nacimiento: " + mockPatient.getBirthdate());
			System.out.println();
			
			// PASO 2: Convertir a FHIR R4
			System.out.println("▶ PASO 2: Convirtiendo a FHIR R4 (Perfil MINSA)...");
			Patient fhirPatient = DyakuPatientMapper.toDyakuFhir(mockPatient);
			System.out.println("✅ Conversión a objeto FHIR exitosa");
			System.out.println("   • Perfil: " + fhirPatient.getMeta().getProfile().get(0).getValue());
			System.out.println("   • Identificador System: " + fhirPatient.getIdentifier().get(0).getSystem());
			System.out.println("   • Identificador Value: " + fhirPatient.getIdentifier().get(0).getValue());
			System.out.println();
			
			// PASO 3: Serializar a JSON
			System.out.println("▶ PASO 3: Serializando a JSON FHIR...");
			FhirContext ctx = FhirContext.forR4();
			String jsonPatient = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(fhirPatient);
			
			System.out.println("✅ Serialización exitosa");
			System.out.println();
			System.out.println("════════════════ JSON FHIR GENERADO ════════════════");
			System.out.println(jsonPatient);
			System.out.println("════════════════════════════════════════════════════");
			System.out.println();
			
			// PASO 4: Validaciones visuales
			System.out.println("▶ PASO 4: Validaciones del JSON generado...");
			validateJson(jsonPatient);
			System.out.println();
			
			// PASO 5: Crear Bundle para envío
			System.out.println("▶ PASO 5: Creando Bundle FHIR (tipo transaction)...");
			Bundle bundle = createBundle(fhirPatient);
			String jsonBundle = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundle);
			
			System.out.println("✅ Bundle creado exitosamente");
			System.out.println("   • Tipo: " + bundle.getType());
			System.out.println("   • Entradas: " + bundle.getEntry().size());
			System.out.println();
			System.out.println("════════════════ BUNDLE FHIR PARA ENVÍO ════════════════");
			System.out.println(jsonBundle);
			System.out.println("════════════════════════════════════════════════════════");
			System.out.println();
			
			// PASO 6: Intentar envío al simulador (opcional)
			System.out.println("▶ PASO 6: Intentando envío al servidor HAPI FHIR...");
			System.out.println("   • Endpoint: " + HAPI_FHIR_URL);
			testSendToHapiFhir(ctx, bundle);
			System.out.println();
			
			// RESUMEN FINAL
			System.out.println("╔════════════════════════════════════════════════════════════════╗");
			System.out.println("║                    ✅ PRUEBA COMPLETADA                        ║");
			System.out.println("╚════════════════════════════════════════════════════════════════╝");
			System.out.println();
			System.out.println("🎯 SIGUIENTE PASO:");
			System.out.println("   1. Si el envío falló, levanta el servidor HAPI FHIR en puerto 8081");
			System.out.println("   2. Vuelve a ejecutar esta clase para probar el envío completo");
			System.out.println();
			
		}
		catch (Exception e) {
			System.err.println("❌ ERROR EN LA PRUEBA:");
			System.err.println("   " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Crea un paciente OpenMRS ficticio con datos válidos para Perú
	 */
	private static org.openmrs.Patient createMockPatient() {
		
		org.openmrs.Patient patient = new org.openmrs.Patient();
		
		// Configurar ID ficticio
		patient.setId(12345);
		
		// 1. Crear y agregar NOMBRE
		PersonName name = new PersonName();
		name.setGivenName("Juan");
		name.setMiddleName("Carlos");
		name.setFamilyName("PEREZ");
		name.setFamilyName2("GONZALES"); // Apellido materno
		patient.addName(name);
		
		// 2. Crear y agregar DNI (IDENTIFICADOR)
		PatientIdentifierType dniType = new PatientIdentifierType();
		dniType.setName("DNI");
		dniType.setDescription("Documento Nacional de Identidad");
		
		PatientIdentifier dniIdentifier = new PatientIdentifier();
		dniIdentifier.setIdentifierType(dniType);
		dniIdentifier.setIdentifier("10203040"); // DNI de 8 dígitos
		dniIdentifier.setPreferred(true);
		dniIdentifier.setVoided(false);
		
		Set<PatientIdentifier> identifiers = new HashSet<>();
		identifiers.add(dniIdentifier);
		patient.setIdentifiers(identifiers);
		
		// 3. Configurar GÉNERO
		patient.setGender("M"); // Masculino
		
		// 4. Configurar FECHA DE NACIMIENTO
		try {
			SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
			Date birthdate = sdf.parse("15/07/1985");
			patient.setBirthdate(birthdate);
			patient.setBirthdateEstimated(false);
		} catch (Exception e) {
			patient.setBirthdate(new Date());
		}
		
		// 5. Crear y agregar DIRECCIÓN (con UBIGEO)
		PersonAddress address = new PersonAddress();
		address.setAddress1("Jr. Los Libertadores 456");
		address.setAddress2("Barrio Belén");
		address.setCityVillage("Iquitos");
		address.setStateProvince("Loreto");
		address.setCountry("PE");
		address.setPostalCode("16001");
		address.setCountyDistrict("160101"); // UBIGEO de Loreto-Maynas-Iquitos (6 dígitos)
		address.setPreferred(true);
		patient.addAddress(address);
		
		// 6. Estado del paciente
		patient.setVoided(false);
		
		return patient;
	}
	
	/**
	 * Crea un Bundle FHIR tipo transaction para envío al servidor
	 */
	private static Bundle createBundle(Patient fhirPatient) {
		Bundle bundle = new Bundle();
		bundle.setType(Bundle.BundleType.TRANSACTION);
		
		// Agregar el paciente como entrada POST
		Bundle.BundleEntryComponent entry = bundle.addEntry();
		entry.setResource(fhirPatient);
		entry.getRequest().setMethod(Bundle.HTTPVerb.POST).setUrl("Patient");
		
		return bundle;
	}
	
	/**
	 * Valida que el JSON generado contenga los elementos esperados
	 */
	private static void validateJson(String json) {
		
		boolean valid = true;
		
		// Validar OID RENIEC
		if (json.contains("urn:oid:2.16.840.1.113883.4.447")) {
			System.out.println("   ✅ OID RENIEC presente");
		} else {
			System.out.println("   ❌ OID RENIEC NO encontrado");
			valid = false;
		}
		
		// Validar perfil MINSA
		if (json.contains("http://minsa.gob.pe/fhir/StructureDefinition/PacienteMinsa")) {
			System.out.println("   ✅ Perfil PacienteMinsa presente");
		} else {
			System.out.println("   ❌ Perfil PacienteMinsa NO encontrado");
			valid = false;
		}
		
		// Validar DNI
		if (json.contains("\"10203040\"")) {
			System.out.println("   ✅ DNI presente en el JSON");
		} else {
			System.out.println("   ❌ DNI NO encontrado");
			valid = false;
		}
		
		// Validar extensión de apellido materno
		if (json.contains("ApellidoMaterno")) {
			System.out.println("   ✅ Extensión ApellidoMaterno presente");
		} else {
			System.out.println("   ⚠️  Extensión ApellidoMaterno no presente (puede ser normal)");
		}
		
		// Validar UBIGEO
		if (json.contains("160101")) {
			System.out.println("   ✅ UBIGEO presente");
		} else {
			System.out.println("   ⚠️  UBIGEO no presente (puede ser normal)");
		}
		
		if (valid) {
			System.out.println("   ✅ TODAS LAS VALIDACIONES PASARON");
		} else {
			System.out.println("   ❌ ALGUNAS VALIDACIONES FALLARON");
		}
	}
	
	/**
	 * Intenta enviar el Bundle al servidor HAPI FHIR simulado
	 */
	private static void testSendToHapiFhir(FhirContext ctx, Bundle bundle) {
		try {
			// Configurar cliente FHIR
			IGenericClient client = ctx.newRestfulGenericClient(HAPI_FHIR_URL);
			
			// Configurar timeout
			ctx.getRestfulClientFactory().setConnectTimeout(5000); // 5 segundos
			ctx.getRestfulClientFactory().setSocketTimeout(5000);
			
			System.out.println("   • Conectando al servidor...");
			
			// Enviar Bundle mediante transacción
			Bundle response = client.transaction().withBundle(bundle).execute();
			
			System.out.println("✅ Envío exitoso al simulador HAPI FHIR");
			System.out.println("   • ID del recurso: " + response.getId());
			System.out.println("   • Entradas en respuesta: " + response.getEntry().size());
			
		}
		catch (Exception e) {
			System.out.println("⚠️  Fallo de conexión (Simulador apagado o no disponible)");
			System.out.println("   • Error: " + e.getMessage());
			System.out.println();
			System.out.println("   💡 SOLUCIÓN:");
			System.out.println("      1. Levantar el servidor HAPI FHIR:");
			System.out.println("         docker run -p 8081:8080 hapiproject/hapi:latest");
			System.out.println("      2. Volver a ejecutar esta clase");
		}
	}
	
	/**
	 * Obtiene el DNI del paciente (método auxiliar)
	 */
	private static String getDNI(org.openmrs.Patient patient) {
		for (PatientIdentifier identifier : patient.getIdentifiers()) {
			if (identifier.getIdentifierType().getName().contains("DNI")) {
				return identifier.getIdentifier();
			}
		}
		return "N/A";
	}
}
