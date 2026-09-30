package es.tragsa.sido.backendtests.framework.runner;

import es.tragsa.sido.backendtests.framework.config.ApiTestConfig;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

/**
 * Clase base de los tests de API. Cada test concreto solo declara que spec ejecuta, tal y como los
 * {@code tests/*.spec.js} de PlaywrightSDD registran su feature en una linea.
 *
 * <pre>
 * &#64;Tag("vehiculos")
 * class VehiculosApiTest extends AbstractApiSpecTest {
 *     &#64;Override protected String specResource() {
 *         return "specs/vehiculos/vehiculos.spec.json";
 *     }
 * }
 * </pre>
 *
 * <p>No lleva un {@code @AfterAll} que cierre Unirest: el cierre lo hace el shutdown hook que
 * registra {@link ApiTestConfig#configure()}, para que la configuracion de TLS sobreviva de una
 * clase de test a la siguiente.
 */
public abstract class AbstractApiSpecTest {

    /**
     * Ruta del spec en {@code src/test/resources}, por ejemplo {@code specs/vehiculos/vehiculos.spec.json}.
     */
    protected abstract String specResource();

    @TestFactory
    public Stream<DynamicTest> apiScenarios() {
        ApiTestConfig.configure();

        return ApiScenarioRunner.run(specResource());
    }
}
