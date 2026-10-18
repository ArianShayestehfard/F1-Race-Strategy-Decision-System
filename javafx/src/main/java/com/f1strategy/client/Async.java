package com.f1strategy.client;

import javafx.application.Platform;

import java.util.function.Consumer;

/**
 * Single place where UI work is scheduled around background API calls.
 * Every view uses this so threading and error handling stay consistent.
 */
final class Async {

    @FunctionalInterface
    interface ApiCall {
        void run() throws Exception;
    }

    private Async() {
    }

    /** Runs {@code call} on a worker thread, then exactly one of the callbacks on the FX thread. */
    static void run(ApiCall call, Runnable onSuccess, Consumer<String> onError) {
        Thread worker = new Thread(() -> {
            try {
                call.run();
                Platform.runLater(onSuccess);
            } catch (Exception ex) {
                Platform.runLater(() -> onError.accept(rootMessage(ex)));
            }
        }, "f1-api-call");
        worker.setDaemon(true);
        worker.start();
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }
}
