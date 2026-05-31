package design.patterns.database.manager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.framework.config.data.DatabaseConfiguration;

import design.patterns.database.core.DatabaseConnection;
import design.patterns.database.factory.DatabaseConnectionFactory;

/**
 * Registry manager to securely retrieve and manage database
 * connections based on their configuration.
 * It utilizes the {@link DatabaseConfiguration} base interface to remain scalable
 * for any number of different databases.
 */
public class DBManager {

    private static final Logger logger = LoggerFactory.getLogger(DBManager.class);

    // Map to hold different DBManager instances based on configuration class name
    private static final Map<String, DBManager> instances = new ConcurrentHashMap<>();

    private DatabaseConnection connection;
    private final DatabaseConfiguration config;

    /**
     * Private constructor to prevent direct instantiation.
     */
    private DBManager(DatabaseConfiguration config) {
        this.config = config;
    }

    /**
     * Retrieves the instance of the DBManager specific to the provided configuration.
     * 
     * @param config The database configuration to connect with
     * @return The specific {@link DBManager} instance for that config.
     */
    public static DBManager getInstance(DatabaseConfiguration config) {
        return instances.computeIfAbsent(config.getClass().getName(), key -> new DBManager(config));
    }

    /**
     * Retrieves the active database connection, establishing one if necessary.
     * Uses credentials from the configuration file securely.
     *
     * @return The active {@link DatabaseConnection}
     */
    public synchronized DatabaseConnection getConnection() {
        if (connection == null || !connection.isConnected()) {
            logger.debug("Initializing new database connection for " + config.getClass().getSimpleName() + "...");
            try {
                String dbUrl = config.dbUrl();
                String dbUser = config.dbUserName();
                String dbPassword = config.dbPassword();

                connection = DatabaseConnectionFactory.createConnection(dbUrl);
                connection.connect(dbUrl, dbUser, dbPassword);
            } catch (SQLException e) {
                logger.error("Could not initialize standard database connection", e);
                throw new RuntimeException("Database connection initialization failed", e);
            }
        }
        return connection;
    }

    /**
     * Closes the active database connection if one exists.
     */
    public synchronized void closeConnection() {
        if (connection != null && connection.isConnected()) {
            connection.disconnect();
            connection = null;
        }
    }

    /**
     * Utility method: Executes a query using the managed connection.
     * 
     * @param query The SQL Query
     * @return The resulting {@link ResultSet}
     */
    public ResultSet executeQuery(String query) {
        try {
            return getConnection().executeQuery(query);
        } catch (SQLException e) {
            logger.error("Failed executing DBManager query: " + query, e);
            return null; // For backward compatibility with existing usages
        }
    }
}
