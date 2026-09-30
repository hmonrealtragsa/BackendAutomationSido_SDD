package es.tragsa.sido.backendtests.framework.model;

import com.fasterxml.jackson.databind.JsonNode;
import es.tragsa.sido.backendtests.framework.support.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Un fichero spec: la fuente de verdad de una feature, equivalente a los
 * {@code *.spec.json} de PlaywrightSDD.
 */
public final class SpecFeature {

    private final String resource;
    private final String feature;
    private final String description;
    private final List<Scenario> scenarios;

    private SpecFeature(String resource, String feature, String description, List<Scenario> scenarios) {
        this.resource = resource;
        this.feature = feature;
        this.description = description;
        this.scenarios = scenarios;
    }

    public static SpecFeature parse(String resource, JsonNode node) {
        String feature = Json.text(node.get("feature"), resource);
        List<Scenario> scenarios = new ArrayList<Scenario>();
        JsonNode scenariosNode = node.get("scenarios");

        if (scenariosNode != null && scenariosNode.isArray()) {
            for (JsonNode scenario : scenariosNode) {
                scenarios.add(Scenario.parse(feature, scenario));
            }
        }

        return new SpecFeature(resource, feature, Json.text(node.get("description"), null),
                Collections.unmodifiableList(scenarios));
    }

    public String getResource() {
        return resource;
    }

    public String getFeature() {
        return feature;
    }

    public String getDescription() {
        return description;
    }

    public List<Scenario> getScenarios() {
        return scenarios;
    }
}
