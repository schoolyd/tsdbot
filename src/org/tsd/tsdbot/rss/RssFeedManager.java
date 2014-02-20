package org.tsd.tsdbot.rss;

import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.xml.XmlPage;
import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;
import org.apache.http.client.HttpClient;
import org.tsd.tsdbot.NotificationEntity;
import org.tsd.tsdbot.NotificationManager;

import javax.naming.OperationNotSupportedException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

public class RssFeedManager extends NotificationManager<RssItem> {

    private URL rssUrl;

    public RssFeedManager(String rssUrl) throws MalformedURLException {
        this.rssUrl = new URL(rssUrl);
    }

    @Override
    public LinkedList<RssItem> sweep(HttpClient client) throws OperationNotSupportedException {
        throw new OperationNotSupportedException("Please use the WebClient version instead.");
    }

    @Override
    public LinkedList<RssItem> sweep(WebClient webClient) throws OperationNotSupportedException {

        webClient.getCookieManager().setCookiesEnabled(true);
        final XmlPage rssPage;

        LinkedList<RssItem> items = new LinkedList<>();

        try {
            rssPage = webClient.getPage(rssUrl);
            InputStream is = new ByteArrayInputStream(rssPage.getContent().getBytes());


            SyndFeedInput input = new SyndFeedInput();
            SyndFeed feed = input.build(new XmlReader(is));

            for (Object objEntry: feed.getEntries()) {

                try {
                    SyndEntry entry = (SyndEntry) objEntry;
                    RssItem item = syndToRss(entry);
                    items.add(item);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        } catch (IOException | FeedException e) {
            e.printStackTrace();
        }

        return items;

    }

    private RssItem syndToRss(SyndEntry entry) throws MalformedURLException {
        RssItem item = new RssItem();
        item.setDate(entry.getPublishedDate());
        item.setTitle(entry.getTitle());
        SyndContent description = entry.getDescription();
        List contents = entry.getContents();

        if (contents != null && !contents.isEmpty()) {
            SyndContent cont = (SyndContent) contents.get(0);
            item.setContent(cont.getValue());
        }

        if (description != null) {
            item.setDescription(entry.getDescription().getValue());
        }

        item.setLink(new URL(entry.getLink()));

        return item;
    }

    @Override
    public List<RssItem> history() {
        return null;
    }

    @Override
    public NotificationEntity expand(String key) {
        return null;
    }
}