package com.framework.config.data;

import org.aeonbits.owner.Config;
import org.aeonbits.owner.ConfigCache;

public class ConfigurationManager {

	/**
	 * Generic method to retrieve any configuration interface extending Owner
	 * Config.
	 * This provides a scalable approach for any project-specific configurations.
	 * 
	 * @param <T>         The configuration type
	 * @param configClass The Class object of the configuration interface
	 * @return A cached instance of the requested configuration
	 */
	public static <T extends Config> T getConfiguration(Class<T> configClass) {
		return ConfigCache.getOrCreate(configClass);
	}

	/**
	 * Returns a standalone {@link FrameworkConfiguration} proxy backed only by
	 * {@code frameworkConfig.properties}.
	 *
	 * @deprecated Since the unified config architecture (v5), every project's
	 *     {@link ProjectAppConfiguration} implementation already merges
	 *     {@code frameworkConfig.properties} via {@code @Config.Sources} +
	 *     {@code @LoadPolicy(LoadType.MERGE)}. {@link ProjectDirector} uses the
	 *     single project config object and no longer needs to call this method
	 *     separately. This method is retained only for code that explicitly needs
	 *     framework-only settings outside the normal test lifecycle.
	 */
	@Deprecated
	public static FrameworkConfiguration frameworkConfig() {
		return getConfiguration(FrameworkConfiguration.class);
	}
}
