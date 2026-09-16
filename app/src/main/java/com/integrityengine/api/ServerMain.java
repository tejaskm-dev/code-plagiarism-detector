package com.integrityengine.api;

import io.javalin.Javalin;
import java.nio.file.Path;

/** Starts the HTTP server. The CLI remains the entry point for scripted use. */
public final class ServerMain {

    private static final int DEFAULT_PORT = 7070;

    private static final String USAGE = """
            usage: serve [--port <n>] [--db <file>]

              --port <n>     port to listen on (default: 7070)
              --db <file>    SQLite database (default: integrity-server.db)

            Serves the web UI at / and the REST API under /api/v1.
            """;

    private ServerMain() {
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        Path database = Path.of("integrity-server.db");

        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--help", "-h" -> {
                        System.out.print(USAGE);
                        return;
                    }
                    case "--port" -> port = parsePort(valueAfter(args, i++));
                    case "--db" -> database = Path.of(valueAfter(args, i++));
                    default -> throw new IllegalArgumentException("unknown option: " + args[i]);
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println();
            System.err.print(USAGE);
            System.exit(2);
            return;
        }

        Javalin app = new IntegrityApi(database).server().start(port);
        // docker stop and Ctrl+C both arrive as signals; stopping Jetty lets in-flight
        // requests finish rather than cutting a SQLite write off halfway.
        Runtime.getRuntime().addShutdownHook(new Thread(app::stop, "integrity-shutdown"));
        System.out.printf("Integrity engine %s listening on http://localhost:%d  (database: %s)%n",
                IntegrityApi.VERSION, app.port(), database.toAbsolutePath());
    }

    private static String valueAfter(String[] args, int index) {
        if (index + 1 >= args.length) {
            throw new IllegalArgumentException("missing value for " + args[index]);
        }
        return args[index + 1];
    }

    private static int parsePort(String raw) {
        try {
            int port = Integer.parseInt(raw);
            if (port >= 0 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException e) {
            // Falls through to the shared message.
        }
        throw new IllegalArgumentException("--port must be a number from 0 to 65535, got " + raw);
    }
}
