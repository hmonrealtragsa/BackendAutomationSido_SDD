package es.tragsa.sido.backendtests.framework.assertions;

import com.fasterxml.jackson.databind.JsonNode;
import es.tragsa.sido.backendtests.framework.clients.ApiResponse;
import es.tragsa.sido.backendtests.framework.model.Expectation;
import es.tragsa.sido.backendtests.framework.model.Scenario;
import es.tragsa.sido.backendtests.framework.support.Json;
import es.tragsa.sido.backendtests.framework.support.JsonPath;
import es.tragsa.sido.backendtests.framework.support.Placeholders;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Aplica el bloque {@code expect} de un spec sobre la respuesta real.
 *
 * <p>Se recogen todas las comprobaciones fallidas y se informa de una sola vez, para no tener
 * que ir corrigiendo el spec de un fallo en un fallo.
 */
public final class ResponseAssertions {

    private ResponseAssertions() {
    }

    public static void validate(ApiResponse response, Expectation expectation, JsonNode context, Scenario scenario) {
        List<String> failures = new ArrayList<String>();

        if (expectation.getStatus() != null && !expectation.getStatus().isNull()) {
            checkStatus(response, expectation.getStatus(), failures);
        }

        if (expectation.getContentType() != null) {
            checkContentType(response, expectation.getContentType(), failures);
        }

        checkHeaders(response, expectation, failures);
        checkBodyEmpty(response, expectation, failures);

        if (expectation.hasResponse()) {
            checkBody(response, expectation, context, failures);
        }

        for (Expectation.JsonPathCheck check : expectation.getJsonPaths()) {
            checkJsonPath(response, check, context, failures);
        }

        if (expectation.getResponseTimeMs() != null) {
            checkResponseTime(response, expectation.getResponseTimeMs(), failures);
        }

        if (failures.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder();

        if (scenario != null) {
            message.append(scenario.label()).append('\n');
        }

        message.append("Fallo la verificacion de la respuesta de ").append(response.describe()).append(':');

        for (String failure : failures) {
            message.append("\n  - ").append(failure);
        }

        if (scenario != null && scenario.getThen() != null) {
            message.append("\n\nThen del spec: ").append(scenario.getThen());
        }

        message.append("\n\nRespuesta:\n").append(ApiResponse.truncate(response.getBody()));
        message.append("\nCuerpo parseado:\n").append(prettyOrRaw(response));

        throw new AssertionError(message.toString());
    }

    private static void checkStatus(ApiResponse response, JsonNode expected, List<String> failures) {
        if (expected.isArray()) {
            List<String> labels = new ArrayList<String>();

            for (JsonNode item : expected) {
                labels.add(item.asText());

                if (matchesStatus(item.asText(), response.getStatusCode())) {
                    return;
                }
            }

            failures.add("status esperado uno de " + labels + " pero fue " + response.getStatusCode());
            return;
        }

        if (!matchesStatus(expected.asText(), response.getStatusCode())) {
            failures.add("status esperado " + expected.asText() + " pero fue " + response.getStatusCode());
        }
    }

    private static void checkResponseTime(ApiResponse response, int maxMillis, List<String> failures) {
        if (response.getElapsedMs() > maxMillis) {
            failures.add("la respuesta tardo " + response.getElapsedMs() + " ms y el maximo es " + maxMillis
                    + " ms");
        }
    }

    private static boolean matchesStatus(String expected, int actual) {
        String value = expected.trim();

        if (value.length() == 3 && (value.endsWith("x") || value.endsWith("X"))) {
            try {
                int family = Integer.parseInt(value.substring(0, 1));
                return actual / 100 == family;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        try {
            return Integer.parseInt(value) == actual;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void checkContentType(ApiResponse response, String expectedFragment, List<String> failures) {
        String contentType = response.header("Content-Type");

        if (contentType == null) {
            failures.add("no viene la cabecera Content-Type (se buscaba '" + expectedFragment + "')");
            return;
        }

        if (!contentType.toLowerCase(Locale.ROOT).contains(expectedFragment.toLowerCase(Locale.ROOT))) {
            failures.add("Content-Type '" + contentType + "' no contiene '" + expectedFragment + "'");
        }
    }

    private static void checkHeaders(ApiResponse response, Expectation expectation, List<String> failures) {
        for (Map.Entry<String, Expectation.Header> entry : expectation.getHeaders().entrySet()) {
            String name = entry.getKey();
            Expectation.Header matcher = entry.getValue();
            String actual = response.header(name);

            if (Boolean.FALSE.equals(matcher.getExists())) {
                if (actual != null) {
                    failures.add("la cabecera " + name + " no deberia venir pero es '" + actual + "'");
                }
                continue;
            }

            if (actual == null) {
                failures.add("falta la cabecera " + name + " en la respuesta");
                continue;
            }

            if (matcher.getEqualsValue() != null && !matcher.getEqualsValue().equals(actual)) {
                failures.add("cabecera " + name + " esperada '" + matcher.getEqualsValue() + "' pero es '" + actual + "'");
            }

            if (matcher.getContainsValue() != null && !actual.contains(matcher.getContainsValue())) {
                failures.add("cabecera " + name + " deberia contener '" + matcher.getContainsValue() + "' pero es '"
                        + actual + "'");
            }

            if (matcher.getMatches() != null && !Pattern.matches(matcher.getMatches(), actual)) {
                failures.add("cabecera " + name + " no encaja con /" + matcher.getMatches() + "/ pero es '" + actual
                        + "'");
            }
        }
    }

    private static void checkBodyEmpty(ApiResponse response, Expectation expectation, List<String> failures) {
        if (expectation.getBodyEmpty() == null) {
            return;
        }

        boolean empty = response.getBody().trim().isEmpty();

        if (empty != expectation.getBodyEmpty()) {
            failures.add(expectation.getBodyEmpty()
                    ? "se esperaba un cuerpo vacio pero la respuesta devuelve: " + ApiResponse.truncate(response.getBody())
                    : "se esperaba un cuerpo con datos pero la respuesta viene vacia");
        }
    }

    private static void checkBody(ApiResponse response, Expectation expectation, JsonNode context,
            List<String> failures) {
        JsonNode expected = expectation.resolveExpectedResponse(context);

        if (Json.isMissingOrNull(response.jsonOrNull())) {
            failures.add("se esperaba un cuerpo JSON para comparar con " + expectation.getResponseLabel()
                    + " pero la respuesta no tiene cuerpo JSON");
            return;
        }

        try {
            JsonAssertions.assertMatches(response.json(), expected, new JsonAssertions.MatchOptions(expectation),
                    expectation.getResponseLabel());
        } catch (AssertionError e) {
            for (String line : e.getMessage().split("\n")) {
                String trimmed = line.trim();

                if (trimmed.startsWith("- ")) {
                    failures.add(trimmed.substring(2));
                }
            }
        }
    }

    private static void checkJsonPath(ApiResponse response, Expectation.JsonPathCheck check, JsonNode context,
            List<String> failures) {
        JsonNode body = response.jsonOrNull();
        JsonNode actual = body == null ? null : JsonPath.read(body, check.getPath());
        boolean missing = Json.isMissingOrNull(actual);

        JsonNode expected = check.getValue() == null ? null : Placeholders.interpolate(check.getValue(), context);
        List<String> problems = evaluate(check, actual, missing, expected);

        if (!problems.isEmpty()) {
            failures.add(check.label() + " -> " + problems.get(0));
        }
    }

    private static List<String> evaluate(Expectation.JsonPathCheck check, JsonNode actual, boolean missing,
            JsonNode expected) {
        List<String> problems = new ArrayList<String>();
        String operator = check.getOperator();
        boolean ignoreCase = check.isIgnoreCase();

        boolean needsValue = !(check.is(Expectation.JsonPathCheck.EXISTS)
                || check.is(Expectation.JsonPathCheck.NOT_EXISTS)
                || check.is(Expectation.JsonPathCheck.NOT_NULL)
                || check.is(Expectation.JsonPathCheck.NOT_EMPTY));

        if (needsValue && Json.isMissingOrNull(expected)) {
            problems.add("el operador '" + operator + "' necesita un 'value' en el spec");
            return problems;
        }

        if (check.is(Expectation.JsonPathCheck.EXISTS)) {
            if (missing) {
                problems.add("la ruta no existe en la respuesta");
            }
            return problems;
        }

        if (check.is(Expectation.JsonPathCheck.NOT_EXISTS)) {
            if (!missing) {
                problems.add("la ruta existe y contiene " + Json.describe(actual));
            }
            return problems;
        }

        if (missing) {
            problems.add("la ruta " + check.getPath() + " no existe en la respuesta");
            return problems;
        }

        if (check.is(Expectation.JsonPathCheck.NOT_NULL)) {
            if (actual.isNull()) {
                problems.add("el valor es null");
            }
            return problems;
        }

        if (check.is(Expectation.JsonPathCheck.NOT_EMPTY)) {
            if (actual.isNull() || actual.isEmpty() || (actual.isTextual() && actual.textValue().trim().isEmpty())) {
                problems.add("el valor esta vacio");
            }
            return problems;
        }

        if (check.is(Expectation.JsonPathCheck.SIZE)) {
            int actualSize = actual.isArray() || actual.isObject() ? actual.size() : -1;

            if (actualSize != expected.asInt()) {
                problems.add("se esperaban " + expected.asInt() + " elementos y hay " + actualSize);
            }
            return problems;
        }

        if (check.is(Expectation.JsonPathCheck.IN)) {
            if (!expected.isArray() || !contains(expected, actual, ignoreCase)) {
                problems.add("el valor " + Json.describe(actual) + " no esta en " + Json.write(expected));
            }
            return problems;
        }

        if (check.is(Expectation.JsonPathCheck.CONTAINS)) {
            String text = actual.isTextual() ? actual.textValue() : Json.write(actual);

            if (!containsText(text, expected.asText(), ignoreCase)) {
                problems.add("el valor " + Json.describe(actual) + " no contiene '" + expected.asText() + "'");
            }
            return problems;
        }

        if (check.is(Expectation.JsonPathCheck.NOT_CONTAINS)) {
            String text = actual.isTextual() ? actual.textValue() : Json.write(actual);

            if (containsText(text, expected.asText(), ignoreCase)) {
                problems.add("el valor " + Json.describe(actual) + " contiene '" + expected.asText() + "' y no deberia");
            }
            return problems;
        }

        if (check.is(Expectation.JsonPathCheck.MATCHES)) {
            if (!actual.isTextual() || !Pattern.matches(expected.asText(), actual.textValue())) {
                problems.add("el valor " + Json.describe(actual) + " no encaja con /" + expected.asText() + "/");
            }
            return problems;
        }

        if (check.is(Expectation.JsonPathCheck.NOT_EQUALS)) {
            if (equal(actual, expected, ignoreCase)) {
                problems.add("el valor no deberia ser " + Json.describe(expected));
            }
            return problems;
        }

        if (check.is(Expectation.JsonPathCheck.GREATER_THAN)
                || check.is(Expectation.JsonPathCheck.GREATER_THAN_OR_EQUAL)
                || check.is(Expectation.JsonPathCheck.LESS_THAN)
                || check.is(Expectation.JsonPathCheck.LESS_THAN_OR_EQUAL)) {
            Integer comparison = compareNumbers(actual, expected);

            if (comparison == null) {
                problems.add("no se puede comparar " + Json.describe(actual) + " con " + Json.describe(expected));
                return problems;
            }

            int value = comparison.intValue();
            boolean ok;
            if (check.is(Expectation.JsonPathCheck.GREATER_THAN)) {
                ok = value > 0;
            } else if (check.is(Expectation.JsonPathCheck.GREATER_THAN_OR_EQUAL)) {
                ok = value >= 0;
            } else if (check.is(Expectation.JsonPathCheck.LESS_THAN)) {
                ok = value < 0;
            } else {
                ok = value <= 0;
            }

            if (!ok) {
                problems.add("se esperaba " + operator + " " + Json.describe(expected) + " pero es "
                        + Json.describe(actual));
            }
            return problems;
        }

        if (!equal(actual, expected, ignoreCase)) {
            problems.add("se esperaba " + Json.describe(expected) + " pero es " + Json.describe(actual));
        }

        return problems;
    }

    private static boolean contains(JsonNode array, JsonNode value, boolean ignoreCase) {
        for (JsonNode item : array) {
            if (equal(item, value, ignoreCase)) {
                return true;
            }
        }

        return false;
    }

    private static boolean containsText(String text, String fragment, boolean ignoreCase) {
        return ignoreCase
                ? text.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT))
                : text.contains(fragment);
    }

    private static boolean equal(JsonNode actual, JsonNode expected, boolean ignoreCase) {
        if (actual == null || expected == null) {
            return false;
        }

        if (actual.isNumber() && expected.isNumber()) {
            return actual.decimalValue().compareTo(expected.decimalValue()) == 0;
        }

        if (actual.isTextual() && expected.isTextual()) {
            return ignoreCase
                    ? actual.textValue().equalsIgnoreCase(expected.textValue())
                    : actual.textValue().equals(expected.textValue());
        }

        return actual.equals(expected);
    }

    private static Integer compareNumbers(JsonNode actual, JsonNode expected) {
        if (!actual.isNumber() || !expected.isNumber()) {
            return null;
        }

        return actual.decimalValue().compareTo(expected.decimalValue());
    }

    private static String prettyOrRaw(ApiResponse response) {
        JsonNode json = response.jsonOrNull();

        return json == null ? "<sin cuerpo JSON>" : Json.pretty(json);
    }

    public static void assertStatus(ApiResponse response, int... expected) {
        for (int status : expected) {
            if (response.getStatusCode() == status) {
                return;
            }
        }

        throw new AssertionError("Se esperaba un status " + java.util.Arrays.toString(expected) + " en "
                + response.describe() + ".\n\n" + ApiResponse.truncate(response.getBody()));
    }

    public static JsonNode assertJsonPathExists(ApiResponse response, String path) {
        JsonNode value = JsonPath.read(response.jsonOrNull(), path);

        if (Json.isMissingOrNull(value)) {
            throw new AssertionError("La ruta " + path + " no existe en la respuesta de " + response.describe()
                    + ".\n\n" + Json.pretty(response.jsonOrNull()));
        }

        return value;
    }

    public static void assertJsonPathEquals(ApiResponse response, String path, String expected) {
        JsonNode value = assertJsonPathExists(response, path);

        if (!value.isTextual() || !value.textValue().equals(expected)) {
            throw new AssertionError("La ruta " + path + " se esperaba '" + expected + "' pero es "
                    + Json.describe(value) + " en " + response.describe() + ".");
        }
    }

    public static void assertJsonPathContains(ApiResponse response, String path, String fragment) {
        JsonNode value = assertJsonPathExists(response, path);
        String text = value.isTextual() ? value.textValue() : Json.write(value);

        if (!text.contains(fragment)) {
            throw new AssertionError("La ruta " + path + " deberia contener '" + fragment + "' pero es '" + text
                    + "' en " + response.describe() + ".");
        }
    }
}
