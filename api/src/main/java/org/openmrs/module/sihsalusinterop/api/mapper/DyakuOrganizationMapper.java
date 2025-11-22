package org.openmrs.module.sihsalusinterop.api.mapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.*;
import org.openmrs.Location;

/**
 * DyakuOrganizationMapper - Conversor de Organizations OpenMRS a FHIR R4 (Perfil OrganizacionPe)
 * Perfil peruano según: https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/OrganizacionPe
 * Representa IPRESS (Instituciones Prestadoras de Servicios de Salud) Hospital Santa Clotilde -
 * SIH.SALUS
 */
public class DyakuOrganizationMapper {
	
	private static final Log log = LogFactory.getLog(DyakuOrganizationMapper.class);
	
	public static final String PROFILE_ORGANIZACION_PE = "https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/OrganizacionPe";
	
	public static final String CS_IPRESS = "https://www.gob.pe/minsa/RENHICE/fhir/CodeSystem/IPRESSCS";
	
	public static final String VS_IPRESS = "https://www.gob.pe/minsa/RENHICE/fhir/ValueSet/IPRESSVS";
	
	public static final String EXT_UBIGEO = "https://www.gob.pe/minsa/RENHICE/fhir/StructureDefinition/pe-ubigeo";
	
	/**
	 * Convierte una Location de OpenMRS a Organization FHIR R4 (Perfil OrganizacionPe) La Location
	 * en OpenMRS representa el establecimiento de salud (IPRESS)
	 */
	public static Organization toDyakuFhir(Location location) {
		if (location == null) {
			throw new IllegalArgumentException("Location no puede ser nula");
		}
		
		log.info("Convirtiendo Location [" + location.getId() + "] a Organization FHIR R4 (Perfil OrganizacionPe)");
		
		Organization organization = new Organization();
		
		// Meta con perfil peruano
		Meta meta = new Meta();
		meta.addProfile(PROFILE_ORGANIZACION_PE);
		organization.setMeta(meta);
		
		// Identificador RENIPRESS (obligatorio según perfil)
		// Buscar en atributos de Location o usar ID como fallback
		String renipressCode = null;
		for (org.openmrs.LocationAttribute attr : location.getActiveAttributes()) {
			if (attr.getAttributeType() != null &&
			    attr.getAttributeType().getName().toUpperCase().contains("RENIPRESS")) {
				renipressCode = attr.getValueReference();
				break;
			}
		}
		
		if (renipressCode != null && !renipressCode.isEmpty()) {
			Identifier identifier = new Identifier();
			CodeableConcept type = new CodeableConcept();
			type.addCoding()
				.setSystem(CS_IPRESS)
				.setCode("00031361") // TODO: Obtener código real del catálogo IPRESS
				.setDisplay("IPRESS");
			identifier.setType(type);
			identifier.setValue(renipressCode);
			organization.addIdentifier(identifier);
		} else {
			log.warn("⚠ Location sin código RENIPRESS. Usando ID como fallback.");
			Identifier identifier = new Identifier();
			identifier.setValue("LOC-" + location.getId());
			organization.addIdentifier(identifier);
		}
		
		// Estado activo (obligatorio según perfil)
		organization.setActive(!location.getRetired());
		
		// Tipo de organización
		CodeableConcept type = new CodeableConcept();
		type.addCoding()
			.setSystem("http://terminology.hl7.org/CodeSystem/organization-type")
			.setCode("prov")
			.setDisplay("Healthcare Provider");
		organization.addType(type);
		
		// Nombre (obligatorio según perfil)
		organization.setName(location.getName());
		
		// Dirección con extensión UBIGEO
		if (location.getAddress1() != null || location.getCityVillage() != null) {
			Address address = new Address();
			address.setUse(Address.AddressUse.WORK);
			address.setType(Address.AddressType.PHYSICAL);
			
			if (location.getAddress1() != null) {
				address.addLine(location.getAddress1());
			}
			if (location.getAddress2() != null) {
				address.addLine(location.getAddress2());
			}
			if (location.getCityVillage() != null) {
				address.setCity(location.getCityVillage());
			}
			if (location.getStateProvince() != null) {
				address.setState(location.getStateProvince());
			}
			if (location.getCountry() != null) {
				address.setCountry(location.getCountry());
			} else {
				address.setCountry("PE");
			}
			if (location.getPostalCode() != null) {
				address.setPostalCode(location.getPostalCode());
			}
			
			// Extensión UBIGEO (si está disponible)
			String ubigeo = location.getCountyDistrict();
			if (ubigeo != null && ubigeo.matches("\\d{6}")) {
				Extension ubigeoExt = new Extension();
				ubigeoExt.setUrl(EXT_UBIGEO);
				ubigeoExt.setValue(new StringType(ubigeo));
				address.addExtension(ubigeoExt);
			}
			
			organization.addAddress(address);
		}
		
		// Part Of (DISA o establecimiento padre)
		// TODO: Mapear si Location tiene parentLocation
		if (location.getParentLocation() != null) {
			// organization.getPartOf().setReference(...)
		}
		
		log.info("✓ Organization convertida exitosamente");
		return organization;
	}
}
