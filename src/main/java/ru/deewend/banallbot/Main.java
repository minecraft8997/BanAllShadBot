package ru.deewend.banallbot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import ru.deewend.banallbot.database.Database;
import ru.deewend.banallbot.database.Leaderboards;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

@SuppressWarnings("FieldCanBeLocal")
public class Main {
    private static final File PROPERTIES_FILE = new File("bot.properties");
    private static final Object LOCK = new Object();

    private static String   TOKEN;
    private static long     GUILD_ID;
    private static long     CHANNEL_ID;
    private static String   BOT_STATUS_PLAYING;
    private static String   MESSAGE_TITLE;
    private static String   MESSAGE_DESCRIPTION;
    private static String[] MESSAGE_IMAGES;
    private static String   MESSAGE_FOOTER;
    private static String   MESSAGE_UNBANNED_TITLE;
    private static String   MESSAGE_UNBANNED_DESCRIPTION;
    private static String   MESSAGE_UNBANNED_FOOTER;
    private static String[] TOP_SECRET_IMAGES;
    private static int      BAN_PERIOD_SECONDS;
    public static long      ONE_WORD_STORY_CHANNEL_ID;

    private static JDA jda;
    private static volatile boolean IN_BANNED_STATE;

    @SuppressWarnings("BusyWait")
    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        if (!PROPERTIES_FILE.exists()) {
            properties.setProperty("token", "");
            properties.setProperty("guild_id", "");
            properties.setProperty("channel_id", "");
            properties.setProperty("bot_status_playing", "");
            properties.setProperty("message_title", "");
            properties.setProperty("message_description", "");
            properties.setProperty("message_images", "");
            properties.setProperty("message_footer", "");
            properties.setProperty("message_unbanned_title", "");
            properties.setProperty("message_unbanned_description", "");
            properties.setProperty("message_unbanned_footer", "");
            properties.setProperty("top_secret_images", "");
            properties.setProperty("ban_period_seconds", "");
            properties.setProperty("one_word_story_channel_id", "");

            //noinspection ResultOfMethodCallIgnored
            PROPERTIES_FILE.createNewFile();
            try (FileOutputStream fos = new FileOutputStream(PROPERTIES_FILE)) {
                properties.store(fos, "Just a comment");
            }

            return;
        }

        try (FileInputStream fis = new FileInputStream(PROPERTIES_FILE)) {
            properties.load(fis);
        }

        TOKEN = Objects.requireNonNull(
                properties.getProperty("token"));
        GUILD_ID = Long.parseLong(
                properties.getProperty("guild_id"));
        CHANNEL_ID = Long.parseLong(
                properties.getProperty("channel_id"));
        BOT_STATUS_PLAYING = Objects.requireNonNull(
                properties.getProperty("bot_status_playing"));
        MESSAGE_TITLE = Objects.requireNonNull(
                properties.getProperty("message_title"));
        MESSAGE_DESCRIPTION = Objects.requireNonNull(
                properties.getProperty("message_description"));
        MESSAGE_IMAGES = Objects.requireNonNull(
                properties.getProperty("message_images")).split(", ");
        MESSAGE_FOOTER = Objects.requireNonNull(
                properties.getProperty("message_footer"));
        MESSAGE_UNBANNED_TITLE = Objects.requireNonNull(
                properties.getProperty("message_unbanned_title"));
        MESSAGE_UNBANNED_DESCRIPTION = Objects.requireNonNull(
                properties.getProperty("message_unbanned_description"));
        MESSAGE_UNBANNED_FOOTER = Objects.requireNonNull(
                properties.getProperty("message_unbanned_footer"));
        TOP_SECRET_IMAGES = Objects.requireNonNull(
                properties.getProperty("message_unbanned_footer")).split(", ");
        BAN_PERIOD_SECONDS = Integer.parseInt(
                properties.getProperty("ban_period_seconds"));
        ONE_WORD_STORY_CHANNEL_ID = Long.parseLong(
                properties.getProperty("one_word_story_channel_id"));
        {
            String value;
            if ((value = properties.getProperty("shad_is_cool")) != null) {
                if (!value.equals("true")) {
                    System.out.println("\"shad_is_cool\" property exists but does not " +
                            "equal to \"true\". Are you going to say Shad isn't cool?");

                    return;
                }
            }
        }

        Database.getInstance().load();
        Database.getInstance().startSavingThread();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Database.getInstance().interruptSavingThread();
            try {
                Database.getInstance().save();
            } catch (IOException e) {
                System.err.println("Failed to save database on a JVM shutdown hook");
                e.printStackTrace();
            }
        }));

        jda = JDABuilder.createDefault(TOKEN)
                .setActivity(Activity.playing(BOT_STATUS_PLAYING))
                .build();

        /*
         * Will be called internally from OneWordStoryController.
         * This was done to prevent OneWordStoryConstructor handling messages
         * that could potentially get deleted by Controller unit.
         */
        //jda.addEventListener(new OneWordStoryConstructor());

        jda.addEventListener(new OneWordStoryController());
        //noinspection NullableProblems
        jda.addEventListener(new ListenerAdapter() {
            @Override
            public void onSlashCommand(SlashCommandEvent event) {
                switch (event.getCommandString()) {
                    case "/banall":
                        onBanAllCommand(event);
                        break;
                    case "/banall-leaderboard":
                        onLeaderboardCommand(event, true);
                        break;
                    case "/story-leaderboard":
                        onLeaderboardCommand(event, false);
                        break;
                    default:
                        event.reply("Unknown command, please contact deewend.").queue();
                }
            }

            private void onBanAllCommand(SlashCommandEvent event) {
                if (event.getChannel().getIdLong() != CHANNEL_ID) {
                    event.reply("Sorry, I can't ban " +
                            "anyone outside the #ban-all channel.").queue();

                    return;
                }

                synchronized (LOCK) {
                    if (IN_BANNED_STATE) {
                        event.reply("I am already about " +
                                "to Ban All! Please wait...").queue();

                        return;
                    } else {
                        IN_BANNED_STATE = true;

                        LOCK.notifyAll();
                    }
                }

                //noinspection ConstantConditions
                ((GuildChannel) event.getChannel()).getManager()
                        .putPermissionOverride(
                                event.getGuild().getPublicRole(),
                                null,
                                Collections.singletonList(Permission.MESSAGE_WRITE)
                        ).queue();

                User author = event.getUser();
                Database.getInstance().incrementTimesBanned(author);

                EmbedBuilder builder = new EmbedBuilder();
                builder.setColor(Color.RED);
                builder.setTitle(MESSAGE_TITLE);
                builder.setDescription(String.format(
                        MESSAGE_DESCRIPTION, author.getName()));
                builder.setImage(MESSAGE_IMAGES[(int)
                        (Math.random() * MESSAGE_IMAGES.length)]);
                builder.setFooter(MESSAGE_FOOTER);

                event.replyEmbeds(builder.build()).queue(unused -> {
                    if (Math.random() < 0.03D) {
                        event.getChannel().sendMessage(TOP_SECRET_IMAGES[(int)
                                (Math.random() * TOP_SECRET_IMAGES.length)]).queue();
                    }
                });
            }

            private void onLeaderboardCommand(SlashCommandEvent event, boolean banall) {
                OptionMapping offsetOption = event.getOption("offset");
                OptionMapping maxResultsOption = event.getOption("max-results");

                String leaderboardTitle = (banall ? "BanAll" : "One Word Story");
                long offset_ =
                        (offsetOption != null ? offsetOption.getAsLong() : 0);
                long maxResults_ =
                        (maxResultsOption != null ? maxResultsOption.getAsLong() : 20);

                if (offset_ < 0) {
                    event.reply("Negative offset.").queue();

                    return;
                }
                if (offset_ > Integer.MAX_VALUE) {
                    event.reply("Offset is INSANELY high.").queue();

                    return;
                }
                if (maxResults_ < 1 || maxResults_ > 50) {
                    event.reply("Max results value should be >= 1 and <= 50").queue();

                    return;
                }

                int offset = (int) offset_;
                int maxResults = (int) maxResults_;

                long start = System.currentTimeMillis();
                Object[] rendered = Leaderboards.getInstance().renderResults(
                        leaderboardTitle, offset, maxResults, event.getUser().getIdLong());

                EmbedBuilder builder = new EmbedBuilder();
                builder.setColor(banall ? Color.RED : Color.GREEN);
                builder.setTitle(leaderboardTitle + " Leaderboard");
                builder.setDescription((String) rendered[0]);
                builder.setFooter(
                        "Last updated: " + new Date((long) rendered[1]) + " | " +
                        "Entries total: " + rendered[2] + " | " +
                        "Offset: " + offset + " | " +
                        "Max results: " + maxResults + " | " +
                        "Rendered in " + (System.currentTimeMillis() - start) + "ms."
                );

                event.replyEmbeds(builder.build()).queue();
            }

            @Override
            public void onGuildReady(GuildReadyEvent event) {
                Guild guild = event.getGuild();
                if (guild.getIdLong() != GUILD_ID) {
                    System.out.println("Unknown guild id: " + event.getGuild().getIdLong());

                    return;
                }

                List<CommandData> commandList = new ArrayList<>();
                commandList.add(new CommandData("banall", "Bans all!"));

                commandList.add(addLeaderboardOptions(new CommandData(
                        "banall-leaderboard", "Prints BanAll Leaderboard")));

                commandList.add(addLeaderboardOptions(new CommandData("story-leaderboard",
                        "Prints One Word Story Leaderboard")));

                guild.updateCommands().addCommands(commandList).queue();
            }
        });

        EmbedBuilder builder = new EmbedBuilder();
        builder.setColor(Color.GREEN);
        builder.setTitle(MESSAGE_UNBANNED_TITLE);
        builder.setDescription(MESSAGE_UNBANNED_DESCRIPTION);
        builder.setFooter(MESSAGE_UNBANNED_FOOTER);
        MessageEmbed unbannedEmbed = builder.build();

        //noinspection InfiniteLoopStatement
        while (true) {
            synchronized (LOCK) {
                while (!IN_BANNED_STATE) {
                    LOCK.wait();
                }
            }

            Thread.sleep(BAN_PERIOD_SECONDS * 1000L);

            Guild guild = jda.getGuildById(GUILD_ID);
            synchronized (LOCK) {
                IN_BANNED_STATE = false;

                //noinspection ConstantConditions
                guild
                        .getTextChannelById(CHANNEL_ID)
                        .sendMessageEmbeds(unbannedEmbed)
                        .queue();

                //noinspection ConstantConditions
                guild.getGuildChannelById(CHANNEL_ID)
                        .getManager()
                        .putPermissionOverride(
                                guild.getPublicRole(),
                                Collections.singletonList(Permission.MESSAGE_WRITE),
                                null
                        ).queue();
            }
        }
    }

    private static CommandData addLeaderboardOptions(CommandData command) {
        return command
                .addOption(OptionType.INTEGER, "offset", "Must be >= 0")
                .addOption(OptionType.INTEGER, "max-results",
                        "Must be >= 1 and <= 50");
    }
}
