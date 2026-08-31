package com.amazon.oms.inventory.api;

import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzEnterpriseBPIDXRefUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzOrderMutations;
import com.amazon.common.util.AmzRestWebserviceUtil;
import com.ibm.sterling.afc.jsonutil.PLTJSONUtils;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCCommon;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/**
 * This class is invoked through a async service 'AmzConnProcessInvChange' which will pick message from 'AMZ.CONN.INV.INB.Q' internal queue. 
 * This method will call getInventoryItem bwp API with InventoryItemId received in input and return the sellerSku
 * which is required to call SP-API getInventorySummaries.
 * getInventorySummaries API will return the fulfillableQuantity.
 * Based on IV phase enabled property, 
 * If IV is enabled then, will invoke search aggregate demands with custom IV utility to get the totalQuantity
 * which will be added to fulfillableQuantity received from getInventorySummaries. And with sync supply update inventory in IV.
 * Else invoke processAvailabilitySnapShot api to update inventory in GIV.
 */

public class AmzProcessInventoryChange {
	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzProcessInventoryChange.class);
	
	Map<String, String> genricPropertiesMap = new HashMap<>();	
	Map<String, String> bwpPropertiesMap = new HashMap<>();	
	public Document amzProcessInventoryChange(YFSEnvironment env, Document inputDoc) throws Exception {
		logger.beginTimer("class: AmzProcessInventoryChange | method: amzProcessInventoryChange -- Starts");
		logger.info("class: AmzProcessInventoryChange | method: amzProcessInventoryChange -- Starts");

		Document outDoc = null;

		Element eleInputDoc = inputDoc.getDocumentElement();
		
		String eventType = eleInputDoc.getAttribute("EventType");
		if(!eventType.equals("INVENTORY_CHANGED")) {
			YFSException ex =  new YFSException();
			ex.setErrorCode("INPUT_ERROR_002");
			ex.setErrorDescription("Invalid Event Type Received.");
			throw ex;
        }
				
		Element eleItem = SCXmlUtil.getChildElement(eleInputDoc, "Item");

		String businessProductID = eleInputDoc.getAttribute("BusinessProductID");
		
		String strEnterpriseCode = eleItem.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
		
		if(YFCCommon.isVoid(strEnterpriseCode) && !YFCCommon.isVoid(businessProductID)) {		
			strEnterpriseCode = AmzEnterpriseBPIDXRefUtil.getEnterpriseCode(env, eleInputDoc.getAttribute("BusinessProductID"));
		}
		
		Document propertyDoc = SCXmlUtil.createDocument("Properties");
		propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);
		genricPropertiesMap = AmzGetGenericProperty.getGenericProperties(env , propertyDoc);
		bwpPropertiesMap = AmzGetGenericProperty.getBWPIntegProperties(propertyDoc);
		
		String strMarketPlaceId = genricPropertiesMap.get(AmzCommonConstants.PROP_MARKETPLACE_ID);
		logger.debug("strMarketPlaceId: "+ strMarketPlaceId);
		String sOMSItemIDPrefAmazonCatalog = genricPropertiesMap
				.get(AmzCommonConstants.PROP_AMZ_OMS_ITEMID_XREF_AMAZONCATALOG);
		logger.debug("amzConn.oms.ItemID.xref.amazonCatalog generic property value is: " + sOMSItemIDPrefAmazonCatalog);

	
		String shipNode = genricPropertiesMap.get(AmzCommonConstants.PROP_AMZ_SHIP_NODE+ strMarketPlaceId);
				
		String strInventoryItemId = eleItem.getAttribute("InventoryItemId");
		String strSellerSku = null;
		String strItemID = eleItem.getAttribute("ItemID");
		String strExternalId = null;
		if (AmzLiterals.A_JS_AMAZON_SKU.equalsIgnoreCase(sOMSItemIDPrefAmazonCatalog) && !YFCObject.isVoid(strItemID)) {
			strSellerSku = strItemID;
		} else if (AmzLiterals.A_JS_EXTERNAL_ID.equalsIgnoreCase(sOMSItemIDPrefAmazonCatalog)
				&& !YFCObject.isVoid(strItemID)) {
			Document getProductOut = getProduct(strItemID, strEnterpriseCode);
			if (!YFCObject.isVoid(getProductOut)) {
				strSellerSku = SCXmlUtil.getXpathAttribute(getProductOut.getDocumentElement(),
						"/Root/data/product/amazonSku/@value");
				strExternalId = SCXmlUtil.getXpathAttribute(getProductOut.getDocumentElement(),
						"/Root/data/product/externalId/@value");
				
			}
		}
		Document getInventoryItemDoc = null;
		
		if(YFCCommon.isVoid(strSellerSku)) {
		getInventoryItemDoc = getInvetoryItem(strInventoryItemId, strEnterpriseCode);

		logger.debug("getInventoryItemDoc:" + SCXmlUtil.getString(getInventoryItemDoc));

			strSellerSku = SCXmlUtil.getXpathAttribute(getInventoryItemDoc.getDocumentElement(),
					"/Root/data/inventoryItem/product/amazonSku/@value");
			
			strExternalId = SCXmlUtil.getXpathAttribute(getInventoryItemDoc.getDocumentElement(),
					"/Root/data/inventoryItem/product/externalId/@value");
		}
		logger.debug("strSellerSku is: "+strSellerSku);
		logger.debug("strExternalId is: "+strExternalId);
		Document getInvetorySummaryInput = SCXmlUtil.createDocument("InventoryItems");
		getInvetorySummaryInput.getDocumentElement().setAttribute("sellerSku", strSellerSku);
		getInvetorySummaryInput.getDocumentElement().setAttribute("marketPlaceId", strMarketPlaceId);
		
		String invokedEventType = eleInputDoc.getAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE);
		if(YFCCommon.isVoid(invokedEventType)) {
			invokedEventType = "INTRADAY-INV-SYNC";
		}
		getInvetorySummaryInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE, invokedEventType);
		getInvetorySummaryInput.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);

		Document getInvetorySummaryOutput = AmzCommonUtil.callService(env, getInvetorySummaryInput,
				"AmzGetInventorySummaries", null);

		logger.debug("getInvetorySummaryOutput:" + SCXmlUtil.getString(getInvetorySummaryOutput));

		eleInputDoc.setAttribute(AmzLiterals.A_SHIP_NODE, shipNode);
		if (AmzLiterals.A_JS_AMAZON_SKU.equalsIgnoreCase(sOMSItemIDPrefAmazonCatalog)) {
			eleItem.setAttribute("ItemID", strSellerSku);
		} else if (AmzLiterals.A_JS_EXTERNAL_ID.equalsIgnoreCase(sOMSItemIDPrefAmazonCatalog)){
			eleItem.setAttribute("ItemID", strExternalId);
		}
		eleItem.setAttribute(AmzLiterals.ATTR_INV_ORG_CODE, strEnterpriseCode);
		eleItem.setAttribute("AvailableQty", SCXmlUtil.getXpathAttribute(getInvetorySummaryOutput.getDocumentElement(),
				"/Root/payload/inventorySummaries/inventoryDetails/@fulfillableQuantity"));
		eleItem.setAttribute("UnitOfMeasure", genricPropertiesMap.get(AmzCommonConstants.PROP_DEFAULT_UOM));
		eleItem.setAttribute("ProductClass", genricPropertiesMap.get(AmzCommonConstants.PROP_DEFAULT_PRODUCT_CLASS));
		logger.debug("inputDoc after modification:" + SCXmlUtil.getString(inputDoc));

		String strIsIVEnabled = genricPropertiesMap.get(AmzCommonConstants.IV_PHASE2_ENABLED);
		if (strIsIVEnabled.equalsIgnoreCase("Y")) {
			outDoc = amzProcessInventoryChangeForIV(env, inputDoc, genricPropertiesMap);
		} else {
			outDoc = amzProcessInventoryChangeForGIV(env, inputDoc);
		}

		logger.info("class: AmzProcessInventoryChange | method: amzProcessInventoryChange -- Ends");
		logger.endTimer("class: AmzProcessInventoryChange | method: amzProcessInventoryChange -- Ends");
		return outDoc;
	}

	public Document amzProcessInventoryChangeForGIV(YFSEnvironment env, Document inputDoc) throws Exception {
		logger.beginTimer("class: AmzProcessInventoryChange | method: amzProcessInventoryChangeForGIV -- Starts");
		logger.info("class: AmzProcessInventoryChange | method: amzProcessInventoryChangeForGIV -- Starts");

		try {

			Element eleInputDoc = inputDoc.getDocumentElement();

			Document availabilitySnapShotDoc = SCXmlUtil.createDocument("AvailabilitySnapShot");
			Element eleAvailabilitySnapShotDoc = availabilitySnapShotDoc.getDocumentElement();
			Element eleShipNode = SCXmlUtil.createChild(eleAvailabilitySnapShotDoc, AmzLiterals.A_SHIP_NODE);
			eleShipNode.setAttribute("CompleteInventoryFlag", "N");
			eleShipNode.setAttribute(AmzLiterals.A_SHIP_NODE,
					SCXmlUtil.getXpathAttribute(eleInputDoc, AmzLiterals.XPATH_SHIP_NODE));

			Element eleItem = SCXmlUtil.createChild(eleShipNode, "Item");
			eleItem.setAttribute("ItemID", SCXmlUtil.getXpathAttribute(eleInputDoc, AmzLiterals.XPATH_ITEMID));
			eleItem.setAttribute(AmzLiterals.ATTR_INV_ORG_CODE, SCXmlUtil.getXpathAttribute(eleInputDoc, AmzLiterals.XPATH_INV_ORG_CODE));
			eleItem.setAttribute("ProductClass",
					SCXmlUtil.getXpathAttribute(eleInputDoc, AmzLiterals.XPATH_PRODUCT_CLASS));
			eleItem.setAttribute("UnitOfMeasure", SCXmlUtil.getXpathAttribute(eleInputDoc, AmzLiterals.XPATH_UOM));
			Element eleAvailabilityDetails = SCXmlUtil.createChild(eleItem, "AvailabilityDetails");
			eleAvailabilityDetails.setAttribute("Quantity",
					SCXmlUtil.getXpathAttribute(eleInputDoc, AmzLiterals.XPATH_AVAIL_QTY));

			AmzCommonUtil.callAPI(env, availabilitySnapShotDoc, "processAvailabilitySnapShot", null);

			logger.debug("availabilitySnapShotDoc:" + SCXmlUtil.getString(availabilitySnapShotDoc));
		} catch (YFSException e) {
			throw AmzCommonUtil.createException(e);
		}

		logger.info("class: AmzProcessInventoryChange | method: amzProcessInventoryChangeForGIV -- Ends");
		logger.endTimer("class: AmzProcessInventoryChange | method: amzProcessInventoryChangeForGIV -- Ends");
		return inputDoc;

	}

	public Document amzProcessInventoryChangeForIV(YFSEnvironment env, Document inputDoc, Map<String, String> genricPropertiesMap) throws Exception {
		logger.beginTimer("class: AmzProcessInventoryChange | method: amzProcessInventoryChangeForIV -- Starts");
		logger.info("class: AmzProcessInventoryChange | method: amzProcessInventoryChangeForIV -- Starts");

		try {

			Element eleInputDoc = inputDoc.getDocumentElement();

			String baseIVUrl = genricPropertiesMap.get(AmzCommonConstants.IV_BASE_URL);
			String tenantID = genricPropertiesMap.get(AmzCommonConstants.IV_TENANT_ID);
			String searchDemandUrl = baseIVUrl + "/inventory/" + tenantID + "/v2/demands/aggregate-requests?";

			JSONObject jsObjectForAggDemand = createJSONRequestForDemand(inputDoc, genricPropertiesMap);

			logger.debug("jsObjectForAggDemand" + jsObjectForAggDemand);

			Document aggDemandOutDoc = invokeSDFUtilityService(env, "POST", searchDemandUrl, jsObjectForAggDemand);

			Element eleArrDemandOutDoc = aggDemandOutDoc.getDocumentElement();

			String statusCode = SCXmlUtil.getXpathAttribute(eleArrDemandOutDoc,
					"/InventoryVisibilityAPI/Output/@Status");

			double aggregatedDemandQty  = 0;

			if ("200".equalsIgnoreCase(statusCode)) {
				Element eleOutput = SCXmlUtil.getChildElement(eleArrDemandOutDoc, "Output");
				String data = eleOutput.getTextContent();

				JSONObject dataJSON = new JSONObject(data);
				JSONArray dataArray = dataJSON.getJSONArray("data");
				
				if(dataArray.length() > 0) {
				JSONArray aggregationsArr = dataArray.getJSONObject(0).getJSONArray("aggregations");

				for (int i = 0; i < aggregationsArr.length(); i++) {
					JSONObject aggregationsObj = aggregationsArr.getJSONObject(i);
					aggregatedDemandQty  += aggregationsObj.getDouble("totalQuantity");
				}			
				}
				
				String availableQty = SCXmlUtil.getXpathAttribute(eleInputDoc, AmzLiterals.XPATH_AVAIL_QTY);

				double supplyQty = Double.parseDouble(availableQty) + aggregatedDemandQty ;

				Element eleItem = SCXmlUtil.getChildElement(eleInputDoc, "Item");
				eleItem.setAttribute("AvailableQty", String.valueOf(supplyQty));

				String syncSupplyUrl = baseIVUrl + "/inventory/" + tenantID + "/v1/supplies?";

				JSONObject jsObjectForSyncSupply = createJSONRequestForSyncSupply(inputDoc);

				Document syncSupplyOutDoc = invokeSDFUtilityService(env, "PUT", syncSupplyUrl, jsObjectForSyncSupply);

				logger.debug("syncSupplyOutDoc" + SCXmlUtil.getString(syncSupplyOutDoc));
			} else {
				YFSException ex = new YFSException();
				ex.setErrorCode("OMS_API_ERROR_001");
				ex.setErrorDescription("Inventory change update failed");
				throw ex;
			}
		} catch (YFSException e) {
			throw AmzCommonUtil.createException(e);
		}

		logger.info("class: AmzProcessInventoryChange | method: amzProcessInventoryChangeForIV -- Ends");
		logger.endTimer("class: AmzProcessInventoryChange | method: amzProcessInventoryChangeForIV -- Ends");
		return inputDoc;

	}

	public Document invokeSDFUtilityService(YFSEnvironment env, String httpMethod, String url, JSONObject requestJSON)
			throws Exception {
		logger.beginTimer("class: AmzProcessInventoryChange | method: invokeSDFUtilityService -- Starts");
		logger.info("class: AmzProcessInventoryChange | method: invokeSDFUtilityService -- Starts");

		Document outputDoc = null;
		Document inputDoc = SCXmlUtil.createDocument("InventoryVisibilityAPI");
		Element eleInputDoc = inputDoc.getDocumentElement();
		eleInputDoc.setAttribute("Content-Type", "application/json");
		eleInputDoc.setAttribute("HTTPMethod", httpMethod);
		eleInputDoc.setAttribute("URL", url);

		Element eleInput = SCXmlUtil.createChild(eleInputDoc, "Input");
		eleInput.setTextContent(requestJSON.toString());
		try {
			outputDoc = AmzCommonUtil.callService(env, inputDoc, "CustomIVInvokeRestAPI", null);
		} catch (YFSException e) {
			throw AmzCommonUtil.createException(e);
		}

		logger.info("class: AmzProcessInventoryChange | method: invokeSDFUtilityService -- Ends");
		logger.endTimer("class: AmzProcessInventoryChange | method: invokeSDFUtilityService -- Ends");
		return outputDoc;
	}

	private JSONObject createJSONRequestForDemand(Document inputDoc, Map<String, String> genricPropertiesMap) throws JSONException, YFSException {
		logger.beginTimer("class: AmzProcessInventoryChange | method: createJSONRequestForDemand -- Starts");
		logger.info("class: AmzProcessInventoryChange | method: createJSONRequestForDemand -- Starts");

		Element eleInputDoc = inputDoc.getDocumentElement();

		String strDemandType = genricPropertiesMap.get(AmzCommonConstants.AMZ_DEMAND_TYPES);

		String[] arrDemandType = strDemandType.split("/");

		JSONObject aggregations = new JSONObject();
		aggregations.put("field", "type");

		JSONObject shipNode = new JSONObject();
		shipNode.put("operator", "equals");
		JSONArray shipNodeValues = new JSONArray();
		shipNodeValues.put(SCXmlUtil.getXpathAttribute(eleInputDoc, AmzLiterals.XPATH_SHIP_NODE));
		shipNode.put("values", shipNodeValues);

		JSONObject type = new JSONObject();
		type.put("operator", "equals");
		JSONArray typeValues = new JSONArray();
		for (String demandType : arrDemandType) {
			typeValues.put(demandType);
		}
		type.put("values", typeValues);

		JSONObject data = new JSONObject();
		data.put("itemId", SCXmlUtil.getXpathAttribute(eleInputDoc, AmzLiterals.XPATH_ITEMID));
		data.put("productClass", SCXmlUtil.getXpathAttribute(eleInputDoc, AmzLiterals.XPATH_PRODUCT_CLASS));
		data.put("unitOfMeasure", SCXmlUtil.getXpathAttribute(eleInputDoc, AmzLiterals.XPATH_UOM));
		data.put("shipNode", shipNode);
		data.put("type", type);

		JSONObject jsonRequest = new JSONObject();
		jsonRequest.put("data", data);
		jsonRequest.put("aggregations", aggregations);

		logger.info("class: AmzProcessInventoryChange | method: createJSONRequestForDemand -- Ends");
		logger.endTimer("class: AmzProcessInventoryChange | method: createJSONRequestForDemand -- Ends");
		return jsonRequest;

	}

	private JSONObject createJSONRequestForSyncSupply(Document inputDoc) throws JSONException, YFSException {
		logger.beginTimer("class: AmzProcessInventoryChange | method: createJSONRequestForSyncSupply -- Starts");
		logger.info("class: AmzProcessInventoryChange | method: createJSONRequestForSyncSupply -- Starts");

		Element eleInputDoc = inputDoc.getDocumentElement();

		JSONArray supplies = new JSONArray();

		JSONObject supply = new JSONObject();
		supply.put("itemId", SCXmlUtil.getXpathAttribute(eleInputDoc, "/InventoryItems/Item/@ItemID"));
		supply.put("productClass", SCXmlUtil.getXpathAttribute(eleInputDoc, "/InventoryItems/Item/@ProductClass"));
		supply.put("unitOfMeasure", SCXmlUtil.getXpathAttribute(eleInputDoc, "/InventoryItems/Item/@UnitOfMeasure"));
		supply.put("shipNode", SCXmlUtil.getXpathAttribute(eleInputDoc, "/InventoryItems/@ShipNode"));
		supply.put("type", "ONHAND");
		supply.put("quantity", SCXmlUtil.getXpathAttribute(eleInputDoc, "/InventoryItems/Item/@AvailableQty"));

		supplies.put(supply);

		JSONObject jsonRequest = new JSONObject();
		jsonRequest.put("supplies", supplies);
		jsonRequest.put("enableFulfillmentOptionOnlyWhenQtyChanges", "true");

		logger.info("class: AmzProcessInventoryChange | method: createJSONRequestForSyncSupply -- Ends");
		logger.endTimer("class: AmzProcessInventoryChange | method: createJSONRequestForSyncSupply -- Ends");
		return jsonRequest;

	}

	public Document getInvetoryItem(String itemId, String enterpriseCode) throws Exception {
		logger.beginTimer("class: AmzProcessInventoryChange | method: getInvetoryItem -- Starts");
		logger.info("class: AmzProcessInventoryChange | method: getInvetoryItem -- Starts");
		Document outDoc = null;
		try {
		String targetId = bwpPropertiesMap.get(AmzCommonConstants.AMZ_TARGETID);
		String postURL = bwpPropertiesMap.get(AmzCommonConstants.AMZ_POST_URL);
		String apiAccessKey = bwpPropertiesMap.get(AmzCommonConstants.AMZ_API_ACCESS_KEY);
		String apiVersion = bwpPropertiesMap.get(AmzCommonConstants.AMZ_API_VERSION);
		String query = AmzOrderMutations.AMZ_GET_INENTORY_ITEM;

		JSONObject variables = new JSONObject();
		variables.put("id", itemId);

		JSONObject payload = new JSONObject();
		payload.put("query", query);
		payload.put("variables", variables);

		StringEntity requestEntity = new StringEntity(payload.toString(), ContentType.APPLICATION_JSON);

		Map<String, String> headerMap = new HashMap<>();
		headerMap.put("Content-Type", "application/json");
		headerMap.put("authorization", "Bearer" + " " + AmzRestWebserviceUtil.getAuthorizationToken(enterpriseCode));
		headerMap.put("X-Omni-TargetId", targetId);
		headerMap.put("x-api-access-key", apiAccessKey);
		headerMap.put("x-api-version", apiVersion);

		String output = AmzRestWebserviceUtil.invokePost(postURL, 10, requestEntity, headerMap);
		
		AmzCommonUtil.validateResponseMessage(output);
		
		outDoc = PLTJSONUtils.getXmlFromJSON(output, "Root");
		logger.debug("outDoc:" + SCXmlUtil.getString(outDoc));
		
		}
		catch (YFSException e) {
	        e.printStackTrace();
	        throw e;
	    } catch (JSONException e) {
	        e.printStackTrace();
	        YFSException ex = new YFSException();
	        ex.setErrorCode("JSON_ERROR_001");
	        ex.setErrorDescription(e.getMessage());
	        throw AmzCommonUtil.createException(ex);
	    }
		logger.info("class: AmzProcessInventoryChange | method: getInvetoryItem -- Ends");
		logger.endTimer("class: AmzProcessInventoryChange | method: getInvetoryItem -- Ends");
		return outDoc;

	}
	
	public Document getProduct(String strItemID, String strEnterpriseCode) throws Exception {
		logger.beginTimer("class: AmzProcessInventoryChange | method: getProduct -- Starts");
		logger.info("class: AmzProcessInventoryChange | method: getProduct -- Starts");
		Document outDoc = null;
		try {
			String targetId = bwpPropertiesMap.get(AmzCommonConstants.AMZ_TARGETID);
			String postURL = bwpPropertiesMap.get(AmzCommonConstants.AMZ_POST_URL);
			String apiAccessKey = bwpPropertiesMap.get(AmzCommonConstants.AMZ_API_ACCESS_KEY);
			String apiVersion = bwpPropertiesMap.get(AmzCommonConstants.AMZ_API_VERSION);
			String query = AmzOrderMutations.AMZ_GET_PRODUCT;
			JSONObject variables = new JSONObject();
			JSONObject identifier = new JSONObject();
			identifier.put("externalId", strItemID);
			variables.put("identifier", identifier);

			JSONObject payload = new JSONObject();
			payload.put("query", query);
			payload.put("variables", variables);

			StringEntity requestEntity = new StringEntity(payload.toString(), ContentType.APPLICATION_JSON);

			Map<String, String> headerMap = new HashMap<>();
			headerMap.put("Content-Type", "application/json");
			headerMap.put("authorization",
					"Bearer" + " " + AmzRestWebserviceUtil.getAuthorizationToken(strEnterpriseCode));
			headerMap.put("X-Omni-TargetId", targetId);
			headerMap.put("x-api-access-key", apiAccessKey);
			headerMap.put("x-api-version", apiVersion);

			String output = AmzRestWebserviceUtil.invokePost(postURL, 10, requestEntity, headerMap);

			AmzCommonUtil.validateResponseMessage(output);

			outDoc = PLTJSONUtils.getXmlFromJSON(output, "Root");
			logger.debug("outDoc:" + SCXmlUtil.getString(outDoc));

		} catch (YFSException e) {
			e.printStackTrace();
			throw e;
		} catch (JSONException e) {
			e.printStackTrace();
			YFSException ex = new YFSException();
			ex.setErrorCode("JSON_ERROR_001");
			ex.setErrorDescription(e.getMessage());
			throw AmzCommonUtil.createException(ex);
		}
		logger.info("class: AmzProcessInventoryChange | method: getProduct -- Ends");
		logger.endTimer("class: AmzProcessInventoryChange | method: getProduct -- Ends");
		return outDoc;

	}
}
