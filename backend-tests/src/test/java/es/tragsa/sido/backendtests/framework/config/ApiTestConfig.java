package es.tragsa.sido.backendtests.framework.config;

import es.tragsa.sido.backendtests.framework.model.RequestDefinition;
import kong.unirest.Unirest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuracion global de Unirest y cabeceras comunes de las peticiones.
 *
 * <p>Unirest se configura una sola vez por JVM porque su {@code Config} es un singleton con
 * clientes ya construidos: cambiarla despues lanza {@code UnirestConfigException}. Por eso
 * aqui solo se fijan timeouts, validacion TLS y cabeceras por defecto (Accept, ClientId,
 * ClientVersion, Referer), y la autenticacion se resuelve por peticion.
 *
 * <p>A diferencia de RestAssured, Unirest no tiene una "request specification" reutilizable
 * para decidir si va autenticada o no. La cabecera {@code Authorization} se anade en cada
 * peticion segun el campo {@code auth} del spec, de modo que un escenario con
 * {@code auth: none} nunca lleva el token.
 */
public final class ApiTestConfig {

    public static final String ACCEPT = "application/json, text/plain, */*";

    private static boolean configured;

    private ApiTestConfig() {
    }

    /**
     * Aplica la configuracion global de Unirest una unica vez por JVM.
     *
     * <p>El guardia es obligatorio: {@code connectTimeout} y {@code socketTimeout} lanzan
     * {@code UnirestConfigException} si el cliente HTTP ya esta construido, y cada escenario
     * crea su propio {@code ApiClient}.
     *
     * <p>Por la misma razon la configuracion se aplica una vez para toda la JVM y
     * {@code Unirest.shutDown()} queda para el cierre de la JVM, no para el final de cada clase
     * de test: si se cerrara entre clases, la siguiente arrancaria con un cliente recien
     * construido al que ya no se le puede volver a fijar {@code verifySsl}, y fallaria la
     * validacion del certificado en cuanto el entorno usara una CA interna.
     */
    public static synchronized void configure() {
        if (configured) {
            return;
        }

        configured = true;

        Unirest.config()
                .verifySsl(TestEnvironment.VERIFY_SSL)
                .connectTimeout(TestEnvironment.TIMEOUT_MS)
                .socketTimeout(TestEnvironment.TIMEOUT_MS)
                .addDefaultHeader("Accept", ACCEPT)
                .addDefaultHeader("ClientId", TestEnvironment.CLIENT_ID)
                .addDefaultHeader("ClientVersion", TestEnvironment.CLIENT_VERSION)
                .addDefaultHeader("Referer", TestEnvironment.baseUri() + "/vehiculos/misPartesVehiculos");

        registerShutdownHook();
    }

    /**
     * Unirest mantiene un pool de conexiones con hilos vivos. Sin esto la JVM de Surefire puede
     * quedarse esperando al terminar, pero el cierre se hace aqui y no en un {@code @AfterAll}
     * para no perder la configuracion entre clases de test.
     */
    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                Unirest.shutDown();
            }
        }, "unirest-shutdown"));
    }

    /**
     * Cabeceras de autenticacion segun el modo declarado en el spec.
     *
     * @param auth uno de los modos de {@link RequestDefinition}; {@code none} devuelve el
     *             mapa vacio, {@code bearer} exige token y {@code default} lo anade solo
     *             si el entorno lo tiene configurado
     */
    public static Map<String, String> authHeaders(String auth) {
        if (RequestDefinition.AUTH_NONE.equals(auth)) {
            return Collections.emptyMap();
        }

        Map<String, String> headers = new LinkedHashMap<String, String>();

        if (RequestDefinition.AUTH_BEARER.equals(auth)) {
            headers.put("Authorization", "Bearer " + TestEnvironment.requireBearerToken());
        } else if (TestEnvironment.hasBearerToken()) {
            headers.put("Authorization", "Bearer " + TestEnvironment.BEARER_TOKEN);
        }

        return headers;
    }
}
