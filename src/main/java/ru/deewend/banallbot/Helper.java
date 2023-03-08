package ru.deewend.banallbot;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;

public class Helper {
    public static final String ADMIN_COMMAND_PREFIX = "#!admin_panel ";
    public static final byte RESULT_NOT_A_COMMAND = 0;
    public static final byte RESULT_EXECUTED_THE_COMMAND = 1;
    public static final byte RESULT_BINARY_ANSWER_YES = 2;
    public static final byte RESULT_BINARY_ANSWER_NO = 3;
    public static final byte RESULT_THERE_WAS_AN_ISSUE_WITH_THIS_COMMAND = 4;

    public static final long DELTA_SECONDS_NAN = Long.MAX_VALUE;

    private static /* volatile */ boolean adminRestrictionsAreOff;

    private Helper() {
    }

    public static byte checkAdminCommand(boolean isAdmin, String message) {
        message = message.toLowerCase();
        if (message.startsWith(ADMIN_COMMAND_PREFIX)) {
            if (!isAdmin) {
                return RESULT_THERE_WAS_AN_ISSUE_WITH_THIS_COMMAND;
            }

            String command = message.substring(14).trim();
            switch (command) {
                case "are_admin_restrictions_off":
                    return (adminRestrictionsAreOff ? RESULT_BINARY_ANSWER_YES :
                            RESULT_BINARY_ANSWER_NO);
                case "admin_turn_off_restrictions":
                    if (adminRestrictionsAreOff) {
                        return RESULT_THERE_WAS_AN_ISSUE_WITH_THIS_COMMAND;
                    }
                    adminRestrictionsAreOff = true;

                    return RESULT_EXECUTED_THE_COMMAND;
                case "admin_turn_on_restrictions":
                    if (!adminRestrictionsAreOff) {
                        return RESULT_THERE_WAS_AN_ISSUE_WITH_THIS_COMMAND;
                    }
                    adminRestrictionsAreOff = false;

                    return RESULT_EXECUTED_THE_COMMAND;
                default:
                    return RESULT_THERE_WAS_AN_ISSUE_WITH_THIS_COMMAND;
            }
        }

        return RESULT_NOT_A_COMMAND;
    }

    public static boolean areAdminRestrictionsOff() {
        return adminRestrictionsAreOff;
    }

    public static String getContent(Message message) {
        return message.getContentRaw().trim();
    }

    public static boolean isMe(User user) {
        return user.isBot() && user.getIdLong() == 1035514434497560686L;
    }

    public static String getPlacePostfix(int place) {
        if (place >= 11 && place <= 13) return "-th";

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
            Thread.currentThread().interrupt();

            return false;
        }
    }

    public static String diffTime(long deltaSeconds) {
        if (deltaSeconds < 0) return "<error, negative delta>";
        if (deltaSeconds == DELTA_SECONDS_NAN) return "<NaN>";

        long days = deltaSeconds / 86400;
        deltaSeconds -= days * 86400;
        long hours = deltaSeconds / 3600;
        deltaSeconds -= hours * 3600;
        long minutes = deltaSeconds / 60;
        deltaSeconds -= minutes * 60;
        long seconds = deltaSeconds;

        String result = "";
        boolean needToAppendSpace = false;
        if (days > 0) {
            result += days + "d";
            needToAppendSpace = true;
        }
        if (hours > 0) {
            result += (needToAppendSpace ? " " : "") + hours + "h";
            needToAppendSpace = true;
        }
        if (minutes > 0) {
            result += (needToAppendSpace ? " " : "") + minutes + "m";
            needToAppendSpace = true;
        }
        if (seconds > 0) {
            result += (needToAppendSpace ? " " : "") + seconds + "s";
        }
        if (result.isEmpty()) result = "0s";

        return result;
    }
}
