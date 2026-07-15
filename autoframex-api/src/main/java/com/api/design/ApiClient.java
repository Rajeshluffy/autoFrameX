package com.api.design;

import io.restassured.specification.RequestSpecification;

public interface ApiClient {	
	/**
	 * 
	 * @param request
	 * @param endPoint
	 * @return
	 */
	
	ResponseAPI get(RequestSpecification request, String endPoint);

	/**
	 * 
	 * @param request
	 * @param endPoint
	 * @return
	 */
	ResponseAPI post(RequestSpecification request, String endPoint);	

	/**
	 * 
	 * @param request
	 * @param endPoint
	 * @param body
	 * @return
	 */
	
	ResponseAPI post(RequestSpecification request, String endPoint, Object body);

	/**
	 * 
	 * @param request
	 * @param endPoint
	 * @param body
	 * @return
	 */

	ResponseAPI put(RequestSpecification request, String endPoint, Object body);


	/**
	 * 
	 * @param request
	 * @param endPoint
	 * @return
	 */
	
	ResponseAPI delete(RequestSpecification request, String endPoint);


}