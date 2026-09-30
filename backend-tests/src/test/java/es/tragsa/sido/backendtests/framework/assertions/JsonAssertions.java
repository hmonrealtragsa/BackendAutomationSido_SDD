package es.tragsa.sido.backendtests.framework.assertions;

import com.fasterxml.jackson.databind.JsonNode;
import es.tragsa.sido.backendtests.framework.model.Expectation;
import es.tragsa.sido.backendtests.framework.support.Json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Comparacion entre el JSON esperado (cargado desde {@code responses/*.json}) y la respuesta
 * real del microservicio.
 *
 * <p>Modo {@code partial}: la respuesta debe contener todos los datos del JSON esperado; puede
 * tener campos y elementos de mas. Es el modo habitual en un test de integracion.
 *
 * <p>Modo {@code exact}: la respuesta debe ser identica al JSON esperado (salvo las rutas de
 * {@code ignorePaths}).
 *
 * <p>Sentinelas admitidos dentro del JSON esperado:
 * <ul>
 *   <li>{@code {"$any": true}}: el valor da igual (ids, fechas, tokens).</li>
 *   <li>{@code {"$type": "array"}}: solo se comprueba el tipo.</li>
 *   <li>{@code {"$regex": "^ES-.*"}: el texto encaja con la expresion regular.</li>
 *   <li>{@code {"$contains": "texto"}: el texto contiene ese fragmento, o el array contiene ese
 *       elemento.</li>
 * </ul>
 */
public final class JsonAssertions {

    public static final String SENTINEL_ANY = "$any";
    public static final String SENTINEL_TYPE = "$type";
    public static final String SENTINEL_REGEX = "$regex";
    public static final String SENTINEL_CONTAINS = "$contains";

    private JsonAssertions() {
    }

    /**
     * Comprueba la respuesta y lanza {@link AssertionError} con todas las diferencias juntas.
     */
    public static void assertMatches(JsonNode actual, JsonNode expected, MatchOptions options, String description) {
        List<String> differences = differences(actual, expected, options);

        if (differences.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("La respuesta no coincide con el JSON esperado (")
                .append(options.isExact() ? "matchMode: exact" : "matchMode: partial").append(") en ")
                .append(description).append(":");

        for (String difference : differences) {
            message.append("\n  - ").append(difference);
        }

        message.append("\n\nRespuesta real:\n").append(Json.pretty(actual));

        throw new AssertionError(message.toString());
    }

    public static List<String> differences(JsonNode actual, JsonNode expected, MatchOptions options) {
        List<String> differences = new ArrayList<String>();
        compare(actual, expected, new String[0], options, differences);
        return differences;
    }

    private static void compare(JsonNode actual, JsonNode expected, String[] path, MatchOptions options,
            List<String> differences) {
        if (options.isIgnored(path)) {
            return;
        }

        if (expected.isObject() && isSentinel(expected)) {
            compareSentinel(actual, expected, path, options, differences);
            return;
        }

        if (expected.isObject()) {
            compareObjects(actual, expected, path, options, differences);
            return;
        }

        if (expected.isArray()) {
            compareArrays(actual, expected, path, options, differences);
            return;
        }

        if (!valuesAreEqual(actual, expected)) {
            differences.add(display(path) + ": se esperaba " + Json.describe(expected) + " pero la respuesta tiene "
                    + Json.describe(actual));
        }
    }

    private static void compareSentinel(JsonNode actual, JsonNode expected, String[] path, MatchOptions options,
            List<String> differences) {
        if (expected.has(SENTINEL_ANY) && expected.get(SENTINEL_ANY).asBoolean()) {
            return;
        }

        if (expected.has(SENTINEL_TYPE)) {
            String expectedType = expected.get(SENTINEL_TYPE).asText();
            String actualType = typeOf(actual);

            if (!expectedType.equalsIgnoreCase(actualType)) {
                differences.add(display(path) + ": se esperaba un valor de tipo '" + expectedType + "' pero es '"
                        + actualType + "' (" + Json.describe(actual) + ")");
            }
            return;
        }

        if (expected.has(SENTINEL_REGEX)) {
            String regex = expected.get(SENTINEL_REGEX).asText();

            if (!actual.isTextual() || !matches(regex, actual.textValue())) {
                differences.add(display(path) + ": se esperaba un texto que encaje con /" + regex + "/ pero es "
                        + Json.describe(actual));
            }
            return;
        }

        if (expected.has(SENTINEL_CONTAINS)) {
            JsonNode fragment = expected.get(SENTINEL_CONTAINS);

            if (actual.isTextual() && fragment.isTextual()) {
                if (!actual.textValue().contains(fragment.textValue())) {
                    differences.add(display(path) + ": se esperaba un texto que contenga '" + fragment.textValue()
                            + "' pero es '" + actual.textValue() + "'");
                }
                return;
            }

            if (actual.isArray()) {
                for (JsonNode item : actual) {
                    if (differences(item, fragment, options).isEmpty()) {
                        return;
                    }
                }

                differences.add(display(path) + ": el array no contiene el elemento " + Json.write(fragment)
                        + ". Contenido: " + Json.write(actual));
                return;
            }

            differences.add(display(path) + ": $contains no se puede aplicar a " + Json.describe(actual));
        }
    }

    private static void compareObjects(JsonNode actual, JsonNode expected, String[] path, MatchOptions options,
            List<String> differences) {
        if (!actual.isObject()) {
            differences.add(display(path) + ": se esperaba un objeto pero la respuesta tiene "
                    + Json.describe(actual));
            return;
        }

        for (java.util.Iterator<String> fields = expected.fieldNames(); fields.hasNext();) {
            String field = fields.next();
            JsonNode expectedValue = expected.get(field);
            JsonNode actualValue = actual.get(field);

            if (actualValue == null) {
                if (!options.isIgnored(append(path, field))) {
                    differences.add(display(append(path, field)) + ": el campo no existe en la respuesta");
                }
                continue;
            }

            compare(actualValue, expectedValue, append(path, field), options, differences);
        }

        if (options.isExact()) {
            for (java.util.Iterator<String> fields = actual.fieldNames(); fields.hasNext();) {
                String field = fields.next();

                if (!expected.has(field) && !options.isIgnored(append(path, field))) {
                    differences.add(display(append(path, field)) + ": campo inesperado en la respuesta, "
                            + "el JSON esperado no lo declara (usa matchMode: partial si es correcto)");
                }
            }
        }
    }

    private static void compareArrays(JsonNode actual, JsonNode expected, String[] path, MatchOptions options,
            List<String> differences) {
        if (!actual.isArray()) {
            differences.add(display(path) + ": se esperaba un array pero la respuesta tiene " + Json.describe(actual));
            return;
        }

        if (options.isArrayMatch(Expectation.ARRAY_BY_KEY)) {
            compareArraysByKey(actual, expected, path, options, differences);
            return;
        }

        if (options.isArrayMatch(Expectation.ARRAY_CONTAINS)) {
            compareArraysContains(actual, expected, path, options, differences);
            return;
        }

        if (options.isExact() && actual.size() != expected.size()) {
            differences.add(display(path) + ": el array tiene " + actual.size() + " elementos y el JSON esperado "
                    + expected.size() + ". Usa matchMode: partial o arrayMatch: contains");
            return;
        }

        if (!options.isExact() && actual.size() < expected.size()) {
            differences.add(display(path) + ": el array tiene " + actual.size() + " elementos y el JSON esperado "
                    + "declara " + expected.size());
            return;
        }

        for (int i = 0; i < expected.size(); i++) {
            compare(actual.get(i), expected.get(i), append(path, String.valueOf(i)), options, differences);
        }
    }

    private static void compareArraysByKey(JsonNode actual, JsonNode expected, String[] path, MatchOptions options,
            List<String> differences) {
        String key = options.getArrayKey();

        if (key == null) {
            throw new IllegalStateException("El spec usa \"arrayMatch\": \"byKey\" pero no declara \"arrayKey\" "
                    + "en " + display(path) + ".");
        }

        for (JsonNode expectedItem : expected) {
            JsonNode expectedKey = Json.at(expectedItem, key);

            if (Json.isMissingOrNull(expectedKey)) {
                differences.add(display(path) + ": un elemento esperado no tiene la clave '" + key + "'");
                continue;
            }

            JsonNode actualItem = findByKey(actual, key, expectedKey);

            if (actualItem == null) {
                differences.add(display(path) + ": no existe ningun elemento con " + key + " = "
                        + Json.describe(expectedKey));
                continue;
            }

            compare(actualItem, expectedItem, append(path, Json.describe(expectedKey)), options, differences);
        }
    }

    private static void compareArraysContains(JsonNode actual, JsonNode expected, String[] path, MatchOptions options,
            List<String> differences) {
        for (int i = 0; i < expected.size(); i++) {
            JsonNode expectedItem = expected.get(i);

            if (findByContains(actual, expectedItem, options) != null) {
                continue;
            }

            // Ningun elemento encaja. Se informa del mas parecido en lugar de un "no contiene" a
            // secas, que no dice nada sobre cual es el campo que falla.
            JsonNode closest = closestTo(actual, expectedItem, options);
            List<String> closestDifferences = closest == null
                    ? new ArrayList<String>()
                    : differences(closest, expectedItem, options);

            if (closestDifferences.isEmpty()) {
                differences.add(display(path) + ": el array esta vacio, no hay ningun elemento que comparar");
            } else {
                differences.add(display(path) + ": ningun elemento encaja con el esperado. El mas parecido "
                        + "falla en:");
                for (String difference : closestDifferences) {
                    differences.add("    " + difference);
                }
            }
        }
    }

    private static JsonNode findByContains(JsonNode array, JsonNode expectedItem, MatchOptions options) {
        for (JsonNode actualItem : array) {
            if (differences(actualItem, expectedItem, options).isEmpty()) {
                return actualItem;
            }
        }

        return null;
    }

    /**
     * Elemento del array con menos diferencias respecto al esperado, para poder explicar el fallo.
     */
    private static JsonNode closestTo(JsonNode array, JsonNode expectedItem, MatchOptions options) {
        JsonNode closest = null;
        int fewest = Integer.MAX_VALUE;

        for (JsonNode actualItem : array) {
            int count = differences(actualItem, expectedItem, options).size();

            if (count < fewest) {
                fewest = count;
                closest = actualItem;
            }
        }

        return closest;
    }

    private static JsonNode findByKey(JsonNode array, String key, JsonNode expectedKey) {
        for (JsonNode item : array) {
            JsonNode candidate = Json.at(item, key);

            if (!Json.isMissingOrNull(candidate) && valuesAreEqual(candidate, expectedKey)) {
                return item;
            }
        }

        return null;
    }

    private static boolean valuesAreEqual(JsonNode actual, JsonNode expected) {
        if (Json.isMissingOrNull(actual) || Json.isMissingOrNull(expected)) {
            return Json.isMissingOrNull(actual) && Json.isMissingOrNull(expected);
        }

        if (actual.isNumber() && expected.isNumber()) {
            return toDecimal(actual).compareTo(toDecimal(expected)) == 0;
        }

        if (actual.isTextual() && expected.isTextual()) {
            return actual.textValue().equals(expected.textValue());
        }

        return actual.equals(expected);
    }

    private static BigDecimal toDecimal(JsonNode node) {
        return node.decimalValue();
    }

    private static boolean isSentinel(JsonNode node) {
        return node.has(SENTINEL_ANY) || node.has(SENTINEL_TYPE) || node.has(SENTINEL_REGEX)
                || node.has(SENTINEL_CONTAINS);
    }

    private static boolean matches(String regex, String value) {
        try {
            return Pattern.matches(regex, value);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("La expresion regular del JSON esperado no es valida: " + regex, e);
        }
    }

    static String typeOf(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return "ausente";
        }
        if (node.isNull()) {
            return "null";
        }
        if (node.isArray()) {
            return "array";
        }
        if (node.isObject()) {
            return "object";
        }
        if (node.isTextual()) {
            return "string";
        }
        if (node.isNumber()) {
            return "number";
        }
        if (node.isBoolean()) {
            return "boolean";
        }
        return "unknown";
    }

    static String display(String[] path) {
        StringBuilder result = new StringBuilder("$");

        for (String segment : path) {
            if (segment.isEmpty()) {
                continue;
            }

            if (segment.matches("\\d+")) {
                result.append('[').append(segment).append(']');
            } else {
                result.append('.').append(segment);
            }
        }

        return result.length() == 1 ? "$" : result.toString();
    }

    private static String[] append(String[] path, String segment) {
        String[] result = Arrays.copyOf(path, path.length + 1);
        result[path.length] = segment;
        return result;
    }

    /**
     * Opciones de comparacion derivadas del bloque {@code expect} del spec.
     */
    public static final class MatchOptions {

        private final boolean exact;
        private final List<String[]> ignorePaths;
        private final String arrayMatch;
        private final String arrayKey;

        public MatchOptions(Expectation expectation) {
            this(Expectation.MATCH_EXACT.equalsIgnoreCase(expectation.getMatchMode()),
                    expectation.getIgnorePaths(), expectation.getArrayMatch(), expectation.getArrayKey());
        }

        public MatchOptions(boolean exact, List<String> ignorePaths, String arrayMatch, String arrayKey) {
            this.exact = exact;
            this.ignorePaths = new ArrayList<String[]>();
            this.arrayMatch = arrayMatch;
            this.arrayKey = arrayKey;

            for (String ignorePath : ignorePaths) {
                this.ignorePaths.add(toSegments(ignorePath));
            }
        }

        public static MatchOptions exact() {
            return new MatchOptions(true, new ArrayList<String>(), Expectation.ARRAY_INDEX, null);
        }

        public boolean isExact() {
            return exact;
        }

        public String getArrayMatch() {
            return arrayMatch;
        }

        public String getArrayKey() {
            return arrayKey;
        }

        /**
         * Compara el modo de comparacion de arrays con una constante sin distinguir mayusculas,
         * para que {@code "byKey"} y {@code "bykey"} sean el mismo modo.
         */
        public boolean isArrayMatch(String mode) {
            return mode.equalsIgnoreCase(arrayMatch);
        }

        boolean isIgnored(String[] path) {
            for (String[] ignorePath : ignorePaths) {
                if (matchesPrefix(ignorePath, path)) {
                    return true;
                }
            }

            return false;
        }

        private static boolean matchesPrefix(String[] prefix, String[] path) {
            if (prefix.length > path.length) {
                return false;
            }

            for (int i = 0; i < prefix.length; i++) {
                if ("*".equals(prefix[i])) {
                    continue;
                }

                if (!prefix[i].equals(path[i])) {
                    return false;
                }
            }

            return true;
        }

        private static String[] toSegments(String path) {
            String pointer = Json.toPointer(path);

            if (pointer.isEmpty() || "/".equals(pointer)) {
                return new String[0];
            }

            pointer = pointer.startsWith("/") ? pointer.substring(1) : pointer;
            return pointer.split("/");
        }
    }
}
