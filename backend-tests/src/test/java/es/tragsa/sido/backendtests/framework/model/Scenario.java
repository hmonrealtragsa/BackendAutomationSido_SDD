package es.tragsa.sido.backendtests.framework.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import es.tragsa.sido.backendtests.framework.support.Json;
import es.tragsa.sido.backendtests.framework.support.Placeholders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Un escenario de un spec: la unidad minima que el runner convierte en un test.
 *
 * <p>Todo lo ejecutable vive aqui: los campos de texto ({@code name}, {@code when}, {@code and},
 * {@code then}) son documentacion funcional y el codigo esta en {@code request} y {@code expect}.
 * No hace falta escribir Java para automatizar un caso de prueba.
 *
 * <p>Un escenario con {@code foreach} se repite por cada elemento de una lista: {@link #expand()}
 * devuelve un escenario por elemento, cada uno con su elemento colgado en el contexto de
 * marcadores, y el runner los reporta como tests independientes.
 */
public final class Scenario {

    private final String feature;
    private final String name;
    private final String action;
    private final String when;
    private final List<String> and;
    private final String then;
    private final JsonNode given;
    private final JsonNode data;
    private final RequestDefinition request;
    private final Expectation expect;
    private final String foreachIn;
    private final String foreachAs;
    private final String foreachLabelBy;
    private final JsonNode item;
    private final String itemLabel;
    private final JsonNode raw;
    private final RuntimeException failure;
    private final String idRunner;

    private Scenario(String feature, String name, String action, String when, List<String> and, String then,
            JsonNode given, JsonNode data, RequestDefinition request, Expectation expect, JsonNode raw,
            String foreachIn, String foreachAs, String foreachLabelBy, JsonNode item, String itemLabel,
            RuntimeException failure) {
        this.feature = feature;
        this.name = name;
        this.action = action;
        this.when = when;
        this.and = and;
        this.then = then;
        this.given = given;
        this.data = data;
        this.request = request;
        this.expect = expect;
        this.raw = raw;
        this.foreachIn = foreachIn;
        this.foreachAs = foreachAs;
        this.foreachLabelBy = foreachLabelBy;
        this.item = item;
        this.itemLabel = itemLabel;
        this.failure = failure;
        this.idRunner = raw == null ? "" : Json.text(raw.get("idRunner"), "");
    }

    public static Scenario parse(String feature, JsonNode node) {
        String name = Json.text(node.get("name"), "");
        String action = Json.text(node.get("action"), "");
        String when = Json.text(node.get("when"), null);
        String then = Json.text(node.get("then"), null);
        List<String> and = new ArrayList<String>();

        JsonNode andNode = node.get("and");
        if (andNode != null && andNode.isArray()) {
            for (JsonNode item : andNode) {
                and.add(item.asText());
            }
        }

        JsonNode foreachNode = node.get("foreach");
        String foreachIn = null;
        String foreachAs = null;
        String foreachLabelBy = null;

        if (foreachNode != null && foreachNode.isObject()) {
            foreachIn = Json.text(foreachNode.get("in"), null);
            foreachAs = Json.text(foreachNode.get("as"), null);
            foreachLabelBy = Json.text(foreachNode.get("labelBy"), null);
        }

        return new Scenario(
                feature,
                name,
                action,
                when,
                Collections.unmodifiableList(and),
                then,
                node.get("given") == null ? Json.object() : node.get("given"),
                node.get("data") == null ? Json.object() : node.get("data"),
                node.get("request") == null || node.get("request").isNull()
                        ? null
                        : RequestDefinition.parse(node.get("request")),
                node.get("expect") == null || node.get("expect").isNull() ? null : Expectation.parse(node.get("expect")),
                Json.deepCopy(node),
                foreachIn,
                foreachAs,
                foreachLabelBy,
                null,
                null,
                null);
    }

    /**
     * Copia del escenario que solo falla al ejecutarse. Lo usa el runner para que un {@code foreach}
     * mal escrito aparezca como un test rojo, y no como un error que tumba la clase entera.
     */
    public Scenario failing(RuntimeException error) {
        return new Scenario(feature, name, action, when, and, then, given, data, request, expect, raw,
                foreachIn, foreachAs, foreachLabelBy, item, itemLabel, error);
    }

    /**
     * Error pendiente de lanzar, o null si el escenario se puede ejecutar.
     */
    public RuntimeException getFailure() {
        return failure;
    }

    public String getFeature() {
        return feature;
    }

    public String getName() {
        return name;
    }

    public String getAction() {
        return action;
    }

    /**
     * Identificador del caso de prueba, declarado en el spec como {@code idRunner}. Es la clave
     * estable que se pasa a Maven para lanzar un unico escenario:
     * {@code mvn test "-Dsido.backend.scenario=CP_02"}.
     *
     * <p>Se deduce de {@code raw} en vez de guardarse aparte para no duplicar el campo en el
     * constructor, que ya tiene demasiados parametros y se copia entero en cada expansion de
     * {@code foreach}.
     */
    public String getIdRunner() {
        return idRunner;
    }

    /**
     * Como se identifica el escenario en los mensajes: el {@code idRunner} si lo declara, y el
     * nombre funcional si no. Acorta el diagnostico y, sobre todo, deja el id en el informe, que
     * es donde se busca cuando algo falla.
     */
    public String label() {
        return idRunner.isEmpty() ? name : "[" + idRunner + "] " + name;
    }

    public String getWhen() {
        return when;
    }

    public List<String> getAnd() {
        return and;
    }

    public String getThen() {
        return then;
    }

    public JsonNode getGiven() {
        return given;
    }

    public JsonNode getData() {
        return data;
    }

    public RequestDefinition getRequest() {
        return request;
    }

    public Expectation getExpect() {
        return expect;
    }

    public JsonNode getRaw() {
        return raw;
    }

    public boolean hasForeach() {
        return foreachIn != null;
    }

    /**
     * Un escenario por cada elemento de la lista del {@code foreach}. Sin {@code foreach} devuelve
     * el propio escenario, de modo que el runner no tiene que distinguir los dos casos.
     *
     * @throws IllegalArgumentException si la lista no existe, no es un array o viene vacia
     */
    public List<Scenario> expand() {
        if (!hasForeach()) {
            return Collections.singletonList(this);
        }

        JsonNode source = foreachSource();
        List<Scenario> expanded = new ArrayList<Scenario>(source.size());
        int position = 0;

        for (JsonNode element : source) {
            expanded.add(withItem(element, position));
            position++;
        }

        if (expanded.isEmpty()) {
            throw new IllegalArgumentException("El 'foreach.in' del escenario '" + name + "' apunta a '"
                    + foreachIn + "', que es una lista vacia. Un foreach sin elementos no comprueba nada.");
        }

        return expanded;
    }

    /**
     * Contexto para los marcadores {@code ${data.campo}}: primero {@code data} y encima
     * {@code given}, de forma que cualquiera de los dos alimenta al body. Si el escenario viene de
     * un {@code foreach}, el elemento se cuelga en la raiz con el nombre de {@code foreach.as}.
     */
    public JsonNode dataContext() {
        ObjectNode context = Json.object();
        context.set("data", mergedData());
        context.set("given", given == null ? Json.object() : given);

        if (item != null && foreachAs != null) {
            context.set(foreachAs, item);
        }

        return context;
    }

    public JsonNode mergedData() {
        ObjectNode merged = Json.object();

        if (given != null && given.isObject()) {
            copyFields(given, merged);
        }
        if (data != null && data.isObject()) {
            copyFields(data, merged);
        }

        return merged;
    }

    /**
     * Valor de dato del escenario por ruta con puntos, por ejemplo {@code usuario.id}.
     */
    public JsonNode resolveData(String path) {
        return Json.at(mergedData(), path);
    }

    public String resolveDataText(String path, String defaultValue) {
        return Json.text(resolveData(path), defaultValue);
    }

    public String displayName() {
        return itemLabel == null
                ? feature + " - " + name
                : feature + " - " + name + " [" + itemLabel + "]";
    }

    /**
     * La lista que recorre el {@code foreach}. Admite las dos formas de escribirla: como marcador
     * ({@code ${data.vehiculos}}) o como ruta en el contexto ({@code data.vehiculos}).
     */
    private JsonNode foreachSource() {
        if (foreachAs == null || foreachAs.trim().isEmpty()) {
            throw new IllegalArgumentException("El escenario '" + name + "' declara 'foreach' pero no dice 'as', "
                    + "que es el nombre con el que el elemento aparece en los marcadores "
                    + "(por ejemplo \"as\": \"vehiculo\" para poder escribir ${vehiculo.matricula}).");
        }

        JsonNode context = dataContext();
        JsonNode source = Placeholders.resolve(foreachIn, context);

        if (Json.isMissingOrNull(source)) {
            throw new IllegalArgumentException("El 'foreach.in' del escenario '" + name + "' apunta a '"
                    + foreachIn + "', que no existe. Revisa el bloque 'data' del spec.");
        }

        if (!source.isArray()) {
            throw new IllegalArgumentException("El 'foreach.in' del escenario '" + name + "' apunta a '"
                    + foreachIn + "', que es " + Json.describe(source) + " y no una lista. "
                    + "'foreach' solo recorre arrays.");
        }

        return source;
    }

    private Scenario withItem(JsonNode element, int position) {
        return new Scenario(feature, name, action, when, and, then, given, data, request, expect, raw,
                foreachIn, foreachAs, foreachLabelBy, element, labelOf(element, position), null);
    }

    /**
     * Texto que identifica al elemento en el nombre del test. Con {@code labelBy} se indica el
     * campo; si no, un objeto de un solo campo se resume como {@code campo=valor} y cualquier
     * otro caso cae en el JSON compacto.
     */
    private String labelOf(JsonNode element, int position) {
        if (foreachLabelBy != null) {
            JsonNode value = Json.at(element, foreachLabelBy);

            if (Json.isMissingOrNull(value)) {
                throw new IllegalArgumentException("El 'foreach.labelBy' del escenario '" + name + "' apunta a '"
                        + foreachLabelBy + "', que no existe en el elemento " + Json.write(element) + ".");
            }

            return Json.describe(value);
        }

        if (element.isObject() && element.size() == 1) {
            String field = element.fieldNames().next();

            return field + "=" + Json.describe(element.get(field));
        }

        if (element.isContainerNode()) {
            return Json.write(element);
        }

        return Json.describe(element) + " (posicion " + position + ")";
    }

    private static void copyFields(JsonNode source, ObjectNode target) {
        for (Iterator<String> fields = source.fieldNames(); fields.hasNext();) {
            String field = fields.next();
            target.set(field, source.get(field));
        }
    }
}
