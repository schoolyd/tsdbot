package org.tsd.tsdbot.database;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.j256.ormlite.jdbc.JdbcConnectionSource;
import com.j256.ormlite.table.DatabaseTable;
import com.j256.ormlite.table.TableUtils;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Set;

@Singleton
public class JdbcConnectionProvider implements Provider<JdbcConnectionSource> {

    private static final Logger logger = LoggerFactory.getLogger(JdbcConnectionProvider.class);

    private final String connectionString;

    @Inject
    public JdbcConnectionProvider(@DBConnectionString String connectionString) {
        this.connectionString = connectionString;
    }

    private JdbcConnectionSource jdbcConnectionSource;
    private boolean tablesLoaded = false;

    @Override
    public JdbcConnectionSource get() {

        if(jdbcConnectionSource == null || !(jdbcConnectionSource.isOpen())) try {

            logger.info("JdbcConnectionSource is null or closed, " +
                    "retrying with connectionString={}", connectionString);
            jdbcConnectionSource = new JdbcConnectionSource(connectionString);

            // scan the model directory for annotations and add tables if necessary
            if(!tablesLoaded) {
                Reflections modelReflect = new Reflections("org.tsd.tsdbot.model");
                Set<Class<?>> tables = modelReflect.getTypesAnnotatedWith(DatabaseTable.class);
                for (Class clazz : tables) {
                    logger.info("Creating table {}", clazz);
                    TableUtils.createTableIfNotExists(jdbcConnectionSource, clazz);
                }
                tablesLoaded = true;
            }

        } catch (SQLException e) {
            logger.error("Error creating JdbcConnectionSource", e);
        }

        return jdbcConnectionSource;
    }
}
