package com.integrityengine.api;

import java.nio.file.Path;

/** Starts the HTTP server. The CLI remains the entry point for scripted use. */
public final class ServerMain {

    private static final int DEFAULT_PORT = 7070;

    private ServerMain() {
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        Path database = Path.of("integrity-server.db");

        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals("--port")) {
                port = Integer.parseInt(args[i + 1]);
            } else if (args[i].equals("--db")) {
                database = Path.of(args[i + 1]);
            }
        }

        new IntegrityApi(database).start(port);
        System.out.printf("Integrity engine listening on http://localhost:%d  (database: %s)%n",
                port, database.toAbsolutePath());
    }
}
