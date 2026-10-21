package design.patterns.database.manager;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.framework.config.data.DatabaseConfiguration;
import com.framework.exception.FrameworkException;

import design.patterns.database.core.DatabaseConnection;

/**
 * {@link DBManager} used to share one JDBC connection between all test threads, key its managers
 * by configuration class name, and turn every query failure into a logged {@code null}. These
 * tests use fake connections, so no database is needed.
 */
public class DBManagerTest {

	// -------------------------------------------------------------------------
	// per-thread connections
	// -------------------------------------------------------------------------

	@Test
	public void eachThreadGetsItsOwnConnectionAndKeepsUsingIt() throws Exception {
		FakeDatabase db = new FakeDatabase();
		DBManager manager = db.manager("jdbc:mysql://host/a", "user");

		DatabaseConnection mine = manager.getConnection();
		Assert.assertSame(manager.getConnection(), mine, "the same thread reuses its connection");

		List<DatabaseConnection> seenByOtherThread = Collections.synchronizedList(new ArrayList<>());
		Thread other = new Thread(() -> {
			seenByOtherThread.add(manager.getConnection());
			seenByOtherThread.add(manager.getConnection());
		});
		other.start();
		other.join();

		Assert.assertNotSame(seenByOtherThread.get(0), mine, "another thread must not share this thread's connection");
		Assert.assertSame(seenByOtherThread.get(0), seenByOtherThread.get(1), "...but reuses its own");
		Assert.assertEquals(db.created.get(), 2);
	}

	@Test
	public void aStaleConnectionIsReplacedNotReused() {
		FakeDatabase db = new FakeDatabase();
		DBManager manager = db.manager("jdbc:mysql://host/a", "user");

		FakeConnection first = (FakeConnection) manager.getConnection();
		first.alive.set(false); // the server dropped it

		FakeConnection second = (FakeConnection) manager.getConnection();
		Assert.assertNotSame(second, first);
		Assert.assertTrue(second.isConnected());
		Assert.assertEquals(first.disconnects.get(), 1, "the stale one is closed, not leaked");
	}

	@Test
	public void closeConnectionClosesOnlyTheCallingThreadsConnection() throws Exception {
		FakeDatabase db = new FakeDatabase();
		DBManager manager = db.manager("jdbc:mysql://host/a", "user");

		FakeConnection mine = (FakeConnection) manager.getConnection();
		FakeConnection[] theirs = new FakeConnection[1];
		Thread other = new Thread(() -> theirs[0] = (FakeConnection) manager.getConnection());
		other.start();
		other.join();

		manager.closeConnection();

		Assert.assertEquals(mine.disconnects.get(), 1);
		Assert.assertEquals(theirs[0].disconnects.get(), 0, "the other thread's connection is untouched");
	}

	@Test
	public void closeAllConnectionsClosesEveryThreadsConnection() throws Exception {
		FakeDatabase db = new FakeDatabase();
		DBManager manager = db.manager("jdbc:mysql://host/a", "user");

		FakeConnection mine = (FakeConnection) manager.getConnection();
		FakeConnection[] theirs = new FakeConnection[1];
		Thread other = new Thread(() -> theirs[0] = (FakeConnection) manager.getConnection());
		other.start();
		other.join();

		manager.closeAllConnections();

		Assert.assertEquals(mine.disconnects.get(), 1);
		Assert.assertEquals(theirs[0].disconnects.get(), 1, "a finished thread's connection must not stay open");
		Assert.assertNotSame(manager.getConnection(), mine, "after closing, the next use opens a new one");
	}

	// -------------------------------------------------------------------------
	// failures are not swallowed
	// -------------------------------------------------------------------------

	@Test
	public void aFailingQueryThrowsInsteadOfReturningNull() {
		FakeDatabase db = new FakeDatabase();
		db.queryFailure = new SQLException("Table 'leads' doesn't exist");
		DBManager manager = db.manager("jdbc:mysql://host/a", "user");

		FrameworkException failure = Assert.expectThrows(FrameworkException.class,
				() -> manager.executeQuery("SELECT PARTY_ID FROM leads"));

		Assert.assertTrue(failure.getMessage().contains("SELECT PARTY_ID FROM leads"), failure.getMessage());
		Assert.assertEquals(failure.getCause(), db.queryFailure, "the SQLException is kept as the cause");
	}

	@Test
	public void aFailingUpdateThrowsToo() {
		FakeDatabase db = new FakeDatabase();
		db.updateFailure = new SQLException("Duplicate entry");
		DBManager manager = db.manager("jdbc:mysql://host/a", "user");

		FrameworkException failure = Assert.expectThrows(FrameworkException.class,
				() -> manager.executeUpdate("INSERT INTO leads VALUES (1)"));
		Assert.assertEquals(failure.getCause(), db.updateFailure);
	}

	@Test
	public void aSuccessfulUpdateReturnsTheRowCount() {
		FakeDatabase db = new FakeDatabase();
		DBManager manager = db.manager("jdbc:mysql://host/a", "user");
		Assert.assertEquals(manager.executeUpdate("DELETE FROM leads"), 3);
	}

	@Test
	public void aConnectFailureThrowsAndNamesTheUrlButNotThePassword() {
		FakeDatabase db = new FakeDatabase();
		db.connectFailure = new SQLException("Access denied for user");
		DBManager manager = db.manager("jdbc:mysql://host/a", "user");

		FrameworkException failure = Assert.expectThrows(FrameworkException.class, manager::getConnection);

		Assert.assertTrue(failure.getMessage().contains("jdbc:mysql://host/a"), failure.getMessage());
		Assert.assertFalse(failure.getMessage().contains(FakeDatabase.PASSWORD), "the password must not be in the message");
		Assert.assertEquals(failure.getCause(), db.connectFailure);
	}

	@Test
	public void anUnsupportedUrlIsReportedAsAFailureToo() {
		FakeDatabase db = new FakeDatabase();
		db.factoryFailure = new IllegalArgumentException("Unsupported database type for URL: jdbc:oracle:thin:@x");
		DBManager manager = db.manager("jdbc:oracle:thin:@x", "user");

		FrameworkException failure = Assert.expectThrows(FrameworkException.class, manager::getConnection);
		Assert.assertTrue(failure.getCause() instanceof IllegalArgumentException);
	}

	// -------------------------------------------------------------------------
	// one manager per database
	// -------------------------------------------------------------------------

	@Test
	public void managersAreKeyedByDatabaseNotByConfigurationClass() {
		DatabaseConfiguration a1 = new FakeConfig("jdbc:mysql://host/a", "user");
		DatabaseConfiguration a2 = new FakeConfig("jdbc:mysql://host/a", "user");
		DatabaseConfiguration otherUrl = new FakeConfig("jdbc:mysql://host/b", "user");
		DatabaseConfiguration otherUser = new FakeConfig("jdbc:mysql://host/a", "admin");

		Assert.assertEquals(DBManager.keyFor(a1), DBManager.keyFor(a2), "same database: same manager");
		Assert.assertNotEquals(DBManager.keyFor(a1), DBManager.keyFor(otherUrl),
				"the same configuration class pointing at a second database must not be shadowed");
		Assert.assertNotEquals(DBManager.keyFor(a1), DBManager.keyFor(otherUser));
		Assert.assertFalse(DBManager.keyFor(a1).contains(FakeDatabase.PASSWORD), "the password is not part of the key");
		Assert.assertSame(DBManager.getInstance(a1), DBManager.getInstance(a2));
		Assert.assertNotSame(DBManager.getInstance(a1), DBManager.getInstance(otherUrl));
	}

	// -------------------------------------------------------------------------
	// fakes
	// -------------------------------------------------------------------------

	private static final class FakeConfig implements DatabaseConfiguration {
		private final String url;
		private final String user;

		FakeConfig(String url, String user) {
			this.url = url;
			this.user = user;
		}

		@Override
		public String dbUrl() {
			return url;
		}

		@Override
		public String dbUserName() {
			return user;
		}

		@Override
		public String dbPassword() {
			return FakeDatabase.PASSWORD;
		}
	}

	/** Hands out {@link FakeConnection}s and can be told to fail at each step. */
	private static final class FakeDatabase {
		static final String PASSWORD = "s3cr3t-pw";

		final AtomicInteger created = new AtomicInteger();
		volatile SQLException connectFailure;
		volatile SQLException queryFailure;
		volatile SQLException updateFailure;
		volatile RuntimeException factoryFailure;

		DBManager manager(String url, String user) {
			return new DBManager(new FakeConfig(url, user), requestedUrl -> {
				if (factoryFailure != null) {
					throw factoryFailure;
				}
				created.incrementAndGet();
				return new FakeConnection(this);
			});
		}
	}

	private static final class FakeConnection implements DatabaseConnection {
		final AtomicBoolean alive = new AtomicBoolean(false);
		final AtomicInteger disconnects = new AtomicInteger();
		private final FakeDatabase db;

		FakeConnection(FakeDatabase db) {
			this.db = db;
		}

		@Override
		public Connection connect(String url, String username, String password) throws SQLException {
			if (db.connectFailure != null) {
				throw db.connectFailure;
			}
			alive.set(true);
			return null;
		}

		@Override
		public void disconnect() {
			disconnects.incrementAndGet();
			alive.set(false);
		}

		@Override
		public ResultSet executeQuery(String query) throws SQLException {
			if (db.queryFailure != null) {
				throw db.queryFailure;
			}
			return null;
		}

		@Override
		public int executeUpdate(String query) throws SQLException {
			if (db.updateFailure != null) {
				throw db.updateFailure;
			}
			return 3;
		}

		@Override
		public boolean isConnected() {
			return alive.get();
		}
	}
}
