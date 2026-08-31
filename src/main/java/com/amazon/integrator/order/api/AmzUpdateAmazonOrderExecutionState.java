package com.amazon.integrator.order.api;

import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzOrderMutations;
import com.amazon.common.util.AmzRestWebserviceUtil;
import com.amazon.common.util.AmzXMLUtil;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.interop.japi.YIFClientCreationException;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/*
 * This class verify the desiredExecutionState and invoke amazon update order
 * To update the DesiredExecutionState as STARTED
 */
public class AmzUpdateAmazonOrderExecutionState implements YIFCustomApi {

	final YFCLogCategory logger = YFCLogCategory.instance(AmzUpdateAmazonOrderExecutionState.class);
	List<String> amzOrderIdList = new ArrayList<>();
	Map<String, String> mapBWPIntegProps = null;
	Map<String, String> mapGenericProps = null;
	private Properties props;

	/*
	 * This method verify the is there any amazonOrder to update the
	 * desiredExecutionState.
	 */
	public Document verifyAndUpdateAmzOrdExecutionState(YFSEnvironment env, Document indoc) throws Exception {
		logger.timer(
				"class: AmzUpdateAmazonOrderExecutionState | method: verifyAndUpdateAmzOrdExecutionState -- Starts");
		logger.info(
				"class: AmzUpdateAmazonOrderExecutionState | method: verifyAndUpdateAmzOrdExecutionState -- Starts");
		prepareAndLogRequest(indoc);
		Element eleOrder = indoc.getDocumentElement();
		String strEnterPriseCode = eleOrder.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
		Document inDocGetGenrcProperty = AmzXMLUtil.createDocument(AmzLiterals.E_PROPERTIES);
		inDocGetGenrcProperty.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterPriseCode);
		mapBWPIntegProps = AmzGetGenericProperty.getBWPIntegProperties(inDocGetGenrcProperty);
		mapGenericProps = AmzGetGenericProperty.getGenericProperties(env, inDocGetGenrcProperty);
		List<String> amzItemInvUpdate = new ArrayList<>();
		Element eleOrdLines = AmzXMLUtil.getChildElement(eleOrder, AmzLiterals.E_ORDER_LINES);
		NodeList nOrderLine = eleOrdLines.getElementsByTagName(AmzLiterals.E_ORDER_LINE);
		int iOrderLineLen = nOrderLine.getLength();
		for (int i = 0; i < iOrderLineLen; i++) {
			Element eleOrderLine = (Element) nOrderLine.item(i);
			Element eleOrdLineExtn = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_EXTN);
			if (!YFCObject.isVoid(eleOrdLineExtn)
					&& !YFCObject.isVoid(eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID))) {
				String strExtnAmzOrdId = eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID);
				logger.debug("AmzUpdateAmazonOrderExecutionState.strExtnAmzOrdId is: " + strExtnAmzOrdId);
				String strExtnAmzFulfillable = eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_IS_AMAZON_FULFILLABLE);
				String strExtnIssPrimeElg = eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_IS_PRIME_ELIGIBLE);
				if (!YFCObject.isVoid(strExtnAmzOrdId) && !YFCObject.isVoid(strExtnIssPrimeElg)
						&& !YFCObject.isVoid(strExtnAmzFulfillable)
						&& (AmzCommonConstants.STR_VAL_Y.equalsIgnoreCase(strExtnIssPrimeElg)
								|| AmzCommonConstants.STR_VAL_Y.equalsIgnoreCase(strExtnAmzFulfillable))) {
					logger.debug("strEnterPriseCode is: " + strEnterPriseCode);
					boolean isInventoryPrimitiveOn = AmzCommonUtil.invokeCondition(env, strEnterPriseCode,
							"AmzCheckInventoryPrimitive", AmzCommonConstants.STR_GENERAL);
					logger.debug("isInventoryPrimitiveOn is: " + isInventoryPrimitiveOn);
					if (isInventoryPrimitiveOn) {
						updateOMSInvSupply(env, eleOrderLine, strEnterPriseCode, amzItemInvUpdate);
					}
					invokedAmazonUpdateOrder(env, strExtnAmzOrdId, indoc);

				}
			}
		}
		logger.info("class: AmzUpdateAmazonOrderExecutionState | method: verifyAndUpdateAmzOrdExecutionState -- Ends");
		logger.timer("class: AmzUpdateAmazonOrderExecutionState | method: verifyAndUpdateAmzOrdExecutionState -- Ends");
		return indoc;
	}

	/*
	 * This method invoke the amazon update order, to update the amazon order
	 * DesiredExecutionState to STARTED.
	 */

	public void invokedAmazonUpdateOrder(YFSEnvironment env, String strExtnAmzOrdId, Document indoc) throws Exception {

		logger.timer("class: AmzUpdateAmazonOrderExecutionState | method: updateAmzOrderExecutionstate -- Starts");
		logger.info("class: AmzUpdateAmazonOrderExecutionState | method: updateAmzOrderExecutionstate -- Starts");

		try {
			String targetId = mapBWPIntegProps.get(AmzCommonConstants.AMZ_TARGETID);
			logger.debug("targetId is: " + targetId);

			String postURL = mapBWPIntegProps.get(AmzCommonConstants.AMZ_POST_URL);
			logger.debug("postURL is: " + postURL);

			String apiAccessKey = mapBWPIntegProps.get(AmzCommonConstants.AMZ_API_ACCESS_KEY);
			logger.debug("apiAccessKey is: " + apiAccessKey);

			String apiVersion = mapBWPIntegProps.get(AmzCommonConstants.AMZ_API_VERSION);
			logger.debug("apiVersion is: " + apiVersion);
			if (!amzOrderIdList.contains(strExtnAmzOrdId)) {
				amzOrderIdList.add(strExtnAmzOrdId);

				String query = AmzOrderMutations.AMZ_UPDATE_ORDER_FOR_EXECUTION_STATE_QUERY;
				JSONObject variables = new JSONObject();
				JSONObject orderIdentifier = new JSONObject();
				orderIdentifier.put("orderId", strExtnAmzOrdId);
				variables.put("orderIdentifier", orderIdentifier);
				JSONObject input = new JSONObject();
				input.put("desiredExecutionState",
						mapGenericProps.get(AmzCommonConstants.AMZ_UPDORD_DESIRED_EXECUTION_STATE));
				variables.put("input", input);
				JSONObject payload = new JSONObject();
				payload.put(AmzLiterals.A_JS_QUERY, query);
				payload.put(AmzLiterals.A_JS_VARIABLES, variables);
				logger.debug("AmzCreateOrder.getOrder variable is : " + variables);
				logger.debug("AmzCreateOrder.Payload to updateOrder is : " + payload.toString());
				StringEntity requestEntity = new StringEntity(payload.toString(), ContentType.APPLICATION_JSON);
				Map<String, String> headerMap = new HashMap<>();
				headerMap.put(AmzLiterals.A_JS_CONTENTS_TYPE, AmzLiterals.A_JS_APPLICATION_JSON);
				headerMap.put(AmzLiterals.A_JS_AUTHORIZATION, "Bearer" + " " + AmzRestWebserviceUtil
						.getAuthorizationToken(indoc.getDocumentElement().getAttribute(AmzLiterals.A_ENTERPRISE_CODE)));
				headerMap.put(AmzLiterals.A_JS_X_OMNI_TARGETID, targetId);
				headerMap.put(AmzLiterals.A_JS_X_API_ACCESS_KEY, apiAccessKey);
				headerMap.put(AmzLiterals.A_JS_X_API_VERSION, apiVersion);
				String output = AmzRestWebserviceUtil.invokePost(postURL, 10, requestEntity, headerMap);
				validateResponseMessage(env, output, indoc, strExtnAmzOrdId);

				logger.debug("AmzUpdateAmazonOrderExecutionState.updateAmzOrderExecutionstate.outDoc:" + output);

				logger.info("class: AmzUpdateAmazonOrderExecutionState | method: updateAmzOrderExecutionstate -- Ends");
				logger.timer(
						"class: AmzUpdateAmazonOrderExecutionState | method: updateAmzOrderExecutionstate -- Ends");

			}

			prepareAndLogResponse(AmzLiterals.STR_SUCCESS, strExtnAmzOrdId, indoc, null);
		} catch (Exception e) {

			e.printStackTrace();
			prepareAndLogResponse(AmzLiterals.STR_ERROR, strExtnAmzOrdId, indoc, e.getMessage());
			YFSException ex = new YFSException();
			ex.setErrorCode("UPDATE_AMAZON_ORDER_EXECUTION_STATE_FAILED");
			ex.setErrorDescription("Update Amazon Order Execution State Failed " + e.getMessage());
			logger.error("class: AmzUpdateAmazonOrderExecutionState | method: invokedAmazonUpdateOrder "
					+ ExceptionUtils.getStackTrace(ex));
			throw ex;
		}

	}

	/*
	 * This method post the Inventory changed event message to internal queue
	 */

	private void updateOMSInvSupply(YFSEnvironment env, Element eleOrderLine, String strEnterPriseCode,
			List<String> amzItemInvUpdate) throws RemoteException, YIFClientCreationException {
		logger.timer("class: AmzUpdateAmazonOrderExecutionState | method: updateOMSInvSupply -- Starts");
		logger.info("class: AmzUpdateAmazonOrderExecutionState | method: updateOMSInvSupply -- Starts");
		try {

			Element itemEle = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_ITEM);
			String strItemID = itemEle.getAttribute(AmzLiterals.A_ITEM_ID);
			if (!amzItemInvUpdate.contains(strItemID)) {
				amzItemInvUpdate.add(strItemID);
				Document outDoc = SCXmlUtil.createDocument("InventoryItems");
				Element eleInventoryItems = outDoc.getDocumentElement();
				eleInventoryItems.setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE, "INVENTORY_CHANGED");
				eleInventoryItems.setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE,
						AmzCommonConstants.STR_AMZCONN_UPDATE_ORDER);
				eleInventoryItems.setAttribute("ShipNode", "");
				Date currentDate = new Date();
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");
				sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
				String currentUtcDateTime = sdf.format(currentDate);
				eleInventoryItems.setAttribute("DateTime", currentUtcDateTime);
				eleInventoryItems.setAttribute("IsAvailableQtyExist", "N");
				Element eleItem = SCXmlUtil.createChild(eleInventoryItems, "Item");
				eleItem.setAttribute(AmzLiterals.A_ITEM_ID, itemEle.getAttribute(AmzLiterals.A_ITEM_ID));

				eleItem.setAttribute("InventoryItemId", "");
				eleItem.setAttribute("AvailableQty", "");
				eleItem.setAttribute("IsAvailableQtyExist", "N");
				eleItem.setAttribute("UnitOfMeasure", mapGenericProps.get(AmzCommonConstants.PROP_DEFAULT_UOM));
				eleItem.setAttribute("ProductClass",
						mapGenericProps.get(AmzCommonConstants.PROP_DEFAULT_PRODUCT_CLASS));
				eleItem.setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterPriseCode);
				AmzCommonUtil.logAmzConnRequest(outDoc);
				logger.debug("Input Document to AmzPostMsgToInternalQueue  is:  " + SCXmlUtil.getString(outDoc));
				AmzCommonUtil.invokeService(env, "AmzPostMsgToInternalQueue", outDoc);

			}
		} catch (YFSException e) {
			e.printStackTrace();
			YFSException yfse = new YFSException();
			yfse.setErrorCode("ERROR_WHILE_POSTING_INENTORY_CHANGED_MSG_TO_INTERNAL_QUEUE_FROM_AMAZON_ORDER_UPDATE");
			yfse.setErrorDescription(e.getMessage());
			logger.error("class: AmzUpdateAmazonOrderExecutionState | method: updateOMSInvSupply :"
					+ ExceptionUtils.getStackTrace(yfse));
			throw yfse;
		}
		logger.info("class: AmzUpdateAmazonOrderExecutionState | method: updateOMSInvSupply -- Ends");
		logger.timer("class: AmzUpdateAmazonOrderExecutionState | method: updateOMSInvSupply -- Ends");
	}

	/*
	 * This method is to log the request before from amazon update order
	 */
	private void prepareAndLogRequest(Document indoc) {
		logger.beginTimer("class: AmzProcessCreateOrderMessage | method: prepareAndLogRequest -- Starts");
		logger.info("class: AmzProcessCreateOrderMessage | method: prepareAndLogRequest -- Starts");
		logger.debug("class: AmzProcessCreateOrderMessage | method: prepareAndLogRequest: inDoc is: "
				+ AmzXMLUtil.getString(indoc));
		Element eleOrder = indoc.getDocumentElement();
		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE,
				AmzCommonConstants.STR_AMZCONN_UPDATE_ORDER);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,
				eleOrder.getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE));
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO,
				eleOrder.getAttribute(AmzLiterals.A_ORDER_NO));
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_FULFILLABLE_BY_AMAZON, AmzCommonConstants.STR_VAL_Y);
		AmzCommonUtil.logAmzConnRequest(logInput);
		logger.endTimer("class: AmzProcessCreateOrderMessage | method: prepareAndLogRequest -- Ends");
		logger.info("class: AmzProcessCreateOrderMessage | method: prepareAndLogRequest -- Ends");

	}

	/*
	 * This method is to log the response before from amazon update order
	 */
	private void prepareAndLogResponse(String processStatus, String strAmazonOrderId, Document indoc, String message) {
		logger.beginTimer("class: AmzProcessCreateOrderMessage | method: prepareAndLogResponse -- Starts");
		logger.info("class: AmzProcessCreateOrderMessage | method: prepareAndLogResponse -- Starts");
		Element eleOrder = indoc.getDocumentElement();
		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE,
				AmzCommonConstants.STR_AMZCONN_UPDATE_ORDER);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,
				eleOrder.getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE));
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, strAmazonOrderId);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO,
				eleOrder.getAttribute(AmzLiterals.A_ORDER_NO));
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_FULFILLABLE_BY_AMAZON, AmzCommonConstants.STR_VAL_Y);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_PROCESS_STATUS, "SUCCESS");
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_HTTP_MESSAGE, AmzCommonConstants.STR_OK);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_HTTP_CODE, AmzCommonConstants.STR_HTTP_STATUS_OK);
		if (!YFCObject.isVoid(message)) {
			if (processStatus.equalsIgnoreCase(AmzLiterals.STR_SUCCESS)) {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_MESSAGE, message);
			} else {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_ERROR_MSG, message);
			}
		}
		AmzCommonUtil.logAmzConnResponse(logInput);

		logger.endTimer("class: AmzProcessCreateOrderMessage | method: prepareAndLogResponse -- Ends");
		logger.info("class: AmzProcessCreateOrderMessage | method: prepareAndLogResponse -- Ends");

	}

	@Override
	public void setProperties(Properties inProps) {
		this.props = inProps;
	}

	/*
	 * This method to cancel a BWP orderline with notes if amazon order is cancelled
	 * in amazon while updating a desiredExecutionState as STARTED
	 */
	private void cancelOrderInOMS(YFSEnvironment env, Document indoc, String strExtnAmzOrdId) {
		logger.beginTimer("class: AmzProcessCreateOrderMessage | method: cancelOrderInOMS -- Starts");
		logger.info("class: AmzProcessCreateOrderMessage | method: cancelOrderInOMS -- Starts");
		env.setTxnObject("CancelledThroughDeliveryEvent", "Y");
		Document inDocChangeOrder = SCXmlUtil.createDocument(AmzLiterals.E_ORDER);
		Element eleOrderInChgOrd = inDocChangeOrder.getDocumentElement();
		eleOrderInChgOrd.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY,
				indoc.getDocumentElement().getAttribute(AmzLiterals.A_ORDER_HEADER_KEY));
		eleOrderInChgOrd.setAttribute(AmzLiterals.A_OVERRIDE, AmzCommonConstants.STR_VAL_Y);
		Element eleInChgOrdOrdLines = SCXmlUtil.createChild(eleOrderInChgOrd, AmzLiterals.E_ORDER_LINES);

		Element eleInOrderLines = SCXmlUtil.getChildElement(indoc.getDocumentElement(), AmzLiterals.E_ORDER_LINES);
		NodeList nOrderLine = eleInOrderLines.getElementsByTagName(AmzLiterals.E_ORDER_LINE);
		int iOrderLineLen = nOrderLine.getLength();
		for (int i = 0; i < iOrderLineLen; i++) {
			Element eleInOrdLine = (Element) nOrderLine.item(i);
			String strExtnAmazonOrderId = SCXmlUtil.getXpathAttribute(eleInOrdLine, "Extn/@ExtnAmazonOrderId");
			if (!YFCObject.isVoid(strExtnAmazonOrderId) && strExtnAmzOrdId.equalsIgnoreCase(strExtnAmazonOrderId)) {
				Element eleInChgOrdOrdLine = SCXmlUtil.createChild(eleInChgOrdOrdLines, AmzLiterals.E_ORDER_LINE);
				eleInChgOrdOrdLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY,
						eleInOrdLine.getAttribute(AmzLiterals.A_ORDER_LINE_KEY));
				eleInChgOrdOrdLine.setAttribute(AmzLiterals.A_ACTION, AmzLiterals.STR_CANCEL);
				Element eleNotes = SCXmlUtil.createChild(eleInChgOrdOrdLine, AmzLiterals.E_NOTES);
				Element eleNote = SCXmlUtil.createChild(eleNotes, AmzLiterals.E_NOTE);
				eleNote.setAttribute(AmzLiterals.A_NOTE_TEXT,
						"Can not Initiate Execution as Amazon Order is already cancelled");
				eleNote.setAttribute(AmzLiterals.A_VISIBLE_TO_ALL, AmzCommonConstants.STR_VAL_Y);
			}
		}

		logger.debug("class: AmzProcessCreateOrderMessage | method: cancelOrderInOMS | changeOrder InDoc is: "
				+ SCXmlUtil.getString(inDocChangeOrder));
		AmzCommonUtil.invoke(env, AmzCommonConstants.API_CHANGE_ORDER, inDocChangeOrder);
		logger.endTimer("class: AmzProcessCreateOrderMessage | method: cancelOrderInOMS -- Ends");
		logger.info("class: AmzProcessCreateOrderMessage | method: cancelOrderInOMS -- Ends");

	}

	/*
	 * This method will validate a response, if any errors then invoke a
	 * getAmazonOrderDetails check the status of the order if it is cancelled in
	 * amazon cancel a BWP order associated line in OMS or else throw an Exception
	 * in OMS
	 */
	public void validateResponseMessage(YFSEnvironment env, String output, Document indoc, String strExtnAmzOrdId)
			throws JSONException, YFSException, RemoteException, YIFClientCreationException, XPathExpressionException {
		logger.timer("class: AmzCommonUtil | method: validateResponseMessage -- Starts");
		logger.info("class: AmzCommonUtil | method: validateResponseMessage -- Starts");
		JSONObject outputJson = new JSONObject(output);
		if (outputJson.has("errors")) {
			JSONArray errors = outputJson.getJSONArray("errors");
			if (errors.length() > 0) {
				JSONObject error = errors.getJSONObject(0);
				String strErrorMessage = error.optString("message", "Unknown error occurred");
				JSONObject extensions = error.optJSONObject("extensions");
				String strErrorCode = null;
				String strErrorType = null;
				if (extensions != null && extensions.has("classification")) {
					JSONObject classification = extensions.getJSONObject("classification");
					strErrorCode = classification.optString("errorCode", "Unknown code");
					strErrorType = classification.optString("errorType", "Unknown error type");
				}
				logger.debug("class: AmzCommonUtil | method: validateResponseMessage | errorMessage ::"
						+ strErrorMessage + '\n');
				logger.debug(
						"class: AmzCommonUtil | method: validateResponseMessage | errorCode ::" + strErrorCode + '\n');
				logger.debug(
						"class: AmzCommonUtil | method: validateResponseMessage | errorType ::" + strErrorType + '\n');
				Document getOrdInDoc = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER);
				Element ordInDocEle = getOrdInDoc.getDocumentElement();
				ordInDocEle.setAttribute("AmzOrderID", strExtnAmzOrdId);
				ordInDocEle.setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE,
						AmzCommonConstants.STR_AMZCONN_UPDATE_ORDER);
				ordInDocEle.setAttribute(AmzLiterals.A_ENTERPRISE_CODE,
						indoc.getDocumentElement().getAttribute(AmzLiterals.A_ENTERPRISE_CODE));

				Document orderOutDoc = AmzCommonUtil.callService(env, getOrdInDoc,
						AmzCommonConstants.SERVICE_AMAZON_GET_ORDER_DETAILS, null);
				if (!YFCObject.isVoid(orderOutDoc)) {
					Element elemetadata = AmzXMLUtil.getXpathElement(orderOutDoc.getDocumentElement(),
							"data/order/metadata");
					if (!YFCObject.isVoid(elemetadata)) {
						String strExecutionState = elemetadata.getAttribute("executionState");
						logger.debug(
								"AmzUpdateAmazonOrderExecutionState.updateAmzOrderExecutionstate: strExecutionState is: :"
										+ strExecutionState);

						if (!YFCObject.isVoid(strExecutionState)
								&& AmzCommonConstants.AMZ_EXECUTION_STATE_NOT_STARTED.equals(strExecutionState)) {
							NodeList nPendingPackagelist = AmzXMLUtil.getXpathNodes(orderOutDoc.getDocumentElement(),
									"data/order/packageInformation/summary[@state='PENDING']");
							NodeList nCancelledPackagelist = AmzXMLUtil.getXpathNodes(orderOutDoc.getDocumentElement(),
									"data/order/packageInformation/summary[@state='CANCELLED']");
							if (nCancelledPackagelist.getLength() > 0 && nPendingPackagelist.getLength() == 0) {
								cancelOrderInOMS(env, indoc, strExtnAmzOrdId);
							} else {
								AmzCommonUtil.throwCustomExecption(strErrorMessage, strErrorCode, strErrorType);
							}
						} else {
							AmzCommonUtil.throwCustomExecption(strErrorMessage, strErrorCode, strErrorType);
						}
					}
				}

				logger.info("class: AmzCommonUtil | method: validateResponseMessage -- Ends");
				logger.timer("class: AmzCommonUtil | method: validateResponseMessage -- Ends");

			}
		}

	}

}
