package com.amazon.oms.returns.api;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzEnterpriseBPIDXRefUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.interop.japi.YIFClientCreationException;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCCommon;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/**
 * This class is invoked through a async service 'AmazonProcessReturnSyncEvents'
 * which will be used to read messages from Queue and process the events. Method
 * processReturnUpdates will check if return is created with received return id
 * or not. If return is not created create a return in OMS and update status
 * else just update status.
 */

public class AmzProcessReturnUpdates {
	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzProcessReturnUpdates.class);

	Map<String, String> genricPropertiesMap = new HashMap<>();
	boolean duplicateEvent = false;
	boolean isRefundProcessedForDeliveryID = false;
	List<String> lSoLineItemAlias = new ArrayList<>();
	Document getSalesOrderLineListOp = null;

	public Document processReturnUpdates(YFSEnvironment env, Document inDoc) throws Exception {
		logger.beginTimer("class: AmzProcessReturnUpdates | method: processReturnUpdates -- Starts");

		Document outDoc = null;
		Document getOrderDetails = null;
		Document getOrderListOp = null;
		try {
			Element eleInDoc = inDoc.getDocumentElement() ;

			String amazonOrderId = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID);
			String extnAmazonReturnOrderId = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_RETURN_ID);
			String eventType = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE);

			String strEnterpriseCode = AmzEnterpriseBPIDXRefUtil.getEnterpriseCode(env,
					eleInDoc.getAttribute("BusinessProductID"));

			// Fetch enterprise level generic properties
			Document propertyDoc = SCXmlUtil.createDocument(AmzLiterals.E_PROPERTIES);
			propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);
			genricPropertiesMap = AmzGetGenericProperty.getGenericProperties(env, propertyDoc);

			// Call getOrderList api
			Document getOrderListInput = SCXmlUtil.createDocument(AmzLiterals.E_ORDER);
			Element eleOrderInput = getOrderListInput.getDocumentElement();
			eleOrderInput.setAttribute(AmzLiterals.A_DOCUMENT_TYPE, AmzCommonConstants.STR_RETURN_DOCUMENT_TYPE);
			eleOrderInput.setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);

			Element eleOrderExtn = SCXmlUtil.createChild(eleOrderInput, AmzLiterals.E_EXTN);
			eleOrderExtn.setAttribute(AmzLiterals.A_EXTN_AMAZON_RETURN_ORDER_ID, extnAmazonReturnOrderId);
			logger.debug("getOrderListInput is: " + SCXmlUtil.getString(getOrderListInput));

			getOrderListOp = AmzCommonUtil.invokeAPI(env, AmzCommonConstants.TEMP_GET_ORDER_LIST_AMZ_INIT_RETURN,
					AmzCommonConstants.API_GET_ORDER_LIST, getOrderListInput);
			logger.debug("getOrderList output: " + SCXmlUtil.getString(getOrderListOp));

			invokeSalesOrderGetOrderLineList(env, amazonOrderId, inDoc);

			// Query Amazon Order to Fetch order details
			Document getOrderDetailsInput = SCXmlUtil.createDocument(AmzLiterals.STR_ORDER);
			getOrderDetailsInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMZ_ORDER_ID, amazonOrderId);
			getOrderDetailsInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE, eventType);
			getOrderDetailsInput.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);

			if (YFCObject.isVoid(amazonOrderId)) {
				YFSException ex = new YFSException();
				ex.setErrorCode(AmzLiterals.STR_RETURN_ERROR_CODE);
				ex.setErrorDescription("Amazon OrderId is blank or empty");
				prepareAndLogResponse(AmzLiterals.STR_ERROR, inDoc, ex.getErrorDescription());
				throw ex;
			}
			getOrderDetails = AmzCommonUtil.callService(env, getOrderDetailsInput,
					AmzCommonConstants.SERVICE_AMAZON_GET_ORDER_DETAILS, null);
			logger.debug("getOrderDetails: " + SCXmlUtil.getString(getOrderDetails));

			if (!YFCObject.isVoid(getOrderDetails) && !YFCObject.isVoid(!YFCObject.isVoid(getSalesOrderLineListOp))) {
				addSoLineItemAliasOfReturnToList(getOrderDetails, inDoc);
			}
			outDoc = processReturnStatusEventUpdate(env, inDoc, getOrderDetails, getOrderListOp, outDoc);
		} catch (YFSException e) {
			prepareAndLogResponse(AmzLiterals.STR_ERROR, inDoc, e.getErrorDescription());
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			YFSException yfse = new YFSException();
			yfse.setErrorCode(AmzLiterals.STR_RETURN_ERROR_CODE);
			yfse.setErrorDescription(e.getMessage());
			prepareAndLogResponse(AmzLiterals.STR_ERROR, inDoc, yfse.getErrorDescription());
			throw yfse;
		}
		logger.endTimer("class: AmzProcessReturnUpdates | method: processReturnUpdates -- End");
		return outDoc;
	}

	private Document processReturnStatusEventUpdate(YFSEnvironment env, Document inDoc, Document getOrderDetails,
			Document getOrderListOp, Document outDoc)
			throws XPathExpressionException, RemoteException, YIFClientCreationException {
		logger.beginTimer("class: AmzProcessReturnUpdates | method: processReturnStatusEventUpdate -- Starts");

		Element eleInDoc = inDoc.getDocumentElement();
		String eventType = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE);
		String amazonOrderId = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID);
		String extnAmazonReturnOrderId = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_RETURN_ID);
		if (eventType.equalsIgnoreCase(AmzLiterals.STR_RETURN_STARTED)) {
			outDoc = processReturnStartedUpdate(env, inDoc, getOrderDetails, getOrderListOp);
		} else {
			NodeList nReturnOrderLine = AmzXMLUtil.getXpathNodes(getOrderListOp.getDocumentElement(),
					"Order/OrderLines/OrderLine/Extn[@ExtnAmazonReturnOrderId='" + extnAmazonReturnOrderId + "']");

			if (nReturnOrderLine.getLength() == 0) {
				createReturnAndUpdateStatus(env, inDoc, getOrderDetails);
			} else {

				boolean isElgElgForStatusUpdate = checkElgForStatusUpdate(getOrderListOp);
				logger.debug("isElgElgForStatusUpdate is: " + isElgElgForStatusUpdate);
				if (isElgElgForStatusUpdate) {
					String baseDropStatus = "";
					switch (eventType) {
					case AmzLiterals.STR_RETURN_PACKAGE_IN_TRANSIT:
						baseDropStatus = genricPropertiesMap.get(AmzCommonConstants.PROP_DROPSTATUS_INTRANSIT);
						outDoc = processReturnDeliveryUpdates(env, inDoc, getOrderDetails, getOrderListOp,
								baseDropStatus);
						break;
					case AmzLiterals.STR_RETURN_PACKAGE_DELIVERED:
						baseDropStatus = genricPropertiesMap.get(AmzCommonConstants.PROP_DROPSTATUS_DELIVERED);
						outDoc = processReturnDeliveryUpdates(env, inDoc, getOrderDetails, getOrderListOp,
								baseDropStatus);
						break;
					case AmzLiterals.STR_RETURN_PACKAGE_DELIVERY_FAILED:
						baseDropStatus = genricPropertiesMap.get(AmzCommonConstants.PROP_DROPSTATUS_FAILED);
						outDoc = processReturnDeliveryUpdates(env, inDoc, getOrderDetails, getOrderListOp,
								baseDropStatus);
						break;
					case AmzLiterals.STR_RETURN_ITEM_GRADED:
						baseDropStatus = genricPropertiesMap.get(AmzCommonConstants.PROP_DROPSTATUS_ITEMGRADED);
						outDoc = processReturnItemGradedUpdate(env, inDoc, getOrderDetails, getOrderListOp,
								baseDropStatus);
						break;
					case AmzLiterals.STR_RETURN_COMPLETED:
						outDoc = processReturnCompleteUpdate(env, inDoc, getOrderDetails);
						break;
					default:
						logger.debug(
								"class: AmzProcessReturnUpdates | method: processReturnUpdates  | Non-Processed Event "
										+ eventType);
						break;
					}
				} else {
					saveAmazonEvent(env, inDoc, getOrderListOp);

					String msg = "Return is already in received status for return id " + extnAmazonReturnOrderId
							+ " in OMS. Message is ignored , AmazonOrderId = " + amazonOrderId;

					logger.info("Method: processReturnUpdates | EventType = " + eventType);
					logger.info(msg);
					prepareAndLogResponse(AmzLiterals.STR_SUCCESS, inDoc, msg);
				}
			}
		}
		logger.endTimer("class: AmzProcessReturnUpdates | method: processReturnStatusEventUpdate -- End");

		return outDoc;
	}

	private void saveAmazonEvent(YFSEnvironment env, Document inDoc, Document getOrderListOp)
			throws XPathExpressionException, YFSException, RemoteException, YIFClientCreationException {
		logger.beginTimer("class: AmzProcessReturnUpdates | method: saveAmazonEvent -- Starts");

		Element eleInDoc = inDoc.getDocumentElement();

		String eventType = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE);
		String resources = eleInDoc.getAttribute("Resources");
		String eventId = eleInDoc.getAttribute(AmzLiterals.ATTR_AMZ_EVENT_ID);

		String enterpriseCode = AmzXMLUtil.getXpathAttribute(getOrderListOp.getDocumentElement(),
				"Order/@EnterpriseCode");
		logger.debug("enterpriseCode is: " + enterpriseCode);

		String orderNo = AmzXMLUtil.getXpathAttribute(getOrderListOp.getDocumentElement(), "Order/@OrderNo");
		logger.debug("orderNo is: " + orderNo);

		Document createAmzConnReturnEventsDoc = SCXmlUtil.createDocument(AmzLiterals.STR_AMZCONN_RETURN_EVENTS);
		createAmzConnReturnEventsDoc.getDocumentElement().setAttribute(AmzLiterals.A_ORDER_NO, orderNo);
		createAmzConnReturnEventsDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, enterpriseCode);
		createAmzConnReturnEventsDoc.getDocumentElement().setAttribute(AmzLiterals.ATTR_EVENT_ID, eventId);
		createAmzConnReturnEventsDoc.getDocumentElement().setAttribute(AmzLiterals.ATTR_EVENT_TYPE, eventType);
		createAmzConnReturnEventsDoc.getDocumentElement().setAttribute(AmzLiterals.ATTR_RESOURCES, resources);

		AmzCommonUtil.callService(env, createAmzConnReturnEventsDoc, AmzLiterals.SERVICE_AMZCONN_SAVE_AMZ_EVENTS, null);
		logger.endTimer("class: AmzProcessReturnUpdates | method: saveAmazonEvent -- End");

	}

	private Document processReturnCompleteUpdate(YFSEnvironment env, Document inDoc, Document getOrderDetails)
			throws XPathExpressionException, YFSException, RemoteException, YIFClientCreationException {
		logger.beginTimer("class: AmzProcessReturnUpdates | method: processReturnCompleteUpdate -- Starts");

		// Process Refund for return complete update Starts
		prepareInputForRefunds(env, inDoc, getOrderDetails, null);
		// Process Refund for return complete update Ends
		logger.endTimer("class: AmzProcessReturnUpdates | method: processReturnCompleteUpdate -- End");

		return inDoc;

	}

	private Document processReturnItemGradedUpdate(YFSEnvironment env, Document inDoc, Document getOrderDetails,
			Document getOrderListOp, String baseDropStatus)
			throws RemoteException, YIFClientCreationException, XPathExpressionException {
		logger.beginTimer("class: AmzProcessReturnUpdates | method: processReturnItemGradedUpdate -- Starts");

		Document outDoc = null;
		String extnAmazonReturnOrderId = inDoc.getDocumentElement().getAttribute(AmzLiterals.ATTR_AMAZON_RETURN_ID);
		String extnAmazonReturnLineItem = inDoc.getDocumentElement()
				.getAttribute(AmzLiterals.ATTR_AMAZON_RETURN_LINE_ITEM);
		logger.debug("extnAmazonReturnLineItem is: " + extnAmazonReturnLineItem);

		String eventType = inDoc.getDocumentElement().getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE);

		Element eleOrderListOp = getOrderListOp.getDocumentElement();
		Element eleGetOrderDetails = getOrderDetails.getDocumentElement();
		String strOrderHeaderKey = AmzXMLUtil.getXpathAttribute(eleOrderListOp, "Order/@OrderHeaderKey");
		Document amzConnEventsListDoc = getAmzConnEvents(env, strOrderHeaderKey);
		String strAmzConnEvents = null;
		if (!YFCObject.isVoid(
				AmzXMLUtil.getXpathAttribute(amzConnEventsListDoc.getDocumentElement(), "AmzConnEvents/@AmzConnEvents"))) {
			strAmzConnEvents = AmzXMLUtil.getXpathAttribute(amzConnEventsListDoc.getDocumentElement(),
					"AmzConnEvents/@AmzConnEvents");
		}
		logger.debug("strAmzConnEvents is: " + strAmzConnEvents);

		Document amzConnEventsDoc = null;
		if (!YFCCommon.isVoid(strAmzConnEvents)) {
			amzConnEventsDoc = SCXmlUtil.createFromString(strAmzConnEvents);
			logger.debug("amzConnEventsDoc: " + SCXmlUtil.getString(amzConnEventsDoc));
			Element eleEvent = AmzXMLUtil.getXpathElement(amzConnEventsDoc.getDocumentElement(),
					"/Events/Event[@EventID='" + extnAmazonReturnLineItem + "' and @EventType='" + eventType + "']");
			if (!YFCCommon.isVoid(eleEvent)) {
				duplicateEvent = true;
			}
		}

		if (duplicateEvent) {
			String msg = "This event = " + eventType + " for return line item= " + extnAmazonReturnLineItem
					+ " is already processed in OMS. Message is ignored , AmazonReturnOrderId = "
					+ extnAmazonReturnOrderId;

			logger.info("Method: processReturnItemGradedUpdate | EventType = " + eventType);
			logger.info(msg);
			prepareAndLogResponse(AmzLiterals.STR_SUCCESS, inDoc, msg);
		} else {

			Element eleReturns = SCXmlUtil.getXpathElement(eleGetOrderDetails, AmzLiterals.XPATH_AMZ_RETURNS);
			Element eleDetails = AmzXMLUtil.getXpathElement(eleReturns,
					"//details[@id='" + extnAmazonReturnOrderId + "']");

			Document changeOrderStatusInput = SCXmlUtil.createDocument(AmzLiterals.E_ORDER_STATUS_CHANGE);
			changeOrderStatusInput.getDocumentElement().setAttribute(AmzLiterals.A_DOCUMENT_TYPE,
					AmzLiterals.STR_RO_DOCUMENT_TYPE);
			changeOrderStatusInput.getDocumentElement().setAttribute(AmzLiterals.A_ORDER_HEADER_KEY, strOrderHeaderKey);
			changeOrderStatusInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_TRANSACTION_ID,
					AmzLiterals.TRANS_AMZ_RO_DELIVERY_UPDATE);

			Element eleOrderLines = SCXmlUtil.createChild(changeOrderStatusInput.getDocumentElement(),
					AmzLiterals.E_ORDER_LINES);

			Element eleReturnLineItem = AmzXMLUtil.getXpathElement(eleDetails,
					"returnLineItems[@id='" + extnAmazonReturnLineItem + "']");
			Element eleOrderLine = SCXmlUtil.createChild(eleOrderLines, AmzLiterals.E_ORDER_LINE);

			String strLineItemID = SCXmlUtil.getXpathAttribute(eleReturnLineItem,
					"returnFor/orderLineItemAmounts/lineItem/@id");
			String strQuantity = SCXmlUtil.getXpathAttribute(eleReturnLineItem,
					"returnFor/orderLineItemAmounts/amount/@value");
			String strOrderLineKey = AmzXMLUtil.getXpathAttribute(eleOrderListOp,
					"Order/OrderLines/OrderLine/Extn[@ExtnAmazonSoLineItemAlias='" + strLineItemID
							+ "' and  @ExtnAmazonReturnOrderId='" + extnAmazonReturnOrderId + "']/../@OrderLineKey");

			eleOrderLine.setAttribute(AmzLiterals.ATTR_QUANTITY, strQuantity);
			eleOrderLine.setAttribute(AmzLiterals.ATTR_BASE_DROP_STATUS, baseDropStatus);
			eleOrderLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, strOrderLineKey);

			logger.debug("Method: processReturnItemGradedUpdate changeOrderStatus inDoc"
					+ SCXmlUtil.getString(changeOrderStatusInput));

			try {
				AmzCommonUtil.callAPI(env, changeOrderStatusInput, AmzLiterals.API_CHANGE_ORDER_STATUS, null);
			} catch (YFSException e) {
				prepareAndLogResponse(AmzLiterals.STR_ERROR, inDoc, e.getErrorDescription());
				throw AmzCommonUtil.createException(e);
			}

			// changeOrder to save events
			Document eventsDoc = null;
			if (YFCCommon.isVoid(amzConnEventsDoc)) {
				eventsDoc = amzConnEventsDoc;
			} else {
				eventsDoc = SCXmlUtil.createDocument(AmzLiterals.E_EVENTS);
			}
			Element eleEvent = SCXmlUtil.createChild(eventsDoc.getDocumentElement(), AmzLiterals.E_EVENT);
			eleEvent.setAttribute(AmzLiterals.ATTR_EVENT_TYPE, eventType);
			eleEvent.setAttribute(AmzLiterals.ATTR_EVENT_ID, extnAmazonReturnLineItem);
			logger.debug("Changeorder amzConnEventsDoc: " + SCXmlUtil.getString(amzConnEventsDoc));
			logger.debug("Changeorder eventsDoc: " + SCXmlUtil.getString(eventsDoc));

			outDoc = updateAmzConnEvents(env, inDoc, outDoc, amzConnEventsListDoc, strOrderHeaderKey, eventsDoc);

			// Process Refund for return item graded update Starts
			prepareInputForRefunds(env, inDoc, getOrderDetails, null);
			// Process Refund for return item graded update Ends
		}
		logger.endTimer("class: AmzProcessReturnUpdates | method: processReturnItemGradedUpdate -- Ends");

		return outDoc;
	}

	private Document processReturnDeliveryUpdates(YFSEnvironment env, Document inDoc, Document getOrderDetails,
			Document getOrderListOp, String baseDropStatus)
			throws RemoteException, YIFClientCreationException, XPathExpressionException {
		logger.beginTimer("class: AmzProcessReturnUpdates | method: processReturnDeliveryUpdates -- Starts");

		Document outDoc = null;
		String extnAmazonReturnOrderId = inDoc.getDocumentElement().getAttribute(AmzLiterals.ATTR_AMAZON_RETURN_ID);
		String extnAmazonReturnDelivery = inDoc.getDocumentElement()
				.getAttribute(AmzLiterals.ATTR_AMAZON_RETURN_DELIVERY);
		logger.debug("extnAmazonReturnDelivery is: " + extnAmazonReturnDelivery);

		String eventType = inDoc.getDocumentElement().getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE);

		Element eleOrderListOp = getOrderListOp.getDocumentElement();
		Element eleGetOrderDetails = getOrderDetails.getDocumentElement();
		String strOrderHeaderKey = AmzXMLUtil.getXpathAttribute(eleOrderListOp, "Order/@OrderHeaderKey");
		Document amzConnEventsListDoc = getAmzConnEvents(env, strOrderHeaderKey);
		String strAmzConnEvents = null;
		if (!YFCObject.isVoid(
				AmzXMLUtil.getXpathAttribute(amzConnEventsListDoc.getDocumentElement(), "AmzConnEvents/@AmzConnEvents"))) {
			strAmzConnEvents = AmzXMLUtil.getXpathAttribute(amzConnEventsListDoc.getDocumentElement(),
					"AmzConnEvents/@AmzConnEvents");
		}
		logger.debug("strAmzConnEvents is: " + strAmzConnEvents);

		Document amzConnEventsDoc = null;
		if (!YFCCommon.isVoid(strAmzConnEvents)) {
			amzConnEventsDoc = SCXmlUtil.createFromString(strAmzConnEvents);
			logger.debug("amzConnEventsDoc: " + SCXmlUtil.getString(amzConnEventsDoc));
			Element eleEvent = AmzXMLUtil.getXpathElement(amzConnEventsDoc.getDocumentElement(),
					"/Events/Event[@EventID='" + extnAmazonReturnDelivery + "' and @EventType='" + eventType + "']");
			if (!YFCCommon.isVoid(eleEvent)) {
				duplicateEvent = true;
			}
			String refundEvent = genricPropertiesMap.get(AmzCommonConstants.PROP_RETUND_EVENT);
			Element eleRefundEvent = AmzXMLUtil.getXpathElement(amzConnEventsDoc.getDocumentElement(),
					"/Events/Event[@EventID='" + extnAmazonReturnDelivery + "' and @EventType='" + refundEvent + "']");
			if (!YFCCommon.isVoid(eleRefundEvent)) {
				isRefundProcessedForDeliveryID = true;
			}
		}

		if (duplicateEvent) {
			String msg = "This event = " + eventType + " for return delivery= " + extnAmazonReturnDelivery
					+ " is already processed in OMS. Message is ignored , AmazonReturnOrderId = "
					+ extnAmazonReturnOrderId;

			logger.info("Method: processReturnDeliveryUpdates | EventType = " + eventType);
			logger.info(msg);
			prepareAndLogResponse(AmzLiterals.STR_SUCCESS, inDoc, msg);
		} else if (isRefundProcessedForDeliveryID) {
			saveAmazonEvent(env, inDoc, getSalesOrderLineListOp);
		} else {
			Element eleReturns = SCXmlUtil.getXpathElement(eleGetOrderDetails, AmzLiterals.XPATH_AMZ_RETURNS);
			Element eleDetails = AmzXMLUtil.getXpathElement(eleReturns,
					"//details[@id='" + extnAmazonReturnOrderId + "']");

			Document changeOrderStatusInput = SCXmlUtil.createDocument(AmzLiterals.E_ORDER_STATUS_CHANGE);
			changeOrderStatusInput.getDocumentElement().setAttribute(AmzLiterals.A_DOCUMENT_TYPE,
					AmzLiterals.STR_RO_DOCUMENT_TYPE);
			changeOrderStatusInput.getDocumentElement().setAttribute(AmzLiterals.A_ORDER_HEADER_KEY, strOrderHeaderKey);
			changeOrderStatusInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_TRANSACTION_ID,
					AmzLiterals.TRANS_AMZ_RO_DELIVERY_UPDATE);

			Element eleOrderLines = SCXmlUtil.createChild(changeOrderStatusInput.getDocumentElement(),
					AmzLiterals.E_ORDER_LINES);

			NodeList orderLineItemsList = AmzXMLUtil.getXpathNodes(eleDetails, "returnDeliveryDetails[@id='"
					+ extnAmazonReturnDelivery + "']/returnDeliveryFor/orderLineItemAmounts");

			Map<String, String> deliveryLineItemMap = new HashMap<>();
			for (int i = 0; i < orderLineItemsList.getLength(); i++) {
				Element eleOrderLine = SCXmlUtil.createChild(eleOrderLines, AmzLiterals.E_ORDER_LINE);

				Element eleOrderLineItem = (Element) orderLineItemsList.item(i);
				logger.debug("Method: processReturnDeliveryUpdates: eleOrderLineItem is: "
						+ AmzXMLUtil.getString(eleOrderLineItem));

				String strLineItemID = SCXmlUtil.getXpathAttribute(eleOrderLineItem, AmzLiterals.XPATH_AMZ_LINE_ID);
				String strQuantity = SCXmlUtil.getXpathAttribute(eleOrderLineItem, AmzLiterals.XPATH_AMZ_LINE_VALUE);

				deliveryLineItemMap.put(strLineItemID, strQuantity);

				String strOrderLineKey = AmzXMLUtil.getXpathAttribute(eleOrderListOp,
						"Order/OrderLines/OrderLine/Extn[@ExtnAmazonSoLineItemAlias='" + strLineItemID
								+ "' and  @ExtnAmazonReturnOrderId='" + extnAmazonReturnOrderId
								+ "']/../@OrderLineKey");
				logger.debug("strOrderLineKey is: " + strOrderLineKey);

				eleOrderLine.setAttribute(AmzLiterals.ATTR_QUANTITY, strQuantity);
				eleOrderLine.setAttribute(AmzLiterals.ATTR_BASE_DROP_STATUS, baseDropStatus);
				eleOrderLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, strOrderLineKey);

			}

			logger.debug("Method: processReturnDeliveryUpdates changeOrderStatus inDoc"
					+ SCXmlUtil.getString(changeOrderStatusInput));

			try {
				AmzCommonUtil.callAPI(env, changeOrderStatusInput, AmzLiterals.API_CHANGE_ORDER_STATUS, null);
			} catch (YFSException e) {
				prepareAndLogResponse(AmzLiterals.STR_ERROR, inDoc, e.getErrorDescription());
				throw AmzCommonUtil.createException(e);
			}

			// changeOrder to save events
			Document eventsDoc = null;
			if (!YFCCommon.isVoid(amzConnEventsDoc)) {
				eventsDoc = amzConnEventsDoc;
			} else {
				eventsDoc = SCXmlUtil.createDocument(AmzLiterals.E_EVENTS);
			}
			Element eleEvent = SCXmlUtil.createChild(eventsDoc.getDocumentElement(), AmzLiterals.E_EVENT);
			eleEvent.setAttribute(AmzLiterals.ATTR_EVENT_TYPE, eventType);
			eleEvent.setAttribute(AmzLiterals.ATTR_EVENT_ID, extnAmazonReturnDelivery);
			logger.debug("Changeorder amzConnEventsDoc: " + SCXmlUtil.getString(amzConnEventsDoc));
			logger.debug("Changeorder eventsDoc: " + SCXmlUtil.getString(eventsDoc));

			outDoc = updateAmzConnEvents(env, inDoc, outDoc, amzConnEventsListDoc, strOrderHeaderKey, eventsDoc);
			logger.debug("Method: processReturnDeliveryUpdates  deliveryLineItemMap is: " + deliveryLineItemMap);
			// Process Refund for return delivery updates Starts
			prepareInputForRefunds(env, inDoc, getOrderDetails, deliveryLineItemMap);
			// Process Refund for return delivery updates Ends
		}
		logger.endTimer("class: AmzProcessReturnUpdates | method: processReturnDeliveryUpdates -- Ends");
		return outDoc;
	}

	private boolean checkElgForStatusUpdate(Document getOrderListOp) throws XPathExpressionException {
		logger.beginTimer("class: AmzProcessReturnUpdates | method: checkElgForStatusUpdate -- Starts");

		boolean isElgToUpdateOrderStatus = true;

		NodeList nOrderLine = AmzXMLUtil.getXpathNodes(getOrderListOp.getDocumentElement(),
				"Order/OrderLines/OrderLine");
		for (int k = 0; k < nOrderLine.getLength(); k++) {
			Element eleOrdLine = (Element) nOrderLine.item(k);
			String strMinLineStatus = eleOrdLine.getAttribute(AmzLiterals.A_MIN_LINE_STATUS);
			logger.debug("strMinLineStatus is: " + strMinLineStatus);

			Element eleOrdLineExtn = AmzXMLUtil.getChildElement(eleOrdLine, AmzLiterals.E_EXTN);
			String strSoLineItemAlias = eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_AMAZON_SO_LINE_ITEM_ALIAS);
			logger.debug("strSoLineItemAlias is: " + strSoLineItemAlias);

			if (lSoLineItemAlias.contains(strSoLineItemAlias) && genricPropertiesMap
					.get(AmzCommonConstants.PROP_STATUS_RETURN_RECEIVED).contains(strMinLineStatus)) {
				isElgToUpdateOrderStatus = false;
			}

		}
		logger.endTimer("class: AmzProcessReturnUpdates | method: checkElgForStatusUpdate -- End");

		return isElgToUpdateOrderStatus;

	}

	private Document createReturnAndUpdateStatus(YFSEnvironment env, Document inDoc, Document getOrderDetails)
			throws RemoteException, YIFClientCreationException, XPathExpressionException {
		logger.beginTimer("class: AmzProcessReturnUpdates | method: createReturnAndUpdateStatus -- Starts");

		Document outDoc = null;

		Element eleInDoc = inDoc.getDocumentElement();

		String extnAmazonReturnOrderId = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_RETURN_ID);
		String eventType = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE);

		Element eleGetOrderDetails = getOrderDetails.getDocumentElement();

		Element eleSaleOrderLineListOp = getSalesOrderLineListOp.getDocumentElement();
		Element eleReturns = SCXmlUtil.getXpathElement(eleGetOrderDetails, AmzLiterals.XPATH_AMZ_RETURNS);
		Element eleDetails = AmzXMLUtil.getXpathElement(eleReturns, "//details[@id='" + extnAmazonReturnOrderId + "']");

		String baseDropStatus = "";
		switch (eventType) {
		case AmzLiterals.STR_RETURN_PACKAGE_IN_TRANSIT:
			baseDropStatus = genricPropertiesMap.get(AmzCommonConstants.PROP_DROPSTATUS_INTRANSIT);
			break;
		case AmzLiterals.STR_RETURN_PACKAGE_DELIVERED:
			baseDropStatus = genricPropertiesMap.get(AmzCommonConstants.PROP_DROPSTATUS_DELIVERED);
			break;
		case AmzLiterals.STR_RETURN_PACKAGE_DELIVERY_FAILED:
			baseDropStatus = genricPropertiesMap.get(AmzCommonConstants.PROP_DROPSTATUS_FAILED);
			break;
		case AmzLiterals.STR_RETURN_ITEM_GRADED:
			baseDropStatus = genricPropertiesMap.get(AmzCommonConstants.PROP_DROPSTATUS_ITEMGRADED);
			break;
		default:
			logger.debug("class: AmzProcessReturnUpdates | method: processReturnUpdates  | Non-Processed Event "
					+ eventType);
			break;
		}

		// Create return Order
		Document createOrderDoc = SCXmlUtil.createDocument(AmzLiterals.E_ORDER);
		Element eleCreateOrderDoc = createOrderDoc.getDocumentElement();
		eleCreateOrderDoc.setAttribute(AmzLiterals.A_DOCUMENT_TYPE, AmzLiterals.STR_RO_DOCUMENT_TYPE);
		eleCreateOrderDoc.setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,SCXmlUtil.getXpathAttribute(eleSaleOrderLineListOp, "OrderLine/Order/@EnterpriseCode"));
		Element eleOrderExtn = SCXmlUtil.createChild(eleCreateOrderDoc, AmzLiterals.E_EXTN);
		eleOrderExtn.setAttribute(AmzLiterals.A_EXTN_AMAZON_RETURN_ORDER_ID, extnAmazonReturnOrderId);
		

		//  Merchant Impl Change
		
		eleCreateOrderDoc.setAttribute(AmzLiterals.A_ENTRY_TYPE, AmzLiterals.STR_WEB_CHANNEL);
		eleOrderExtn.setAttribute(AmzLiterals.A_EXTN_OMS_MANAGED_ORDER, AmzLiterals.STR_VAL_Y);

		Element eleOrderLines = SCXmlUtil.createChild(eleCreateOrderDoc, AmzLiterals.E_ORDER_LINES);

		Element elePersonInfoBillTo = SCXmlUtil.getXpathElement(eleSaleOrderLineListOp,
				"OrderLine/Order/PersonInfoBillTo");

		AmzXMLUtil.importElement(eleCreateOrderDoc, elePersonInfoBillTo);

		NodeList orderLineItemsList = AmzXMLUtil.getXpathNodes(eleDetails, "returnFor/orderLineItemAmounts");
		for (int i = 0; i < orderLineItemsList.getLength(); i++) {
			Element eleOrderLine = SCXmlUtil.createChild(eleOrderLines, AmzLiterals.E_ORDER_LINE);
			Element eleDerivedFrom = SCXmlUtil.createChild(eleOrderLine, AmzLiterals.ATTR_DERIVED_FROM);
			Element eleOrderLineExtn = SCXmlUtil.createChild(eleOrderLine, AmzLiterals.E_EXTN);

			Element eleOrderLineItem = (Element) orderLineItemsList.item(i);

			String strLineItemID = SCXmlUtil.getXpathAttribute(eleOrderLineItem, AmzLiterals.XPATH_AMZ_LINE_ID);
			String strAmountValue = SCXmlUtil.getXpathAttribute(eleOrderLineItem, AmzLiterals.XPATH_AMZ_LINE_VALUE);

			String strOrderLineKey = AmzXMLUtil.getXpathAttribute(eleSaleOrderLineListOp,
					"OrderLine/Extn[@ExtnAmazonLineItemAlias='" + strLineItemID + "']/../@OrderLineKey");
			logger.debug("Method: createReturnAndUpdateStatus strOrderLineKey is: " + strOrderLineKey);

			String shipNode = AmzXMLUtil.getXpathAttribute(eleSaleOrderLineListOp,
					"OrderLine[@OrderLineKey='" + strOrderLineKey + "']/@ShipNode");
			logger.debug("Method: createReturnAndUpdateStatus shipNode is: " + shipNode);

			String strReturnableQty = AmzXMLUtil.getXpathAttribute(eleSaleOrderLineListOp,
					"OrderLine/Extn[@ExtnAmazonLineItemAlias='" + strLineItemID + "']/../@ReturnableQty");
			logger.debug("Method: createReturnAndUpdateStatus strReturnableQty is: " + strReturnableQty);

			String strReasonCode = AmzXMLUtil.getXpathAttribute(eleDetails,
					"returnLineItems/returnFor/orderLineItemAmounts/lineItem[@id='" + strLineItemID
							+ "']/../../../returnReason/@reasonCode");
			logger.debug("Method: createReturnAndUpdateStatus strReasonCode is: " + strReasonCode);

			if (strReturnableQty.equalsIgnoreCase(AmzLiterals.STR_ZERO)) {
				YFSException ex = new YFSException();
				ex.setErrorCode(AmzLiterals.STR_RETURN_ERROR_CODE);
				String itemID = AmzXMLUtil.getXpathAttribute(eleSaleOrderLineListOp,
						"OrderLine/Extn[@ExtnAmazonLineItemAlias='" + strLineItemID + "']/../ItemDetails/@ItemID");
				ex.setErrorDescription("ReturnableQty for item : " + itemID + " is " + strReturnableQty);
				prepareAndLogResponse(AmzLiterals.STR_ERROR, inDoc, ex.getErrorDescription());
				throw ex;
			}

			eleOrderLine.setAttribute(AmzLiterals.A_ORDERED_QTY, strAmountValue);
			eleOrderLine.setAttribute(AmzLiterals.ATTR_RETURN_REASON, strReasonCode);
			eleOrderLine.setAttribute(genricPropertiesMap.get(AmzCommonConstants.PROP_RO_PIPELINE_CONDI_ATTR),
					genricPropertiesMap.get(AmzCommonConstants.PROP_RO_PIPELINE_CONDI_ATTR_VALUE));
			eleOrderLine.setAttribute(AmzLiterals.A_SHIP_NODE, shipNode);

			eleDerivedFrom.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, strOrderLineKey);

			eleOrderLineExtn.setAttribute(AmzLiterals.ATTR_EXTN_AMZ_SO_LINEITEM_ALIAS, strLineItemID);
			eleOrderLineExtn.setAttribute(AmzLiterals.ATTR_EXTN_AMZ_INIT_RETURN, "Y");
			eleOrderLineExtn.setAttribute(AmzLiterals.ATTR_EXTN_AMZ_RETURN_ORDERID, extnAmazonReturnOrderId);

		}

		logger.debug("Method: createReturnAndUpdateStatus createOrder inDoc" + SCXmlUtil.getString(createOrderDoc));

		Document createOrderTemp = SCXmlUtil.createFromString(AmzLiterals.TEMP_CREATE_ORDER);
		try {
			outDoc = AmzCommonUtil.callAPI(env, createOrderDoc, "createOrder", createOrderTemp);
		} catch (YFSException e) {
			prepareAndLogResponse(AmzLiterals.STR_ERROR, inDoc, e.getErrorDescription());
			throw AmzCommonUtil.createException(e);
		}
		logger.debug("Method: createReturnAndUpdateStatus createOrder outDoc" + SCXmlUtil.getString(outDoc));

		Document eventsDoc = SCXmlUtil.createDocument(AmzLiterals.E_EVENTS);
		Element eleEvent = SCXmlUtil.createChild(eventsDoc.getDocumentElement(), AmzLiterals.E_EVENT);
		eleEvent.setAttribute(AmzLiterals.ATTR_EVENT_TYPE, eventType);

		// Process change order status based on deliveryId/returnLineItem
		String strOrderHeaderKey = outDoc.getDocumentElement().getAttribute(AmzLiterals.A_ORDER_HEADER_KEY);
		Document changeOrderStatusInput = SCXmlUtil.createDocument(AmzLiterals.E_ORDER_STATUS_CHANGE);
		changeOrderStatusInput.getDocumentElement().setAttribute(AmzLiterals.A_ORDER_HEADER_KEY, strOrderHeaderKey);
		changeOrderStatusInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_TRANSACTION_ID,
				AmzLiterals.TRANS_AMZ_RO_DELIVERY_UPDATE);
		Element eleChangeStatusOrderLines = SCXmlUtil.createChild(changeOrderStatusInput.getDocumentElement(),
				AmzLiterals.E_ORDER_LINES);

		Document changeOrderStatusTemp = SCXmlUtil.createFromString(AmzLiterals.TEMP_CHANGE_ORDER_STATUS);
		Map<String, String> deliveryLineItemMap = new HashMap<>();
		if (eventType.equalsIgnoreCase(AmzLiterals.STR_RETURN_PACKAGE_IN_TRANSIT)
				|| eventType.equalsIgnoreCase(AmzLiterals.STR_RETURN_PACKAGE_DELIVERY_FAILED)
				|| eventType.equalsIgnoreCase(AmzLiterals.STR_RETURN_PACKAGE_DELIVERED)) {
			String extnAmazonReturnDelivery = inDoc.getDocumentElement()
					.getAttribute(AmzLiterals.ATTR_AMAZON_RETURN_DELIVERY);
			NodeList changeStatusOrderLineItemsList = AmzXMLUtil.getXpathNodes(eleDetails, "returnDeliveryDetails[@id='"
					+ extnAmazonReturnDelivery + "']/returnDeliveryFor/orderLineItemAmounts");
			for (int i = 0; i < changeStatusOrderLineItemsList.getLength(); i++) {
				Element eleOrderLine = SCXmlUtil.createChild(eleChangeStatusOrderLines, AmzLiterals.E_ORDER_LINE);

				Element eleOrderLineItem = (Element) changeStatusOrderLineItemsList.item(i);

				String strLineItemID = SCXmlUtil.getXpathAttribute(eleOrderLineItem, AmzLiterals.XPATH_AMZ_LINE_ID);
				String strQuantity = SCXmlUtil.getXpathAttribute(eleOrderLineItem, AmzLiterals.XPATH_AMZ_LINE_VALUE);
				deliveryLineItemMap.put(strLineItemID, strQuantity);
				String strOrderLineKey = AmzXMLUtil.getXpathAttribute(outDoc.getDocumentElement(),
						"OrderLines/OrderLine/Extn[@ExtnAmazonSoLineItemAlias='" + strLineItemID
								+ "' and  @ExtnAmazonReturnOrderId='" + extnAmazonReturnOrderId
								+ "']/../@OrderLineKey");

				eleOrderLine.setAttribute(AmzLiterals.ATTR_QUANTITY, strQuantity);
				eleOrderLine.setAttribute(AmzLiterals.ATTR_BASE_DROP_STATUS, baseDropStatus);
				eleOrderLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, strOrderLineKey);

				eleEvent.setAttribute(AmzLiterals.ATTR_EVENT_ID, extnAmazonReturnDelivery);

			}

		} else if (eventType.equalsIgnoreCase(AmzLiterals.STR_RETURN_ITEM_GRADED)) {

			String extnAmazonReturnLineItem = inDoc.getDocumentElement()
					.getAttribute(AmzLiterals.ATTR_AMAZON_RETURN_LINE_ITEM);
			Element eleReturnLineItem = AmzXMLUtil.getXpathElement(eleDetails,
					"returnLineItems[@id='" + extnAmazonReturnLineItem + "']");
			NodeList nOrderLineItemAmounts = AmzXMLUtil.getXpathNodes(eleReturnLineItem,
					"returnFor/orderLineItemAmounts");
			for (int i = 0; i < nOrderLineItemAmounts.getLength(); i++) {
				Element eleorderLineItemAmounts = (Element) nOrderLineItemAmounts.item(i);
				Element eleOrderLine = SCXmlUtil.createChild(eleChangeStatusOrderLines, AmzLiterals.E_ORDER_LINE);

				String strLineItemID = SCXmlUtil.getXpathAttribute(eleorderLineItemAmounts, "lineItem/@id");
				String strQuantity = SCXmlUtil.getXpathAttribute(eleorderLineItemAmounts, "amount/@value");
				String strOrderLineKey = AmzXMLUtil.getXpathAttribute(outDoc.getDocumentElement(),
						"OrderLines/OrderLine/Extn[@ExtnAmazonSoLineItemAlias='" + strLineItemID
								+ "' and  @ExtnAmazonReturnOrderId='" + extnAmazonReturnOrderId
								+ "']/../@OrderLineKey");

				eleOrderLine.setAttribute(AmzLiterals.ATTR_QUANTITY, strQuantity);
				eleOrderLine.setAttribute(AmzLiterals.ATTR_BASE_DROP_STATUS, baseDropStatus);
				eleOrderLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, strOrderLineKey);
			}
			eleEvent.setAttribute(AmzLiterals.ATTR_EVENT_ID, extnAmazonReturnLineItem);
		}
		logger.debug("Method: createReturnAndUpdateStatus changeOrderStatus inDoc"
				+ SCXmlUtil.getString(changeOrderStatusInput));

		try {
			AmzCommonUtil.callAPI(env, changeOrderStatusInput, AmzLiterals.API_CHANGE_ORDER_STATUS,
					changeOrderStatusTemp);
		} catch (YFSException e) {
			prepareAndLogResponse(AmzLiterals.STR_ERROR, inDoc, e.getErrorDescription());
			throw AmzCommonUtil.createException(e);
		}

		outDoc = updateAmzConnEvents(env, inDoc, outDoc, null, strOrderHeaderKey, eventsDoc);

		// Process Refund for create and update status Starts
		prepareInputForRefunds(env, inDoc, getOrderDetails, deliveryLineItemMap);
		// Process Refund for create and update status Ends

		logger.endTimer("class: AmzProcessReturnUpdates | method: createReturnAndUpdateStatus -- End");
		return outDoc;
	}

	private Document processReturnStartedUpdate(YFSEnvironment env, Document inDoc, Document getOrderDetails,
			Document getOrderListOp) throws XPathExpressionException, RemoteException, YIFClientCreationException {

		logger.beginTimer("class: AmzProcessReturnUpdates | method: processReturnStartedUpdate -- Starts");

		Document outDoc = null;

		Element eleInDoc = inDoc.getDocumentElement();
		String amazonOrderId = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID);
		String extnAmazonReturnOrderId = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_RETURN_ID);
		String eventType = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE);

		Element eleGetOrderDetails = getOrderDetails.getDocumentElement();

		Element eleGetSalesOrderLineListOp = getSalesOrderLineListOp.getDocumentElement();
		Element eleReturns = SCXmlUtil.getXpathElement(eleGetOrderDetails, AmzLiterals.XPATH_AMZ_RETURNS);
		Element eleDetails = AmzXMLUtil.getXpathElement(eleReturns, "//details[@id='" + extnAmazonReturnOrderId + "']");

		String strMinLineStatus = AmzXMLUtil.getXpathAttribute(getOrderListOp.getDocumentElement(),
				"OrderLine/Extn[@ExtnAmazonReturnOrderId='" + extnAmazonReturnOrderId + "']/../@MinLineStatus");

		if (YFCCommon.isVoid(strMinLineStatus)) {

			Document createOrderDoc = SCXmlUtil.createDocument(AmzLiterals.E_ORDER);
			Element eleCreateOrderDoc = createOrderDoc.getDocumentElement();
			eleCreateOrderDoc.setAttribute(AmzLiterals.A_DOCUMENT_TYPE, AmzLiterals.STR_RO_DOCUMENT_TYPE);
			eleCreateOrderDoc.setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,
					SCXmlUtil.getXpathAttribute(eleGetSalesOrderLineListOp, "OrderLine/Order/@EnterpriseCode"));
			Element eleOrderExtn = SCXmlUtil.createChild(eleCreateOrderDoc, AmzLiterals.E_EXTN);
			eleOrderExtn.setAttribute(AmzLiterals.A_EXTN_AMAZON_RETURN_ORDER_ID, extnAmazonReturnOrderId);

			// Merchant impl change		
			eleCreateOrderDoc.setAttribute(AmzLiterals.A_ENTRY_TYPE, AmzLiterals.STR_WEB_CHANNEL);
			eleOrderExtn.setAttribute(AmzLiterals.A_EXTN_OMS_MANAGED_ORDER, AmzLiterals.STR_VAL_Y);
			Element eleOrderLines = SCXmlUtil.createChild(eleCreateOrderDoc, AmzLiterals.E_ORDER_LINES);

			Element elePersonInfoBillTo = SCXmlUtil.getXpathElement(eleGetSalesOrderLineListOp,
					"OrderLine/Order/PersonInfoBillTo");

			AmzXMLUtil.importElement(eleCreateOrderDoc, elePersonInfoBillTo);

			NodeList orderLineItemsList = AmzXMLUtil.getXpathNodes(eleDetails, "returnFor/orderLineItemAmounts");
			for (int i = 0; i < orderLineItemsList.getLength(); i++) {
				Element eleOrderLine = SCXmlUtil.createChild(eleOrderLines, AmzLiterals.E_ORDER_LINE);
				Element eleDerivedFrom = SCXmlUtil.createChild(eleOrderLine, AmzLiterals.ATTR_DERIVED_FROM);
				Element eleOrderLineExtn = SCXmlUtil.createChild(eleOrderLine, AmzLiterals.E_EXTN);

				Element eleOrderLineItem = (Element) orderLineItemsList.item(i);

				String strLineItemID = SCXmlUtil.getXpathAttribute(eleOrderLineItem, AmzLiterals.XPATH_AMZ_LINE_ID);
				String strAmountValue = SCXmlUtil.getXpathAttribute(eleOrderLineItem, AmzLiterals.XPATH_AMZ_LINE_VALUE);

				String strOrderLineKey = AmzXMLUtil.getXpathAttribute(eleGetSalesOrderLineListOp,
						"OrderLine/Extn[@ExtnAmazonLineItemAlias='" + strLineItemID + "']/../@OrderLineKey");
				String shipNode = AmzXMLUtil.getXpathAttribute(eleGetSalesOrderLineListOp,
						"OrderLine[@OrderLineKey='" + strOrderLineKey + "']/@ShipNode");
				String strReturnableQty = AmzXMLUtil.getXpathAttribute(eleGetSalesOrderLineListOp,
						"OrderLine/Extn[@ExtnAmazonLineItemAlias='" + strLineItemID + "']/../@ReturnableQty");

				if (strReturnableQty.equalsIgnoreCase(AmzLiterals.STR_ZERO)) {
					YFSException ex = new YFSException();
					ex.setErrorCode(AmzLiterals.STR_RETURN_ERROR_CODE);
					String itemID = AmzXMLUtil.getXpathAttribute(eleGetSalesOrderLineListOp,
							"OrderLine/Extn[@ExtnAmazonLineItemAlias='" + strLineItemID + "']/../ItemDetails/@ItemID");
					ex.setErrorDescription("ReturnableQty for item : " + itemID + " is " + strReturnableQty);
					prepareAndLogResponse(AmzLiterals.STR_ERROR, inDoc, ex.getErrorDescription());
					throw ex;
				}

				String strReasonCode = AmzXMLUtil.getXpathAttribute(eleDetails,
						"returnLineItems/returnFor/orderLineItemAmounts/lineItem[@id='" + strLineItemID
								+ "']/../../../returnReason/@reasonCode");

				eleOrderLine.setAttribute(AmzLiterals.A_ORDERED_QTY, strAmountValue);
				eleOrderLine.setAttribute(AmzLiterals.ATTR_RETURN_REASON, strReasonCode);
				eleOrderLine.setAttribute(genricPropertiesMap.get(AmzCommonConstants.PROP_RO_PIPELINE_CONDI_ATTR),
						genricPropertiesMap.get(AmzCommonConstants.PROP_RO_PIPELINE_CONDI_ATTR_VALUE));
				eleOrderLine.setAttribute(AmzLiterals.A_SHIP_NODE, shipNode);

				eleDerivedFrom.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, strOrderLineKey);

				eleOrderLineExtn.setAttribute(AmzLiterals.ATTR_EXTN_AMZ_SO_LINEITEM_ALIAS, strLineItemID);
				eleOrderLineExtn.setAttribute(AmzLiterals.ATTR_EXTN_AMZ_INIT_RETURN, "Y");
				eleOrderLineExtn.setAttribute(AmzLiterals.ATTR_EXTN_AMZ_RETURN_ORDERID, extnAmazonReturnOrderId);

			}

			logger.debug(
					"Method: processReturnStartedUpdate createOrderDoc inDoc" + SCXmlUtil.getString(createOrderDoc));
			try {
				outDoc = AmzCommonUtil.callAPI(env, createOrderDoc, "createOrder", null);
			} catch (YFSException e) {
				prepareAndLogResponse(AmzLiterals.STR_ERROR, inDoc, e.getErrorDescription());
				throw AmzCommonUtil.createException(e);
			}
			logger.debug("Method: processReturnStartedUpdate createOrderDoc outDoc" + SCXmlUtil.getString(outDoc));

			// Process Refund for return started update Starts
			prepareInputForRefunds(env, inDoc, getOrderDetails, null);
			// Process Refund for return started update Ends

		} else {
			String msg = "Return is already in created for return id " + extnAmazonReturnOrderId
					+ " in OMS. Message is ignored , AmazonOrderId = " + amazonOrderId;

			logger.info("Method: processReturnStartedUpdate | EventType = " + eventType);
			logger.info(msg);
			prepareAndLogResponse(AmzLiterals.STR_SUCCESS, inDoc, msg);
		}
		logger.endTimer("class: AmzProcessReturnUpdates | method: processReturnStartedUpdate -- End");
		return outDoc;

	}

	private void prepareInputForRefunds(YFSEnvironment env, Document inDoc, Document getOrderDetails,
			Map deliveryLineItemMap)
			throws XPathExpressionException, YFSException, RemoteException, YIFClientCreationException {
		logger.beginTimer("class: AmzProcessReturnUpdates | method: prepareInputForRefunds -- Starts");

		Element eleInDoc = inDoc.getDocumentElement();
		String amazonOrderId = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID);
		String extnAmazonReturnOrderId = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_RETURN_ID);
		String eventType = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE);

		Element eleGetOrderDetails = getOrderDetails.getDocumentElement();

		if (eventType.equalsIgnoreCase(genricPropertiesMap.get(AmzCommonConstants.PROP_RETUND_EVENT))) {

			Document refundDoc = SCXmlUtil.createDocument("Refund");
			Element eleRefundDetails = SCXmlUtil.createChild(refundDoc.getDocumentElement(), "RefundDetails");

			if (eventType.equalsIgnoreCase(AmzLiterals.STR_RETURN_PACKAGE_IN_TRANSIT)
					|| eventType.equalsIgnoreCase(AmzLiterals.STR_RETURN_PACKAGE_DELIVERY_FAILED)
					|| eventType.equalsIgnoreCase(AmzLiterals.STR_RETURN_PACKAGE_DELIVERED)) {
				NodeList refundDetailsList = AmzXMLUtil.getXpathNodes(eleGetOrderDetails,
						"/Root/data/order/refunds/details/refundFor/causedBy[@id='" + extnAmazonReturnOrderId
								+ "']/../..");
				logger.debug(" refundDetailsList lenght is: " + refundDetailsList.getLength());
				Map<String, String> refundLineItemMap = new HashMap<>();
				for (int i = 0; i < refundDetailsList.getLength(); i++) {
					Element eleRefund = (Element) refundDetailsList.item(i);
					logger.debug(" eleRefund is: " + AmzXMLUtil.getString(eleRefund));
					NodeList eleOrderLineItemsList = AmzXMLUtil.getXpathNodes(eleRefund, "refundFor/orderLineItems");
					for (int j = 0; j < eleOrderLineItemsList.getLength(); j++) {
						Element eleOrderLineItem = (Element) eleOrderLineItemsList.item(j);
						String lineItemId = SCXmlUtil.getXpathAttribute(eleOrderLineItem,
								AmzLiterals.XPATH_AMZ_LINE_ID);
						String strQty = SCXmlUtil.getXpathAttribute(eleOrderLineItem, AmzLiterals.XPATH_AMZ_LINE_VALUE);
						refundLineItemMap.put(lineItemId, strQty);
					}
					logger.debug(" deliveryLineItemMap is: " + deliveryLineItemMap);
					logger.debug(" refundLineItemMap is: " + refundLineItemMap);
					for (Object key : deliveryLineItemMap.keySet()) {

						String skey = (String) key;
						logger.debug("skey is: " + skey);
						String svalue = (String) deliveryLineItemMap.get(key);
						logger.debug("svalue is: " + svalue);

						if (refundLineItemMap.containsKey(skey)
								&& svalue.equalsIgnoreCase(refundLineItemMap.get(skey))) {
							Element eleRefundDetail = SCXmlUtil.createChild(eleRefundDetails,
									AmzLiterals.STR_REFUND_DETAIL);
							eleRefundDetail.setAttribute(AmzLiterals.ATTR_REFUND_ID, eleRefund.getAttribute("id"));
							eleRefundDetail.setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, amazonOrderId);
							logger.debug("Method: prepareInputForRefunds eleRefundDetail is: "
									+ AmzXMLUtil.getString(eleRefundDetail));
						}
					}
//				
				}
			} else if (eventType.equalsIgnoreCase(AmzLiterals.STR_RETURN_ITEM_GRADED)) {
				String extnAmazonReturnLineItem = inDoc.getDocumentElement()
						.getAttribute(AmzLiterals.ATTR_AMAZON_RETURN_LINE_ITEM);
				Element eleReturns = SCXmlUtil.getXpathElement(eleGetOrderDetails, AmzLiterals.XPATH_AMZ_RETURNS);
				Element eleDetails = AmzXMLUtil.getXpathElement(eleReturns,
						"//details[@id='" + extnAmazonReturnOrderId + "']");
				Element eleReturnLineItem = AmzXMLUtil.getXpathElement(eleDetails,
						"returnLineItems[@id='" + extnAmazonReturnLineItem + "']");
				NodeList refundDetailsList = AmzXMLUtil.getXpathNodes(eleReturnLineItem, "refundDetails");

				for (int i = 0; i < refundDetailsList.getLength(); i++) {
					Element eleRefund = (Element) refundDetailsList.item(i);
					Element eleRefundDetail = SCXmlUtil.createChild(eleRefundDetails, AmzLiterals.STR_REFUND_DETAIL);
					eleRefundDetail.setAttribute(AmzLiterals.ATTR_REFUND_ID, eleRefund.getAttribute("id"));
					eleRefundDetail.setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, amazonOrderId);
				}
			} else if (eventType.equalsIgnoreCase(AmzLiterals.STR_RETURN_STARTED)
					|| eventType.equalsIgnoreCase(AmzLiterals.STR_RETURN_COMPLETED)) {
				NodeList refundDetailsList = AmzXMLUtil.getXpathNodes(eleGetOrderDetails,
						"/Root/data/order/refunds/details/refundFor/causedBy[@id='" + extnAmazonReturnOrderId
								+ "']/../..");
				for (int i = 0; i < refundDetailsList.getLength(); i++) {
					Element eleRefund = (Element) refundDetailsList.item(i);

					Element eleRefundDetail = SCXmlUtil.createChild(eleRefundDetails, AmzLiterals.STR_REFUND_DETAIL);
					eleRefundDetail.setAttribute(AmzLiterals.ATTR_REFUND_ID, eleRefund.getAttribute("id"));
					eleRefundDetail.setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, amazonOrderId);
				}
			}

			if (!eleRefundDetails.hasChildNodes()) {
				YFSException yfse = new YFSException();
				yfse.setErrorCode(AmzLiterals.STR_RETURN_ERROR_CODE);
				yfse.setErrorDescription("Refund is not available for return id: " + extnAmazonReturnOrderId);
				prepareAndLogResponse(AmzLiterals.STR_ERROR, inDoc, yfse.getErrorDescription());
				throw yfse;
			}

			postMessageToRefundQueue(env, inDoc, eleRefundDetails);
		}
		logger.endTimer("class: AmzProcessReturnUpdates | method: prepareInputForRefunds -- End");

	}

	private void postMessageToRefundQueue(YFSEnvironment env, Document inDoc, Element eleRefundDetails)
			throws YFSException, RemoteException, YIFClientCreationException {
		logger.beginTimer("class: AmzProcessReturnUpdates | method: postMessageToRefundQueue -- Starts");

		Element eleInputDoc = inDoc.getDocumentElement();

		Document refundDoc = SCXmlUtil.createDocument("Refund");
		Element eleRefund = refundDoc.getDocumentElement();
		eleRefund.setAttribute(AmzLiterals.ATTR_BUSINESS_PRODUCT_ID,
				eleInputDoc.getAttribute(AmzLiterals.ATTR_BUSINESS_PRODUCT_ID));
		eleRefund.setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE,
				eleInputDoc.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE));
		eleRefund.setAttribute(AmzLiterals.ATTR_AMZ_EVENT_ID, eleInputDoc.getAttribute(AmzLiterals.ATTR_AMZ_EVENT_ID));
		eleRefund.setAttribute(AmzLiterals.ATTR_EVENT_TIME, eleInputDoc.getAttribute(AmzLiterals.ATTR_EVENT_TIME));
		eleRefund.setAttribute("IdempotencyKey", eleInputDoc.getAttribute("IdempotencyKey"));
		eleRefund.setAttribute("SubscriptionId", eleInputDoc.getAttribute("SubscriptionId"));
		eleRefund.setAttribute(AmzLiterals.ATTR_RESOURCES, eleInputDoc.getAttribute(AmzLiterals.ATTR_RESOURCES));
		AmzXMLUtil.importElement(eleRefund, eleRefundDetails);

		logger.debug("Refund Document posted to queue" + SCXmlUtil.getString(refundDoc));
		AmzCommonUtil.callService(env, refundDoc, AmzLiterals.SERVICE_AMZCONN_POST_REFUND_EVENT_TO_Q, null);
		logger.endTimer("class: AmzProcessReturnUpdates | method: postMessageToRefundQueue -- End");

	}

	private void addSoLineItemAliasOfReturnToList(Document getOrderDetails, Document inDoc)
			throws XPathExpressionException {
		logger.beginTimer("class: AmzProcessReturnUpdates | method: addSoLineItemAliasOfReturnToList -- Starts");

		Element eleInDoc = inDoc.getDocumentElement();
		String extnAmazonReturnOrderId = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_RETURN_ID);
		String eventType = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE);
		if (eventType.equalsIgnoreCase(AmzLiterals.STR_RETURN_PACKAGE_IN_TRANSIT)
				|| eventType.equalsIgnoreCase(AmzLiterals.STR_RETURN_PACKAGE_DELIVERY_FAILED)
				|| eventType.equalsIgnoreCase(AmzLiterals.STR_RETURN_PACKAGE_DELIVERED)) {
			String strReturnDeliveryId = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_RETURN_DELIVERY);
			logger.debug("addSoLineItemAliasOfReturnToList strReturnDeliveryId is: " + strReturnDeliveryId);
			Element eleReturnDeliveryDetails = AmzXMLUtil.getXpathElement(getOrderDetails.getDocumentElement(),
					"data/order/returns/details[@id='" + extnAmazonReturnOrderId + "']/returnDeliveryDetails[@id='"
							+ strReturnDeliveryId + "']");
			NodeList norderLineItemAmounts = AmzXMLUtil.getXpathNodes(eleReturnDeliveryDetails,
					"returnDeliveryFor/orderLineItemAmounts");
			for (int k = 0; k < norderLineItemAmounts.getLength(); k++) {
				Element eleOrderLineItemAmounts = (Element) norderLineItemAmounts.item(k);
				String strLineItemId = AmzXMLUtil.getXpathAttribute(eleOrderLineItemAmounts, "lineItem/@id");
				logger.debug("addSoLineItemAliasOfReturnToList strLineItemId is: " + strLineItemId);

				lSoLineItemAlias.add(strLineItemId);
			}
		} else if (eventType.equalsIgnoreCase(AmzLiterals.STR_RETURN_ITEM_GRADED)) {
			String strReturnLineItemId = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_RETURN_LINE_ITEM);
			logger.debug("addSoLineItemAliasOfReturnToList eleReturnLineItems is: " + strReturnLineItemId);
			Element eleReturnLineItems = AmzXMLUtil.getXpathElement(getOrderDetails.getDocumentElement(),
					"data/order/returns/details[@id='" + extnAmazonReturnOrderId + "']/returnLineItems[@id='"
							+ strReturnLineItemId + "']");

			NodeList norderLineItemAmounts = AmzXMLUtil.getXpathNodes(eleReturnLineItems,
					"returnFor/orderLineItemAmounts");
			for (int k = 0; k < norderLineItemAmounts.getLength(); k++) {
				Element eleOrderLineItemAmounts = (Element) norderLineItemAmounts.item(k);
				String strLineItemId = AmzXMLUtil.getXpathAttribute(eleOrderLineItemAmounts, "lineItem/@id");
				logger.debug("addSoLineItemAliasOfReturnToList strLineItemId is: " + strLineItemId);
				lSoLineItemAlias.add(strLineItemId);
			}
		} else if (eventType.equalsIgnoreCase(AmzLiterals.STR_RETURN_COMPLETED)) {
			Element eleReturnDetails = AmzXMLUtil.getXpathElement(getOrderDetails.getDocumentElement(),
					"data/order/returns/details[@id='" + extnAmazonReturnOrderId + "']");
			NodeList nReturnLineItems = eleReturnDetails.getElementsByTagName("returnLineItems");
			for (int n = 0; n < nReturnLineItems.getLength(); n++) {
				Element eleReturnLineItems = (Element) nReturnLineItems.item(n);
				NodeList norderLineItemAmounts = AmzXMLUtil.getXpathNodes(eleReturnLineItems,
						"returnFor/orderLineItemAmounts");
				for (int k = 0; k < norderLineItemAmounts.getLength(); k++) {
					Element eleOrderLineItemAmounts = (Element) norderLineItemAmounts.item(k);
					String strLineItemId = AmzXMLUtil.getXpathAttribute(eleOrderLineItemAmounts, "lineItem/@id");
					logger.debug("addSoLineItemAliasOfReturnToList strLineItemId is: " + strLineItemId);
					lSoLineItemAlias.add(strLineItemId);
				}
			}
		}
		logger.endTimer("class: AmzProcessReturnUpdates | method: addSoLineItemAliasOfReturnToList -- End");

	}

	private void invokeSalesOrderGetOrderLineList(YFSEnvironment env, String amazonOrderId, Document inDoc) {
		logger.beginTimer("class: AmzProcessReturnUpdates | method: invokeSalesOrderGetOrderLineList -- Starts");

		Document getOrderLineListInput = SCXmlUtil.createDocument(AmzLiterals.E_ORDER_LINE);
		Element eleOrderInput = getOrderLineListInput.getDocumentElement();

		Element eleOrderLineExtn = SCXmlUtil.createChild(eleOrderInput, AmzLiterals.E_EXTN);
		eleOrderLineExtn.setAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID, amazonOrderId);
		logger.debug("getOrderListInput is: " + SCXmlUtil.getString(getOrderLineListInput));
		getSalesOrderLineListOp = AmzCommonUtil.invokeAPI(env,
				AmzCommonConstants.TEMP_GET_ORDER_LINE_LIST_AMZ_INIT_RETURN, AmzCommonConstants.API_GET_ORDER_LINE_LIST,
				getOrderLineListInput);
		logger.debug("getSalesOrderLineListOp is: " + SCXmlUtil.getString(getSalesOrderLineListOp));
		if (!getSalesOrderLineListOp.getDocumentElement().hasChildNodes()) {
			YFSException ex = new YFSException();
			ex.setErrorCode(AmzLiterals.STR_RETURN_ERROR_CODE);
			ex.setErrorDescription("OMS does not have order with amazon order id: " + amazonOrderId);
			prepareAndLogResponse(AmzLiterals.STR_ERROR, inDoc, ex.getErrorDescription());
			throw ex;
		}
		logger.endTimer("class: AmzProcessReturnUpdates | method: invokeSalesOrderGetOrderLineList -- End");

	}

	public void prepareAndLogResponse(String processStatus, Document inDoc, String message) {

		logger.beginTimer("class: AmzProcessReturnUpdates | method: prepareAndLogResponse -- Starts");

		String amazonOrderId = inDoc.getDocumentElement().getAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID);
		String extnAmazonReturnOrderId = inDoc.getDocumentElement().getAttribute(AmzLiterals.ATTR_AMAZON_RETURN_ID);
		String eventType = inDoc.getDocumentElement().getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE);

		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE, eventType);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_ACTION, "OMS-PROCESS");
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, amazonOrderId);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_RETURN_ID, extnAmazonReturnOrderId);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_PROCESS_STATUS, processStatus);

		if (!YFCObject.isVoid(getSalesOrderLineListOp)) {
			Element eleOrderLine = SCXmlUtil.getChildElement(getSalesOrderLineListOp.getDocumentElement(),
					AmzLiterals.E_ORDER_LINE);
			if (!YFCObject.isVoid(eleOrderLine)) {
				Element eleOrder = SCXmlUtil.getChildElement(eleOrderLine, AmzLiterals.E_ORDER);
				String orderNo = eleOrder.getAttribute(AmzLiterals.A_ORDER_NO);
				String enterpriseCode = eleOrder.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
				logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO, orderNo);
				logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE, enterpriseCode);
			}
		}

		if (!YFCObject.isVoid(message)) {
			if (processStatus.equalsIgnoreCase(AmzLiterals.STR_SUCCESS)) {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_MESSAGE, message);
			} else {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_ERROR_MSG, message);
			}
		}
		logger.debug("logInput is: " + AmzXMLUtil.getString(logInput));
		AmzCommonUtil.logAmzConnResponse(logInput);

		logger.endTimer("class: AmzProcessReturnUpdates | method: prepareAndLogResponse -- Ends");
	}

	/**
	 * Get AmzConnEvents Clob Object
	 * 
	 * @param eleOrderListOp
	 * @return
	 * @throws XPathExpressionException
	 */
	private Document getAmzConnEvents(YFSEnvironment env, String strOrderHeaderKey)
			throws XPathExpressionException, YIFClientCreationException, RemoteException {
		Document outListDoc = null;
		Document getAmzConnEventsInput = SCXmlUtil.createDocument(AmzLiterals.E_AMZCONN_EVENTS);
		getAmzConnEventsInput.getDocumentElement().setAttribute(AmzLiterals.A_ORDER_HEADER_KEY, strOrderHeaderKey);
		String strAmzConnEvents = null;
		try {
			outListDoc = AmzCommonUtil.callService(env, getAmzConnEventsInput,
					AmzLiterals.SERVICE_GET_AMZCONN_EVENTS_LIST, null);
		} catch (YFSException e) {
			prepareAndLogResponse(AmzLiterals.STR_ERROR, getAmzConnEventsInput, e.getErrorDescription());
			throw AmzCommonUtil.createException(e);
		}
		return outListDoc;
	}

	private Document updateAmzConnEvents(YFSEnvironment env, Document inDoc, Document outDoc, Document amzConnEventsListDoc, String strOrderHeaderKey,
			Document eventsDoc) throws YIFClientCreationException, RemoteException, XPathExpressionException {
		String strEvents = SCXmlUtil.getString(eventsDoc);
		Document changeAmzConnEventsInput = SCXmlUtil.createDocument(AmzLiterals.E_AMZCONN_EVENTS);
		changeAmzConnEventsInput.getDocumentElement().setAttribute(AmzLiterals.A_ORDER_HEADER_KEY, strOrderHeaderKey);
		changeAmzConnEventsInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMZ_CONN_EVENTS, strEvents);
		String strAmzConnEventsKey = null;
		if (amzConnEventsListDoc != null && !YFCObject.isVoid(
				AmzXMLUtil.getXpathAttribute(amzConnEventsListDoc.getDocumentElement(), "AmzConnEvents/@AmzConnEventsKey"))) {
			strAmzConnEventsKey = AmzXMLUtil.getXpathAttribute(amzConnEventsListDoc.getDocumentElement(),
					"AmzConnEvents/@AmzConnEventsKey");
		}
		
		try {
			if(!YFCObject.isVoid(strAmzConnEventsKey)) {
				changeAmzConnEventsInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMZ_CONN_EVENTS_KEY, strAmzConnEventsKey);
				outDoc = AmzCommonUtil.callService(env, changeAmzConnEventsInput,
						AmzLiterals.SERVICE_UPDATE_AMZCONN_EVENTS_LIST , null);
			}else {
				outDoc = AmzCommonUtil.callService(env, changeAmzConnEventsInput,
						AmzLiterals.SERVICE_CREATE_AMZCONN_EVENTS_LIST, null);
			}
			
		} catch (YFSException e) {
			prepareAndLogResponse(AmzLiterals.STR_ERROR, inDoc, e.getErrorDescription());
			throw AmzCommonUtil.createException(e);
		}
		return outDoc;
	}
}
