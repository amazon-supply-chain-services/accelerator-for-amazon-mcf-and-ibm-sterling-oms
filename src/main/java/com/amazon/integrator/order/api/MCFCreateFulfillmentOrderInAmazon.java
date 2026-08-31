package com.amazon.integrator.order.api;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzRestWebserviceUtil;
import com.amazon.common.util.AmzXMLUtil;
import com.amazon.integrator.common.util.MCFPrepareSPAPICreateFulfillmentOrderRequest;
import com.amazon.oms.order.api.AmzUpdateOrdWithAmazonOrdInfo;
import com.amazon.oms.order.api.AmzUpdateOrderRelease;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.interop.japi.YIFClientCreationException;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/**
 * This class invokes the SP-API createFulfillmentOrder for MCF.
 *
 * <p>On success (empty {} response): stamps ExtnAmazonOrderId and ExtnAmazonLineItemAlias
 * on OMS order lines via changeOrder. No Amazon callback needed — all data is in the input.
 *
 * <p>On error with 'insufficient quantity unavailable': calls invokeChangeReleaseToCancel.
 * On any other error: calls createNewException to log alert.
 *
 * <p>SP-API URL property: amzConn.MCF.CreateFulfillmentOrder.api_url
 */
public class MCFCreateFulfillmentOrderInAmazon {

	final YFCLogCategory logger = YFCLogCategory.instance(MCFCreateFulfillmentOrderInAmazon.class);

	public static final String SP_MCF_CREATE_FULFILLMENT_ORDER_URL = "amzConn.MCF.CreateFulfillmentOrder.api_url";
	private static final String STR_INSUFFICIENT_QUANTITY = "insufficient quantity unavailable";

	/**
	 * Invokes SP-API createFulfillmentOrder for MCF.
	 */
	public void createFulfillmentOrderInAmazon(YFSEnvironment env, Element eleOrder,
			List<String> amzCreateOrdElgPrimeLineNo, List<String> amzCreateOrdElgOrderLineKey,
			String strOrdReleaseKey) throws Exception {

		logger.timer("class: MCFCreateFulfillmentOrderInAmazon | method: createFulfillmentOrderInAmazon -- Starts");
		logger.info("class: MCFCreateFulfillmentOrderInAmazon | method: createFulfillmentOrderInAmazon -- Starts");

		try {
			prepareAndLogRequest(eleOrder);

			String enterpriseCode = eleOrder.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);

			// Get SP-API properties
			Document propertyDoc = SCXmlUtil.createDocument("Properties");
			propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, enterpriseCode);
			Map<String, String> spApiPropertiesMap = AmzGetGenericProperty.getSPIntegProperties(propertyDoc);

			String strPostURL = spApiPropertiesMap.get(SP_MCF_CREATE_FULFILLMENT_ORDER_URL);
			if (YFCObject.isVoid(strPostURL)) {
				strPostURL = "https://sandbox.sellingpartnerapi-na.amazon.com/fulfillment/outbound/2026-07-04/orders";
			}
			logger.debug("SP-API createFulfillmentOrder URL is: " + strPostURL);

			int timeout = 10;
			String strTimeout = spApiPropertiesMap.get(AmzCommonConstants.AMZ_TIME_OUT);
			if (!YFCObject.isVoid(strTimeout)) {
				timeout = Integer.parseInt(strTimeout);
			}

			// Prepare SP-API JSON request body
			MCFPrepareSPAPICreateFulfillmentOrderRequest requestPreparer = new MCFPrepareSPAPICreateFulfillmentOrderRequest();
			JSONObject requestBody = requestPreparer.prepareMCFCreateFulfillmentOrderJSON(env, eleOrder,
					amzCreateOrdElgPrimeLineNo, strOrdReleaseKey);
			logger.debug("MCFCreateFulfillmentOrderInAmazon. requestBody is: " + requestBody);

			// SP-API headers
			Map<String, String> headerMap = new HashMap<>();
			headerMap.put("Content-Type", "application/json");
			headerMap.put("x-amz-access-token", AmzRestWebserviceUtil.getSPAuthorizationToken(enterpriseCode));

			StringEntity requestEntity = new StringEntity(requestBody.toString(), ContentType.APPLICATION_JSON);
			String output = AmzRestWebserviceUtil.invokePost(strPostURL, timeout, requestEntity, headerMap);
			logger.debug("MCFCreateFulfillmentOrderInAmazon. output is: " + output);

			prepareAndLogResponse(AmzLiterals.STR_SUCCESS, eleOrder, output, null);

			if (isSuccessResponse(output)) {
				handleSuccessResponse(env, eleOrder, amzCreateOrdElgPrimeLineNo, requestBody, strOrdReleaseKey);
			} else {
				handleErrorResponse(env, eleOrder, amzCreateOrdElgPrimeLineNo, amzCreateOrdElgOrderLineKey,
						output, strOrdReleaseKey);
			}

		} catch (Exception e) {
			prepareAndLogResponse(AmzLiterals.STR_ERROR, eleOrder, null, e.getMessage());
			YFSException ex = new YFSException();
			ex.setErrorCode("MCF_CREATE_FULFILLMENT_ORDER_FAILED");
			ex.setErrorDescription("MCF CreateFulfillmentOrder Failed: " + e.getMessage());
			logger.error("Exception in MCFCreateFulfillmentOrderInAmazon.createFulfillmentOrderInAmazon: "
					+ ExceptionUtils.getStackTrace(ex));
			throw ex;
		}

		logger.info("class: MCFCreateFulfillmentOrderInAmazon | method: createFulfillmentOrderInAmazon -- Ends");
		logger.timer("class: MCFCreateFulfillmentOrderInAmazon | method: createFulfillmentOrderInAmazon -- Ends");
	}

	/**
	 * Success response from SP-API is empty {} or has no "errors" key.
	 */
	private boolean isSuccessResponse(String output) throws JSONException {
		if (YFCObject.isVoid(output)) {
			return true;
		}
		JSONObject outputJson = new JSONObject(output);
		return !outputJson.has("errors");
	}

	/**
	 * On success: stamp ExtnAmazonOrderId and ExtnAmazonLineItemAlias on OMS order lines.
	 * No Amazon callback — orderId and lineItemId are already known from the input payload.
	 * orderId = sellerFulfillmentOrderId, lineItemId = sellerFulfillmentOrderItemId (= OrderLineKey).
	 */
	private void handleSuccessResponse(YFSEnvironment env, Element eleOutOrder,
			List<String> amzCreateOrdElgPrimeLineNo, JSONObject requestBody, String strOrdReleaseKey)
			throws Exception {

		logger.timer("class: MCFCreateFulfillmentOrderInAmazon | method: handleSuccessResponse -- Starts");
		logger.info("class: MCFCreateFulfillmentOrderInAmazon | method: handleSuccessResponse -- Starts");

		String strAmazonOrderId = requestBody.getString("orderId");
		logger.debug("MCFCreateFulfillmentOrderInAmazon. strAmazonOrderId: " + strAmazonOrderId);

		// Build lineItemId map from request body: OrderLineKey -> lineItemId
		Map<String, String> lineKeyToLineItemId = new HashMap<>();
		JSONArray lineItemsArr = requestBody.getJSONArray("lineItems");
		for (int i = 0; i < lineItemsArr.length(); i++) {
			JSONObject item = lineItemsArr.getJSONObject(i);
			String lineItemId = item.getString("lineItemId");
			// lineItemId IS the OrderLineKey
			lineKeyToLineItemId.put(lineItemId, lineItemId);
		}

		// changeOrder to stamp ExtnAmazonOrderId and ExtnAmazonLineItemAlias
		Document inChangeOrdDoc = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER);
		Element eleInChgOrd = inChangeOrdDoc.getDocumentElement();
		eleInChgOrd.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY,
				eleOutOrder.getAttribute(AmzLiterals.A_ORDER_HEADER_KEY));
		eleInChgOrd.setAttribute(AmzLiterals.A_OVERRIDE, AmzCommonConstants.STR_VAL_Y);

		Element eleOrderLines = AmzXMLUtil.createChild(eleInChgOrd, AmzLiterals.E_ORDER_LINES);
		for (int i = 0; i < amzCreateOrdElgPrimeLineNo.size(); i++) {
			String strPrimeLineNo = amzCreateOrdElgPrimeLineNo.get(i);
			Element eleOrderLine = AmzXMLUtil.getXpathElement(eleOutOrder,
					"OrderLines/OrderLine[@PrimeLineNo='" + strPrimeLineNo + "']");
			String sOrderLineKey = eleOrderLine.getAttribute(AmzLiterals.A_ORDER_LINE_KEY);

			Element eleInChgOrdLine = AmzXMLUtil.createChild(eleOrderLines, AmzLiterals.E_ORDER_LINE);
			eleInChgOrdLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, sOrderLineKey);
			Element eleExtn = AmzXMLUtil.createChild(eleInChgOrdLine, AmzLiterals.E_EXTN);
			eleExtn.setAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID, strAmazonOrderId);
			String strLineItemId = lineKeyToLineItemId.get(sOrderLineKey);
			if (!YFCObject.isVoid(strLineItemId)) {
				eleExtn.setAttribute(AmzLiterals.A_EXTN_AMZ_LINE_ITEM_ALIAS, strLineItemId);
			}
		}

		logger.debug("MCFCreateFulfillmentOrderInAmazon. changeOrder inDoc: " + AmzXMLUtil.getString(inChangeOrdDoc));
		AmzCommonUtil.invokeService(env, AmzCommonConstants.SERVICE_AMZ_CHANGE_ORDER_SERVICE, inChangeOrdDoc);

		// Update release with AmazonOrderId if this is a released order
		Object objTransaction = env.getTxnObject(AmzCommonConstants.STR_TRANSACTION);
		if (!YFCObject.isVoid(objTransaction)) {
			String strTransaction = String.valueOf(objTransaction);
			if (AmzCommonConstants.STR_RELEASED_ORDER.equalsIgnoreCase(strTransaction)) {
				AmzUpdateOrderRelease amzUpdateOrderRelease = new AmzUpdateOrderRelease();
				amzUpdateOrderRelease.updateOMSOrderRelease(env, eleOutOrder, strAmazonOrderId, strOrdReleaseKey);
			}
		}

		logger.info("class: MCFCreateFulfillmentOrderInAmazon | method: handleSuccessResponse -- Ends");
		logger.timer("class: MCFCreateFulfillmentOrderInAmazon | method: handleSuccessResponse -- Ends");
	}

	/**
	 * On error: if 'insufficient quantity unavailable' then invokeChangeReleaseToCancel,
	 * else createNewException to log alert.
	 */
	private void handleErrorResponse(YFSEnvironment env, Element eleOutOrder,
			List<String> amzCreateOrdElgPrimeLineNo, List<String> amzCreateOrdElgOrderLineKey,
			String output, String strOrdReleaseKey)
			throws RemoteException, JSONException, YIFClientCreationException, Exception {

		logger.timer("class: MCFCreateFulfillmentOrderInAmazon | method: handleErrorResponse -- Starts");
		logger.info("class: MCFCreateFulfillmentOrderInAmazon | method: handleErrorResponse -- Starts");

		String strOrderNo = eleOutOrder.getAttribute(AmzLiterals.A_ORDER_NO);
		String strOhKey = eleOutOrder.getAttribute(AmzLiterals.A_ORDER_HEADER_KEY);

		if (isInsufficientQuantityError(output)) {
			logger.info("MCFCreateFulfillmentOrderInAmazon. Insufficient quantity error - cancelling release");
			AmzUpdateOrderRelease amzUpdateOrderRelease = new AmzUpdateOrderRelease();
			amzUpdateOrderRelease.invokeChangeReleaseToCancel(env, eleOutOrder, strOrdReleaseKey, output,
					amzCreateOrdElgOrderLineKey);
		} else {
			logger.info("MCFCreateFulfillmentOrderInAmazon. Other error - creating exception alert");
			AmzUpdateOrdWithAmazonOrdInfo amzUpdateOrdWithAmazonOrdInfo = new AmzUpdateOrdWithAmazonOrdInfo();
			amzUpdateOrdWithAmazonOrdInfo.createNewException(env, strOrderNo, strOhKey, output);
		}

		logger.info("class: MCFCreateFulfillmentOrderInAmazon | method: handleErrorResponse -- Ends");
		logger.timer("class: MCFCreateFulfillmentOrderInAmazon | method: handleErrorResponse -- Ends");
	}

	/**
	 * Checks if the SP-API error response contains 'insufficient quantity unavailable'.
	 */
	private boolean isInsufficientQuantityError(String output) {
		if (YFCObject.isVoid(output)) {
			return false;
		}
		try {
			JSONObject outputJson = new JSONObject(output);
			if (outputJson.has("errors")) {
				JSONArray errors = outputJson.getJSONArray("errors");
				for (int i = 0; i < errors.length(); i++) {
					JSONObject error = errors.getJSONObject(i);
					String message = error.optString("message", "");
					if (message.toLowerCase().contains(STR_INSUFFICIENT_QUANTITY)) {
						return true;
					}
				}
			}
		} catch (JSONException e) {
			logger.error("Error parsing error response: " + e.getMessage());
		}
		return false;
	}

	private void prepareAndLogRequest(Element eleOrder) {
		logger.beginTimer("class: MCFCreateFulfillmentOrderInAmazon | method: prepareAndLogRequest -- Starts");
		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE,
				"AMZCONN_MCF_CREATE_FULFILLMENT_ORDER");
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,
				eleOrder.getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE));
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO,
				eleOrder.getAttribute(AmzLiterals.A_ORDER_NO));
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_FULFILLABLE_BY_AMAZON, AmzCommonConstants.STR_VAL_Y);
		AmzCommonUtil.logAmzConnRequest(logInput);
		logger.endTimer("class: MCFCreateFulfillmentOrderInAmazon | method: prepareAndLogRequest -- Ends");
	}

	private void prepareAndLogResponse(String processStatus, Element eleOrder, String output, String message) {
		logger.beginTimer("class: MCFCreateFulfillmentOrderInAmazon | method: prepareAndLogResponse -- Starts");
		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE,
				"AMZCONN_MCF_CREATE_FULFILLMENT_ORDER");
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,
				eleOrder.getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE));
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO,
				eleOrder.getAttribute(AmzLiterals.A_ORDER_NO));
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_FULFILLABLE_BY_AMAZON, AmzCommonConstants.STR_VAL_Y);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_HTTP_MESSAGE, AmzCommonConstants.STR_OK);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_HTTP_CODE, AmzCommonConstants.STR_HTTP_STATUS_OK);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_PROCESS_STATUS, processStatus);
		if (!YFCObject.isVoid(message)) {
			if (processStatus.equalsIgnoreCase(AmzLiterals.STR_SUCCESS)) {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_MESSAGE, message);
			} else {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_ERROR_MSG, message);
			}
		}
		AmzCommonUtil.logAmzConnResponse(logInput);
		logger.endTimer("class: MCFCreateFulfillmentOrderInAmazon | method: prepareAndLogResponse -- Ends");
	}

}
