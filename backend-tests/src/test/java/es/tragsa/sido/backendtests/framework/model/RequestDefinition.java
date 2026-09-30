package es.tragsa.sido.backendtests.framework.model;

import com.fasterxml.jackson.databind.JsonNode;
import es.tragsa.sido.backendtests.framework.config.Endpoints;
import es.tragsa.sido.backendtests.framework.support.Json;
import es.tragsa.sido.backendtests.framework.support.JsonLoader;
import es.tragsa.sido.backendtests.framework.support.Placeholders;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Peticion HTTP descrita en el spec.
 *
 * <p>{@code body} admite tres formas:
 * <ul>
 *   <li>texto: ruta de un JSON del classpath, por ejemplo {@code bodies/crearParteDiario.json};</li>
 *   <li>objeto: body inline en el propio spec;</li>
 *   <li>null: peticion sin body.</li>
 * </ul>
 *
 * <p>Si el body viene de un fichero, se interpola con los marcadores del escenario, de modo
 * que el mismo fichero sirve para varios casos configurando solo {@code data}.
 */
public final class RequestDefinition {

    public static final String BODY_JSON = "json";
    public static final String BODY_TEXT = "text";
    public static final String BODY_FORM = "form";
    public static final String BODY_MULTIPART = "multipart";
    public static final String BODY_NONE = "none";

    public static final String AUTH_DEFAULT = "default";
    public static final String AUTH_NONE = "none";
    public static final String AUTH_BEARER = "bearer";

    private final String endpointReference;
    private final String method;
    private final JsonNode pathParams;
    private final JsonNode queryParams;
    private final JsonNode headers;
    private final JsonNode body;
    private final String bodyType;
    private final String contentType;
    private final int timeoutMs;
    private final String auth;

    private RequestDefinition(String endpointReference, String method, JsonNode pathParams, JsonNode queryParams,
            JsonNode headers, JsonNode body, String bodyType, String contentType, int timeoutMs, String auth) {
        this.endpointReference = endpointReference;
        this.method = method;
        this.pathParams = pathParams;
        this.queryParams = queryParams;
        this.headers = headers;
        this.body = body;
        this.bodyType = bodyType;
        this.contentType = contentType;
        this.timeoutMs = timeoutMs;
        this.auth = auth;
    }

    public static RequestDefinition parse(JsonNode node) {
        JsonNode body = node.get("body");

        return new RequestDefinition(
                Json.text(node.get("endpoint"), null),
                Json.text(node.get("method"), "GET").toUpperCase(Locale.ROOT),
                objectOrEmpty(node.get("pathParams")),
                objectOrEmpty(node.get("queryParams")),
                objectOrEmpty(node.get("headers")),
                body,
                Json.text(node.get("bodyType"), defaultBodyType(body)),
                Json.text(node.get("contentType"), null),
                node.has("timeoutMs") ? node.get("timeoutMs").asInt() : 0,
                Json.text(node.get("auth"), AUTH_DEFAULT));
    }

    public String getEndpointReference() {
        return endpointReference;
    }

    public String getMethod() {
        return method;
    }

    public JsonNode getPathParams() {
        return pathParams;
    }

    public JsonNode getQueryParams() {
        return queryParams;
    }

    public JsonNode getHeaders() {
        return headers;
    }

    public String getBodyType() {
        return bodyType;
    }

    public String getContentType() {
        return contentType;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public String getAuth() {
        return auth;
    }

    /**
     * URL final con los path params ya sustituidos.
     */
    public String resolveUrl(JsonNode context) {
        String url = Endpoints.url(endpointReference);

        for (Iterator<Map.Entry<String, JsonNode>> params = pathParams.fields(); params.hasNext();) {
            Map.Entry<String, JsonNode> param = params.next();
            url = url.replace("{" + param.getKey() + "}", Json.describe(param.getValue()));
        }

        return Placeholders.resolveText(url, context);
    }

    /**
     * Descripcion corta para logs y mensajes de error.
     */
    public String describe() {
        return method + " " + resolveUrl(null);
    }

    /**
     * Body final.
     *
     * <p>Si el body viene de un fichero del classpath se interpola el texto antes de
     * interpretarlo, de modo que {@code "km": ${data.km}} puede quedar numerico. Si viene
     * inline en el spec, se interpola sobre el arbol JSON.
     */
    public JsonNode resolveBody(JsonNode context) {
        if (body == null || body.isNull()) {
            return null;
        }

        if (body.isTextual()) {
            String raw = JsonLoader.readContent(body.textValue());

            return Json.parse(Placeholders.resolveText(raw, context));
        }

        return Placeholders.interpolate(body, context);
    }

    public JsonNode resolvePathParams(JsonNode context) {
        return Placeholders.interpolate(pathParams, context);
    }

    public JsonNode resolveQueryParams(JsonNode context) {
        return Placeholders.interpolate(queryParams, context);
    }

    public JsonNode resolveHeaders(JsonNode context) {
        return Placeholders.interpolate(headers, context);
    }

    private static String defaultBodyType(JsonNode body) {
        if (body == null || body.isNull()) {
            return BODY_NONE;
        }
        return BODY_JSON;
    }

    private static JsonNode objectOrEmpty(JsonNode node) {
        return node == null || !node.isObject() ? Json.object() : node;
    }
}
