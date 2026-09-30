package es.tragsa.sido.backendtests.framework.model;

import com.fasterxml.jackson.databind.JsonNode;
import es.tragsa.sido.backendtests.framework.support.Json;
import es.tragsa.sido.backendtests.framework.support.JsonLoader;
import es.tragsa.sido.backendtests.framework.support.Placeholders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Comprobaciones esperadas sobre la respuesta, tal y como las declara el spec.
 *
* <p>{@code status} admite {@code 200}, {@code "2xx"} o {@code [200, 204]}.
 * {@code response} admite las mismas dos formas que {@code request.body}: una ruta de un JSON
 * del classpath (por ejemplo {@code responses/vehiculos/parteDiario.json}) o el objeto
 * declarado en linea. Se compara segun {@code matchMode}: {@code partial} exige que la
 * respuesta contenga todos los datos del JSON esperado (sobran mas campos), {@code exact}
 * exige igualdad exacta.
 */
public final class Expectation {

    public static final String MATCH_PARTIAL = "partial";
    public static final String MATCH_EXACT = "exact";

    public static final String ARRAY_INDEX = "index";
    public static final String ARRAY_BY_KEY = "byKey";
    public static final String ARRAY_CONTAINS = "contains";

    private final JsonNode status;
    private final String contentType;
    private final Map<String, Header> headers;
    private final JsonNode response;
    private final String matchMode;
    private final List<String> ignorePaths;
    private final String arrayMatch;
    private final String arrayKey;
    private final List<JsonPathCheck> jsonPaths;
    private final Boolean bodyEmpty;
    private final Integer responseTimeMs;

    private Expectation(JsonNode status, String contentType, Map<String, Header> headers, JsonNode response,
            String matchMode, List<String> ignorePaths, String arrayMatch, String arrayKey,
            List<JsonPathCheck> jsonPaths, Boolean bodyEmpty, Integer responseTimeMs) {
        this.status = status;
        this.contentType = contentType;
        this.headers = headers;
        this.response = response;
        this.matchMode = matchMode;
        this.ignorePaths = ignorePaths;
        this.arrayMatch = arrayMatch;
        this.arrayKey = arrayKey;
        this.jsonPaths = jsonPaths;
        this.bodyEmpty = bodyEmpty;
        this.responseTimeMs = responseTimeMs;
    }

    public static Expectation parse(JsonNode node) {
        JsonNode status = node.get("status");
        JsonNode response = node.get("response");
        JsonNode ignorePaths = node.get("ignorePaths");
        List<String> ignore = new ArrayList<String>();

        if (ignorePaths != null && ignorePaths.isArray()) {
            for (JsonNode item : ignorePaths) {
                ignore.add(item.asText());
            }
        }

        Map<String, Header> headers = new LinkedHashMap<String, Header>();
        JsonNode headersNode = node.get("headers");

        if (headersNode != null && headersNode.isObject()) {
            for (java.util.Iterator<String> names = headersNode.fieldNames(); names.hasNext();) {
                String name = names.next();
                headers.put(name, Header.parse(headersNode.get(name)));
            }
        }

        List<JsonPathCheck> jsonPaths = new ArrayList<JsonPathCheck>();
        JsonNode jsonPathsNode = node.get("jsonPaths");

        if (jsonPathsNode != null && jsonPathsNode.isArray()) {
            for (JsonNode item : jsonPathsNode) {
                jsonPaths.add(JsonPathCheck.parse(item));
            }
        }

        return new Expectation(
                status,
                Json.text(node.get("contentType"), null),
                Collections.unmodifiableMap(headers),
                response,
                Json.text(node.get("matchMode"), MATCH_PARTIAL).toLowerCase(Locale.ROOT),
                Collections.unmodifiableList(ignore),
                Json.text(node.get("arrayMatch"), ARRAY_INDEX).toLowerCase(Locale.ROOT),
                Json.text(node.get("arrayKey"), null),
                Collections.unmodifiableList(jsonPaths),
                node.has("bodyEmpty") ? node.get("bodyEmpty").asBoolean() : null,
                node.has("responseTimeMs") ? node.get("responseTimeMs").asInt() : null);
    }

    public JsonNode getStatus() {
        return status;
    }

    public String getContentType() {
        return contentType;
    }

    public Map<String, Header> getHeaders() {
        return headers;
    }

    /**
     * Nodo declarado en {@code expect.response}: un texto con la ruta del JSON del classpath o
     * el objeto esperado en linea. Null o absent si el escenario no comprueba el cuerpo.
     */
    public JsonNode getResponse() {
        return response;
    }

    public boolean hasResponse() {
        return !Json.isMissingOrNull(response);
    }

    /**
     * Como se nombra el cuerpo esperado en los mensajes de fallo: la ruta del fichero cuando
     * viene referenciado, o una descripcion generica cuando va en linea.
     */
    public String getResponseLabel() {
        if (!hasResponse()) {
            return "<sin response esperado>";
        }

        return response.isTextual()
                ? response.textValue()
                : "el 'response' declarado en linea en el spec";
    }

    public String getMatchMode() {
        return matchMode;
    }

    public List<String> getIgnorePaths() {
        return ignorePaths;
    }

    public String getArrayMatch() {
        return arrayMatch;
    }

    public String getArrayKey() {
        return arrayKey;
    }

    public List<JsonPathCheck> getJsonPaths() {
        return jsonPaths;
    }

    public Boolean getBodyEmpty() {
        return bodyEmpty;
    }

    public Integer getResponseTimeMs() {
        return responseTimeMs;
    }

    /**
     * JSON esperado, con los marcadores del escenario ya sustituidos.
     *
     * <p>Si el {@code response} viene de un fichero se interpola el texto antes de
     * interpretarlo, para poder dejar marcadores sin comillas cuando el valor esperado es
     * numerico. Si viene en linea en el spec, se interpola sobre el arbol JSON.
     */
    public JsonNode resolveExpectedResponse(JsonNode context) {
        if (!hasResponse()) {
            return null;
        }

        if (response.isTextual()) {
            String raw = JsonLoader.readContent(response.textValue());

            return Json.parse(Placeholders.resolveText(raw, context));
        }

        return Placeholders.interpolate(response, context);
    }

    /**
     * Comprobacion de una cabecera de la respuesta.
     */
    public static final class Header {

        private final String equalsValue;
        private final String containsValue;
        private final String matches;
        private final Boolean exists;

        private Header(String equalsValue, String containsValue, String matches, Boolean exists) {
            this.equalsValue = equalsValue;
            this.containsValue = containsValue;
            this.matches = matches;
            this.exists = exists;
        }

        static Header parse(JsonNode node) {
            if (node == null || node.isNull()) {
                return new Header(null, null, null, Boolean.TRUE);
            }

            if (node.isBoolean()) {
                return new Header(null, null, null, node.asBoolean());
            }

            if (node.isTextual()) {
                return new Header(node.textValue(), null, null, Boolean.TRUE);
            }

            return new Header(
                    Json.text(node.get("equals"), null),
                    Json.text(node.get("contains"), null),
                    Json.text(node.get("matches"), null),
                    node.has("exists") ? node.get("exists").asBoolean() : Boolean.TRUE);
        }

        public String getEqualsValue() {
            return equalsValue;
        }

        public String getContainsValue() {
            return containsValue;
        }

        public String getMatches() {
            return matches;
        }

        public Boolean getExists() {
            return exists;
        }
    }

    /**
     * Comprobacion sobre una ruta de la respuesta ({@code $.data[0].id}).
     */
    public static final class JsonPathCheck {

        public static final String EQUALS = "equals";
        public static final String NOT_EQUALS = "notEquals";
        public static final String CONTAINS = "contains";
        public static final String NOT_CONTAINS = "notContains";
        public static final String MATCHES = "matches";
        public static final String IN = "in";
        public static final String EXISTS = "exists";
        public static final String NOT_EXISTS = "notExists";
        public static final String NOT_NULL = "notNull";
        public static final String NOT_EMPTY = "notEmpty";
        public static final String GREATER_THAN = "greaterThan";
        public static final String GREATER_THAN_OR_EQUAL = "greaterThanOrEqual";
        public static final String LESS_THAN = "lessThan";
        public static final String LESS_THAN_OR_EQUAL = "lessThanOrEqual";
        public static final String SIZE = "size";

        private final String path;
        private final String operator;
        private final JsonNode value;
        private final boolean ignoreCase;
        private final String description;

        private JsonPathCheck(String path, String operator, JsonNode value, boolean ignoreCase, String description) {
            this.path = path;
            this.operator = operator;
            this.value = value;
            this.ignoreCase = ignoreCase;
            this.description = description;
        }

        static JsonPathCheck parse(JsonNode node) {
            return new JsonPathCheck(
                    Json.text(node.get("path"), null),
                    Json.text(node.get("operator"), EQUALS).toLowerCase(Locale.ROOT),
                    node.get("value"),
                    node.has("ignoreCase") && node.get("ignoreCase").asBoolean(),
                    Json.text(node.get("description"), null));
        }

        public String getPath() {
            return path;
        }

        public String getOperator() {
            return operator;
        }

        public JsonNode getValue() {
            return value;
        }

        public boolean isIgnoreCase() {
            return ignoreCase;
        }

        public String getDescription() {
            return description;
        }

        public String label() {
            return description == null ? path + " (" + operator + ")" : description;
        }

        /**
         * Compara el operador del spec con una de estas constantes sin distinguir mayusculas, para
         * que {@code "notNull"} y {@code "notnull"} sean el mismo operador.
         */
        public boolean is(String constant) {
            return constant.equalsIgnoreCase(operator);
        }
    }
}
