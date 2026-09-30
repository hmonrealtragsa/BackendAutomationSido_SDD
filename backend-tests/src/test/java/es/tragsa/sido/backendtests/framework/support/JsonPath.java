package es.tragsa.sido.backendtests.framework.support;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Evaluacion de rutas estilo JsonPath sobre nodos Jackson.
 *
 * <p>Soporta {@code $.data.id}, {@code $.data[0].id} y {@code $['clave con espacio']}.
 * Los filtros ({@code $.data[?(@.id=='1')]}) y el descenso recursivo ({@code $..id}) no estan
 * soportados y fallan con un mensaje explicito, para no dar un falso negativo silencioso.
 */
public final class JsonPath {

    private JsonPath() {
    }

    public static JsonNode read(JsonNode root, String path) {
        checkSupported(path);

        if (root == null) {
            return Json.mapper().getNodeFactory().missingNode();
        }

        JsonNode node = Json.at(root, path);

        return node == null ? Json.mapper().getNodeFactory().missingNode() : node;
    }

    /**
     * Texto de un valor de la ruta, o {@code null} si no existe.
     */
    public static String readText(JsonNode root, String path) {
        JsonNode node = read(root, path);

        return Json.isMissingOrNull(node) ? null : Json.describe(node);
    }

    private static void checkSupported(String path) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("La ruta JsonPath no puede estar vacia.");
        }

        if (path.contains("?(") || path.contains("?@")) {
            throw new IllegalArgumentException("Los filtros JsonPath no estan soportados ('" + path
                    + "'). Usa una comprobacion por campo o 'arrayMatch': 'byKey'.");
        }

        if (path.contains("..")) {
            throw new IllegalArgumentException("El descenso recursivo JsonPath no esta soportado ('" + path
                    + "'). Indica la ruta exacta del campo.");
        }
    }
}
