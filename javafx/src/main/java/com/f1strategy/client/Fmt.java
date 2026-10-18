package com.f1strategy.client;

/**
 * Time formatting shared by the views. All times cross the API as
 * integer milliseconds (see docs/API_CONTRACT.md).
 */
final class Fmt {

    private Fmt() {
    }

    /** 94476 -> "1:34.5" */
    static String lapTime(long ms) {
        if (ms <= 0) {
            return "-";
        }
        return String.format("%d:%04.1f", ms / 60_000, (ms % 60_000) / 1000.0);
    }

    /** 5321000 -> "1:28:41" */
    static String totalTime(long ms) {
        if (ms <= 0) {
            return "-";
        }
        long h = ms / 3_600_000;
        long m = (ms % 3_600_000) / 60_000;
        long s = (ms % 60_000) / 1000;
        return String.format("%d:%02d:%02d", h, m, s);
    }
}
