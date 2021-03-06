package org.tsd.tsdbot.notifications;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import it.sauronsoftware.feed4j.FeedParser;
import it.sauronsoftware.feed4j.bean.Feed;
import it.sauronsoftware.feed4j.bean.FeedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsd.tsdbot.NotificationType;
import org.tsd.tsdbot.TSDBot;
import org.tsd.tsdbot.module.NotifierChannels;
import org.tsd.tsdbot.util.HtmlSanitizer;
import org.tsd.tsdbot.util.IRCUtil;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
public class DboNewsManager extends NotificationManager<DboNewsManager.DboNewsPost> {

    private static Logger logger = LoggerFactory.getLogger(DboNewsManager.class);

    private static final Pattern authorPattern = Pattern.compile("\\((.*?)\\d{2}:\\d{2}:\\d{2}\\s{1}GMT\\)",Pattern.DOTALL);
    private static final Pattern postIdPattern = Pattern.compile("(\\d+)");

    static {
        HtmlSanitizer.allowedTags = Pattern.compile("^()$");
        HtmlSanitizer.forbiddenTags = Pattern.compile("^(b|p|i|s|a|img|table|thead|tbody|tfoot|tr|th|td|dd|dl|dt|em|h1|h2|h3|h4|h5|h6|li|ul|ol|span|div|strike|strong|"
                + "sub|sup|pre|del|code|blockquote|strike|kbd|br|hr|area|map|object|embed|param|link|form|small|big|script|object|embed|link|style|form|input)$");
    }

    @Inject
    public DboNewsManager(TSDBot bot, @NotifierChannels Map notifierChannels) {
        super(bot, 5, true);
        this.channels = (List<String>) notifierChannels.get("dbon");
    }

    @Override
    public LinkedList<DboNewsPost> sweep() {
        LinkedList<DboNewsPost> notifications = new LinkedList<>();
        try {
            URL url = new URL("http://destiny.bungie.org/rss.xml");
            Feed feed = FeedParser.parse(url);

            int items = feed.getItemCount();
            DboNewsPost newsPost = null;
            for (int i = 0; i < Math.min(items,MAX_HISTORY); i++) {
                FeedItem item = feed.getItem(i);
                int postId = getPostNumFromLink(item.getLink().toString());
                if((!recentNotifications.isEmpty()) && postId <= recentNotifications.getFirst().getPostId()) break;
                newsPost = new DboNewsPost();
                newsPost.setPostId(postId);
                newsPost.setDate(item.getPubDate());
                newsPost.setTitle(item.getTitle());
                newsPost.setBody(item.getDescriptionAsText());
                newsPost.setAuthor(getAuthorFromBody(item.getDescriptionAsText()));
                
                notifications.addLast(newsPost);
            }
        } catch (Exception e) {
            logger.error("DboNewsManager sweep() error", e);
            bot.incrementBlunderCnt();
        }

        recentNotifications.addAll(0,notifications);
        trimHistory();
        return notifications;
    }

    @Override
    public NotificationType getNotificationType() {
        return NotificationType.DBO_NEWS;
    }

    private int getPostNumFromLink(String url) throws Exception {
        Matcher m = postIdPattern.matcher(url);
        while(m.find()) {
            return Integer.parseInt(m.group(1));
        }
        throw new Exception("Could not parse " + url + " for post ID");
    }

    private String getAuthorFromBody(String body) {
        String credit = body.substring(body.lastIndexOf("("));
        Matcher m = authorPattern.matcher(credit);
        while(m.find()) {
            return m.group(1);
        }
        return "Unknown";
    }

    public class DboNewsPost extends NotificationEntity {

        private int postId;
        private String author;
        private String title;
        private String body;

        public String getAuthor() {
            return author;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public int getPostId() {
            return postId;
        }

        public void setPostId(int postId) {
            this.postId = postId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        @Override
        public String getInline() {
            return "[DBO News] " + "(" + postId + ") " + author + " -- " + title + " -- " + IRCUtil.shortenUrl("http://destiny.bungie.org/n/" + postId);
        }

        @Override
        public String getPreview() {
            setOpened(true);
            return IRCUtil.trimToSingleMsg(body);
        }

        @Override
        public String[] getFullText() {
            setOpened(true);
            return IRCUtil.splitLongString(body);
        }

        @Override
        public String getKey() {
            return ""+postId;
        }

    }


}
