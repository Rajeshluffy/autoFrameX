package com.framework.config.data;

import org.aeonbits.owner.Config;

/**
 * Base Database Configuration interface. 
 * Any environment-specific configuration should extend this to be used with the scalable DBManager.
 */
public interface DatabaseConfiguration extends Config {
    String dbUrl();
    String dbUserName();
    String dbPassword();
}
