package com.framework.config.data;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.framework.exception.FrameworkException;

/**
 * An unknown {@code execution.mode} used to become TARGETED: a typo such as "dataprovider" ran one account instead
 * of every row of the data file, and the build stayed green. Only an unset value may default.
 */
public class ExecutionModeTest {

	@Test
	public void theTwoModesParseInAnyCase() {
		Assert.assertEquals(ExecutionMode.from("targeted"), ExecutionMode.TARGETED);
		Assert.assertEquals(ExecutionMode.from("DATA_PROVIDER"), ExecutionMode.DATA_PROVIDER);
		Assert.assertEquals(ExecutionMode.from("  Data_Provider "), ExecutionMode.DATA_PROVIDER);
	}

	@Test
	public void aHyphenOrSpaceIsReadAsAnUnderscore() {
		Assert.assertEquals(ExecutionMode.from("data-provider"), ExecutionMode.DATA_PROVIDER);
		Assert.assertEquals(ExecutionMode.from("data provider"), ExecutionMode.DATA_PROVIDER);
	}

	@Test
	public void unsetMeansTargeted() {
		Assert.assertEquals(ExecutionMode.from(null), ExecutionMode.TARGETED);
		Assert.assertEquals(ExecutionMode.from(""), ExecutionMode.TARGETED);
		Assert.assertEquals(ExecutionMode.from("   "), ExecutionMode.TARGETED);
	}

	@Test
	public void aTypoFailsAndListsTheAllowedValues() {
		FrameworkException e = Assert.expectThrows(FrameworkException.class, () -> ExecutionMode.from("dataprovider"));
		Assert.assertTrue(e.getMessage().contains("dataprovider"), "names the bad value: " + e.getMessage());
		Assert.assertTrue(e.getMessage().contains("DATA_PROVIDER") && e.getMessage().contains("TARGETED"),
				"lists what is allowed: " + e.getMessage());
	}
}
