package es.tragsa.sido.backendtests.framework.config;

/**
 * Resolucion de la configuracion del entorno de pruebas.
 *
 * <p>Orden de precedencia de cada valor:
 * <ol>
 *   <li>Propiedad del sistema (por ejemplo {@code -Dsido.backend.baseUri=https://x}).</li>
 *   <li>Variable de entorno (por ejemplo {@code SIDO_BACKEND_BASE_URI}).</li>
 *   <li>Valor por defecto de esta clase.</li>
 * </ol>
 *
 * <p>Las propiedades se pueden fijar desde la linea de comandos con
 * {@code mvn test -Dsido.backend.baseUri=...} porque {@code pom.xml} las reenvia al
 * proceso de Surefire.
 */
public final class TestEnvironment {

    public static final String BASE_URI = resolve("sido.backend.baseUri", "SIDO_BACKEND_BASE_URI",
            "https://sido2prepro.tragsa.es");

    public static final String BEARER_TOKEN = resolve("sido.backend.token", "SIDO_BACKEND_BEARER_TOKEN", "");

    public static final String CLIENT_ID = resolve("sido.backend.clientId", "SIDO_BACKEND_CLIENT_ID", "SIDO_WEB");

    public static final String CLIENT_VERSION = resolve("sido.backend.clientVersion", "SIDO_BACKEND_CLIENT_VERSION",
            "2.135");

    public static final int TIMEOUT_MS = intValue(
            resolve("sido.backend.timeoutMs", "SIDO_BACKEND_TIMEOUT_MS", "30000"));

    /**
     * Verificacion del certificado TLS. Por defecto {@code false} porque los entornos de
     * preproduccion suelen usar una CA interna que no esta en el almacen de la maquina.
     */
    public static final boolean VERIFY_SSL = Boolean
            .parseBoolean(resolve("sido.backend.verifySsl", "SIDO_BACKEND_VERIFY_SSL", "false"));

    /**
     * Filtro de ejecucion por accion o nombre de escenario, por ejemplo
     * {@code -Dsido.backend.scenario=getUserContextWithoutToken}. Vacio significa todos.
     */
    public static final String SCENARIO = resolve("sido.backend.scenario", "SIDO_BACKEND_SCENARIO", "");

    private TestEnvironment() {
    }

    public static String resolve(String systemProperty, String environmentVariable, String defaultValue) {
        return resolve(systemProperty, environmentVariable, defaultValue, false);
    }

    public static String resolve(String systemProperty, String environmentVariable, String defaultValue,
            boolean required) {
        String value = System.getProperty(systemProperty);

        if (isBlank(value)) {
            value = System.getenv(environmentVariable);
        }

        if (isBlank(value)) {
            if (required) {
                throw new IllegalStateException("Falta configurar '" + environmentVariable + "'. Usa la variable de "
                        + "entorno o la propiedad -D" + systemProperty + "=...");
            }
            return defaultValue;
        }

        return value.trim();
    }

    public static String requireBearerToken() {
        return resolve("sido.backend.token", "SIDO_BACKEND_BEARER_TOKEN", null, true);
    }

    public static String baseUri() {
        String baseUri = BASE_URI;

        while (baseUri.endsWith("/")) {
            baseUri = baseUri.substring(0, baseUri.length() - 1);
        }

        return baseUri;
    }

    public static boolean hasBearerToken() {
        return !isBlank(BEARER_TOKEN);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Convierte un valor de entorno o propiedad en numero, con el valor por defecto si no es un
     * entero valido (por ejemplo si la propiedad llega vacia desde el {@code pom.xml}).
     */
    static int intValue(String value) {
        if (isBlank(value)) {
            return 0;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Se esperaba un numero entero y se recibio '" + value + "'.", e);
        }
    }
}
