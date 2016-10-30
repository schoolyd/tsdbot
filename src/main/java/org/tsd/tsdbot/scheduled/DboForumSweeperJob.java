package org.tsd.tsdbot.scheduled;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import com.google.inject.Inject;
import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.dao.DaoManager;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.xerces.dom.DeferredElementNSImpl;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsd.tsdbot.database.JdbcConnectionProvider;
import org.tsd.tsdbot.markov.MarkovFileManager;
import org.tsd.tsdbot.markov.MarkovKey;
import org.tsd.tsdbot.model.dbo.forum.Post;
import org.tsd.tsdbot.util.HtmlSanitizer;
import org.tsd.tsdbot.util.MarkovUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DboForumSweeperJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(DboForumSweeperJob.class);

    private static final String latestPostsRss = "http://destiny.bungie.org/forum/index.php?mode=rss";
    private static final Pattern postIdPattern = Pattern.compile("(\\d+)");
    private static SimpleDateFormat dboSdf; //Thu, 20 Feb 2014 01:59:08 +0000

    static {
        dboSdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z");
        dboSdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        HtmlSanitizer.allowedTags = Pattern.compile("^()$");
        HtmlSanitizer.forbiddenTags = Pattern.compile("^(b|p|i|s|a|img|table|thead|tbody|tfoot|tr|th|td|dd|dl|dt|em|h1|h2|h3|h4|h5|h6|li|ul|ol|span|div|strike|strong|"
                + "sub|sup|pre|del|code|blockquote|strike|kbd|br|hr|area|map|object|embed|param|link|form|small|big|script|object|embed|link|style|form|input)$");
    }

    private final WebClient webClient;
    private final JdbcConnectionProvider connectionProvider;
    private final MarkovFileManager markovFileManager;

    @Inject
    public DboForumSweeperJob(WebClient webClient,
                              JdbcConnectionProvider connectionProvider,
                              MarkovFileManager markovFileManager) {
        this.webClient = webClient;
        this.connectionProvider = connectionProvider;
        this.markovFileManager = markovFileManager;
    }

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {

        try {
            final XmlPage rssPage = webClient.getPage(latestPostsRss);
            Document rssDoc = rssPage.getXmlDocument();
            NodeList nlist = rssDoc.getElementsByTagName("item");

            Post post = null;
            Dao<Post, Integer> postDao = DaoManager.createDao(connectionProvider.get(), Post.class);
            for(int i=0 ; i < nlist.getLength() ; i++) {
                Node n = nlist.item(i);
                if(n.getNodeType() == Node.ELEMENT_NODE) {
                    DeferredElementNSImpl e = (DeferredElementNSImpl)n;
                    int postId = getPostNumFromLink(getField(e, "guid"));

                    post = postDao.queryForId(postId);
                    boolean newPost = false;
                    if(post != null) {
                        logger.info("Post {} already exists in database", postId);
                    } else {
                        logger.info("Post {} is new, adding...", postId);
                        post = new Post(postId);
                        newPost = true;
                    }

                    post.setAuthor(getField(e, "dc:creator"));
                    post.setSubject(getField(e, "title"));
                    post.setDate(dboSdf.parse(getField(e, "pubDate")));
                    post.setBody(HtmlSanitizer.sanitize(getField(e, "content:encoded")));

                    postDao.createOrUpdate(post);

                    if(newPost) {
                        processMarkov(post);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Error sweeping DBO forum posts", e);
        }
    }

    private void processMarkov(Post post) throws IOException {
        String[] rawWords = post.getBody().split("\\s+");
        List<String> sanitizedWords = new LinkedList<>();
        String word;
        for (String rawWord : rawWords) {
            word = MarkovUtil.sanitize(rawWord);
            if (StringUtils.isNotBlank(word)) {
                sanitizedWords.add(word);
            }
        }

        String[] sanitizedWordsArray = sanitizedWords.toArray(new String[sanitizedWords.size()]);

        int keyLength = 2;
        MarkovKey key;
        for(int i = (keyLength-1) ; i < (sanitizedWordsArray.length-1) ; i++) {
            key = new MarkovKey(ArrayUtils.subarray(sanitizedWordsArray, i-(keyLength-1), i+1));
            markovFileManager.addToFile(post.getAuthor(), key, sanitizedWordsArray[i+1]);
        }
    }

    private String getField(Element e, String fieldName) {
        return e.getElementsByTagName(fieldName).item(0).getTextContent();
    }

    private int getPostNumFromLink(String url) throws Exception {
        Matcher m = postIdPattern.matcher(url);
        while(m.find()) {
            return Integer.parseInt(m.group(1));
        }
        throw new Exception("Could not parse " + url + " for post ID");
    }
}