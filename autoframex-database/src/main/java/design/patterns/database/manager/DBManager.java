package design.patterns.database.manager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.framework.config.data.DatabaseConfiguration;
import com.framework.exception.FrameworkException;

import design.patterns.database.core.DatabaseConnection;
import design.patterns.database.factory.DatabaseConnectionFactory;

/**
 * Registry manager to retrieve and manage database connections based on their configuration.
 * It utilizes the {@link DatabaseConfiguration} base interface to remain scalable
 * for any number of different databases.
 *
 * <h3>Connections are per thread</h3>
 * Under parallel execution one JDBC connection used by several test threads shares its
 * transaction state and interleaves their statements. Each thread therefore gets its own
 * connection to the database, opened on first use and reopened if it has gone stale.
 * Threads that finish leave their connection open until {@link #closeAllConnections()}
 * (or the static {@link #closeAll()}, which also runs when the JVM exits).
 *
 * <h3>Failures are never hidden</h3>
 * A connection or query failure throws a {@link FrameworkException} carrying the
 * {@link SQLException} as its cause. {@link #executeQuery} used to log the error and return
 * {@code null}, so a failed lookup surfaced later as an unrelated {@code NullPointerException}
 * or, worse, as "no data found".
 *
 * <h3>One manager per database, not per config class</h3>
 * Managers are keyed by JDBC URL and user, so two configuration classes pointing at the same
 * database share one, and one configuration class can no longer shadow a second database.
 */
public class DBManager {

    private static final Logger logger = LoggerFactory.getLogger(DBManager.class);

    // One DBManager per database (JDBC URL + user)
    private static final Map<String, DBManager> instances = new ConcurrentHashMap<>();
    private static final AtomicBoolean shutdownHookRegistered = new AtomicBoolean();

    private final DatabaseConfiguration config;
    private final Function<String, DatabaseConnection> connectionFactory;

    /** The calling thread's connection. */
    private final ThreadLocal<DatabaseConnection> connections = new ThreadLocal<>();
    /** Every connection this manager has opened, for the thread that closes them all. */
    private final Set<DatabaseConnection> open = ConcurrentHashMap.newKeySet();

    /**
     * Private constructor to prevent direct instantiation.
     */
    private DBManager(DatabaseConfiguration config) {
        this(config, DatabaseConnectionFactory::createConnection);
    }

    /** Package-private so tests can supply their own connections instead of a real database. */
    DBManager(DatabaseConfiguration config, Function<String, DatabaseConnection> connectionFactory) {
        this.config = config;
        this.connectionFactory = connectionFactory;
    }

    /**
     * Retrieves the DBManager for the database the provided configuration points at.
     *
     * @param config The database configuration to connect with
     * @return The {@link DBManager} for that JDBC URL and user (created on first use)
     */
    public static DBManager getInstance(DatabaseConfiguration config) {
        registerShutdownHook();
        return instances.computeIfAbsent(keyFor(config), key -> new DBManager(config));
    }

    /** Identity of the database a configuration points at. The password is deliberately not part of it. */
    static String keyFor(DatabaseConfiguration config) {
        return config.dbUrl() + "|" + config.dbUserName();
    }

    /**
     * Retrieves the calling thread's connection, opening one if it has none or its last one
     * has gone stale. Credentials come from the configuration.
     *
     * @return The active {@link DatabaseConnection} for this thread
     * @throws FrameworkException if the connection cannot be established
     */
    public DatabaseConnection getConnection() {
        DatabaseConnection current = connections.get();
        if (current != null && current.isConnected()) {
            return current;
        }
        if (current != null) {
            logger.debug("Dropping a stale database connection for thread {}", Thread.currentThread().getName());
            discard(current);
        }

        String dbUrl = config.dbUrl();
        try {
            logger.debug("Initializing new database connection for {} on thread {}...",
                    config.getClass().getSimpleName(), Thread.currentThread().getName());
            DatabaseConnection fresh = connectionFactory.apply(dbUrl);
            fresh.connect(dbUrl, config.dbUserName(), config.dbPassword());
            connections.set(fresh);
            open.add(fresh);
            return fresh;
        } catch (SQLException | RuntimeException e) {
            // The password is never part of this message; the URL is, because it is what you need to find the problem.
            throw new FrameworkException("Could not connect to the database at " + dbUrl, e);
        }
    }

    /**
     * Closes the calling thread's connection, if it has one.
     */
    public void closeConnection() {
        DatabaseConnection current = connections.get();
        connections.remove();
        if (current != null) {
            discard(current);
        }
    }

    /**
     * Closes every connection this manager has opened, whichever thread opened it. Call it once
     * when a suite ends. Other threads that are still running simply open a new one on next use.
     */
    public void closeAllConnections() {
        connections.remove();
        for (DatabaseConnection connection : open) {
            discard(connection);
        }
    }

    /** Closes every connection of every manager. Also registered to run when the JVM exits. */
    public static void closeAll() {
        instances.values().forEach(DBManager::closeAllConnections);
    }

    /**
     * Utility method: Executes a query on the calling thread's connection.
     *
     * @param query The SQL Query
     * @return The resulting {@link ResultSet}; never {@code null}
     * @throws FrameworkException if the query fails (the {@link SQLException} is the cause)
     */
    public ResultSet executeQuery(String query) {
        try {
            return getConnection().executeQuery(query);
        } catch (SQLException e) {
            throw new FrameworkException("Database query failed: " + query, e);
        }
    }

    /**
     * Utility method: Executes an INSERT, UPDATE or DELETE on the calling thread's connection.
     *
     * @param statement The SQL statement
     * @return The number of rows affected
     * @throws FrameworkException if the statement fails (the {@link SQLException} is the cause)
     */
    public int executeUpdate(String statement) {
        try {
            return getConnection().executeUpdate(statement);
        } catch (SQLException e) {
            throw new FrameworkException("Database update failed: " + statement, e);
        }
    }

    private void discard(DatabaseConnection connection) {
        open.remove(connection);
        try {
            connection.disconnect();
        } catch (RuntimeException e) {
            logger.warn("Error closing a database connection: {}", e.toString());
        }
    }

    private static void registerShutdownHook() {
        if (shutdownHookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(DBManager::closeAll, "DBManager-ShutdownHook"));
        }
    }
}
