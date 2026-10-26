package design.patterns.database.core;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Interface representing a generic database connection.
 * Provides the contract for standard database operations.
 */
public interface DatabaseConnection {

    /**
     * Connects to the database using the provided credentials.
     *
     * @param url      The JDBC connection URL
     * @param username The database username
     * @param password The database password
     * @return The established SQL {@link Connection}
     * @throws SQLException If a database access error occurs
     */
    Connection connect(String url, String username, String password) throws SQLException;

    /**
     * Disconnects from the current database, safely closing the connection.
     */
    void disconnect();

    /**
     * Executes a SELECT query and returns the {@link ResultSet}.
     * Note: Managing the lifecycle of the ResultSet (and corresponding Statement)
     * is typically offloaded to the caller or handled safely within scoped methods.
     *
     * @param query The SQL query to execute
     * @return The resulting {@link ResultSet}
     * @throws SQLException If a database access error occurs
     */
    ResultSet executeQuery(String query) throws SQLException;

    /**
     * Executes an INSERT, UPDATE, or DELETE query and returns the row count.
     *
     * @param query The SQL DML query to execute
     * @return The number of rows affected
     * @throws SQLException If a database access error occurs
     */
    int executeUpdate(String query) throws SQLException;

    /**
     * Executes a SELECT with {@code ?} placeholders and returns the rows, detached from the connection. Pass
     * values as parameters, never by building them into the SQL text: a value with a quote in it
     * ({@code O'Brien}) breaks a built-up statement, and a value from outside can change it.
     *
     * @throws SQLException if the statement fails, or the number of parameters does not match its placeholders
     */
    default ResultSet executeQuery(String query, Object... parameters) throws SQLException {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not support parameters");
    }

    /**
     * Executes an INSERT, UPDATE or DELETE with {@code ?} placeholders; see
     * {@link #executeQuery(String, Object...)} for why values go in as parameters.
     *
     * @return the number of rows affected
     */
    default int executeUpdate(String query, Object... parameters) throws SQLException {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not support parameters");
    }

    /**
     * Checks if the connection is currently open and valid.
     *
     * @return true if connected and valid; false otherwise
     */
    boolean isConnected();
}
