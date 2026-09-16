package com.integrityengine.cli;

import com.integrityengine.api.ServerMain;
import java.util.Arrays;
import java.util.Optional;

/** Command-line entry point. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        String first = args.length > 0 ? args[0] : "";
        switch (first) {
            // The one place the two front doors meet, so the release jar is a single
            // download: `java -jar integrity-engine.jar serve`.
            case "serve" -> ServerMain.main(Arrays.copyOfRange(args, 1, args.length));
            case "--version", "version" -> System.out.println("integrity-engine " + Optional
                    .ofNullable(Main.class.getPackage().getImplementationVersion()).orElse("dev"));
            default -> System.exit(new AnalyzeCommand(System.out, System.err).run(args));
        }
    }
}
