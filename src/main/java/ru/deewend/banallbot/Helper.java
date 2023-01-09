package ru.deewend.banallbot;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;

import java.text.DecimalFormat;

public class Helper {
    public static final long DELTA_SECONDS_NAN = Long.MAX_VALUE;

    private Helper() {
    }

    public static String getContent(Message message) {
        return message.getContentRaw().trim();
    }

    public static boolean isMe(User user) {
        return user.isBot() && user.getIdLong() == 1035514434497560686L;
    }

    public static String getPlacePostfix(int place) {
        char lastChar = (char) ('0' + (place % 10));
        String placePostfix;
        if (lastChar == '1')      placePostfix = "-st: ";
        else if (lastChar == '2') placePostfix = "-nd: ";
        else if (lastChar == '3') placePostfix = "-rd: ";
        else                      placePostfix = "-th: ";

        return placePostfix;
    }

    public static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);

            return true;
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();

            return false;
        }
    }

    public static String diffTime(long deltaSeconds) {
        if (deltaSeconds < 0) return "<error, negative delta>";
        if (deltaSeconds == DELTA_SECONDS_NAN) return "<NaN>";
        if (deltaSeconds == 1) return "1 second";
        if (deltaSeconds < 60) return deltaSeconds + " seconds";
        if (deltaSeconds == 60) return "1 minute";
        if (deltaSeconds < 3600) return divide(deltaSeconds, 60) + " minutes";
        if (deltaSeconds == 3600) return "1 hour";
        if (deltaSeconds < 86400) return divide(deltaSeconds, 3600) + " hours";
        if (deltaSeconds == 86400) return "1 day";

        return divide(deltaSeconds, 86400) + " days";
    }

    private static String divide(long deltaSeconds, double value) {
        return new DecimalFormat("#0.00").format(deltaSeconds / value);
    }
}
