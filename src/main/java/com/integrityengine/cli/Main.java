package com.integrityengine.cli;

/** Command-line entry point. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(new AnalyzeCommand(System.out, System.err).run(args));
    }
}
