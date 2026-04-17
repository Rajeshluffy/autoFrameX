package design.patterns.database.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract implementation of {@link DatabaseConnection} providing boilerplate
 * logic for connection management, logging, and query execution.
 */
public abstract class AbstractDatabaseConnection implements DatabaseConnection {

    private static final Logger logger = Logger.getLogger(AbstractDatabaseConnection.class.getName());

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
            logger.fine("Connecting to database: " + url);
            connection = DriverManager.getConnection(url, username, password);
            logger.info("Database connection established successfully.");
            return connection;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to connect to the database: " + url, e);
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
                logger.log(Level.WARNING, "Error occurred while closing the database connection.", e);
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

        logger.fine("Executing query: " + query);
        try {
            // Note: Returning ResultSet leaves the Statement open.
            // In a more advanced robust layer, we might map this to List<Map<String,
            // Object>> or DTOs to close statements eagerly.
            Statement statement = connection.createStatement();
            return statement.executeQuery(query);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to execute query: " + query, e);
            throw e;
        }
    }

    @Override
    public int executeUpdate(String query) throws SQLException {
        if (!isConnected()) {
            throw new SQLException("Cannot execute update. Not connected to the database.");
        }

        logger.fine("Executing update: " + query);
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(query);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to execute update: " + query, e);
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
