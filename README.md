# BanAll Shad Bot

![jenkins](https://img.shields.io/badge/Jenkins-What%20is%20that-red)
![build](https://img.shields.io/badge/Build-dying-red)
![code contains potatos](https://img.shields.io/badge/Code%20contains-potatos-blue)

This is the repository of a Discord bot I've written specially for
Shad's Discord server. It introduces our favourite /banall command
(though it's designed to be available only in a dedicated channel)
plus it helps to moderate the One Word Story channel and also is
able to compile many one-word messages into complete sentence(s).
Since the bot does not cache messages for further concatenating them,
it can be freely restarted while other folks are sending the next
messages. Pretty much many things are configurable via the bot.properties
file.

## A security notice on running the application
According to Snyk report, the application *might* be vulnerable to
CWE-378 (Creation of Temporary File With Insecure Permissions) which
still persists in Kotlin standard library (at least at the moment of
writing this README file). The bot is written in pure Java, however
the sole library I am using, JDA-Discord, internally utilizes OkHttp,
modern versions of which are written in Kotlin. The exact state whether
the application is indeed vulnerable is unknown, but to follow best
practices I would recommend including the following JVM flag to your
startup command to mitigate the issue:

`-Djava.io.tmpdir=the_directory_you_consider_safe_enough_for_temporary_files`

An example of a secure startup command would be
`java -Xmx128M -Djava.io.tmpdir=/home/deewend/BanAllShadBot/tmp/ -jar BanAllShadBot.jar`
where `/home/deewend/BanAllShadBot/` is, for example, the working directory
and `tmp` is a folder specially designed for possible temporary files
with properly configured permissions so its contents visible only for you.
If the machine is utilized only by you, probably it's safe to ignore this issue
completely, but I would still recommend you setting this flag just to be
sure everything is fine.

Also, I am not recommending running old bot versions before commit
[`65c8cf1`](https://github.com/minecraft8997/BanAllShadBot/commit/65c8cf1d829ef874229ac0060753006a24258b40).
According to Snyk report, there were some potential medium-scored vulnerabilities
coming from internal JDA-Discord dependencies.

## Building
This is a Gradle project, so it should be easy to import it to your
favourite Java IDE. To build the bot from the command line, do
`gradlew.bat build` (Windows) or `./gradlew build` (Unix). Requires
JDK 8. The binary should be located in the `./build/libs` folder.