package com.amazon.inventory.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.json.JSONObject;
import org.json.JSONException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzRestWebserviceUtil;
import com.amazon.common.util.AmzXMLUtil;
import com.amazon.integrator.inventory.api.AmzGetInventorySummaries;
import com.amazon.oms.inventory.api.AmzProcessInventoryChange;
import com.ibm.sterling.afc.jsonutil.PLTJSONUtils;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.ycp.japi.util.YCPBaseAgent;
import com.yantra.yfc.core.YFCIterable;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.dom.YFCDocument;
import com.yantra.yfc.dom.YFCElement;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCCommon;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/**
 * This is a custom agent class to Initiate and process the full sync of
 * inventory from Amazon to OMS
 *
 */
public class AmzProcessFullSyncAgent extends YCPBaseAgent {

	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzProcessFullSyncAgent.class);
	Map<String, String> genricPropertiesMap = new HashMap<>();
	Map<String, String> bwpPropertiesMap = new HashMap<>();	
	/**
	 * This method will Call Amazon InventorySummaries API, iterate through each
	 * item in the response and add them to a List. It will iteratively call the
	 * inventory summaries API with a nextToken till the last page is reached.
	 */
	@Override
	public List<Document> getJobs(YFSEnvironment env, Document criteria, Document lastMessageCreated)
			throws Exception {
		logger.debug("AmzProcessFullSyncAgent Criteria Doc:" + SCXmlUtil.getString(criteria));
		List<Document> listDocuments = new ArrayList<Document>();
		String nextToken = null;
		boolean isNextTokenAvailable = true;
		// Return empty list if the job ran once
		if(lastMessageCreated != null && lastMessageCreated.getDocumentElement() != null) {
			return listDocuments;
		}

		String marketplaceID = criteria.getDocumentElement().getAttribute(AmzCommonConstants.ATTR_MARKETPLACEID);
		String shipNode = criteria.getDocumentElement().getAttribute(AmzCommonConstants.ATTR_SHIPNODE);
		
		String strEnterpriseCode = criteria.getDocumentElement().getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
		String invOrgCode = criteria.getDocumentElement().getAttribute(AmzLiterals.ATTR_INV_ORG_CODE);
		if(YFCObject.isVoid(invOrgCode)) {
			invOrgCode = strEnterpriseCode;
		}
		
		Document propertyDoc = SCXmlUtil.createDocument("Properties");
		propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);
		genricPropertiesMap = AmzGetGenericProperty.getGenericProperties(env , propertyDoc);
		bwpPropertiesMap = AmzGetGenericProperty.getBWPIntegProperties(propertyDoc);
		
		String defaultUOM = genricPropertiesMap.get(AmzCommonConstants.PROP_DEFAULT_UOM);
		String defaultProductClass = genricPropertiesMap.get(AmzCommonConstants.PROP_DEFAULT_PRODUCT_CLASS);
		String sOMSItemIDPrefAmazonCatalog = genricPropertiesMap
				.get(AmzCommonConstants.PROP_AMZ_OMS_ITEMID_XREF_AMAZONCATALOG);
		logger.debug("amzConn.oms.ItemID.xref.amazonCatalog generic property value is: " + sOMSItemIDPrefAmazonCatalog);
		
		String granularityID = criteria.getDocumentElement().getAttribute("GranularityId");
		while (isNextTokenAvailable) {
			YFCDocument inputInventorySummariesDoc = YFCDocument.createDocument("InventorySummaries");

			inputInventorySummariesDoc.getDocumentElement()
						.setAttribute(AmzCommonConstants.AMZ_ATTRIBUTE_MARKETPLACE_ID, marketplaceID);


			inputInventorySummariesDoc.getDocumentElement()
					.setAttribute(AmzCommonConstants.AMZ_ATTRIBUTE_GRANULARITY_ID, granularityID);

			inputInventorySummariesDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);

			if (!YFCObject.isVoid(nextToken)) {
				inputInventorySummariesDoc.getDocumentElement()
						.setAttribute(AmzCommonConstants.AMZ_ATTRIBUTE_NEXT_TOKEN, nextToken);
			}
			
			inputInventorySummariesDoc.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE, "FULLSYNC");
			logger.debug("AmzProcessFullSyncAgent Get Inventory Summaries Input:"
					+ SCXmlUtil.getString(inputInventorySummariesDoc.getDocument()));
			Document outDoc = (new AmzGetInventorySummaries()).getInventorySummaries(env,
					inputInventorySummariesDoc.getDocument());
			logger.debug("AmzProcessFullSyncAgent Get Inventory Summaries Output:" + SCXmlUtil.getString(outDoc));
			validateResponseDoc(outDoc);
			
			//Changes for ItemId XReference - Starts
			HashMap<String, String> itemIDMap = new HashMap<>();
			
			if (AmzLiterals.A_JS_EXTERNAL_ID.equalsIgnoreCase(sOMSItemIDPrefAmazonCatalog)){
				NodeList inventorySummariesList = AmzXMLUtil.getXpathNodes(outDoc.getDocumentElement(),
						"/Root/payload/inventorySummaries");
				
				//This list will store all sellerSku's from getInventorySummaries output.
				ArrayList<String> sellerSkuList = new ArrayList<String>();
				
				for (int k = 0; k < inventorySummariesList.getLength(); k++) {
					Element eleInventorySummaries = (Element) inventorySummariesList.item(k);
					String sellerSku = eleInventorySummaries.getAttribute(AmzLiterals.A_SELLER_SKU);
					sellerSkuList.add(sellerSku);
				}
				
				int skuCount  = sellerSkuList.size();
				int batchSize = AmzCommonConstants.INT_PRODUCTS_BATCH_SIZE;
				
				//This will create map of sellerSku - externalID
				for (int i = 0; i < skuCount; i += batchSize) {
					int end = Math.min(i + batchSize, skuCount);
					String externalIds = "";
					for (int j = i; j < end; j++) {
						externalIds = externalIds.concat(" \"" + sellerSkuList.get(j) + "\",");
					}
					Document productsDoc = getProducts(externalIds, strEnterpriseCode);
					logger.debug("productsDoc: "+ SCXmlUtil.getString(productsDoc));
										
					for (int k = i; k < end; k++) {
						String externalID = AmzXMLUtil.getXpathAttribute(productsDoc.getDocumentElement(),
								"/Root/data/products/edges/node/amazonSku[@value='"
										+ sellerSkuList.get(k) + "']/../externalId/@value");
						itemIDMap.put(sellerSkuList.get(k), externalID);
					}
				}
				
			}
			//Changes for ItemId XReference - Ends
			
			if (!YFCObject.isVoid(outDoc)) {
				YFCDocument outputInventorySummariesDoc = YFCDocument.getDocumentFor(outDoc);
				YFCElement outputElem = outputInventorySummariesDoc.getDocumentElement();
				YFCElement paginationElem = outputElem.getChildElement(AmzCommonConstants.AMZ_ELEM_PAGINATION);
				if (paginationElem != null && paginationElem.getAttribute(AmzCommonConstants.AMZ_ATTRIBUTE_NEXT_TOKEN) != null) {
					nextToken = paginationElem.getAttribute(AmzCommonConstants.AMZ_ATTRIBUTE_NEXT_TOKEN);
					isNextTokenAvailable = true;
				} else {
					isNextTokenAvailable = false;
				}
				// iterate through items and add to List
				YFCElement payloadElem = outputElem.getChildElement(AmzCommonConstants.AMZ_ELEM_PAYLOAD);
				if (payloadElem != null) {
					YFCIterable<YFCElement> itemIterable = payloadElem.getChildren(AmzCommonConstants.AMZ_ELEM_INVENTORY_SUMMARIES);
					if (itemIterable != null) {
						while (itemIterable.hasNext()) {
							YFCElement inventorySummariesElem = itemIterable.next();
							if (inventorySummariesElem != null
									&& inventorySummariesElem.getChildElement(AmzCommonConstants.AMZ_ELEM_INVENTORY_DETAILS) != null) {
								YFCDocument inventoryItemsDoc = YFCDocument.createDocument(AmzCommonConstants.AMZ_ELEM_INVENTORY_ITEMS);
								YFCElement inventoryItemsElem = inventoryItemsDoc.getDocumentElement();
								setAttribute(inventoryItemsElem, AmzCommonConstants.ATTR_MARKETPLACEID, marketplaceID);
								setAttribute(inventoryItemsElem, AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);
								setAttribute(inventoryItemsElem, AmzCommonConstants.ATTR_SHIPNODE, shipNode);
								YFCElement itemElem = inventoryItemsElem.createChild(AmzCommonConstants.AMZ_ELEM_ITEM);
								
								//Changes for ItemId XReference - Starts
								if (AmzLiterals.A_JS_EXTERNAL_ID.equalsIgnoreCase(sOMSItemIDPrefAmazonCatalog)){
								setAttribute(itemElem, AmzCommonConstants.ATTR_ITEMID, itemIDMap.get(inventorySummariesElem.getAttribute(AmzCommonConstants.AMZ_ATTRIBUTE_SELLER_SKU)));	
								logger.debug("ItemID :" + itemIDMap.get(inventorySummariesElem.getAttribute(AmzCommonConstants.AMZ_ATTRIBUTE_SELLER_SKU)));
								}
								//Changes for ItemId XReference - Ends
								else {
								setAttribute(itemElem, AmzCommonConstants.ATTR_ITEMID, inventorySummariesElem.getAttribute(AmzCommonConstants.AMZ_ATTRIBUTE_SELLER_SKU));
								}
								setAttribute(itemElem, AmzLiterals.ATTR_INV_ORG_CODE, invOrgCode);
								setAttribute(itemElem, AmzCommonConstants.ATTR_AVAILABLE_QTY, inventorySummariesElem
										.getChildElement(AmzCommonConstants.AMZ_ELEM_INVENTORY_DETAILS).getAttribute(AmzCommonConstants.ATTR_FULFILLABLE_QTY));
								setAttribute(itemElem, AmzCommonConstants.ATTR_UOM, defaultUOM);
								setAttribute(itemElem, AmzCommonConstants.ATTR_PRODUCT_CLASS, defaultProductClass);
								setAttribute(itemElem, AmzCommonConstants.AMZ_ATTRIBUTE_SELLER_SKU, inventorySummariesElem.getAttribute(AmzCommonConstants.AMZ_ATTRIBUTE_SELLER_SKU));
							
								logger.debug("AmzProcessFullSyncAgent Adding Doc to List:"
										+ SCXmlUtil.getString(inventoryItemsDoc.getDocument()));
								listDocuments.add(inventoryItemsDoc.getDocument());								
							}
						}
					}
				}
			}
		}

		return listDocuments;
	}

	/**
	 * This method will take individual Item messages and update Inventory in GIV or
	 * IV based on what is enabled
	 */
	@Override
	public void executeJob(YFSEnvironment env, Document inputDoc) throws Exception {
		AmzProcessInventoryChange processIventoryChangeUtil = new AmzProcessInventoryChange();
		logger.debug("AmzProcessFullSyncAgent running execute job for:" + SCXmlUtil.getString(inputDoc));
		
		String strEnterpriseCode = SCXmlUtil.getXpathAttribute(inputDoc.getDocumentElement(), AmzLiterals.XPATH_FULLSYNC_ENTERPRISE_CODE);
		Document propertyDoc = SCXmlUtil.createDocument("Properties");
		propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);
		genricPropertiesMap = AmzGetGenericProperty.getGenericProperties(env , propertyDoc);
		
		String isIVEnabled = genricPropertiesMap.get(AmzCommonConstants.IV_PHASE2_ENABLED);
		logger.debug("AmzProcessFullSyncAgent execute job isIVEnabled:" + isIVEnabled);
		Document outDoc = null;
		
		String itemID = SCXmlUtil.getXpathAttribute(inputDoc.getDocumentElement(), AmzLiterals.XPATH_ITEMID);
		String sellerSku = SCXmlUtil.getXpathAttribute(inputDoc.getDocumentElement(), "/InventoryItems/Item/@sellerSku");

		if(YFCCommon.isVoid(itemID)) {
			YFSException customYFSException = new YFSException();
			customYFSException.setErrorCode("FULLSYNC_ITEM_ID_EMPTY");
			customYFSException.setErrorDescription("ItemID (externalId) is not found for the sellerSku= "+ sellerSku+" from Amazon GetProduct API .");
			prepareAndLogResponse(AmzLiterals.STR_ERROR,inputDoc, customYFSException.getErrorDescription());
			throw customYFSException;
		}
		
		if (isIVEnabled.equalsIgnoreCase(AmzCommonConstants.STR_VAL_Y)) {
			outDoc = processIventoryChangeUtil.amzProcessInventoryChangeForIV(env, inputDoc, genricPropertiesMap);
		} else {
			outDoc = processIventoryChangeUtil.amzProcessInventoryChangeForGIV(env, inputDoc);
		}
		logger.debug("AmzProcessFullSyncAgent execute job inventory processing outdoc:" + SCXmlUtil.getString(outDoc));

	}

	/**
	 * Simple util method to set an attribute if the value is non-null
	 */
	private void setAttribute(YFCElement itemElem, String attribName, String attribValue) {
		if (itemElem != null && !YFCObject.isVoid(attribValue)) {
			itemElem.setAttribute(attribName, attribValue);
		}

	}
	
	public Document getProducts(String externalIds, String strEnterpriseCode) throws Exception {
		logger.beginTimer("class: AmzProcessFullSyncAgent | method: getProduct -- Starts");
		logger.info("class: AmzProcessFullSyncAgent | method: getProduct -- Starts");
		Document outDoc = null;
		try {
			String targetId = bwpPropertiesMap.get(AmzCommonConstants.AMZ_TARGETID);
			String postURL = bwpPropertiesMap.get(AmzCommonConstants.AMZ_POST_URL);
			String apiAccessKey = bwpPropertiesMap.get(AmzCommonConstants.AMZ_API_ACCESS_KEY);
			String apiVersion = bwpPropertiesMap.get(AmzCommonConstants.AMZ_API_VERSION);

			String query = "query products {\r\n"
					+ "  products(\r\n"
					+ "    filter: {\r\n"
					+ "      amazonSku: {\r\n"
					+ "        anyOf: [\r\n"
					+ externalIds
					+ "		  \r\n"
					+ "        ]\r\n"
					+ "      }\r\n"
					+ "    }\r\n"
					+ "  ) {\r\n"
					+ "    pageInfo {\r\n"
					+ "      hasNextPage\r\n"
					+ "      hasPreviousPage\r\n"
					+ "      startCursor\r\n"
					+ "      endCursor\r\n"
					+ "    }\r\n"
					+ "    edges {\r\n"
					+ "      cursor\r\n"
					+ "      node {\r\n"
					+ "        id\r\n"
					+ "        ... on Product {\r\n"
					+ "          id\r\n"
					+ "          externalId {\r\n"
					+ "            value\r\n"
					+ "          }\r\n"
					+ "          sku {\r\n"
					+ "            value\r\n"
					+ "          }\r\n"
					+ "          amazonSku {\r\n"
					+ "            marketplaceId\r\n"
					+ "            value\r\n"
					+ "          }\r\n"
					+ "          offerPrime\r\n"
					+ "          productDetailPageUrl\r\n"
					+ "          image {\r\n"
					+ "            displayReadyUrl\r\n"
					+ "            sourceUrl\r\n"
					+ "          }\r\n"
					+ "          buyability {\r\n"
					+ "            status\r\n"
					+ "          }\r\n"
					+ "          inventoryItem {\r\n"
					+ "            inventoryItemId\r\n"
					+ "            buyableQuantity {\r\n"
					+ "              amount\r\n"
					+ "              unit\r\n"
					+ "            }\r\n"
					+ "          }\r\n"
					+ "        }\r\n"
					+ "      }\r\n"
					+ "    }\r\n"
					+ "  }\r\n"
					+ "}\r\n"
					+ "";

			
			JSONObject payload = new JSONObject();
			payload.put("query", query);

			logger.debug("query: "+ query);
			StringEntity requestEntity = new StringEntity(payload.toString(), ContentType.APPLICATION_JSON);

			Map<String, String> headerMap = new HashMap<>();
			headerMap.put("Content-Type", "application/json");
			headerMap.put("authorization",
					"Bearer" + " " + AmzRestWebserviceUtil.getAuthorizationToken(strEnterpriseCode));
			headerMap.put("X-Omni-TargetId", targetId);
			headerMap.put("x-api-access-key", apiAccessKey);
			headerMap.put("x-api-version", apiVersion);

			String output = AmzRestWebserviceUtil.invokePost(postURL, 10, requestEntity, headerMap);

			JSONObject outputJson = new JSONObject(output);
			 if (! outputJson.has("data")) {
				 AmzCommonUtil.validateResponseMessage(output);
			 }
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
		logger.info("class: AmzProcessFullSyncAgent | method: getProduct -- Ends");
		logger.endTimer("class: AmzProcessFullSyncAgent | method: getProduct -- Ends");
		return outDoc;

	}
	
	public void validateResponseDoc(Document output) throws Exception {
		logger.beginTimer("class: AmzProcessFullSyncAgent | method: validateResponseDoc -- Starts");
		logger.info("class: AmzProcessFullSyncAgent | method: validateResponseDoc -- Starts");
		
		Element eleOutput = output.getDocumentElement();
		Element eleError = SCXmlUtil.getChildElement(eleOutput, "Error");
		if(!YFCCommon.isVoid(eleError)) {
			YFSException ex = new YFSException();
			ex.setErrorCode(eleError.getAttribute("ErrorCode"));
			ex.setErrorDescription(eleError.getAttribute("ErrorDescription"));
			throw ex;
		}
		logger.info("class: AmzProcessFullSyncAgent | method: validateResponseDoc -- Ends");
		logger.endTimer("class: AmzProcessFullSyncAgent | method: validateResponseDoc -- Ends");
		
		
	}
	
	public void prepareAndLogResponse(String processStatus, Document indoc, String message) {

		logger.beginTimer("class: AmzProcessFullSyncAgent | method: prepareAndLogResponse -- Starts");
		
		String sellerSku = SCXmlUtil.getXpathAttribute(indoc.getDocumentElement(), "/InventoryItems/Item/@sellerSku");

		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE, "FULLSYNC");
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_ACTION, "OMS-PROCESS");
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_SELLER_SKU, sellerSku);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_PROCESS_STATUS, processStatus);

		if (!YFCObject.isVoid(message)) {
			if (processStatus.equalsIgnoreCase(AmzLiterals.STR_SUCCESS)) {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_MESSAGE, message);
			} else {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_ERROR_MSG, message);
			}
		}
		AmzCommonUtil.logAmzConnResponse(logInput);

		logger.endTimer("class: AmzProcessFullSyncAgent | method: prepareAndLogResponse -- Ends");
	}

}
