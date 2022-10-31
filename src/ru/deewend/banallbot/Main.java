package ru.deewend.banallbot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Collections;
import java.util.Objects;
import java.util.Properties;

@SuppressWarnings("FieldCanBeLocal")
public class Main {
    private static final File PROPERTIES_FILE = new File("bot.properties");
    private static final Object LOCK = new Object();

    private static String  TOKEN;
    private static long    GUILD_ID;
    private static long    CHANNEL_ID;
    private static String  MESSAGE_TITLE;
    private static String  MESSAGE_DESCRIPTION;
    private static String  MESSAGE_IMAGE;
    private static String  MESSAGE_FOOTER;
    private static String  MESSAGE_UNBANNED_TITLE;
    private static String  MESSAGE_UNBANNED_DESCRIPTION;
    private static String  MESSAGE_UNBANNED_FOOTER;
    private static int     BAN_PERIOD_SECONDS;

    private static volatile boolean IN_BANNED_STATE;

    @SuppressWarnings("BusyWait")
    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        if (!PROPERTIES_FILE.exists()) {
            properties.setProperty("token", "");
            properties.setProperty("guild_id", "");
            properties.setProperty("channel_id", "");
            properties.setProperty("message_title", "");
            properties.setProperty("message_description", "");
            properties.setProperty("message_image", "");
            properties.setProperty("message_footer", "");
            properties.setProperty("message_unbanned_title", "");
            properties.setProperty("message_unbanned_description", "");
            properties.setProperty("message_unbanned_footer", "");
            properties.setProperty("ban_period_seconds", "");

            //noinspection ResultOfMethodCallIgnored
            PROPERTIES_FILE.createNewFile();
            try (FileOutputStream fos = new FileOutputStream(PROPERTIES_FILE)) {
                properties.store(fos, "Just a comment");
            }
            System.out.println("Saved properties file");

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
        MESSAGE_TITLE = Objects.requireNonNull(
                properties.getProperty("message_title"));
        MESSAGE_DESCRIPTION = Objects.requireNonNull(
                properties.getProperty("message_description"));
        MESSAGE_IMAGE = Objects.requireNonNull(
                properties.getProperty("message_image"));
        MESSAGE_FOOTER = Objects.requireNonNull(
                properties.getProperty("message_footer"));
        MESSAGE_UNBANNED_TITLE = Objects.requireNonNull(
                properties.getProperty("message_unbanned_title"));
        MESSAGE_UNBANNED_DESCRIPTION = Objects.requireNonNull(
                properties.getProperty("message_unbanned_description"));
        MESSAGE_UNBANNED_FOOTER = Objects.requireNonNull(
                properties.getProperty("message_unbanned_footer"));
        BAN_PERIOD_SECONDS = Integer.parseInt(
                properties.getProperty("ban_period_seconds")
        );

        JDA jda = JDABuilder.createDefault(TOKEN)
                .setActivity(Activity.playing("[MCCH] Panda Anarchy. /BanAll"))
                .build();

        //noinspection NullableProblems
        jda.addEventListener(new ListenerAdapter() {
            @Override
            @SuppressWarnings("ConstantConditions")
            public void onSlashCommand(SlashCommandEvent event) {
                if (event.getChannel().getIdLong() != CHANNEL_ID) {
                    event.reply("Sorry, I can't ban " +
                            "anyone outside the <#1035970921602748478> channel").queue();

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

                ((GuildChannel) event.getChannel()).getManager()
                        .putPermissionOverride(
                                event.getGuild().getPublicRole(),
                                null,
                                Collections.singletonList(Permission.MESSAGE_WRITE)
                        ).queue();

                EmbedBuilder builder = new EmbedBuilder();
                builder.setColor(Color.RED);
                builder.setTitle(MESSAGE_TITLE);
                builder.setDescription(String.format(
                        MESSAGE_DESCRIPTION, event.getUser().getName()));
                builder.setImage(MESSAGE_IMAGE);
                builder.setFooter(MESSAGE_FOOTER);

                event.replyEmbeds(builder.build()).queue();
            }

            @Override
            public void onGuildReady(GuildReadyEvent event) {
                Guild guild = event.getGuild();
                if (guild.getIdLong() != GUILD_ID) {
                    System.out.println("Unknown guild id: " + event.getGuild().getIdLong());

                    return;
                }

                guild.updateCommands().addCommands(Collections.singletonList(
                        new CommandData("banall", "Bans all!"))).queue();
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
}
