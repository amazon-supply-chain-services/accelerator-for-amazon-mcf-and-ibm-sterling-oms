package com.amazon.common.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.http.HttpResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.apache.http.HttpStatus;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCCommon;
import com.yantra.yfs.japi.YFSException;

public class AmzRestWebserviceUtil{
	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzRestWebserviceUtil.class);
	private static boolean isDebugEnabled = true;
	
	Map<String, String> bwpPropertiesMap = new HashMap<>();	
	
	public static String invokeGet(String strEndpointUrl,Map<String, String> headerMap,Map<String, String> paramsMap) throws Exception {
		logger.beginTimer("class: AmzRestWebserviceUtil | method: invokeGet -- Starts");
		if(isDebugEnabled) {
			logger.debug("AmzRestWebserviceUtil.invokeGet Start");
		}
		
		String responseBody = "";
		
		try  {
			
			URIBuilder builder = new URIBuilder(strEndpointUrl);
			
			if (!paramsMap.isEmpty()) {
				for (Map.Entry<String, String> entry : paramsMap.entrySet()) {
					logger.debug("AmzRestWebserviceUtil:entry.getKey():" + entry.getKey() + " :entry.getValue()::"
							+ entry.getValue());
					builder.addParameter(entry.getKey(), entry.getValue());
				}
			}
			
			URL url = builder.build().toURL();
			
			HttpGet httpGet = new HttpGet(url.toString());

			if (!headerMap.isEmpty()) {
				for (Map.Entry<String, String> entry : headerMap.entrySet()) {
					logger.debug("AmzRestWebserviceUtil:entry.getKey():" + entry.getKey() + " :entry.getValue()::"
							+ entry.getValue());
					httpGet.addHeader(entry.getKey(), entry.getValue());
				}
			}
			
			SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
			TrustManager[] trustAllCerts = trustAllCertificates();
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

			SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContext);
			if (isDebugEnabled) {
				logger.debug("AmzRestWebserviceUtil:socketFactory::" + socketFactory);
			}
			CloseableHttpClient httpclient = HttpClients.custom().setSSLSocketFactory(socketFactory).build();
			
			logger.debug("Executing GET request...");
			HttpResponse response = httpclient.execute(httpGet);
			if (!YFCObject.isVoid(response)) {
				int statusCode = response.getStatusLine().getStatusCode();
				if (isDebugEnabled) {
					logger.debug("AmzRestWebserviceUtil.invokeGet:statusCode::" + statusCode);
				}
				String statusMessage = response.getStatusLine().getReasonPhrase();
				if (isDebugEnabled) {
					logger.debug("AmzRestWebserviceUtil:statusMessage::" + statusMessage);
				}
				
				if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_CREATED) {
					responseBody = getResponse(response);
					if (isDebugEnabled) {
						logger.debug("AmzRestWebserviceUtil: for OK:strResponse::" + responseBody);
					}
				}else {
					// Capturing the Error Codes which has HTTP status other than 200
					if(YFCCommon.isVoid(statusMessage)) {
						statusMessage = EntityUtils.toString(response.getEntity());
					}
					responseBody = getErrorResponse(statusCode, statusMessage);
					if (isDebugEnabled) {
						logger.debug("AmzRestWebserviceUtil: for not OK:strResponse::" + responseBody);
						logger.debug("AmzRestWebserviceUtil: Rest webservice response:statusCode:" + statusCode
								+ " :statusMessage::" + statusMessage);
					}
					
				}
			}else {
				logger.debug("AmzRestWebserviceUtil: Rest webservice client response is NULL");
			}
		} catch (SocketTimeoutException | ParserConfigurationException | HTTPClientException e) {
			e.printStackTrace();
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			YFSException yfse = new YFSException();
			yfse.setErrorCode("Exception in AmzRestWebserviceUtil.invokeGet");
			yfse.setErrorDescription(e.getMessage());
			throw yfse;
		}
		
		if(isDebugEnabled) {
			logger.debug("AmzRestWebserviceUtil.invokeGet End");
		}
		logger.endTimer("class: AmzRestWebserviceUtil | method: invokeGet -- Ends");
		return responseBody;
	}
	
	public static String invokePost(String strEndpointUrl, int intTimeoutDuration, StringEntity requestEntity,
			Map<String, String> headerMap) throws Exception {
		logger.beginTimer("class: AmzRestWebserviceUtil | method: invokePost -- Starts");
		logger.debug("AmzRestWebserviceUtil.invokePost Start");
		logger.debug("invokePost RequestConfig");

		// timeoutDuration is in seconds, so getting time in milliseconds
		RequestConfig config = RequestConfig.custom().setConnectTimeout(intTimeoutDuration * 1000)
				.setConnectionRequestTimeout(intTimeoutDuration * 1000).setSocketTimeout(intTimeoutDuration * 1000)
				.build();
		logger.debug("invokePost RequestConfig");
		HttpPost post = new HttpPost(strEndpointUrl);
		String strResponse = null;
		HttpResponse rawResponse = null;
		try {
			if (!headerMap.isEmpty()) {
				for (Map.Entry<String, String> entry : headerMap.entrySet()) {
					if (isDebugEnabled) {
						logger.debug("AmzRestWebserviceUtil:entry.getKey():" + entry.getKey() + " :entry.getValue()::"
								+ entry.getValue());
					}
					post.addHeader(entry.getKey(), entry.getValue());
				}
			}

			if (requestEntity != null) {
				post.setEntity(requestEntity);
			}

			post.setEntity(requestEntity);

			SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
			TrustManager[] trustAllCerts = trustAllCertificates();
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

			SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContext);
			if (isDebugEnabled) {
				logger.debug("AmzRestWebserviceUtil:socketFactory::" + socketFactory);
			}
			CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(socketFactory).setDefaultRequestConfig(config).build();
			if (isDebugEnabled) {
				logger.debug("AmzRestWebserviceUtil:httpClient::" + httpClient);
				logger.debug("AmzRestWebserviceUtil:post::" + post);
			}

			rawResponse = httpClient.execute(post);

			if (!YFCObject.isVoid(rawResponse)) {
				int statusCode = rawResponse.getStatusLine().getStatusCode();
				if (isDebugEnabled) {
					logger.debug("AmzRestWebserviceUtil:statusCode::" + statusCode);
				}

				String statusMessage = rawResponse.getStatusLine().getReasonPhrase();
				if (isDebugEnabled) {
					logger.debug("AmzRestWebserviceUtil:statusMessage::" + statusMessage);
				}

				if (statusCode == HttpStatus.SC_OK || statusCode == HttpStatus.SC_CREATED || statusCode == HttpStatus.SC_ACCEPTED ) {
					strResponse = getResponse(rawResponse);
					if (isDebugEnabled) {
						logger.debug("AmzRestWebserviceUtil: for OK:strResponse::" + strResponse);
					}
					
				} else {
					// Capturing the Error Codes which has HTTP status other than 200
					strResponse = getErrorResponse(statusCode, statusMessage);
					logger.debug("AmzRestWebserviceUtil: for not OK:strResponse::" + strResponse);
					logger.debug("AmzRestWebserviceUtil: Rest webservice response:statusCode:" + statusCode
							+ " :statusMessage::" + statusMessage);
				}
			} else {
				logger.debug("AmzRestWebserviceUtil: Rest webservice client response is NULL");
			}

		} catch (SocketTimeoutException | ParserConfigurationException | HTTPClientException e) {
			e.printStackTrace();
			throw e;
		}
		catch (Exception ex) {
			logger.debug("AmzRestWebserviceUtil: Exception while calling Rest webservice : " + ex.getMessage());
			if (rawResponse == null) {
				throw new Exception(
						"TimeOut/Destination url could not be reached : " + ex.getLocalizedMessage());
			}
		}		
		logger.debug("AmzRestWebserviceUtil.invokePost End");
		logger.endTimer("class: AmzRestWebserviceUtil | method: invokePost -- Ends");
		return strResponse;
	}
	

	
	private static String getResponse(HttpResponse rawResponse) throws Exception {
		logger.beginTimer("class: AmzRestWebserviceUtil | method: getResponse -- Starts");
		logger.debug("Begin AmzRestWebserviceUtil.getResponse");
		String strResponse = null;
		BufferedReader reader = null;
		try {
			/**
			 * Check for null because there will be cases where the rest responses will be
			 * null when the status code is 204
			 */
			if (null != rawResponse && null != rawResponse.getEntity()
					&& null != rawResponse.getEntity().getContent()) {
				reader = new BufferedReader(new InputStreamReader(rawResponse.getEntity().getContent()));
				String inputLine;
				StringBuilder response = new StringBuilder();
				while ((inputLine = reader.readLine()) != null) {
					response.append(inputLine);
				}
				strResponse = response.toString();
				if (isDebugEnabled) {
					logger.debug("AmzRestWebserviceUtil:strResponse::" + strResponse);
				}
			}

		} catch (Exception e) {
			logger.debug("Exception Occured in AmzRestWebserviceUtil:getResponse::" + e.getMessage());
			strResponse = e.toString();
			throw e;
		} finally {
			if (null != reader) {
				reader.close();
			}
		}

		logger.debug("End AmzRestWebserviceUtil.getResponse");
		logger.endTimer("class: AmzRestWebserviceUtil | method: getResponse -- Ends");
		return strResponse;
	}
	
	private static String getErrorResponse(int errorCode, String errorMessage) throws Exception {
		logger.beginTimer("class: AmzRestWebserviceUtil | method: getErrorResponse -- Starts");
		logger.debug("Begin AmzRestWebserviceUtil.getErrorresponse");
		String strResponse = null;

		Document errorDoc = SCXmlUtil.createDocument("Error");
		Element errorEle = errorDoc.getDocumentElement();

		// set Error Code
		Element errorCodeEle = SCXmlUtil.createChild(errorEle, "ErrorCode");
		errorCodeEle.setTextContent(String.valueOf(errorCode));
		
		if(!YFCCommon.isVoid(errorMessage) && errorMessage.contains(AmzCommonConstants.STR_ERRORS)) {
			JSONObject outputJson = new JSONObject(errorMessage);
			 if (outputJson.has(AmzCommonConstants.STR_ERRORS)) {
				 JSONArray errors = outputJson.getJSONArray(AmzCommonConstants.STR_ERRORS);
				 if (errors.length() > 0) {
					 JSONObject error = errors.getJSONObject(0);
					 errorMessage =  error.getString("message");
				 }
			 }
		}
		
			//set Error Description
			Element errorDescEle = SCXmlUtil.createChild(errorEle, "ErrorDescription");
			errorDescEle.setTextContent((!YFCObject.isVoid(errorMessage)) ? errorMessage : "NullResponse");
			
			String strErrorEle = SCXmlUtil.getString(errorEle);
			JSONObject errorJSONResponse;
			errorJSONResponse = XML.toJSONObject(strErrorEle);
			if(errorJSONResponse != null) {
				strResponse = errorJSONResponse.toString();
			}	
		logger.debug("End AmzRestWebserviceUtil.getErrorresponse");
		logger.endTimer("class: AmzRestWebserviceUtil | method: getErrorResponse -- Ends");
		return strResponse;
	}
	
	public static String getAuthorizationToken(String enterpriseCode) {
		logger.beginTimer("class: AmzRestWebserviceUtil | method: getAuthorizationToken -- Starts");
		if (isDebugEnabled) {
			logger.debug("Inside AmzRestWebserviceUtil.getAuthorizationToken");
		}

		AmzToken tokenObj = null;
		try {

			logger.debug("Invoking AmzTokenGenerationUtil");
			tokenObj = AmzTokenGenerationUtil.getToken(enterpriseCode);
			tokenObj.amzTokenMap.forEach((key, value) -> logger.debug(key + ": " + value));

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
		logger.endTimer("class: AmzRestWebserviceUtil | method: getAuthorizationToken -- Ends");
		return tokenObj.amzTokenMap.get("amz_token_" + enterpriseCode);
	}
	
	public static String getSPAuthorizationToken(String enterpriseCode) {
		logger.beginTimer("class: AmzRestWebserviceUtil | method: getSPAuthorizationToken -- Starts");
		SPApiToken tokenObj = null;
		try {

			logger.debug("Invoking SPApiTokenGenerationUtil");
			tokenObj = SPApiTokenGenerationUtil.getToken(enterpriseCode);
			tokenObj.spApiTokenMap.forEach((key, value) -> logger.debug(key + ": " + value));

		}catch (YFSException e) {
			e.printStackTrace();
			throw AmzCommonUtil.createException(e);
		}  
		catch (Exception e) {
			YFSException ex = new YFSException();
	        ex.setErrorCode("SPAPI_TOKEN_GENERATION_FAILED");
	        ex.setErrorDescription("Failed to generate SP API token");
	        throw ex;
		}
		logger.endTimer("class: AmzRestWebserviceUtil | method: getSPAuthorizationToken -- Ends");
		return tokenObj.spApiTokenMap.get("sp_token_" + enterpriseCode);
	}
	
	public static TrustManager[] trustAllCertificates() {
		logger.debug("Begin AmzRestWebserviceUtil.trustAllCertificates");
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

		logger.debug("End AmzRestWebserviceUtil.trustAllCertificates");
		return trustAllCerts;

	}
	
}
