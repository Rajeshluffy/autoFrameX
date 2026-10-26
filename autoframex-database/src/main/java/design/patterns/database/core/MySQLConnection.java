package design.patterns.database.core;

import java.sql.SQLException;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Concrete implementation of the {@link DatabaseConnection} for MySQL
 * databases.
 */
public class MySQLConnection extends AbstractDatabaseConnection {

    private static final Logger logger = LoggerFactory.getLogger(MySQLConnection.class);
    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";
    /** Connector/J's own default is no timeout at all. */
    static final String DEFAULT_CONNECT_TIMEOUT_MILLIS = "10000";

    @Override
    protected Properties connectionProperties(String url) {
        Properties properties = new Properties();
        if (url == null || !url.toLowerCase().contains("connecttimeout")) {
            properties.setProperty("connectTimeout", DEFAULT_CONNECT_TIMEOUT_MILLIS);
        }
        return properties;
    }

    @Override
    protected void registerDriver() throws SQLException {
        try {
            // Modern JDBC drivers auto-register, but loading the class explicitly
            // ensures compatibility with older environments or strict classloaders.
            Class.forName(DRIVER_CLASS);
            logger.debug("MySQL JDBC Driver registered successfully.");
        } catch (ClassNotFoundException e) {
            logger.error("MySQL JDBC Driver not found. Ensure the dependency is in your pom.xml.");
            throw new SQLException("MySQL JDBC Driver class not found: " + DRIVER_CLASS, e);
        }
    }
}
