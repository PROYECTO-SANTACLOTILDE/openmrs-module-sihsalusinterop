package org.openmrs.module.sihsalusinterop;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.util.DatabaseUpdater;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * This class contains the logic that is run every time this module is either started or stopped.
 */
public class SihSalusInteropActivator extends BaseModuleActivator {

	protected Log log = LogFactory.getLog(getClass());

	/**
	 * @see BaseModuleActivator#started()
	 */
	@Override
	public void started() {
		log.info("=======================================================");
		log.info("SIH SALUS Interoperability Module - Iniciando...");
		log.info("=======================================================");
		
		try {
			// Ejecutar Liquibase para crear/actualizar las tablas
			runLiquibase();
			log.info("✓ Tablas de base de datos creadas/actualizadas correctamente");
		} catch (Exception e) {
			log.error("✗ Error al ejecutar Liquibase", e);
			throw new RuntimeException("Error al inicializar la base de datos del módulo SIH SALUS Interop", e);
		}
		
		log.info("=======================================================");
		log.info("SIH SALUS Interoperability Module - Iniciado exitosamente");
		log.info("=======================================================");
	}

	/**
	 * @see BaseModuleActivator#stopped()
	 */
	@Override
	public void stopped() {
		log.info("SIH SALUS Interoperability Module stopped");
	}

	/**
	 * Ejecuta Liquibase para crear/actualizar las tablas de la base de datos.
	 * Lee el archivo liquibase.xml y aplica los changeSets pendientes.
	 */
	private void runLiquibase() throws Exception {
		log.info("Ejecutando Liquibase para módulo SIH SALUS Interop...");
		
		Connection connection = null;
		Database database = null;
		Liquibase liquibase = null;
		
		try {
			// Obtener conexión a la base de datos
			connection = DatabaseUpdater.getConnection();
			
			// Crear objeto Database de Liquibase
			database = DatabaseFactory.getInstance()
				.findCorrectDatabaseImplementation(new JdbcConnection(connection));
			
			// Crear instancia de Liquibase con el archivo de changelog
			liquibase = new Liquibase(
				"liquibase.xml",
				new ClassLoaderResourceAccessor(getClass().getClassLoader()),
				database
			);
			
			// Ejecutar todos los changeSets pendientes
			liquibase.update((String) null);
			
			log.info("Liquibase ejecutado exitosamente - Tablas creadas/actualizadas");
			
		} catch (DatabaseException e) {
			log.error("Error de base de datos al ejecutar Liquibase", e);
			throw e;
		} catch (LiquibaseException e) {
			log.error("Error de Liquibase al ejecutar changesets", e);
			throw e;
		} finally {
			// Cerrar recursos de Liquibase
			if (liquibase != null && liquibase.getDatabase() != null) {
				try {
					liquibase.getDatabase().close();
				} catch (DatabaseException e) {
					log.warn("Error al cerrar Database de Liquibase", e);
				}
			}
			
			// Cerrar conexión
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException e) {
					log.warn("Error al cerrar conexión de base de datos", e);
				}
			}
		}
	}

}