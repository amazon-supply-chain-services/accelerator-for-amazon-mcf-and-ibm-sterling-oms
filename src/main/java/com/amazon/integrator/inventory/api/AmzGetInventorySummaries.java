package com.amazon.integrator.inventory.api;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.json.JSONException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzRestWebserviceUtil;
import com.ibm.sterling.afc.jsonutil.PLTJSONUtils;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCCommon;
import com.yantra.yfs.core.YFSSystem;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;


/**
 * This class will return getInventorySummeries SP-API output. 
 * 
 */
public class AmzGetInventorySummaries {
	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzGetInventorySummaries.class);
    
    public Document getInventorySummaries(YFSEnvironment env, Document doc) throws Exception {
    	logger.beginTimer("class: AmzGetInventorySummaries | method: getInventorySummaries -- Starts");
        Document outDoc = null;
        try {
        	
        Map<String, String> spApiPropertiesMap = new HashMap<>();
        Map<String, String> genricPropertiesMap = new HashMap<>();
        Document propertyDoc = SCXmlUtil.createDocument("Properties");
		propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, 
				doc.getDocumentElement().getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE));
		spApiPropertiesMap = AmzGetGenericProperty.getSPIntegProperties(propertyDoc);
		genricPropertiesMap = AmzGetGenericProperty.getGenericProperties(env , propertyDoc);
		
        String strResourceURL = spApiPropertiesMap.get(AmzCommonConstants.SP_GET_URL);
        String defaultGranularityType = genricPropertiesMap.get(AmzCommonConstants.AMZ_GRANULARITY_TYPE);
        Element eleDoc = doc.getDocumentElement();
        String sellerSku = eleDoc.getAttribute(AmzCommonConstants.AMZ_ATTRIBUTE_SELLER_SKU);
        String marketPlaceId = eleDoc.getAttribute(AmzCommonConstants.AMZ_ATTRIBUTE_MARKETPLACE_ID);
        String granularityID = eleDoc.getAttribute(AmzCommonConstants.AMZ_ATTRIBUTE_GRANULARITY_ID);
        String nextToken = eleDoc.getAttribute(AmzCommonConstants.AMZ_ATTRIBUTE_NEXT_TOKEN);
        String enterpriseCode=eleDoc.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
        if(YFCObject.isVoid(granularityID))
            granularityID = marketPlaceId;
        
        if(YFCCommon.isVoid(marketPlaceId) ) {
        	YFSException ex =  new YFSException();
			ex.setErrorCode("INPUT_ERROR_003");
			ex.setErrorDescription("MarketPlaceId is empty");
			throw ex;
        }
        
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("x-amz-access-token", AmzRestWebserviceUtil.getSPAuthorizationToken(enterpriseCode));
        
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("marketplaceIds", marketPlaceId);
        paramMap.put("granularityId", granularityID);
        paramMap.put("granularityType", defaultGranularityType);
        if(!YFCObject.isVoid(sellerSku))
            paramMap.put(AmzCommonConstants.AMZ_ATTRIBUTE_SELLER_SKU, sellerSku);
        paramMap.put("details", "true");
        if(!YFCObject.isVoid(nextToken))
            paramMap.put(AmzCommonConstants.AMZ_ATTRIBUTE_NEXT_TOKEN, nextToken);
  
        String output = AmzRestWebserviceUtil.invokeGet(strResourceURL,headerMap,paramMap);
        logger.debug("getInventorySummeries Output:"+output);
        
        AmzCommonUtil.validateResponseMessage(output);
        
        outDoc = PLTJSONUtils.getXmlFromJSON(output, "Root");
        logger.debug("getInventorySummeries outDoc:"+ SCXmlUtil.getString(outDoc)); 
        
        if(!YFCObject.isVoid(outDoc)) {
        	prepareAndLogResponse(AmzLiterals.STR_SUCCESS, doc, null);
        }
        
        } 
        catch (YFSException e) {
            e.printStackTrace();
            prepareAndLogResponse(AmzLiterals.STR_ERROR, doc, e.getErrorDescription());
            throw e;
        } catch (JSONException e) {
            e.printStackTrace();
            YFSException ex = new YFSException();
            ex.setErrorCode("JSON_ERROR_001");
            ex.setErrorDescription(e.getMessage());
            prepareAndLogResponse(AmzLiterals.STR_ERROR, doc, ex.getErrorDescription());
            throw ex;
        }
        
        logger.endTimer("class: AmzGetInventorySummaries | method: getInventorySummaries -- Starts");
        return outDoc;
    }
    
    public void prepareAndLogResponse(String processStatus, Document inDoc, String message) {

		logger.beginTimer("class: AmzProcessInTransitAndDeliveredEvent | method: prepareAndLogResponse -- Starts");

		String marketPlaceId = inDoc.getDocumentElement().getAttribute(AmzCommonConstants.AMZ_ATTRIBUTE_MARKETPLACE_ID);
		String invokedEventType = inDoc.getDocumentElement().getAttribute("InvokedEventType");

		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE, "AMZCONN_GET_INVENTORY_SUMMARIES");
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE, invokedEventType);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_EVENT, AmzLiterals.STR_RESPONSE);
		logInput.getDocumentElement().setAttribute(AmzLiterals.AMZ_ATTRIBUTE_MARKETPLACE_ID, marketPlaceId);
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
