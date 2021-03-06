package org.tsd.tsdbot.functions;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.jibble.pircbot.User;
import org.tsd.tsdbot.TSDBot;
import org.tsd.tsdbot.module.Function;
import org.tsd.tsdbot.util.AuthenticationUtil;
import org.tsd.tsdbot.util.IRCUtil;

@Singleton
@Function(initialRegex = "^\\.blacklist.*")
public class Blacklist extends MainFunctionImpl {

    private final AuthenticationUtil authenticationUtil;

    @Inject
    public Blacklist(TSDBot bot, AuthenticationUtil authenticationUtil) {
        super(bot);
        this.description = "Adds or removes a user from the bot's blacklist";
        this.usage = "USAGE: .blacklist [ add <user> | remove <user> ]";
        this.authenticationUtil = authenticationUtil;
    }

    @Override
    public void run(String channel, String sender, String ident, String text) {

        if(!authenticationUtil.userIsOwner(sender)) {
            bot.sendMessage(channel, "Only my owner can banish people to the shadow realm");
            return;
        }

        String[] cmdParts = text.split("\\s+");
        if(cmdParts.length != 3) {
            bot.sendMessage(channel, getUsage());
            return;
        }

        User user = bot.getUserFromNick(channel, cmdParts[2]);
        if(user == null) {
            bot.sendMessage(channel, "Could not find user named "+cmdParts[2]);
            return;
        }

        switch(cmdParts[1]) {
            case "add": {
                if(bot.addToBlacklist(user)) {
                    bot.sendMessage(channel, IRCUtil.getPrefixlessNick(user)+" has been sent to the shadow realm");
                } else {
                    bot.sendMessage(channel, IRCUtil.getPrefixlessNick(user)+" was already in the shadow realm");
                }
                break;
            }
            case "remove": {
                if(bot.removeFromBlacklist(user)) {
                    bot.sendMessage(channel, IRCUtil.getPrefixlessNick(user)+" has been freed from the shadow realm");
                } else {
                    bot.sendMessage(channel, IRCUtil.getPrefixlessNick(user)+" was not in the shadow realm");
                }
                break;
            }
            default: {
                bot.sendMessage(channel, getUsage());
            }
        }
    }
}
