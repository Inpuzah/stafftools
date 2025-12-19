package com.inpuzah.stafftools.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtil {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);

    /**
     * Parses a duration like "30m", "1h", "1h30m", "2d", "perm" into minutes.
     * Returns 0 for permanent.
     */
    public static long parseDuration(String duration) {
        if (duration == null) return -1;
        String input = duration.trim();
        if (input.isEmpty()) return -1;

        if (input.equalsIgnoreCase("permanent") || input.equalsIgnoreCase("perm") || input.equalsIgnoreCase("0")) {
            return 0;
        }

        Matcher matcher = DURATION_PATTERN.matcher(input);

        long totalSeconds = 0;
        boolean matched = false;
        while (matcher.find()) {
            matched = true;
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();

            switch (unit) {
                case "s" -> totalSeconds += value;
                case "m" -> totalSeconds += value * 60;
                case "h" -> totalSeconds += value * 60 * 60;
                case "d" -> totalSeconds += value * 60 * 60 * 24;
                case "w" -> totalSeconds += value * 60 * 60 * 24 * 7;
            }
        }

        // If user passed a raw number (e.g. "15"), treat as minutes.
        if (!matched) {
            try {
                return Math.max(0, Long.parseLong(input));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }

        // Round up partial minutes.
        return (totalSeconds + 59) / 60;
    }

    public static String formatDuration(long minutes) {
        if (minutes == 0) {
            return "Permanent";
        }

        long days = minutes / 1440;
        long hours = (minutes % 1440) / 60;
        long mins = minutes % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(" day").append(days > 1 ? "s " : " ");
        if (hours > 0) sb.append(hours).append(" hour").append(hours > 1 ? "s " : " ");
        if (mins > 0) sb.append(mins).append(" minute").append(mins > 1 ? "s" : "");

        return sb.toString().trim();
    }

    public static String formatDurationMillis(long millis) {
        if (millis < 0) return "Permanent";
        return formatDuration(millis / (60 * 1000));
    }

    public static String formatDate(long timestamp) {
        return DATE_FORMAT.format(new Date(timestamp));
    }

    public static String getTimeAgo(long timestamp) {
        long diff = System.currentTimeMillis() - timestamp;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long weeks = days / 7;
        long months = days / 30;
        long years = days / 365;

        if (years > 0) return years + " year" + (years > 1 ? "s" : "") + " ago";
        if (months > 0) return months + " month" + (months > 1 ? "s" : "") + " ago";
        if (weeks > 0) return weeks + " week" + (weeks > 1 ? "s" : "") + " ago";
        if (days > 0) return days + " day" + (days > 1 ? "s" : "") + " ago";
        if (hours > 0) return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
        if (minutes > 0) return minutes + " minute" + (minutes > 1 ? "s" : "") + " ago";
        return "Just now";
    }
}
