package es.tragsa.sido.backendtests.framework.runner;

import es.tragsa.sido.backendtests.framework.config.TestEnvironment;
import es.tragsa.sido.backendtests.framework.model.Scenario;
import es.tragsa.sido.backendtests.framework.model.SpecFeature;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Convierte cada escenario de un spec en un test dinamico de JUnit 5, de forma que cada
 * escenario aparece como un caso independiente en el informe.
 *
 * <p>Un escenario con {@code foreach} produce un test por cada elemento de su lista. Si el
 * {@code foreach} esta mal escrito, el fallo se reporta como un test mas y no como un error de la
 * clase entera, de modo que el resto de escenarios sigue ejecutandose.
 */
public final class ApiScenarioRunner {

    private ApiScenarioRunner() {
    }

    public static Stream<DynamicTest> run(String specResource) {
        return run(SpecLoader.load(specResource));
    }

    public static Stream<DynamicTest> run(SpecFeature feature) {
        List<DynamicTest> tests = new ArrayList<DynamicTest>();
        String filter = TestEnvironment.SCENARIO;
        List<String> available = new ArrayList<String>();

        for (Scenario scenario : feature.getScenarios()) {
            available.add(scenario.getIdRunner());

            if (!matches(scenario)) {
                continue;
            }

            for (Scenario iteration : iterationsOf(scenario)) {
                tests.add(DynamicTest.dynamicTest(iteration.displayName(),
                        () -> ScenarioExecutor.execute(iteration)));
            }
        }

        if (!filter.isEmpty() && tests.isEmpty()) {
            return Stream.of(unmatched(filter, available));
        }

        return tests.stream();
    }

    /**
     * El filtro de Maven no ha seleccionado ningun escenario. Devuelve un test que falla diciendolo, en
     * lugar de devolver un stream vacio: un {@code mvn test} con un id mal escrito se terminaria
     * en {@code BUILD SUCCESS} sin haber ejecutado nada, que es el peor resultado posible porque
     * parece que todo esta bien.
     */
    private static DynamicTest unmatched(String filter, List<String> available) {
        StringBuilder message = new StringBuilder();
        message.append("El filtro -Dsido.backend.scenario='").append(filter)
                .append("' no coincide con ningun escenario.");

        if (available.isEmpty()) {
            message.append(" El spec no declara ningun 'idRunner'.");
        } else {
            message.append("\n  Disponibles: ").append(String.join(", ", available))
                    .append("\n  Acepta '*' al final como comodin, por ejemplo '").append(available.get(0))
                    .append("*'.");
        }

        return DynamicTest.dynamicTest("filtro '" + filter + "' sin coincidencias", new Executable() {
            @Override
            public void execute() {
                throw new AssertionError(message.toString());
            }
        });
    }

    private static List<Scenario> iterationsOf(Scenario scenario) {
        try {
            return scenario.expand();
        } catch (RuntimeException e) {
            return java.util.Collections.singletonList(scenario.failing(e));
        }
    }

    /**
     * Surefire no sabe elegir un test dinamico concreto, asi que el filtro viene de
     * {@code -Dsido.backend.scenario}. Compara contra el {@code idRunner} del escenario y contra
     * su nombre, sin distinguir mayusculas, y acepta un patron con {@code *} al final.
     */
    private static boolean matches(Scenario scenario) {
        String filter = TestEnvironment.SCENARIO;

        if (filter.isEmpty()) {
            return true;
        }

        return matchesFilter(filter, scenario.getIdRunner()) || matchesFilter(filter, scenario.getName());
    }

    private static boolean matchesFilter(String filter, String value) {
        if (value == null) {
            return false;
        }

        if (filter.endsWith("*")) {
            return value.toLowerCase().startsWith(filter.substring(0, filter.length() - 1).toLowerCase());
        }

        return value.equalsIgnoreCase(filter);
    }
}
