package org.openmrs.module.sihsalusinterop.api.mapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.*;
import org.openmrs.PersonAttribute;
import org.openmrs.Provider;
import org.openmrs.User;

/**
 * DyakuPractitionerMapper - Conversor de Practitioners OpenMRS a FHIR R4 (Perfil PractitionerPe)
 * Perfil peruano según: https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/PractitionerPe
 * Hospital Santa Clotilde - SIH.SALUS
 */
public class DyakuPractitionerMapper {
	
	private static final Log log = LogFactory.getLog(DyakuPractitionerMapper.class);
	
	// Perfil peruano
	public static final String PROFILE_PRACTITIONER_PE = "https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/PractitionerPe";
	
	// CodeSystem para identificadores de persona
	public static final String CS_IDENTIFICADORES_PERSONA = "https://www.gob.pe/minsa/RENHICE/fhir/CodeSystem/IdspersonaPeru";
	
	public static final String CODE_DNI = "1";
	
	public static final String CODE_CE = "2"; // Carné de Extranjería
	
	public static final String CODE_PASSPORT = "3";
	
	// CodeSystem para colegios profesionales
	public static final String CS_COLEGIOS_PROFESIONALES = "https://www.gob.pe/minsa/RENHICE/fhir/CodeSystem/ColegiosProfesionalesSaludCS";
	
	public static final String VS_COLEGIOS_PROFESIONALES = "https://www.gob.pe/minsa/RENHICE/fhir/ValueSet/ColegiosProfesionalesSaludVS";
	
	// Extensión para país emisor
	public static final String EXT_PAIS_EMISOR = "https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/pe-pais";
	
	/**
	 * OID del DNI RENIEC - Registro Nacional de Identificación y Estado Civil Según estándar
	 * nacional peruano
	 */
	public static final String OID_DNI_RENIEC = "urn:oid:2.16.840.1.113883.4.904";
	
	/**
	 * Convierte un User de OpenMRS a Practitioner FHIR R4 (Perfil PractitionerPe)
	 */
	public static Practitioner toDyakuFhir(User user) {
		if (user == null) {
			throw new IllegalArgumentException("User no puede ser nulo");
		}
		
		log.info("Convirtiendo User [" + user.getId() + "] a Practitioner FHIR R4 (Perfil PractitionerPe)");
		
		Practitioner practitioner = new Practitioner();
		
		// Meta con perfil peruano
		Meta meta = new Meta();
		meta.addProfile(PROFILE_PRACTITIONER_PE);
		practitioner.setMeta(meta);
		
		// Identificadores (DNI obligatorio según perfil)
		if (user.getPerson() != null) {
			mapIdentifiers(user.getPerson(), practitioner);
		}
		
		// Nombre
		if (user.getPerson() != null && user.getPersonName() != null) {
			mapNames(user.getPersonName(), practitioner);
		}
		
		// Calificaciones (CMP, RNE, etc.)
		mapQualifications(user, practitioner);
		
		log.info("✓ Practitioner convertido exitosamente");
		return practitioner;
	}
	
	/**
	 * Mapea los identificadores del profesional (DNI obligatorio)
	 */
	private static void mapIdentifiers(org.openmrs.Person person, Practitioner practitioner) {
		// Buscar DNI en atributos de persona
		PersonAttribute dniAttr = person.getAttribute("DNI");
		if (dniAttr == null) {
			// Buscar en otros atributos comunes
			for (PersonAttribute attr : person.getActiveAttributes()) {
				if (attr.getAttributeType() != null && 
				    attr.getAttributeType().getName().toUpperCase().contains("DNI")) {
					dniAttr = attr;
					break;
				}
			}
		}
		
		if (dniAttr != null && dniAttr.getValue() != null) {
			Identifier identifier = new Identifier();
			
			// System: OID de RENIEC para el identificador DNI
			identifier.setSystem(OID_DNI_RENIEC);
			identifier.setValue(dniAttr.getValue());
			identifier.setUse(Identifier.IdentifierUse.OFFICIAL);
			
			// Tipo de identificador (DNI) según CodeSystem peruano IdspersonaPeru
			CodeableConcept type = new CodeableConcept();
			type.addCoding()
				.setSystem(CS_IDENTIFICADORES_PERSONA)
				.setCode(CODE_DNI)
				.setDisplay("DNI - Documento Nacional de Identidad");
			identifier.setType(type);
			
			// Extensión para país emisor (Perú)
			Extension paisExt = new Extension();
			paisExt.setUrl(EXT_PAIS_EMISOR);
			CodeableConcept pais = new CodeableConcept();
			pais.addCoding()
				.setSystem("https://www.gob.pe/minsa/RENHICE/fhir/CodeSystem/PaisesCS")
				.setCode("PER")
				.setDisplay("Perú");
			paisExt.setValue(pais);
			type.addExtension(paisExt);
			
			practitioner.addIdentifier(identifier);
			
			log.info("✓ DNI mapeado: " + dniAttr.getValue());
		} else {
			log.warn("⚠ User sin DNI - El perfil PractitionerPe requiere identificador");
		}
	}
	
	/**
	 * Mapea los nombres del profesional
	 */
	private static void mapNames(org.openmrs.PersonName personName, Practitioner practitioner) {
		HumanName name = new HumanName();
		name.setUse(HumanName.NameUse.OFFICIAL);
		
		// Apellidos
		if (personName.getFamilyName() != null) {
			name.setFamily(personName.getFamilyName());
		}
		
		// Nombres
		if (personName.getGivenName() != null) {
			name.addGiven(personName.getGivenName());
		}
		if (personName.getMiddleName() != null) {
			name.addGiven(personName.getMiddleName());
		}
		
		// Texto completo
		name.setText(personName.getFullName());
		
		practitioner.addName(name);
	}
	
	/**
	 * Mapea las calificaciones profesionales (CMP, RNE, etc.)
	 */
	private static void mapQualifications(User user, Practitioner practitioner) {
		// Buscar Provider asociado al User
		org.openmrs.api.ProviderService providerService = 
			org.openmrs.api.context.Context.getProviderService();
		
		Provider provider = null;
		for (Provider p : providerService.getAllProviders(false)) {
			if (p.getPerson() != null && p.getPerson().equals(user.getPerson())) {
				provider = p;
				break;
			}
		}
		
		if (provider != null && provider.getIdentifier() != null) {
			Practitioner.PractitionerQualificationComponent qualification = new Practitioner.PractitionerQualificationComponent();
			
			// Identificador de colegiatura (CMP, RNE, etc.)
			Identifier qualIdentifier = new Identifier();
			qualIdentifier.setValue(provider.getIdentifier());
			
			// Código del colegio (ej: "01" = Colegio Médico del Perú)
			CodeableConcept code = new CodeableConcept();
			code.addCoding()
				.setSystem(CS_COLEGIOS_PROFESIONALES)
				.setCode("01") // TODO: Obtener código real según tipo de profesional
				.setDisplay("Colegio Médico del Perú");
			qualification.setCode(code);
			
			qualification.addIdentifier(qualIdentifier);
			practitioner.addQualification(qualification);
			
			log.info("✓ Calificación profesional mapeada: " + provider.getIdentifier());
		}
	}
}
