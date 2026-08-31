package com.amazon.integrator.delivery.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzRestWebserviceUtil;
import com.ibm.sterling.afc.jsonutil.PLTJSONUtils;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/**
 * Calls the SP-API ListFulfillmentOrders endpoint to retrieve MCF orders updated after a given timestamp.
 *
 * SP-API endpoint:
 *   GET /fulfillment/outbound/2026-07-04/orders?updatedAfter={timestamp}&shipments=INCLUDE
 *
 * Handles pagination internally — loops until no more nextToken is returned.
 * Accumulates all fulfillmentOrders elements across pages into a single output document.
 *
 * Input:
 *   <Order EnterpriseCode="DEFAULT" UpdatedAfter="2025-01-01T00:00:00Z"/>
 *
 * Output (per page, JSON response converted to XML):
 *   <Payload>
 *     <fulfillmentOrders orderId="322-ZU58-6J6E5P" status="PROCESSING" ...>
 *       <lineItems lineItemId="...">
 *         <amount unit="EACHES" value="2"/>
 *         <product><productIdentifier amazonSku="..."/></product>
 *         <unfulfillableAmount unit="EACHES" value="0"/>
 *         <cancelledAmount unit="EACHES" value="0"/>
 *       </lineItems>
 *       <shipments amazonShipmentId="..." status="SHIPPED">
 *         <fulfillmentCenter id="PHX3"/>
 *         <lineItems lineItemId="..." packageNumber="..."/>
 *         <shipmentPackages carrierCode="ups" packageNumber="..." trackingNumber="...">
 *           <carrierTracking carrierCode="ups" trackingNumber="..."/>
 *         </shipmentPackages>
 *         <items lineItemId="..." packageNumber="..."/>
 *       </shipments>
 *     </fulfillmentOrders>
 *     <fulfillmentOrders orderId="322-5T2X-229635" .../>
 *     <pagination nextToken="..."/>
 *   </Payload>
 */
public class ListMCFOrders {

	private static final YFCLogCategory logger = YFCLogCategory.instance(ListMCFOrders.class);
	private static final String CLASS_NAME = "ListMCFOrders";

	private static final String DEFAULT_SP_API_URL = "https://sandbox.sellingpartnerapi-na.amazon.com/fulfillment/outbound/2026-07-04/orders";

	public static Document listMCFOrders(YFSEnvironment env, Document doc) throws Exception {
		logger.beginTimer("class: " + CLASS_NAME + " | method: listMCFOrders -- Starts");
		Document outDoc = null;
		try {
			Element eleDoc = doc.getDocumentElement();
			String enterpriseCode = eleDoc.getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE);
			String updatedAfter = eleDoc.getAttribute("UpdatedAfter");
			logger.debug("enterpriseCode: " + enterpriseCode + " updatedAfter: " + updatedAfter);

			if (YFCObject.isVoid(updatedAfter)) {
				YFSException ex = new YFSException();
				ex.setErrorCode("INPUT_ERROR_MCF_002");
				ex.setErrorDescription("UpdatedAfter timestamp is empty");
				throw ex;
			}

			// Get SP-API properties
			Document propertyDoc = SCXmlUtil.createDocument("Properties");
			propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, enterpriseCode);
			Map<String, String> spApiPropertiesMap = AmzGetGenericProperty.getSPIntegProperties(propertyDoc);

			String strGetURL = spApiPropertiesMap.get(AmzCommonConstants.SP_MCF_LIST_FULFILLMENT_ORDERS_URL);
			if (YFCObject.isVoid(strGetURL)) {
				strGetURL = DEFAULT_SP_API_URL;
			}
			logger.debug("SP-API ListFulfillmentOrders URL: " + strGetURL);

			// Build accumulated output document
			outDoc = SCXmlUtil.createDocument("Root");
			Element eleOutRoot = outDoc.getDocumentElement();

			String pageToken = null;
			boolean hasMorePages = true;

			while (hasMorePages) {
				// SP-API headers
				Map<String, String> headerMap = new HashMap<>();
				headerMap.put("Content-Type", "application/json");
				headerMap.put("x-amz-access-token", AmzRestWebserviceUtil.getSPAuthorizationToken(enterpriseCode));

				// Query parameters
				Map<String, String> paramsMap = new HashMap<>();
				paramsMap.put("updatedAfter", updatedAfter);
				paramsMap.put("shipments", "INCLUDE");
				if (!YFCObject.isVoid(pageToken)) {
					paramsMap.put("pageToken", pageToken);
				}

				logger.debug("Invoking SP-API GET: " + strGetURL + " pageToken: " + pageToken);
				String output = AmzRestWebserviceUtil.invokeGet(strGetURL, headerMap, paramsMap);
				logger.debug("SP-API ListFulfillmentOrders output: " + output);

				if (YFCObject.isVoid(output)) {
					break;
				}

				Document pageDoc = PLTJSONUtils.getXmlFromJSON(output, "Payload");
				logger.debug("listMCFOrders pageDoc: " + SCXmlUtil.getString(pageDoc));

				// SP-API 2026-07-04: the list response array is "orders" (was "fulfillmentOrders")
				NodeList fulfillmentOrders = pageDoc.getDocumentElement().getElementsByTagName("orders");
				for (int i = 0; i < fulfillmentOrders.getLength(); i++) {
					Element eleFO = (Element) fulfillmentOrders.item(i);
					eleOutRoot.appendChild(outDoc.importNode(eleFO, true));
				}

				// Check for nextToken attribute on pagination element
				pageToken = null;
				Element elePagination = SCXmlUtil.getChildElement(pageDoc.getDocumentElement(), "pagination");
				if (!YFCObject.isVoid(elePagination)) {
					pageToken = elePagination.getAttribute(AmzCommonConstants.AMZ_ATTRIBUTE_NEXT_TOKEN);
				}
				if (YFCObject.isVoid(pageToken)) {
					hasMorePages = false;
				}
			}

			logger.debug("listMCFOrders outDoc: " + SCXmlUtil.getString(outDoc));
			prepareAndLogResponse(AmzLiterals.STR_SUCCESS, doc, null);

		} catch (YFSException e) {
			e.printStackTrace();
			prepareAndLogResponse(AmzLiterals.STR_ERROR, doc, e.getErrorDescription());
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			prepareAndLogResponse(AmzLiterals.STR_ERROR, doc, e.getMessage());
			YFSException ex = new YFSException();
			ex.setErrorCode("MCF_LIST_FULFILLMENT_ORDERS_FAILED");
			ex.setErrorDescription(e.getMessage());
			throw ex;
		}

		logger.endTimer("class: " + CLASS_NAME + " | method: listMCFOrders -- Ends");
		return outDoc;
	}

	public static void prepareAndLogResponse(String processStatus, Document inDoc, String message) {
		logger.beginTimer("class: " + CLASS_NAME + " | method: prepareAndLogResponse -- Starts");

		String invokedEventType = inDoc.getDocumentElement().getAttribute("InvokedEventType");

		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE, "AMZCONN_MCF_LIST_FULFILLMENT_ORDERS");
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE, invokedEventType);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_EVENT, AmzLiterals.STR_RESPONSE);
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

		logger.endTimer("class: " + CLASS_NAME + " | method: prepareAndLogResponse -- Ends");
	}
}
