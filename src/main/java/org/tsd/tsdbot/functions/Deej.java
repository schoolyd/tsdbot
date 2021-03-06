package org.tsd.tsdbot.functions;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.tsd.tsdbot.TSDBot;
import org.tsd.tsdbot.history.HistoryBuff;
import org.tsd.tsdbot.history.filter.MessageFilter;
import org.tsd.tsdbot.history.filter.NoBotsStrategy;
import org.tsd.tsdbot.history.filter.NoCommandsStrategy;
import org.tsd.tsdbot.history.filter.NoURLsStrategy;
import org.tsd.tsdbot.module.Function;

import java.util.Random;

@Singleton
@Function(initialRegex = "^\\.deej$")
public class Deej extends MainFunctionImpl {

    private HistoryBuff historyBuff;
    private Random random;

    @Inject
    public Deej(TSDBot bot,
                HistoryBuff historyBuff,
                Random random) {
        super(bot);
        this.description = "DeeJ utility. Picks a random line from the channel history and makes it all dramatic and shit";
        this.usage = "USAGE: .deej";
        this.historyBuff = historyBuff;
        this.random = random;
    }

    @Override
    public void run(String channel, String sender, String ident, String text) {
        HistoryBuff.Message chosen = historyBuff.getRandomFilteredMessage(
                channel,
                null,
                MessageFilter.create()
                        .addFilter(new NoCommandsStrategy())
                        .addFilter(new NoBotsStrategy())
                        .addFilter(new NoURLsStrategy())
        );

        if(chosen != null) {
            // return the deej-formatted selected message
            bot.sendMessage(channel, String.format(formats[random.nextInt(formats.length)], chosen.text));
        }
    }

    private static final String[] formats = new String[] {
            "Fear not, Guardians: %s",
            "But be wary, Guardians of our city: %s",
            "The scribes of our city, stewards of the lost knowledge from our Golden Age, have uncovered a " +
                    "mysterious tome whose pages are all empty but for one mysterious line: \"%s\"",
            "Rejoice, Guardians! %s",
            "The spirits and specters from our bygone era of prosperity remind us: %s",
            "A message appears written in the amber skies above Earth's last city, at once a harbinger of caution " +
                    "and hope for all whose light shines bright against the darkness: \"%s\""
    };
}
