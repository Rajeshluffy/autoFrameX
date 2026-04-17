package design.patterns.database.core;

import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Concrete implementation of the {@link DatabaseConnection} for MySQL
 * databases.
 */
public class MySQLConnection extends AbstractDatabaseConnection {

    private static final Logger logger = Logger.getLogger(MySQLConnection.class.getName());
    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    @Override
    protected void registerDriver() throws SQLException {
        try {
            // Modern JDBC drivers auto-register, but loading the class explicitly
            // ensures compatibility with older environments or strict classloaders.
            Class.forName(DRIVER_CLASS);
            logger.fine("MySQL JDBC Driver registered successfully.");
        } catch (ClassNotFoundException e) {
            logger.severe("MySQL JDBC Driver not found. Ensure the dependency is in your pom.xml.");
            throw new SQLException("MySQL JDBC Driver class not found: " + DRIVER_CLASS, e);
        }
    }
}
