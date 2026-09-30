package es.tragsa.sido.backendtests.framework.support;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Carga de ficheros JSON del classpath ({@code src/test/resources}).
 *
 * <p>Permite guardar los bodies de peticion y las respuestas esperadas como ficheros
 * {@code .json} independientes del spec, de forma que se puedan reutilizar y versionar.
 */
public final class JsonLoader {

    private static final Map<String, JsonNode> CACHE = new ConcurrentHashMap<String, JsonNode>();

    private JsonLoader() {
    }

    /**
     * Carga (y cachea) un JSON del classpath.
     *
     * @param resourcePath ruta relativa a {@code src/test/resources}, por ejemplo
     *                     {@code bodies/crearParteDiario.json}
     */
    public static JsonNode load(String resourcePath) {
        if (resourcePath == null) {
            return null;
        }

        String key = normalize(resourcePath);
        JsonNode cached = CACHE.get(key);

        if (cached != null) {
            return Json.deepCopy(cached);
        }

        JsonNode node = Json.parse(readContent(key));
        CACHE.put(key, node);
        return Json.deepCopy(node);
    }

    public static String readContent(String resourcePath) {
        String path = normalize(resourcePath);
        InputStream stream = open(path);

        if (stream == null) {
            throw new IllegalStateException("No se encuentra el recurso '" + path
                    + "' en el classpath. Comprueba que existe en src/test/resources/" + path + ".");
        }

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;

            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }

            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el recurso '" + path + "': " + e.getMessage(), e);
        } finally {
            close(stream);
        }
    }

    public static boolean exists(String resourcePath) {
        try {
            close(open(normalize(resourcePath)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static InputStream open(String path) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();

        if (loader == null) {
            loader = JsonLoader.class.getClassLoader();
        }

        return loader.getResourceAsStream(path);
    }

    private static String normalize(String resourcePath) {
        String path = resourcePath.trim().replace('\\', '/');

        if (path.startsWith("classpath:")) {
            path = path.substring("classpath:".length());
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        return path;
    }

    private static void close(InputStream stream) {
        if (stream == null) {
            return;
        }

        try {
            stream.close();
        } catch (IOException ignored) {
            // noop
        }
    }
}
