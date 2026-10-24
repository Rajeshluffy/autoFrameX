package com.api.rest.assured.base;

import java.util.HashMap;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.RequestSpecification;

/**
 * One builder is shared by a whole service object, and each call adds its own query parameter, path
 * parameter or header with the single-slot setters. Those used to stay set, so after one lookup by
 * {@code sysparm_query} every later create, update or delete carried that parameter too.
 */
public class RequestSpecBuilderStateTest {

	private static FilterableRequestSpecification view(RequestSpecification spec) {
		return (FilterableRequestSpecification) spec;
	}

	private static RequestSpecBuilder builder() {
		return new RequestSpecBuilder().setBaseUri("http://localhost:1");
	}

	@Test
	public void aQueryParameterAppliesToTheNextBuildOnly() {
		RequestSpecBuilder builder = builder();

		RequestSpecification lookup = builder.setQueryParamKey("sysparm_query").setQueryParamValue("number=INC1").build();
		RequestSpecification create = builder.build();

		Assert.assertEquals(view(lookup).getQueryParams().get("sysparm_query"), "number=INC1");
		Assert.assertFalse(view(create).getQueryParams().containsKey("sysparm_query"),
				"the next request built from the same builder must not inherit the lookup's query parameter");
	}

	@Test
	public void aHeaderAppliesToTheNextBuildOnly() {
		RequestSpecBuilder builder = builder();

		RequestSpecification first = builder.setHeaderKey("x-requested-with").setHeaderValue("XMLHttpRequest").build();
		RequestSpecification second = builder.build();

		Assert.assertTrue(view(first).getHeaders().hasHeaderWithName("x-requested-with"));
		Assert.assertFalse(view(second).getHeaders().hasHeaderWithName("x-requested-with"));
	}

	@Test
	public void aPathParameterAppliesToTheNextBuildOnly() {
		RequestSpecBuilder builder = builder();

		RequestSpecification first = builder.setPathParamKey("id").setPathParamValue("42").build();
		RequestSpecification second = builder.build();

		Assert.assertEquals(view(first).getNamedPathParams().get("id"), "42");
		Assert.assertTrue(view(second).getNamedPathParams().isEmpty());
	}

	@Test
	public void settingsThatDescribeTheServiceStayForEveryBuild() {
		RequestSpecBuilder builder = builder().setBasePath("/api/now/table").setBearerToken("abc");

		builder.setQueryParamKey("k").setQueryParamValue("v").build();
		FilterableRequestSpecification second = view(builder.build());

		Assert.assertEquals(second.getBaseUri(), "http://localhost:1");
		Assert.assertEquals(second.getBasePath(), "/api/now/table");
		Assert.assertEquals(second.getHeaders().getValue("Authorization"), "Bearer abc");
	}

	@Test
	public void nullMapsMeanNoParametersInsteadOfAFailureAtBuildTime() {
		RequestSpecification spec = builder().setQueryParams(null).setHeaders(null).build();

		Assert.assertNotNull(spec);
		Assert.assertTrue(view(spec).getQueryParams().isEmpty());
	}

	@Test
	public void theMapsAreCopiedSoLaterChangesByTheCallerDoNotLeakIn() {
		Map<String, String> params = new HashMap<>();
		params.put("a", "1");
		RequestSpecBuilder builder = builder().setQueryParams(params);

		params.put("b", "2");
		FilterableRequestSpecification spec = view(builder.build());

		Assert.assertEquals(spec.getQueryParams().get("a"), "1");
		Assert.assertFalse(spec.getQueryParams().containsKey("b"));
	}

	@Test
	public void everyBuiltSpecCarriesTheTimeouts() {
		FilterableRequestSpecification spec = view(builder().build());

		Assert.assertNotNull(spec.getConfig().getHttpClientConfig().params().get("http.connection.timeout"));
		Assert.assertNotNull(spec.getConfig().getHttpClientConfig().params().get("http.socket.timeout"));
	}
}
