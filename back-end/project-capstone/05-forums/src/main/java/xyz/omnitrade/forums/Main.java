
package xyz.omnitrade.forums;

import io.helidon.common.LogConfig;
import io.helidon.common.reactive.Single;
import io.helidon.config.Config;
import io.helidon.dbclient.DbClient;
import io.helidon.dbclient.health.DbClientHealthCheck;
import io.helidon.health.HealthSupport;
import io.helidon.media.jsonb.JsonbSupport;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.metrics.MetricsSupport;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

/**
 * Main class that starts up server and registers Pokemon services.
 *
 * Pokémon, and Pokémon character names are trademarks of Nintendo.
 */
public final class Main {

    /**
     * Cannot be instantiated.
     */
    private Main() {
    }

    /**
     * Application main entry point.
     *
     * @param args command line arguments
     */
    public static void main(final String[] args) {
        startServer();
    }

    /**
     * Start the server.
     *
     * @return the created {@link io.helidon.webserver.WebServer} instance
     */
    static Single<WebServer> startServer() {
        // Load logging configuration
        LogConfig.configureRuntime();

        // By default this will pick up application.yaml from the classpath
        Config config = Config.create();

        // Prepare routing and build server
        Routing routing = createRouting(config);
        WebServer server = WebServer.builder(routing)
                .addMediaSupport(JsonpSupport.create())
                .addMediaSupport(JsonbSupport.create())
                .config(config.get("server"))
                .build();

        Single<WebServer> webserver = server.start();

        // Try to start the server. If successful, print some info and arrange to
        // print a message at shutdown. If unsuccessful, print the exception.
        webserver.thenAccept(ws -> {
                    System.out.println(
                            "WEB server is up! http://localhost:" + ws.port() + "/posts");
                    ws.whenShutdown().thenRun(()
                            -> System.out.println("WEB server is DOWN. Good bye!"));
                })
                .exceptionallyAccept(t -> {
                    System.err.println("Startup failed: " + t.getMessage());
                    t.printStackTrace(System.err);
                });

        return webserver;
    }

    /**
     * Creates new {@link io.helidon.webserver.Routing}.
     *
     * @param config configuration for this server
     * @return routing configured with JSON support, a health check, and a service
     */
    private static Routing createRouting(Config config) {
        Config dbConfig = config.get("db");

        // Client services are added through a service loader
        DbClient dbClient = DbClient.builder(dbConfig).build();

        // Support for health
        HealthSupport health = HealthSupport.builder()
                .addLiveness(DbClientHealthCheck.create(dbClient, dbConfig.get("health-check")))
                .build();

        // Initialize database schema
        InitializeDb.init(dbClient);

        return Routing.builder()
                .register(health)                   // Health at "/health"
                .register(MetricsSupport.create())  // Metrics at "/metrics"
                // .register("/", new ForumPostService(dbClient))
                .register("/", new PokemonService(dbClient))
                .build();
    }
}
