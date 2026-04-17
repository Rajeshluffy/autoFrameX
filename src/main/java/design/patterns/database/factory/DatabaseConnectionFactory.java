package design.patterns.database.factory;

import design.patterns.database.core.DatabaseConnection;
import design.patterns.database.core.MySQLConnection;

/**
 * Factory class for creating {@link DatabaseConnection} instances.
 * Abstracts the instantiation logic based on the driver type.
 */
public class DatabaseConnectionFactory {

    /**
     * Creates a DatabaseConnection based on the JDBC URL or explicit type.
     * Currently supports MySQL, but easily extensible.
     *
     * @param url The JDBC URL to determine the database type
     * @return The appropriate {@link DatabaseConnection} implementation
     */
    public static DatabaseConnection createConnection(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("JDBC URL cannot be null or empty.");
        }

        String lowerUrl = url.toLowerCase();

        if (lowerUrl.startsWith("jdbc:mysql:")) {
            return new MySQLConnection();
        }
        // Add more databases here using Else-If blocks when needed (e.g., PostgreSQL,
        // Oracle)
        else {
            throw new IllegalArgumentException("Unsupported database type for URL: " + url +
                    "\nCurrent support is limited to MySQL.");
        }
    }
}
