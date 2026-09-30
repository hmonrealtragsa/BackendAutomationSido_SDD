package es.tragsa.sido.backendtests.framework.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.tragsa.sido.backendtests.framework.config.TestEnvironment;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interpolacion de marcadores {@code ${...}} en specs, bodies y responses.
 *
 * <p>Marcadores soportados:
 * <ul>
 *   <li>{@code ${baseUri}}: raiz del entorno ({@code TestEnvironment.baseUri()}).</li>
 *   <li>{@code ${data.campo}}: valor del escenario, primero {@code data} y luego {@code given}.</li>
 *   <li>{@code ${env:NOMBRE}} o {@code ${env.NOMBRE}}: variable de entorno.</li>
 *   <li>{@code ${today}} {@code dd/MM/yyyy}, {@code ${todayIso}} {@code yyyy-MM-dd}.</li>
 *   <li>{@code ${nowIso}}, {@code ${epochMillis}}, {@code ${uuid}}.</li>
 * </ul>
 *
 * <p>Si el valor es exactamente un marcador y el dato de origen no es texto, se sustituye por
 * el nodo original, de modo que un {@code ${data.id}} numerico sigue siendo numerico en el body.
 */
public final class Placeholders {

    private static final Pattern TOKEN = Pattern.compile("\\$\\{([^{}]+)}");

    private static final Pattern SINGLE_TOKEN = Pattern.compile("^\\$\\{([^{}]+)}$");

    private static final int MAX_PASSES = 5;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final DateTimeFormatter ISO_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private Placeholders() {
    }

    public static JsonNode interpolate(JsonNode node, JsonNode context) {
        if (node == null) {
            return null;
        }

        if (node.isObject()) {
            ObjectNode result = Json.object();

            for (java.util.Iterator<String> fields = node.fieldNames(); fields.hasNext();) {
                String field = fields.next();
                result.set(field, interpolate(node.get(field), context));
            }

            return result;
        }

        if (node.isArray()) {
            ArrayNode result = Json.array();

            for (JsonNode item : node) {
                result.add(interpolate(item, context));
            }

            return result;
        }

        if (node.isTextual()) {
            return interpolateValue(node.textValue(), context);
        }

        return Json.deepCopy(node);
    }

    private static JsonNode interpolateValue(String value, JsonNode context) {
        if (value == null || !value.contains("${")) {
            return Json.text(value);
        }

        Matcher single = SINGLE_TOKEN.matcher(value);

        if (single.matches()) {
            JsonNode resolved = resolveNode(single.group(1).trim(), context);

            if (resolved != null && !resolved.isTextual()) {
                return Json.deepCopy(resolved);
            }
        }

        return Json.text(resolveText(value, context));
    }

    /**
     * Sustituye los marcadores de un texto. Repite la sustitucion sobre el resultado para
     * que un dato que a su vez sea un marcador ({@code "fecha": "${data.fecha}"}) con
     * {@code data.fecha = "${todayIso}"} se resuelva entero.
     */
    public static String resolveText(String value, JsonNode context) {
        String current = value;

        for (int pass = 0; pass < MAX_PASSES && current != null && current.contains("${"); pass++) {
            String resolved = resolvePass(current, context, pass == 0);

            if (resolved.equals(current)) {
                break;
            }

            current = resolved;
        }

        return current;
    }

    private static String resolvePass(String value, JsonNode context, boolean strict) {
        Matcher matcher = TOKEN.matcher(value);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String marker = matcher.group(1).trim();
            JsonNode resolved = resolveNode(marker, context);
            String replacement = resolved == null || resolved.isMissingNode() ? null : Json.describe(resolved);

            if (replacement == null) {
                if (strict) {
                    throw unknownMarker(marker, context);
                }

                replacement = matcher.group(0);
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Resuelve un marcador suelto a su nodo JSON, sin convertirlo a texto.
     *
     * <p>Es lo que usa el runner para leer la lista de un {@code foreach}: el valor puede ser un
     * array, un objeto o un numero, y perder el tipo seria perder el sentido de la operacion.
     *
     * <p>Acepta las dos formas de escribir el marcador: {@code ${data.vehiculos}} y
     * {@code data.vehiculos}.
     *
     * @return el nodo resuelto, o un nodo ausente si el marcador no existe
     */
    public static JsonNode resolve(String marker, JsonNode context) {
        String name = unwrap(marker);
        JsonNode resolved = resolveNode(name, context);

        return resolved == null ? Json.mapper().getNodeFactory().missingNode() : resolved;
    }

    /**
     * Quita la envoltura {@code ${...}} de un marcador, si la trae.
     */
    public static String unwrap(String marker) {
        if (marker == null) {
            return null;
        }

        String value = marker.trim();

        if (value.startsWith("${") && value.endsWith("}")) {
            return value.substring(2, value.length() - 1).trim();
        }

        return value;
    }

    private static JsonNode resolveNode(String marker, JsonNode context) {
        if ("baseUri".equals(marker)) {
            return Json.text(TestEnvironment.baseUri());
        }

        if (marker.startsWith("env:") || marker.startsWith("env.")) {
            String value = System.getenv(marker.substring(4));
            return value == null ? null : Json.text(value);
        }

        if (marker.startsWith("data.")) {
            return context == null ? null : Json.at(context, "data/" + marker.substring("data.".length()));
        }

        if (marker.startsWith("given.")) {
            return context == null ? null : Json.at(context, "given/" + marker.substring("given.".length()));
        }

        if ("today".equals(marker)) {
            return Json.text(LocalDate.now().format(DAY));
        }

        if ("todayIso".equals(marker)) {
            return Json.text(LocalDate.now().format(ISO_DAY));
        }

        if ("nowIso".equals(marker)) {
            return Json.text(OffsetDateTime.now().toString());
        }

        if ("epochMillis".equals(marker)) {
            return Json.mapper().getNodeFactory().numberNode(System.currentTimeMillis());
        }

        if ("uuid".equals(marker)) {
            return Json.text(UUID.randomUUID().toString());
        }

        // Cualquier otra clave existe en la raiz del contexto, que es donde el runner cuelga el
        // elemento de un foreach: "${vehiculo.matricula}" con "foreach.as": "vehiculo".
        return context == null ? null : Json.at(context, marker);
    }

    private static IllegalArgumentException unknownMarker(String marker, JsonNode context) {
        return new IllegalArgumentException("Marcador desconocido '${" + marker + "}'." + dataKeys(context)
                + " Marcadores validos: ${baseUri}, ${data.campo}, ${env:NOMBRE}, ${today}, ${todayIso},"
                + " ${nowIso}, ${epochMillis}, ${uuid}.");
    }

    private static String dataKeys(JsonNode context) {
        if (context == null || context.isEmpty()) {
            return "";
        }

        StringBuilder keys = new StringBuilder(" Datos del escenario:");

        for (java.util.Iterator<String> fields = context.fieldNames(); fields.hasNext();) {
            keys.append(' ').append(fields.next());
        }

        return keys.toString();
    }
}
