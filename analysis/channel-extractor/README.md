# channel-extractor

Static extraction of the inter-service channel model of a Spring microservice system: the first
stage of the incremental inter-service taint analysis (the per-service taint summaries are
composed along these channels).

It reads sources only (no build, no classpath) and produces JSON with:

| Section       | Content                                                                               |
|---------------|---------------------------------------------------------------------------------------|
| `endpoints`   | Spring MVC handlers: HTTP method, path, parameter sources, exposure through the gateway |
| `httpClients` | OpenFeign client methods and the places that call them                                |
| `publishers`  | `RabbitTemplate` sends and transactional-outbox enqueues: exchange, routing key, payload type |
| `bindings`    | queue ↔ exchange bindings (`BindingBuilder`, `new Binding`, `@QueueBinding`)           |
| `listeners`   | `@RabbitListener` / `@RabbitHandler` methods and their payload parameter             |
| `channels`    | resolved edges: `HTTP` client → endpoint, `AMQP` publisher → listener                 |
| `unresolved`  | what was seen but could not be linked, with the reason                                |

Values are resolved through constants, string concatenation, local variables, helper methods
(also in shared libraries) and the arguments of every caller of a private helper, so a queue is
only paired with the routing keys passed together with it.

## Usage

```bash
./mvnw package
java -jar target/channel-extractor-0.1.0-SNAPSHOT.jar ../../ecommerce/backend --out model.json
```

`--gateway-config <file>` sets the Spring Cloud Gateway routes; by default the first
`api-gateway.yml` under the root is used.

## Not modelled yet

- programmatic HTTP clients (`RestTemplate`, `RestClient`, `WebClient`): listed as `HTTP_CALL`
- gateway path rewriting filters (`StripPrefix`, `RewritePath`)
- queue names and keys from property placeholders (`${...}`)
- bindings declared inside shared-library helpers
