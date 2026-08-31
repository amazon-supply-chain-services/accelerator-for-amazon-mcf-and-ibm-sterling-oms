package com.amazon.oms.delivery.api;

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
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/**
 * This class processes MCF FULFILLMENT_ORDER_STATUS notifications for
 * SHIPMENT_STATUS_CHANGED events.
 * It is invoked through an async service which reads messages from a queue.
 *
 * This class does NOT call the Amazon API to get order details — all shipment,
 * tracking, and line item information is available directly in the event XML.
 *
 * Processing logic:
 *   - Container does NOT exist: confirm shipment (create container), log success
 *   - Container already exists: ignore and log
 *
 * Input (MCF Event XML — SHIPMENT_STATUS_CHANGED):
 * <AmzProcessShipmentStatusChangedEvent eventType="SHIPMENT_STATUS_CHANGED"
 *     fulfillentServiceId="FS01-o8vlfw8b1tiu1" merchantId="merchantId">
 *   <order orderId="Y100003115-01" status="PROCESSING">
 *     <shipments amazonShipmentId="09581f64ed04398cb1953e2ca550809b0" status="SHIPPED">
 *       <amazonFacility facilityId="FC456"/>
 *       <items lineItemId="202603151921549163264" packageId="13153368">
 *         <amount unit="EACHES" value="1.0"/>
 *         <productIdentifier amazonSku="Deep_bwp_MCF_6"/>
 *       </items>
 *       <packages packageId="13153368" status="PROCESSING">
 *         <tracking>
 *           <carrier carrierCode="FEDEX" trackingNumber="123456789"/>
 *           <amazon trackingNumber="234567890"/>
 *         </tracking>
 *       </packages>
 *     </shipments>
 *   </order>
 * </AmzProcessShipmentStatusChangedEvent>
 *
 * Mapping:
 *   amazonShipmentId  -> ExtnAmazonDeliveryId (container lookup)
 *   lineItemId        -> ExtnAmazonLineItemAlias (order line lookup)
 *   merchantId        -> used to resolve EnterpriseCode
 *   carrierTracking   -> TrackingNo, CarrierCode on Container
 */
public class AmzProcessMCFShipmentEvent implements YIFCustomApi {

	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzProcessMCFShipmentEvent.class);
	private Properties props;

	Map<String, String> genericPropertiesMap = new HashMap<>();

	public Document processShipmentEvent(YFSEnvironment env, Document inDoc) throws Exception {
		logger.beginTimer(
				"class: AmzProcessMCFShipmentEvent | method: processShipmentEvent -- Starts");

		Document outDoc = null;
		Document getShipContainerListOp = null;
		Document getOrderLineListOp = null;
		try {
			Element eleEvent = inDoc.getDocumentElement();
			String merchantId = eleEvent.getAttribute("merchantId");
			String eventType = eleEvent.getAttribute("eventType");

			Element eleFulfillmentOrder = SCXmlUtil.getChildElement(eleEvent, "order");
			String amazonOrderId = eleFulfillmentOrder.getAttribute("orderId");

			String strEnterpriseCode = AmzEnterpriseBPIDXRefUtil.getEnterpriseCode(env, merchantId);

			NodeList shipmentNodes = eleFulfillmentOrder.getElementsByTagName("shipments");

			for (int s = 0; s < shipmentNodes.getLength(); s++) {
				Element eleShipment = (Element) shipmentNodes.item(s);
				String amazonShipmentId = eleShipment.getAttribute("amazonShipmentId");

				if (YFCObject.isVoid(amazonShipmentId)) {
					YFSException ex = new YFSException();
					ex.setErrorCode(AmzLiterals.STR_DELIVERY_ERROR_CODE);
					ex.setErrorDescription("Amazon Shipment ID (amazonShipmentId) is blank or empty");
					throw ex;
				}

				if (YFCObject.isVoid(amazonOrderId)) {
					YFSException ex = new YFSException();
					ex.setErrorCode(AmzLiterals.STR_DELIVERY_ERROR_CODE);
					ex.setErrorDescription("Amazon OrderId is blank or empty");
					throw ex;
				}

				// Check if container already exists in OMS for this shipment ID
				Document getShipContainerListDoc = SCXmlUtil.createDocument(AmzLiterals.ELE_CONTAINER);
				Element eleExtn = SCXmlUtil.createChild(getShipContainerListDoc.getDocumentElement(),
						AmzLiterals.E_EXTN);
				eleExtn.setAttribute(AmzLiterals.ATTR_AMZ_DELIVERY_ID, amazonShipmentId);
				logger.debug("getShipContainerListDoc: " + SCXmlUtil.getString(getShipContainerListDoc));

				Document getShipContainerListTemp = SCXmlUtil.createFromString(AmzLiterals.TEMP_SHIP_CONTAINER_LIST);

				getShipContainerListOp = AmzCommonUtil.callAPI(env, getShipContainerListDoc,
						"getShipmentContainerList", getShipContainerListTemp);
				logger.debug("getShipContainerListOp: " + SCXmlUtil.getString(getShipContainerListOp));

				String totalNumberOfRecords = getShipContainerListOp.getDocumentElement()
						.getAttribute("TotalNumberOfRecords");

				if ("0".equalsIgnoreCase(totalNumberOfRecords)) {
					// Container does not exist — confirm shipment

					String lineItemId = getFirstLineItemId(eleShipment);
					if (YFCObject.isVoid(lineItemId)) {
						YFSException ex = new YFSException();
						ex.setErrorCode(AmzLiterals.STR_DELIVERY_ERROR_CODE);
						ex.setErrorDescription("Amazon LineItemAlias is blank or empty in MCF API event");
						throw ex;
					}

					Document getOrderLineListInput = SCXmlUtil.createDocument(AmzLiterals.E_ORDER_LINE);
					Element eleOrderLineExtn = SCXmlUtil.createChild(
							getOrderLineListInput.getDocumentElement(), AmzLiterals.E_EXTN);
					eleOrderLineExtn.setAttribute(AmzLiterals.A_EXTN_AMZ_LINE_ITEM_ALIAS, lineItemId);
					logger.debug("getOrderLineListInput: " + SCXmlUtil.getString(getOrderLineListInput));

					Document getOrderLineListTemp = SCXmlUtil.createFromString(AmzLiterals.TEMP_ORDER_LINE_LIST);

					getOrderLineListOp = AmzCommonUtil.callAPI(env, getOrderLineListInput,
							AmzCommonConstants.API_GET_ORDER_LINE_LIST, getOrderLineListTemp);
					logger.debug("getOrderLineListOp: " + SCXmlUtil.getString(getOrderLineListOp));

					// Confirm shipment (creates the container)
					outDoc = prepareInputAndConfirmShipment(env, inDoc, eleShipment, getOrderLineListOp);

					prepareAndLogResponse(AmzLiterals.STR_SUCCESS, outDoc, inDoc, amazonShipmentId,
							amazonOrderId, eventType, null);

				} else {
					// Container already exists — ignore
					String msg = "Message = Shipment container already exist for amazonShipmentId "
							+ amazonShipmentId + " in OMS. Message is ignored.";
					logger.info("EventType = " + eventType);
					logger.info(msg);
					prepareAndLogResponse(AmzLiterals.STR_SUCCESS, getShipContainerListOp, inDoc,
							amazonShipmentId, amazonOrderId, eventType, msg);
				}
			}
		} catch (YFSException e) {
			prepareAndLogResponse(AmzLiterals.STR_ERROR, outDoc, inDoc, "", "", "", e.getErrorDescription());
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			YFSException yfse = new YFSException();
			yfse.setErrorCode(AmzLiterals.STR_DELIVERY_ERROR_CODE);
			yfse.setErrorDescription(e.getMessage());
			prepareAndLogResponse(AmzLiterals.STR_ERROR, outDoc, inDoc, "", "", "", yfse.getErrorDescription());
			throw yfse;
		}
		logger.endTimer(
				"class: AmzProcessMCFShipmentEvent | method: processShipmentEvent -- Ends");
		return outDoc;
	}

	/**
	 * Builds the confirmShipment input XML from the MCF API shipment element
	 * and OMS order line list, then calls confirmShipment API.
	 *
	 * Tracking info comes directly from the event (CarrierTracking element).
	 */
	public Document prepareInputAndConfirmShipment(YFSEnvironment env, Document inDoc,
			Element eleSpApiShipment, Document getOrderLineList) throws Exception {
		logger.beginTimer(
				"class: AmzProcessMCFShipmentEvent | method: prepareInputAndConfirmShipment -- Starts");

		Document outDoc = null;
		Element eleGetOrderLineListOp = getOrderLineList.getDocumentElement();

		String amazonShipmentId = eleSpApiShipment.getAttribute("amazonShipmentId");

		String extnOrderCountry = SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp,
				AmzLiterals.XPATH_EXTN_COUNTRY_CODE);
		String enterpriseCode = SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp,
				AmzLiterals.XPATH_ENTERPRISE_CODE);

		Document propertyDoc = SCXmlUtil.createDocument("Properties");
		propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, enterpriseCode);
		genericPropertiesMap = AmzGetGenericProperty.getGenericProperties(env, propertyDoc);

		String shipNode = genericPropertiesMap.get(AmzCommonConstants.PROP_AMZ_SHIP_NODE + extnOrderCountry);
		logger.debug("shipNode: " + shipNode);

		NodeList orderStatusesList = AmzXMLUtil.getXpathNodes(eleGetOrderLineListOp,
				"/OrderLineList/OrderLine/OrderStatuses/OrderStatus[@ShipNode='" + shipNode + "']");

		ArrayList<String> orderLineKeyList = new ArrayList<String>();
		for (int k = 0; k < orderStatusesList.getLength(); k++) {
			Element eleOrderStatus = (Element) orderStatusesList.item(k);
			String orderLineKey = eleOrderStatus.getAttribute(AmzLiterals.A_ORDER_LINE_KEY);
			if (!orderLineKeyList.contains(orderLineKey)) {
				orderLineKeyList.add(orderLineKey);
			}
		}

		if (!YFCObject.isVoid(orderLineKeyList) && orderLineKeyList.size() > 1) {
			YFSException yfse = new YFSException();
			String message = "Order has multi releases with same Ship Node " + shipNode + ". OrderLineKeys are:";
			for (int i = 0; i < orderLineKeyList.size(); i++) {
				message = message.concat(orderLineKeyList.get(i) + " ");
			}
			yfse.setErrorCode(AmzLiterals.STR_DELIVERY_ERROR_CODE);
			yfse.setErrorDescription(message);
			throw yfse;
		}

		// Build confirmShipment input
		Document confirmShipmentDoc = SCXmlUtil.createDocument(AmzLiterals.STR_SHIPMENT);
		Element eleConfirmShipmentDoc = confirmShipmentDoc.getDocumentElement();
		eleConfirmShipmentDoc.setAttribute(AmzLiterals.ATTR_SELLER_ORG_CODE,
				SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_SELLER_ORG_CODE));
		eleConfirmShipmentDoc.setAttribute(AmzLiterals.A_ORDER_NO,
				SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_SALES_ORDER_NO));
		eleConfirmShipmentDoc.setAttribute(AmzLiterals.ATTR_ORDER_RELEASE_KEY,
				SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp,
						"/OrderLineList/OrderLine/OrderStatuses/OrderStatus[@ShipNode='"
								+ shipNode + "']/@OrderReleaseKey"));
		eleConfirmShipmentDoc.setAttribute(AmzLiterals.ATTR_DOCUMENT_TYPE,
				SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_DOCUEMENT_TYPE));
		eleConfirmShipmentDoc.setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,
				SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_ENTERPRISE_CODE));
		eleConfirmShipmentDoc.setAttribute(AmzLiterals.A_SHIP_NODE,
				SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp,
						"/OrderLineList/OrderLine/OrderStatuses/OrderStatus[@ShipNode='"
								+ shipNode + "']/@ShipNode"));

		// Container — tracking info from event ShipmentPackage
		Element eleContainers = SCXmlUtil.createChild(eleConfirmShipmentDoc, AmzLiterals.ELE_CONTAINERS);
		Element eleContainer = SCXmlUtil.createChild(eleContainers, AmzLiterals.ELE_CONTAINER);

		String trackingNo = "";
		String carrierCode = "";
		Element eleShipmentPackage = SCXmlUtil.getChildElement(eleSpApiShipment, "packages");
		if (!YFCObject.isVoid(eleShipmentPackage)) {
			// SP-API 2026-07-04: carrier tracking is nested under tracking.carrier
			Element eleTracking = SCXmlUtil.getChildElement(eleShipmentPackage, "tracking");
			Element eleCarrierTracking = !YFCObject.isVoid(eleTracking)
					? SCXmlUtil.getChildElement(eleTracking, "carrier") : null;
			if (!YFCObject.isVoid(eleCarrierTracking)) {
				trackingNo = eleCarrierTracking.getAttribute("trackingNumber");
				carrierCode = eleCarrierTracking.getAttribute("carrierCode");
			}
		}

		eleContainer.setAttribute(AmzLiterals.ATTR_TRACKING_NO, trackingNo);

		Element eleContainerExtn = SCXmlUtil.createChild(eleContainer, AmzLiterals.E_EXTN);
		eleContainerExtn.setAttribute(AmzLiterals.ATTR_AMZ_DELIVERY_ID, amazonShipmentId);
		eleContainerExtn.setAttribute(AmzLiterals.ATTR_EXTN_CARRIER_CODE, carrierCode);

		Element eleContainerDetails = SCXmlUtil.createChild(eleContainer, AmzLiterals.ELE_CONTAINER_DETAILS);
		Element eleShipmentLines = SCXmlUtil.createChild(eleConfirmShipmentDoc, AmzLiterals.ELE_SHIPMENT_LINES);

		// Line items — directly from event (flat structure: <lineItems> is the item itself)
		NodeList lineItemNodes = eleSpApiShipment.getElementsByTagName("items");
		if (lineItemNodes.getLength() > 0) {
			for (int i = 0; i < lineItemNodes.getLength(); i++) {
				Element eleLineItem = (Element) lineItemNodes.item(i);
				String strLineItemId = eleLineItem.getAttribute("lineItemId");
				Element eleAmount = SCXmlUtil.getChildElement(eleLineItem, "amount");
				String strAmountValue = !YFCObject.isVoid(eleAmount) ? eleAmount.getAttribute("value") : "";

				Element eleContainerDetail = SCXmlUtil.createChild(eleContainerDetails,
						AmzLiterals.ELE_CONTAINER_DETAIL);
				Element eleContShipmentLine = SCXmlUtil.createChild(eleContainerDetail,
						AmzLiterals.ELE_SHIPMENT_LINE);
				Element eleShipmentLine = SCXmlUtil.createChild(eleShipmentLines,
						AmzLiterals.ELE_SHIPMENT_LINE);

				// Look up OMS OrderLineKey using lineItemId = ExtnAmazonLineItemAlias
				String strOrderLineKey = AmzXMLUtil.getXpathAttribute(eleGetOrderLineListOp,
						"/OrderLineList/OrderLine/Order/OrderLines/OrderLine/Extn[@ExtnAmazonLineItemAlias='"
								+ strLineItemId + "']/../@OrderLineKey");
				String strOrderReleaseKey = AmzXMLUtil.getXpathAttribute(eleGetOrderLineListOp,
						"/OrderLineList/OrderLine/OrderStatuses/OrderStatus[@ShipNode='"
								+ shipNode + "']/@OrderReleaseKey");

				if (YFCObject.isVoid(strOrderReleaseKey)) {
					YFSException yfse = new YFSException();
					yfse.setErrorCode(AmzLiterals.STR_DELIVERY_ERROR_CODE);
					yfse.setErrorDescription(
							"Order Release could not be found for the order lines sent in the Package");
					throw yfse;
				}

				eleContainerDetail.setAttribute(AmzLiterals.ATTR_QUANTITY, strAmountValue);

				String orderNo = SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp,
						AmzLiterals.XPATH_SALES_ORDER_NO);

				eleContShipmentLine.setAttribute(AmzLiterals.A_ORDER_NO, orderNo);
				eleContShipmentLine.setAttribute(AmzLiterals.ATTR_ORDER_RELEASE_KEY, strOrderReleaseKey);
				eleContShipmentLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, strOrderLineKey);
				eleContShipmentLine.setAttribute(AmzLiterals.ATTR_QUANTITY, strAmountValue);

				eleShipmentLine.setAttribute(AmzLiterals.A_ORDER_NO, orderNo);
				eleShipmentLine.setAttribute(AmzLiterals.ATTR_ORDER_RELEASE_KEY, strOrderReleaseKey);
				eleShipmentLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, strOrderLineKey);
				eleShipmentLine.setAttribute(AmzLiterals.ATTR_QUANTITY, strAmountValue);
			}
		}

		Document tempConfirmShipment = SCXmlUtil.createFromString(AmzLiterals.TEMP_SHIPMENT);

		logger.debug("confirmShipmentDoc: " + SCXmlUtil.getString(confirmShipmentDoc));

		try {
			outDoc = AmzCommonUtil.callAPI(env, confirmShipmentDoc, "confirmShipment", tempConfirmShipment);
		} catch (YFSException e) {
			throw AmzCommonUtil.createException(e);
		}
		logger.debug("confirmShipmentDoc outDoc: " + SCXmlUtil.getString(outDoc));

		logger.endTimer(
				"class: AmzProcessMCFShipmentEvent | method: prepareInputAndConfirmShipment -- Ends");
		return outDoc;
	}

	/**
	 * Extracts the first lineItemId from the Shipment element.
	 */
	private String getFirstLineItemId(Element eleShipment) {
		Element eleLineItem = SCXmlUtil.getChildElement(eleShipment, "items");
		if (!YFCObject.isVoid(eleLineItem)) {
			return eleLineItem.getAttribute("lineItemId");
		}
		return "";
	}

	public void prepareAndLogResponse(String processStatus, Document apiOutput, Document inDoc,
			String amazonShipmentId, String amazonOrderId, String eventType, String message) {

		logger.beginTimer(
				"class: AmzProcessMCFShipmentEvent | method: prepareAndLogResponse -- Starts");

		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE, eventType);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_ACTION, "OMS-PROCESS");
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, amazonOrderId);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_DELIVERY_ID, amazonShipmentId);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_PROCESS_STATUS, processStatus);

		if (!YFCObject.isVoid(apiOutput)) {
			logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_SHIPMENT_NO,
					apiOutput.getDocumentElement().getAttribute(AmzLiterals.ATTR_SHIPMENT_NO));
			logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO,
					apiOutput.getDocumentElement().getAttribute(AmzLiterals.A_ORDER_NO));
			logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,
					apiOutput.getDocumentElement().getAttribute(AmzLiterals.A_ENTERPRISE_CODE));

			Element eleContainers = SCXmlUtil.getChildElement(apiOutput.getDocumentElement(),
					AmzLiterals.ELE_CONTAINERS);
			if (!YFCObject.isVoid(eleContainers)) {
				Element eleContainer = SCXmlUtil.getChildElement(eleContainers, AmzLiterals.ELE_CONTAINER);
				String containerNo = eleContainer.getAttribute("ContainerNo");
				logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_CONTAINER_NO, containerNo);
			}
		}

		if (!YFCObject.isVoid(message)) {
			if (processStatus.equalsIgnoreCase(AmzLiterals.STR_SUCCESS)) {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_MESSAGE, message);
			} else {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_ERROR_MSG, message);
			}
		}
		AmzCommonUtil.logAmzConnResponse(logInput);

		logger.endTimer(
				"class: AmzProcessMCFShipmentEvent | method: prepareAndLogResponse -- Ends");
	}

	@Override
	public void setProperties(Properties inProps) {
		this.props = inProps;
	}
}
