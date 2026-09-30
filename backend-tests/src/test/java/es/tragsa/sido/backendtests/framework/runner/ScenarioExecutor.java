package es.tragsa.sido.backendtests.framework.runner;

import com.fasterxml.jackson.databind.JsonNode;
import es.tragsa.sido.backendtests.framework.assertions.ResponseAssertions;
import es.tragsa.sido.backendtests.framework.clients.ApiClient;
import es.tragsa.sido.backendtests.framework.clients.ApiResponse;
import es.tragsa.sido.backendtests.framework.model.Expectation;
import es.tragsa.sido.backendtests.framework.model.RequestDefinition;
import es.tragsa.sido.backendtests.framework.model.Scenario;

/**
 * Ejecuta un escenario tal y como lo declara su spec: lanza la peticion de {@code request} y aplica
 * encima las comprobaciones de {@code expect}.
 *
 * <p>No hay logica en Java por delante: todo lo que hace falta para automatizar el caso esta en el
 * propio spec, incluidos los bucles ({@code foreach}), la peticion ({@code request}) y las
 * aserciones ({@code expect}).
 */
public final class ScenarioExecutor {

    private ScenarioExecutor() {
    }

    public static ApiResponse execute(Scenario scenario) {
        if (scenario.getFailure() != null) {
            throw scenario.getFailure();
        }

        JsonNode context = scenario.dataContext();
        ApiResponse response = callDeclaredRequest(scenario, context);

        Expectation expect = scenario.getExpect();

        if (expect != null) {
            ResponseAssertions.validate(response, expect, context, scenario);
        }

        return response;
    }

    private static ApiResponse callDeclaredRequest(Scenario scenario, JsonNode context) {
        RequestDefinition request = scenario.getRequest();

        if (request == null) {
            throw new IllegalStateException("El escenario '" + scenario.label() + "' no define 'request', y es "
                    + "el unico sitio donde el runner busca la peticion. Declara endpoint y method en el spec.");
        }

        ApiResponse response = new ApiClient().call(request, context);
        System.out.println("[" + scenario.label() + "] [api] " + response.describe()
                + " (" + response.getElapsedMs() + " ms)");
        return response;
    }
}
