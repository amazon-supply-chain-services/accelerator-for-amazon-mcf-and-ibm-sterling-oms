package com.amazon.integrator.common.util;

import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPathExpressionException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;

/**
 * This class prepares the SP-API JSON input request for MCF createFulfillmentOrder
 * using OMS order XML.
 *
 * <p>SP-API CreateFulfillmentOrder input format:
 * <pre>{@code
 * {
 *   "orderId": "Y100001234-01",
 *   "displayableOrderId": "Y100001234",
 *   "fulfillmentConfiguration": {
 *     "serviceLevel": { "serviceTiers": ["STANDARD"] },
 *     "action": "SHIP",
 *     "policy": "FILL_ALL_AVAILABLE"
 *   },
 *   "destination": {
 *     "deliveryAddress": {
 *       "name": "FirstName LastName",
 *       "addressLine1": "1000 Winthrop Ave N",
 *       "addressLine2": "Floor 19",
 *       "city": "Seattle",
 *       "stateOrRegion": "WA",
 *       "postalCode": "98103",
 *       "countryCode": "US",
 *       "phone": "123-456-7890",
 *       "email": "shopper@email.com"
 *     }
 *   },
 *   "origin": { "countryCode": "US" },
 *   "lineItems": [
 *     {
 *       "lineItemId": "OrderLineKey",
 *       "product": {
 *         "productIdentifier": { "amazonSku": "SKU1" }
 *       },
 *       "amount": { "unit": "EACHES", "value": "2.0" }
 *     }
 *   ]
 * }
 * }</pre>
 */
public class MCFPrepareSPAPICreateFulfillmentOrderRequest {

	final YFCLogCategory logger = YFCLogCategory.instance(MCFPrepareSPAPICreateFulfillmentOrderRequest.class);

	/**
	 * Prepares the SP-API JSON body for createFulfillmentOrder.
	 */
	public JSONObject prepareMCFCreateFulfillmentOrderJSON(YFSEnvironment env, Element eleOrder,
			List<String> amzCreateOrdElgPrimeLineNo, String strOrdReleaseKey) throws Exception {

		logger.timer("class: MCFPrepareSPAPICreateFulfillmentOrderRequest | method: prepareMCFCreateFulfillmentOrderJSON -- Starts");
		logger.info("class: MCFPrepareSPAPICreateFulfillmentOrderRequest | method: prepareMCFCreateFulfillmentOrderJSON -- Starts");

		JSONObject requestBody = new JSONObject();

		// orderId: OrderNo + "-" + zero-padded ReleaseNo
		String strOrderId = buildOrderId(env, eleOrder, strOrdReleaseKey);
		requestBody.put("orderId", strOrderId);

		// displayableOrderId: base OMS OrderNo (human-facing), before release padding. Required by the 2026-07-04 version.
		requestBody.put("displayableOrderId", eleOrder.getAttribute(AmzLiterals.A_ORDER_NO));

		// SP-API 2026-07-04: serviceLevel/action/policy are nested under fulfillmentConfiguration,
		// and serviceLevel carries a serviceTiers array.
		JSONObject fulfillmentConfiguration = new JSONObject();
		JSONObject serviceLevel = new JSONObject();
		serviceLevel.put("serviceTiers", new JSONArray().put("STANDARD"));
		fulfillmentConfiguration.put("serviceLevel", serviceLevel);
		fulfillmentConfiguration.put("action", "SHIP");
		fulfillmentConfiguration.put("policy", "FILL_ALL_AVAILABLE");
		requestBody.put("fulfillmentConfiguration", fulfillmentConfiguration);

		// destination.deliveryAddress from PersonInfoShipTo
		Element eleOrdLines = AmzXMLUtil.getChildElement(eleOrder, AmzLiterals.E_ORDER_LINES);
		Element elePersonInfo = null;
		for (int i = 0; i < amzCreateOrdElgPrimeLineNo.size(); i++) {
			String strPrimeLineNo = amzCreateOrdElgPrimeLineNo.get(i);
			Element eleOrderLine = AmzXMLUtil.getXpathElement(eleOrdLines,
					"OrderLine[@PrimeLineNo='" + strPrimeLineNo + "']");
			if (YFCObject.isVoid(elePersonInfo)) {
				elePersonInfo = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_PERSON_INFO_SHIP_TO);
			}
		}
		if (!YFCObject.isVoid(elePersonInfo)) {
			JSONObject destination = new JSONObject();
			destination.put("deliveryAddress", buildDeliveryAddress(elePersonInfo));
			requestBody.put("destination", destination);

			// origin countryCode from ship-to address
			JSONObject origin = new JSONObject();
			origin.put("countryCode", elePersonInfo.getAttribute(AmzLiterals.A_COUNTRY));
			requestBody.put("origin", origin);
		}

		// lineItems
		JSONArray lineItems = new JSONArray();
		for (int i = 0; i < amzCreateOrdElgPrimeLineNo.size(); i++) {
			String strPrimeLineNo = amzCreateOrdElgPrimeLineNo.get(i);
			Element eleOrderLine = AmzXMLUtil.getXpathElement(eleOrdLines,
					"OrderLine[@PrimeLineNo='" + strPrimeLineNo + "']");
			lineItems.put(buildLineItem(eleOrderLine));
		}
		requestBody.put("lineItems", lineItems);

		logger.debug("MCFPrepareSPAPICreateFulfillmentOrderRequest. requestBody is: " + requestBody);
		logger.info("class: MCFPrepareSPAPICreateFulfillmentOrderRequest | method: prepareMCFCreateFulfillmentOrderJSON -- Ends");
		logger.timer("class: MCFPrepareSPAPICreateFulfillmentOrderRequest | method: prepareMCFCreateFulfillmentOrderJSON -- Ends");
		return requestBody;
	}

	private String buildOrderId(YFSEnvironment env, Element eleOrder, String strOrdReleaseKey) {
		String strOrderNo = eleOrder.getAttribute(AmzLiterals.A_ORDER_NO);
		if (!YFCObject.isVoid(strOrdReleaseKey)) {
			String strReleaseNo = getReleaseNo(env, strOrdReleaseKey);
			if (!YFCObject.isVoid(strReleaseNo)) {
				int iReleaseNo = Integer.parseInt(strReleaseNo);
				String strPaddedReleaseNo = String.format("%02d", iReleaseNo);
				logger.debug("strPaddedReleaseNo is: " + strPaddedReleaseNo);
				return strOrderNo + "-" + strPaddedReleaseNo;
			}
		}
		return strOrderNo;
	}

	private JSONObject buildDeliveryAddress(Element elePersonInfo) throws JSONException {
		JSONObject address = new JSONObject();
		address.put("name", elePersonInfo.getAttribute(AmzLiterals.A_FIRST_NAME) + " "
				+ elePersonInfo.getAttribute(AmzLiterals.A_LAST_NAME));
		address.put("addressLine1", elePersonInfo.getAttribute(AmzLiterals.A_ADDRESS_LINE1));
		String addressLine2 = elePersonInfo.getAttribute("AddressLine2");
		if (!YFCObject.isVoid(addressLine2)) {
			address.put("addressLine2", addressLine2);
		}
		address.put("city", elePersonInfo.getAttribute(AmzLiterals.A_CITY));
		address.put("stateOrRegion", elePersonInfo.getAttribute(AmzLiterals.A_STATE));
		address.put("postalCode", elePersonInfo.getAttribute(AmzLiterals.A_ZIP_CODE));
		address.put("countryCode", elePersonInfo.getAttribute(AmzLiterals.A_COUNTRY));
		String phone = elePersonInfo.getAttribute("DayPhone");
		if (!YFCObject.isVoid(phone)) {
			address.put("phone", phone);
		}
		String email = elePersonInfo.getAttribute("EMailID");
		if (!YFCObject.isVoid(email)) {
			address.put("email", email);
		}
		return address;
	}

	private JSONObject buildLineItem(Element eleOrderLine) throws JSONException, XPathExpressionException {
		JSONObject lineItem = new JSONObject();
		// lineItemId = OrderLineKey
		lineItem.put("lineItemId", eleOrderLine.getAttribute(AmzLiterals.A_ORDER_LINE_KEY));

		// product.productIdentifier.amazonSku = ItemID
		Element eleItem = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_ITEM);
		JSONObject product = new JSONObject();
		JSONObject productIdentifier = new JSONObject();
		productIdentifier.put("amazonSku", eleItem.getAttribute(AmzLiterals.A_ITEM_ID));
		product.put("productIdentifier", productIdentifier);
		lineItem.put("product", product);

		// amount.unit + amount.value  (SP-API 2026-07-04 renamed "unitOfMeasure" -> "unit")
		JSONObject amount = new JSONObject();
		amount.put("unit", "EACHES");
		String strOrderedQty = eleOrderLine.getAttribute(AmzLiterals.A_ORDERED_QTY);
		if (!YFCObject.isVoid(strOrderedQty)) {
			amount.put("value", strOrderedQty);
		} else {
			amount.put("value", "1.0");
		}
		lineItem.put("amount", amount);

		return lineItem;
	}

	private String getReleaseNo(YFSEnvironment env, String strOrderReleaseKey) {
		logger.info("class: MCFPrepareSPAPICreateFulfillmentOrderRequest | method: getReleaseNo -- Starts");
		Document inDocGetOrdReleaseList = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER_RELEASE);
		Element eleInOrdRelease = inDocGetOrdReleaseList.getDocumentElement();
		eleInOrdRelease.setAttribute(AmzLiterals.A_ORDER_RELEASE_KEY, strOrderReleaseKey);
		Document outDocGetOrdReleaseList = AmzCommonUtil.invokeAPI(env,
				AmzCommonConstants.TEMPLATE_GET_ORDER_RELEASE_LIST_FOR_AMZ_CREATE_ORDER,
				AmzCommonConstants.API_GET_ORDER_RELEASE_LIST, inDocGetOrdReleaseList);
		Element eleOutOrdRelList = outDocGetOrdReleaseList.getDocumentElement();
		Element eleOutOrdRelease = AmzXMLUtil.getChildElement(eleOutOrdRelList, AmzLiterals.E_ORDER_RELEASE);
		logger.info("class: MCFPrepareSPAPICreateFulfillmentOrderRequest | method: getReleaseNo -- Ends");
		return eleOutOrdRelease.getAttribute(AmzLiterals.A_RELEASE_NO);
	}

}
