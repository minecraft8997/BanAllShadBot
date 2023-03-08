package ru.deewend.banallbot;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ru.deewend.banallbot.database.Database;

import java.util.List;

public class OneWordStoryController extends ListenerAdapter {
    private final OneWordStoryConstructor storyConstructor = new OneWordStoryConstructor();

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        TextChannel channel = event.getChannel();
        if (channel.getIdLong() != Main.ONE_WORD_STORY_CHANNEL_ID) return;

        Message messageReceived = event.getMessage();
        User author = messageReceived.getAuthor();
        if (Helper.isMe(author)) return;

        String messageContent = Helper.getContent(messageReceived);
        if (messageContent.isEmpty() || messageContent.startsWith("#?")) return;

        boolean isAdmin = Main.isAdministrator(author);
        byte commandResult = Helper.checkAdminCommand(isAdmin, messageContent);
        if (commandResult != Helper.RESULT_NOT_A_COMMAND) {
            switch (commandResult) {
                case Helper.RESULT_THERE_WAS_AN_ISSUE_WITH_THIS_COMMAND:
                    messageReceived.reply("There was " +
                            "an issue executing this command").queue();
                    break;
                case Helper.RESULT_EXECUTED_THE_COMMAND:
                    messageReceived.reply("Okay :thumbsup:").queue();
                    break;
                case Helper.RESULT_BINARY_ANSWER_YES:
                    messageReceived.reply("Yes").queue();
                    break;
                case Helper.RESULT_BINARY_ANSWER_NO:
                    messageReceived.reply("No").queue();
                    break;
                default:
                    messageReceived.reply("Error: no route for this result").queue();
                    break;
            }

            return;
        }

        if (isAdmin && Helper.areAdminRestrictionsOff()) {
            messageOk(event);

            return;
        }

        if (messageContent.split(" ").length > 1) {
            messageReceived.reply("Only one-word messages are allowed :(")
                    .queue(unused -> messageReceived.delete().queue());

            return;
        }

        channel.getHistory()
                .retrievePast(20)
                .queue(list -> {
                    if (checkCooldown(messageReceived, messageContent, list)) {
                        messageOk(event);
                    }
                });
    }

    private void messageOk(GuildMessageReceivedEvent event) {
        Database.getInstance().incrementWordsSent(event.getAuthor());

        storyConstructor.onGuildMessageReceived(event);
    }

    private static boolean checkCooldown(
            Message messageReceived,
            String messageContent,
            List<Message> lastMessages
    ) {
        User originalAuthor = messageReceived.getAuthor();
        long originalAuthorId = originalAuthor.getIdLong();

        int actualI = 0;
        int thisMessageActualI = -1;
        boolean violatedCooldown = false;
        long secondsLeft = Helper.DELTA_SECONDS_NAN;
        boolean showCooldownNotice = false;
        for (Message message : lastMessages) {
            if (Helper.isMe(message.getAuthor())) continue;
            if (thisMessageActualI == -1) {
                if (message.getIdLong() == messageReceived.getIdLong()) {
                    thisMessageActualI = actualI;
                }
                actualI++;

                continue;
            }

            if (message.getAuthor().getIdLong() == originalAuthorId) {
                boolean finishAfterChecking = false;
                if (messageContent.equals(".")) {// start point of the story
                    // do nothing, the user just ended previous story
                    // and has rights to contribute to a new one

                    // moreover, we can finish observing history as
                    // we've just noticed the start of the story and
                    // didn't detect any violations

                    break;
                } else if (messageContent.startsWith(".")) {
                    finishAfterChecking = true;
                }

                int cooldownSeconds = 30 * 60; // 30 minutes
                if (actualI == thisMessageActualI + 1) {
                    cooldownSeconds = 60 * 60; // 1 hour
                }

                long deltaSeconds = messageReceived.getTimeCreated()
                        .toEpochSecond() - message.getTimeCreated().toEpochSecond();
                if (deltaSeconds < cooldownSeconds) {
                    violatedCooldown = true;
                    secondsLeft = cooldownSeconds - deltaSeconds;
                    showCooldownNotice = (actualI == thisMessageActualI + 1);

                    break;
                }

                if (finishAfterChecking) break;
            }

            actualI++;
        }

        if (violatedCooldown) {
            String message = "This could be a fantastic continuation **" +
                    originalAuthor.getName() + "** but your cooldown timer (" +
                    Helper.diffTime(secondsLeft) + " left) is not over at this " +
                    "moment :(\n" +
                    "Please let others contribute to this story as well! " +
                    (showCooldownNotice ? "**Note:** your cooldown will be decreased by " +
                            "30 minutes when someone writes the next word here." : "");

            messageReceived.reply(message)
                    .queue(unused -> messageReceived.delete().queue());

            return false;
        }

        return true;
    }
}
