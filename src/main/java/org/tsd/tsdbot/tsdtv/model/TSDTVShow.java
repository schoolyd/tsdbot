package org.tsd.tsdbot.tsdtv.model;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsd.tsdbot.tsdtv.EpisodeNotFoundException;
import org.tsd.tsdbot.tsdtv.ShowNotFoundException;
import org.tsd.tsdbot.tsdtv.TSDTVConstants;
import org.tsd.tsdbot.util.fuzzy.FuzzyLogic;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

public class TSDTVShow implements Comparable<TSDTVShow> {

    private static final Logger log = LoggerFactory.getLogger(TSDTVShow.class);

    private static File defaultImg = null;

    private File directory;

    public TSDTVShow(File directory) {
        this.directory = directory;
    }

    public File getDirectory() {
        return directory;
    }

    public TreeSet<TSDTVEpisode> getOrderedEpisodes() {
        TreeSet<TSDTVEpisode> result = new TreeSet<>();
        for(File f : directory.listFiles()) {
            if(!f.getName().startsWith(".")) {
                result.add(new TSDTVEpisode(f, this));
            }
        }
        return result;
    }

    public LinkedList<TSDTVEpisode> getAllEpisodes() {
        LinkedList<TSDTVEpisode> result = new LinkedList<>();
        for(File f : directory.listFiles()) {
            if( (!f.getName().endsWith(".sh")) && (!f.getName().startsWith(".")) ) {
                result.add(new TSDTVEpisode(f, this));
            }
        }
        return result;
    }

    public TSDTVEpisode getEpisode(String query) throws ShowNotFoundException {
        List<TSDTVEpisode> matchingFiles = FuzzyLogic.fuzzySubset(
                query,
                getAllEpisodes(),
                TSDTVEpisode::getRawName);

        if(matchingFiles.size() == 0)
            throw new ShowNotFoundException("Could not find episode matching \"" + query + "\"");
        else if(matchingFiles.size() > 1) {
            StringBuilder sb = new StringBuilder();
            sb.append("Found multiple episodes matching \"").append(query).append("\":");
            for(TSDTVEpisode episode : matchingFiles)
                sb.append(" ").append(episode.getRawName());
            throw new ShowNotFoundException(sb.toString());
        } else {
            return matchingFiles.get(0);
        }
    }

    public TSDTVEpisode getEpisode(int episodeNumber) throws EpisodeNotFoundException {
        for(TSDTVEpisode episode : getAllEpisodes()) {
            if(episodeNumber == episode.getEpisodeNumber())
                return episode;
        }
        throw new EpisodeNotFoundException("Could not find episode number " + episodeNumber + " for show " + getRawName());
    }

    public TSDTVEpisode getRandomEpisode(Random random) {
        LinkedList<TSDTVEpisode> allEps = getAllEpisodes();
        return allEps.get(random.nextInt(allEps.size()));
    }

    public String getRawName() {
        return directory.getName();
    }

    public String getPrettyName() {
        return directory.getName().replaceAll("_", " ");
    }

    public File getQueueImage() {
        File img = new File(directory.getAbsolutePath() + "/" + TSDTVConstants.SHOW_IMG_NAME);
        if(!img.exists()) {
            if(defaultImg == null) try {
                defaultImg = File.createTempFile("defaultImg", ".jpg");
                try(    InputStream is = new URL("http://i.imgur.com/6HNzLdK.jpg").openStream();
                        FileOutputStream fos = new FileOutputStream(defaultImg)) {
                    IOUtils.copy(is, fos);
                }
            } catch (Exception e) {
                log.error("Error downloading default queue image", e);
            }
            return defaultImg;
        } else {
            return img;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TSDTVShow tsdtvShow = (TSDTVShow) o;

        if (!directory.equals(tsdtvShow.directory)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return directory.hashCode();
    }

    @Override
    public int compareTo(TSDTVShow o) {
        return getRawName().compareToIgnoreCase(o.getRawName());
    }
}
