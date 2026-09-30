package es.tragsa.sido.backendtests.framework.clients;

import com.fasterxml.jackson.databind.JsonNode;
import es.tragsa.sido.backendtests.framework.config.ApiTestConfig;
import es.tragsa.sido.backendtests.framework.config.TestEnvironment;
import es.tragsa.sido.backendtests.framework.model.RequestDefinition;
import es.tragsa.sido.backendtests.framework.support.Json;
import kong.unirest.Header;
import kong.unirest.HttpRequest;
import kong.unirest.HttpRequestWithBody;
import kong.unirest.HttpResponse;
import kong.unirest.MultipartBody;
import kong.unirest.Unirest;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Cliente HTTP del framework. Encapsula Unirest para que los steps Java solo traten con
 * endpoints y aserciones, no con la libreria.
 *
 * <p>El runner usa {@link #call(RequestDefinition)}; los steps Java que necesiten encadenar
 * varias llamadas usan los metodos cortos ({@link #get}, {@link #postJson}, ...).
 *
 * <p>El cuerpo se envia siempre serializado con Jackson y no con el mapper de Unirest: asi el
 * JSON que sale es exactamente el que genera el spec, sin el remapeo de la libreria.
 */
public final class ApiClient {

    public ApiClient() {
        ApiTestConfig.configure();
    }

    public ApiResponse call(RequestDefinition request) {
        return call(request, null);
    }

    /**
     * Ejecuta la peticion descrita en el spec, resolviendo los marcadores con el contexto dado.
     */
    public ApiResponse call(RequestDefinition request, JsonNode context) {
        String url = request.resolveUrl(context);
        HttpRequest<?> unirestRequest = buildRequest(request, context, url);

        long started = System.nanoTime();
        HttpResponse<String> response = unirestRequest.asString();
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        return new ApiResponse(request.getMethod(), url, response.getStatus(), headersOf(response),
                response.getBody(), elapsed);
    }

    public ApiResponse get(String endpointReference) {
        return send("GET", endpointReference, null, null);
    }

    public ApiResponse get(String endpointReference, Map<String, ?> queryParams) {
        return send("GET", endpointReference, queryParams, null);
    }

    public ApiResponse postJson(String endpointReference, Object body) {
        return send("POST", endpointReference, null, body);
    }

    /**
     * Atajo para un step Java: ejecuta una llamada suelta declarada en codigo.
     */
    public ApiResponse send(String method, String endpointReference, Map<String, ?> queryParams, Object body) {
        com.fasterxml.jackson.databind.node.ObjectNode node = Json.object();
        node.put("endpoint", endpointReference);
        node.put("method", method);

        if (queryParams != null && !queryParams.isEmpty()) {
            com.fasterxml.jackson.databind.node.ObjectNode query = node.putObject("queryParams");

            for (Map.Entry<String, ?> param : queryParams.entrySet()) {
                query.set(param.getKey(), toNode(param.getValue()));
            }
        }

        if (body != null) {
            node.set("body", toNode(body));
        }

        return call(RequestDefinition.parse(node), null);
    }

    private HttpRequest<?> buildRequest(RequestDefinition request, JsonNode context, String url) {
        HttpRequestWithBody unirest = Unirest.request(request.getMethod(), url);

        unirest.headers(ApiTestConfig.authHeaders(request.getAuth()));
        applyHeaders(unirest, request.resolveHeaders(context));
        applyQueryParams(unirest, request.resolveQueryParams(context));

        int timeout = request.getTimeoutMs() > 0 ? request.getTimeoutMs() : TestEnvironment.TIMEOUT_MS;

        if (timeout > 0) {
            unirest.connectTimeout(timeout).socketTimeout(timeout);
        }

        return applyBody(unirest, request, context);
    }

    private void applyHeaders(HttpRequestWithBody unirest, JsonNode headers) {
        if (headers == null || !headers.isObject()) {
            return;
        }

        for (Iterator<String> names = headers.fieldNames(); names.hasNext();) {
            String name = names.next();
            JsonNode value = headers.get(name);

            if (value.isArray()) {
                for (JsonNode item : value) {
                    unirest.header(name, Json.describe(item));
                }
            } else {
                unirest.header(name, Json.describe(value));
            }
        }
    }

    private void applyQueryParams(HttpRequestWithBody unirest, JsonNode queryParams) {
        if (queryParams == null || !queryParams.isObject()) {
            return;
        }

        for (Iterator<String> names = queryParams.fieldNames(); names.hasNext();) {
            String name = names.next();
            JsonNode value = queryParams.get(name);

            if (value.isArray()) {
                unirest.queryString(name, toStringList(value));
            } else {
                unirest.queryString(name, value.isNull() ? "" : Json.describe(value));
            }
        }
    }

    private HttpRequest<?> applyBody(HttpRequestWithBody unirest, RequestDefinition request, JsonNode context) {
        String bodyType = request.getBodyType();
        JsonNode body = request.resolveBody(context);

        if (RequestDefinition.BODY_NONE.equals(bodyType) || body == null) {
            return unirest;
        }

        if (RequestDefinition.BODY_JSON.equals(bodyType)) {
            return unirest.contentType(contentType(request, "application/json")).body(Json.write(body));
        }

        if (RequestDefinition.BODY_TEXT.equals(bodyType)) {
            String text = body.isTextual() ? body.textValue() : Json.write(body);

            return unirest.contentType(contentType(request, "text/plain")).body(text);
        }

        if (RequestDefinition.BODY_FORM.equals(bodyType)) {
            // Unirest solo ofrece multipart para cuerpos con campos, asi que un
            // application/x-www-form-urlencoded se construye a mano como texto.
            StringBuilder form = new StringBuilder();

            if (body.isObject()) {
                for (Iterator<String> names = body.fieldNames(); names.hasNext();) {
                    String name = names.next();

                    if (form.length() > 0) {
                        form.append('&');
                    }

                    form.append(urlEncode(name)).append('=').append(urlEncode(Json.describe(body.get(name))));
                }
            }

            return unirest.contentType(contentType(request, "application/x-www-form-urlencoded"))
                    .body(form.toString());
        }

        if (RequestDefinition.BODY_MULTIPART.equals(bodyType)) {
            // El Content-Type con la boundary lo genera Unirest al enviar; si lo fijamos
            // a mano aqui la cabecera llega sin boundary y el servidor no puede parsearla.
            MultipartBody multipart = unirest.multiPartContent();

            if (request.getContentType() != null) {
                multipart.contentType(request.getContentType());
            }

            if (body.isObject()) {
                for (Iterator<String> names = body.fieldNames(); names.hasNext();) {
                    String name = names.next();
                    multipart.field(name, Json.describe(body.get(name)));
                }
            }

            return multipart;
        }

        return unirest;
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 no esta soportado en esta JVM", e);
        }
    }

    private static String contentType(RequestDefinition request, String defaultValue) {
        return request.getContentType() == null ? defaultValue : request.getContentType();
    }

    private static List<String> toStringList(JsonNode array) {
        List<String> values = new ArrayList<String>(array.size());

        for (JsonNode item : array) {
            values.add(Json.describe(item));
        }

        return values;
    }

    private static JsonNode toNode(Object value) {
        if (value instanceof JsonNode) {
            return (JsonNode) value;
        }
        if (value instanceof String) {
            return Json.text((String) value);
        }

        // valueToTree conserva el tipo numerico exacto (Integer, Long, Double, BigDecimal).
        return Json.mapper().valueToTree(value);
    }

    private static Map<String, String> headersOf(HttpResponse<String> response) {
        Map<String, String> headers = new LinkedHashMap<String, String>();

        for (Header header : response.getHeaders().all()) {
            String name = header.getName();
            String previous = headers.get(name);

            headers.put(name, previous == null ? header.getValue() : previous + ", " + header.getValue());
        }

        return headers;
    }
}
