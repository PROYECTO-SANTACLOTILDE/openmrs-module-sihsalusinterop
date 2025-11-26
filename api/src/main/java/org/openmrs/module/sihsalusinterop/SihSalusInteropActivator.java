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
import org.openmrs.api.context.Context;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.scheduler.SchedulerException;
import org.openmrs.scheduler.TaskDefinition;
import org.openmrs.util.DatabaseUpdater;
import org.openmrs.module.sihsalusinterop.api.advice.EncounterSavedAdvice;
import org.openmrs.api.EncounterService;

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
			// Registrar bean fhirR4 en el contexto global de OpenMRS
			// Esto asegura que esté disponible para otros módulos (ej: FHIR2)
			registerFhirR4Bean();
			log.info("✓ Bean fhirR4 registrado en contexto global");
		} catch (Exception e) {
			log.warn("⚠ No se pudo registrar bean fhirR4 globalmente: " + e.getMessage());
			// No lanzar excepción, el bean ya está en moduleApplicationContext.xml
		}
		
		try {
			// Ejecutar Liquibase para crear/actualizar las tablas
			runLiquibase();
			log.info("✓ Tablas de base de datos creadas/actualizadas correctamente");
		} catch (Exception e) {
			log.error("✗ Error al ejecutar Liquibase", e);
			throw new RuntimeException("Error al inicializar la base de datos del módulo SIH SALUS Interop", e);
		}
		
		try {
			// Registrar scheduler para procesar cola automáticamente
			registerScheduler();
			log.info("✓ Scheduler registrado correctamente");
		} catch (Exception e) {
			log.error("✗ Error al registrar scheduler", e);
			// No lanzar excepción para no impedir el arranque del módulo
		}
		
		try {
			// Registrar advice para interceptar saveEncounter
			registerEncounterSavedAdvice();
			log.info("✓ EncounterSavedAdvice registrado correctamente");
		} catch (Exception e) {
			log.error("✗ Error al registrar EncounterSavedAdvice", e);
			// No lanzar excepción para no impedir el arranque del módulo
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
	 * Registra el bean fhirR4 en el contexto global de OpenMRS
	 * Esto asegura que esté disponible para otros módulos que lo necesiten (ej: FHIR2)
	 */
	private void registerFhirR4Bean() {
		try {
			// Obtener el ApplicationContext del módulo
			org.openmrs.module.Module mod = org.openmrs.module.ModuleFactory.getModuleById("sihsalusinterop");
			if (mod != null) {
				// El bean ya está definido en moduleApplicationContext.xml
				// Solo verificamos que esté disponible
				org.springframework.context.ApplicationContext appContext = 
					(org.springframework.context.ApplicationContext) mod.getClass().getMethod("getApplicationContext").invoke(mod);
				
				if (appContext != null && appContext.containsBean("fhirR4")) {
					log.info("Bean fhirR4 verificado en contexto del módulo");
				}
			}
		} catch (Exception e) {
			log.debug("No se pudo verificar bean fhirR4: " + e.getMessage());
		}
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
	
	/**
	 * Registra el scheduler para procesar la cola automáticamente cada 5 minutos
	 */
	private void registerScheduler() throws SchedulerException {
		// Usar método sin importar el tipo específico del servicio
		Object schedulerService = Context.getSchedulerService();
		
		if (schedulerService == null) {
			log.warn("SchedulerService no disponible. El procesamiento automático de cola no se activará.");
			log.warn("NOTA: Puedes registrar manualmente la tarea desde la interfaz de administración de OpenMRS");
			return;
		}
		
		// Usar reflexión para evitar problemas de compilación si el servicio no está disponible
		try {
			java.lang.reflect.Method getTaskByName = schedulerService.getClass().getMethod("getTaskByName", String.class);
			String taskName = "SIH SALUS Interop Queue Processor";
			TaskDefinition taskDef = (TaskDefinition) getTaskByName.invoke(schedulerService, taskName);
			
			if (taskDef == null) {
				// Crear nueva tarea
				taskDef = new TaskDefinition();
				taskDef.setName(taskName);
				taskDef.setDescription("Procesa automáticamente la cola de mensajes FHIR pendientes cada 5 minutos");
				taskDef.setTaskClass("org.openmrs.module.sihsalusinterop.api.tasks.QueueProcessorTask");
				taskDef.setStartOnStartup(true);
				taskDef.setRepeatInterval(300000L); // 5 minutos = 300,000 ms
				taskDef.setStartTime(null); // Iniciar inmediatamente
				
				java.lang.reflect.Method scheduleTask = schedulerService.getClass().getMethod("scheduleTask", TaskDefinition.class);
				scheduleTask.invoke(schedulerService, taskDef);
				log.info("Tarea programada creada: " + taskName);
			} else {
				// Verificar si está activa
				if (!taskDef.getStarted()) {
					taskDef.setStartOnStartup(true);
					taskDef.setRepeatInterval(300000L);
					java.lang.reflect.Method saveTask = schedulerService.getClass().getMethod("saveTaskDefinition", TaskDefinition.class);
					saveTask.invoke(schedulerService, taskDef);
					java.lang.reflect.Method scheduleTask = schedulerService.getClass().getMethod("scheduleTask", TaskDefinition.class);
					scheduleTask.invoke(schedulerService, taskDef);
					log.info("Tarea programada reactivada: " + taskName);
				} else {
					log.info("Tarea programada ya está activa: " + taskName);
				}
			}
		} catch (Exception e) {
			log.warn("No se pudo registrar el scheduler automáticamente. Error: " + e.getMessage());
			log.warn("Puedes registrar manualmente la tarea 'QueueProcessorTask' desde la interfaz de administración de OpenMRS");
		}
	}
	
	/**
	 * Registra el advice para interceptar saveEncounter del EncounterService
	 * Esto permite detectar automáticamente cuando se guarda un Encounter
	 * y construir el Bundle FHIR correspondiente.
	 */
	private void registerEncounterSavedAdvice() {
		try {
			// Verificar que el EncounterService esté disponible
			EncounterService encounterService = Context.getEncounterService();
			if (encounterService == null) {
				log.warn("EncounterService no disponible. El advice no se registrará.");
				return;
			}
			
			// Crear el advice
			// El advice ya verifica internamente que sea el método saveEncounter
			EncounterSavedAdvice advice = new EncounterSavedAdvice();
			
			// Registrar el advice usando Context.addAdvice() con la clase del servicio
			// OpenMRS espera un MethodInterceptor (Advice), no un Advisor
			Context.addAdvice(EncounterService.class, advice);
			
			log.info("EncounterSavedAdvice registrado exitosamente. Se interceptará saveEncounter().");
			
		} catch (Exception e) {
			log.error("Error al registrar EncounterSavedAdvice: " + e.getMessage(), e);
			// No lanzar excepción para no impedir el arranque del módulo
			log.warn("El advice no se registró, pero el módulo continuará funcionando. La detección automática de Encounter no estará activa.");
		}
	}

}