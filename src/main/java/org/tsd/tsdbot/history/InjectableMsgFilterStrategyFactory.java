package org.tsd.tsdbot.history;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Joe on 9/21/2014.
 */
@Singleton
public class InjectableMsgFilterStrategyFactory {

    private static Logger logger = LoggerFactory.getLogger(InjectableMsgFilterStrategyFactory.class);

    @Inject
    protected Injector injector;

    public void injectStrategy(MessageFilterStrategy strat) {
        logger.info("Creating MessageFilterStrategy {}", strat.getClass().getName());
        injector.injectMembers(strat);
    }
}