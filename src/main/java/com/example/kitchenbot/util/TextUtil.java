package com.example.kitchenbot.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextUtil {

    public static String cleanMarkdown(String text) {
        if (text == null) return "";
        return text.replace("**", "").replace("*", "").replace("#", "").trim();
    }

    public static long parseDuration(String text) {
        if (text == null) return 0;
        text = text.toLowerCase().trim();

        long totalSeconds = 0;
        boolean foundExplicit = false;

        Matcher matcherH = Pattern.compile("(\\d+)\\s*(ч|h|час)").matcher(text);
        if (matcherH.find()) {
            totalSeconds += Long.parseLong(matcherH.group(1)) * 3600;
            foundExplicit = true;
        }

        Matcher matcherM = Pattern.compile("(\\d+)\\s*(м|m|мин)").matcher(text);
        if (matcherM.find()) {
            totalSeconds += Long.parseLong(matcherM.group(1)) * 60;
            foundExplicit = true;
        }

        Matcher matcherS = Pattern.compile("(\\d+)\\s*(с|s|сек)").matcher(text);
        if (matcherS.find()) {
            totalSeconds += Long.parseLong(matcherS.group(1));
            foundExplicit = true;
        }

        if (foundExplicit) return totalSeconds;

        if (text.contains(":")) {
            String[] parts = text.split(":");
            if (parts.length == 3) {
                return Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60 + Long.parseLong(parts[2]);
            } else if (parts.length == 2) {
                return Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1]);
            }
        }

        String digits = text.replaceAll("\\D", "");
        if (!digits.isEmpty()) {
            try {
                return Long.parseLong(digits);
            } catch (NumberFormatException e) {}
        }

        return 0;
    }

    public static String formatDuration(long totalSeconds) {
        if (totalSeconds < 60) return totalSeconds + " сек";
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append(" ч ");
        if (m > 0) sb.append(m).append(" мин ");
        if (s > 0) sb.append(s).append(" сек");

        return sb.toString().trim();
    }
}