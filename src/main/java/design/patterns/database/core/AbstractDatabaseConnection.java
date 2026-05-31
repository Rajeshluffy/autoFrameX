package design.patterns.database.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;

/**
 * Abstract implementation of {@link DatabaseConnection} providing boilerplate
 * logic for connection management, logging, and query execution.
 */
public abstract class AbstractDatabaseConnection implements DatabaseConnection {

    private static final Logger logger = LoggerFactory.getLogger(AbstractDatabaseConnection.class);

    protected Connection connection;

    // Abstract method for specific database drivers to register themselves if
    // needed
    protected abstract void registerDriver() throws SQLException;

    @Override
    public Connection connect(String url, String username, String password) throws SQLException {
        if (isConnected()) {
            logger.info("Already connected to the database.");
            return connection;
        }

        try {
            registerDriver();
            logger.debug("Connecting to database: " + url);
            connection = DriverManager.getConnection(url, username, password);
            logger.info("Database connection established successfully.");
            return connection;
        } catch (SQLException e) {
            logger.error("Failed to connect to the database: " + url, e);
            throw e;
        }
    }

    @Override
    public void disconnect() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    logger.info("Database connection closed successfully.");
                }
            } catch (SQLException e) {
                logger.warn("Error occurred while closing the database connection.", e);
            } finally {
                connection = null; // Ensure the reference is cleared
            }
        }
    }

    @Override
    public ResultSet executeQuery(String query) throws SQLException {
        if (!isConnected()) {
            throw new SQLException("Cannot execute query. Not connected to the database.");
        }

        logger.debug("Executing query: " + query);
        // CachedRowSet disconnects the data from the JDBC connection: the Statement
        // and underlying ResultSet are closed inside the try-with-resources block
        // while the populated CachedRowSet (which implements ResultSet) is returned
        // to the caller without keeping any JDBC resource open.
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(query)) {
            CachedRowSet crs = RowSetProvider.newFactory().createCachedRowSet();
            crs.populate(rs);
            return crs;
        } catch (SQLException e) {
            logger.error("Failed to execute query: " + query, e);
            throw e;
        }
    }

    @Override
    public int executeUpdate(String query) throws SQLException {
        if (!isConnected()) {
            throw new SQLException("Cannot execute update. Not connected to the database.");
        }

        logger.debug("Executing update: " + query);
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(query);
        } catch (SQLException e) {
            logger.error("Failed to execute update: " + query, e);
            throw e;
        }
    }

    @Override
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }
}
