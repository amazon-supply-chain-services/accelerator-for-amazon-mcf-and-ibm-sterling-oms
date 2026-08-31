package com.amazon.common.util;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.json.JSONObject;
import org.apache.http.HttpStatus;
import org.w3c.dom.Document;

import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSException;

/**
 * A utility class to generate token for Amazon API invocation. The class
 * exposes getToken API as public method. The client is expected to pass the
 * required credentials to connect to the token system.
 * 
 */

public class AmzTokenGenerationUtil {

	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzTokenGenerationUtil.class);

	private static AmzToken tokenObj = null;
	private static boolean isDebugEnabled = true;

	/**
	 * Single Api that all consumers can invoke. The method always returns a valid
	 * token. The method manages internally all complexities surrounding validity
	 * checks and new token creation etc.
	 * 
	 * @param - env Represents a valid YFSEnvironment object.
	 * 
	 * @throws IOException, MalformedURLException and Exception
	 */
	public static AmzToken getToken(String enterpriseCode) throws Exception {
		logger.beginTimer("class: AmzTokenGenerationUtil | method: getToken -- Starts");
		if (isDebugEnabled) {
			logger.debug("Inside getToken method..");

		}

		/**
		 * First check if you have a token. If not, call Amazon to get a valid token
		 * Else - Check the validity of the current token If valid, return it Else -
		 * Invoke Amazon to get a new one - Assign it to the Token object at class level
		 * - Return the new token to the client.
		 *
		 **/
		
		if (tokenObj == null || !tokenObj.amzTokenMap.containsKey("amz_token_" + enterpriseCode)) {
			logger.debug("Token object is empty. Fetching a new one from Amazon.");
			// Get a new token from OMS service.

			tokenObj = fetchNewTokenFromAmazon(enterpriseCode);

			logger.debug("Obtained a new Token. Returning token value: " + tokenObj.amzTokenMap.get("amz_token_" + enterpriseCode));

			return tokenObj;

		} else {
			logger.debug("Token exists. Checking if it is still valid.");

			if (isTokenValid(tokenObj,enterpriseCode) == true) {
				// Token is valid. Use it.
				logger.debug("Token is valid. Returning it...");

				return tokenObj;

			} else {

				// Get a new token.
				logger.debug("Token is invalid. Getting a new one...");

				// Get a new token from OMS service.
				tokenObj = fetchNewTokenFromAmazon(enterpriseCode);

				return tokenObj;
			} // else

		} // else ends here.
		
	}// End of getToken method

	/**
	 * Check if the Token value is still valid.
	 */
	private static boolean isTokenValid(AmzToken tokenObj, String enterpriseCode) throws Exception {
		logger.beginTimer("class: AmzTokenGenerationUtil | method: isTokenValid -- Starts");
		logger.debug("Inside isValidToken method..");
		if (tokenObj == null) {
			throw new Exception("Token Object cannot be null.");
		}

		Date now = new Date();
		// Get current time in seconds.
		long currentTime = now.getTime() / 1000L;

		logger.debug("Current time in seconds is: " + currentTime);
		logger.debug("Token generate time in seconds is: " + tokenObj.amzTokenMap.get("amz_tokenGeneratedTime_" + enterpriseCode));
		logger.debug("Token Validity in seconds is: " + tokenObj.amzTokenMap.get("amz_validity_" + enterpriseCode));

		int tokenValidity = Integer.parseInt(tokenObj.amzTokenMap.get("amz_validity_" + enterpriseCode));
		long tokenGeneratedTime = Long.parseLong(tokenObj.amzTokenMap.get("amz_tokenGeneratedTime_" + enterpriseCode));
		if ((currentTime - tokenGeneratedTime) >= (tokenValidity - 10)) {
			logger.debug("Token has expired. Returning false");

			return false;

		} // if ends here

		logger.debug("Token is valid. Returning true");
		logger.endTimer("class: AmzTokenGenerationUtil | method: isTokenValid -- Ends");
		return true;
	}

	/**
	 * This method connects with OMS token generator API and gets a new token. It
	 * receives the YFSEnvironment as parameter. It then creates an HTTP connection
	 * object and makes a POST request. The output json is read and token value
	 * extracted and returned.
	 */
	private static AmzToken fetchNewTokenFromAmazon(String enterpriseCode) {
		logger.beginTimer("class: AmzTokenGenerationUtil | method: fetchNewTokenFromAmazon -- Starts");
		if (isDebugEnabled) {
			logger.debug("Inside fetchNewToken method. Fetching the URL.");

		}
		try {
			
			Map<String, String> bwpPropertiesMap = new HashMap<>();
			Document propertyDoc = SCXmlUtil.createDocument("Properties");
			propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, enterpriseCode);
			bwpPropertiesMap = AmzGetGenericProperty.getBWPIntegProperties(propertyDoc);
			
			String sAuthUrl = bwpPropertiesMap.get(AmzCommonConstants.AMZ_TOKEN_URL);
			String sClientId = bwpPropertiesMap.get(AmzCommonConstants.AMZ_CLIENT_ID);
			String sClientSecret = bwpPropertiesMap.get(AmzCommonConstants.AMZ_CLIENT_SECRET);
			String sGrantType = bwpPropertiesMap.get(AmzCommonConstants.AMZ_GRANT_TYPE);
			String sTimeOut = bwpPropertiesMap.get(AmzCommonConstants.AMZ_TIME_OUT);

			logger.debug("Auth URL: " + sAuthUrl);
			logger.debug("Client ID: " + sClientId);
			logger.debug("Client Secret: ****");
			logger.debug("Grant Type: " + sGrantType);

			SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
			TrustManager[] trustAllCerts = trustAllCertificates();
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
			SSLContext.setDefault(sslContext);

			HttpClient httpclient = new HttpClient();

			PostMethod postObbj = new PostMethod(sAuthUrl);

			postObbj.addParameter(new NameValuePair("grant_type", sGrantType));
			postObbj.addParameter(new NameValuePair("client_id", sClientId));
			postObbj.addParameter(new NameValuePair("client_secret", sClientSecret));

			// Get HTTP client
			httpclient.executeMethod(postObbj);
			// Default Value for Timeout in seconds

			if (!YFCObject.isVoid(sTimeOut)) {
				httpclient.getHttpConnectionManager().getParams().setSoTimeout(new Integer(sTimeOut) * 1000);
			}

			httpclient.executeMethod(postObbj);

			// get response message
			String sResponse = postObbj.getResponseBodyAsString();
			logger.debug("sResponse: " + sResponse);
			int code = postObbj.getStatusCode();
			String statusMessage = postObbj.getStatusLine().getReasonPhrase();
			if (code == HttpStatus.SC_OK) {
				JSONObject jsonResp = new JSONObject(sResponse);
				if (tokenObj == null) {
					tokenObj = new AmzToken();
				}

				Date dt = new Date();
				long sec = dt.getTime() / 1000;
				
				tokenObj.amzTokenMap.put("amz_token_" + enterpriseCode, (String) jsonResp.get("access_token"));
				tokenObj.amzTokenMap.put("amz_validity_" + enterpriseCode, String.valueOf(jsonResp.get("expires_in")));
				tokenObj.amzTokenMap.put("amz_tokenGeneratedTime_" + enterpriseCode, Long.toString(sec));
			} else {
				YFSException ex = new YFSException();
				ex.setErrorCode(Integer.toString(code));
				ex.setErrorDescription(statusMessage);
				throw ex;
			}
		}catch (YFSException e) {
			e.printStackTrace();
			throw AmzCommonUtil.createException(e);
		}  
		catch (Exception e) {
			e.printStackTrace();
			YFSException ex = new YFSException();
			ex.setErrorCode("AMZ_TOKEN_GENERATION_FAILED");
			ex.setErrorDescription("Failed to generate amazon token");
			throw ex;
		}
		logger.endTimer("class: AmzTokenGenerationUtil | method: fetchNewTokenFromAmazon -- Ends");
		return tokenObj;
	}// fetchNewToken Ends here

	/**
	 * This method will disable the SSL security certificate
	 * 
	 * @param
	 * @param
	 */
	private static TrustManager[] trustAllCertificates() {
		logger.info("class: AmzTokenGenerationUtil | method: trustAllCertificates -- Starts");
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			@Override
			public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType)
					throws CertificateException {
			}

			@Override
			public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
					throws CertificateException {
			}
		} };

		logger.info("class: AmzTokenGenerationUtil | method: trustAllCertificates -- Ends");
		return trustAllCerts;

	}
}
