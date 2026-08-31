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
 * This class is invoked through a async service
 * 'AmazonProcessFulfillmentEvents' which will be used to read messages from
 * Queue and process the events. Method processDeliveryCancelledEvent will
 * check if container is exist for deliveredID received in input. If container
 * is exist then call changeShipmentStatus to update the shipment status as Delivery Cancelled and log message. 
 * Else, query the amazon order to get the package details information. 
 * If amazon order is in cancelled status then invoke change order to cancel line only BWP Lines
 * and MCF line should be in BackOrdered status.
 * Input message to service: 
 * <Shipment EventType="PACKAGE_DELIVERY_CANCELLED" EventId=""   EventTime=""  AmazonOrderId="" >
        <Containers>
                <Container ExtnAmazonDeliveryID=""/> 
        </Containers>
</Shipment>
 */

public class AmzProcessDeliveryCancelledEvent implements YIFCustomApi {

	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzProcessDeliveryCancelledEvent.class);
	private Properties props;
	String strShipNode=null;
	String strEnterpriseCode=null;
	Map<String, String> genricPropertiesMap = new HashMap<>();
	/**
	 * @param env
	 * @param inDoc
	 * @return
	 * @throws Exception
	 * This method processes a delivery cancellation event, validates in document message, 
	 * makes API calls to get shipmentContainer information, and generates an output document with the results. 
	 * It also handles logging and error handling, including responding with success or failure statuses
	 */
	
	public Document processDeliveryCancelledEvent(YFSEnvironment env, Document inDoc) throws Exception {
	    logger.beginTimer("class: AmzProcessDeliveryCancelledEvent | method: processDeliveryCancelledEvent -- Starts");
	    logger.info("class: AmzProcessDeliveryCancelledEvent | method: processDeliveryCancelledEvent -- Starts");
	    logger.debug("class: AmzProcessDeliveryCancelledEvent | method: processDeliveryCancelledEvent input doc is: " + AmzXMLUtil.getString(inDoc));
	    AmzCommonUtil.logAmzConnRequest(inDoc);
	    Document returnOutDocument = null;  
	    try {
	        Element eleInDoc = inDoc.getDocumentElement();
	        String extnAmazonDeliveryID = SCXmlUtil.getXpathAttribute(eleInDoc, AmzLiterals.XPATH_AMZ_DELIVERY_ID);
	        String amazonOrderId = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID);
	        String eventType = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE);
	    	strEnterpriseCode = AmzEnterpriseBPIDXRefUtil.getEnterpriseCode(env, eleInDoc.getAttribute("BusinessProductID"));

	        validateInput(extnAmazonDeliveryID, amazonOrderId,inDoc);
	        
	        //Added for Merchant
	        env.setTxnObject("CancelledThroughDeliveryEvent", "Y");

	        Document getShipContainerListOutDoc = callGetShipmentContainerListAPI(env, extnAmazonDeliveryID);
	        logger.debug("class: AmzProcessDeliveryCancelledEvent | method: processDeliveryCancelledEvent | getShipContainerListOutDoc" +SCXmlUtil.getString(getShipContainerListOutDoc));
	        
	        String totalNumberOfRecords = getShipContainerListOutDoc.getDocumentElement().getAttribute("TotalNumberOfRecords");
	        logger.debug("class: AmzProcessDeliveryCancelledEvent | method: processDeliveryCancelledEvent | TotalNumberOfRecords records are: " + totalNumberOfRecords);

	        String shipmentStatus = SCXmlUtil.getXpathAttribute(getShipContainerListOutDoc.getDocumentElement(),
					AmzLiterals.XPATH_SHIPMENT_STATUS);
			String baseDropStatus = props.getProperty(AmzLiterals.A_SHIPMENT_BASE_DROP_STATUS);
			String shipmentNo = SCXmlUtil.getXpathAttribute(getShipContainerListOutDoc.getDocumentElement(),
					AmzLiterals.XPATH_SHIPMENT_NO);
			// validate if shipment is already cancelled: start
			if (shipmentStatus.equals(baseDropStatus)) {
				String msg = "Message = Shipment container already cancelled for deliveryId "
						+ extnAmazonDeliveryID + " in OMS. Shipment No: " + shipmentNo + " Status: " + shipmentStatus
						+ ". Message is ignored , AmazonDeliveryId = " + extnAmazonDeliveryID;
				logger.info("EventType = " + eventType);
				logger.info(msg);
				  prepareAndLogResponse(AmzLiterals.STR_ERROR, returnOutDocument, inDoc, msg);
				  YFSException yfse = new YFSException();
	              yfse.setErrorCode(AmzLiterals.STR_DELIV_CANCEL_ERR_CODE+"SHIPMENT_CANCELLED");
	              yfse.setErrorDescription(msg);
	  	          throw yfse;
		    	}
			// validate if shipment is already cancelled: End
	        
	        if ("0".equalsIgnoreCase(totalNumberOfRecords)) {
	            returnOutDocument = updateOrderLines(env, inDoc, amazonOrderId, eventType, extnAmazonDeliveryID);
	            
	           logger.debug("class: AmzProcessDeliveryCancelledEvent | method: processDeliveryCancelledEvent | outDoc" +SCXmlUtil.getString(returnOutDocument));
	        
	        } else {
	            returnOutDocument = updateShipmentAndOrderLines(env, getShipContainerListOutDoc);
	        }

	        if (returnOutDocument != null && returnOutDocument.getDocumentElement() != null && !"Errors".equalsIgnoreCase(returnOutDocument.getDocumentElement().getNodeName())) {
	        	prepareAndLogResponse(AmzLiterals.STR_SUCCESS, returnOutDocument, inDoc, null);
	        	
	        }
	        else {
	        	String errorDescription = (returnOutDocument != null && returnOutDocument.getDocumentElement() != null) ? returnOutDocument.getDocumentElement().getElementsByTagName("Error").item(0).getAttributes().getNamedItem("ErrorDescription").getNodeValue() : null;
	        	prepareAndLogResponse(AmzLiterals.STR_ERROR, returnOutDocument, inDoc, errorDescription);
	        }			
	    	
	    	
	    } catch (YFSException e) {
	    	throw e;
	    } catch(Exception e) {
	    	  e.printStackTrace();
              YFSException yfse = new YFSException();
              yfse.setErrorCode(AmzLiterals.STR_DELIV_CANCEL_ERR_CODE+"EXCEPTION");
              yfse.setErrorDescription(e.getMessage());
              prepareAndLogResponse(AmzLiterals.STR_ERROR, returnOutDocument, inDoc, e.getMessage());
  	          throw yfse;
	    }
	    
	    logger.info("class: AmzProcessDeliveryCancelledEvent | method: processDeliveryCancelledEvent -- Ends");
	    logger.endTimer("class: AmzProcessDeliveryCancelledEvent | method: processDeliveryCancelledEvent -- Ends");
	    if (!YFCObject.isVoid(returnOutDocument)) {
	    return returnOutDocument;
	    }else {
	    	return inDoc;
	    }
	}

	/**
	 * @param extnAmazonDeliveryID
	 * @param amazonOrderId
	 * @throws YFSException
	 * Method to validate Indoc attributes, if Attribute doesn't exist then throw error
	 */
	private void validateInput(String extnAmazonDeliveryID, String amazonOrderId,Document inDoc) throws YFSException {
		logger.beginTimer("class: AmzProcessDeliveryCancelledEvent | method: validateInput -- Starts");
		logger.info("class: AmzProcessDeliveryCancelledEvent | method: validateInput -- Starts");
		  
		if (YFCObject.isVoid(extnAmazonDeliveryID)) {
	        YFSException yfse = new YFSException();
			yfse.setErrorCode(AmzLiterals.STR_DELIV_CANCEL_ERR_CODE+"DELIVERY_ID_MISSING");
			yfse.setErrorDescription("The Amazon Delivery ID is blank/Empty");
			prepareAndLogResponse(AmzLiterals.STR_ERROR, null, inDoc, yfse.getErrorDescription());
			throw yfse;		
		}else if (YFCObject.isVoid(amazonOrderId)) {
	        YFSException yfse = new YFSException();
			yfse.setErrorCode(AmzLiterals.STR_DELIV_CANCEL_ERR_CODE+"AMAZON_ORDER_ID_MISSING");
			yfse.setErrorDescription("The Amazon OrderId is blank/Empty");
			prepareAndLogResponse(AmzLiterals.STR_ERROR, null, inDoc, yfse.getErrorDescription());
			throw yfse;		
		}
		logger.info("class: AmzProcessDeliveryCancelledEvent | method: validateInput -- Ends");
		logger.endTimer("class: AmzProcessDeliveryCancelledEvent | method: validateInput -- Ends");
		   
	}

	/**
	 * @param env
	 * @param extnAmazonDeliveryID
	 * @return
	 * @throws Exception
	 * Method to Prepare input and call getShipmentContainerList API
	 */
	private Document callGetShipmentContainerListAPI(YFSEnvironment env, String extnAmazonDeliveryID) throws Exception {
		logger.beginTimer("class: AmzProcessDeliveryCancelledEvent | method: callGetShipmentContainerListAPI -- Starts");
		logger.info("class: AmzProcessDeliveryCancelledEvent | method: callGetShipmentContainerListAPI -- Starts");
		Document getShipContainerListoutDoc=null;
		Document getShipContainerListInDoc = SCXmlUtil.createDocument(AmzLiterals.ELE_CONTAINER);
	    Element eleExtn = SCXmlUtil.createChild(getShipContainerListInDoc.getDocumentElement(), AmzLiterals.E_EXTN);
	    eleExtn.setAttribute(AmzLiterals.ATTR_AMZ_DELIVERY_ID, extnAmazonDeliveryID);
	    
	    
	    Document getShipContainerListTemp = SCXmlUtil.createFromString(AmzLiterals.TEMP_SHIP_CONTAINER_LIST);
	    getShipContainerListoutDoc =AmzCommonUtil.callAPI(env, getShipContainerListInDoc, AmzCommonConstants.API_GET_SHIPMENT_CONTAINER_LIST, getShipContainerListTemp);
	    logger.info("class: AmzProcessDeliveryCancelledEvent | method: callGetShipmentContainerListAPI -- Ends");
	    logger.endTimer("class: AmzProcessDeliveryCancelledEvent | method: callGetShipmentContainerListAPI -- Ends");
		return getShipContainerListoutDoc;
	       
	}

	/**
	 * @param env
	 * @param inDoc
	 * @param amazonOrderId
	 * @param eventType
	 * @param extnAmazonDeliveryID
	 * @return
	 * @throws Exception
	 * Method to call amazon order and validate amazon order details and prepare input request
	 */
	private Document updateOrderLines(YFSEnvironment env, Document inDoc, String amazonOrderId, String eventType, String extnAmazonDeliveryID) throws Exception {
		logger.beginTimer("class: AmzProcessDeliveryCancelledEvent | method: updateOrderLines -- Starts");
		logger.info("class: AmzProcessDeliveryCancelledEvent | method: updateOrderLines -- Starts");
		Document getOrderDetailsAmazonOutDoc = getAmazonOrderDetails(env, amazonOrderId, eventType);
	    logger.debug("class: AmzProcessDeliveryCancelledEvent | method: updateOrderLines |getOrderDetailsAmazonOutDoc " +SCXmlUtil.getString(getOrderDetailsAmazonOutDoc));
		Element elePackageInformation = SCXmlUtil.getXpathElement(getOrderDetailsAmazonOutDoc.getDocumentElement(), AmzLiterals.XPATH_AMZ_PACKAGE_INFO);
	    Element eleMatchedPackageDetailsDetails = AmzXMLUtil.getXpathElement(elePackageInformation, "//packageInformation/details[@id='" + extnAmazonDeliveryID + "']");

	    if (YFCObject.isVoid(eleMatchedPackageDetailsDetails)) {
	    	YFSException yfse = new YFSException();
			yfse.setErrorCode(AmzLiterals.STR_DELIV_CANCEL_ERR_CODE+"DELIVERY_ID_MISSING");
			yfse.setErrorDescription("Could not find DeliveryId" + extnAmazonDeliveryID + " in Amazon Order details");
			prepareAndLogResponse(AmzLiterals.STR_ERROR, null, inDoc, yfse.getErrorDescription());
			throw yfse;	
	    }

	    String state = eleMatchedPackageDetailsDetails.getAttribute(AmzLiterals.STR_STATE);
	    logger.info("class: AmzProcessDeliveryCancelledEvent | method: updateOrderLines -- Ends");
	    logger.endTimer("class: AmzProcessDeliveryCancelledEvent | method: updateOrderLines -- Ends");
		
	    if (AmzLiterals.STR_CANCELLED.equalsIgnoreCase(state)) {
	        return processLineItem(env, inDoc, extnAmazonDeliveryID, eleMatchedPackageDetailsDetails,getOrderDetailsAmazonOutDoc);
	    } else {
	        YFSException yfse = new YFSException();
			yfse.setErrorCode(AmzLiterals.STR_DELIV_CANCEL_ERR_CODE+"ORDER_STATUS");
			yfse.setErrorDescription("Amazon Order " + amazonOrderId + " is not in Cancelled Status for the Delivery id :" + extnAmazonDeliveryID);
			prepareAndLogResponse(AmzLiterals.STR_ERROR, null, inDoc, yfse.getErrorDescription());
			throw yfse;	    
	    }
	    
	}

	/**
	 * @param env
	 * @param amazonOrderId
	 * @param eventType
	 * @return
	 * @throws Exception
	 * Method to prepare input and call amazon order details service
	 */
	private Document getAmazonOrderDetails(YFSEnvironment env, String amazonOrderId, String eventType) throws Exception {
		logger.beginTimer("class: AmzProcessDeliveryCancelledEvent | method: getAmazonOrderDetails -- Starts");
		logger.info("class: AmzProcessDeliveryCancelledEvent | method: getAmazonOrderDetails -- Starts");
		
		Document getOrderDetailsAmazonOutDoc=null;
		Document getOrderDetailsAmazonInDoc = SCXmlUtil.createDocument(AmzLiterals.STR_ORDER);
	    getOrderDetailsAmazonInDoc.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMZ_ORDER_ID, amazonOrderId);
	    getOrderDetailsAmazonInDoc.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE, eventType);
	    getOrderDetailsAmazonInDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);
	    getOrderDetailsAmazonOutDoc= AmzCommonUtil.callService(env, getOrderDetailsAmazonInDoc, AmzCommonConstants.SERVICE_AMAZON_GET_ORDER_DETAILS, null);
	    logger.info("class: AmzProcessDeliveryCancelledEvent | method: getAmazonOrderDetails -- Ends");
	    logger.endTimer("class: AmzProcessDeliveryCancelledEvent | method: getAmazonOrderDetails -- Ends");
	    return getOrderDetailsAmazonOutDoc;
	}

	/**
	 * @param env
	 * @param inDoc
	 * @param extnAmazonDeliveryID
	 * @param eleDetails
	 * @param getOrderDetailsAmzonOutDoc
	 * @return
	 * @throws Exception
	 * The processLineItem method processes a specific line item in the context of a delivery cancellation event. 
	 * It validates and retrieves information related to the line item using its alias (lineItemId) 
	 * and makes an API call to retrieve the associated order line details. 
	 */
	private Document processLineItem(YFSEnvironment env, Document inDoc, String extnAmazonDeliveryID, Element eleDetails,Document getOrderDetailsAmzonOutDoc) throws Exception {
		logger.beginTimer("class: AmzProcessDeliveryCancelledEvent | method: processLineItem -- Starts");
		logger.info("class: AmzProcessDeliveryCancelledEvent | method: processLineItem -- Starts");
		String lineItemId = SCXmlUtil.getXpathAttribute(eleDetails, AmzLiterals.XPATH_LINE_ITEM_ID);
		logger.debug("class: AmzProcessDeliveryCancelledEvent | method: processLineItem | lineItemId :: " +lineItemId);
	    
		if (YFCObject.isVoid(lineItemId)) {
	        YFSException yfse = new YFSException();
			yfse.setErrorCode(AmzLiterals.STR_DELIV_CANCEL_ERR_CODE +"ALIAS_ITEM_MISSING");
			yfse.setErrorDescription("ExtnAmazonLineItemAlias is empty for the Delivery id :" + extnAmazonDeliveryID);
			prepareAndLogResponse(AmzLiterals.STR_ERROR, null, inDoc, yfse.getErrorDescription());
			throw yfse;
		}

	    Document getOrderLineListInDoc = SCXmlUtil.createDocument(AmzLiterals.E_ORDER_LINE);
	   logger.debug("class: AmzProcessDeliveryCancelledEvent | method: processLineItem | getOrderLineListInDoc :: " +SCXmlUtil.getString(getOrderLineListInDoc));
	    Element eleOrderLineExtn = SCXmlUtil.createChild(getOrderLineListInDoc.getDocumentElement(), AmzLiterals.E_EXTN);
	    eleOrderLineExtn.setAttribute(AmzLiterals.A_EXTN_AMZ_LINE_ITEM_ALIAS, lineItemId);

	    Document getOrderLineListTemp = SCXmlUtil.createFromString(AmzLiterals.TEMP_ORDER_LINE_LIST);
	    Document getOrderLineListOutDoc = AmzCommonUtil.callAPI(env, getOrderLineListInDoc, AmzCommonConstants.API_GET_ORDER_LINE_LIST, getOrderLineListTemp);
	    logger.debug("class: AmzProcessDeliveryCancelledEvent | method: processLineItem | getOrderLineListOutDoc :: " +SCXmlUtil.getString(getOrderLineListOutDoc));

	    NodeList nlOrderLineList = getOrderLineListOutDoc.getDocumentElement().getElementsByTagName("OrderLine");
	    
	    if (nlOrderLineList.getLength() == 0) {
	    	YFSException yfse = new YFSException();
			yfse.setErrorCode(AmzLiterals.STR_DELIV_CANCEL_ERR_CODE +"OL_LIST_EMPTY");
			yfse.setErrorDescription("Unable to get OrderLineList information for ExtnAmazonLineItemAlias" +lineItemId);
			prepareAndLogResponse(AmzLiterals.STR_ERROR, null, inDoc, yfse.getErrorDescription());
			throw yfse;
	    }
	    Element eleGetOrderLineListOp=getOrderLineListOutDoc.getDocumentElement();
	    String extnOrderCountry = SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp,AmzLiterals.XPATH_EXTN_COUNTRY_CODE);
	    String entepriseCode = SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp,AmzLiterals.XPATH_ENTERPRISE_CODE);  
	    Document propertyDoc = SCXmlUtil.createDocument(AmzLiterals.E_PROPERTIES);
		propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, entepriseCode);
		genricPropertiesMap = AmzGetGenericProperty.getGenericProperties(env , propertyDoc);
		 strShipNode = genricPropertiesMap.get(AmzCommonConstants.PROP_AMZ_SHIP_NODE+ extnOrderCountry);
		logger.debug("shipNode: "+ strShipNode);
	    NodeList nlOrderStatuesList = AmzXMLUtil.getXpathNodes(eleGetOrderLineListOp,"/OrderLineList/OrderLine/OrderStatuses/OrderStatus[@ShipNode='" + strShipNode + "']");
		ArrayList<String> orderLineKeyList = new ArrayList<String>();
		for (int k = 0; k < nlOrderStatuesList.getLength(); k++) {
			Element eleOrderStatus = (Element) nlOrderStatuesList.item(k);
			String orderLineKey = eleOrderStatus.getAttribute(AmzLiterals.A_ORDER_LINE_KEY);
			if(!orderLineKeyList.contains(orderLineKey)) {
			orderLineKeyList.add(orderLineKey);
			}
		}
		if (!YFCObject.isVoid(orderLineKeyList) && orderLineKeyList.size() > 1) {
	 			    YFSException yfse = new YFSException();
			String message = "Order has multi releases with same Ship Node "+strShipNode+". OrderReleaseKeys are:";
			for (int i = 0; i < orderLineKeyList.size(); i++) {
				message = message.concat(orderLineKeyList.get(i)+ " ");
			}
			yfse.setErrorCode(AmzLiterals.STR_DELIV_CANCEL_ERR_CODE +"MULTI_RELEASE");
			yfse.setErrorDescription(message);
	 				prepareAndLogResponse(AmzLiterals.STR_ERROR, null, inDoc, yfse.getErrorDescription());
	 				throw yfse;
	 		}
	 		 		
	   logger.debug("class: AmzProcessDeliveryCancelledEvent | method: processLineItem | getOrderLineListOutDoc :: " +SCXmlUtil.getString(getOrderLineListOutDoc));
	    logger.info("class: AmzProcessDeliveryCancelledEvent | method: processLineItem -- Ends");
	    logger.endTimer("class: AmzProcessDeliveryCancelledEvent | method: processLineItem -- Ends");
	    return prepareInputAndInvokeOrderApis(env, inDoc, getOrderDetailsAmzonOutDoc, getOrderLineListOutDoc);
	}

	
	/**
	 * @param env
	 * @param getShipContainerListoutDoc
	 * @return
	 * @throws Exception
	 * The updateShipmentAndOrderLines method updates the shipment status based on the provided shipment container list.
	 * and invokes the invokeChangeShipmentStatus method to change the shipment status
	 */
	private Document updateShipmentAndOrderLines(YFSEnvironment env, Document getShipContainerListoutDoc) throws Exception {
		logger.beginTimer("class: AmzProcessDeliveryCancelledEvent | method: handleShipmentContainer -- Starts");
		logger.info("class: AmzProcessDeliveryCancelledEvent | method: handleShipmentContainer -- Starts");
		String strShipmentKey = SCXmlUtil.getXpathAttribute(getShipContainerListoutDoc.getDocumentElement(), AmzLiterals.XPATH_SHIPMENT_KEY);
	    Document changeShipStatusInput = SCXmlUtil.createDocument(AmzLiterals.STR_SHIPMENT);
	    changeShipStatusInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_SHIPMENT_KEY, strShipmentKey);
	   logger.debug("class: AmzProcessDeliveryCancelledEvent | method: handleShipmentContainer | changeShipStatusInput :: " +SCXmlUtil.getString(changeShipStatusInput));
	    logger.info("class: AmzProcessDeliveryCancelledEvent | method: handleShipmentContainer -- Ends");
	    logger.endTimer("class: AmzProcessDeliveryCancelledEvent | method: handleShipmentContainer -- Ends");
		
	    return invokeChangeShipmentStatus(env, changeShipStatusInput);
	}

	/**
	 * @param env
	 * @param inDoc
	 * @param eleGetOrderLineListOp
	 * @param strOrderReleaseKey
	 * @return
	 * @throws Exception
	 * The prepareChangeReleaseInDoc method creates and prepares a new XML document for invoking the "Change Release" API
	 */
	private Document prepareChangeReleaseInDoc(YFSEnvironment env, Document inDoc,Element eleGetOrderLineListOp,String strOrderReleaseKey) throws Exception {
		logger.beginTimer("class: AmzProcessDeliveryCancelledEvent | method: prepareChangeReleaseInDoc -- Starts");
		logger.info("class: AmzProcessDeliveryCancelledEvent | method: prepareChangeReleaseInDoc -- Starts");
		
		Document changeReleaseInDoc = SCXmlUtil.createDocument(AmzLiterals.E_ORDER_RELEASE);
		Element eleChangeReleaseInDoc = changeReleaseInDoc.getDocumentElement();
		eleChangeReleaseInDoc.setAttribute(AmzLiterals.A_OVERRIDE, AmzLiterals.STR_VAL_Y);
		eleChangeReleaseInDoc.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY,
		SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_SALES_ORDER_HEADER_KEY));
		eleChangeReleaseInDoc.setAttribute(AmzLiterals.A_ORDER_RELEASE_KEY, strOrderReleaseKey);
		eleChangeReleaseInDoc.setAttribute(AmzLiterals.A_SELECT_METHOD,AmzCommonConstants.STR_WAIT);
		SCXmlUtil.createChild(eleChangeReleaseInDoc, AmzLiterals.E_ORDER_LINES);
		logger.debug("class: AmzProcessDeliveryCancelledEvent | method: prepareChangeReleaseInDoc | changeReleaseInDoc :: " +SCXmlUtil.getString(changeReleaseInDoc));
		logger.info("class: AmzProcessDeliveryCancelledEvent | method: prepareChangeReleaseInDoc -- Ends");
		logger.endTimer("class: AmzProcessDeliveryCancelledEvent | method: prepareChangeReleaseInDoc -- Ends");
		return changeReleaseInDoc;
		}


	/**
	 * @param eleGetOrderLineListOp
	 * @return
	 * The prepareChangeOrderInDoc method creates and prepares a new XML document for invoking the "Change Order" API
	 */
	private Document prepareChangeOrderInDoc(Element eleGetOrderLineListOp) {
		logger.beginTimer("class: AmzProcessDeliveryCancelledEvent | method: prepareChangeOrderInDoc -- Starts");
		logger.info("class: AmzProcessDeliveryCancelledEvent | method: prepareChangeOrderInDoc -- Starts");
		
		Document changeOrderInDoc= SCXmlUtil.createDocument(AmzLiterals.E_ORDER);
		Element elechangeOrderInDoc = changeOrderInDoc.getDocumentElement();
		elechangeOrderInDoc.setAttribute(AmzLiterals.A_OVERRIDE,AmzLiterals.STR_VAL_Y);
		elechangeOrderInDoc.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY,
				SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_SALES_ORDER_HEADER_KEY));
		elechangeOrderInDoc.setAttribute(AmzLiterals.A_ORDER_NO,
				SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_SALES_ORDER_NO));
		elechangeOrderInDoc.setAttribute(AmzLiterals.ATTR_DOCUMENT_TYPE,
				SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_DOCUEMENT_TYPE));
		elechangeOrderInDoc.setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,
				SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_ENTERPRISE_CODE));
		elechangeOrderInDoc.setAttribute(AmzLiterals.A_SELECT_METHOD,AmzCommonConstants.STR_WAIT);
		SCXmlUtil.createChild(elechangeOrderInDoc, AmzLiterals.E_ORDER_LINES);
		logger.debug("class: AmzProcessDeliveryCancelledEvent | method: prepareChangeOrderInDoc |changeOrderInDoc :: " +SCXmlUtil.getString(changeOrderInDoc));
		logger.info("class: AmzProcessDeliveryCancelledEvent | method: prepareChangeOrderInDoc -- Ends");
		logger.endTimer("class: AmzProcessDeliveryCancelledEvent | method: prepareChangeOrderInDoc -- Ends");
		return changeOrderInDoc;
		
	}

		/**
		 * @param env
		 * @param inDoc
		 * @param getOrderDetailsAmzonOutDoc
		 * @param getOrderLineListOutDoc
		 * @return
		 * @throws Exception 
		 * Method to prepare ChangeOrder & ChangeRelease document and call processOrderLineItems to append orderlines
		 *
		 */
		public Document prepareInputAndInvokeOrderApis(YFSEnvironment env, Document inDoc, Document getOrderDetailsAmzonOutDoc,Document getOrderLineListOutDoc) throws Exception {
			logger.beginTimer("class: AmzProcessDeliveryCancelledEvent | method: prepareInputAndInvokeOrderApis -- Starts");
			logger.info("class: AmzProcessDeliveryCancelledEvent | method: prepareInputAndInvokeOrderApis -- Starts");
			Document returnOutDoc=null;
			Document changeOrderOutDoc = null;
			Document changeReleaseOutDoc = null;
			String extnAmazonDeliveryID = SCXmlUtil.getXpathAttribute(inDoc.getDocumentElement(), AmzLiterals.XPATH_AMZ_DELIVERY_ID);

			// Extract package information
			Element eleAmazonGetOrderDetail = getOrderDetailsAmzonOutDoc.getDocumentElement();
			Element elePackageInformation = SCXmlUtil.getXpathElement(eleAmazonGetOrderDetail, AmzLiterals.XPATH_AMZ_PACKAGE_INFO);
			Element eleMatchingPckgDetails = AmzXMLUtil.getXpathElement(elePackageInformation, "//packageInformation/details[@id='" + extnAmazonDeliveryID + "']");
			String strState = eleMatchingPckgDetails.getAttribute(AmzLiterals.STR_STATE);
			if (strState.equalsIgnoreCase(AmzLiterals.STR_CANCELLED)) {
				String strReason = eleMatchingPckgDetails.getAttribute("reason");
				Document changeOrderInDoc = prepareChangeOrderInDoc(getOrderLineListOutDoc.getDocumentElement());
				String strOrderReleaseKey =SCXmlUtil.getXpathAttribute(getOrderLineListOutDoc.getDocumentElement(),"/OrderLineList/OrderLine/OrderStatuses/OrderStatus[@ShipNode='" + strShipNode + "']/@OrderReleaseKey");
				logger.debug("class: AmzProcessDeliveryCancelledEvent | method: prepareInputAndInvokeOrderApis | strOrderReleaseKey ::" +strOrderReleaseKey);
				Document changeReleaseInDoc = prepareChangeReleaseInDoc(env,inDoc,getOrderLineListOutDoc.getDocumentElement(),strOrderReleaseKey);

				NodeList cancelPackageLineItemsList = AmzXMLUtil.getXpathNodes(eleMatchingPckgDetails, AmzLiterals.XPATH_AMZ_LINE_ITEMS);
				processOrderLineItems(env,cancelPackageLineItemsList, changeOrderInDoc, changeReleaseInDoc, getOrderLineListOutDoc, strReason, extnAmazonDeliveryID);

				if (hasOrderLines(changeOrderInDoc)) {
					logger.debug("class: AmzProcessDeliveryCancelledEvent | method: prepareInputAndInvokeOrderApis | changeOrderInDoc :: " +SCXmlUtil.getString(changeOrderInDoc));
					   
					 changeOrderOutDoc= callChangeOrderAPI(env, changeOrderInDoc);
					 returnOutDoc=changeOrderOutDoc;
					 logger.debug("class: AmzProcessDeliveryCancelledEvent | method: prepareInputAndInvokeOrderApis | changeOrderOutDoc :: " +SCXmlUtil.getString(changeOrderOutDoc));
				}

				if (hasOrderLines(changeReleaseInDoc)) {
					logger.debug("class: AmzProcessDeliveryCancelledEvent | method: prepareInputAndInvokeOrderApis | changeReleaseInDoc :: " +SCXmlUtil.getString(changeReleaseInDoc));
					 changeReleaseOutDoc = callChangeReleaseAPI(env, changeReleaseInDoc);
					 returnOutDoc=changeReleaseOutDoc;
					logger.debug("class: AmzProcessDeliveryCancelledEvent | method: prepareInputAndInvokeOrderApis | changeReleaseOutDoc :: " +SCXmlUtil.getString(changeReleaseOutDoc));
						
				}
			}
			else {
				YFSException yfse = new YFSException();
				yfse.setErrorCode(AmzLiterals.STR_DELIV_CANCEL_ERR_CODE+"AMZ_ORDER_STATUS");
				yfse.setErrorDescription("Amazon Order is not in Cancelled Status for the Delivery id :" + extnAmazonDeliveryID);
				prepareAndLogResponse(AmzLiterals.STR_ERROR, null, inDoc, yfse.getErrorDescription());
				throw yfse;
			
			}
			logger.info("class: AmzProcessDeliveryCancelledEvent | method: prepareInputAndInvokeOrderApis -- Ends");
			logger.endTimer("class: AmzProcessDeliveryCancelledEvent | method: prepareInputAndInvokeOrderApis -- Ends");
		
			return returnOutDoc;
			
		}

		
		
		/**
		 * @param env
		 * @param cancelPackageLineItemsList
		 * @param changeOrderInDoc
		 * @param changeReleaseInDoc
		 * @param getOrderLineListOutDoc
		 * @param strReason
		 * @param extnAmazonDeliveryID
		 * @throws Exception 
		 * Method to validate OrderLine item b/w amazon order and OMS order and prepare Input request
		 */
		private void processOrderLineItems(YFSEnvironment env,NodeList cancelPackageLineItemsList, Document changeOrderInDoc, Document changeReleaseInDoc,
			Document getOrderLineListOutDoc, String strReason, String extnAmazonDeliveryID) throws Exception {
			logger.beginTimer("class: AmzProcessDeliveryCancelledEvent | method: processOrderLineItems -- Starts");
		    logger.info("class: AmzProcessDeliveryCancelledEvent | method: processOrderLineItems -- Starts");
		
			Element eleOrderLines = (Element) changeOrderInDoc.getDocumentElement().getElementsByTagName("OrderLines").item(0);
			Element eleChangeReleaseOrderLines = (Element) changeReleaseInDoc.getDocumentElement().getElementsByTagName("OrderLines").item(0);
			Element eleGetOrderLineList=getOrderLineListOutDoc.getDocumentElement();
			
			
			// Start : get value of amzConn.MCF.BackOrderCancelledLine
				String strBackOrderCancelledValue = genricPropertiesMap.get(AmzCommonConstants.STR_AMZCONN_MCF_BACKORDERCANCELLEDLINE);
				logger.debug("class: AmzProcessDeliveryCancelledEvent | method: processOrderLineItems | strBackOrderCancelledValue ::" + strBackOrderCancelledValue);
				// End: get value of amzConn.MCF.BackOrderCancelledLine
			
			for (int i = 0; i < cancelPackageLineItemsList.getLength(); i++) {
				Element eleOrderLine = null;
				Element eleChangeReleaseLine = null;
				
				Element eleOrderLineItem = (Element) cancelPackageLineItemsList.item(i);
				String strLineItemID = SCXmlUtil.getXpathAttribute(eleOrderLineItem, AmzLiterals.XPATH_LINEITEM_ID);
				String strAmountValue = SCXmlUtil.getXpathAttribute(eleOrderLineItem, AmzLiterals.XPATH_AMOUNT_VALUE);
				
				Element eleGetOrdeLineDetail= AmzXMLUtil.getXpathElement(eleGetOrderLineList, "/OrderLineList/OrderLine/Order/OrderLines/OrderLine/Extn[@ExtnAmazonLineItemAlias='" + strLineItemID + "']/..");
				logger.debug("class: AmzProcessDeliveryCancelledEvent | method: processOrderLineItems | eleGetOrdeLineDetail" +SCXmlUtil.getString(eleGetOrdeLineDetail));
				Element eleGetOrdeLineDetailExtn=SCXmlUtil.getChildElement(eleGetOrdeLineDetail,	AmzLiterals.E_EXTN);
				logger.debug("class: AmzProcessDeliveryCancelledEvent | method: processOrderLineItems |eleGetOrdeLineDetailExtn" +SCXmlUtil.getString(eleGetOrdeLineDetailExtn));
				
				Element eleGetOrdeLineDetailItem=SCXmlUtil.getChildElement(eleGetOrdeLineDetail,	"Item");
				logger.debug("class: AmzProcessDeliveryCancelledEvent | method: processOrderLineItems|eleGetOrdeLineDetailItem" +SCXmlUtil.getString(eleGetOrdeLineDetailItem));
				
				String strOrderLineKey =eleGetOrdeLineDetail.getAttribute(AmzLiterals.A_ORDER_LINE_KEY);
				String strOrderLineQty =eleGetOrdeLineDetail.getAttribute(AmzLiterals.A_ORDERED_QTY);
				String strOrderLineMaxLineStatus =eleGetOrdeLineDetail.getAttribute("MaxLineStatus");
				String strOrderLineMinLineStatus =eleGetOrdeLineDetail.getAttribute("MinLineStatus");
				String strPrimeLineNo =eleGetOrdeLineDetail.getAttribute(AmzLiterals.A_PRIME_LINE_NO);
				String strItemId =eleGetOrdeLineDetailItem.getAttribute(AmzLiterals.A_ITEM_ID);
				String strExtnIsAmazonFulfillable =eleGetOrdeLineDetailExtn.getAttribute(AmzLiterals.A_EXTN_IS_AMAZON_FULFILLABLE);
				String strExtnIsPrimeEligible =eleGetOrdeLineDetailExtn.getAttribute(AmzLiterals.A_EXTN_IS_PRIME_ELIGIBLE);
				
				boolean isPrimeEligible = !YFCObject.isVoid(strExtnIsPrimeEligible) && AmzLiterals.STR_VAL_Y.equalsIgnoreCase(strExtnIsPrimeEligible);
				boolean isAmazonFulFillable = !YFCObject.isVoid(strExtnIsAmazonFulfillable) && AmzLiterals.STR_VAL_Y.equalsIgnoreCase(strExtnIsAmazonFulfillable);
				boolean isBackOrderCancelOn = !YFCObject.isVoid(strBackOrderCancelledValue) && AmzLiterals.STR_VAL_Y.equalsIgnoreCase(strBackOrderCancelledValue);
				boolean isCustomerRequested = !YFCObject.isVoid(strReason) && "CUSTOMER_REQUESTED".equalsIgnoreCase(strReason);
				
				if ((isPrimeEligible && isAmazonFulFillable)|| (!isPrimeEligible && isAmazonFulFillable && isCustomerRequested)) {
					
					if (strOrderLineMaxLineStatus.equalsIgnoreCase("3200")) {
						eleChangeReleaseLine = SCXmlUtil.createChild(eleChangeReleaseOrderLines, AmzLiterals.E_ORDER_LINE);
						eleChangeReleaseLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, strOrderLineKey);
						 eleChangeReleaseLine.setAttribute(AmzLiterals.A_ACTION, AmzLiterals.STR_CANCEL);
						 //qty is  cancelled in amazon order
						 if (!YFCObject.isVoid(strAmountValue)) {
						     eleChangeReleaseLine.setAttribute(AmzLiterals.A_CHANGE_IN_QTY, String.format("%.2f", -Math.abs(Double.parseDouble(strAmountValue))));
						     eleChangeReleaseLine.setAttribute(AmzLiterals.A_SHIP_NODE, strShipNode);
						    }
						 else {
							 eleChangeReleaseLine.setAttribute(AmzLiterals.A_CHANGE_IN_QTY, String.format("%.2f", -Math.abs(Double.parseDouble(strOrderLineQty))));
							 eleChangeReleaseLine.setAttribute(AmzLiterals.A_SHIP_NODE,strShipNode );
						 }
					    } else {
					
					eleOrderLine = SCXmlUtil.createChild(eleOrderLines, AmzLiterals.E_ORDER_LINE);
					processOrderLine(changeOrderInDoc, eleOrderLine, strOrderLineKey,strAmountValue, strOrderLineQty, strReason, extnAmazonDeliveryID,strPrimeLineNo,strItemId);
					}
				}else if ((isPrimeEligible && isAmazonFulFillable)|| (!isPrimeEligible && isAmazonFulFillable && !isBackOrderCancelOn )) {
					eleOrderLine = SCXmlUtil.createChild(eleOrderLines, AmzLiterals.E_ORDER_LINE);
					processOrderLine(changeOrderInDoc, eleOrderLine, strOrderLineKey,strAmountValue, strOrderLineQty, strReason, extnAmazonDeliveryID,strPrimeLineNo,strItemId);
				}
				else if (!isPrimeEligible && isAmazonFulFillable && isBackOrderCancelOn ) {
					if (strOrderLineMaxLineStatus.equalsIgnoreCase("3200") || (strOrderLineMaxLineStatus.equalsIgnoreCase("3700") && strOrderLineMinLineStatus.equalsIgnoreCase("3200"))) {
						eleChangeReleaseLine = SCXmlUtil.createChild(eleChangeReleaseOrderLines, AmzLiterals.E_ORDER_LINE);
						eleChangeReleaseLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, strOrderLineKey);
						 eleChangeReleaseLine.setAttribute(AmzLiterals.A_ACTION, AmzLiterals.STR_BACKORDER);
						 //qty is  cancelled in amazon order
						 if (!YFCObject.isVoid(strAmountValue)) {
						     //eleChangeReleaseLine.setAttribute(AmzLiterals.A_ORDERED_QTY, strAmountValue);
							 eleChangeReleaseLine.setAttribute(AmzLiterals.A_CHANGE_IN_QTY, String.format("%.2f", -Math.abs(Double.parseDouble(strAmountValue))));
						     eleChangeReleaseLine.setAttribute(AmzLiterals.A_SHIP_NODE, strShipNode);
						    }
						 else {
							 eleChangeReleaseLine.setAttribute(AmzLiterals.A_ORDERED_QTY, strOrderLineQty);
							 eleChangeReleaseLine.setAttribute(AmzLiterals.A_SHIP_NODE, strShipNode);
						 }
					    eleOrderLine = SCXmlUtil.createChild(eleOrderLines, AmzLiterals.E_ORDER_LINE);
						processOrderLineSourcingControls(env,changeOrderInDoc, eleOrderLine, strOrderLineKey, strShipNode, extnAmazonDeliveryID,strReason);
					
					} else {
						eleOrderLine = SCXmlUtil.createChild(eleOrderLines, AmzLiterals.E_ORDER_LINE);
						processOrderLineSourcingControls(env,changeOrderInDoc, eleOrderLine, strOrderLineKey, strShipNode, extnAmazonDeliveryID,strReason);
					} 
				}
			}
			logger.info("class: AmzProcessDeliveryCancelledEvent | method: processOrderLineItems -- Ends");
			logger.endTimer("class: AmzProcessDeliveryCancelledEvent | method: processOrderLineItems -- Ends");
			
			}

			
		    private Document callChangeOrderAPI(YFSEnvironment env, Document changeOrderInDoc) throws YFSException, RemoteException, YIFClientCreationException {
		    	//return AmzCommonUtil.callAPI(env, changeOrderInDoc, AmzCommonConstants.API_CHANGE_ORDER, null);
		    	return AmzCommonUtil.invokeService(env, AmzCommonConstants.SERVICE_AMZ_CHANGE_ORDER_SERVICE, changeOrderInDoc);
		    }

		    private Document callChangeReleaseAPI(YFSEnvironment env, Document changeReleaseInDoc) throws YFSException, RemoteException, YIFClientCreationException {
		    	//return AmzCommonUtil.callAPI(env, changeReleaseInDoc, AmzCommonConstants.API_CHANGE_RELEASE, null);
		    	return AmzCommonUtil.invokeService(env,  AmzCommonConstants.SERVICE_AMZ_CHANGE_RELEASE, changeReleaseInDoc);
		    }
	
	

	/**
	 * @param orderInDoc
	 * @return
	 * Method to validate if outIndoc has orderline information
	 */
	private boolean hasOrderLines(Document orderInDoc) {
	    return orderInDoc.getElementsByTagName(AmzLiterals.E_ORDER_LINE).getLength() > 0 &&
			    (orderInDoc.getElementsByTagName(AmzLiterals.E_ORDER_LINE).item(0)).hasAttributes();
	}

	
	/**
	 * @param env
	 * @param inDoc
	 * @return
	 * @throws YIFClientCreationException 
	 * @throws RemoteException 
	 * @throws Exception
	 * The invokeChangeShipmentStatus method is used to change the status of a shipment. 
	 * It modifies the input shipment document by adding relevant status and transaction information, 
	 * then calls the API to update the shipment status and returns the response document.
	 * BaseDropStatus= 1400.300 and TransactionId= AMZCONN_DELIVER_SHIPMENT.0001.ex
	 */
	public Document invokeChangeShipmentStatus(YFSEnvironment env, Document inDoc) throws RemoteException, YIFClientCreationException {
		logger.beginTimer("class: AmzProcessDeliveryCancelledEvent | method: invokeChangeShipmentStatus -- Starts");
		logger.info("class: AmzProcessDeliveryCancelledEvent | method: invokeChangeShipmentStatus -- Starts");
		
		Document changeShipmentOutDoc=null;
		Element eleShipment = inDoc.getDocumentElement();
		String shipmentBaseDropStatus = props.getProperty(AmzLiterals.A_SHIPMENT_BASE_DROP_STATUS);
		String shipmentTransactionId = props.getProperty(AmzLiterals.A_SHIPMENT_TRANSACTION_ID);
		eleShipment.setAttribute(AmzLiterals.A_SHIPMENT_BASE_DROP_STATUS, shipmentBaseDropStatus);
		eleShipment.setAttribute(AmzLiterals.A_SHIPMENT_TRANSACTION_ID, shipmentTransactionId);
		logger.debug("class: AmzProcessDeliveryCancelledEvent | method: invokeChangeShipmentStatus.changeShipmentStatusInDoc is: " + SCXmlUtil.getString(inDoc));
		Document tempchangeShipmentStatus = SCXmlUtil.createFromString(AmzLiterals.TEMP_SHIPMENT);
		changeShipmentOutDoc = AmzCommonUtil.callAPI(env, inDoc, AmzCommonConstants.API_CHANGE_SHIPMENT_STATUS, tempchangeShipmentStatus);
		logger.debug("class: AmzProcessDeliveryCancelledEvent | method: invokeChangeShipmentStatus.changeShipmentStatusOutDoc is: " + SCXmlUtil.getString(changeShipmentOutDoc));
		logger.info("class: AmzProcessDeliveryCancelledEvent | method: invokeChangeShipmentStatus -- Ends");
		logger.endTimer("class: AmzProcessDeliveryCancelledEvent | method: invokeChangeShipmentStatus -- Ends");
		return changeShipmentOutDoc;
		
	}

	@Override
	public void setProperties(Properties inProps) {
		this.props = inProps;
	}
	
   /**
	 * @param processStatus
	 * @param apiOutput
	 * @param inDoc
	 * @param message
	 * The prepareAndLogResponse method is designed to prepare and log the response after processing a delivery cancellation event.
	 * It generates a log document that includes essential information like Amazon delivery ID, order ID, event type, process status, and shipment details.
	 * The method also logs a message,which may either be a success or an error message based on the process status.
	 */
	public void prepareAndLogResponse(String processStatus,Document apiOutput, Document inDoc, String message) {
		logger.beginTimer("class: AmzProcessDeliveryCancelledEvent | method: prepareAndLogResponse -- Starts");
		logger.info("class: AmzProcessDeliveryCancelledEvent | method: prepareAndLogResponse -- Starts");
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
		logger.info("class: AmzProcessDeliveryCancelledEvent | method: prepareAndLogResponse -- Ends");
		logger.endTimer("class: AmzProcessDeliveryCancelledEvent | method: prepareAndLogResponse -- Ends");
	
	}

     /**
	 * @param changeOrderInDoc
	 * @param eleOrderLine
	 * @param strOrderLineKey
	 * @param strAmountValue
	 * @param strOrderLineQty
	 * @param strReason
	 * @param extnAmazonDeliveryID
	 * The method processes an order line by adjusting its quantity based on the cancellation amount.
	 */
	
    
	public void processOrderLine(Document changeOrderInDoc,Element eleOrderLine, String strOrderLineKey, String strAmountValue, String strOrderLineQty, String strReason, 
			String extnAmazonDeliveryID, String strPrimeLineNo, String itemID) {
		logger.beginTimer("class: AmzProcessDeliveryCancelledEvent | method: processOrderLine -- Starts");
		logger.info("class: AmzProcessDeliveryCancelledEvent | method: processOrderLine -- Starts");
			
		eleOrderLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, strOrderLineKey);	    
	    // Cancel Quantity logic
	    double dOrderLineQty = Double.parseDouble(strOrderLineQty);
        double dAmazonAmountValue = Double.parseDouble(strAmountValue);
        double dRemainingQty = dOrderLineQty - dAmazonAmountValue;
        String strOrderQty=Double.toString(dRemainingQty);
	    if (dRemainingQty == 0) {
	        eleOrderLine.setAttribute(AmzLiterals.A_ACTION, AmzLiterals.STR_CANCEL);
	      // Add cancellation notes
	 	    addCancellationNotes(changeOrderInDoc,eleOrderLine, strReason, extnAmazonDeliveryID,dOrderLineQty,strPrimeLineNo, itemID,"FULL");
	 	   
	    }else if (dRemainingQty>0){	    	
	    	 eleOrderLine.setAttribute(AmzLiterals.A_ORDERED_QTY, strOrderQty);
	    	// Add cancellation notes
	 	    addCancellationNotes(changeOrderInDoc,eleOrderLine, strReason, extnAmazonDeliveryID,dAmazonAmountValue,strPrimeLineNo, itemID,"PARTIAL");
	 	       
	    }

	     logger.endTimer("class: AmzProcessDeliveryCancelledEvent | method: processOrderLine -- Ends");
		logger.info("class: AmzProcessDeliveryCancelledEvent | method: processOrderLine -- Ends");
		
	}
	
	/**
	 * @param changeOrderInDoc
	 * @param eleOrderLine
	 * @param strReason
	 * @param extnAmazonDeliveryID
	 * The addCancellationNotes method is used to add cancellation notes to an order line in the changeOrderInDoc document. 
	 * it appends a Note element to the OrderLine element with specific cancellation details. The cancellation notes contain a reason code,
	 *  description, and an associated Amazon delivery ID.
	 */
	public void addCancellationNotes(Document changeOrderInDoc,Element eleOrderLine, String strReason, String extnAmazonDeliveryID,double dRemainingQty,String strPrimeLineNo, String itemID, String Status) {
		logger.beginTimer("class: AmzProcessDeliveryCancelledEvent | method: addCancellationNotes -- Starts");
		logger.info("class: AmzProcessDeliveryCancelledEvent | method: addCancellationNotes -- Starts");
		
		Element eleOLineNotes = changeOrderInDoc.createElement(AmzLiterals.E_NOTES);
	    Element eleOLineNote = changeOrderInDoc.createElement(AmzLiterals.E_NOTE);
	    eleOLineNote.setAttribute(AmzLiterals.A_REASON_CODE, strReason);
	    if("PARTIAL".equalsIgnoreCase(Status)) {
	      eleOLineNote.setAttribute(AmzLiterals.A_NOTE_TEXT, "Order Line with PrimeLine = " +strPrimeLineNo + " and ItemID= " +itemID+" ,is partly CANCELLED for Qty " +dRemainingQty +", as part of processing DELIVERY_CANCELLED for DeliveryId " + extnAmazonDeliveryID + " event from Amazon with reason code " + strReason);
	    }
	    else {
	    	 eleOLineNote.setAttribute(AmzLiterals.A_NOTE_TEXT, "Order Line with PrimeLine = " +strPrimeLineNo + " and ItemID= "+itemID+" ,is fully CANCELLED for Qty " +dRemainingQty +", as part of processing DELIVERY_CANCELLED for DeliveryId " + extnAmazonDeliveryID + " event from Amazon with reason code " + strReason);
	    }
	    eleOLineNotes.appendChild(eleOLineNote);
	    eleOrderLine.appendChild(eleOLineNotes);
	    logger.info("class: AmzProcessDeliveryCancelledEvent | method: addCancellationNotes -- Ends");
	    logger.endTimer("class: AmzProcessDeliveryCancelledEvent | method: addCancellationNotes -- Ends");
		
	    
	}
	
	
	/**
	 * @param changeOrderInDoc
	 * @param eleOrderLine
	 * @param strOrderLineKey
	 * @param strOrderLineStatus
	 * @param strShipNode
	 * @param extnAmazonDeliveryID
	 * The method updates an order line in the provided changeOrderInDoc based on the  other parameters, 
	 * such as the delivery ID, ship node, and order line key.
	 * If the order line's status is "Created" , "Scheduled", "Released"
	 * it adds an "Order Line Sourcing Control" to the order line with specific attributes related to inventory check, 
	 * suppression of sourcing, and a reason for adding the sourcing control.
	 * @throws Exception 
	 */
	public void processOrderLineSourcingControls(YFSEnvironment env,Document changeOrderInDoc,Element eleOrderLine, String strOrderLineKey, String strShipNode, String extnAmazonDeliveryID, String strReason) throws Exception {
		logger.beginTimer("class: AmzProcessDeliveryCancelledEvent | method: processOrderLineSourcingControls -- Starts");
		logger.info("class: AmzProcessDeliveryCancelledEvent | method: processOrderLineSourcingControls -- Starts");
		eleOrderLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, strOrderLineKey);
		        Element eleOLineExtn = changeOrderInDoc.createElement(AmzLiterals.E_EXTN);
	            eleOLineExtn.setAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID, "");
	            Element eleOrderLineSourcingControls = changeOrderInDoc.createElement(AmzLiterals.E_ORDER_LINE_SOURCING_CONTROLS);
	            Element eleOrderLineSourcingControl = changeOrderInDoc.createElement(AmzLiterals.E_ORDER_LINE_SOURCING_CNTRL);
	            eleOrderLineSourcingControl.setAttribute(AmzLiterals.A_INVENTORY_CHECK_CODE, "NOINV");
	            eleOrderLineSourcingControl.setAttribute(AmzLiterals.A_SUPPRESS_SOURCING, AmzLiterals.STR_VAL_Y);
	            eleOrderLineSourcingControl.setAttribute(AmzLiterals.A_REASON_TEXT, "Added OrderLineSourcingControl as part of processing DELIVERY_CANCELLED for DeliveryId " + extnAmazonDeliveryID + " event from Amazon with reason code" +strReason);
	            eleOrderLineSourcingControl.setAttribute(AmzLiterals.A_NODE, strShipNode);
	            eleOrderLine.appendChild(eleOLineExtn);
	            eleOrderLineSourcingControls.appendChild(eleOrderLineSourcingControl);
	            eleOrderLine.appendChild(eleOrderLineSourcingControls);
	        
        logger.info("class: AmzProcessDeliveryCancelledEvent | method: processOrderLineSourcingControls -- Ends");
		logger.endTimer("class: AmzProcessDeliveryCancelledEvent | method: processOrderLineSourcingControls -- Ends");
	}

}
