package com.api.rest.assured.base;

import java.util.Objects;

/**
 * Everything needed to get an OAuth access token. Immutable.
 *
 * <p>{@link #cacheKey()} says which settings share a cached token: the token URL, grant type, client id,
 * user and scope. The secrets are deliberately not part of it, so the key is safe to log.
 * {@link #toString()} leaves the secrets out as well.
 */
public final class OAuthSettings {

    private final String tokenUrl;
    private final String grantType;
    private final String clientId;
    private final String clientSecret;
    private final String username;
    private final String password;
    private final String scope;

    public OAuthSettings(String tokenUrl, String grantType, String clientId, String clientSecret,
            String username, String password, String scope) {
        this.tokenUrl = Objects.requireNonNull(tokenUrl, "tokenUrl");
        this.grantType = grantType;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.username = username;
        this.password = password;
        this.scope = scope;
    }

    public String tokenUrl()     { return tokenUrl; }
    public String grantType()    { return grantType; }
    public String clientId()     { return clientId; }
    public String clientSecret() { return clientSecret; }
    public String username()     { return username; }
    public String password()     { return password; }
    public String scope()        { return scope; }

    public String cacheKey() {
        return String.join("|", tokenUrl, String.valueOf(grantType), String.valueOf(clientId),
                String.valueOf(username), String.valueOf(scope));
    }

    @Override
    public String toString() {
        return "OAuthSettings{" + cacheKey() + "}";
    }
}
