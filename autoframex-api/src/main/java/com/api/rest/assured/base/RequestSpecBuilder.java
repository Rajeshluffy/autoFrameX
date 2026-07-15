package com.api.rest.assured.base;

import java.util.HashMap;
import java.util.Map;
import static io.restassured.RestAssured.*;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

public class RequestSpecBuilder {

	private String baseUri;
	private String basePath;
	private String oAuthBasePath;
	private ContentType contentType;
	private ContentType accept;
	private Map<String, String> queryParams = new HashMap<>();
	private Map<String, String> headers = new HashMap<>();
	private String queryParamKey;
	private String queryParamValue;
	private String pathParamKey;
	private String pathParamValue;
	private String headerKey;
	private String headerValue;
	private String username;

	private String grantType;
	private String clientId;
	private String clientSecret;
	private String token;
	private String bearerToken;
	private String refreshToken;

	private String password;
	private RequestSpecification specification;
	private boolean preemptive;

	RequestAuthentication auth=	new RequestAuthentication();

	/**
	 * 
	 * @param baseUri
	 * @return
	 */
	public RequestSpecBuilder setBaseUri(String baseUri) {
		this.baseUri = baseUri;
		return this;
	}

	/**
	 * 
	 * @param basePath
	 * @return
	 */
	public RequestSpecBuilder setBasePath(String basePath) {
		this.basePath = basePath;
		return this;
	}
	/**
	 * 
	 * @param oAuthBasePath
	 * @return
	 */
	public RequestSpecBuilder setOauthBasePath(String oAuthBasePath) {
		this.oAuthBasePath = oAuthBasePath;
		return this;
	}

	/**
	 * 
	 * @param contentType
	 * @return
	 */
	public RequestSpecBuilder setContentType(ContentType contentType) {
		this.contentType = contentType;
		return this;
	}
	/**
	 * 
	 * @param accept
	 * @return
	 */
	public RequestSpecBuilder setAccept(ContentType accept) {
		this.accept = accept;
		return this;
	}

	/**
	 * 
	 * @param queryParams
	 * @return
	 */
	public RequestSpecBuilder setQueryParams(Map<String, String> queryParams) {
		this.queryParams = queryParams;
		return this;
	}

	/**
	 * 
	 * @param queryParamKey
	 * @return
	 */
	public RequestSpecBuilder setQueryParamKey(String queryParamKey) {
		this.queryParamKey = queryParamKey;
		return this;
	}

	/**
	 * 
	 * @param queryParamValue
	 * @return
	 */
	public RequestSpecBuilder setQueryParamValue(String queryParamValue) {
		this.queryParamValue = queryParamValue;
		return this;
	}

	/**
	 * 
	 * @param pathParamValue
	 * @return
	 */
	public RequestSpecBuilder setPathParamValue(String pathParamValue) {
		this.pathParamValue = pathParamValue;
		return this;
	}

	/**
	 * 
	 * @param pathParamKey
	 * @return
	 */
	public RequestSpecBuilder setPathParamKey(String pathParamKey) {
		this.pathParamKey = pathParamKey;
		return this;
	}

	/**
	 * 
	 * @param username
	 * @return
	 */
	public RequestSpecBuilder setUsername(String username) {
		this.username = username;
		return this;
	}
	/**
	 * 
	 * @param password
	 * @return
	 */
	public RequestSpecBuilder setPassword(String password) {
		this.password = password;
		return this;
	}

	/**
	 * 
	 * @param specification
	 * @return
	 */
	public RequestSpecBuilder setSpecification(RequestSpecification specification) {
		this.specification = specification;
		return this;
	}

	/**
	 * 
	 * @param headers
	 * @return
	 */
	public RequestSpecBuilder setHeaders(Map<String, String> headers) {
		this.headers = headers;
		return this;
	}

	/**
	 * 
	 * @param headerKey
	 * @return
	 */
	public RequestSpecBuilder setHeaderKey(String headerKey) {
		this.headerKey = headerKey;
		return this;
	}

	/**
	 * 
	 * @param headerValue
	 * @return
	 */
	public RequestSpecBuilder setHeaderValue(String headerValue) {
		this.headerValue = headerValue;
		return this;
	}

	/**
	 * 
	 * @param preemptive
	 * @return
	 */
	public RequestSpecBuilder setPreemptive(boolean preemptive) {
		this.preemptive = preemptive;
		return this;
	}

	/**
	 * 
	 * @param username
	 * @param password
	 * @return
	 */
	public RequestSpecBuilder setBasicAuth(String username, String password) {
		this.username = username;
		this.password = password; 
		return this;
	}
	/**
	 * @author rajesh
	 * @param baseUri
	 * @param oAuthBasePath
	 * @param grantType
	 * @param clientId
	 * @param clientSecret
	 * @param username
	 * @param password
	 * @return
	 */
	public RequestSpecBuilder setOAuth(String baseUri,String oAuthBasePath,String grantType, String clientId, String clientSecret, String username, String password) {
		this.baseUri =baseUri;
		this.oAuthBasePath =oAuthBasePath;
		this.grantType = grantType;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.username = username;
		this.password = password;

		auth.setOAuthBaseUrl(baseUri + oAuthBasePath);
		token = auth.getOAuthToken(grantType, clientId, clientSecret, username, password);

		if (token == null || token.isEmpty()) {
			throw new RuntimeException("OAuth token retrieval failed.");
		}
		return this;
	}

	public RequestSpecBuilder setBearerToken(String grantType, String clientId, String clientSecret, String refreshToken) {
		this.grantType = grantType;
		this.clientId = clientId;
		this.clientSecret = clientSecret;
		this.refreshToken= refreshToken;
		token =	auth.refreshOAuthToken(grantType, clientId, clientSecret, refreshToken);
		return this;
	}

	public RequestSpecBuilder setBearerToken(String token) {
		this.bearerToken = token;
		return this;
	}

	/**
	 * 
	 * @return
	 */
	public RequestSpecification build() {
		RequestSpecification requestSpecification = given();

		if (baseUri != null) {
			requestSpecification.baseUri(baseUri);
		}

		if (basePath != null) {
			requestSpecification.basePath(basePath);
		}
		// oAuthBasePath is only used during token acquisition (see setOAuth()).
		// It must NOT overwrite basePath on the main request spec.

		if (contentType != null) {
			requestSpecification.contentType(contentType);
		}

		if (accept != null) {
			requestSpecification.accept(accept);
		}

		if (queryParamKey != null && queryParamValue != null) {
			requestSpecification.queryParam(queryParamKey, queryParamValue);
		}

		if (pathParamKey != null && pathParamValue != null) {
			requestSpecification.pathParam(pathParamKey, pathParamValue);
		}

		if (username != null && password != null) {
			if (preemptive) {
				requestSpecification.auth().preemptive().basic(username, password);
			} else {
				requestSpecification.auth().basic(username, password);
			}
		}

		if (!queryParams.isEmpty()) {
			requestSpecification.queryParams(queryParams);
		}

		if (specification != null) {
			requestSpecification.spec(specification);
		}

		if (headerKey != null && headerValue != null) {
			requestSpecification.header(headerKey, headerValue);
		}

		if (!headers.isEmpty()) {
			requestSpecification.headers(headers);
		}

		if (token != null) {
			requestSpecification.auth().oauth2(token);
		}
		
		if (bearerToken != null) {
			requestSpecification.header("Authorization", "Bearer " + bearerToken);
		}

		return requestSpecification;
	}


}
