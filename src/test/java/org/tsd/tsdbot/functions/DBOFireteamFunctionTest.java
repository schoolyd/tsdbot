package org.tsd.tsdbot.functions;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import org.jibble.pircbot.User;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.tsd.tsdbot.Bot;
import org.tsd.tsdbot.IntegTestUtils;
import org.tsd.tsdbot.TestBot;
import org.tsd.tsdbot.database.DBConnectionString;
import org.tsd.tsdbot.database.JdbcConnectionProvider;
import org.tsd.tsdbot.model.dboft.DboUser;
import org.tsd.tsdbot.model.dboft.Fireteam;
import org.tsd.tsdbot.model.dboft.Platform;

import java.sql.SQLException;
import java.util.Date;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by Joe on 3/29/2015.
 */
@RunWith(JukitoRunner.class)
public class DBOFireteamFunctionTest {

    private static final String channel = "#tsd";

    private boolean dataPopulated = false;

    @Before
    public void setup(JdbcConnectionSource connectionSource) throws SQLException {
        if(!dataPopulated) {

            Dao<DboUser, Integer> userDao = DaoManager.createDao(connectionSource, DboUser.class);
            DboUser user = new DboUser(1, "TestUser");
            userDao.create(user);

            Dao<Fireteam, Integer> fireteamDao = DaoManager.createDao(connectionSource, Fireteam.class);
            Fireteam fireteam = new Fireteam(1);
            fireteam.setPlatform(Platform.xb1);
            fireteam.setEventTime(new Date());
            fireteam.setCreator(user);
            fireteamDao.create(fireteam);

            dataPopulated = true;
        }
    }

    @Test
    public void test(Bot bot, JdbcConnectionSource connectionSource) throws SQLException {
        TestBot testBot = (TestBot)bot;
        Dao<Fireteam, Integer> dao = DaoManager.createDao(connectionSource, Fireteam.class);

        String lastMessage = sendMessageGetResponse(testBot, "Schooly_D", ".dboft subscribe someteam");
        assertTrue(lastMessage.toLowerCase().contains("must be an integer"));

        lastMessage = sendMessageGetResponse(testBot, "Schooly_D", ".dboft subscribe 2");
        assertTrue(lastMessage.toLowerCase().contains("could not find fireteam"));

        lastMessage = sendMessageGetResponse(testBot, "Schooly_D", ".dboft subscribe 1");
        assertTrue(lastMessage.toLowerCase().contains("now subscribed to"));
        assertTrue(dao.queryForId(1).isSubscribed());

        lastMessage = sendMessageGetResponse(testBot, "Schooly_D", ".dboft subscribe 1");
        assertTrue(lastMessage.toLowerCase().contains("already subscribed"));

        lastMessage = sendMessageGetResponse(testBot, "Schooly_D", ".dboft unsubscribe 2");
        assertTrue(lastMessage.toLowerCase().contains("could not find fireteam"));

        lastMessage = sendMessageGetResponse(testBot, "Schooly_D", ".dboft unsubscribe 1");
        assertTrue(lastMessage.toLowerCase().contains("only an op can"));

        lastMessage = sendMessageGetResponse(testBot, "OpUser", ".dboft unsubscribe 1");
        assertTrue(lastMessage.toLowerCase().contains("no longer subscribed to"));
        assertFalse(dao.queryForId(1).isSubscribed());

        lastMessage = sendMessageGetResponse(testBot, "OpUser", ".dboft unsubscribe 1");
        assertTrue(lastMessage.toLowerCase().contains("not currently subscribed"));
        assertFalse(dao.queryForId(1).isSubscribed());

    }

    private String sendMessageGetResponse(TestBot testBot, String user, String msg) {
        testBot.onMessage(channel, user, "ident", "hostname", msg);
        return testBot.getLastMessage(channel);
    }

    public static class Module extends JukitoModule {
        @Override
        protected void configureTest() {
            bind(String.class).annotatedWith(DBConnectionString.class).toInstance("jdbc:h2:mem:test");
            bind(JdbcConnectionSource.class).toProvider(JdbcConnectionProvider.class);
            TestBot testBot = new TestBot(channel, null);
            bind(Bot.class).toInstance(testBot);
            testBot.addChannelUser(channel, User.Priv.OP, "OpUser");
            IntegTestUtils.loadFunctions(binder(), DboFireteamFunction.class);
            requestInjection(testBot);
        }
    }
}