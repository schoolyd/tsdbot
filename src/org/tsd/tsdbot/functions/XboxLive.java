package org.tsd.tsdbot.functions;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.jibble.pircbot.User;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsd.tsdbot.TSDBot;
import org.tsd.tsdbot.history.HistoryBuff;
import org.tsd.tsdbot.history.MessageFilter;
import org.tsd.tsdbot.history.MessageFilterStrategy;
import org.tsd.tsdbot.util.FuzzyLogic;
import org.tsd.tsdbot.util.IRCUtil;
import org.tsd.tsdbot.util.RelativeDate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Created by Joe on 5/24/14.
 */
@Singleton
public class XboxLive extends MainFunction {

    private static final Logger logger = LoggerFactory.getLogger(Printout.class);

    private static final SimpleDateFormat jsonTimestampFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS'Z'");

    // list of all friends for given XUID
    private static final String FRIENDS_TARGET = "https://xboxapi.com/v2/%d/friends";

    // status information for a given XUID
    private static final String PRESENCE_TARGET = "https://xboxapi.com/v2/%d/presence";

    // recent activity for a given XUID
    private static final String ACTIVITY_TARGET = "https://xboxapi.com/v2/%d/activity/recent";

    static {
        jsonTimestampFmt.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private final String xblApiKey; // App key to use API
    private final long xuid;      // XUID for TSD IRC account

    private TreeSet<Player> friendsList = new TreeSet<>(new Comparator<Player>() {
        @Override
        public int compare(Player o1, Player o2) {
            return o1.gamertag.compareToIgnoreCase(o2.gamertag);
        }
    });

    LoadingCache<Player, PlayerStatus> statusCache = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build(new CacheLoader<Player, PlayerStatus>() {
                @Override
                public PlayerStatus load(Player player) throws Exception {
                    return fetchPlayerStatus(player);
                }
            });

    @Inject
    public XboxLive(TSDBot bot, Properties properties) throws IOException, URISyntaxException {
        super(bot);
        xblApiKey = properties.getProperty("xbl.apiKey");
        xuid = Long.parseLong(properties.getProperty("xbl.xuid"));
        loadFriendsList();
    }

    @Override
    public void run(String channel, String sender, String ident, String text) {

        String[] cmdParts = text.split("\\s+");

        if(cmdParts.length == 1) try {

            HashMap<String, List<String>> onlinePlayers = new HashMap<>(); // game -> players
            for(Player player : friendsList) {
                PlayerStatus status = getCachedPlayerStatus(player);
                if (!status.state.equals(PlayerState.offline)) {
                    String game = status.currentGame;
                    if (!onlinePlayers.containsKey(game))
                        onlinePlayers.put(game, new LinkedList<String>());
                    onlinePlayers.get(game).add(player.gamertag);
                }
            }

            if(onlinePlayers.isEmpty()) {
                bot.sendMessage(channel, "All my friends are dead.");
            } else {
                StringBuilder sb = new StringBuilder();
                boolean firstGame = true;
                for (String game : onlinePlayers.keySet()) {
                    if (!firstGame) sb.append(" || ");
                    if(onlinePlayers.get(game).size() == 1) {
                        sb.append(onlinePlayers.get(game).get(0)).append(": ").append(game);
                    } else {
                        sb.append(game).append(": ");
                        sb.append(StringUtils.join(onlinePlayers.get(game), ", "));
                    }
                    firstGame = false;
                }

                bot.sendMessage(channel, sb.toString());
            }

        } catch (Exception e) {
            logger.error("Error getting XBL info", e);
            bot.sendMessage(channel, "Error getting XBL info");

        } else {

            String subCmd = cmdParts[1];

            if(subCmd.equals("reload")) {

                if(!bot.getUserFromNick(channel, sender).hasPriv(User.Priv.OP)) {
                    bot.sendMessage(channel, "Only ops can use .xbl reload");
                } else try {
                    loadFriendsList();
                    bot.sendMessage(channel, "Friends list reloaded successfully");
                } catch (Exception e) {
                    logger.error("Error reloading friends list", e);
                    bot.sendMessage(channel, "Error reloading friends list");
                }

            } else if(subCmd.equals("list")) {

                bot.sendMessage(channel, "Sending you my friends list, " + sender);
                StringBuilder sb = new StringBuilder();
                for(Player player : friendsList) {
                    if(sb.length() > 0) sb.append(", ");
                    sb.append(player.gamertag);
                }
                bot.sendMessage(sender, sb.toString());

            } else try {

                String gt = cmdParts[1];

                List<Player> matchedPlayers = FuzzyLogic.fuzzySubset(gt, friendsList, new FuzzyLogic.FuzzyVisitor<Player>() {
                    @Override
                    public String visit(Player o1) {
                        return o1.gamertag;
                    }
                });

                if(matchedPlayers.size() == 0) {
                    bot.sendMessage(channel, "Could not find gamertag matching \"" + gt + "\"... maybe I'm not following them?");
                } else if(matchedPlayers.size() > 1) {
                    StringBuilder msg = new StringBuilder();
                    msg.append("Found multiple gamertags matching \"").append(gt).append("\": ").append(StringUtils.join(matchedPlayers, ", "));
                    bot.sendMessage(channel, msg.toString());
                } else {
                    Player player = matchedPlayers.get(0);
                    PlayerStatus status = getCachedPlayerStatus(player);
                    bot.sendMessage(channel, status.toString());
                }

            } catch (Exception e) {
                logger.error("Error getting player status", e);
                bot.sendMessage(channel, "Error getting player status");
            }
        }
    }

    /**
     * Fetching functions
     */
    private String fetch(String target, Object... args) throws URISyntaxException, IOException {
        URIBuilder builder = new URIBuilder(String.format(target, args));
        URL url = new URL(builder.toString());
        URLConnection connection = url.openConnection();
        connection.addRequestProperty("X-AUTH", xblApiKey);
        String line;
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        return sb.toString();
    }

    private PlayerStatus fetchPlayerStatus(Player player) throws IOException, URISyntaxException {
        String playerStatusJson = fetch(PRESENCE_TARGET, player.xuid);
        return new PlayerStatus(player.gamertag, playerStatusJson);
    }

    private PlayerStatus getCachedPlayerStatus(Player player) throws IOException, URISyntaxException {
        try {
            return statusCache.get(player);
        } catch (ExecutionException e) {
            logger.error("Error getting cached activity for {}, defaulting to new fetch...", player.gamertag, e);
            return fetchPlayerStatus(player);
        }
    }

//    private PlayerActivity fetchPlayerActivity(Player player) throws IOException, URISyntaxException {
//        String playerActivityJson = fetch(ACTIVITY_TARGET, player.xuid);
//        return new PlayerActivity(playerActivityJson);
//    }

    private void loadFriendsList() throws IOException, URISyntaxException {
        friendsList.clear();
        String friendsJson = fetch(FRIENDS_TARGET, xuid);
        JSONArray list = new JSONArray(friendsJson);
        for (int i = 0; i < list.length(); i++) {
            JSONObject player = list.getJSONObject(i);
            Player p = new Player(player.getLong("id"), player.getString("Gamertag"));
            friendsList.add(p);
        }
    }

    /**
     * Wrapper classes
     */
    class Player {
        public long xuid;
        public String gamertag;

        Player(long xuid, String gamertag) {
            this.xuid = xuid;
            this.gamertag = gamertag;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Player player = (Player) o;

            if (xuid != player.xuid) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return (int) (xuid ^ (xuid >>> 32));
        }
    }

    class PlayerStatus {
        public long xuid;
        public String gamertag;
        public PlayerState state;
        public LastSeen lastSeen;
        public Device[] devices;
        public String currentGame;
        public String backgroundApp;

        public PlayerStatus(String gamertag, String json) {
            this.gamertag = gamertag;
            JSONObject playerStatus = new JSONObject(json);
            this.xuid = playerStatus.getLong("xuid");
            this.state = PlayerState.fromString(playerStatus.getString("state"));

            JSONObject ls = playerStatus.optJSONObject("lastSeen");
            if(state.equals(PlayerState.offline) && ls != null) try {
                this.lastSeen = new LastSeen(ls);
            } catch (Exception e) {
                logger.error("Error unmarshaling lastSeen info for {}", xuid, e);
            } else if(state.equals(PlayerState.online) || state.equals(PlayerState.away) || state.equals(PlayerState.busy)) try {
                JSONArray devicesArr = playerStatus.getJSONArray("devices");
                this.devices = new Device[devicesArr.length()];
                for(int i=0 ; i < devicesArr.length() ; i++) {
                    devices[i] = new Device(devicesArr.getJSONObject(i));
                }

                if(devices.length > 0) {
                    Device device = devices[0];
                    switch(device.platform) {
                        case xbone: {
                            if (device.titles[0].name.equals("Home") && device.titles[1] == null) {
                                currentGame = "Xbone Home";
                            } else if (device.titles.length == 3 && device.titles[1].name.equals("Home") && StringUtils.isNotEmpty(device.titles[2].name)) {
                                currentGame = device.titles[0].name;
                                backgroundApp = device.titles[2].name;
                            } else if (device.titles.length == 3 && device.titles[0].placement.equals("Background") && StringUtils.isNotEmpty(device.titles[1].name)) {
                                currentGame = device.titles[1].name;
                                backgroundApp = device.titles[2].name;
                            } else {
                                currentGame = device.titles[1].name;
                            }
                        }
                        case x360: {
                            currentGame = device.titles[0].name;
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Error unmarshaling devices info for {}", xuid, e);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(gamertag).append(" is ").append(state);

            if(state.equals(PlayerState.offline) && lastSeen != null) {
                sb.append(". ").append(lastSeen.toString());
            } else if(devices.length > 0) {
                Device device = devices[0];
                switch(device.platform) {
                    case xbone: {
                        if (currentGame.equals("Home") && StringUtils.isEmpty(backgroundApp)) {
                            sb.append(" on the Xbone home screen.");
                        } else {
                            sb.append(" playing ").append(currentGame).append(" (").append(device.platform.displayString).append(")");
                            if(StringUtils.isNotEmpty(backgroundApp))
                                sb.append(" with the ").append(backgroundApp).append(" app snapped in the background");
                        }
                        break;
                    }
                    case x360: {
                        sb.append(" playing ").append(currentGame).append(" (").append(device.platform.displayString).append(")");
                        break;
                    }
                }
            }
            return sb.toString();
        }
    }

    class LastSeen {
        public Platform platform;
        public String titleName;
        public Date time;

        public LastSeen(JSONObject jsonObject) throws ParseException {
            this.platform = Platform.fromFileString(jsonObject.getString("deviceType"));
            this.titleName = jsonObject.getString("titleName");
            this.time = jsonTimestampFmt.parse(jsonObject.getString("timestamp"));
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Last seen ").append(RelativeDate.getRelativeDate(time));
            if(platform.equals(Platform.xbone) && titleName.equals("Home")) {
                sb.append(" on the Xbone's home screen.");
            } else {
                sb.append(" playing ").append(titleName).append(" (").append(platform.displayString).append(")");
            }
            return sb.toString();
        }
    }

    class Device {
        public Platform platform;
        public Title[] titles;

        public Device(JSONObject jsonObject) throws ParseException {
            this.platform = Platform.fromFileString(jsonObject.getString("type"));
            JSONArray t = jsonObject.getJSONArray("titles");
            this.titles = new Title[t.length()];
            for(int i=0 ; i < t.length() ; i++) {
                titles[i] = new Title(t.getJSONObject(i));
            }
        }
    }

    class Title {
        public long id;
        public Activity activity;
        public String name;
        public String placement;
        public String state;
        public Date lastModified;

        public Title(JSONObject jsonObject) throws ParseException {
            this.id = jsonObject.getLong("id");
            this.name = jsonObject.getString("name");
            this.placement = jsonObject.getString("placement");
            this.state = jsonObject.getString("state");
            this.lastModified = jsonTimestampFmt.parse(jsonObject.getString("lastModified"));
            JSONObject act = jsonObject.optJSONObject("activity");
            if(act != null) this.activity = new Activity(act);
        }
    }

    class Activity {
        public String richPresence;

        public Activity(JSONObject jsonObject) {
            this.richPresence = jsonObject.getString("richPresence");
        }
    }

//    class PlayerActivity {
//        public Platform platform;
//        public String contentTitle;
//
//        // used to represent an offline person
//        public PlayerActivity() {}
//
//        public PlayerActivity(String json) {
//            JSONArray playerActivity = new JSONArray(json);
//            JSONObject activity = playerActivity.getJSONObject(0);
//            this.platform = Platform.fromFileString(activity.getString("platform"));
//            this.contentTitle = activity.getString("contentTitle");
//        }
//
//        public boolean isOffline() {
//            return contentTitle == null;
//        }
//    }

    enum PlayerState {
        online,
        away,
        busy,
        offline;
        public static PlayerState fromString(String s) {
            for(PlayerState ps : values()) {
                if(s.equalsIgnoreCase(ps.toString()))
                    return ps;
            }
            return null;
        }
    }

    enum Platform {
        xbone("Xbone", "XboxOne"),
        x360("Xbox 360", "Xbox360");

        public String displayString;
        public String fileString;

        Platform(String displayString, String fileString) {
            this.displayString = displayString;
            this.fileString = fileString;
        }

        public static Platform fromFileString(String fs) {
            for(Platform p : values()) {
                if(p.fileString.equals(fs))
                    return p;
            }
            return null;
        }
    }

}
