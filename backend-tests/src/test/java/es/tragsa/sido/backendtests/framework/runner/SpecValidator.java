package es.tragsa.sido.backendtests.framework.runner;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Valida un spec contra {@code schemas/api-feature.schema.json} antes de ejecutarlo, para que
 * un campo mal escrito o un tipo incorrecto fallen en el arranque y no a mitad de una peticion.
 */
public final class SpecValidator {

    public static final String SCHEMA_RESOURCE = "schemas/api-feature.schema.json";

    private static final JsonSchema SCHEMA = loadSchema();

    private SpecValidator() {
    }

    public static void validate(String resourcePath, JsonNode spec) {
        Set<ValidationMessage> messages = SCHEMA.validate(spec);

        if (messages.isEmpty()) {
            return;
        }

        List<String> errors = new ArrayList<String>(new TreeSet<String>(toStrings(messages)));

        StringBuilder message = new StringBuilder();
        message.append("El spec '").append(resourcePath).append("' no cumple el esquema ")
                .append(SCHEMA_RESOURCE).append(':');

        for (String error : errors) {
            message.append("\n  - ").append(error);
        }

        throw new IllegalStateException(message.toString());
    }

    private static Set<String> toStrings(Set<ValidationMessage> messages) {
        Set<String> texts = new TreeSet<String>();

        for (ValidationMessage message : messages) {
            texts.add(message.getMessage());
        }

        return Collections.unmodifiableSet(texts);
    }

    private static JsonSchema loadSchema() {
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(SCHEMA_RESOURCE);

        if (stream == null) {
            throw new IllegalStateException("No se encuentra el esquema del spec '" + SCHEMA_RESOURCE
                    + "' en src/test/resources.");
        }

        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(stream);
    }
}
