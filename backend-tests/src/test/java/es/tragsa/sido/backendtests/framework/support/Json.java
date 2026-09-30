package es.tragsa.sido.backendtests.framework.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utilidades JSON sobre Jackson.
 */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode array() {
        return MAPPER.createArrayNode();
    }

    public static JsonNode text(String value) {
        return MAPPER.getNodeFactory().textNode(value);
    }

    public static JsonNode parse(String content) {
        try {
            return MAPPER.readTree(content);
        } catch (Exception e) {
            throw new IllegalArgumentException("El contenido no es JSON valido: " + e.getMessage(), e);
        }
    }

    public static String write(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el JSON: " + e.getMessage(), e);
        }
    }

    public static String pretty(JsonNode node) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo serializar el JSON: " + e.getMessage(), e);
        }
    }

    public static JsonNode deepCopy(JsonNode node) {
        return node == null ? null : node.deepCopy();
    }

    public static boolean isMissingOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull();
    }

    public static JsonNode get(JsonNode node, String field) {
        if (node == null || !node.isObject()) {
            return null;
        }
        return node.get(field);
    }

    public static String text(JsonNode node, String defaultValue) {
        if (isMissingOrNull(node)) {
            return defaultValue;
        }
        return node.isTextual() ? node.textValue() : node.toString();
    }

    /**
     * Convierte una ruta con puntos y corchetes en un JSON pointer.
     * Ejemplos: {@code usuario.id -> /usuario/id}, {@code partes[0].id -> /partes/0/id}.
     */
    public static String toPointer(String path) {
        if (path == null) {
            return "";
        }

        String value = path.trim();

        if (value.startsWith("$")) {
            value = value.substring(1);
        }
        if (value.isEmpty() || value.startsWith("/")) {
            return value;
        }

        StringBuilder pointer = new StringBuilder();
        StringBuilder token = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);

            if (current == '.') {
                flush(pointer, token);
            } else if (current == '[') {
                flush(pointer, token);
                int close = value.indexOf(']', i);
                if (close < 0) {
                    throw new IllegalArgumentException("Ruta mal formada, falta ']' en: " + path);
                }
                pointer.append('/').append(value.substring(i + 1, close).replace("'", ""));
                i = close;
            } else {
                token.append(current);
            }
        }

        flush(pointer, token);
        return pointer.toString();
    }

    /**
     * Resuelve una ruta con puntos y corchetes sobre un nodo JSON.
     */
    public static JsonNode at(JsonNode node, String path) {
        if (node == null) {
            return null;
        }

        String pointer = toPointer(path);

        if (pointer.isEmpty()) {
            return node;
        }

        return node.at(pointer);
    }

    /**
     * Texto legible de un valor: el texto si es textual, si no el JSON compacto.
     */
    public static String describe(JsonNode node) {
        if (isMissingOrNull(node)) {
            return "<ausente>";
        }
        return node.isTextual() ? node.textValue() : node.toString();
    }

    /**
     * Convierte un objeto JSON en un mapa utilizable como query params de un step Java,
     * conservando el tipo (texto, numero, booleano).
     */
    public static Map<String, Object> toMap(JsonNode node) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();

        if (node == null || !node.isObject()) {
            return map;
        }

        for (java.util.Iterator<String> names = node.fieldNames(); names.hasNext();) {
            String name = names.next();
            JsonNode value = node.get(name);

            if (value.isNull()) {
                map.put(name, null);
            } else if (value.isTextual()) {
                map.put(name, value.textValue());
            } else if (value.isNumber()) {
                map.put(name, value.numberValue());
            } else if (value.isBoolean()) {
                map.put(name, value.asBoolean());
            } else {
                map.put(name, value);
            }
        }

        return map;
    }

    private static void flush(StringBuilder pointer, StringBuilder token) {
        if (token.length() > 0) {
            pointer.append('/').append(token);
            token.setLength(0);
        }
    }
}
