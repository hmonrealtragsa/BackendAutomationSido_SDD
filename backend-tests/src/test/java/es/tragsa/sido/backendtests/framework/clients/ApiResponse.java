package es.tragsa.sido.backendtests.framework.clients;

import com.fasterxml.jackson.databind.JsonNode;
import es.tragsa.sido.backendtests.framework.support.Json;

import java.util.Map;

/**
 * Respuesta de una llamada al microservicio, con los metodos que usan las aserciones.
 */
public final class ApiResponse {

    private final String method;
    private final String url;
    private final int statusCode;
    private final Map<String, String> headers;
    private final String body;
    private final long elapsedMs;
    private final JsonNode json;

    public ApiResponse(String method, String url, int statusCode, Map<String, String> headers, String body,
            long elapsedMs) {
        this.method = method;
        this.url = url;
        this.statusCode = statusCode;
        this.headers = headers;
        this.body = body == null ? "" : body;
        this.elapsedMs = elapsedMs;
        this.json = null;
    }

    public String getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getBody() {
        return body;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Cabecera de la respuesta. El nombre no distingue mayusculas, como en HTTP.
     */
    public String header(String name) {
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (header.getKey() != null && header.getKey().equalsIgnoreCase(name)) {
                return header.getValue();
            }
        }

        return null;
    }

    /**
     * Cuerpo parseado como JSON. Lanza si el cuerpo esta vacio o no es JSON.
     */
    public JsonNode json() {
        if (body.trim().isEmpty()) {
            throw new AssertionError("Se esperaba un cuerpo JSON en la respuesta de " + describe()
                    + " pero el cuerpo esta vacio.");
        }

        try {
            return Json.parse(body);
        } catch (RuntimeException e) {
            throw new AssertionError("El cuerpo de la respuesta de " + describe() + " no es JSON valido.\n\n"
                    + truncate(body) + "\n\n" + e.getMessage(), e);
        }
    }

    /**
     * Cuerpo parseado, o null si esta vacio o no es JSON.
     */
    public JsonNode jsonOrNull() {
        try {
            return json();
        } catch (AssertionError e) {
            return null;
        }
    }

    public String describe() {
        return method + " " + url + " -> " + statusCode;
    }

    public String summary() {
        return describe() + " (" + elapsedMs + " ms)\n" + truncate(body);
    }

    public static String truncate(String value) {
        int limit = 4000;

        return value.length() <= limit ? value : value.substring(0, limit) + "\n... [" + (value.length() - limit)
                + " caracteres mas]";
    }
}
