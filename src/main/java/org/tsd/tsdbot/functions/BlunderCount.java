package org.tsd.tsdbot.functions;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.jibble.pircbot.User;
import org.tsd.tsdbot.Command;
import org.tsd.tsdbot.TSDBot;

import java.util.Random;

/**
 * Created by Joe on 5/24/14.
 */
@Singleton
public class BlunderCount extends MainFunction {

    private Random random;

    @Inject
    public BlunderCount(TSDBot bot, Random random) {
        super(bot);
        this.random = random;
    }

    @Override
    public void run(String channel, String sender, String ident, String text) {

        String[] cmdParts = text.split("\\s+");

        if(cmdParts.length == 1) {
            bot.sendMessage(channel, Command.BLUNDER_COUNT.getUsage());
        } else {
            String subCmd = cmdParts[1];
            if(subCmd.equals("count")) { // display the current blunder count
                bot.sendMessage(channel, "Current blunder count: " + TSDBot.blunderCount);
            } else if(subCmd.equals("+") // vv not correct but help em out anyway vv
                    || (cmdParts.length > 2 && cmdParts[1].equals("count") && cmdParts[2].equals("+"))) {
                if( (!bot.getUserFromNick(channel, sender).hasPriv(User.Priv.SUPEROP)) && random.nextDouble() < 0.05 ) {
                    // user is not a super op
                    bot.kick(channel, sender, "R-E-K-T, REKT REKT REKT!");
                    return;
                }

                String response = String.format(responses[random.nextInt(responses.length)]
                        + "Blunder count incremented to %d",++TSDBot.blunderCount);
                bot.sendMessage(channel, response);
            } else {
                bot.sendMessage(channel, Command.BLUNDER_COUNT.getUsage());
            }
        }
    }

    private static String[] responses = new String[]{
            "",                                     "I saw that too. ",
            "kek. ",                                "My sides are moving on their own. ",
            "My sides. ",                           "M-muh sides. ",
            "Wow. ",                                "No argument here. ",
            "*tip* ",                               "Shit I missed it. Ah well. ",
            "BLOWN. THE. FUCK. OUT. ",              "B - T - F - O. ",
            "Shrekt. ",                             "Rekt. ",
            "[blunders intensify] ",                "What a blunder. ",
            "Zim-zam status: flim-flammed. "
    };
}