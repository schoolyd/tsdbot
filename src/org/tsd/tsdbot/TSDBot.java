package org.tsd.tsdbot;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jibble.pircbotm.PircBot;
import org.jibble.pircbotm.User;
import org.tsd.tsdbot.runnable.Strawpoll;
import org.tsd.tsdbot.util.IRCUtil;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Joe on 2/18/14.
 */
public class TSDBot extends PircBot implements Runnable {

    private static String chan;
    
    private Thread mainThread;

    private HashMap<NotificationManager.NotificationOrigin, NotificationManager> notificationManagers = new HashMap<>();

    private ExecutorService threadPool = Executors.newFixedThreadPool(10);
    private Strawpoll runningPoll;

    public TSDBot(String channel, String name) {
        chan = channel;

        setName(name);
        setAutoNickChange(true);
        setLogin("tsdbot");

        CloseableHttpClient httpClient = HttpClients.createMinimal();

        WebClient webClient = new WebClient(BrowserVersion.CHROME);
        webClient.getCookieManager().setCookiesEnabled(true);

        Twitter twitterClient = TwitterFactory.getSingleton();

        notificationManagers.put(NotificationManager.NotificationOrigin.HBO_FORUM, new HboForumManager(httpClient));
        notificationManagers.put(NotificationManager.NotificationOrigin.DBO_FORUM, new DboForumManager(webClient));
        notificationManagers.put(NotificationManager.NotificationOrigin.HBO_NEWS, new HboNewsManager());
        notificationManagers.put(NotificationManager.NotificationOrigin.DBO_NEWS, new DboNewsManager());
        notificationManagers.put(NotificationManager.NotificationOrigin.TWITTER, new TwitterManager(this,twitterClient));
        
        mainThread = new Thread(this);
        mainThread.start();
    }

    @Override
    public synchronized void onMessage(String channel, String sender, String login, String hostname, String message) {
        if(!message.startsWith(".")) return; //not a command, ignore
        String[] cmdParts = message.split("\\s+");

        Command command = Command.fromString(cmdParts[0]);
        if(command == null) return;

        switch(command) {
            case HBO_FORUM: omniPostCmd(command, cmdParts); break;
            case HBO_NEWS: omniPostCmd(command, cmdParts); break;
            case DBO_FORUM: omniPostCmd(command, cmdParts); break;
            case DBO_NEWS: omniPostCmd(command, cmdParts); break;
            case TOM_CRUISE: tc(cmdParts); break;
            case STRAWPOLL: poll(message.split(";")); break;
            case VOTE: vote(sender, login, cmdParts); break;
            case TWITTER: tw(sender, cmdParts); break;
        }

    }

    private void omniPostCmd(Command command, String[] cmdParts) {

        NotificationManager<NotificationEntity> mgr = notificationManagers.get(command.getOrigin());

        if(cmdParts.length == 1) {
            sendLine("USAGE: " + command.getCmd() + " [ list | pv [postId (optional)] ]");
        } else if(cmdParts[1].equals("list")) {
            for(NotificationEntity notification : mgr.history()) {
                sendLine(notification.getInline());
            }
        } else if(cmdParts[1].equals("pv")) {
            if(mgr.history().isEmpty()) {
                sendLine("No " + command.getOrigin().getDisplayString() +" posts in recent history");
            } else if(cmdParts.length == 2) {
                NotificationEntity mostRecent = mgr.history().getFirst();
                if(mostRecent.isOpened()) sendLine("Post " + mostRecent.getKey() + " has already been opened");
                else sendLine(mostRecent.getPreview());
            } else {
                String postKey = cmdParts[2].trim();
                LinkedList<NotificationEntity> ret = mgr.getNotificationByTail(postKey);
                if(ret.size() == 0) sendLine("Could not find " + command.getOrigin() + " post with ID " + postKey + " in recent history");
                else if(ret.size() > 1) {
                    String returnString = "Found multiple matching " + command.getOrigin() + " posts in recent history:";
                    for(NotificationEntity not : ret) returnString += (" " + not.getKey());
                    returnString += ". Help me out here";
                    sendLine(returnString);
                }
                else sendLine(ret.get(0).getPreview());
            }
        } else {
            sendLine("USAGE: " + command.getCmd() + " [ list | pv [postId (optional)] ]");
        }

    }

    private void tc(String[] cmdParts) {
        if(cmdParts.length == 1) {
            sendLine(TomCruise.getRandom());
        } else if(cmdParts[1].equals("quote")) {
            sendLine(TomCruise.getRandomQuote());
        } else if(cmdParts[1].equals("clip")) {
            sendLine(TomCruise.getRandomClip());
        } else {
            sendLine(".tc usage: .tc [ clip | quote ] (optional)");
        }
    }

    private void poll(String[] cmdParts) {
        if(runningPoll != null) {
            sendMessage(chan, "There is already a poll running. It will end in " + runningPoll.getMinutes() + " minute(s)");
            return;
        }

        if(cmdParts.length < 4) {
            sendMessage(chan,".poll usage: .poll <question> ; <duration (integer)> ; choice 1 ; choice 2 [ choice 3 ...]");
            return;
        }

        String question = cmdParts[0].substring(cmdParts[0].indexOf(" ") + 1).trim(); //  .poll This is a question
                                                                                      //        ^--->
        int minutes = Integer.parseInt(cmdParts[1].trim());
        String[] choices = new String[cmdParts.length-2];
        for(int i=0 ; i < choices.length ; i++) {
            choices[i] = cmdParts[i+2].trim();
        }

        try {
            runningPoll = new Strawpoll(
                    this,
                    question,
                    minutes,
                    choices
            );
            threadPool.submit(runningPoll);
        } catch (Exception e) {
            sendMessage(chan,e.getMessage());
        }
    }

    private void vote(String sender, String login, String[] cmdParts) {
        if(runningPoll == null) {
            sendMessage(chan,"There is no poll running");
            return;
        }

        if(cmdParts.length != 2) {
            sendMessage(chan,".vote <number of your choice>");
            return;
        }

        int selection = -1;
        try {
            selection = Integer.parseInt(cmdParts[1]);
        } catch (NumberFormatException nfe) {
            sendMessage(chan,".vote <number of your choice>");
            return;
        }

        String voteResult = runningPoll.castVote(login, selection);
        if(voteResult != null) {
            sendMessage(chan, voteResult + ", " + sender);
        }
    }

    public void tw(String sender, String[] cmdParts) {

        if(!getUserFromNick(sender).isOp()) {
            sendMessage(chan,"Only ops can use the twitter function");
            return;
        }

        TwitterManager mgr = (TwitterManager) notificationManagers.get(NotificationManager.NotificationOrigin.TWITTER);

        if(cmdParts.length == 1) {
            sendMessage(chan, "USAGE: .tw [ following | timeline | tweet <message> | follow <handle> | unfollow <handle> ]");
        } else {
            try {
                String subCmd = cmdParts[1];
                if(subCmd.equals("following")) {
                    for(String s : mgr.getFollowing()) sendMessage(chan,s);
                } else if(subCmd.equals("timeline")) {
                    if(mgr.history().isEmpty()) sendLine("I don't have any tweets in my recent history");
                    else for(TwitterManager.Tweet t : mgr.history()) sendLine(t.getInline());
                } else if(cmdParts.length < 3) {
                    sendLine("USAGE: .tw [ following | timeline | tweet <message> | follow <handle> | unfollow <handle> ]");
                } else if(subCmd.equals("tweet") ) {
                    String tweet = "";
                    for(int i=2 ; i < cmdParts.length ; i++) {
                        tweet += (cmdParts[i] + " ");
                    }
                    mgr.postTweet(tweet);
                } else if(subCmd.equals("reply")) {
                    String replyToString = cmdParts[2];
                    LinkedList<TwitterManager.Tweet> matchedTweets = mgr.getNotificationByTail(replyToString);
                    if(matchedTweets.size() == 0) sendLine("Could not find tweet with ID matching" + replyToString + " in recent history");
                    else if(matchedTweets.size() > 1) {
                        String returnString = "Found multiple matching Tweets in recent history:";
                        for(NotificationEntity not : matchedTweets) returnString += (" " + not.getKey());
                        returnString += ". Help me out here";
                        sendLine(returnString);
                    }
                    else {
                        String tweet = "";
                        for(int i=3 ; i < cmdParts.length ; i++) {
                            tweet += (cmdParts[i] + " ");
                        }
                        mgr.postReply(matchedTweets.get(0),tweet);
                    }
                } else if(subCmd.equals("follow")) {
                    mgr.follow(cmdParts[2]);
                } else if(subCmd.equals("unfollow")) {
                    mgr.unfollow(cmdParts[2]);
                } else {
                    sendLine("USAGE: .tw [ following | timeline | tweet <message> | follow <handle> | unfollow <handle> ]");
                }
            } catch (TwitterException t) {
                sendLine("Error: " + t.getMessage());
            }
        }
    }

    public void handlePollStart(String question, int minutes, TreeMap<Integer, String> choices) {
        String[] displayTable = new String[choices.size()+2];
        displayTable[0] = "NEW STRAWPOLL: " + question;
        for(Integer i : choices.keySet()) {
            displayTable[i] = i + ": " + choices.get(i);
        }
        displayTable[displayTable.length-1] = "The voting will end in " + minutes + " minute(s)";
        sendLines(displayTable);
    }

    public void handlePollResult(String question, HashMap<String, Integer> results) {
        String[] resultsTable = new String[results.size()+1];
        resultsTable[0] = question + " | RESULTS:";
        int i=1;
        for(String choice : results.keySet()) {
            resultsTable[i] = choice + ": " + results.get(choice);
            i++;
        }
        this.runningPoll = null;
        sendLines(resultsTable);
    }

    @Override
    public synchronized void run() {

        boolean firstPass = true; //TODO: use DB to avoid this

        while(true) {
            try {
                wait(120 * 1000); // check every 2 minutes
            } catch (InterruptedException e) {
                // something notified this thread, panic.blimp
            }

            try {
                for(NotificationManager<NotificationEntity> sweeper : notificationManagers.values()) {
                    for(NotificationEntity notification : sweeper.sweep()) {
                        if(!firstPass) sendLine(notification.getInline());
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            firstPass = false;
        }
    }

    public void sendLine(String line) {
        if(line.length() > 510) sendLines(IRCUtil.splitLongString(line));
        else sendMessage(chan, line);
    }
    
    public void sendLines(String[] lines) {
        for(String line : lines) sendMessage(chan, line);
    }

    private User getUserFromNick(String nick) {
        User user = null;
        String prefixlessNick;
        for(User u : getUsers(chan)) {
            prefixlessNick = u.getNick().replaceAll(u.getPrefix(),"");
            if(prefixlessNick.equals(nick)) {
                user = u;
                break;
            }
        }
        return user;
    }

    public enum Command {
        TOM_CRUISE(".tc", null),
        HBO_FORUM(".hbof", NotificationManager.NotificationOrigin.HBO_FORUM),
        HBO_NEWS(".hbon", NotificationManager.NotificationOrigin.HBO_NEWS),
        DBO_FORUM(".dbof", NotificationManager.NotificationOrigin.DBO_FORUM),
        DBO_NEWS(".dbon", NotificationManager.NotificationOrigin.DBO_NEWS),
        STRAWPOLL(".poll", null),
        VOTE(".vote", null),
        TWITTER(".tw", NotificationManager.NotificationOrigin.TWITTER);

        private String cmd;
        private NotificationManager.NotificationOrigin origin;

        Command(String cmd, NotificationManager.NotificationOrigin origin) {
            this.cmd = cmd;
            this.origin = origin;
        }

        public String getCmd() {
            return cmd;
        }

        public NotificationManager.NotificationOrigin getOrigin() {
            return origin;
        }

        public static Command fromString(String s) {
            for(Command a : values()) {
                if(a.cmd.equals(s)) return a;
            }
            return null;
        }

    }
}
