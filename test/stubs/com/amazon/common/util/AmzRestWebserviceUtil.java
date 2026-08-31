package com.amazon.common.util;

import java.util.Map;

import org.apache.http.entity.StringEntity;

/**
 * OFFLINE TEST STUB of the accelerator's {@code AmzRestWebserviceUtil} (the SP-API HTTP client).
 *
 * <p>NOT IBM code and contains NO IBM proprietary material. It replaces the live SP-API network
 * calls so the REAL production classes ({@code MCFCreateFulfillmentOrderInAmazon},
 * {@code ListMCFOrders}, {@code GetMCFOrder}) can run OFFLINE:
 * <ul>
 *   <li>{@link #invokeGet} / {@link #invokePost} return whatever response the test scripts via
 *       {@link #setNextResponse(String)} (e.g. the contents of a JSON sample fixture);</li>
 *   <li>{@link #invokePost} also CAPTURES the request body so a test can assert the exact SP-API
 *       request JSON the accelerator produced (see {@link #getLastPostBody()});</li>
 *   <li>{@link #getSPAuthorizationToken} returns a dummy token (no auth call offline).</li>
 * </ul>
 * The real utility performs live HTTPS calls with a licensed connector at runtime; this stub is
 * used only by the test harness.
 */
public final class AmzRestWebserviceUtil {

	private static String nextResponse = "";
	private static String lastPostBody;
	private static String lastGetUrl;
	private static String lastPostUrl;

	private AmzRestWebserviceUtil() {
	}

	/** Script the response the next invokeGet/invokePost should return. */
	public static void setNextResponse(String response) {
		nextResponse = response;
	}

	/** The body string of the most recent invokePost (the produced SP-API request JSON). */
	public static String getLastPostBody() {
		return lastPostBody;
	}

	public static String getLastGetUrl() {
		return lastGetUrl;
	}

	public static String getLastPostUrl() {
		return lastPostUrl;
	}

	/** Reset captured state between tests. */
	public static void reset() {
		nextResponse = "";
		lastPostBody = null;
		lastGetUrl = null;
		lastPostUrl = null;
	}

	public static String invokeGet(String strEndpointUrl, Map<String, String> headerMap,
			Map<String, String> paramsMap) {
		lastGetUrl = strEndpointUrl;
		return nextResponse;
	}

	public static String invokePost(String strEndpointUrl, int intTimeoutDuration,
			StringEntity requestEntity, Map<String, String> headerMap) {
		lastPostUrl = strEndpointUrl;
		lastPostBody = requestEntity == null ? null : requestEntity.getContentString();
		return nextResponse;
	}

	public static String getSPAuthorizationToken(String enterpriseCode) {
		return "offline-test-token";
	}
}
