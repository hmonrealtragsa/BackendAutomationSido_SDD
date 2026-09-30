package es.tragsa.sido.backendtests.features.vehiculos;

import es.tragsa.sido.backendtests.framework.runner.AbstractApiSpecTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;

/**
 * Todos los escenarios de vehiculos. Salen de {@code specs/vehiculos/vehiculos.spec.json}; no hay
 * codigo Java, todo esta en el spec.
 */
@Tag("vehiculos")
@DisplayName("Vehiculos SIDO2")
class VehiculosApiTest extends AbstractApiSpecTest {

    @Override
    protected String specResource() {
        return "specs/vehiculos/vehiculos.spec.json";
    }
}
