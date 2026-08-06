package com.syidcreate.nav;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NavParser {
    public static final class NavInstruction {
        public final String turn;
        public final int distanceMeters;
        public final String street;
        public final String eta;

        public NavInstruction(String turn, int distanceMeters, String street, String eta) {
            this.turn = turn;
            this.distanceMeters = Math.max(distanceMeters, 0);
            this.street = street == null ? "-" : street;
            this.eta = eta == null ? "-" : eta;
        }

        @Override
        public String toString() {
            return turn + " • " + distanceMeters + " m • " + street + " • " + eta;
        }
    }

    private static final Pattern DISTANCE = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(km|kilometer|kilometre|m|meter|metre)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOCK = Pattern.compile("\\b([01]?\\d|2[0-3])[:.]([0-5]\\d)\\b");
    private static final Pattern DURATION = Pattern.compile("\\b(\\d+)\\s*(min|menit|minute|minutes|jam|hour|hours)\\b", Pattern.CASE_INSENSITIVE);

    private NavParser() {}

    public static NavInstruction parse(String title, String text, String bigText, String subText) {
        String combined = join(title, text, bigText, subText);
        String lower = combined.toLowerCase(Locale.ROOT);
        String turn = detectTurn(lower);
        int distance = detectDistance(combined);
        String eta = detectEta(combined);
        String street = detectStreet(title, text, bigText);
        return new NavInstruction(turn, distance, street, eta);
    }

    private static String detectTurn(String lower) {
        if (containsAny(lower, "putar balik", "u-turn", "u turn")) return "UTURN";
        if (containsAny(lower, "bundaran", "roundabout")) return "ROUNDABOUT";
        if (containsAny(lower, "keluar kiri", "exit left")) return "EXIT_LEFT";
        if (containsAny(lower, "keluar kanan", "exit right")) return "EXIT_RIGHT";
        if (containsAny(lower, "tajam kiri", "sharp left")) return "SHARP_LEFT";
        if (containsAny(lower, "tajam kanan", "sharp right")) return "SHARP_RIGHT";
        if (containsAny(lower, "serong kiri", "sedikit ke kiri", "slight left", "keep left")) return "SLIGHT_LEFT";
        if (containsAny(lower, "serong kanan", "sedikit ke kanan", "slight right", "keep right")) return "SLIGHT_RIGHT";
        if (containsAny(lower, "gabung kiri", "merge left")) return "MERGE_LEFT";
        if (containsAny(lower, "gabung kanan", "merge right")) return "MERGE_RIGHT";
        if (containsAny(lower, "belok kiri", "turn left", "ke kiri")) return "LEFT";
        if (containsAny(lower, "belok kanan", "turn right", "ke kanan")) return "RIGHT";
        if (containsAny(lower, "tiba", "tujuan", "arrive", "destination")) return "ARRIVE";
        return "STRAIGHT";
    }

    private static int detectDistance(String text) {
        Matcher matcher = DISTANCE.matcher(text);
        if (!matcher.find()) return 0;
        double value;
        try {
            value = Double.parseDouble(matcher.group(1).replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return 0;
        }
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        return unit.startsWith("k") ? (int) Math.round(value * 1000.0) : (int) Math.round(value);
    }

    private static String detectEta(String text) {
        Matcher clock = CLOCK.matcher(text);
        if (clock.find()) return clock.group().replace('.', ':');
        Matcher duration = DURATION.matcher(text);
        if (duration.find()) return duration.group().trim();
        return "-";
    }

    private static String detectStreet(String title, String text, String bigText) {
        String[] candidates = {title, text, bigText};
        for (String candidate : candidates) {
            String cleaned = cleanStreet(candidate);
            if (!cleaned.isEmpty() && cleaned.length() >= 3) return cleaned;
        }
        return "Jalan berikutnya";
    }

    private static String cleanStreet(String input) {
        if (input == null) return "";
        String value = input.replace('\n', ' ').replace('|', ' ').trim();
        value = DISTANCE.matcher(value).replaceAll("");
        value = value.replaceAll("(?i)^(belok|turn|continue|lanjut|lurus|keep|ambil|menuju|head|exit|keluar)\\s+", "");
        value = value.replaceAll("\\s{2,}", " ").trim();
        if (value.length() > 44) value = value.substring(0, 44).trim();
        return value;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) if (text.contains(needle)) return true;
        return false;
    }

    private static String join(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                if (builder.length() > 0) builder.append(" | ");
                builder.append(value.trim());
            }
        }
        return builder.toString();
    }
}
