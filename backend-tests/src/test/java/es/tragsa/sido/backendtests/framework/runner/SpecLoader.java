package es.tragsa.sido.backendtests.framework.runner;

import com.fasterxml.jackson.databind.JsonNode;
import es.tragsa.sido.backendtests.framework.model.SpecFeature;
import es.tragsa.sido.backendtests.framework.support.JsonLoader;

import java.util.ArrayList;
import java.util.List;

/**
 * Carga un {@code *.spec.json} del classpath y lo valida contra el esquema.
 */
public final class SpecLoader {

    private SpecLoader() {
    }

    public static SpecFeature load(String resourcePath) {
        JsonNode node = JsonLoader.load(resourcePath);
        SpecValidator.validate(resourcePath, node);

        return SpecFeature.parse(resourcePath, node);
    }

    public static List<SpecFeature> loadAll(List<String> resourcePaths) {
        List<SpecFeature> features = new ArrayList<SpecFeature>();

        for (String resourcePath : resourcePaths) {
            features.add(load(resourcePath));
        }

        return features;
    }
}
