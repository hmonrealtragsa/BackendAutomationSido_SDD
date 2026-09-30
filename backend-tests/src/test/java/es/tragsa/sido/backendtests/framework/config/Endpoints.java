package es.tragsa.sido.backendtests.framework.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registro central de endpoints de los microservicios de SIDO2.
 *
 * <p>Aqui se declara una variable por endpoint. Los specs nunca escriben URLs: referencian
 * el nombre de la constante ({@code "endpoint": "SIDO2_USUARIOCONTEXTO"}) y el runner
 * resuelve la URL final.
 *
 * <p>En una constante se pueden usar los marcadores {@code ${baseUri}} (raiz del entorno) y
 * {@code ${env:NOMBRE}} (variable de entorno), de modo que cambiar de prepro a produccion
 * solo obliga a cambiar {@code SIDO_BACKEND_BASE_URI}.
 */
public final class Endpoints {

    public static final String BASE = "${baseUri}";

    public static final String SIDO2_USUARIOCONTEXTO = BASE + "/api/usuarios/usuariocontexto";

    public static final String SIDO2_USUARIOS_LIST = BASE + "/api/usuarios/usuarios";

    public static final String SIDO2_VEHICULOS_MISPARTESDIARIOS = BASE + "/api/vehiculos/partesdiariossituacion";

    public static final String SIDO2_VEHICULOS_LIST = BASE + "/api/vehiculos/vehiculos";

    private static final Map<String, String> REGISTRY = registry();

    private Endpoints() {
    }

    /**
     * Resuelve la referencia de endpoint de un spec.
     *
     * @param reference nombre de la constante del registro, URL absoluta o plantilla con marcadores
     * @return URL lista para la peticion
     */
    public static String url(String reference) {
        if (reference == null || reference.trim().isEmpty()) {
            throw new IllegalArgumentException("El spec no define 'request.endpoint'.");
        }

        String key = reference.trim();

        if (REGISTRY.containsKey(key)) {
            return resolve(REGISTRY.get(key));
        }

        if (key.contains("${") || key.startsWith("http://") || key.startsWith("https://") || key.startsWith("/")) {
            return resolve(key);
        }

        throw new IllegalArgumentException("El endpoint '" + key + "' no existe en "
                + Endpoints.class.getSimpleName() + ". Disponibles: " + String.join(", ", REGISTRY.keySet())
                + ". O bien indica la URL completa.");
    }

    /**
     * Sustituye los marcadores de una plantilla de URL.
     */
    public static String resolve(String template) {
        String resolved = template.replace("${baseUri}", TestEnvironment.baseUri());
        return es.tragsa.sido.backendtests.framework.support.Placeholders.resolveText(resolved, null);
    }

    public static Map<String, String> registry() {
        Map<String, String> registry = new LinkedHashMap<>();
        registry.put("SIDO2_USUARIOCONTEXTO", SIDO2_USUARIOCONTEXTO);
        registry.put("SIDO2_USUARIOS_LIST", SIDO2_USUARIOS_LIST);
        registry.put("SIDO2_VEHICULOS_MISPARTESDIARIOS", SIDO2_VEHICULOS_MISPARTESDIARIOS);
        registry.put("SIDO2_VEHICULOS_LIST", SIDO2_VEHICULOS_LIST);
        return Collections.unmodifiableMap(registry);
    }
}
