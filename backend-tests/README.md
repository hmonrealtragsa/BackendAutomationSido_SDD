# backend-tests

Framework de pruebas de backend de SIDO2: **JUnit 5 + Unirest**, dirigido por
especificaciones JSON, replicando el patron SDD de `PlaywrightSDD`
(`config` + `specs` + `steps` + un runner).

Un caso de prueba se declara en un `*.spec.json`: se indica la peticion, el body (un
`.json` aparte) y lo que se espera de la respuesta (otro `.json` aparte). **No hace falta
escribir Java** salvo que el escenario necesite encadenar varias llamadas o iterar.

## Estructura

El codigo se divide en dos mitades: `framework/` es el motor y no cambia al anadir
casos; `features/` es donde crece el proyecto, con una carpeta por dominio.

```
src/test/java/es/tragsa/sido/backendtests/
├── framework/                 <- el motor
│   ├── config/                Endpoints, TestEnvironment, ApiTestConfig
│   ├── support/               Json, JsonLoader, JsonPath, Placeholders
│   ├── model/                 SpecFeature, Scenario, RequestDefinition, Expectation
│   ├── clients/               ApiClient, ApiResponse
│   ├── assertions/            ResponseAssertions, JsonAssertions
│   └── runner/                Carga, validacion y ejecucion de los escenarios
└── features/                  <- aqui se anaden los tests
    └── vehiculos/             Un paquete por dominio
        ├── VehiculosApiTest.java
        └── steps/             Steps Java del dominio (solo si hacen falta)

src/test/resources/
├── schemas/api-feature.schema.json    Esquema que valida los specs al arrancar
├── specs/<dominio>/<feature>.spec.json
├── bodies/<dominio>/*.json            Bodies reutilizables
└── responses/<dominio>/*.json         Respuestas esperadas
```

Al anadir un dominio nuevo se crea `features/<dominio>/` y, si hacen falta steps Java,
`features/<dominio>/steps/`. No hay que tocar `framework/`.

| Ruta | Que es |
|---|---|
| `src/test/java/.../framework/config/Endpoints.java` | **Registro central de endpoints.** Una constante por endpoint. |
| `src/test/java/.../framework/config/TestEnvironment.java` | Variables de entorno y propiedades del sistema. |
| `src/test/java/.../framework/config/ApiTestConfig.java` | Configuracion global de Unirest, cabeceras comunes y autenticacion Bearer. |
| `src/test/java/.../framework/support/` | Utilidades: JSON, carga de recursos, marcadores, rutas. |
| `src/test/java/.../framework/model/` | El spec interpretado (`SpecFeature`, `Scenario`, `RequestDefinition`, `Expectation`). |
| `src/test/java/.../framework/clients/ApiClient.java` | Cliente HTTP (equivale a un Page Object). |
| `src/test/java/.../framework/assertions/` | Comparacion de la respuesta con el JSON esperado. |
| `src/test/java/.../framework/runner/` | Motor: carga, valida y ejecuta los escenarios. |
| `src/test/java/.../features/<dominio>/` | Clases de test del dominio (una por spec) y sus `steps/`. |
| `src/test/resources/specs/<dominio>/` | **Fuente de verdad**: un `*.spec.json` por feature. |
| `src/test/resources/bodies/<dominio>/` | Bodies de peticion como `.json` reutilizables. |
| `src/test/resources/responses/<dominio>/` | Respuestas esperadas como `.json`. |
| `src/test/resources/schemas/` | Esquema JSON que valida los specs al arrancar. |

## Variables de entorno

Copia `.env.example` y define (o pasalas como `-D` de Maven):

| Variable | Propiedad equivalente | Descripcion |
|---|---|---|
| `SIDO_BACKEND_BASE_URI` | `-Dsido.backend.baseUri` | URL base de la API. |
| `SIDO_BACKEND_BEARER_TOKEN` | `-Dsido.backend.token` | Token Bearer. |
| `SIDO_BACKEND_CLIENT_ID` | `-Dsido.backend.clientId` | Cabecera `ClientId`. |
| `SIDO_BACKEND_CLIENT_VERSION` | `-Dsido.backend.clientVersion` | Cabecera `ClientVersion`. |
| `SIDO_BACKEND_TIMEOUT_MS` | `-Dsido.backend.timeoutMs` | Timeout de conexion y de lectura. |
| `SIDO_BACKEND_VERIFY_SSL` | `-Dsido.backend.verifySsl` | Verificar el certificado TLS. Por defecto `false`, porque la CA interna de preproduccion no suele estar en el almacen de la maquina. |
| `SIDO_BACKEND_SCENARIO` | `-Dsido.backend.scenario` | Ejecutar un unico escenario, por su `idRunner` o su nombre. Vacio = todos. |

## Endpoints

Todos los endpoints viven en `framework/config/Endpoints.java`:

```java
public static final String SIDO2_USUARIOCONTEXTO = BASE + "/api/usuarios/usuariocontexto";
public static final String SIDO2_VEHICULOS_MISPARTESDIARIOS = BASE + "/api/vehiculos/mispartesdiarios";
```

`BASE` es `${baseUri}`, que se sustituye por `SIDO_BACKEND_BASE_URI`. Para cambiar de
prepro a produccion no se toca ningun spec.

En un spec, `request.endpoint` puede ser:

- el nombre de la constante: `"endpoint": "SIDO2_USUARIOCONTEXTO"`;
- una plantilla: `"endpoint": "${baseUri}/api/usuarios/usuariocontexto"`;
- una URL absoluta: `"endpoint": "https://sido2prepro.tragsa.es/api/usuarios/usuariocontexto"`.

## Anatomia de un spec

```json
{
  "feature": "Usuarios SIDO2",
  "scenarios": [
    {
  "idRunner": "CP_01",
  "name": "SIDO llama al microservicio SIDO2 usuariocontexto para obtener la informacion del User",
      "when": "Llamamos al microservicio usuariocontexto con un metodo GET",
      "then": "Se comprueba que nos devuelve un 200 y que contiene los datos userContext_hector",
      "data": { "usuarioId": 12345 },
      "request": {
        "endpoint": "SIDO2_USUARIOCONTEXTO",
        "method": "GET",
        "queryParams": { "codigoUsuario": "${data.usuarioId}" }
      },
      "expect": {
        "status": 200,
        "response": "responses/usuarios/userContext_hector.json",
        "matchMode": "partial"
      }
    }
  ]
}
```

- `idRunner` es **obligatorio** y unico: es la clave con la que se lanza el caso suelto
  (`-Dsido.backend.scenario=CP_01_Vehiculos`). Admite `A-Z a-z 0-9 _ . -`.
  Conviene acabar el nombre en el dominio (`CP_01_Vehiculos`) para que los ids de features
  distintas no choquen cuando se lanzan a la vez.
- `name`, `when`, `and`, `then` son documentacion funcional (igual que en PlaywrightSDD).
- `data` y `given` alimentan los marcadores `${data.campo}`.

## Como se ejecuta un escenario

Prioridad, igual que `feature-runner.js` en PlaywrightSDD:

1. Si hay un **step Java** registrado con la clave `scenario.idRunner`, se ejecuta y se usa la
   respuesta que devuelve.
2. Si no, se ejecuta la **peticion declarada en `request`**.
3. El bloque **`expect`** se aplica siempre, venga la respuesta del step o del JSON.

Si no hay ni step ni `request`, el test falla con un mensaje que dice exactamente que falta.

## Comprobar la respuesta

### Comparar contra un `.json` (`response` + `matchMode`)

`response` admite las dos formas descritas en [Bodies y respuestas](#bodies-y-respuestas-las-dos-formas):
una ruta de fichero o el objeto en linea. El modo de comparacion es el mismo en ambos casos.

| `matchMode` | Significado |
|---|---|
| `partial` (por defecto) | La respuesta debe contener **todos** los datos del JSON esperado. Puede tener campos y elementos de mas. |
| `exact` | La respuesta debe ser **igual** al JSON esperado. |

Para arrays, `arrayMatch` decide como comparar:

| `arrayMatch` | Significado |
|---|---|
| `index` (por defecto) | Por posicion. |
| `byKey` | Busca cada elemento esperado por el campo `arrayKey`. |
| `contains` | Cada elemento esperado tiene que aparecer en algun sitio. |

`ignorePaths` omite rutas concretas y admite comodin:

```json
"ignorePaths": ["$.fechaActualizacion", "$.content[*].fecha"]
```

Sentinelas disponibles dentro del JSON esperado, para no tener que fijar valores volatiles:

| Sentinela | Significado |
|---|---|
| `{ "$any": true }` | El valor da igual (ids, fechas, tokens). |
| `{ "$type": "array" }` | Solo se comprueba el tipo. |
| `{ "$regex": "^[0-9]{8}[A-Z]$" }` | El texto encaja con la expresion regular. |
| `{ "$contains": "GESTOR" }` | El texto contiene ese fragmento. |

### Comprobaciones sueltas (`jsonPaths`)

```json
"jsonPaths": [
  { "path": "$.id", "operator": "equals", "value": "${data.usuarioId}" },
  { "path": "$.content", "operator": "notNull" },
  { "path": "$.content", "operator": "greaterThanOrEqual", "value": 0 },
  { "path": "$.nombre", "operator": "contains", "value": "hect", "ignoreCase": true }
]
```

Operadores: `equals`, `notEquals`, `contains`, `notContains`, `matches`, `in`, `exists`,
`notExists`, `notNull`, `notEmpty`, `greaterThan`, `greaterThanOrEqual`, `lessThan`,
`lessThanOrEqual`, `size`.

Los filtros JsonPath (`$.data[?(@.id=='1')]`) y el descenso recursivo (`$..id`) no estan
soportados a proposito: fallan con un mensaje explicito en vez de dar un falso negativo.

### Otras comprobaciones

| Campo | Uso |
|---|---|
| `status` | `200`, `"2xx"` o `[200, 204]`. |
| `contentType` | El `Content-Type` debe contener el texto. |
| `headers` | `{ "X-Total-Count": { "equals": "10" }, "Content-Type": { "contains": "json" } }` |
| `bodyEmpty` | `true`/`false`. |
| `responseTimeMs` | Tiempo maximo de respuesta. |

Se recogen **todos** los fallos y se informan juntos, no de uno en uno.

## Bodies y respuestas: las dos formas

`request.body` y `expect.response` admiten **las dos mismas formas**:

| Forma | Como se escribe | Cuando usarla |
|---|---|---|
| **Fichero** (preferida) | `"body": "bodies/vehiculos/parteDiario.json"` | Siempre que el body o la respuesta sea reutilizable. |
| **Inline** | `"body": { "id": 1 }` | Al pegar o explorar un caso rapido, y como formato de *entrada*. |

El framework solo **lee** las dos. No escribe ficheros en `src/` durante un `mvn test`: la
extraccion es una tarea de autoria, la hace el agente (o tu editor) al escribir el spec, no el
runner.

### Reglas de la forma fichero

1. **Extrae siempre** `body` y `expect.response` a fichero. Un spec legible vale mas que un
   fichero mas.
2. **Nombra por `idRunner`**: `bodies/<dominio>/<idRunner>.<concepto>.json`. `idRunner` ya es
   unico y el esquema exige el patron `^[A-Za-z0-9_.-]+$`, asi que el nombre se deriva y no se
   inventa. Si el fichero lo comparten varios escenarios, nombralo por el concepto
   (`bodies/vehiculos/parteDiario.json`), no por un `idRunner` concreto.
3. **Lo que varia entre escenarios va a `data`**, no a un fichero nuevo.

### Ejemplo real

Un unico spec, `specs/vehiculos/vehiculos.spec.json`, con dos escenarios que comparten el mismo
body y solo se diferencian en la respuesta esperada:

```json
// bodies/vehiculos/parteDiario.json
{
  "fecha": "30/9/2030",
  "vehiculoId": "5eb5f53c-7ac3-4f20-9990-a8eb41c9e7aa",
  "situacion": "KILOMETROS",
  "kilometraje": [{
    "lecturaCuentaKilometrosInicio": 300,
    "lecturaCuentaKilometrosFin": 350,
    "itinerario": "TEST"
  }]
}
```

```json
// specs/vehiculos/vehiculos.spec.json
{
  "idRunner": "CP_02_Vehiculos",
  "name": "El usuario intenta realizar un parte con una fecha ya utilizada",
  "request": {
    "endpoint": "SIDO2_VEHICULOS_MISPARTESDIARIOS",
    "method": "POST",
    "body": "bodies/vehiculos/cp_02.parteVehiculoUsado.json"
  },
  "expect": {
    "status": 400,
    "response": "responses/vehiculos/cp_02.error_parteVehiculoUsado.json"
  }
}
```

Un unico `VehiculosApiTest` los ejecuta todos: cada escenario del spec sale como un test
independiente en el informe.

### Interpolacion: fichero vs inline

Un fichero se interpola **como texto** antes de interpretarlo; un objeto inline se interpola
**sobre el arbol JSON**:

| | Fichero | Inline |
|---|---|---|
| `"km": ${data.km}` | numerico (se interpola antes de parsear) | da error, no es JSON valido |
| `"km": "${data.km}"` | texto | numerico si `data.km` es numero |
| Marcadores en claves, filtros, etc. | no | si |

Por eso un fichero con marcadores sin comillas (`"km": ${data.km}`) no es JSON estricto hasta
que se interpola, y el editor lo marcara en rojo. Es intencionado; si prefieres que el fichero
sea JSON valido siempre, entrecomilla los marcadores y acepta que los numeros viajen como texto.

Las respuestas esperadas se guardan en `src/test/resources/responses/<dominio>/` con el mismo
mecanismo. Lo mas comodo es capturar la respuesta real una vez y quedarse con los campos que
importan, dejando `{ "$any": true }` para los volatiles.

## Marcadores disponibles

| Marcador | Valor |
|---|---|
| `${baseUri}` | URL base del entorno. |
| `${data.campo}` | Dato del escenario (`data` tiene prioridad sobre `given`). |
| `${env:NOMBRE}` | Variable de entorno. |
| `${today}` / `${todayIso}` | Fecha de hoy `dd/MM/yyyy` / `yyyy-MM-dd`. |
| `${nowIso}` / `${epochMillis}` / `${uuid}` | Marca de tiempo / UUID generados en ejecucion. |

Un marcador desconocido hace fallar el test con un mensaje que indica cuales existen. Un dato
que a su vez sea un marcador se resuelve en cadena: si `data.fecha = "${todayIso}"`,
`"fecha": "${data.fecha}"` produce la fecha de hoy.

## Anadir un caso de prueba nuevo

1. Si el endpoint no existe, anade la constante en `framework/config/Endpoints.java`.
2. Si necesitas un body, crea `src/test/resources/bodies/<dominio>/<nombre>.json`.
3. Si vas a comparar la respuesta completa, crea `src/test/resources/responses/<dominio>/<nombre>.json`.
4. Anade el escenario a `src/test/resources/specs/<dominio>/<feature>.spec.json`.
5. Si el dominio es nuevo, crea `features/<dominio>/` y un test de 10 lineas:

```java
package es.tragsa.sido.backendtests.features.usuarios;

@Tag("usuarios")
class UsuariosApiTest extends AbstractApiSpecTest {
    @Override
    protected String specResource() {
        return "specs/usuarios/usuarios.spec.json";
    }

    @Override
    protected Map<String, ScenarioStep> steps() {
        return UsuariosSteps.createUsuariosSteps();
    }
}
```

Cada escenario aparece como un test independiente en el informe, con el nombre
`<feature> - <scenario.name>`. El spec se valida contra
`schemas/api-feature.schema.json` al arrancar, asi que un campo mal escrito o un tipo
incorrecto fallan antes de lanzar ninguna peticion.

## Cliente HTTP: Unirest

Las llamadas las hace [Unirest](https://kong.github.io/unirest-java/), y todo queda
encapsulado en `clients/ApiClient.java`, asi que los specs y los steps no cambian.

```java
HttpRequest<?> request = Unirest.request("GET", url)
        .headers(ApiTestConfig.authHeaders(auth))   // Authorization segun "auth"
        .queryString("matricula", "1234ABC");

HttpResponse<String> response = request.asString();
response.getStatus();       // int
response.getBody();         // String
response.getHeaders().all() // List<kong.unirest.Header>
```

Tres detalles que conviene tener en cuenta al tocar el cliente:

- **La config es un singleton y se aplica una vez por JVM.** `Unirest.config()` construye el pool
  de conexiones la primera vez, y `connectTimeout`/`socketTimeout` lanzan
  `UnirestConfigException` si se llaman despues. Por eso `ApiTestConfig.configure()` esta
  guardado con un flag y se invoca una sola vez. Por la misma razon `Unirest.shutDown()` **no**
  va en un `@AfterAll` por clase: cerrarlo entre clases deja a la siguiente con un cliente recien
  construido al que ya no se le puede volver a fijar `verifySsl`, y falla la validacion del
  certificado (PKIX) en cuanto el entorno usa una CA interna. El cierre esta en un shutdown hook
  de la JVM, registrado por `configure()`.
- **La autenticacion va por peticion, no por defecto global.** Unirest no tiene el concepto de
  "request specification" de RestAssured, asi que la cabecera `Authorization` se anade en cada
  llamada segun el campo `auth` del spec. Con `auth: none` el token no sale, lo cual permite
  comprobar que un endpoint responde 401 sin credenciales.
- **El cuerpo se serializa con Jackson.** `body(String)` de Unirest, no su mapper, para que el
  JSON enviado sea exactamente el que produce el spec.

## Steps Java (solo cuando hace falta)

Registra la accion en `features/<dominio>/steps/` y devuelve la respuesta para que el
bloque `expect` del spec la siga comprobando:

```java
steps.put("getUserContextAndVehicles", UsuariosSteps::getUserContextAndVehicles);

static ApiResponse getUserContextAndVehicles(ApiClient client, Scenario scenario) {
    ApiResponse userContext = client.call(scenario.getRequest(), scenario.dataContext());
    ResponseAssertions.assertStatus(userContext, 200);

    Map<String, Object> query = new LinkedHashMap<String, Object>();
    query.put("devolverResults", true);
    ApiResponse partes = client.send("GET", Endpoints.SIDO2_VEHICULOS_MISPARTESDIARIOS, query, null);
    ResponseAssertions.assertJsonPathExists(partes, "$.content");

    return partes;
}
```

## Ejecucion

Requisitos: **JDK 8+** y **Maven**.

```bash
mvn test
```

### Lanzar un solo caso por su `idRunner`

Cada escenario declara un `idRunner` unico en el spec. Es la forma de lanzar **un unico caso**:

```bash
mvn test "-Dsido.backend.scenario=CP_02_Vehiculos"
```

Con token y demas configuracion a la vez:

```bash
mvn test "-Dsido.backend.token=XXXX" "-Dsido.backend.scenario=CP_02_Vehiculos"
```

Un `*` al final acts como comodin, para lanzar una familia entera:

```bash
mvn test "-Dsido.backend.scenario=CP_0*"     # todos los CP_0x
```

Detalles del filtro:

- Compara contra el `idRunner` y contra el `name`, sin distinguir mayusculas.
- Si el valor **no coincide con ningun escenario, el test falla** en vez de terminar en
  `BUILD SUCCESS` sin ejecutar nada. El mensaje lista los ids disponibles:

  ```
  El filtro -Dsido.backend.scenario='CP_99' no coincide con ningun escenario.
    Disponibles: CP_01_Vehiculos, CP_02_Vehiculos
    Acepta '*' al final como comodin, por ejemplo 'CP_01_Vehiculos*'.
  ```

- **En Windows/PowerShell las `-D` van entrecomilladas.** Sin comillas PowerShell las parte mal y
  Maven no recibe la propiedad. En bash o cmd tambien funciona sin comillas.

### Otros filtros

Solo una feature (esto si lo resuelve Surefire, porque selecciona la clase):

```bash
mvn test -Dtest=VehiculosApiTest
```

Solo los escenarios de un tag:

```bash
mvn test -Dgroups=vehiculos
```

Los escenarios que crean datos estan en `vehiculos-mutating.spec.json`, marcados con el tag
`mutating` y **excluidos por defecto**. Para ejecutarlos:

```bash
mvn test -Dbackend.excludedGroups=
```

Con propiedades en lugar de variables de entorno:

```bash
mvn test -Dsido.backend.baseUri=https://sido2prepro.tragsa.es -Dsido.backend.token=XXXX
```

### Como aparece el caso en el informe

Surefire 3.x **descarta el nombre de los tests dinamicos**: en el informe todos los casos de una
feature salen como `apiScenarios`, distinguidos solo por `Run 1` / `Run 2` segun el orden de
ejecucion. Por eso el `idRunner` se antepone al mensaje de fallo, que si llega al informe:

```
Run 1: VehiculosApiTest.apiScenarios [CP_01] CP_01.Cuando el usuario...
```

y en la traza de consola:

```
[[CP_02] CP_02...][api] POST https://sido2prepro.../partesdiariossituacion -> 401
```
