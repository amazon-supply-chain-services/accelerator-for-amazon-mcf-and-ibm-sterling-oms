package com.amazon.integrator.delivery.api;

import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzRestWebserviceUtil;
import com.amazon.common.util.AmzXMLUtil;
import com.ibm.sterling.afc.jsonutil.PLTJSONUtils;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/**
 * Calls the SP-API GetOrder endpoint to retrieve MCF order details.
 *
 * This is the MCF equivalent of AmzGetOrderDetails (which uses the BwP GraphQL API).
 * Uses SP-API authentication (x-amz-access-token) instead of BwP auth.
 *
 * SP-API endpoint:
 *   GET /fulfillment/outbound/2026-07-04/orders/{orderId}?shipments=INCLUDE
 *
 * Input:
 *   <Order AmzOrderID="ABC123" EnterpriseCode="DEFAULT"/>
 *
 * Output (JSON response converted to XML):
 *   <AmzProcessPackageStatusChangedEvent>
 *     <order orderId="ABC123" receiveTime="2025-08-28T23:39:38Z" status="COMPLETE"
 *         statusUpdateTime="2025-08-29T08:29:05Z">
 *       <fulfillmentConfiguration action="SHIP" policy="FILL_ALL_AVAILABLE">
 *         <serviceLevel>
 *           <serviceTiers>STANDARD</serviceTiers>
 *         </serviceLevel>
 *       </fulfillmentConfiguration>
 *       <lineItems lineItemId="item1">
 *         <product><productIdentifier amazonSku="SKU1"/></product>
 *         <amount unit="EACHES" value="1"/>
 *         <unfulfillableAmount unit="EACHES" value="0"/>
 *         <cancelledAmount unit="EACHES" value="0"/>
 *       </lineItems>
 *       <destination>
 *         <deliveryAddress addressLine1="1000 Winthrop Ave N" addressLine2="Floor 19"
 *             city="Seattle" countryCode="US" name="Recipient Name"
 *             phone="123-456-7890" postalCode="98103" stateOrRegion="WA"/>
 *       </destination>
 *       <services><packaging blankBox="REQUIRED"/></services>
 *       <shipments amazonShipmentId="shipmentID" deliveryTime="2025-08-31T06:59:59Z"
 *           shipTime="2025-08-29T08:23:44Z" status="SHIPPED">
 *         <amazonFacility facilityId="PAE2"/>
 *         <packages deliveryTime="2025-08-29T20:00:00Z" packageId="1">
 *           <tracking>
 *             <carrier carrierCode="Amazon Logistics" trackingNumber="TBA32397232322"/>
 *           </tracking>
 *         </packages>
 *         <items lineItemId="item1">
 *           <amount unit="EACHES" value="2"/>
 *           <productIdentifier amazonSku="SKU1"/>
 *         </items>
 *       </shipments>
 *     </order>
 *   </AmzProcessPackageStatusChangedEvent>
 */
public class GetMCFOrder {

	private static final YFCLogCategory logger = YFCLogCategory.instance(GetMCFOrder.class);
	private static final String CLASS_NAME = "GetMCFOrder";

	private static final String DEFAULT_SP_API_URL = "https://sandbox.sellingpartnerapi-na.amazon.com/fulfillment/outbound/2026-07-04/orders/";

	public static Document getMCFOrder(YFSEnvironment env, Document doc) throws Exception {
		logger.beginTimer("class: " + CLASS_NAME + " | method: getMCFOrder -- Starts");
		Document outDoc = null;
		try {
			Element eleDoc = doc.getDocumentElement();
			String orderId = eleDoc.getAttribute(AmzCommonConstants.AMZ_ORDER_ID);
			String enterpriseCode = eleDoc.getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE);
			logger.debug("enterpriseCode: " + enterpriseCode + " orderId: " + orderId);

			if (YFCObject.isVoid(orderId)) {
				YFSException ex = new YFSException();
				ex.setErrorCode("INPUT_ERROR_MCF_001");
				ex.setErrorDescription("Amazon OrderId is empty");
				throw ex;
			}

			// Get SP-API properties
			Document propertyDoc = SCXmlUtil.createDocument("Properties");
			propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, enterpriseCode);
			Map<String, String> spApiPropertiesMap = AmzGetGenericProperty.getSPIntegProperties(propertyDoc);

			String strGetURL = spApiPropertiesMap.get(AmzCommonConstants.SP_MCF_GET_FULFILLMENT_ORDER_URL);
			if (YFCObject.isVoid(strGetURL)) {
				strGetURL = DEFAULT_SP_API_URL;
			}
			logger.debug("SP-API GetFulfillmentOrder base URL: " + strGetURL);

			int timeout = 10;
			String strTimeout = spApiPropertiesMap.get(AmzCommonConstants.AMZ_TIME_OUT);
			if (!YFCObject.isVoid(strTimeout)) {
				timeout = Integer.parseInt(strTimeout);
			}

			// Build full URL: baseUrl + orderId + ?shipments=INCLUDE
			String fullURL = strGetURL + orderId;

			// SP-API headers
			Map<String, String> headerMap = new HashMap<>();
			headerMap.put("Content-Type", "application/json");
			headerMap.put("x-amz-access-token", AmzRestWebserviceUtil.getSPAuthorizationToken(enterpriseCode));

			// Query parameters
			Map<String, String> paramsMap = new HashMap<>();
			paramsMap.put("shipments", "INCLUDE");

			logger.debug("Invoking SP-API GET: " + fullURL);
			String output = AmzRestWebserviceUtil.invokeGet(fullURL, headerMap, paramsMap);
			logger.debug("SP-API GetFulfillmentOrder output: " + output);

			if (!YFCObject.isVoid(output)) {
				// Convert JSON response to XML
				outDoc = PLTJSONUtils.getXmlFromJSON(output, "Root");
				logger.debug("getMCFOrder outDoc: " + SCXmlUtil.getString(outDoc));
				prepareAndLogResponse(AmzLiterals.STR_SUCCESS, doc, null);
			}

		} catch (YFSException e) {
			e.printStackTrace();
			prepareAndLogResponse(AmzLiterals.STR_ERROR, doc, e.getErrorDescription());
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			prepareAndLogResponse(AmzLiterals.STR_ERROR, doc, e.getMessage());
			YFSException ex = new YFSException();
			ex.setErrorCode("MCF_GET_FULFILLMENT_ORDER_FAILED");
			ex.setErrorDescription(e.getMessage());
			throw ex;
		}

		logger.endTimer("class: " + CLASS_NAME + " | method: getMCFOrder -- Ends");
		return outDoc;
	}

	public static void prepareAndLogResponse(String processStatus, Document inDoc, String message) {
		logger.beginTimer("class: " + CLASS_NAME + " | method: prepareAndLogResponse -- Starts");

		String amazonOrderId = inDoc.getDocumentElement().getAttribute(AmzCommonConstants.AMZ_ORDER_ID);
		String invokedEventType = inDoc.getDocumentElement().getAttribute("InvokedEventType");

		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE, "AMZCONN_MCF_GET_FULFILLMENT_ORDER");
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

		logger.endTimer("class: " + CLASS_NAME + " | method: prepareAndLogResponse -- Ends");
	}
}
