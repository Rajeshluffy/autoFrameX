package design.patterns.database.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * The JDBC layer of the module had no test: {@code DBManagerTest} fakes the connections, so nothing ran a real
 * statement. These run against an in-memory H2 database (a real JDBC driver and SQL engine, no server).
 *
 * <p>What they pin: the API took only raw SQL text, so a test that put a value in a query had to quote it itself
 * ({@code O'Brien} broke the statement and {@code x' OR '1'='1} changed it); a statement that never answered
 * hung the test forever; a second {@code connect} to another database was silently answered with the first
 * connection; and every query paid two extra {@code isValid} round trips.
 */
public class AbstractDatabaseConnectionTest {

    /** An {@link AbstractDatabaseConnection} over H2, plus access to its connection for the tests. */
    static final class H2Connection extends AbstractDatabaseConnection {
        final AtomicInteger validityChecks = new AtomicInteger();

        @Override
        protected void registerDriver() {
            // H2 registers itself
        }

        /** Counts the isValid() calls (each is a network round trip against a real server). */
        void countValidityChecks() {
            Connection real = connection;
            connection = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] { Connection.class },
                    (proxy, method, args) -> {
                        if (method.getName().equals("isValid")) {
                            validityChecks.incrementAndGet();
                        }
                        try {
                            return method.invoke(real, args);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }
    }

    private static int databaseNumber;
    private H2Connection db;
    private String url;

    @BeforeMethod
    public void openDatabase() throws SQLException {
        url = "jdbc:h2:mem:abstractDbTest" + (++databaseNumber) + ";DB_CLOSE_DELAY=-1";
        db = new H2Connection();
        db.connect(url, "sa", "");
        db.executeUpdate("CREATE TABLE people (id INT PRIMARY KEY, name VARCHAR(100), age INT)");
    }

    @AfterMethod(alwaysRun = true)
    public void closeDatabase() {
        System.clearProperty("db.query.timeout.seconds");
        db.disconnect();
    }

    private static List<String> names(ResultSet rows) throws SQLException {
        List<String> names = new ArrayList<>();
        while (rows.next()) {
            names.add(rows.getString("name"));
        }
        return names;
    }

    // ------------------------------------------------------------------ plain statements

    @Test
    public void aQueryResultStaysReadableAfterTheConnectionIsClosed() throws SQLException {
        db.executeUpdate("INSERT INTO people VALUES (1, 'Ada', 36)");

        ResultSet rows = db.executeQuery("SELECT * FROM people");
        db.disconnect();

        Assert.assertEquals(names(rows), List.of("Ada"));
    }

    @Test
    public void anUpdateReturnsTheNumberOfRowsItChanged() throws SQLException {
        db.executeUpdate("INSERT INTO people VALUES (1, 'Ada', 36)");
        db.executeUpdate("INSERT INTO people VALUES (2, 'Alan', 41)");

        Assert.assertEquals(db.executeUpdate("UPDATE people SET age = age + 1"), 2);
    }

    @Test
    public void aBadStatementThrowsTheSqlException() {
        Assert.expectThrows(SQLException.class, () -> db.executeQuery("SELECT * FROM no_such_table"));
    }

    @Test
    public void usingTheConnectionAfterDisconnectFailsLoudly() {
        db.disconnect();
        Assert.assertFalse(db.isConnected());
        Assert.expectThrows(SQLException.class, () -> db.executeQuery("SELECT 1"));
    }

    // ------------------------------------------------------------------ parameters

    @Test
    public void aValueWithAQuoteIsPassedAsAParameterNotSpliceIntoTheSql() throws SQLException {
        db.executeUpdate("INSERT INTO people VALUES (?, ?, ?)", 1, "O'Brien", 50);

        ResultSet rows = db.executeQuery("SELECT * FROM people WHERE name = ?", "O'Brien");

        Assert.assertEquals(names(rows), List.of("O'Brien"));
    }

    @Test
    public void anInjectionStringIsJustAValueThatMatchesNothing() throws SQLException {
        db.executeUpdate("INSERT INTO people VALUES (?, ?, ?)", 1, "Ada", 36);

        ResultSet rows = db.executeQuery("SELECT * FROM people WHERE name = ?", "x' OR '1'='1");

        Assert.assertEquals(names(rows), List.of(), "the payload must not change the statement");
    }

    @Test
    public void nullAndNumbersAreBoundByType() throws SQLException {
        db.executeUpdate("INSERT INTO people VALUES (?, ?, ?)", 1, null, 36);

        ResultSet rows = db.executeQuery("SELECT * FROM people WHERE name IS NULL AND age = ?", 36);

        Assert.assertTrue(rows.next());
        Assert.assertNull(rows.getString("name"));
    }

    @Test
    public void aParameterCountThatDoesNotMatchTheStatementFails() {
        Assert.expectThrows(SQLException.class, () -> db.executeQuery("SELECT * FROM people WHERE id = ?"));
        Assert.expectThrows(SQLException.class, () -> db.executeQuery("SELECT * FROM people WHERE id = ?", 1, 2));
    }

    // ------------------------------------------------------------------ a statement that never answers

    @Test
    public void aStatementThatRunsTooLongIsStoppedByTheQueryTimeout() {
        System.setProperty("db.query.timeout.seconds", "1");

        long started = System.nanoTime();
        Assert.expectThrows(SQLException.class, () -> db.executeQuery("SELECT COUNT(*) FROM SYSTEM_RANGE(1, 100000) a, SYSTEM_RANGE(1, 100000) b WHERE a.x + b.x = -1"));
        long seconds = (System.nanoTime() - started) / 1_000_000_000L;

        Assert.assertTrue(seconds < 20, "stopped after about a second, not left running: " + seconds + " s");
    }

    // ------------------------------------------------------------------ one connection, one database

    @Test
    public void connectingAgainToTheSameDatabaseReturnsTheOpenConnection() throws SQLException {
        Connection first = db.connect(url, "sa", "");
        Assert.assertSame(db.connect(url, "sa", ""), first);
    }

    @Test
    public void connectingToAnotherDatabaseWhileConnectedFailsInsteadOfAnsweringWithTheFirst() {
        IllegalStateException e = Assert.expectThrows(IllegalStateException.class,
                () -> db.connect("jdbc:h2:mem:someOtherDatabase", "sa", ""));

        Assert.assertTrue(e.getMessage().contains("disconnect"), e.getMessage());
    }

    // ------------------------------------------------------------------ cost

    @Test
    public void aQueryDoesNotPayExtraValidityRoundTrips() throws SQLException {
        db.countValidityChecks();

        db.executeQuery("SELECT 1");
        db.executeUpdate("INSERT INTO people VALUES (1, 'Ada', 36)");

        Assert.assertEquals(db.validityChecks.get(), 0,
                "isValid() is a network round trip; DBManager already checks staleness once per getConnection()");
    }

    // ------------------------------------------------------------------ credentials in URLs

    @Test
    public void credentialsInAJdbcUrlAreRedactedForLogsAndMessages() {
        Assert.assertEquals(JdbcUrls.redact("jdbc:mysql://host:3306/app?user=bob&password=hunter2&useSSL=true"),
                "jdbc:mysql://host:3306/app?user=bob&password=***&useSSL=true");
        Assert.assertEquals(JdbcUrls.redact("jdbc:mysql://bob:hunter2@host/app"), "jdbc:mysql://bob:***@host/app");
        Assert.assertEquals(JdbcUrls.redact("jdbc:h2:mem:x;PASSWORD=hunter2;USER=sa"), "jdbc:h2:mem:x;PASSWORD=***;USER=sa");
        Assert.assertEquals(JdbcUrls.redact("jdbc:mysql://host/app"), "jdbc:mysql://host/app", "nothing to hide, nothing changed");
        Assert.assertEquals(JdbcUrls.redact(null), "<none>");
    }
}
