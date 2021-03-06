/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.module.webservices.rest.web.v1_0.controller;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.context.Context;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.RestUtil;
import org.springframework.stereotype.Controller;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Resource controllers should extend this base class to have standard exception handling done
 * automatically. (This is necessary to send error messages as HTTP statuses rather than just as
 * html content, as the core web application does.)
 */
@Controller
@RequestMapping(value = "/rest/**")
public class BaseRestController {
	
	private int errorCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
	
	private static final String DISABLE_WWW_AUTH_HEADER_NAME = "Disable-WWW-Authenticate";
	
	private String errorDetail;
	
	@ExceptionHandler(APIAuthenticationException.class)
	@ResponseBody
	private SimpleObject apiAuthenticationExceptionHandler(Exception ex, HttpServletRequest request,
	        HttpServletResponse response) throws Exception {
		if (Context.isAuthenticated()) {
			// user is logged in but doesn't have the relevant privilege -> 403 FORBIDDEN
			errorCode = HttpServletResponse.SC_FORBIDDEN;
			errorDetail = "User is logged in but doesn't have the relevant privilege";
		} else {
			// user is not logged in -> 401 UNAUTHORIZED
			errorCode = HttpServletResponse.SC_UNAUTHORIZED;
			errorDetail = "User is not logged in";
			if (shouldAddWWWAuthHeader(request)) {
				response.addHeader("WWW-Authenticate", "Basic realm=\"OpenMRS at " + RestConstants.URI_PREFIX + "\"");
			}
		}
		response.setStatus(errorCode);
		return RestUtil.wrapErrorResponse(ex, errorDetail);
	}
	
	@ExceptionHandler(Exception.class)
	@ResponseBody
	private SimpleObject handleException(Exception ex, HttpServletRequest request, HttpServletResponse response)
	        throws Exception {
		ResponseStatus ann = ex.getClass().getAnnotation(ResponseStatus.class);
		if (ann != null) {
			errorCode = ann.value().value();
			if (StringUtils.isNotEmpty(ann.reason())) {
				errorDetail = ann.reason();
			}
			
		} else if (RestUtil.hasCause(ex, APIAuthenticationException.class)) {
			return apiAuthenticationExceptionHandler(ex, request, response);
		} else if (ex.getClass() == HttpRequestMethodNotSupportedException.class) {
			errorCode = HttpServletResponse.SC_METHOD_NOT_ALLOWED;
		}
		response.setStatus(errorCode);
		return RestUtil.wrapErrorResponse(ex, errorDetail);
	}
	
	private boolean shouldAddWWWAuthHeader(HttpServletRequest request) {
		return request.getHeader(DISABLE_WWW_AUTH_HEADER_NAME) == null
		        || !request.getHeader(DISABLE_WWW_AUTH_HEADER_NAME).equals("true");
	}
	
	/**
	 * It should be overridden if you want to expose resources under a different URL than /rest/v1.
	 * 
	 * @return the namespace
	 */
	public String getNamespace() {
		return RestConstants.VERSION_1;
	}
	
	public String buildResourceName(String resource) {
		String namespace = getNamespace();
		
		if (StringUtils.isBlank(namespace)) {
			return resource;
		} else {
			if (namespace.startsWith("/")) {
				namespace = namespace.substring(1);
			}
			if (!namespace.endsWith("/")) {
				namespace += "/";
			}
			return namespace + resource;
		}
	}
	
}
