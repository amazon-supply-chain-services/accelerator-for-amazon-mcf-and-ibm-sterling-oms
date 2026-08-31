package com.amazon.integrator.refund.api;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
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
import com.amazon.integrator.common.util.AmzPrepareAmazonCompleteRefundReq;
import com.ibm.sterling.afc.jsonutil.PLTJSONUtils;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

public class AmzUpdateCompleteRefundStatusToAmazon {
	
	/*This class is responsible for invoking the Amazon endpoint and updating the refund status*/
	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzUpdateCompleteRefundStatusToAmazon.class);
	
	/**
	 * @param env
	 * @param doc
	 * @return
	 * @throws Exception
	 */
	public static Document updateRefundStatusToAmazon(YFSEnvironment env, Document doc) throws Exception {
		logger.beginTimer("class: AmzUpdateCompleteRefundStatusToAmazon | method: updateRefundStatusToAmazon -- Starts");
		logger.info("class: AmzUpdateCompleteRefundStatusToAmazon | method: updateRefundStatusToAmazon -- Starts");
		
		Document outDoc = null;
		try {
	        
			Map<String, String> bwpPropertiesMap = new HashMap<>();			
			Document propertyDoc = SCXmlUtil.createDocument(AmzLiterals.E_PROPERTIES);
			propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, 
					doc.getDocumentElement().getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE));
			bwpPropertiesMap = AmzGetGenericProperty.getBWPIntegProperties(propertyDoc);
			
			String targetId = bwpPropertiesMap.get(AmzCommonConstants.AMZ_TARGETID);
			String postURL = bwpPropertiesMap.get(AmzCommonConstants.AMZ_POST_URL);
			String apiAccessKey = bwpPropertiesMap.get(AmzCommonConstants.AMZ_API_ACCESS_KEY);
			String apiVersion = bwpPropertiesMap.get(AmzCommonConstants.AMZ_API_VERSION);
			
	        Element eleDoc = doc.getDocumentElement();
	        String query = AmzOrderMutations.AMZ_UPDATE_COMPLETE_REFUND_DETAILS;
	        String enterpriseCode = eleDoc.getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE);
	        logger.debug("class: AmzUpdateCompleteRefundStatusToAmazon | method: updateRefundStatusToAmazon | enterpriseCode : "+enterpriseCode);
	        JSONObject variables = AmzPrepareAmazonCompleteRefundReq.prepareCompleteRefundReqJSON(doc);		
	        logger.debug("class: AmzUpdateCompleteRefundStatusToAmazon | method: updateRefundStatusToAmazon | variable is : " + variables.toString());
	        if(variables.length() == 0) {
	        	YFSException yfse = new YFSException();
				yfse.setErrorCode(AmzLiterals.STR_REFUND_REQ_ERROR_CODE+"EMPTY_JSON_VARIABLE");
				yfse.setErrorDescription("The payload JSON is empty. The associated RefundId and AmazonId for the order could not be found.");
				prepareAndLogResponse(AmzLiterals.STR_ERROR, doc, yfse.getErrorDescription());
				throw yfse;
	        }
	        JSONObject payload = new JSONObject();
			payload.put("query", query);
			payload.put("variables", variables);
			logger.debug("class: AmzUpdateCompleteRefundStatusToAmazon | method: updateRefundStatusToAmazon| Payload is : " + payload.toString());
			StringEntity requestEntity = new StringEntity(payload.toString(), ContentType.APPLICATION_JSON);
			Map<String, String> headerMap = new HashMap<>();
			headerMap.put(AmzLiterals.A_JS_CONTENTS_TYPE, AmzLiterals.A_JS_APPLICATION_JSON);
			headerMap.put(AmzLiterals.A_JS_AUTHORIZATION, "Bearer" + " " + AmzRestWebserviceUtil.getAuthorizationToken(enterpriseCode));
			headerMap.put(AmzLiterals.A_JS_X_OMNI_TARGETID, targetId);
			headerMap.put(AmzLiterals.A_JS_X_API_ACCESS_KEY, apiAccessKey);
			headerMap.put(AmzLiterals.A_JS_X_API_VERSION, apiVersion);
			String output = AmzRestWebserviceUtil.invokePost(postURL, 10, requestEntity, headerMap);
			logger.debug("class: AmzUpdateCompleteRefundStatusToAmazon | method: updateRefundStatusToAmazon| output : "+ output);
	        AmzCommonUtil.validateResponseMessage(output);
	        
	        outDoc = PLTJSONUtils.getXmlFromJSON(output, "Root");
	        if(!YFCObject.isVoid(outDoc)) {
	        	prepareAndLogResponse(AmzLiterals.STR_SUCCESS, doc, "Refund amount successfully updated to Amazon.");
	        }
	        logger.debug("class: AmzUpdateCompleteRefundStatusToAmazon | method: updateRefundStatusToAmazon | UpdateOrderDetails outDoc:"+ SCXmlUtil.getString(outDoc)); 
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
		logger.info("class: AmzUpdateCompleteRefundStatusToAmazon | method: updateRefundStatusToAmazon -- Ends");
		logger.endTimer("class: AmzUpdateCompleteRefundStatusToAmazon | method: updateRefundStatusToAmazon -- Ends");
		return outDoc;
	}
	
	// method for generating logs
	
	public static void prepareAndLogResponse(String processStatus, Document inDoc, String message) {
		logger.beginTimer("class: AmzUpdateCompleteRefundStatusToAmazon | method: prepareAndLogResponse -- Starts");
		logger.info("class: AmzUpdateCompleteRefundStatusToAmazon | method: prepareAndLogResponse -- Starts");
		Element eleIndoc=inDoc.getDocumentElement();
		Element eleIndocExtn = SCXmlUtil.getChildElement(eleIndoc,AmzLiterals.E_EXTN);
		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE, "UPDATE_REFUND_STATUS");
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE, "REFUND_STATUS");
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_EVENT, AmzLiterals.STR_RESPONSE);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE,eleIndoc.getAttribute(AmzLiterals.A_ENTERPRISE_CODE) );
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_ORDER_HEADER_KEY,eleIndoc.getAttribute(AmzLiterals.A_ORDER_HEADER_KEY) );
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_PROCESS_STATUS, processStatus);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_HTTP_CODE, AmzCommonConstants.STR_HTTP_STATUS_OK);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_HTTP_MESSAGE, AmzCommonConstants.STR_OK);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID,eleIndocExtn.getAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID) );
		logInput.getDocumentElement().setAttribute("RefundID",eleIndocExtn.getAttribute(AmzLiterals.A_EXTN_AMZ_REFUND_ID) );
		
		if (!YFCObject.isVoid(message)) {
			if (processStatus.equalsIgnoreCase(AmzLiterals.STR_SUCCESS)) {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_MESSAGE, message);
			} else {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_ERROR_MSG, message);
			}
		}
		AmzCommonUtil.logAmzConnResponse(logInput);
		logger.info("class: AmzUpdateCompleteRefundStatusToAmazon | method: prepareAndLogResponse -- Ends");
		logger.endTimer("class: AmzUpdateCompleteRefundStatusToAmazon | method: prepareAndLogResponse -- Ends");
	}
}
