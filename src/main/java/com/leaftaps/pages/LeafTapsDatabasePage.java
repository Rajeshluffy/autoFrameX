package com.leaftaps.pages;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.framework.config.data.ConfigurationManager;
import com.leaftaps.config.data.LeafTapConfiguration;

import design.patterns.database.manager.DBManager;

/**
 * Utility class for Database operations.
 * Refactored to use the robust {@link DBManager} and Factory patterns.
 */
public class LeafTapsDatabasePage {

	private static final Logger logger = Logger.getLogger(LeafTapsDatabasePage.class.getName());

	/**
	 * Retrieves a random Lead ID from the database using configurations
	 * defined in the properties file.
	 *
	 * @return A random PARTY_ID as a String, or null if an error occurs.
	 */
	public static String getRandomLeadID() {
		String query = ConfigurationManager.getConfiguration(LeafTapConfiguration.class).dbQuery();
		String leadID = null;

		try {
			ResultSet rs = DBManager.getInstance(ConfigurationManager.getConfiguration(LeafTapConfiguration.class)).executeQuery(query);
			if (rs != null && rs.next()) {
				leadID = rs.getString("PARTY_ID");
				logger.fine("Successfully fetched random lead ID: " + leadID);
			}
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Failed to fetch random lead ID from Database", e);
		}

		return leadID;
	}
}