package com.amazon.integrator.delivery.api;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.json.JSONException;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzOrderMutations;
import com.amazon.common.util.AmzRestWebserviceUtil;
import com.amazon.common.util.HTTPClientException;
import com.ibm.sterling.afc.jsonutil.PLTJSONUtils;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCCommon;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;
import java.net.SocketTimeoutException;
import javax.xml.parsers.ParserConfigurationException;

public class AmzGetOrderDetails {
	
	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzGetOrderDetails.class);
	
	public static Document getOrderDetails(YFSEnvironment env, Document doc) throws Exception {
		logger.beginTimer("class: AmzGetOrderDetails | method: getOrderDetails -- Starts");
		Document outDoc = null;
		try {
	        
			Map<String, String> bwpPropertiesMap = new HashMap<>();
			
			Document propertyDoc = SCXmlUtil.createDocument("Properties");
			propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, 
					doc.getDocumentElement().getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE));
			bwpPropertiesMap = AmzGetGenericProperty.getBWPIntegProperties(propertyDoc);
			
			String targetId = bwpPropertiesMap.get(AmzCommonConstants.AMZ_TARGETID);
			String postURL = bwpPropertiesMap.get(AmzCommonConstants.AMZ_POST_URL);
			String apiAccessKey = bwpPropertiesMap.get(AmzCommonConstants.AMZ_API_ACCESS_KEY);
			String apiVersion = bwpPropertiesMap.get(AmzCommonConstants.AMZ_API_VERSION);
			
	        Element eleDoc = doc.getDocumentElement();
	        String orderId = eleDoc.getAttribute(AmzCommonConstants.AMZ_ORDER_ID);
	        String query = AmzOrderMutations.AMZ_COMPLETE_ORDER_DETAILS;
	        String enterpriseCode = eleDoc.getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE);
	        logger.debug("enterpriseCode: "+enterpriseCode);
	        if(YFCCommon.isVoid(orderId) ) {
	        	YFSException ex =  new YFSException();
				ex.setErrorCode("INPUT_ERROR_004");
				ex.setErrorDescription("Amazon OrderId is empty");
				throw ex;
	        }
	        
	        JSONObject variables = new JSONObject();
			JSONObject orderIdentifier = new JSONObject();
			orderIdentifier.put("orderId", orderId);
			variables.put("orderIdentifier", orderIdentifier);
						
			JSONObject payload = new JSONObject();
			payload.put("query", query);
			payload.put("variables", variables);
	        
			StringEntity requestEntity = new StringEntity(payload.toString(), ContentType.APPLICATION_JSON);
			Map<String, String> headerMap = new HashMap<>();
			headerMap.put(AmzLiterals.A_JS_CONTENTS_TYPE, AmzLiterals.A_JS_APPLICATION_JSON);
			headerMap.put(AmzLiterals.A_JS_AUTHORIZATION, "Bearer" + " " + AmzRestWebserviceUtil.getAuthorizationToken(enterpriseCode));
			headerMap.put(AmzLiterals.A_JS_X_OMNI_TARGETID, targetId);
			headerMap.put(AmzLiterals.A_JS_X_API_ACCESS_KEY, apiAccessKey);
			headerMap.put(AmzLiterals.A_JS_X_API_VERSION, apiVersion);
			String output = AmzRestWebserviceUtil.invokePost(postURL, 10, requestEntity, headerMap);
	        logger.debug("output:"+ output);
	        AmzCommonUtil.validateResponseMessage(output);
	        
	        outDoc = PLTJSONUtils.getXmlFromJSON(output, "Root");
	        if(!YFCObject.isVoid(outDoc)) {
	        	prepareAndLogResponse(AmzLiterals.STR_SUCCESS, doc, null);
	        }
	        logger.debug("getOrderDetails outDoc:"+ SCXmlUtil.getString(outDoc)); 
	        } 
			catch (SocketTimeoutException | ParserConfigurationException | HTTPClientException e) {
				e.printStackTrace();
				prepareAndLogResponse(AmzLiterals.STR_ERROR, doc, e.getMessage());
				throw e;
			} catch (YFSException e) {
				e.printStackTrace();
				prepareAndLogResponse(AmzLiterals.STR_ERROR, doc, e.getErrorDescription());
				throw e;
			} catch (JSONException e) {
				e.printStackTrace();
				YFSException ex = new YFSException();
				ex.setErrorCode("JSON_ERROR_002");
				ex.setErrorDescription(e.getMessage());
				prepareAndLogResponse(AmzLiterals.STR_ERROR, doc, ex.getErrorDescription());
				throw ex;
			}
		
		logger.endTimer("class: AmzGetOrderDetails | method: getOrderDetails -- Ends");
		return outDoc;
	}
	
	public static void prepareAndLogResponse(String processStatus, Document inDoc, String message) {

		logger.beginTimer("class: AmzProcessInTransitAndDeliveredEvent | method: prepareAndLogResponse -- Starts");

		String amazonOrderId = inDoc.getDocumentElement().getAttribute(AmzCommonConstants.AMZ_ORDER_ID);
		String invokedEventType = inDoc.getDocumentElement().getAttribute("InvokedEventType");

		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE, "AMZCONN_GET_ORDER");
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE, invokedEventType);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_EVENT, AmzLiterals.STR_RESPONSE);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, amazonOrderId);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_PROCESS_STATUS, processStatus);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_HTTP_CODE, AmzCommonConstants.STR_HTTP_STATUS_OK);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_HTTP_MESSAGE, AmzCommonConstants.STR_OK);


		if (!YFCObject.isVoid(message)) {
			if (processStatus.equalsIgnoreCase(AmzLiterals.STR_SUCCESS)) {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_MESSAGE, message);
			} else {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_ERROR_MSG, message);
			}
		}
		AmzCommonUtil.logAmzConnResponse(logInput);

		logger.endTimer("class: AmzProcessInTransitAndDeliveredEvent | method: prepareAndLogResponse -- Ends");
	}
}
