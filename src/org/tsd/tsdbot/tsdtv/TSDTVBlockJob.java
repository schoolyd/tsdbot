package org.tsd.tsdbot.tsdtv;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tsd.tsdbot.functions.TSDTV;

import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedList;

/**
 * Created by Joe on 3/16/14.
 */
public class TSDTVBlockJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(TSDTVBlockJob.class);

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        TSDTV.TSDTVBlock blockInfo = new TSDTV.TSDTVBlock(jobExecutionContext.getJobDetail().getJobDataMap());
        try {
            TSDTV.getInstance().prepareScheduledBlock(blockInfo, 0);
        } catch (SQLException e) {
            logger.error("Error preparing scheduled block", e);
        }
    }
}
