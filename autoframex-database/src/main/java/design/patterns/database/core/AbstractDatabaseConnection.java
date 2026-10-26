package design.patterns.database.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Properties;
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

    /** Seconds a statement may run before it is cancelled; {@code 0} disables. System property {@code db.query.timeout.seconds}. */
    static final int DEFAULT_QUERY_TIMEOUT_SECONDS = 60;
    private static final String QUERY_TIMEOUT_PROPERTY = "db.query.timeout.seconds";

    protected Connection connection;
    private String connectedUrl;

    // Abstract method for specific database drivers to register themselves if
    // needed
    protected abstract void registerDriver() throws SQLException;

    /**
     * Extra driver properties for {@link #connect}, beyond the user and password. A driver that has no connect
     * timeout of its own (MySQL's is "none") can hang a test for as long as the operating system lets a TCP
     * connect last; a subclass sets one here.
     */
    protected Properties connectionProperties(String url) {
        return new Properties();
    }

    @Override
    public Connection connect(String url, String username, String password) throws SQLException {
        if (isConnected()) {
            if (java.util.Objects.equals(url, connectedUrl)) {
                logger.info("Already connected to the database.");
                return connection;
            }
            // Answering with the first connection would run the caller's statements against the wrong database.
            throw new IllegalStateException("Already connected to " + JdbcUrls.redact(connectedUrl)
                    + "; disconnect() before connecting to " + JdbcUrls.redact(url));
        }

        try {
            registerDriver();
            logger.debug("Connecting to database: " + JdbcUrls.redact(url));
            Properties properties = connectionProperties(url);
            if (username != null) {
                properties.setProperty("user", username);
            }
            if (password != null) {
                properties.setProperty("password", password);
            }
            connection = DriverManager.getConnection(url, properties);
            connectedUrl = url;
            logger.info("Database connection established successfully.");
            return connection;
        } catch (SQLException e) {
            logger.error("Failed to connect to the database: " + JdbcUrls.redact(url), e);
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
                connectedUrl = null;
            }
        }
    }

    @Override
    public ResultSet executeQuery(String query) throws SQLException {
        if (!isOpen()) {
            throw new SQLException("Cannot execute query. Not connected to the database.");
        }

        logger.debug("Executing query: " + query);
        // CachedRowSet disconnects the data from the JDBC connection: the Statement
        // and underlying ResultSet are closed inside the try-with-resources block
        // while the populated CachedRowSet (which implements ResultSet) is returned
        // to the caller without keeping any JDBC resource open.
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(queryTimeoutSeconds());
            try (ResultSet rs = statement.executeQuery(query)) {
                CachedRowSet crs = RowSetProvider.newFactory().createCachedRowSet();
                crs.populate(rs);
                return crs;
            }
        } catch (SQLException e) {
            logger.error("Failed to execute query: " + query, e);
            throw e;
        }
    }

    @Override
    public ResultSet executeQuery(String query, Object... parameters) throws SQLException {
        if (!isOpen()) {
            throw new SQLException("Cannot execute query. Not connected to the database.");
        }

        logger.debug("Executing query: {} ({} parameter(s))", query, parameters.length);
        try (PreparedStatement statement = prepare(query, parameters);
             ResultSet rs = statement.executeQuery()) {
            CachedRowSet crs = RowSetProvider.newFactory().createCachedRowSet();
            crs.populate(rs);
            return crs;
        } catch (SQLException e) {
            // The values are deliberately not logged: they are often exactly the credentials and personal data
            // a test is checking.
            logger.error("Failed to execute query: " + query, e);
            throw e;
        }
    }

    @Override
    public int executeUpdate(String query) throws SQLException {
        if (!isOpen()) {
            throw new SQLException("Cannot execute update. Not connected to the database.");
        }

        logger.debug("Executing update: " + query);
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(queryTimeoutSeconds());
            return statement.executeUpdate(query);
        } catch (SQLException e) {
            logger.error("Failed to execute update: " + query, e);
            throw e;
        }
    }

    @Override
    public int executeUpdate(String query, Object... parameters) throws SQLException {
        if (!isOpen()) {
            throw new SQLException("Cannot execute update. Not connected to the database.");
        }

        logger.debug("Executing update: {} ({} parameter(s))", query, parameters.length);
        try (PreparedStatement statement = prepare(query, parameters)) {
            return statement.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to execute update: " + query, e);
            throw e;
        }
    }

    private PreparedStatement prepare(String query, Object[] parameters) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(query);
        try {
            statement.setQueryTimeout(queryTimeoutSeconds());
            for (int i = 0; i < parameters.length; i++) {
                if (parameters[i] == null) {
                    statement.setNull(i + 1, Types.NULL);
                } else {
                    statement.setObject(i + 1, parameters[i]);
                }
            }
            return statement;
        } catch (SQLException | RuntimeException e) {
            statement.close();
            throw e;
        }
    }

    /**
     * Seconds a statement may run, from {@code db.query.timeout.seconds} (default 60, {@code 0} = no limit). A
     * statement against a database that has stopped answering used to block the test forever.
     */
    static int queryTimeoutSeconds() {
        String configured = System.getProperty(QUERY_TIMEOUT_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_QUERY_TIMEOUT_SECONDS;
        }
        try {
            int seconds = Integer.parseInt(configured.trim());
            if (seconds < 0) {
                throw new NumberFormatException("negative");
            }
            return seconds;
        } catch (NumberFormatException e) {
            throw new IllegalStateException(QUERY_TIMEOUT_PROPERTY + " must be a whole number of seconds (0 = no limit), got '"
                    + configured + "'");
        }
    }

    /** Whether the connection object is open. A local check: no round trip to the server. */
    private boolean isOpen() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
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
