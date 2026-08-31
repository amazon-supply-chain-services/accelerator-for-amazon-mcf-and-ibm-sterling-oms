package com.amazon.inventory.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.integrator.inventory.api.AmzGetInventorySummaries;
import com.amazon.oms.inventory.api.AmzProcessInventoryChange;
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
 * MCF version of Full Sync Agent. Uses sellerSku from
 * getInventorySummaries directly as the OMS ItemID.
 * No BWP getProducts call for externalId resolution.
 */
public class AmzMCFProcessFullSyncAgent extends YCPBaseAgent {

	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzMCFProcessFullSyncAgent.class);
	Map<String, String> genricPropertiesMap = new HashMap<>();

	@Override
	public List<Document> getJobs(YFSEnvironment env, Document criteria, Document lastMessageCreated)
			throws Exception {
		logger.debug("AmzMCFProcessFullSyncAgent Criteria Doc:" + SCXmlUtil.getString(criteria));
		List<Document> listDocuments = new ArrayList<Document>();
		String nextToken = null;
		boolean isNextTokenAvailable = true;

		// Return empty list if the job ran once
		if (lastMessageCreated != null && lastMessageCreated.getDocumentElement() != null) {
			return listDocuments;
		}

		String marketplaceID = criteria.getDocumentElement().getAttribute(AmzCommonConstants.ATTR_MARKETPLACEID);
		String shipNode = criteria.getDocumentElement().getAttribute(AmzCommonConstants.ATTR_SHIPNODE);
		String strEnterpriseCode = criteria.getDocumentElement().getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
		String invOrgCode = criteria.getDocumentElement().getAttribute(AmzLiterals.ATTR_INV_ORG_CODE);
		if (YFCObject.isVoid(invOrgCode)) {
			invOrgCode = strEnterpriseCode;
		}

		Document propertyDoc = SCXmlUtil.createDocument("Properties");
		propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);
		genricPropertiesMap = AmzGetGenericProperty.getGenericProperties(env, propertyDoc);

		String defaultUOM = genricPropertiesMap.get(AmzCommonConstants.PROP_DEFAULT_UOM);
		String defaultProductClass = genricPropertiesMap.get(AmzCommonConstants.PROP_DEFAULT_PRODUCT_CLASS);

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
			logger.debug("AmzMCFProcessFullSyncAgent Get Inventory Summaries Input:"
					+ SCXmlUtil.getString(inputInventorySummariesDoc.getDocument()));
			Document outDoc = (new AmzGetInventorySummaries()).getInventorySummaries(env,
					inputInventorySummariesDoc.getDocument());
			logger.debug("AmzMCFProcessFullSyncAgent Get Inventory Summaries Output:" + SCXmlUtil.getString(outDoc));
			validateResponseDoc(outDoc);

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

								// Use sellerSku directly as ItemID
								setAttribute(itemElem, AmzCommonConstants.ATTR_ITEMID,
										inventorySummariesElem.getAttribute(AmzCommonConstants.AMZ_ATTRIBUTE_SELLER_SKU));

								setAttribute(itemElem, AmzLiterals.ATTR_INV_ORG_CODE, invOrgCode);
								setAttribute(itemElem, AmzCommonConstants.ATTR_AVAILABLE_QTY, inventorySummariesElem
										.getChildElement(AmzCommonConstants.AMZ_ELEM_INVENTORY_DETAILS).getAttribute(AmzCommonConstants.ATTR_FULFILLABLE_QTY));
								setAttribute(itemElem, AmzCommonConstants.ATTR_UOM, defaultUOM);
								setAttribute(itemElem, AmzCommonConstants.ATTR_PRODUCT_CLASS, defaultProductClass);
								setAttribute(itemElem, AmzCommonConstants.AMZ_ATTRIBUTE_SELLER_SKU,
										inventorySummariesElem.getAttribute(AmzCommonConstants.AMZ_ATTRIBUTE_SELLER_SKU));

								logger.debug("AmzMCFProcessFullSyncAgent Adding Doc to List:"
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

	@Override
	public void executeJob(YFSEnvironment env, Document inputDoc) throws Exception {
		AmzProcessInventoryChange processIventoryChangeUtil = new AmzProcessInventoryChange();
		logger.debug("AmzMCFProcessFullSyncAgent running execute job for:" + SCXmlUtil.getString(inputDoc));

		String strEnterpriseCode = SCXmlUtil.getXpathAttribute(inputDoc.getDocumentElement(), AmzLiterals.XPATH_FULLSYNC_ENTERPRISE_CODE);
		Document propertyDoc = SCXmlUtil.createDocument("Properties");
		propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);
		genricPropertiesMap = AmzGetGenericProperty.getGenericProperties(env, propertyDoc);

		String isIVEnabled = genricPropertiesMap.get(AmzCommonConstants.IV_PHASE2_ENABLED);
		logger.debug("AmzMCFProcessFullSyncAgent execute job isIVEnabled:" + isIVEnabled);
		Document outDoc = null;

		if (isIVEnabled.equalsIgnoreCase(AmzCommonConstants.STR_VAL_Y)) {
			outDoc = processIventoryChangeUtil.amzProcessInventoryChangeForIV(env, inputDoc, genricPropertiesMap);
		} else {
			outDoc = processIventoryChangeUtil.amzProcessInventoryChangeForGIV(env, inputDoc);
		}
		logger.debug("AmzMCFProcessFullSyncAgent execute job inventory processing outdoc:" + SCXmlUtil.getString(outDoc));
	}

	private void setAttribute(YFCElement itemElem, String attribName, String attribValue) {
		if (itemElem != null && !YFCObject.isVoid(attribValue)) {
			itemElem.setAttribute(attribName, attribValue);
		}
	}

	public void validateResponseDoc(Document output) throws Exception {
		logger.beginTimer("class: AmzMCFProcessFullSyncAgent | method: validateResponseDoc -- Starts");
		logger.info("class: AmzMCFProcessFullSyncAgent | method: validateResponseDoc -- Starts");

		Element eleOutput = output.getDocumentElement();
		Element eleError = SCXmlUtil.getChildElement(eleOutput, "Error");
		if (!YFCCommon.isVoid(eleError)) {
			YFSException ex = new YFSException();
			ex.setErrorCode(eleError.getAttribute("ErrorCode"));
			ex.setErrorDescription(eleError.getAttribute("ErrorDescription"));
			throw ex;
		}
		logger.info("class: AmzMCFProcessFullSyncAgent | method: validateResponseDoc -- Ends");
		logger.endTimer("class: AmzMCFProcessFullSyncAgent | method: validateResponseDoc -- Ends");
	}

	public void prepareAndLogResponse(String processStatus, Document indoc, String message) {
		logger.beginTimer("class: AmzMCFProcessFullSyncAgent | method: prepareAndLogResponse -- Starts");

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

		logger.endTimer("class: AmzMCFProcessFullSyncAgent | method: prepareAndLogResponse -- Ends");
	}
}
