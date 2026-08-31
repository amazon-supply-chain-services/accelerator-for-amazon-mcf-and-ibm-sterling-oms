package com.amazon.integrator.order.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzRestWebserviceUtil;
import com.amazon.common.util.AmzXMLUtil;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.log.YFCLogCategory;

/*
 * This class invoke the amazon cancel order api.
 */
public class AmzCancelOrderInAmazon {
	static final YFCLogCategory logger = YFCLogCategory.instance(AmzCancelOrderInAmazon.class);
	static Map<String, String> mapBWPIntegProperties = null;
	/**
	 * @param payload
	 * @return The response from Amazon's service after the GraphQL mutation query is sent.
	 * @throws Exception
	 * This method sends the prepared GraphQL mutation query to Amazon's service via a POST request.
	 * It includes necessary authentication and header information in the request.
	 * The method returns the response from Amazon, indicating whether the cancellation was successful or not.
	 */
	public static String invokeAmazonPostCall(JSONObject payload,Document inputDoc) throws Exception {
	    logger.beginTimer("class: AmzCancelOrderInAmazon | method: invokeAmazonPostCall -- Starts");
	    logger.info("class: AmzCancelOrderInAmazon | method: invokeAmazonPostCall -- Starts");
	    prepareAndLogRequest(inputDoc);	    
		String output = null;
	    HttpsURLConnection connection = null ;
	    int responseCode=0;
	    try {
	    	Element eleOrder = inputDoc.getDocumentElement();
		    Document inDocGetBWPIntegProps = AmzXMLUtil.createDocument(AmzLiterals.E_PROPERTIES);
			inDocGetBWPIntegProps.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE,
					eleOrder.getAttribute(AmzLiterals.A_ENTERPRISE_CODE));
			mapBWPIntegProperties = AmzGetGenericProperty.getBWPIntegProperties(inDocGetBWPIntegProps);
			
			logger.debug("mapBWPIntegProperties is: " + mapBWPIntegProperties);
			String targetId = mapBWPIntegProperties.get(AmzCommonConstants.AMZ_TARGETID);
			logger.debug("targetId is: " + targetId);
			
			String postURL = mapBWPIntegProperties.get(AmzCommonConstants.AMZ_POST_URL);
			logger.debug("postURL is: " + postURL);

			String apiAccessKey = mapBWPIntegProperties.get(AmzCommonConstants.AMZ_API_ACCESS_KEY);
			logger.debug("apiAccessKey is: " + apiAccessKey);
			
			String apiVersion = mapBWPIntegProperties.get(AmzCommonConstants.AMZ_API_VERSION);
			logger.debug("apiVersion is: " + apiVersion);
			
	    	URL url = new URL(postURL);
	    	SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
			TrustManager[] trustAllCerts = AmzRestWebserviceUtil.trustAllCertificates();
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
	      	
        	connection = (HttpsURLConnection) url.openConnection();
	        connection.setConnectTimeout(10000);
	        connection.setReadTimeout(10000);  // 10 seconds timeout for reading
	        connection.setRequestMethod("POST");
	        connection.setRequestProperty(AmzLiterals.A_JS_CONTENTS_TYPE, AmzLiterals.A_JS_APPLICATION_JSON);
	        connection.setRequestProperty(AmzLiterals.A_JS_AUTHORIZATION, "Bearer " + AmzRestWebserviceUtil.getAuthorizationToken(eleOrder.getAttribute(AmzLiterals.A_ENTERPRISE_CODE)));
	        connection.setRequestProperty(AmzLiterals.A_JS_X_OMNI_TARGETID, targetId);
	        connection.setRequestProperty(AmzLiterals.A_JS_X_API_ACCESS_KEY, apiAccessKey);
	        connection.setRequestProperty(AmzLiterals.A_JS_X_API_VERSION, apiVersion);
	        connection.setRequestProperty("Accept", "*/*");
	        connection.setDoOutput(true);
	        
	        // Write payload to request body
	         connection.getOutputStream().write(payload.toString().getBytes(StandardCharsets.UTF_8));
	        // Get the response
	         responseCode = connection.getResponseCode();
	        String responseMessage = connection.getResponseMessage();
	        
	        logger.debug("class: AmzCancelOrderInAmazon | method: invokeAmazonPostCall | Response Code: " + responseCode);
	        logger.debug("class: AmzCancelOrderInAmazon | method: invokeAmazonPostCall | Response Message: " + responseMessage);

	        if (responseCode != 200 && responseCode != 201 && responseCode != 202) {            	// Handle non-200 response code
	                output = getResponseBody(connection.getErrorStream());
	        } else {
	              	output = getResponseBody(connection.getInputStream());	            
	           
	        }

	    } catch (Exception e) {
	    	logger.error("class: AmzCancelOrderInAmazon | method: invokeAmazonPostCall | Error during Amazon Cancel Order API request " + e);
	         throw e;
	    }
	    
	    finally {
	        if (connection != null) {
	            connection.disconnect();
	        }
	    }
	    
	    // Log output before returning it
	    logger.debug("class: AmzRequestOrderCancellation | method: invokeAmazonPostCall | Response from Amazon service: " + output);
	    logger.info("class: AmzRequestOrderCancellation | method: invokeAmazonPostCall -- Ends");
	    logger.endTimer("class: AmzRequestOrderCancellation | method: invokeAmazonPostCall -- Ends");
	    
	    return output;
	}
	
	/**
	 * This method is to read response body *
	 *
	 * @param responseStream
	 * @return
	 * @throws IOException
	 */
	private static String getResponseBody(InputStream responseStream)
			throws IOException {
         // \A is the beginning of the stream boundary
		Scanner scanner = new Scanner(responseStream, "UTF-8");
		String rBody = "";
		if (scanner.hasNext())
			rBody = scanner.useDelimiter("\\A").next();
		if(scanner!=null)
		   scanner.close();
		if (responseStream != null)
			responseStream.close();
		return rBody;
	}
	
	/*
	 * This method is to log the request before from amazon create order
	 */
	private static void prepareAndLogRequest(Document  inputDoc) {
		logger.beginTimer("class: AmzProcessCreateOrderMessage | method: prepareAndLogRequest -- Starts");
		logger.info("class: AmzProcessCreateOrderMessage | method: prepareAndLogRequest -- Starts");
		Element eleOrder = inputDoc.getDocumentElement();
		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE,"AMZCONN_CANCEL_ORDER");
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,eleOrder.getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE));
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO, eleOrder.getAttribute(AmzLiterals.A_ORDER_NO));
		 AmzCommonUtil.logAmzConnRequest(logInput);
		logger.endTimer("class: AmzProcessCreateOrderMessage | method: prepareAndLogRequest -- Ends");
		logger.info("class: AmzProcessCreateOrderMessage | method: prepareAndLogRequest -- Ends");

	}
	
}
