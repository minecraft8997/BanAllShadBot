package ru.deewend.banallbot;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class OneWordStoryConstructor extends ListenerAdapter {
    private static final String[] INTRODUCTIONS = {
            "Was really excited listening to this story!",
            "Constructed your masterpiece into one message:",
            "tbh this story was quite weird but anyway printing it rn",
            "Ummmmmm",
            ":claps:",
            "Got too bored",
            "\\*hysterical laughter*",
            "Guys,"
    };

    // please don't read the next 4 lines if you are underage

    private static final String[] FORBIDDEN_WORDS =
            { "fuck", "fucked", "fucking", "motherfucker", "penis",
              "porn", "cunt", "bitch", "shit", "bullshit", "ass", "asshole" };

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        TextChannel channel = event.getChannel();
        if (channel.getIdLong() != Main.ONE_WORD_STORY_CHANNEL_ID) return;

        Message messageReceived = event.getMessage();
        if (Helper.isMe(messageReceived.getAuthor())) return;
        long itsId = messageReceived.getIdLong();

        String messageContent = Helper.getContent(messageReceived);
        if (messageContent.startsWith(".")) {
            channel.getHistory().retrievePast(100).queue(list -> {
                StringBuilder storyBuilder = new StringBuilder();
                int i;
                int thisDotI = -1;
                int foundBetween = 0;
                for (i = 0; i < list.size(); i++) {
                    Message message = list.get(i);
                    if (Helper.isMe(message.getAuthor())) continue;
                    if (thisDotI == -1) {
                        if (message.getIdLong() == itsId) thisDotI = i;

                        continue;
                    }

                    String messageContent_ = Helper.getContent(message);
                    if (messageContent_.startsWith(".")) {
                        if (messageContent_.length() > 1) {
                            String trueStart = messageContent_.substring(1);
                            boolean foundAtLeastOneLetter = false;
                            for (int j = 0; j < trueStart.length(); j++) {
                                if (Character.isLetter(trueStart.charAt(j))) {
                                    foundAtLeastOneLetter = true;

                                    break;
                                }
                            }
                            if (foundAtLeastOneLetter) {
                                storyBuilder.append(trueStart);
                                if (foundBetween > 0) storyBuilder.append(' ');
                            }
                        }
                        i -= 1;

                        break;
                    } else {
                        foundBetween++;
                    }
                }

                if (thisDotI == -1) {
                    messageReceived.reply("Just noticed this message contains " +
                            "a dot and while constructing the final story I didn't " +
                            "manage to access it :/").queue();
                    // Possible reasons:
                    // a) the author decided to delete message while we were
                    //    processing history;
                    // b) already 100 messages passed while we were doing our job.

                    return;
                }
                if (foundBetween == 0 && storyBuilder.length() == 0) return;
                i--;

                boolean firstWord = (storyBuilder.length() == 0);
                long firstMessageTimestamp = list
                        .get(!firstWord ? (i + 1) : i)
                        .getTimeCreated().toEpochSecond();
                long lastMessageTimestamp =
                        messageReceived.getTimeCreated().toEpochSecond();
                String diffTime = Helper
                        .diffTime((lastMessageTimestamp - firstMessageTimestamp));

                for (; i > thisDotI; i--) {
                    Message message = list.get(i);
                    if (Helper.isMe(message.getAuthor())) continue;
                    String messageContent_ = Helper.getContent(message);
                    if (messageContent_.isEmpty()) continue;

                    char firstChar = messageContent_.charAt(0);
                    boolean isLetter = Character.isLetter(firstChar);
                    boolean isUppercase = Character.isUpperCase(firstChar);

                    if (firstWord && (isLetter && !isUppercase)) {
                        char[] chars = messageContent_.toCharArray();
                        chars[0] = Character.toUpperCase(firstChar);
                        messageContent_ = new String(chars);

                    } else if (!firstWord && (isLetter && isUppercase)) {
                        char[] chars = messageContent_.toCharArray();
                        chars[0] = Character.toLowerCase(firstChar);
                        messageContent_ = new String(chars);
                    }

                    CharSequence processed = checkForbiddenWord(messageContent_);
                    storyBuilder.append(processed);
                    if (i > thisDotI + 1) storyBuilder.append(' ');

                    if (firstWord) firstWord = false;
                }
                storyBuilder.append('.');

                // announcing the completed story
                StringBuilder announcementBuilder = new StringBuilder();
                String introduction =
                        INTRODUCTIONS[(int) (Math.random() * INTRODUCTIONS.length)];
                announcementBuilder
                        .append(introduction)
                        .append("\n\n")
                        .append("**").append(storyBuilder).append("**")
                        .append("\n\n")
                        .append("Time elapsed (between the first and the last messages): ")
                        .append(diffTime);
                channel.sendMessage(announcementBuilder).queue();
            });
        }
    }

    private static CharSequence checkForbiddenWord(String message) {
        for (String badWord : FORBIDDEN_WORDS) {
            if (message.equalsIgnoreCase(badWord)) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < message.length(); i++) {
                    sb.append('#');
                }

                return sb;
            }
        }

        return message;
    }
}
