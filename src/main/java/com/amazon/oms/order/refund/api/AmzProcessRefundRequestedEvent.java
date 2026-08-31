package com.amazon.oms.order.refund.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
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
import com.yantra.yfc.dom.YFCDocument;
import com.yantra.yfc.dom.YFCElement;
import com.yantra.yfc.dom.YFCNodeList;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/*This class will be invoked by service 'AmzConnRefundRequested' with below input
 * 	<Refund BusinessProductID="bp-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
    EventId="d9a1cba2-0dcc-0b64-8068-d0840af4beb5"
    EventTime="2025-03-28T07:11:17Z" EventType="REFUND_REQUESTED"
    IdempotencyKey="NDBiZTg1ZDMtNjQyZC00ZWY3LTkxMzUtNmJlOGY0ZmZmOTgyI2Q5YTFjYmEyLTBkY2MtMGI2NC04MDY4LWQwODQwYWY0YmViNQ=="
    Resources="businessProduct/bp-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/order/322-R48X-A4JJ99/refund/2adb111a57f92b1d8c2084c739f6b8148cb5f3f21ef1c0ccf072c35ce81f4364" SubscriptionId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx">
    <RefundDetails>
        <RefundDetail AmazonOrderId="322-R48X-A4JJ99" RefundId="2adb111a57f92b1d8c2084c739f6b8148cb5f3f21ef1c0ccf072c35ce81f4364"/>
    </RefundDetails>
	</Refund>
 * */


public class AmzProcessRefundRequestedEvent implements YIFCustomApi {
	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzProcessRefundRequestedEvent.class);
	private Properties props;
	Map<String, String> genricPropertiesMap = new HashMap<>();	
	String strEnterpriseCode=null;
	String strEventType=null;
	String strShipNode=null;
	String strAmzOrderNo=null;
	String strRefundId=null;
	
	
	
	/**
	 * @param env
	 * @param inDoc
	 * @return
	 * Handles the processing of refund requests from Amazon. It validates refund details, retrieves order and refund information,
	 *  and processes the refund based on specific reasons (e.g., "RETURN", "DELIVERY_ISSUES", "CANCELLED_ORDER"). 
	 *  The method logs success or error responses accordingly.
	 */
	public Document processRefundRequestedEvent(YFSEnvironment env, Document inDoc) {
		logger.beginTimer("class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent -- Starts");
		logger.info("class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent -- Starts");
		Document outDoc = null;
		Document getOrderLineListOutDoc = null;
		
		try {
        		Element eleInDoc = inDoc.getDocumentElement();
		    	strEnterpriseCode = AmzEnterpriseBPIDXRefUtil.getEnterpriseCode(env, eleInDoc.getAttribute("BusinessProductID"));
			    strEventType = eleInDoc.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE);
			    NodeList nlRefundDetail = inDoc.getDocumentElement().getElementsByTagName(AmzLiterals.STR_REFUND_DETAIL);
			    for (int k = 0; k < nlRefundDetail.getLength(); k++) {
				   Element eleRefundDetail = (Element) nlRefundDetail.item(k);
				   strAmzOrderNo=eleRefundDetail.getAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID);
				   logger.debug("class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent | strAmzOrderNo : " +strAmzOrderNo);
				   strRefundId=eleRefundDetail.getAttribute(AmzLiterals.ATTR_REFUND_ID);
				   logger.debug("class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent | strRefundId : " +strRefundId);
				   Document getOrderDetailsAmazonOutDoc = getAmazonOrdRefundDetail(env, strAmzOrderNo, strEventType,strEnterpriseCode);
				   logger.debug("class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent |getOrderDetailsAmazonOutDoc : " +SCXmlUtil.getString(getOrderDetailsAmazonOutDoc));
				   Element eleRefundDetailInformation = SCXmlUtil.getXpathElement(getOrderDetailsAmazonOutDoc.getDocumentElement(), "/Root/data/order[@id='" + strAmzOrderNo +"']/refunds/details");
				   logger.debug("class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent |eleRefundDetailInformation : " +SCXmlUtil.getString(eleRefundDetailInformation));
				   Element eleMatchedRefundDetailId = AmzXMLUtil.getXpathElement(eleRefundDetailInformation, "//refunds/details[@id='" + strRefundId + "']");
				   logger.debug("class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent |eleMatchedRefundDetailId " +SCXmlUtil.getString(eleMatchedRefundDetailId));
				   if (YFCObject.isVoid(eleMatchedRefundDetailId)) {
					    	YFSException yfse = new YFSException();
							yfse.setErrorCode(AmzLiterals.STR_REFUND_REQ_ERROR_CODE+"REFUND_ID_MISSING");
							yfse.setErrorDescription("Could not find Refund Id" + strRefundId + " Against Amazon Order details : " +strAmzOrderNo);
							prepareAndLogResponse(AmzLiterals.STR_ERROR, null, strEventType,strAmzOrderNo,strRefundId, yfse.getErrorDescription());
							throw yfse;	
					  }
				   String strLineItemId = SCXmlUtil.getXpathAttribute(eleMatchedRefundDetailId, "refundFor/orderLineItems/lineItem/@id");
				   logger.debug("class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent | lineItemId : " +strLineItemId);
				   if (YFCObject.isVoid(strLineItemId)) {
					     YFSException yfse = new YFSException();
						 yfse.setErrorCode(AmzLiterals.STR_REFUND_REQ_ERROR_CODE +"ALIAS_ITEM_MISSING");
						 yfse.setErrorDescription("Amazon LineItemAlias is blank or empty for the Refund id : " + strRefundId +"Amazon Order id : " +strAmzOrderNo);
						 prepareAndLogResponse(AmzLiterals.STR_ERROR, null, strEventType,strAmzOrderNo,strRefundId, yfse.getErrorDescription());
						 throw yfse;
					 }
				   
				   	String strCausedByTypeName = SCXmlUtil.getXpathAttribute(eleMatchedRefundDetailId, "refundFor/causedBy/@__typename");
				   	String strCausedById = SCXmlUtil.getXpathAttribute(eleMatchedRefundDetailId, "refundFor/causedBy/@id");
				   	 String strRefundRequestReason=SCXmlUtil.getXpathAttribute(eleMatchedRefundDetailId, "@refundRequestReason");
				   	 logger.debug( "class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent | strRefundRequestReason : " +strRefundRequestReason);
					 boolean reasonCodeFlag = validateRefundReasonCode(env, strRefundRequestReason);
					 
					 if(!reasonCodeFlag){
	                        strRefundRequestReason = "UNKNOWN";
	                     }
  					 logger.debug( "class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent | reasonCodeFlag : " +reasonCodeFlag);
						
  						
  					
  					if (reasonCodeFlag && "ReturnDetails".equalsIgnoreCase(strCausedByTypeName)) {
						 getOrderLineListOutDoc = getOrderLineListApi(env, strLineItemId,strCausedById,"REFUND");
						 logger.debug("class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent |getOrderLineListOutDoc : " +SCXmlUtil.getString(getOrderLineListOutDoc));
						 NodeList amzOrderLineItemsList = AmzXMLUtil.getXpathNodes(eleMatchedRefundDetailId, "refundFor/orderLineItems");
						 NodeList nlOrderLineList = getOrderLineListOutDoc.getDocumentElement().getElementsByTagName("OrderLine");
						 if (nlOrderLineList.getLength() == 0) {
						    	YFSException yfse = new YFSException();
								yfse.setErrorCode(AmzLiterals.STR_REFUND_REQ_ERROR_CODE +"OL_LIST_EMPTY");
								yfse.setErrorDescription("Unable to get OrderLineList information for ExtnAmazonLineItemAlias/ExtnDerivedFromLineAlias : " +strLineItemId);
								prepareAndLogResponse(AmzLiterals.STR_ERROR, null, strEventType,strAmzOrderNo,strRefundId, yfse.getErrorDescription());
								throw yfse;
						  }
						   
						 boolean isRefundIdAlreadyProcessed=isRefundIdAlreadyProcessed(env,strRefundId,strCausedById);
						 logger.debug("class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent |isRefundIdAlreadyProcessed : " +isRefundIdAlreadyProcessed);
						
						//DROP3- DEFECT No-3 Starts
						  strEnterpriseCode = SCXmlUtil.getXpathAttribute(getOrderLineListOutDoc.getDocumentElement(),AmzLiterals.XPATH_ENTERPRISE_CODE);  
						  Document propertyDoc = SCXmlUtil.createDocument(AmzLiterals.E_PROPERTIES);
			  			  propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);
			  			  genricPropertiesMap = AmzGetGenericProperty.getGenericProperties(env, propertyDoc);
			  		   //DROP3- DEFECT No-3 Ends  
						 if(!isRefundIdAlreadyProcessed && (strEventType.equalsIgnoreCase(genricPropertiesMap.get(AmzCommonConstants.PROP_RETUND_EVENT)))) {
							 outDoc = prepareInputAndReceiveOrder(env, getOrderLineListOutDoc,amzOrderLineItemsList,strAmzOrderNo,strRefundId,strCausedById);
							 prepareAndLogResponse(AmzLiterals.STR_SUCCESS, outDoc, strEventType,strAmzOrderNo,strRefundId, "SUCCESS");
							 
						 }
						 else {
							 prepareAndLogResponse(AmzLiterals.STR_ERROR, null, strEventType,strAmzOrderNo,strRefundId, "RefundId is already processed for the order."); 
						 }
					 }else  {
					//	 else  if (reasonCodeFlag && "CancelDetails".equalsIgnoreCase(strCausedByTypeName)) {
						 getOrderLineListOutDoc = getOrderLineListApi(env, strLineItemId,strEnterpriseCode,"CANCELLED");
						 logger.debug("class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent |getOrderLineListOutDoc : " +SCXmlUtil.getString(getOrderLineListOutDoc));
						 NodeList amzOrderLineItemsList = AmzXMLUtil.getXpathNodes(eleMatchedRefundDetailId, "refundFor/orderLineItems");
						 NodeList nlOrderLineList = getOrderLineListOutDoc.getDocumentElement().getElementsByTagName("OrderLine");
						   
						 if (nlOrderLineList.getLength() == 0) {
						    	YFSException yfse = new YFSException();
								yfse.setErrorCode(AmzLiterals.STR_REFUND_REQ_ERROR_CODE +"OL_LIST_EMPTY");
								yfse.setErrorDescription("Unable to get OrderLineList information for ExtnAmazonLineItemAlias : " +strLineItemId);
								prepareAndLogResponse(AmzLiterals.STR_ERROR, null, strEventType,strAmzOrderNo,strRefundId, yfse.getErrorDescription());
								throw yfse;
						  }
						 
						 //DROP-3 UAT without Invoice Cancellation Defect STARTS
						 String strTotalAmount = SCXmlUtil.getXpathAttribute(eleMatchedRefundDetailId, "refundTotal/totalAmount/@amount");
						 String strCurrencyCode= SCXmlUtil.getXpathAttribute(eleMatchedRefundDetailId, "refundTotal/totalAmount/@currencyCode");
						 logger.debug("class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent | "  + "eleMatchedRefundDetailId refundTotal/totalAmount: " + strTotalAmount
								    + " | eleMatchedRefundDetailId refundTotal/currencyCode: " + strCurrencyCode);
						 //DROP-3 UAT without Invoice Cancellation Defect ENDS
						  	 
						 String strPackageDetailId= getPackageDetailId(env, getOrderLineListOutDoc,getOrderDetailsAmazonOutDoc,strRefundId,strAmzOrderNo,amzOrderLineItemsList);
						 logger.debug("class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent |strPackageDetailId : " +strPackageDetailId);
				/*		 if (!YFCObject.isVoid(strPackageDetailId)) {
							  Document updateDeliveryCanOutDoc=null;
							  Document updateDeliveryCanIndoc = SCXmlUtil.createDocument(AmzLiterals.STR_SHIPMENT);
							  Element eleShipment= updateDeliveryCanIndoc.getDocumentElement();
							  eleShipment.setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE,"PACKAGE_DELIVERY_CANCELLED");
							  eleShipment.setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID,strAmzOrderNo);
							  eleShipment.setAttribute("EventId",eleInDoc.getAttribute("EventId"));
							  eleShipment.setAttribute("EventTime",eleInDoc.getAttribute("EventTime"));
							  Element eleContainers = SCXmlUtil.createChild(eleShipment, AmzLiterals.ELE_CONTAINERS);
							  Element eleContainer = SCXmlUtil.createChild(eleContainers, AmzLiterals.ELE_CONTAINER);
							  eleContainer.setAttribute("ExtnAmazonDeliveryID",strPackageDetailId);
							  logger.debug("class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent |updateDeliveryCanIndoc : " +SCXmlUtil.getString(updateDeliveryCanIndoc));
							  updateDeliveryCanOutDoc= AmzCommonUtil.callService(env, updateDeliveryCanIndoc, AmzCommonConstants.SERVICE_AMAZON_POST_MSGTO_FUFILLMENT_EVENT_Q, null);
							  Thread.sleep(3000);
							  logger.debug("class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent |updateDeliveryCanOutDoc : " +SCXmlUtil.getString(updateDeliveryCanOutDoc));
						 
							 } */
						 //Prepare documents for getOrderInvoice Details API
						 Document getOrderInvoiceListDoc=getOrderInvoiceDetails(env,strRefundId);
						 boolean isOrderInvoiceExists = getOrderInvoiceListDoc.getDocumentElement().hasChildNodes();
						//If Order is already Invoice for refund Id then don't take any action
						 logger.debug("class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent |isOrderInvoiceExists : " +isOrderInvoiceExists);
						 if(!isOrderInvoiceExists) {
							 
						    //outDoc = prepareInputAndGetCreditMemoDetails(env, getOrderLineListOutDoc,strRefundId,strAmzOrderNo,amzOrderLineItemsList);
							//DROP-3 UAT without Invoice Cancellation Defect STARTS
							  outDoc = prepareInputAndGetCreditMemoDetails(env, getOrderLineListOutDoc,strRefundId,strAmzOrderNo,amzOrderLineItemsList,strTotalAmount,strCurrencyCode);
						    //DROP-3 UAT without Invoice Cancellation Defect ENDS
							
						    prepareAndLogResponse(AmzLiterals.STR_SUCCESS, outDoc, strEventType,strAmzOrderNo,strRefundId, "SUCCESS");
							
						 }else {
							 prepareAndLogResponse(AmzLiterals.STR_ERROR, null, strEventType,strAmzOrderNo,strRefundId, "Credit Memo already issued for the Order");
						 }
					 }
					 
				}
				} catch (YFSException e) {
					YFSException yfse = new YFSException();
					yfse.setErrorCode(AmzLiterals.STR_REFUND_REQ_ERROR_CODE+e.getErrorCode());
					yfse.setErrorDescription(e.getMessage());
					prepareAndLogResponse(AmzLiterals.STR_ERROR, outDoc, strEventType,strAmzOrderNo,strRefundId, e.getErrorDescription());
					throw e;
				} catch (Exception e) {
					e.printStackTrace();
					YFSException yfse = new YFSException();
					yfse.setErrorCode(AmzLiterals.STR_REFUND_REQ_ERROR_CODE+"EXCEPTION");
					yfse.setErrorDescription(e.getMessage());
					prepareAndLogResponse(AmzLiterals.STR_ERROR, outDoc, strEventType,strAmzOrderNo,strRefundId, yfse.getErrorDescription());
					throw yfse;
				}
		logger.info("class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent -- Ends");
		logger.endTimer("class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent -- Ends");
		return outDoc;

	}
	
	
		/**
		 * @param env
		 * @param getOrderLineListOutDoc
		 * @param getOrderDetailsAmazonOutDoc
		 * @param strRefundId
		 * @param strAmzOrderNo
		 * @param amzOrderLineItemsList
		 * @return
		 * @throws XPathExpressionException
		 * If order status is not cancelled then take cancellation Package Id
		 */
		public String getPackageDetailId(YFSEnvironment env,Document getOrderLineListOutDoc, Document getOrderDetailsAmazonOutDoc,String strRefundId,String strAmzOrderNo, NodeList amzOrderLineItemsList) throws XPathExpressionException {
		
		logger.beginTimer("class: AmzProcessRefundRequestedEvent | method: getPackageDetailId -- Starts");
		logger.info("class: AmzProcessRefundRequestedEvent | method: getPackageDetailId -- Starts");
		String strPackDetailId =null;
		Element eleGetOrderLineListOp = getOrderLineListOutDoc.getDocumentElement();
		String extnOrderCountry = SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp,AmzLiterals.XPATH_EXTN_COUNTRY_CODE);
	    String entepriseCode = SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp,AmzLiterals.XPATH_ENTERPRISE_CODE);  
	    Document propertyDoc = SCXmlUtil.createDocument(AmzLiterals.E_PROPERTIES);
		propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, entepriseCode);
		genricPropertiesMap = AmzGetGenericProperty.getGenericProperties(env , propertyDoc);
		strShipNode = genricPropertiesMap.get(AmzCommonConstants.PROP_AMZ_SHIP_NODE+ extnOrderCountry);
		logger.debug("class: AmzProcessRefundRequestedEvent | method: getPackageDetailId | shipNode: "+ strShipNode);
		HashSet<String> packageDetailsIds = new HashSet<String>();
		for (int i = 0; i < amzOrderLineItemsList.getLength(); i++) {
			Element eleOrderLineItem = (Element) amzOrderLineItemsList.item(i);
			logger.debug("class: AmzProcessRefundRequestedEvent | method: getPackageDetailId| eleOrderLineItem :" +SCXmlUtil.getString(eleOrderLineItem));
			String strLineItemID = SCXmlUtil.getXpathAttribute(eleOrderLineItem, AmzLiterals.XPATH_AMZ_LINE_ID);
			logger.debug("class: AmzProcessRefundRequestedEvent | method: getPackageDetailId| strLineItemID : " +strLineItemID);
			Element eleGetOrdeLineDetail= AmzXMLUtil.getXpathElement(eleGetOrderLineListOp, "/OrderLineList/OrderLine/Order/OrderLines/OrderLine/Extn[@ExtnAmazonLineItemAlias='" + strLineItemID + "']/..");
			logger.debug("class: AmzProcessRefundRequestedEvent | method: getPackageDetailId | eleGetOrdeLineDetail : " +SCXmlUtil.getString(eleGetOrdeLineDetail));
			String strOrderLineMaxLineStatus =eleGetOrdeLineDetail.getAttribute("MaxLineStatus");
			if(!(AmzCommonConstants.STR_CANCELLED_STATUS.equalsIgnoreCase(strOrderLineMaxLineStatus) || AmzCommonConstants.STR_DELIVERY_CANCELLED_STATUS.equalsIgnoreCase(strOrderLineMaxLineStatus))) 
			{
				Element elePackageInformationDetails = SCXmlUtil.getXpathElement(getOrderDetailsAmazonOutDoc.getDocumentElement(), "/Root/data/order[@id='" +strAmzOrderNo+ "']/packageInformation/details[@state='CANCELLED']");
				Element packageInformationDetailsFor = SCXmlUtil.getXpathElement(elePackageInformationDetails, "packageInformationDetailsFor/orderLineItems/lineItem[@id='"+strLineItemID+"']");
				if(!YFCObject.isVoid(packageInformationDetailsFor)) {
					String strdetailId=elePackageInformationDetails.getAttribute("id");
					packageDetailsIds.add(strdetailId);
				}
				
			}
		}
		Iterator<String> iterator = packageDetailsIds.iterator();
        if (iterator.hasNext()) {
            strPackDetailId = iterator.next();
            logger.debug("class: AmzProcessRefundRequestedEvent | method: getPackageDetailId | strPackDetailId:: " + strPackDetailId);
        }
        
		return strPackDetailId;
	}


	/**
	 * @param env
	 * @param inDoc
	 * @param getAmazonOrdRefundDetailOutDoc
	 * @param getOrderLineListOutDoc
	 * @param strRefundId
	 * @param strAmzOrderNo
	 * @param amzOrderLineItemsList
	 * @return
	 * @throws XPathExpressionException
	 * @throws YFSException
	 * @throws RemoteException
	 * @throws YIFClientCreationException
	 * This method prepares the input document for the creation of a credit memo based on the refund details provided. 
	 * It retrieves the necessary information, constructs an order invoice document, and invokes an API to record the credit memo if applicable. 
	 * It handles the order line details, including charges and taxes, and ensures that prime eligible items are processed correctly.
	 */
	//public Document prepareInputAndGetCreditMemoDetails(YFSEnvironment env,Document getOrderLineListOutDoc,String strRefundId,String strAmzOrderNo,NodeList amzOrderLineItemsList) throws XPathExpressionException, YFSException, RemoteException, YIFClientCreationException {
		//DROP-3 UAT without Invoice Cancellation Defect STARTS
	 public Document prepareInputAndGetCreditMemoDetails(YFSEnvironment env,Document getOrderLineListOutDoc,String strRefundId,String strAmzOrderNo,NodeList amzOrderLineItemsList,String strTotalAmount, String strCurrencyCode) throws XPathExpressionException, YFSException, RemoteException, YIFClientCreationException {
		 //DROP-3 UAT without Invoice Cancellation Defect ENDS					
		logger.beginTimer("class: AmzProcessRefundRequestedEvent | method: prepareInputAndGetCreditMemoDetails -- Starts");
		logger.info("class: AmzProcessRefundRequestedEvent | method: prepareInputAndGetCreditMemoDetails -- Starts");
		Document orderInvoiceIndoc = null;
		Document orderInvoiceOutDoc=null;
		Element eleGetOrderLineListOp = getOrderLineListOutDoc.getDocumentElement();
		String extnOrderCountry = SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp,AmzLiterals.XPATH_EXTN_COUNTRY_CODE);
	    String entepriseCode = SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp,AmzLiterals.XPATH_ENTERPRISE_CODE);  
	    Document propertyDoc = SCXmlUtil.createDocument(AmzLiterals.E_PROPERTIES);
		propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, entepriseCode);
		genricPropertiesMap = AmzGetGenericProperty.getGenericProperties(env , propertyDoc);
		strShipNode = genricPropertiesMap.get(AmzCommonConstants.PROP_AMZ_SHIP_NODE+ extnOrderCountry);
		logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndGetCreditMemoDetails | shipNode: "+ strShipNode);
		orderInvoiceIndoc = SCXmlUtil.createDocument(AmzLiterals.E_ORDER_INVOICE);
		Element eleorderInvoiceIndoc= orderInvoiceIndoc.getDocumentElement();
		eleorderInvoiceIndoc.setAttribute(AmzLiterals.A_DOCUMENT_TYPE,	SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_DOCUEMENT_TYPE));
		eleorderInvoiceIndoc.setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,	SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_ENTERPRISE_CODE));
		eleorderInvoiceIndoc.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY,	SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, "/OrderLineList/OrderLine/Order/@OrderHeaderKey"));
    	eleorderInvoiceIndoc.setAttribute(AmzLiterals.A_ORDER_NO,	SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_SALES_ORDER_NO));
    	eleorderInvoiceIndoc.setAttribute(AmzLiterals.A_INVOICE_TYPE,AmzLiterals.STR_CREDIT_MEMO);
    	eleorderInvoiceIndoc.setAttribute(AmzLiterals.ATTR_SELLER_ORG_CODE,	SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_SELLER_ORG_CODE));
    	eleorderInvoiceIndoc.setAttribute(AmzLiterals.A_SHIP_NODE,strShipNode);
    	eleorderInvoiceIndoc.setAttribute(AmzLiterals.A_USE_ORDER_LINE_CHARGES,AmzLiterals.STR_VAL_Y);
    	Element eleExtnorderInvoiceIndoc = SCXmlUtil.createChild(eleorderInvoiceIndoc, AmzLiterals.E_EXTN);
    	eleExtnorderInvoiceIndoc.setAttribute(AmzLiterals.A_EXTN_AMZ_REFUND_ID,strRefundId);
    	eleExtnorderInvoiceIndoc.setAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID,strAmzOrderNo);
		Element eleLineDetails = SCXmlUtil.createChild(eleorderInvoiceIndoc, AmzLiterals.E_LINE_DETAILS);
		
		
		for (int i = 0; i < amzOrderLineItemsList.getLength(); i++) {
			Element eleOrderLineItem = (Element) amzOrderLineItemsList.item(i);
			logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndGetCreditMemoDetails | eleOrderLineItem :" +SCXmlUtil.getString(eleOrderLineItem));
			String strLineItemID = SCXmlUtil.getXpathAttribute(eleOrderLineItem, AmzLiterals.XPATH_AMZ_LINE_ID);
			logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndGetCreditMemoDetails | strLineItemID : " +strLineItemID);
			String strAmountValue = SCXmlUtil.getXpathAttribute(eleOrderLineItem, AmzLiterals.XPATH_AMOUNT_VALUE);
			double dCancelledQty = Double.parseDouble(strAmountValue);
			Element eleGetOrdeLineDetail= AmzXMLUtil.getXpathElement(eleGetOrderLineListOp, "/OrderLineList/OrderLine/Order/OrderLines/OrderLine/Extn[@ExtnAmazonLineItemAlias='" + strLineItemID + "']/..");
			logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndGetCreditMemoDetails | eleGetOrdeLineDetail : " +SCXmlUtil.getString(eleGetOrdeLineDetail));
			Element eleGetOrdeLineDetailExtn=SCXmlUtil.getChildElement(eleGetOrdeLineDetail,AmzLiterals.E_EXTN);
			logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndGetCreditMemoDetails |eleGetOrdeLineDetailExtn : " +SCXmlUtil.getString(eleGetOrdeLineDetailExtn));
			Element eleGetOrdeLineDetailPriceInfo=SCXmlUtil.getChildElement(eleGetOrdeLineDetail,	"LinePriceInfo");
			logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndGetCreditMemoDetails |eleGetOrdeLineDetailPriceInfo : " +SCXmlUtil.getString(eleGetOrdeLineDetailPriceInfo));
			
			String strExtnIsPrimeEligible =eleGetOrdeLineDetailExtn.getAttribute(AmzLiterals.A_EXTN_IS_PRIME_ELIGIBLE);
			String strInvoiceQuantity =eleGetOrdeLineDetail.getAttribute(AmzLiterals.A_INVOICED_QUANTITY);
			double dinvoicedQty = Double.parseDouble(strInvoiceQuantity); 
			
			String strOrderedQuantity =eleGetOrdeLineDetail.getAttribute("OrderedQty");
			double dorderedQty = Double.parseDouble(strOrderedQuantity); 
			
			boolean isPrimeEligible = !YFCObject.isVoid(strExtnIsPrimeEligible) && AmzLiterals.STR_VAL_Y.equalsIgnoreCase(strExtnIsPrimeEligible);
			if ((isPrimeEligible)&& dinvoicedQty>0) {
				Element eleLineDetail = SCXmlUtil.createChild(eleLineDetails, AmzLiterals.E_LINE_DETAIL);
				eleLineDetail.setAttribute(AmzLiterals.A_PRIME_LINE_NO,eleGetOrdeLineDetail.getAttribute(AmzLiterals.A_PRIME_LINE_NO) );
				eleLineDetail.setAttribute(AmzLiterals.A_SUB_LINE_NO,eleGetOrdeLineDetail.getAttribute(AmzLiterals.A_SUB_LINE_NO) );
				eleLineDetail.setAttribute(AmzLiterals.A_ORDER_LINE_KEY,eleGetOrdeLineDetail.getAttribute(AmzLiterals.A_ORDER_LINE_KEY) );
				eleLineDetail.setAttribute(AmzLiterals.ATTR_QUANTITY,strAmountValue );
				eleLineDetail.setAttribute(AmzLiterals.A_ORDER_NO,SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_SALES_ORDER_NO) );
				eleLineDetail.setAttribute(AmzLiterals.A_UNIT_PRICE,eleGetOrdeLineDetailPriceInfo.getAttribute(AmzLiterals.A_UNIT_PRICE) );
				Element eleLineChargeList = SCXmlUtil.createChild(eleLineDetail, AmzLiterals.E_LINE_CHARGE_LIST);
				Element eleLineTaxList = SCXmlUtil.createChild(eleLineDetail, AmzLiterals.E_LINE_TAX_LIST);
				NodeList nlOrdLineCharges = AmzXMLUtil.getXpathNodes(eleGetOrdeLineDetail,"LineCharges/LineCharge");
				for (int k = 0; k < nlOrdLineCharges.getLength(); k++) {
				    Node nodeOrdLineCharge = nlOrdLineCharges.item(k);
				    double chargeAmount = Double.parseDouble(((Element)nodeOrdLineCharge).getAttribute("ChargeAmount"));
				    BigDecimal refundChargeAmount = (BigDecimal.valueOf((chargeAmount/dorderedQty)*dCancelledQty)).setScale(2, RoundingMode.HALF_UP);
				    ((Element)nodeOrdLineCharge).setAttribute("ChargeAmount", refundChargeAmount.toString());
				    ((Element)nodeOrdLineCharge).removeAttribute("ChargePerLine");
				    ((Element)nodeOrdLineCharge).removeAttribute("ChargePerUnit");
				    ((Element)nodeOrdLineCharge).removeAttribute("InvoicedChargeAmount");
				    ((Element)nodeOrdLineCharge).removeAttribute("InvoicedChargePerLine");
				    ((Element)nodeOrdLineCharge).removeAttribute("InvoicedChargePerUnit");
				    ((Element)nodeOrdLineCharge).removeAttribute("RemainingChargeAmount");
				    ((Element)nodeOrdLineCharge).removeAttribute("RemainingChargePerLine");
				    ((Element)nodeOrdLineCharge).removeAttribute("RemainingChargePerUnit");
				    eleLineChargeList.appendChild(orderInvoiceIndoc.importNode(nodeOrdLineCharge, true));
				}
				NodeList nlOrdLineTaxes = AmzXMLUtil.getXpathNodes(eleGetOrdeLineDetail,"LineTaxes/LineTax");
				for (int j = 0; j < nlOrdLineTaxes.getLength(); j++) {
				    Node nlOrdLineTax = nlOrdLineTaxes.item(j);
				    double taxAmount = Double.parseDouble(((Element)nlOrdLineTax).getAttribute("Tax"));
				    BigDecimal refundTaxAmount = (BigDecimal.valueOf((taxAmount/dorderedQty)*dCancelledQty)).setScale(2, RoundingMode.HALF_UP);
				    ((Element)nlOrdLineTax).setAttribute("Tax", refundTaxAmount.toString());
				    ((Element)nlOrdLineTax).removeAttribute("InvoicedTax");
				    ((Element)nlOrdLineTax).removeAttribute("RemainingTax");
				    
				    eleLineTaxList.appendChild(orderInvoiceIndoc.importNode(nlOrdLineTax, true));
				}
			}
		}
		logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndGetCreditMemoDetails |orderInvoiceIndoc : " +SCXmlUtil.getString(orderInvoiceIndoc));
		logger.info("class: AmzProcessRefundRequestedEvent | method: prepareInputAndGetCreditMemoDetails -- Ends");
		logger.endTimer("class: AmzProcessRefundRequestedEvent | method: prepareInputAndGetCreditMemoDetails -- Ends");
		
		if (hasLineDetail(orderInvoiceIndoc)) {
			logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndGetCreditMemoDetails | orderInvoiceIndoc : " +SCXmlUtil.getString(orderInvoiceIndoc));
			 orderInvoiceOutDoc= AmzCommonUtil.callAPI(env, orderInvoiceIndoc, AmzCommonConstants.API_RECORD_INVOICE_CREATION, null);
			 logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndGetCreditMemoDetails | OrderInvoiceOutDoc : " +SCXmlUtil.getString(orderInvoiceOutDoc));
			 return orderInvoiceOutDoc;
		 }
		//DROP-3 UAT without Invoice Cancellation Defect STARTS
		// Suppress Refund Failure Message to Amazon
		/*else {
			 eleorderInvoiceIndoc.setAttribute(AmzLiterals.A_TOTAL_AMOUNT,strTotalAmount);
			 eleorderInvoiceIndoc.setAttribute(AmzLiterals.A_CURRENCY,strCurrencyCode);
			 eleorderInvoiceIndoc.setAttribute("isFailure",AmzLiterals.STR_VAL_Y);	
			 logger.debug("class: AmzProcessRefundRequestedEvent | method: verifyInvoiceTypeAndPostMessageToQ | orderInvoiceDoc ::: "+SCXmlUtil.getString(orderInvoiceIndoc) );
			 AmzCommonUtil.invokeService(env, AmzCommonConstants.SERVICE_AMZ_CONN_POST_COMPLETE_REFUND_REQ_MSG_TO_Q, orderInvoiceIndoc);			 
		 }*/
		//DROP-3 UAT without Invoice Cancellation Defect ENDS
		return orderInvoiceIndoc;
	}

	
	


	/**
	 * @param env
	 * @param inDoc
	 * @param getAmazonOrdRefundDetailOutDoc
	 * @param getOrderLineListOutDoc
	 * @param strRefundId
	 * @param strAmzOrderNo
	 * @param amzOrderLineItemsList
	 * @return
	 * @throws XPathExpressionException
	 * @throws YFSException
	 * @throws RemoteException
	 * @throws YIFClientCreationException
	 * This method prepares the input document for receiving an order based on the refund details provided. 
	 * It constructs a receipt document, processes line items (including those that are prime eligible), and invokes an API to receive the order. 
	 * The method ensures that prime eligible items are handled correctly, and it returns the result of the API call or the input document if no receipt lines are processed.
	 */
	public Document prepareInputAndReceiveOrder(YFSEnvironment env,Document getOrderLineListOutDoc,NodeList amzOrderLineItemsList,String strAmzOrderNo, String strRefundId, String strReturnId) throws XPathExpressionException, YFSException, RemoteException, YIFClientCreationException {
		logger.beginTimer("class: AmzProcessRefundRequestedEvent | method: prepareInputAndReceiveOrder -- Starts");
		logger.info("class: AmzProcessRefundRequestedEvent | method: prepareInputAndReceiveOrder -- Starts");
		Document createOrderInvoiceInDoc = SCXmlUtil.createDocument(AmzLiterals.E_ORDER);
		Document receiveOrderInDoc = null;
		Document receiveOrderOutDoc=null;
		Document closeReceiptOutDoc=null;
		Document createOrderInvoiceOutDoc=null;
		Element eleGetOrderLineListOp = getOrderLineListOutDoc.getDocumentElement();
		String extnOrderCountry = SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp,"/OrderLineList/OrderLine/DerivedFromOrder/Extn/@ExtnOrderCountry");
	    String entepriseCode = SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp,AmzLiterals.XPATH_ENTERPRISE_CODE);  
	    Document propertyDoc = SCXmlUtil.createDocument(AmzLiterals.E_PROPERTIES);
		propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, entepriseCode);
		genricPropertiesMap = AmzGetGenericProperty.getGenericProperties(env , propertyDoc);
		strShipNode = genricPropertiesMap.get(AmzCommonConstants.PROP_AMZ_SHIP_NODE+ extnOrderCountry);
		logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndReceiveOrder | shipNode : "+ strShipNode);
		receiveOrderInDoc = SCXmlUtil.createDocument(AmzLiterals.E_RECEIPT);
		Element eleReceiptInDoc= receiveOrderInDoc.getDocumentElement();
		eleReceiptInDoc.setAttribute(AmzLiterals.A_DOCUMENT_TYPE,	SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_DOCUEMENT_TYPE));
		eleReceiptInDoc.setAttribute(AmzLiterals.STR_RECEIVING_NODE,strShipNode);
		Element eleShipment = SCXmlUtil.createChild(eleReceiptInDoc, AmzLiterals.STR_SHIPMENT);
		eleShipment.setAttribute(AmzLiterals.ATTR_SELLER_ORG_CODE, SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, "/OrderLineList/OrderLine/DerivedFromOrder/@SellerOrganizationCode"));
		eleShipment.setAttribute(AmzLiterals.A_ORDER_NO,SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_SALES_ORDER_NO));
		eleShipment.setAttribute(AmzLiterals.ATTR_DOCUMENT_TYPE,SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_DOCUEMENT_TYPE));
		eleShipment.setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_ENTERPRISE_CODE));
		eleShipment.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY,SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, "/OrderLineList/OrderLine/Order/@OrderHeaderKey"));
		eleShipment.setAttribute(AmzLiterals.A_SHIP_NODE,strShipNode);
		Element eleReceiptLines  = SCXmlUtil.createChild(eleReceiptInDoc, AmzLiterals.E_RECEIPT_LINES);
		
		//Prepare createOrderInvoice document
		 Element eleCreateOrderInvoice= createOrderInvoiceInDoc.getDocumentElement();
		 eleCreateOrderInvoice.setAttribute(AmzLiterals.A_ORDER_NO,SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_SALES_ORDER_NO));
		 eleCreateOrderInvoice.setAttribute(AmzLiterals.ATTR_DOCUMENT_TYPE,SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_DOCUEMENT_TYPE));
		 eleCreateOrderInvoice.setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_ENTERPRISE_CODE));
		 eleCreateOrderInvoice.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY,SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, "/OrderLineList/OrderLine/Order/@OrderHeaderKey"));
		 eleCreateOrderInvoice.setAttribute("TransactionId","CREATE_ORDER_INVOICE.0003");
		 eleCreateOrderInvoice.setAttribute("IgnoreStatusCheck","Y");
		 eleCreateOrderInvoice.setAttribute("IgnoreTransactionDependencies","Y");
		 Element eleInvoiceOrderLines  = SCXmlUtil.createChild(eleCreateOrderInvoice, AmzLiterals.E_ORDER_LINES);
				
		for (int i = 0; i < amzOrderLineItemsList.getLength(); i++) {
			Element eleOrderLineItem = (Element) amzOrderLineItemsList.item(i);
			logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndReceiveOrder | eleOrderLineItem : " +SCXmlUtil.getString(eleOrderLineItem));
			String strLineItemID = SCXmlUtil.getXpathAttribute(eleOrderLineItem, AmzLiterals.XPATH_AMZ_LINE_ID);
			logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndReceiveOrder | strLineItemID : " +strLineItemID);
			
			String strAmountValue = SCXmlUtil.getXpathAttribute(eleOrderLineItem, AmzLiterals.XPATH_AMOUNT_VALUE);
			
			Element eleGetOrdeLineDetail= AmzXMLUtil.getXpathElement(eleGetOrderLineListOp, "/OrderLineList/OrderLine/Order/OrderLines/OrderLine/Extn[@ExtnAmazonSoLineItemAlias='" + strLineItemID + "']/..");
			logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndReceiveOrder | eleGetOrdeLineDetail : " +SCXmlUtil.getString(eleGetOrdeLineDetail));
			Element eleGetOrdeLineDetailExtn=SCXmlUtil.getChildElement(eleGetOrdeLineDetail,AmzLiterals.E_EXTN);
			logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndReceiveOrder |eleGetOrdeLineDetailExtn : " +SCXmlUtil.getString(eleGetOrdeLineDetailExtn));
			
			Element eleGetOrdeLineDetailItem=SCXmlUtil.getChildElement(eleGetOrdeLineDetail,"Item");
			logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndReceiveOrder  |eleGetOrdeLineDetailItem : " +SCXmlUtil.getString(eleGetOrdeLineDetailItem));
			String strIsAmazonInitReturn =eleGetOrdeLineDetailExtn.getAttribute(AmzLiterals.A_EXTN_IS_AMZ_INT_RETURN);
			boolean isAmazonInitReturn = !YFCObject.isVoid(strIsAmazonInitReturn) && AmzLiterals.STR_VAL_Y.equalsIgnoreCase(strIsAmazonInitReturn);
			if (isAmazonInitReturn) {
				Element eleReceiptLine = SCXmlUtil.createChild(eleReceiptLines, AmzLiterals.E_RECEIPT_LINE);
				eleReceiptLine.setAttribute(AmzLiterals.A_ITEM_ID,eleGetOrdeLineDetailItem.getAttribute(AmzLiterals.A_ITEM_ID) );
				eleReceiptLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY,eleGetOrdeLineDetail.getAttribute(AmzLiterals.A_ORDER_LINE_KEY) );
				eleReceiptLine.setAttribute(AmzLiterals.ATTR_QUANTITY,strAmountValue );
				eleReceiptLine.setAttribute(AmzLiterals.A_ORDER_NO,SCXmlUtil.getXpathAttribute(eleGetOrderLineListOp, AmzLiterals.XPATH_SALES_ORDER_NO) );
				Element eleExtReceiptLine = SCXmlUtil.createChild(eleReceiptLine, AmzLiterals.E_EXTN);
				eleExtReceiptLine.setAttribute(AmzLiterals.A_EXTN_AMZ_REFUND_ID,strRefundId);
				eleExtReceiptLine.setAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID,strAmzOrderNo);
				eleExtReceiptLine.setAttribute(AmzLiterals.A_EXTN_AMZ_RETURN_ORD_ID,strReturnId);
				
				//InvoiceOrderLines
				Element eleInvoiceLine = SCXmlUtil.createChild(eleInvoiceOrderLines, AmzLiterals.E_ORDER_LINE);
				eleInvoiceLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY,eleGetOrdeLineDetail.getAttribute(AmzLiterals.A_ORDER_LINE_KEY) );
				eleInvoiceLine.setAttribute(AmzLiterals.ATTR_QUANTITY,strAmountValue );
			}
		}
		logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndReceiveOrder | receiptInDoc" +SCXmlUtil.getString(receiveOrderInDoc));
		logger.info("class: AmzProcessRefundRequestedEvent | method: prepareInputAndReceiveOrder -- Ends");
		logger.endTimer("class: AmzProcessRefundRequestedEvent | method: prepareInputAndReceiveOrder -- Ends");
		
		if (hasReceiptLine(receiveOrderInDoc)) {
			 logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndReceiveOrder | receiveOrderInDoc : " +SCXmlUtil.getString(receiveOrderInDoc));
			 Document receiveOrderTemplate = SCXmlUtil.createFromString(AmzLiterals.TEMP_RECEIVE_ORDER);
			 logger.debug("class: AmzProcessRefundRequestedEvent | method: processRefundRequestedEvent | receiveOrderTemplate : " +SCXmlUtil.getString(receiveOrderTemplate));
			 
			 receiveOrderOutDoc= AmzCommonUtil.callAPI(env, receiveOrderInDoc, AmzCommonConstants.API_RECEIVE_ORDER, receiveOrderTemplate);
			 logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndReceiveOrder | receiveOrderOutDoc : " +SCXmlUtil.getString(receiveOrderOutDoc));
			 if(receiveOrderOutDoc!=null) {
				 closeReceiptOutDoc= AmzCommonUtil.callAPI(env, receiveOrderOutDoc, AmzCommonConstants.API_CLOSE_RECEIPT, null);
				 logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndReceiveOrder | closeReceiptOutDoc : " +SCXmlUtil.getString(closeReceiptOutDoc));
				 //return closeReceiptOutDoc;
				 logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndReceiveOrder | createOrderInvoiceInDoc : " +SCXmlUtil.getString(createOrderInvoiceInDoc));
				 createOrderInvoiceOutDoc= AmzCommonUtil.callAPI(env, createOrderInvoiceInDoc, AmzCommonConstants.API_CREATE_ORDER_INVOICE, null);
				 logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndReceiveOrder | createOrderInvoiceOutDoc : " +SCXmlUtil.getString(createOrderInvoiceOutDoc));
				//return createOrderInvoiceOutDoc;
				 Element eleOrderInvoiceOutDoc= SCXmlUtil.getXpathElement(createOrderInvoiceOutDoc.getDocumentElement(), "/OrderInvoiceList/OrderInvoice");
				
				 //changeOrderInvoice
				 Document changeOrderInvoiceInDoc = SCXmlUtil.createDocument(AmzLiterals.E_ORDER_INVOICE);
				 Element eleChangeOrderInvoiceInDoc=changeOrderInvoiceInDoc.getDocumentElement();
				 eleChangeOrderInvoiceInDoc.setAttribute(AmzLiterals.A_ORDER_INVOICE_KEY,eleOrderInvoiceOutDoc.getAttribute(AmzLiterals.A_ORDER_INVOICE_KEY));
				 Element eleChangeOrderInvoiceExtn = SCXmlUtil.createChild(eleChangeOrderInvoiceInDoc, AmzLiterals.E_EXTN);
				 eleChangeOrderInvoiceExtn.setAttribute(AmzLiterals.A_EXTN_AMZ_REFUND_ID,strRefundId);
				 eleChangeOrderInvoiceExtn.setAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID,strAmzOrderNo);
				 logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndReceiveOrder | changeOrderInvoiceInDoc : " +SCXmlUtil.getString(changeOrderInvoiceInDoc));
				 Document changeOrderInvoiceOutDoc= AmzCommonUtil.callAPI(env, changeOrderInvoiceInDoc, AmzCommonConstants.API_CHANGE_ORDER_INVOICE, null);
				 logger.debug("class: AmzProcessRefundRequestedEvent | method: prepareInputAndReceiveOrder | changeOrderInvoiceOutDoc : " +SCXmlUtil.getString(changeOrderInvoiceOutDoc));
				return changeOrderInvoiceOutDoc;
			 }
			 return receiveOrderOutDoc;
		 }
		return receiveOrderInDoc;
	}


	
		/**
		 * @param env
		 * @param strRefundId
		 * @return
		 * @throws Exception
		 * This method retrieves the order invoice details based on a provided strRefundId. It prepares an input document for the API_GET_ORDER_INVOICE_LIST API call, 
		 * invokes the API, and returns the resulting document with the order invoice details.
		 */
		public Document getOrderInvoiceDetails(YFSEnvironment env, String strRefundId ) throws Exception {
		logger.beginTimer("class: AmzProcessRefundRequestedEvent | method: getOrderInvoiceDetails -- Starts");
		logger.info("class: AmzProcessRefundRequestedEvent | method: getOrderInvoiceDetails -- Starts");
		 Document getOrderInvoiceInDoc = SCXmlUtil.createDocument(AmzLiterals.E_ORDER_INVOICE);
		 Element eleGetOrderInvoiceInDoc=getOrderInvoiceInDoc.getDocumentElement();
		 Element eleOrderLineExtn = SCXmlUtil.createChild(eleGetOrderInvoiceInDoc, AmzLiterals.E_EXTN);
		 eleOrderLineExtn.setAttribute(AmzLiterals.A_EXTN_AMZ_REFUND_ID, strRefundId);
		 logger.debug("class: AmzProcessRefundRequestedEvent | method: getOrderInvoiceDetails | getOrderInvoiceInDoc : " +SCXmlUtil.getString(getOrderInvoiceInDoc));
		 Document getOrderInvoiceOutDoc = AmzCommonUtil.callAPI(env, getOrderInvoiceInDoc, AmzCommonConstants.API_GET_ORDER_INVOICE_LIST, null);
		 logger.debug("class: AmzProcessRefundRequestedEvent | method: getOrderInvoiceDetails | getOrderInvoiceOutDoc : " +SCXmlUtil.getString(getOrderInvoiceOutDoc));
		 logger.info("class: AmzProcessRefundRequestedEvent | method: getOrderInvoiceDetails -- Ends");
		 logger.endTimer("class: AmzProcessRefundRequestedEvent | method: getOrderInvoiceDetails -- Ends");
		 return getOrderInvoiceOutDoc;
	}
	
	
		
		
		/**
		 * @param env
		 * @param strRefundId
		 * @return
		 * @throws Exception
		 * This method will call ReceiptLineList API based on a provided strRefundId. 
		 * It prepares an input document and check RefundID already exists or not for the order.
		 */
		public boolean isRefundIdAlreadyProcessed(YFSEnvironment env, String strRefundId, String strReturnId ) throws Exception {
		logger.beginTimer("class: AmzProcessRefundRequestedEvent | method: isRefundIdAlreadyProcessed -- Starts");
		logger.info("class: AmzProcessRefundRequestedEvent | method: isRefundIdAlreadyProcessed -- Starts");
		boolean isRefundIdExist=false; 
		Document getReceiptLineListIndoc = SCXmlUtil.createDocument(AmzLiterals.E_RECEIPT_LINE);
		 Element eleGetReceiptLineListIndoc=getReceiptLineListIndoc.getDocumentElement();
		 Element eleOrderLineExtn = SCXmlUtil.createChild(eleGetReceiptLineListIndoc, AmzLiterals.E_EXTN);
		 eleOrderLineExtn.setAttribute(AmzLiterals.A_EXTN_AMZ_REFUND_ID, strRefundId);
		 eleOrderLineExtn.setAttribute(AmzLiterals.A_EXTN_AMZ_RETURN_ORD_ID, strReturnId);
		 logger.debug("class: AmzProcessRefundRequestedEvent | method: isRefundIdAlreadyProcessed | getReceiptLineListIndoc : " +SCXmlUtil.getString(getReceiptLineListIndoc));
		 Document getReceiptLineListTemp = SCXmlUtil.createFromString(AmzLiterals.TEMP_GET_RECEIPT_LINE_LIST);
		// Call the API 
		 logger.debug("class: AmzProcessRefundRequestedEvent | method: isRefundIdAlreadyProcessed | getReceiptLineListTemp : " +SCXmlUtil.getString(getReceiptLineListTemp));
		 Document getReceiptLineListOutDoc = AmzCommonUtil.callAPI(env, getReceiptLineListIndoc, AmzCommonConstants.API_GET_RECEIPT_LINE_LIST, getReceiptLineListTemp);
		 logger.debug("class: AmzUpdateRefundStatusToAmazon | method: isRefundIdAlreadyProcessed |getReceiptLineListOutDoc : " +SCXmlUtil.getString(getReceiptLineListOutDoc));
		
		 Element eleGetReceiptLineListOutDoc=getReceiptLineListOutDoc.getDocumentElement();
		 String strTotalNumberOfRecords=eleGetReceiptLineListOutDoc.getAttribute("TotalNumberOfRecords");
		 if("0".equalsIgnoreCase(strTotalNumberOfRecords)) {
			 isRefundIdExist=false; 
		 }else {
			 isRefundIdExist=true; 
		 }
		 
		 logger.info("class: AmzProcessRefundRequestedEvent | method: isRefundIdAlreadyProcessed -- Ends");
		 logger.endTimer("class: AmzProcessRefundRequestedEvent | method: isRefundIdAlreadyProcessed -- Ends");
		 return isRefundIdExist;
	}
	
	
		/**
		 * @param env
		 * @param lineItemId
		 * @param strEnterpriseCode
		 * @param strType
		 * @return
		 * @throws Exception
		 * This method retrieves a list of order lines based on the provided line item ID, enterprise code, 
		 * and type (either "REFUND" or "CANCELLED"). It prepares an input document for the API_GET_ORDER_LINE_LIST API call, 
		 * invokes the API, and returns the resulting document with the order line details.
		 */
		public Document getOrderLineListApi(YFSEnvironment env, String lineItemId,String strReturnId, String strType ) throws Exception {
		logger.beginTimer("class: AmzProcessRefundRequestedEvent | method: getOrderLineListApi -- Starts");
		logger.info("class: AmzProcessRefundRequestedEvent | method: getOrderLineListApi -- Starts");
		 Document getOrderLineListInDoc = SCXmlUtil.createDocument(AmzLiterals.E_ORDER_LINE);
		 Element eleGetOrderLineListInDoc=getOrderLineListInDoc.getDocumentElement();
		 Element eleOrderLineExtn = SCXmlUtil.createChild(eleGetOrderLineListInDoc, AmzLiterals.E_EXTN);
		 if ("REFUND".equalsIgnoreCase(strType)) {
		 eleOrderLineExtn.setAttribute(AmzLiterals.A_EXTN_AMZ_SO_LINE_ITEM_ALIAS, lineItemId);
		 eleOrderLineExtn.setAttribute(AmzLiterals.A_EXTN_AMZ_RETURN_ORD_ID, strReturnId);		 
		 }else if ("CANCELLED".equalsIgnoreCase(strType))  {
		  eleOrderLineExtn.setAttribute(AmzLiterals.A_EXTN_AMZ_LINE_ITEM_ALIAS, lineItemId);
		 }
		 logger.debug("class: AmzProcessRefundRequestedEvent | method: getOrderLineListApi | getOrderLineListInDoc : " +SCXmlUtil.getString(getOrderLineListInDoc));
		 Document getOrderLineListTemp = SCXmlUtil.createFromString(AmzLiterals.TEMP_ORDER_LINE_LIST_FOR_REFUND);
		 logger.debug("class: AmzProcessRefundRequestedEvent | method: getOrderLineListApi | getOrderLineListTemp : " +SCXmlUtil.getString(getOrderLineListTemp));
		 Document getOrderLineListOutDoc = AmzCommonUtil.callAPI(env, getOrderLineListInDoc, AmzCommonConstants.API_GET_ORDER_LINE_LIST, getOrderLineListTemp);
		 logger.debug("class: AmzProcessRefundRequestedEvent | method: getOrderLineListApi | getOrderLineListOutDoc : " +SCXmlUtil.getString(getOrderLineListOutDoc));
		 logger.info("class: AmzProcessRefundRequestedEvent | method: getOrderLineListApi -- Ends");
		 logger.endTimer("class: AmzProcessRefundRequestedEvent | method: getOrderLineListApi -- Ends");
		 return getOrderLineListOutDoc;
	}
	
	
	
	
	/**
	 * @param env
	 * @param amazonOrderId
	 * @param eventType
	 * @param strEnterpriseCode
	 * @return
	 * @throws Exception
	 * This method retrieves the Amazon order refund details by making a service call (SERVICE_AMAZON_GET_REFUND_ORDER_DETAILS) using the provided Amazon Order ID, event type, and enterprise code. 
	 * It prepares the input document, invokes the service, and returns the resulting document containing the refund order details.
	 */
	private Document getAmazonOrdRefundDetail(YFSEnvironment env, String amazonOrderId, String eventType,String strEnterpriseCode ) throws Exception {
		logger.beginTimer("class: AmzProcessRefundRequestedEvent | method: getAmazonOrdRefundDetail -- Starts");
		logger.info("class: AmzProcessRefundRequestedEvent | method: getAmazonOrdRefundDetail -- Starts");
		Document getAmzOrderRefundDetailOutDoc=null;
		Document getAmzOrderRefundDetailInDoc = SCXmlUtil.createDocument(AmzLiterals.STR_ORDER);
	    getAmzOrderRefundDetailInDoc.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMZ_ORDER_ID, amazonOrderId);
	    getAmzOrderRefundDetailInDoc.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE, eventType);
	    getAmzOrderRefundDetailInDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);
	    logger.debug("class: AmzProcessRefundRequestedEvent | method: getAmazonOrdRefundDetail | getAmzOrderRefundDetailInDoc : " +SCXmlUtil.getString(getAmzOrderRefundDetailInDoc));
	    getAmzOrderRefundDetailOutDoc= AmzCommonUtil.callService(env, getAmzOrderRefundDetailInDoc, AmzCommonConstants.SERVICE_AMAZON_GET_REFUND_ORDER_DETAILS, null);
	    logger.debug("class: AmzProcessRefundRequestedEvent | method: getAmazonOrdRefundDetail | getAmzOrderRefundDetailOutDoc : " +SCXmlUtil.getString(getAmzOrderRefundDetailOutDoc));
	    logger.info("class: AmzProcessRefundRequestedEvent | method: getAmazonOrdRefundDetail -- Ends");
	    logger.endTimer("class: AmzProcessRefundRequestedEvent | method: getAmazonOrdRefundDetail -- Ends");
	    return getAmzOrderRefundDetailOutDoc;
	}
	
	
	/**
	 * @param processStatus
	 * @param apiOutput
	 * @param strEventType
	 * @param strAmzOrderNo
	 * @param strRefundId
	 * @param message
	 * This method is responsible for preparing a response document and logging it based on the provided information. 
	 * It creates a log entry with details about the process, Amazon order, refund ID, process status, and any relevant data from the API output. 
	 * The method also handles both success and failure cases and ensures that the response is logged for later review.
	 */
	public void prepareAndLogResponse(String processStatus,Document apiOutput, String strEventType, String strAmzOrderNo,String strRefundId, String message) {
		logger.beginTimer("class: AmzProcessRefundRequestedEvent | method: prepareAndLogResponse -- Starts");
		logger.info("class: AmzProcessRefundRequestedEvent | method: prepareAndLogResponse -- Starts");
		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE, strEventType);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_ACTION, "OMS-PROCESS");
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, strAmzOrderNo);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_REFUND_ID, strRefundId);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_PROCESS_STATUS, processStatus);
		if (!YFCObject.isVoid(apiOutput)) {
			Element eleApiOutput=apiOutput.getDocumentElement();
			if (eleApiOutput.getTagName().equals(AmzLiterals.E_RECEIPT)){
				logInput.getDocumentElement().setAttribute(AmzLiterals.A_RECEIPT_NO,apiOutput.getDocumentElement().getAttribute(AmzLiterals.A_RECEIPT_NO));
				logInput.getDocumentElement().setAttribute(AmzLiterals.A_RECEIPT_HEADER_KEY,apiOutput.getDocumentElement().getAttribute(AmzLiterals.A_RECEIPT_HEADER_KEY));
				logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_SHIPMENT_KEY,	apiOutput.getDocumentElement().getAttribute(AmzLiterals.ATTR_SHIPMENT_KEY));
				Element eleShipment = SCXmlUtil.getChildElement(eleApiOutput, AmzLiterals.STR_SHIPMENT);
				logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO,eleShipment.getAttribute(AmzLiterals.A_ORDER_NO));
				logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,eleShipment.getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE));
				logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_SELLER_ORG_CODE,eleShipment.getAttribute(AmzLiterals.ATTR_SELLER_ORG_CODE));
				logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_SHIPMENT_NO,eleShipment.getAttribute(AmzLiterals.ATTR_SHIPMENT_NO));
			
			}else if (eleApiOutput.getTagName().equals(AmzLiterals.E_ORDER_INVOICE)){
				logInput.getDocumentElement().setAttribute(AmzLiterals.A_INVOICE_NO,apiOutput.getDocumentElement().getAttribute(AmzLiterals.A_INVOICE_NO));
				logInput.getDocumentElement().setAttribute(AmzLiterals.A_INVOICE_TYPE,apiOutput.getDocumentElement().getAttribute(AmzLiterals.A_INVOICE_TYPE));
				logInput.getDocumentElement().setAttribute(AmzLiterals.A_ORDER_HEADER_KEY,apiOutput.getDocumentElement().getAttribute(AmzLiterals.A_ORDER_HEADER_KEY));
				logInput.getDocumentElement().setAttribute(AmzLiterals.A_ORDER_INVOICE_KEY,apiOutput.getDocumentElement().getAttribute(AmzLiterals.A_ORDER_INVOICE_KEY));
				logInput.getDocumentElement().setAttribute(AmzLiterals.A_DATE_INVOICED,apiOutput.getDocumentElement().getAttribute(AmzLiterals.A_DATE_INVOICED));
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
		logger.info("class: AmzProcessRefundRequestedEvent | method: prepareAndLogResponse -- Ends");
		logger.endTimer("class: AmzProcessRefundRequestedEvent | method: prepareAndLogResponse -- Ends");
	
	}

	
	/**
	 * @param env
	 * @param strReason
	 * @param indoc
	 * @return
	 * @throws YFSException
	 * @throws RemoteException
	 * @throws YIFClientCreationException
	 * This method validates whether a given refund reason code is valid based on a predefined list of valid code
	 * fetched from the common code API. It checks if the provided strReason (refund reason code) exists in the list of valid Amazon refund reason codes.
	 */
	private Boolean validateRefundReasonCode(YFSEnvironment env, String strReason) throws YFSException, RemoteException, YIFClientCreationException {
		logger.beginTimer("class: AmzProcessRefundRequestedEvent | method: validateRefundReasonCode -- Starts");
		logger.info("class: AmzProcessRefundRequestedEvent | method: validateRefundReasonCode -- Starts");
		Boolean validateReasonCodeFlag=false;
		Document apiOutput=null;
		Document commonCodeInput = SCXmlUtil.createDocument(AmzLiterals.E_COMMON_CODE);
		commonCodeInput.getDocumentElement().setAttribute(AmzLiterals.A_CODE_TYPE,AmzLiterals.STR_VAL_AMZ_REFUND_REASONS);
		try {
		apiOutput = AmzCommonUtil.callAPI(env, commonCodeInput, AmzCommonConstants.GET_COMMON_CODE_LIST, null);
		}catch (YFSException e) {
	    	 
			YFSException yfse = new YFSException();
			yfse.setErrorCode(AmzLiterals.STR_REFUND_REQ_ERROR_CODE+"COMMON_CODE_API");
			yfse.setErrorDescription(e.getErrorDescription());
			prepareAndLogResponse(AmzLiterals.STR_ERROR, null, strEventType,strAmzOrderNo,strRefundId, e.getErrorDescription());
			throw yfse;
	    }		
		YFCElement commonCodeElement = YFCDocument.getDocumentFor(apiOutput).getDocumentElement();
		YFCNodeList<YFCElement> commonCodeList = commonCodeElement.getElementsByTagName(AmzLiterals.E_COMMON_CODE);
		for (YFCElement tempElement : commonCodeList) {
			String codeValue = tempElement.getAttribute(AmzLiterals.A_CODE_VALUE);
			if (strReason.equals(codeValue)) {
				validateReasonCodeFlag=true;
				return validateReasonCodeFlag;
			}
		}
		logger.info("class: AmzProcessRefundRequestedEvent | method: validateRefundReasonCode -- Ends");
		logger.endTimer("class: AmzProcessRefundRequestedEvent | method: validateRefundReasonCode -- Ends");
		return validateReasonCodeFlag;
	}	
	
	
	
	@Override
	public void setProperties(Properties props) {
		this.props = props;
	}
	/**
	 * @param orderInDoc
	 * @return
	 * Method to validate if outIndoc has ReceiptLine information
	 */
	private boolean hasReceiptLine(Document orderInDoc) {
	    return orderInDoc.getElementsByTagName(AmzLiterals.E_RECEIPT_LINE).getLength() > 0 &&
			    (orderInDoc.getElementsByTagName(AmzLiterals.E_RECEIPT_LINE).item(0)).hasAttributes();
	}
	
	/**
	 * @param orderInDoc
	 * @return
	 * Method to validate if outIndoc has ReceiptLine information
	 */
	private boolean hasLineDetail(Document orderInDoc) {
	    return orderInDoc.getElementsByTagName(AmzLiterals.E_LINE_DETAIL).getLength() > 0 &&
			    (orderInDoc.getElementsByTagName(AmzLiterals.E_LINE_DETAIL).item(0)).hasAttributes();
	}
	
}