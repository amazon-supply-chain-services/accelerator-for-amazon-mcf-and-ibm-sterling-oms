package com.amazon.oms.order.api;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.List;

import javax.xml.xpath.XPathExpressionException;

import org.json.JSONException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.interop.japi.YIFClientCreationException;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;
import org.apache.commons.lang3.exception.ExceptionUtils;


/*
 * This class invoke the changeOrder to update the Amazon order info in the OMS.
 * To cancel the orderline if amazon order creation failed for BWP lines.
 */
public class AmzUpdateOrdWithAmazonOrdInfo {
	 final YFCLogCategory logger = YFCLogCategory.instance(AmzUpdateOrdWithAmazonOrdInfo.class);
	 HashMap<String, String> lineKeyToUpdateAmzLineItemAlias = new HashMap<>();

	/*
	 * This method invoke changeOrder to update Amazon order info in OMS.
	 */
	public  void updateOMSOrderWithAmzResp(YFSEnvironment env, Element eleOutOrder,
			List<String> amzCreateOrdElgPrimeLineNo, String strAmazonOrderid, List<String> amzCreateOrdElgOrderLineKey)
			throws Exception {
		logger.timer("class: AmzUpdateOrdWithAmazonOrdInfo | method: updateOMSOrderWithAmzResp -- Starts");
		logger.info("class: AmzUpdateOrdWithAmazonOrdInfo | method: updateOMSOrderWithAmzResp -- Starts");
		fetchLineItemId(env, amzCreateOrdElgPrimeLineNo, strAmazonOrderid, amzCreateOrdElgOrderLineKey, eleOutOrder);
		Document inChangeOrdDoc = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER);
		Element eleInChgOrd = inChangeOrdDoc.getDocumentElement();
		eleInChgOrd.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY,
				eleOutOrder.getAttribute(AmzLiterals.A_ORDER_HEADER_KEY));
		eleInChgOrd.setAttribute(AmzLiterals.A_OVERRIDE, AmzCommonConstants.STR_VAL_Y);

		Element eleOrderLines = AmzXMLUtil.createChild(eleInChgOrd, AmzLiterals.E_ORDER_LINES);
		int iorderLineLen = amzCreateOrdElgPrimeLineNo.size();
		for (int i = 0; i < iorderLineLen; i++) {
			String strPrimeLineNo = amzCreateOrdElgPrimeLineNo.get(i);
			logger.debug("AmzCreateOrder. updateOMSOrderWithAmzResp.strPrimeLineNo is: " + strPrimeLineNo);
			Element eleOrderLine = AmzXMLUtil.getXpathElement(eleOutOrder,
					"OrderLines/OrderLine[@PrimeLineNo='" + strPrimeLineNo + "']");
			String sOrderLinekey = eleOrderLine.getAttribute(AmzLiterals.A_ORDER_LINE_KEY);
			logger.debug("AmzCreateOrder. updateOMSOrderWithAmzResp.sOrderLinekey is: " + sOrderLinekey);
			Element eleInChgOrdOrdLine = AmzXMLUtil.createChild(eleOrderLines, AmzLiterals.E_ORDER_LINE);
			eleInChgOrdOrdLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, sOrderLinekey);
			Element eleInChgOrdOrdLineExtn = AmzXMLUtil.createChild(eleInChgOrdOrdLine, AmzLiterals.E_EXTN);
			eleInChgOrdOrdLineExtn.setAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID, strAmazonOrderid);
			String strLineItemID = lineKeyToUpdateAmzLineItemAlias.get(sOrderLinekey);
			logger.debug("AmzCreateOrder. updateOMSOrderWithAmzResp.strLineItemID is: " + strLineItemID);
			if (!YFCObject.isVoid(strLineItemID)) {
				eleInChgOrdOrdLineExtn.setAttribute(AmzLiterals.A_EXTN_AMZ_LINE_ITEM_ALIAS, strLineItemID);
			}

		}
		logger.debug(
				"AmzCreateOrder. updateOMSOrderWithAmzResp. Input doc to changeOrder to update amazon order id is: "
						+ AmzXMLUtil.getString(inChangeOrdDoc));
		AmzCommonUtil.invokeService(env, AmzCommonConstants.SERVICE_AMZ_CHANGE_ORDER_SERVICE, inChangeOrdDoc);
		logger.info("class: AmzUpdateOrdWithAmazonOrdInfo | method: updateOMSOrderWithAmzResp -- Ends");
		logger.timer("class: AmzUpdateOrdWithAmazonOrdInfo | method: updateOMSOrderWithAmzResp -- Ends");

	}

	/*
	 * This method fetch the alias ID from amazon and stamp in OMS
	 */
	private  void fetchLineItemId(YFSEnvironment env, List<String> amzCreateOrdElgPrimeLineNo,
			String strAmazonOrderid, List<String> amzCreateOrdElgOrderLineKey, Element eleOutOrder) throws Exception {

		logger.info("class: AmzUpdateOrdWithAmazonOrdInfo | method: amzCreateOrdElgPrimeLineNo -- Starts");
		logger.timer("class: AmzUpdateOrdWithAmazonOrdInfo | method: amzCreateOrdElgPrimeLineNo -- Starts");

		Document getOrdInDoc = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER);
		Element ordInDocEle = getOrdInDoc.getDocumentElement();
		ordInDocEle.setAttribute("AmzOrderID", strAmazonOrderid);
		ordInDocEle.setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE, AmzCommonConstants.STR_AMZCONN_CREATE_ORDER);
		ordInDocEle.setAttribute(AmzLiterals.A_ENTERPRISE_CODE,
				eleOutOrder.getAttribute(AmzLiterals.A_ENTERPRISE_CODE));

		Document orderOutDoc = AmzCommonUtil.callService(env, getOrdInDoc,
				AmzCommonConstants.SERVICE_AMAZON_GET_ORDER_DETAILS, null);
		if (!YFCObject.isVoid(orderOutDoc)) {

			Element eleRoot = orderOutDoc.getDocumentElement();
			Element eleData = AmzXMLUtil.getChildElement(eleRoot, "data");
			Element eleOrder = AmzXMLUtil.getChildElement(eleData, "order");

			NodeList nlineItems = eleOrder.getElementsByTagName("lineItems");

			// Loop through lineItems to get the aliases and aliasIds
			for (int i = 0; i < nlineItems.getLength(); i++) {
				Element eleLineItem = (Element) nlineItems.item(i);
				NodeList aliases = eleLineItem.getElementsByTagName(AmzLiterals.A_JS_ALIASES);
				String sOrderLineKey = null;
				String sPrimeLineNo = null;
				// Loop through aliases array and print the aliasId
				for (int j = 0; j < aliases.getLength(); j++) {
					Element elealias = (Element) aliases.item(j);
					String sAliasType = elealias.getAttribute(AmzLiterals.A_JS_ALIAS_TYPE);
					logger.debug("aliasId: " + sAliasType);
					String sAliasId = elealias.getAttribute(AmzLiterals.A_JS_ALIAS_ID);
					logger.debug("aliasId: " + sAliasId);
					switch (sAliasType) {
					case "OMS_PRIMELINE_NO":
						sPrimeLineNo = sAliasId;
						break;
					case "OMS_ORDERLINE_KEY":
						sOrderLineKey = sAliasId;
						break;
					default:
						sOrderLineKey = null;
						sPrimeLineNo = null;
					}
				}
				logger.debug("sOrderLineKey is: " + sOrderLineKey);
				logger.debug("sPrimeLineNo is: " + sPrimeLineNo);
				String sLineItemId = eleLineItem.getAttribute("id");
				logger.debug("sLineItemId is: " + sLineItemId);
				if (!YFCObject.isVoid(sPrimeLineNo) && !YFCObject.isVoid(sOrderLineKey)
						&& !YFCObject.isVoid(sLineItemId) && amzCreateOrdElgOrderLineKey.contains(sOrderLineKey)
						&& amzCreateOrdElgPrimeLineNo.contains(sPrimeLineNo)) {
					lineKeyToUpdateAmzLineItemAlias.put(sOrderLineKey, sLineItemId);
				}
			}
		}
		logger.info("class: AmzUpdateOrdWithAmazonOrdInfo | method: amzCreateOrdElgPrimeLineNo -- Ends");
		logger.timer("class: AmzUpdateOrdWithAmazonOrdInfo | method: amzCreateOrdElgPrimeLineNo -- Ends");

	}

	/*
	 * This method invoke changeOrder api to cancel orderline if BWP amazon order
	 * creation failed.
	 * 
	 * @param env
	 * 
	 * @param eleOutOrder
	 * 
	 * @param amzCreateOrdElgPrimeLineNo
	 * 
	 * @param output
	 * 
	 * @return
	 * 
	 * @throws XPathExpressionException
	 * 
	 * @throws RemoteException
	 * 
	 * @throws YIFClientCreationException
	 * 
	 * @throws JSONException This method to prepare change Order input* and call API
	 */

	public  Document invokeChangeOrderApi(YFSEnvironment env, Element eleOutOrder,
			List<String> amzCreateOrdElgPrimeLineNo, String output)
			throws XPathExpressionException, RemoteException, JSONException, YIFClientCreationException {
		logger.timer("class: AmzUpdateOrdWithAmazonOrdInfo | method: invokeChangeOrderApi -- Starts");
		logger.info("class: AmzUpdateOrdWithAmazonOrdInfo | method: invokeChangeOrderApi -- Starts");
		String strOrderNo = eleOutOrder.getAttribute(AmzLiterals.A_ORDER_NO);
		String strOhKey = eleOutOrder.getAttribute(AmzLiterals.A_ORDER_HEADER_KEY);
		String stDocType = eleOutOrder.getAttribute(AmzLiterals.A_DOCUMENT_TYPE);
		Document inDocChangeOrder = null;
		Document outDocChangeOrder = null;
		// Build the change order XML structure
		inDocChangeOrder = SCXmlUtil.createDocument(AmzLiterals.E_ORDER);
		Element eleChOrder = inDocChangeOrder.getDocumentElement();
		eleChOrder.setAttribute(AmzLiterals.A_ORDER_NO, strOrderNo);
		eleChOrder.setAttribute(AmzLiterals.A_OVERRIDE, AmzLiterals.STR_VAL_Y);
		eleChOrder.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY, strOhKey);
		eleChOrder.setAttribute(AmzLiterals.A_DOCUMENT_TYPE, stDocType);
		eleChOrder.setAttribute(AmzLiterals.A_SELECT_METHOD, AmzCommonConstants.STR_WAIT);

		// Create the order lines container element for change order
		Element eleChOrderLines = AmzXMLUtil.createChild(eleChOrder, AmzLiterals.E_ORDER_LINES);
		Element eleChOrderLine = null;
		// Get the OrderLines element from the provided order
		Element eleOrdLines = AmzXMLUtil.getChildElement(eleOutOrder, AmzLiterals.E_ORDER_LINES);
		Element eleOrderLine = null;

		// Get the number of eligible lines
		int iAmzCreateOrdElgLines = amzCreateOrdElgPrimeLineNo.size();

		// Loop through the eligible prime line numbers and build the change order input
		for (int i = 0; i < iAmzCreateOrdElgLines; i++) {
			String strPrimeLineNo = amzCreateOrdElgPrimeLineNo.get(i);

			// Get the specific order line based on PrimeLineNo
			eleOrderLine = AmzXMLUtil.getXpathElement(eleOrdLines, "OrderLine[@PrimeLineNo='" + strPrimeLineNo + "']");

			if (eleOrderLine != null) {
				String strOrderLineKey = eleOrderLine.getAttribute(AmzLiterals.A_ORDER_LINE_KEY);
				// Create a change order line element for each eligible order line
				eleChOrderLine = inDocChangeOrder.createElement(AmzLiterals.E_ORDER_LINE);
				eleChOrderLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, strOrderLineKey);
				eleChOrderLine.setAttribute(AmzLiterals.A_ACTION, AmzLiterals.STR_CANCEL);
				// Add the change order line to the order lines element
				eleChOrderLines.appendChild(eleChOrderLine);
				createNewException(env, strOrderNo, strOhKey, output);
			}
		}
		logger.debug("Change order Api Input document: " + SCXmlUtil.getString(inDocChangeOrder));
		// If the outChangeOrderDoc is successfully created, you can return or use it as
		// needed.
		try {
			outDocChangeOrder = AmzCommonUtil.callAPI(env, inDocChangeOrder, AmzCommonConstants.API_CHANGE_ORDER, null);
		} catch (YFSException e) {
			YFSException yfsException = new YFSException();
			yfsException.setErrorCode("ERROR_WHILE_CANCELLING_ORDERLINE_FROM_UPDATE_OMS_ORDER");
			yfsException.setErrorDescription(
					"Error While Invoking changeOrder api to Cancel BWP OrderLine from OMS due to Amazon Order Creation Failure");
			logger.error("Exception in AmzUpdateOrdWithAmazonOrdInfo.createNewException Method: "
					+ ExceptionUtils.getStackTrace(yfsException));
			throw AmzCommonUtil.createException(e);

		}
		logger.debug("Change order Api output document: " + SCXmlUtil.getString(outDocChangeOrder));
		logger.info("class: AmzUpdateOrdWithAmazonOrdInfo | method: invokeChangeOrderApi -- Ends");
		logger.timer("class: AmzUpdateOrdWithAmazonOrdInfo | method: invokeChangeOrderApi -- Ends");
		return outDocChangeOrder;

	}

	/**
	 * This method create a Alert exception if order creation failed from amzaon.
	 * 
	 * @param env
	 * @param strOrderNo
	 * @param strOrderHeaderKey
	 * @param strOrderLineKey
	 * @param output
	 * @throws RemoteException
	 * @throws YIFClientCreationException
	 * @throws JSONException              This method to create an exception after
	 *                                    order gets cancelled.
	 */
	public  void createNewException(YFSEnvironment env, String strOrderNo, String strOrderHeaderKey,
			String output) throws RemoteException, YIFClientCreationException, JSONException {
		logger.info("class: AmzUpdateOrdWithAmazonOrdInfo | method: createNewException -- Starts");
		logger.timer("class: AmzUpdateOrdWithAmazonOrdInfo | method: createNewException -- Starts");
		String strExceptionType = "AmazonCreateOrderException";
		HashMap<String, String> errorDetails = AmzCommonUtil.getErrorCodeAndDetails(output);
		String strMessage = null;
		String strErrorCode = null;
		String strErrorType = null;
		String strCode = null;
		for (int i = 0; i < errorDetails.size() / 4; i++) {
			strMessage = errorDetails.get("ErrorMessage " + (i + 1));
			strErrorType = errorDetails.get("ErrorType " + (i + 1));
			strErrorCode = errorDetails.get("ErrorCode " + (i + 1));
			strCode = errorDetails.get("Code " + (i + 1));

			logger.debug(
					"class: AmzUpdateOrdWithAmazonOrdInfo | method: createNewException | strMessage :: " + strMessage);
			logger.debug("class: AmzUpdateOrdWithAmazonOrdInfo | method: createNewException | strErrorType :: "
					+ strErrorType);
			logger.debug("class: AmzUpdateOrdWithAmazonOrdInfo | method: createNewException | strErrorCode :: "
					+ strErrorCode);
			logger.debug("class: AmzUpdateOrdWithAmazonOrdInfo | method: createNewException | strCode :: " + strCode);

			Document docInCreateException = null;
			docInCreateException = SCXmlUtil.createDocument(AmzLiterals.E_INBOX);
			Element eleInbox = docInCreateException.getDocumentElement();
			eleInbox.setAttribute(AmzLiterals.A_ACTIVE_FLAG, AmzLiterals.STR_VAL_Y);
			eleInbox.setAttribute(AmzLiterals.A_EXCEPTION_TYPE, strExceptionType);
			eleInbox.setAttribute(AmzLiterals.A_CONSOLIDATE, AmzLiterals.STR_VAL_Y);
			eleInbox.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY, strOrderHeaderKey);
			eleInbox.setAttribute(AmzLiterals.A_ORDER_NO, strOrderNo);
			eleInbox.setAttribute(AmzLiterals.A_DETAIL_DESC, strMessage);
			String strDesc = "Error " + strErrorCode + ":" + strErrorType + "-" + strCode;
			eleInbox.setAttribute(AmzLiterals.A_DESC, strDesc);
			logger.debug("Input to createException API is :: " + SCXmlUtil.getString(docInCreateException));

			try {
				AmzCommonUtil.callAPI(env, docInCreateException, AmzCommonConstants.API_CREATE_EXCEPTION, null);
			} catch (YFSException e) {
				YFSException yfsException = new YFSException();
				yfsException.setErrorCode("ERROR_WHILE_CREATING_ALERT_FROM_UPDATE_OMS_ORDER");
				yfsException.setErrorDescription(
						"Error While invoking createException api to Create a alert during update order due to amazon order creation failure");
				logger.error("Exception in AmzUpdateOrdWithAmazonOrdInfo.createNewException Method: "
						+ ExceptionUtils.getStackTrace(yfsException));
				throw AmzCommonUtil.createException(e);
			}

		}
		logger.info("class: AmzUpdateOrdWithAmazonOrdInfo | method: createNewException -- Ends");
		logger.timer("class: AmzUpdateOrdWithAmazonOrdInfo | method: createNewException -- Ends");
	}

}
