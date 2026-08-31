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
 * This class is invoked through a async service
 * 'AmazonProcessFulfillmentEvents' which will be used to read messages from
 * Queue and process the events. Method processInTransitAndDeliveredEvent will
 * check if container is exist for deliveredID received in input. If container
 * is exist then simply ignore and log message. Else, query the amazon order to
 * get the tracking details. If its in transit event then confirm the shipment
 * with tracking details at container level. If it is delivered event and
 * shipment is not created, then confirm shipment and move status to Delivered.
 */

public class AmzProcessInTransitAndDeliveredEvent implements YIFCustomApi {

	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzProcessInTransitAndDeliveredEvent.class);
	private Properties props;

	Map<String, String> genricPropertiesMap = new HashMap<>();	
	public Document processInTransitAndDeliveredEvent(YFSEnvironment env, Document inDoc) throws Exception {
		logger.beginTimer(
				"class: AmzProcessInTransitAndDeliveredEvent | method: processInTransitAndDeliveredEvent -- Starts");

		Document outDoc = null;
		Document getShipContainerListOp = null;
		Document getOrderDetails = null;
		Document getOrderLineListOp = null;
		try {

			Element eleInDoc = inDoc.getDocumentElement();

			String extnAmazonDeliveryID = SCXmlUtil.getXpathAttribute(eleInDoc, AmzLiterals.XPATH_AMZ_DELIVERY_ID);
			String amazonOrderId = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID);
			String eventType = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE);

			String strEnterpriseCode = AmzEnterpriseBPIDXRefUtil.getEnterpriseCode(env, eleInDoc.getAttribute("BusinessProductID"));

			Document getShipContainerListDoc = SCXmlUtil.createDocument(AmzLiterals.ELE_CONTAINER);
			Element eleExtn = SCXmlUtil.createChild(getShipContainerListDoc.getDocumentElement(), AmzLiterals.E_EXTN);
			eleExtn.setAttribute(AmzLiterals.ATTR_AMZ_DELIVERY_ID, extnAmazonDeliveryID);
			logger.debug("getShipContainerListDoc: " + SCXmlUtil.getString(getShipContainerListDoc));

			Document getShipContainerListTemp = SCXmlUtil.createFromString(AmzLiterals.TEMP_SHIP_CONTAINER_LIST);

			if (YFCObject.isVoid(extnAmazonDeliveryID)) {
				YFSException ex = new YFSException();
				ex.setErrorCode(AmzLiterals.STR_DELIVERY_ERROR_CODE);
				ex.setErrorDescription("Amazon Delivery ID is blank or empty");
				throw ex;
			}
			getShipContainerListOp = AmzCommonUtil.callAPI(env, getShipContainerListDoc, "getShipmentContainerList",
					getShipContainerListTemp);
			logger.debug("getShipContainerListOp: " + SCXmlUtil.getString(getShipContainerListOp));

			String totalNumberOfRecords = getShipContainerListOp.getDocumentElement()
					.getAttribute("TotalNumberOfRecords");

			if ("0".equalsIgnoreCase(totalNumberOfRecords)) {
				Document getOrderDetailsInput = SCXmlUtil.createDocument(AmzLiterals.STR_ORDER);
				getOrderDetailsInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMZ_ORDER_ID, amazonOrderId);
				getOrderDetailsInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE, eventType);
				getOrderDetailsInput.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);
				
				if (YFCObject.isVoid(amazonOrderId)) {
					YFSException ex = new YFSException();
					ex.setErrorCode(AmzLiterals.STR_DELIVERY_ERROR_CODE);
					ex.setErrorDescription("Amazon OrderId is blank or empty");
					throw ex;
				}
				getOrderDetails = AmzCommonUtil.callService(env, getOrderDetailsInput, AmzCommonConstants.SERVICE_AMAZON_GET_ORDER_DETAILS, null);
				logger.debug("getOrderDetails: " + SCXmlUtil.getString(getOrderDetails));

				Element eleGetOrderDetails = getOrderDetails.getDocumentElement();

				Element elePackageInformation = SCXmlUtil.getXpathElement(eleGetOrderDetails,
						AmzLiterals.XPATH_AMZ_PACKAGE_INFO);
				Element eleDetails = AmzXMLUtil.getXpathElement(elePackageInformation,
						"//packageInformation/details[@id='" + extnAmazonDeliveryID + "']");

				String lineItemId = SCXmlUtil.getXpathAttribute(eleDetails, AmzLiterals.XPATH_LINE_ITEM_ID);

				Document getOrderLineListInput = SCXmlUtil.createDocument(AmzLiterals.E_ORDER_LINE);

				if (YFCObject.isVoid(lineItemId)) {
					YFSException ex = new YFSException();
					ex.setErrorCode(AmzLiterals.STR_DELIVERY_ERROR_CODE);
					ex.setErrorDescription("Amazon LineItemAlias is blank or empty");
					throw ex;
				}
				Element eleOrderLineExtn = SCXmlUtil.createChild(getOrderLineListInput.getDocumentElement(),
						AmzLiterals.E_EXTN);
				eleOrderLineExtn.setAttribute(AmzLiterals.A_EXTN_AMZ_LINE_ITEM_ALIAS, lineItemId);
				logger.debug("getOrderLineListInput: " + SCXmlUtil.getString(getOrderLineListInput));

				Document getOrderLineListTemp = SCXmlUtil.createFromString(AmzLiterals.TEMP_ORDER_LINE_LIST);

				getOrderLineListOp = AmzCommonUtil.callAPI(env, getOrderLineListInput, AmzCommonConstants.API_GET_ORDER_LINE_LIST,
						getOrderLineListTemp);
				logger.debug("getOrderLineListOp: " + SCXmlUtil.getString(getOrderLineListOp));

				outDoc = prepareInputAndConfirmShipment(env, inDoc, getOrderDetails, getOrderLineListOp);

				if (eventType.equals("PACKAGE_DELIVERY_IN_TRANSIT")) {
					prepareAndLogResponse(AmzLiterals.STR_SUCCESS, outDoc, inDoc, null);
				}
				if (eventType.equals("PACKAGE_DELIVERED")) {
					String shipmentKey = SCXmlUtil.getXpathAttribute(outDoc.getDocumentElement(),
							"/Shipment/@ShipmentKey");
					Document changeShipStatusInput = SCXmlUtil.createDocument(AmzLiterals.STR_SHIPMENT);
					changeShipStatusInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_SHIPMENT_KEY, shipmentKey);

					outDoc = invokeChangeShipmentStatus(env, changeShipStatusInput);

					prepareAndLogResponse(AmzLiterals.STR_SUCCESS, outDoc, inDoc, null);
				}

			} else {

				if (eventType.equals("PACKAGE_DELIVERY_IN_TRANSIT")) {
					String msg = "Message = Shipment container already exist for deliveryId " + extnAmazonDeliveryID
							+ " in OMS. Message is ignored , AmazonDeliveryId = " + extnAmazonDeliveryID;

					logger.info("EventType = " + eventType);
					logger.info(msg);
					prepareAndLogResponse(AmzLiterals.STR_SUCCESS, getShipContainerListOp, inDoc, msg);
				}

				if (eventType.equals("PACKAGE_DELIVERED")) {
					String shipmentKey = SCXmlUtil.getXpathAttribute(getShipContainerListOp.getDocumentElement(),
							AmzLiterals.XPATH_SHIPMENT_KEY);
					String shipmentNo = SCXmlUtil.getXpathAttribute(getShipContainerListOp.getDocumentElement(),
							AmzLiterals.XPATH_SHIPMENT_NO);
					String status = SCXmlUtil.getXpathAttribute(getShipContainerListOp.getDocumentElement(),
							AmzLiterals.XPATH_SHIPMENT_STATUS);
					String baseDropStatus = props.getProperty(AmzLiterals.ATTR_BASE_DROP_STATUS);
					if (status.equals(baseDropStatus)) {
						String msg = "Message = Shipment container already delivered for deliveryId "
								+ extnAmazonDeliveryID + " in OMS. Shipment No: " + shipmentNo + " Status: " + status
								+ ". Message is ignored , AmazonDeliveryId = " + extnAmazonDeliveryID;
						logger.info("EventType = " + eventType);
						logger.info(msg);
						prepareAndLogResponse(AmzLiterals.STR_SUCCESS, getShipContainerListOp, inDoc, msg);
					} else {
						Document changeShipStatusInput = SCXmlUtil.createDocument(AmzLiterals.STR_SHIPMENT);
						changeShipStatusInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_SHIPMENT_KEY,
								shipmentKey);

						outDoc = invokeChangeShipmentStatus(env, changeShipStatusInput);

						prepareAndLogResponse(AmzLiterals.STR_SUCCESS, outDoc, inDoc, null);
					}
				}
			}
		} catch (YFSException e) {
			prepareAndLogResponse(AmzLiterals.STR_ERROR, outDoc, inDoc, e.getErrorDescription());
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			YFSException yfse = new YFSException();
			yfse.setErrorCode(AmzLiterals.STR_DELIVERY_ERROR_CODE);
			yfse.setErrorDescription(e.getMessage());
			prepareAndLogResponse(AmzLiterals.STR_ERROR, outDoc, inDoc, yfse.getErrorDescription());
			throw yfse;
		}
		logger.endTimer(
				"class: AmzProcessInTransitAndDeliveredEvent | method: processInTransitAndDeliveredEvent -- Starts");
		return outDoc;
	}

	public Document prepareInputAndConfirmShipment(YFSEnvironment env, Document inDoc, Document getOrderDetails,
			Document getOrderLineList) throws Exception {
		logger.beginTimer(
				"class: AmzProcessInTransitAndDeliveredEvent | method: prepareInputAndConfirmShipment -- Starts");

		Document outDoc = null;
		Element eleGetOrderLineListOp = getOrderLineList.getDocumentElement();
		Element eleGetOrderDetails = getOrderDetails.getDocumentElement();

		String extnAmazonDeliveryID = SCXmlUtil.getXpathAttribute(inDoc.getDocumentElement(),
				AmzLiterals.XPATH_AMZ_DELIVERY_ID);

		Element elePackageInformation = SCXmlUtil.getXpathElement(eleGetOrderDetails,
				AmzLiterals.XPATH_AMZ_PACKAGE_INFO);
		Element eleDetails = AmzXMLUtil.getXpathElement(elePackageInformation,
				"//packageInformation/details[@id='" + extnAmazonDeliveryID + "']");

		String extnOrderCountry = SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp,
				AmzLiterals.XPATH_EXTN_COUNTRY_CODE);
		String entepriseCode = SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp,
				AmzLiterals.XPATH_ENTERPRISE_CODE);
		
		Document propertyDoc = SCXmlUtil.createDocument("Properties");
		propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, entepriseCode);
		genricPropertiesMap = AmzGetGenericProperty.getGenericProperties(env , propertyDoc);
		
		String shipNode = genricPropertiesMap.get(AmzCommonConstants.PROP_AMZ_SHIP_NODE+ extnOrderCountry);
		logger.debug("shipNode: "+ shipNode);
		
		NodeList orderStatuesList = AmzXMLUtil.getXpathNodes(eleGetOrderLineListOp,
				"/OrderLineList/OrderLine/OrderStatuses/OrderStatus[@ShipNode='" + shipNode + "']");
		
		ArrayList<String> orderLineKeyList = new ArrayList<String>();
		for (int k = 0; k < orderStatuesList.getLength(); k++) {
			Element eleOrderStatus = (Element) orderStatuesList.item(k);
			String orderLineKey = eleOrderStatus.getAttribute(AmzLiterals.A_ORDER_LINE_KEY);
			if(!orderLineKeyList.contains(orderLineKey)) {
			orderLineKeyList.add(orderLineKey);
			}
		}
		
		if (!YFCObject.isVoid(orderLineKeyList) && orderLineKeyList.size() > 1) {
			YFSException yfse = new YFSException();
			String message = "Order has multi releases with same Ship Node "+shipNode+". OrderLineKeys are:";
			for (int i = 0; i < orderLineKeyList.size(); i++) {
				message = message.concat(orderLineKeyList.get(i)+ " ");
			}
			yfse.setErrorCode(AmzLiterals.STR_DELIVERY_ERROR_CODE);
			yfse.setErrorDescription(message);
			prepareAndLogResponse(AmzLiterals.STR_ERROR, outDoc, inDoc, yfse.getErrorDescription());
			throw yfse;
		}

		Document confirmShipmentDoc = SCXmlUtil.createDocument(AmzLiterals.STR_SHIPMENT);
		Element eleConfirmShipmentDoc = confirmShipmentDoc.getDocumentElement();
		eleConfirmShipmentDoc.setAttribute(AmzLiterals.ATTR_SELLER_ORG_CODE,
				SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_SELLER_ORG_CODE));
		eleConfirmShipmentDoc.setAttribute(AmzLiterals.A_ORDER_NO,
				SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_SALES_ORDER_NO));
		eleConfirmShipmentDoc.setAttribute(AmzLiterals.ATTR_ORDER_RELEASE_KEY, SCXmlUtil.getXpathAttribute(
				eleGetOrderLineListOp,
				"/OrderLineList/OrderLine/OrderStatuses/OrderStatus[@ShipNode='" + shipNode + "']/@OrderReleaseKey"));
		eleConfirmShipmentDoc.setAttribute(AmzLiterals.ATTR_DOCUMENT_TYPE,
				SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_DOCUEMENT_TYPE));
		eleConfirmShipmentDoc.setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,
				SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_ENTERPRISE_CODE));
		eleConfirmShipmentDoc.setAttribute(AmzLiterals.A_SHIP_NODE, SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp,
				"/OrderLineList/OrderLine/OrderStatuses/OrderStatus[@ShipNode='" + shipNode + "']/@ShipNode"));

		Element eleContainers = SCXmlUtil.createChild(eleConfirmShipmentDoc, AmzLiterals.ELE_CONTAINERS);
		Element eleContainer = SCXmlUtil.createChild(eleContainers, AmzLiterals.ELE_CONTAINER);
		eleContainer.setAttribute(AmzLiterals.ATTR_TRACKING_NO,
				SCXmlUtil.getXpathAttribute(eleDetails, AmzLiterals.XPATH_AMZ_TRACKING_NO));
		eleContainer.setAttribute(AmzLiterals.ATTR_EXTN_REF_1,
				SCXmlUtil.getXpathAttribute(eleDetails, AmzLiterals.XPATH_AMZ_TRACKING_URL));
		Element eleContainerExtn = SCXmlUtil.createChild(eleContainer, AmzLiterals.E_EXTN);
		eleContainerExtn.setAttribute(AmzLiterals.ATTR_AMZ_DELIVERY_ID, extnAmazonDeliveryID);
		eleContainerExtn.setAttribute(AmzLiterals.ATTR_AMZ_TRACKING_URL,
				SCXmlUtil.getXpathAttribute(eleDetails, AmzLiterals.XPATH_AMZ_TRACKING_URL));
		eleContainerExtn.setAttribute(AmzLiterals.ATTR_EXTN_CARRIER_CODE,
				SCXmlUtil.getXpathAttribute(eleDetails, AmzLiterals.XPATH_AMZ_CARRIER_CODE));
		Element eleContainerDetails = SCXmlUtil.createChild(eleContainer, AmzLiterals.ELE_CONTAINER_DETAILS);

		Element eleShipmentLines = SCXmlUtil.createChild(eleConfirmShipmentDoc, AmzLiterals.ELE_SHIPMENT_LINES);

		NodeList orderLineItemsList = AmzXMLUtil.getXpathNodes(eleDetails, AmzLiterals.XPATH_AMZ_LINE_ITEMS);
		for (int i = 0; i < orderLineItemsList.getLength(); i++) {
			Element eleContainerDetail = SCXmlUtil.createChild(eleContainerDetails, AmzLiterals.ELE_CONTAINER_DETAIL);
			Element eleContShipmentLine = SCXmlUtil.createChild(eleContainerDetail, AmzLiterals.ELE_SHIPMENT_LINE);
			Element eleShipmentLine = SCXmlUtil.createChild(eleShipmentLines, AmzLiterals.ELE_SHIPMENT_LINE);

			Element eleOrderLineItem = (Element) orderLineItemsList.item(i);
			String strLineItemID = SCXmlUtil.getXpathAttribute(eleOrderLineItem, AmzLiterals.XPATH_AMZ_LINE_ID);
			String strAmountValue = SCXmlUtil.getXpathAttribute(eleOrderLineItem, AmzLiterals.XPATH_AMZ_LINE_VALUE);
			String strOrderLineKey = AmzXMLUtil.getXpathAttribute(eleGetOrderLineListOp,
					"/OrderLineList/OrderLine/Order/OrderLines/OrderLine/Extn[@ExtnAmazonLineItemAlias='"
							+ strLineItemID + "']/../@OrderLineKey");
			String strOrderReleaseKey = AmzXMLUtil.getXpathAttribute(eleGetOrderLineListOp,
					"/OrderLineList/OrderLine/OrderStatuses/OrderStatus[@ShipNode='" + shipNode
							+ "']/@OrderReleaseKey");
			if (YFCObject.isVoid(strOrderReleaseKey)) {
				YFSException yfse = new YFSException();
				yfse.setErrorCode(AmzLiterals.STR_DELIVERY_ERROR_CODE);
				yfse.setErrorDescription("Order Release could not be found for the order lines sent in the Package");
				throw yfse;
			}
			eleContainerDetail.setAttribute(AmzLiterals.ATTR_QUANTITY, strAmountValue);
			eleContShipmentLine.setAttribute(AmzLiterals.A_ORDER_NO,
					SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_SALES_ORDER_NO));
			eleContShipmentLine.setAttribute(AmzLiterals.ATTR_ORDER_RELEASE_KEY, strOrderReleaseKey);
			eleContShipmentLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, strOrderLineKey);
			eleContShipmentLine.setAttribute(AmzLiterals.ATTR_QUANTITY, strAmountValue);

			eleShipmentLine.setAttribute(AmzLiterals.A_ORDER_NO,
					SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_SALES_ORDER_NO));
			eleShipmentLine.setAttribute(AmzLiterals.ATTR_ORDER_RELEASE_KEY, strOrderReleaseKey);
			eleShipmentLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, strOrderLineKey);
			eleShipmentLine.setAttribute(AmzLiterals.ATTR_QUANTITY, strAmountValue);

		}

		Document tempConfirmShipment = SCXmlUtil.createFromString(AmzLiterals.TEMP_SHIPMENT);

		logger.debug("confirmShipmentDoc" + SCXmlUtil.getString(confirmShipmentDoc));

		try {
			outDoc = AmzCommonUtil.callAPI(env, confirmShipmentDoc, "confirmShipment", tempConfirmShipment);
		} catch (YFSException e) {
			throw AmzCommonUtil.createException(e);
		}
		logger.debug("confirmShipmentDoc outDoc" + SCXmlUtil.getString(outDoc));

		logger.endTimer(
				"class: AmzProcessInTransitAndDeliveredEvent | method: prepareInputAndConfirmShipment -- Starts");
		return outDoc;
	}

	public Document invokeChangeShipmentStatus(YFSEnvironment env, Document inDoc) throws Exception {
		logger.beginTimer("class: AmzProcessInTransitAndDeliveredEvent | method: invokeChangeShipmentStatus -- Starts");

		Element eleShipment = inDoc.getDocumentElement();

		String baseDropStatus = props.getProperty(AmzLiterals.ATTR_BASE_DROP_STATUS);
		String transactionId = props.getProperty(AmzLiterals.ATTR_TRANSACTION_ID);

		eleShipment.setAttribute(AmzLiterals.ATTR_BASE_DROP_STATUS, baseDropStatus);
		eleShipment.setAttribute(AmzLiterals.ATTR_TRANSACTION_ID, transactionId);

		logger.debug("changeShipmentStatus inDoc" + SCXmlUtil.getString(inDoc));

		Document tempchangeShipmentStatus = SCXmlUtil.createFromString(AmzLiterals.TEMP_SHIPMENT);
		Document outDoc = null;

		try {
			outDoc = AmzCommonUtil.callAPI(env, inDoc, "changeShipmentStatus", tempchangeShipmentStatus);
		} catch (YFSException e) {
			throw AmzCommonUtil.createException(e);
		}
		logger.endTimer("class: AmzProcessInTransitAndDeliveredEvent | method: invokeChangeShipmentStatus -- Ends");
		return outDoc;
	}

	public void prepareAndLogResponse(String processStatus, Document apiOutput, Document inDoc, String message) {

		logger.beginTimer("class: AmzProcessInTransitAndDeliveredEvent | method: prepareAndLogResponse -- Starts");

		String extnAmazonDeliveryID = SCXmlUtil.getXpathAttribute(inDoc.getDocumentElement(),
				AmzLiterals.XPATH_AMZ_DELIVERY_ID);
		String amazonOrderId = inDoc.getDocumentElement().getAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID);
		String eventType = inDoc.getDocumentElement().getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE);

		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE, eventType);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_ACTION, "OMS-PROCESS");
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, amazonOrderId);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_DELIVERY_ID, extnAmazonDeliveryID);
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

		logger.endTimer("class: AmzProcessInTransitAndDeliveredEvent | method: prepareAndLogResponse -- Ends");
	}

	@Override
	public void setProperties(Properties inProps) {
		this.props = inProps;
	}
}
