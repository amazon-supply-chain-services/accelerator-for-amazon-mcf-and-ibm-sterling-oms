package com.amazon.oms.delivery.api;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
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
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/**
 * This class processes MCF ORDER_STATUS_CHANGED notifications when
 * the fulfillment order status is COMPLETE, COMPLETE_PARTIAL, or UNFULFILLABLE
 * and one or more line items have cancelled or unfulfillable quantities.
 *
 * For each line item with cancelledValue > 0 or unfulfillableValue > 0, the class
 * looks up the corresponding OMS order line via lineItemId (ExtnAmazonLineItemAlias),
 * then invokes changeOrder to cancel the quantity and/or changeRelease to cancel
 * released quantity.
 *
 * This class does NOT call the Amazon API — all information is in the event XML.
 * This class does NOT check for shipment containers — this is an order-level
 * cancellation, not a delivery-level event.
 *
 * Input Sample 1 - Cancelled (FulfillmentOrder status=COMPLETE):
 * <AmzProcessMCFOrderStatusChangedEvent eventType="ORDER_STATUS_CHANGED"
 *     fulfillentServiceId="FS01-o8vlfw8b1tiu1" merchantId="merchantId">
 *   <order orderId="Y100003115-01" status="COMPLETE">
 *     <lineItems lineItemId="202603151921549163264">
 *       <product>
 *         <perUnitDeclaredValue amount="14.99" currencyCode="USD"/>
 *         <productIdentifier amazonSku="Deep_bwp_MCF_6"/>
 *       </product>
 *       <amount unit="EACHES" value="2.0"/>
 *       <unfulfillableAmount unit="EACHES" value="0"/>
 *       <cancelledAmount unit="EACHES" value="2.0"/>
 *     </lineItems>
 *   </order>
 * </AmzProcessMCFOrderStatusChangedEvent>
 *
 * Input Sample 2 - Partially Fulfilled (FulfillmentOrder status=COMPLETE_PARTIAL):
 * <AmzProcessMCFOrderStatusChangedEvent eventType="ORDER_STATUS_CHANGED"
 *     fulfillentServiceId="FS01-o8vlfw8b1tiu1" merchantId="merchantId">
 *   <order orderId="Y100003115-01" status="COMPLETE_PARTIAL">
 *     <lineItems lineItemId="202603151921549163264">
 *       <product>
 *         <perUnitDeclaredValue amount="14.99" currencyCode="USD"/>
 *         <productIdentifier amazonSku="Deep_bwp_MCF_6"/>
 *       </product>
 *       <amount unit="EACHES" value="2.0"/>
 *       <unfulfillableAmount unit="EACHES" value="1.0"/>
 *       <cancelledAmount unit="EACHES" value="0"/>
 *     </lineItems>
 *   </order>
 * </AmzProcessMCFOrderStatusChangedEvent>
 *
 * Input Sample 3 - Unfulfillable (FulfillmentOrder status=UNFULFILLABLE):
 * <AmzProcessMCFOrderStatusChangedEvent eventType="ORDER_STATUS_CHANGED"
 *     fulfillentServiceId="FS01-o8vlfw8b1tiu1" merchantId="merchantId">
 *   <order orderId="Y100003115-01" status="UNFULFILLABLE">
 *     <lineItems lineItemId="202603151921549163264">
 *       <product>
 *         <perUnitDeclaredValue amount="14.99" currencyCode="USD"/>
 *         <productIdentifier amazonSku="Deep_bwp_MCF_6"/>
 *       </product>
 *       <amount unit="EACHES" value="2.0"/>
 *       <unfulfillableAmount unit="EACHES" value="2.0"/>
 *       <cancelledAmount unit="EACHES" value="0"/>
 *     </lineItems>
 *   </order>
 * </AmzProcessMCFOrderStatusChangedEvent>
 *
 * Mapping:
 *   merchantId        -> used to resolve EnterpriseCode
 *   lineItemId        -> ExtnAmazonLineItemAlias (order line lookup)
 *   cancelledAmount/@value + unfulfillableAmount/@value -> quantity to cancel in OMS
 */
public class AmzProcessMCFOrderStatusChangeEvent implements YIFCustomApi {

	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzProcessMCFOrderStatusChangeEvent.class);
	private static final String ERR_PREFIX = "MCF_ORDER_STATUS_ERR_";
	private Properties props;

	String strShipNode = null;
	Map<String, String> genericPropertiesMap = new HashMap<>();

	public Document processOrderStatusChangeEvent(YFSEnvironment env, Document inDoc) throws Exception {
		logger.beginTimer("class: AmzProcessMCFOrderStatusChangeEvent | method: processOrderStatusChangeEvent -- Starts");
		AmzCommonUtil.logAmzConnRequest(inDoc);
		Document returnOutDoc = null;
		try {
			Element eleEvent = inDoc.getDocumentElement();
			String merchantId = eleEvent.getAttribute("merchantId");
			String eventType = eleEvent.getAttribute("eventType");

			Element eleFulfillmentOrder = SCXmlUtil.getChildElement(eleEvent, "order");
			String amazonOrderId = eleFulfillmentOrder.getAttribute("orderId");
			String orderStatus = eleFulfillmentOrder.getAttribute("status");


			String strEnterpriseCode = AmzEnterpriseBPIDXRefUtil.getEnterpriseCode(env, merchantId);

			// Collect line items that have cancelled or unfulfillable quantities
			NodeList lineItemNodes = eleFulfillmentOrder.getElementsByTagName("lineItems");
			if (lineItemNodes.getLength() == 0) {
				YFSException yfse = new YFSException();
				yfse.setErrorCode(ERR_PREFIX + "NO_LINE_ITEMS");
				yfse.setErrorDescription("No lineItems found in the ORDER_STATUS_CHANGED event");
				prepareAndLogResponse(AmzLiterals.STR_ERROR, null, inDoc, yfse.getErrorDescription());
				throw yfse;
			}

			ArrayList<Element> cancelLineItems = new ArrayList<>();
			for (int i = 0; i < lineItemNodes.getLength(); i++) {
				Element eleLineItem = (Element) lineItemNodes.item(i);
				double cancelledValue = getChildElementValue(eleLineItem, "cancelledAmount");
				double unfulfillableValue = getChildElementValue(eleLineItem, "unfulfillableAmount");
				if ((cancelledValue + unfulfillableValue) > 0) {
					cancelLineItems.add(eleLineItem);
				}
			}

			if (cancelLineItems.isEmpty()) {
				String msg = "No cancelled or unfulfillable line items found for orderId " + amazonOrderId
						+ " with status " + orderStatus + ". Message is ignored.";
				logger.info(msg);
				prepareAndLogResponse(AmzLiterals.STR_SUCCESS, null, inDoc, msg);
				return inDoc;
			}

			// Use the first line item to look up OMS order line and resolve ship node
			String firstLineItemId = cancelLineItems.get(0).getAttribute("lineItemId");
			Document getOrderLineListOutDoc = callGetOrderLineList(env, firstLineItemId, inDoc);

			Element eleGetOrderLineListOp = getOrderLineListOutDoc.getDocumentElement();
			String extnOrderCountry = SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_EXTN_COUNTRY_CODE);
			String enterpriseCode = SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_ENTERPRISE_CODE);

			Document propertyDoc = SCXmlUtil.createDocument(AmzLiterals.E_PROPERTIES);
			propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, enterpriseCode);
			genericPropertiesMap = AmzGetGenericProperty.getGenericProperties(env, propertyDoc);
			strShipNode = genericPropertiesMap.get(AmzCommonConstants.PROP_AMZ_SHIP_NODE + extnOrderCountry);
			logger.debug("shipNode: " + strShipNode);

			// Validate single release for ship node
			NodeList nlOrderStatusesList = AmzXMLUtil.getXpathNodes(eleGetOrderLineListOp,
					"/OrderLineList/OrderLine/OrderStatuses/OrderStatus[@ShipNode='" + strShipNode + "']");
			ArrayList<String> orderLineKeyList = new ArrayList<>();
			for (int k = 0; k < nlOrderStatusesList.getLength(); k++) {
				Element eleOrderStatus = (Element) nlOrderStatusesList.item(k);
				String orderLineKey = eleOrderStatus.getAttribute(AmzLiterals.A_ORDER_LINE_KEY);
				if (!orderLineKeyList.contains(orderLineKey)) {
					orderLineKeyList.add(orderLineKey);
				}
			}
			if (!YFCObject.isVoid(orderLineKeyList) && orderLineKeyList.size() > 1) {
				YFSException yfse = new YFSException();
				String message = "Order has multi releases with same Ship Node " + strShipNode + ". OrderLineKeys are:";
				for (int i = 0; i < orderLineKeyList.size(); i++) {
					message = message.concat(orderLineKeyList.get(i) + " ");
				}
				yfse.setErrorCode(ERR_PREFIX + "MULTI_RELEASE");
				yfse.setErrorDescription(message);
				prepareAndLogResponse(AmzLiterals.STR_ERROR, null, inDoc, yfse.getErrorDescription());
				throw yfse;
			}

			// Prepare changeOrder and changeRelease documents
			String strOrderReleaseKey = SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp,
					"/OrderLineList/OrderLine/OrderStatuses/OrderStatus[@ShipNode='" + strShipNode + "']/@OrderReleaseKey");

			Document changeOrderInDoc = prepareChangeOrderInDoc(eleGetOrderLineListOp);
			Document changeReleaseInDoc = prepareChangeReleaseInDoc(eleGetOrderLineListOp, strOrderReleaseKey);

			Element eleChangeOrderLines = (Element) changeOrderInDoc.getDocumentElement()
					.getElementsByTagName(AmzLiterals.E_ORDER_LINES).item(0);
			Element eleChangeReleaseOrderLines = (Element) changeReleaseInDoc.getDocumentElement()
					.getElementsByTagName(AmzLiterals.E_ORDER_LINES).item(0);

			// Process each cancelled/unfulfillable line item
			for (Element eleLineItem : cancelLineItems) {
				String lineItemId = eleLineItem.getAttribute("lineItemId");
				double cancelledValue = getChildElementValue(eleLineItem, "cancelledAmount");
				double unfulfillableValue = getChildElementValue(eleLineItem, "unfulfillableAmount");
				double totalCancelQty = cancelledValue + unfulfillableValue;

				// Look up OMS order line by ExtnAmazonLineItemAlias
				Element eleGetOrderLineDetail = AmzXMLUtil.getXpathElement(eleGetOrderLineListOp,
						"/OrderLineList/OrderLine/Order/OrderLines/OrderLine/Extn[@ExtnAmazonLineItemAlias='"
								+ lineItemId + "']/..");

				if (YFCObject.isVoid(eleGetOrderLineDetail)) {
					YFSException yfse = new YFSException();
					yfse.setErrorCode(ERR_PREFIX + "OL_NOT_FOUND");
					yfse.setErrorDescription("Could not find OMS OrderLine for lineItemId " + lineItemId);
					prepareAndLogResponse(AmzLiterals.STR_ERROR, null, inDoc, yfse.getErrorDescription());
					throw yfse;
				}

				String strOrderLineKey = eleGetOrderLineDetail.getAttribute(AmzLiterals.A_ORDER_LINE_KEY);
				String strOrderLineQty = eleGetOrderLineDetail.getAttribute(AmzLiterals.A_ORDERED_QTY);
				String strMaxLineStatus = eleGetOrderLineDetail.getAttribute("MaxLineStatus");
				String strPrimeLineNo = eleGetOrderLineDetail.getAttribute(AmzLiterals.A_PRIME_LINE_NO);
				Element eleItem = SCXmlUtil.getChildElement(eleGetOrderLineDetail, "Item");
				String strItemId = eleItem.getAttribute(AmzLiterals.A_ITEM_ID);

				// If line is in Released status (3200), use changeRelease to cancel released qty
				if ("3200".equalsIgnoreCase(strMaxLineStatus)) {
					Element eleChangeReleaseLine = SCXmlUtil.createChild(eleChangeReleaseOrderLines, AmzLiterals.E_ORDER_LINE);
					eleChangeReleaseLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, strOrderLineKey);
					eleChangeReleaseLine.setAttribute(AmzLiterals.A_ACTION, AmzLiterals.STR_CANCEL);
					eleChangeReleaseLine.setAttribute(AmzLiterals.A_CHANGE_IN_QTY,
							String.format("%.2f", -Math.abs(totalCancelQty)));
					eleChangeReleaseLine.setAttribute(AmzLiterals.A_SHIP_NODE, strShipNode);
				} else {
					// Use changeOrder to cancel/reduce qty
					Element eleOrderLine = SCXmlUtil.createChild(eleChangeOrderLines, AmzLiterals.E_ORDER_LINE);
					processOrderLine(changeOrderInDoc, eleOrderLine, strOrderLineKey,
							totalCancelQty, Double.parseDouble(strOrderLineQty),
							orderStatus, amazonOrderId, strPrimeLineNo, strItemId);
				}
			}

			// Invoke changeOrder if it has order lines
			Document changeOrderOutDoc = null;
			Document changeReleaseOutDoc = null;

			if (hasOrderLines(changeOrderInDoc)) {
				logger.debug("changeOrderInDoc: " + SCXmlUtil.getString(changeOrderInDoc));
				changeOrderOutDoc = callChangeOrderAPI(env, changeOrderInDoc);
				returnOutDoc = changeOrderOutDoc;
				logger.debug("changeOrderOutDoc: " + SCXmlUtil.getString(changeOrderOutDoc));
			}

			// Invoke changeRelease if it has order lines
			if (hasOrderLines(changeReleaseInDoc)) {
				logger.debug("changeReleaseInDoc: " + SCXmlUtil.getString(changeReleaseInDoc));
				changeReleaseOutDoc = callChangeReleaseAPI(env, changeReleaseInDoc);
				returnOutDoc = changeReleaseOutDoc;
				logger.debug("changeReleaseOutDoc: " + SCXmlUtil.getString(changeReleaseOutDoc));
			}

			if (returnOutDoc != null && returnOutDoc.getDocumentElement() != null
					&& !"Errors".equalsIgnoreCase(returnOutDoc.getDocumentElement().getNodeName())) {
				prepareAndLogResponse(AmzLiterals.STR_SUCCESS, returnOutDoc, inDoc, null);
			} else {
				String errorDescription = (returnOutDoc != null && returnOutDoc.getDocumentElement() != null)
						? returnOutDoc.getDocumentElement().getElementsByTagName("Error").item(0)
								.getAttributes().getNamedItem("ErrorDescription").getNodeValue()
						: null;
				prepareAndLogResponse(AmzLiterals.STR_ERROR, returnOutDoc, inDoc, errorDescription);
			}

		} catch (YFSException e) {
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			YFSException yfse = new YFSException();
			yfse.setErrorCode(ERR_PREFIX + "EXCEPTION");
			yfse.setErrorDescription(e.getMessage());
			prepareAndLogResponse(AmzLiterals.STR_ERROR, returnOutDoc, inDoc, e.getMessage());
			throw yfse;
		}
		logger.endTimer("class: AmzProcessMCFOrderStatusChangeEvent | method: processOrderStatusChangeEvent -- Ends");
		return !YFCObject.isVoid(returnOutDoc) ? returnOutDoc : inDoc;
	}

	private void validateInput(String merchantId, String amazonOrderId, Document inDoc) throws YFSException {
		if (YFCObject.isVoid(merchantId)) {
			YFSException yfse = new YFSException();
			yfse.setErrorCode(ERR_PREFIX + "MERCHANT_ID_MISSING");
			yfse.setErrorDescription("merchantId is blank or empty in the event");
			prepareAndLogResponse(AmzLiterals.STR_ERROR, null, inDoc, yfse.getErrorDescription());
			throw yfse;
		}
		if (YFCObject.isVoid(amazonOrderId)) {
			YFSException yfse = new YFSException();
			yfse.setErrorCode(ERR_PREFIX + "ORDER_ID_MISSING");
			yfse.setErrorDescription("FulfillmentOrder orderId is blank or empty in the event");
			prepareAndLogResponse(AmzLiterals.STR_ERROR, null, inDoc, yfse.getErrorDescription());
			throw yfse;
		}
	}

	/**
	 * Calls getOrderLineList API using lineItemId = ExtnAmazonLineItemAlias.
	 */
	private Document callGetOrderLineList(YFSEnvironment env, String lineItemId, Document inDoc) throws Exception {
		if (YFCObject.isVoid(lineItemId)) {
			YFSException yfse = new YFSException();
			yfse.setErrorCode(ERR_PREFIX + "ALIAS_ITEM_MISSING");
			yfse.setErrorDescription("lineItemId (ExtnAmazonLineItemAlias) is empty in the event");
			prepareAndLogResponse(AmzLiterals.STR_ERROR, null, inDoc, yfse.getErrorDescription());
			throw yfse;
		}

		Document getOrderLineListInDoc = SCXmlUtil.createDocument(AmzLiterals.E_ORDER_LINE);
		Element eleOrderLineExtn = SCXmlUtil.createChild(getOrderLineListInDoc.getDocumentElement(), AmzLiterals.E_EXTN);
		eleOrderLineExtn.setAttribute(AmzLiterals.A_EXTN_AMZ_LINE_ITEM_ALIAS, lineItemId);

		Document getOrderLineListTemp = SCXmlUtil.createFromString(AmzLiterals.TEMP_ORDER_LINE_LIST);
		Document getOrderLineListOutDoc = AmzCommonUtil.callAPI(env, getOrderLineListInDoc,
				AmzCommonConstants.API_GET_ORDER_LINE_LIST, getOrderLineListTemp);
		logger.debug("getOrderLineListOutDoc: " + SCXmlUtil.getString(getOrderLineListOutDoc));

		NodeList nlOrderLineList = getOrderLineListOutDoc.getDocumentElement().getElementsByTagName("OrderLine");
		if (nlOrderLineList.getLength() == 0) {
			YFSException yfse = new YFSException();
			yfse.setErrorCode(ERR_PREFIX + "OL_LIST_EMPTY");
			yfse.setErrorDescription("Unable to get OrderLineList for ExtnAmazonLineItemAlias " + lineItemId);
			prepareAndLogResponse(AmzLiterals.STR_ERROR, null, inDoc, yfse.getErrorDescription());
			throw yfse;
		}
		return getOrderLineListOutDoc;
	}

	private Document prepareChangeOrderInDoc(Element eleGetOrderLineListOp) {
		Document changeOrderInDoc = SCXmlUtil.createDocument(AmzLiterals.E_ORDER);
		Element eleChangeOrderInDoc = changeOrderInDoc.getDocumentElement();
		eleChangeOrderInDoc.setAttribute(AmzLiterals.A_OVERRIDE, AmzLiterals.STR_VAL_Y);
		eleChangeOrderInDoc.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY,
				SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_SALES_ORDER_HEADER_KEY));
		eleChangeOrderInDoc.setAttribute(AmzLiterals.A_ORDER_NO,
				SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_SALES_ORDER_NO));
		eleChangeOrderInDoc.setAttribute(AmzLiterals.ATTR_DOCUMENT_TYPE,
				SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_DOCUEMENT_TYPE));
		eleChangeOrderInDoc.setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,
				SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_ENTERPRISE_CODE));
		eleChangeOrderInDoc.setAttribute(AmzLiterals.A_SELECT_METHOD, AmzCommonConstants.STR_WAIT);
		SCXmlUtil.createChild(eleChangeOrderInDoc, AmzLiterals.E_ORDER_LINES);
		return changeOrderInDoc;
	}

	private Document prepareChangeReleaseInDoc(Element eleGetOrderLineListOp, String strOrderReleaseKey) {
		Document changeReleaseInDoc = SCXmlUtil.createDocument(AmzLiterals.E_ORDER_RELEASE);
		Element eleChangeReleaseInDoc = changeReleaseInDoc.getDocumentElement();
		eleChangeReleaseInDoc.setAttribute(AmzLiterals.A_OVERRIDE, AmzLiterals.STR_VAL_Y);
		eleChangeReleaseInDoc.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY,
				SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_SALES_ORDER_HEADER_KEY));
		eleChangeReleaseInDoc.setAttribute(AmzLiterals.A_ORDER_RELEASE_KEY, strOrderReleaseKey);
		eleChangeReleaseInDoc.setAttribute(AmzLiterals.A_SELECT_METHOD, AmzCommonConstants.STR_WAIT);
		SCXmlUtil.createChild(eleChangeReleaseInDoc, AmzLiterals.E_ORDER_LINES);
		return changeReleaseInDoc;
	}

	/**
	 * Processes an order line by adjusting its quantity based on the cancel amount.
	 * If the entire quantity is cancelled, sets Action=CANCEL.
	 * If partially cancelled, reduces OrderedQty.
	 */
	private void processOrderLine(Document changeOrderInDoc, Element eleOrderLine,
			String strOrderLineKey, double cancelQty, double orderLineQty,
			String orderStatus, String amazonOrderId, String strPrimeLineNo, String itemId) {

		eleOrderLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, strOrderLineKey);

		double remainingQty = orderLineQty - cancelQty;
		String cancelType;

		if (remainingQty <= 0) {
			eleOrderLine.setAttribute(AmzLiterals.A_ACTION, AmzLiterals.STR_CANCEL);
			cancelType = "FULL";
		} else {
			eleOrderLine.setAttribute(AmzLiterals.A_ORDERED_QTY, Double.toString(remainingQty));
			cancelType = "PARTIAL";
		}

		addCancellationNotes(changeOrderInDoc, eleOrderLine, orderStatus, amazonOrderId,
				cancelQty, strPrimeLineNo, itemId, cancelType);
	}

	private void addCancellationNotes(Document changeOrderInDoc, Element eleOrderLine,
			String orderStatus, String amazonOrderId, double cancelQty,
			String strPrimeLineNo, String itemId, String cancelType) {

		Element eleNotes = changeOrderInDoc.createElement(AmzLiterals.E_NOTES);
		Element eleNote = changeOrderInDoc.createElement(AmzLiterals.E_NOTE);
		eleNote.setAttribute(AmzLiterals.A_REASON_CODE, orderStatus);

		String noteText;
		if ("PARTIAL".equalsIgnoreCase(cancelType)) {
			noteText = "Order Line with PrimeLine = " + strPrimeLineNo + " and ItemID= " + itemId
					+ " ,is partly CANCELLED for Qty " + cancelQty
					+ ", as part of processing ORDER_STATUS_CHANGED for orderId " + amazonOrderId
					+ " from Amazon MCF API with status " + orderStatus;
		} else {
			noteText = "Order Line with PrimeLine = " + strPrimeLineNo + " and ItemID= " + itemId
					+ " ,is fully CANCELLED for Qty " + cancelQty
					+ ", as part of processing ORDER_STATUS_CHANGED for orderId " + amazonOrderId
					+ " from Amazon MCF API with status " + orderStatus;
		}
		eleNote.setAttribute(AmzLiterals.A_NOTE_TEXT, noteText);
		eleNotes.appendChild(eleNote);
		eleOrderLine.appendChild(eleNotes);
	}

	private boolean hasOrderLines(Document orderInDoc) {
		return orderInDoc.getElementsByTagName(AmzLiterals.E_ORDER_LINE).getLength() > 0
				&& orderInDoc.getElementsByTagName(AmzLiterals.E_ORDER_LINE).item(0).hasAttributes();
	}

	private Document callChangeOrderAPI(YFSEnvironment env, Document changeOrderInDoc)
			throws YFSException, RemoteException, YIFClientCreationException {
		return AmzCommonUtil.invokeService(env, AmzCommonConstants.SERVICE_AMZ_CHANGE_ORDER_SERVICE, changeOrderInDoc);
	}

	private Document callChangeReleaseAPI(YFSEnvironment env, Document changeReleaseInDoc)
			throws YFSException, RemoteException, YIFClientCreationException {
		return AmzCommonUtil.invokeService(env, AmzCommonConstants.SERVICE_AMZ_CHANGE_RELEASE, changeReleaseInDoc);
	}

	public void prepareAndLogResponse(String processStatus, Document apiOutput, Document inDoc, String message) {
		logger.beginTimer("class: AmzProcessMCFOrderStatusChangeEvent | method: prepareAndLogResponse -- Starts");

		Element eleEvent = inDoc.getDocumentElement();
		String eventType = eleEvent.getAttribute("eventType");
		Element eleFulfillmentOrder = SCXmlUtil.getChildElement(eleEvent, "order");
		String amazonOrderId = !YFCObject.isVoid(eleFulfillmentOrder) ? eleFulfillmentOrder.getAttribute("orderId") : "";
		String orderStatus = !YFCObject.isVoid(eleFulfillmentOrder) ? eleFulfillmentOrder.getAttribute("status") : "";

		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE, eventType);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_ACTION, "OMS-PROCESS");
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, amazonOrderId);
		logInput.getDocumentElement().setAttribute("FulfillmentOrderStatus", orderStatus);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_PROCESS_STATUS, processStatus);

		if (!YFCObject.isVoid(apiOutput)) {
			logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO,
					apiOutput.getDocumentElement().getAttribute(AmzLiterals.A_ORDER_NO));
			logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,
					apiOutput.getDocumentElement().getAttribute(AmzLiterals.A_ENTERPRISE_CODE));
		}

		if (!YFCObject.isVoid(message)) {
			if (processStatus.equalsIgnoreCase(AmzLiterals.STR_SUCCESS)) {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_MESSAGE, message);
			} else {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_ERROR_MSG, message);
			}
		}
		AmzCommonUtil.logAmzConnResponse(logInput);
		logger.endTimer("class: AmzProcessMCFOrderStatusChangeEvent | method: prepareAndLogResponse -- Ends");
	}

	private double getChildElementValue(Element parent, String childTagName) {
		Element child = SCXmlUtil.getChildElement(parent, childTagName);
		if (YFCObject.isVoid(child)) {
			return 0.0;
		}
		String value = child.getAttribute("value");
		if (YFCObject.isVoid(value)) {
			return 0.0;
		}
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			return 0.0;
		}
	}

	@Override
	public void setProperties(Properties inProps) {
		this.props = inProps;
	}
}
