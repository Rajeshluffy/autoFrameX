package com.leaftaps.testcases;

import org.testng.annotations.Test;

import com.framework.config.data.ConfigManager;
import com.framework.config.data.ConfigurationManager;
import com.framework.config.data.ProjectConfig;
import com.framework.testng.api.base.ProjectSpecificMethods;
import com.framework.testng.api.base.TestMetadata;
import com.leaftaps.config.data.LeafTapConfiguration;
import com.leaftaps.pages.LoginPage;

@TestMetadata(
		name        = "VerifyLogin",
		description = "Verify Login functionality with positive data",
		authors     = "Rajesh",
		category    = "Smoke"
)
public class TC001_VerifyLogin extends ProjectSpecificMethods {

	@Test
	public void runLogin() {
		ProjectConfig config = ConfigManager.getInstance().getConfig();
	 LeafTapConfiguration leafTapConfig = ConfigurationManager.getConfiguration(LeafTapConfiguration.class);

		LoginPage lp = new LoginPage();
		lp.enterUsername(leafTapConfig.loginUserName())
				.enterPassword(leafTapConfig.loginPassword())
				.clickLogin();
	}
}