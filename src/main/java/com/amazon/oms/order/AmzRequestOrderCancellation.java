package com.amazon.oms.order;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import javax.xml.xpath.XPathExpressionException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.amazon.integrator.order.api.AmzCancelOrderInAmazon;
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

/**
 * This class is responsible for processing and sending order cancellation requests to Amazon's service. 
 * It validates the cancellation reason, prepares a mutation query, and invokes the necessary API to cancel the order on Amazon. 
 * The class interacts with Amazon's GraphQL API to execute the cancellation and handles responses to ensure that the order is successfully cancelled. 
 * Input request to service 
 * <Order OrderHeaderKey="" OrderNo="" DocumentType="" EnterpriseCode="">
	<OrderLines>
		<OrderLine OrderLineKey="" PrimeLineNo="" SubLineNo=""/>
		<OrderLine OrderLineKey="" PrimeLineNo="" SubLineNo=""/>
	</OrderLines>
	<CancelRequestDetails Comments="" CancelReason=""/>
</Order>
 */
public class AmzRequestOrderCancellation implements YIFCustomApi{
	static final YFCLogCategory logger = YFCLogCategory.instance(AmzRequestOrderCancellation.class);
	Properties properties;
	String strComments=null;
	String strReason=null;
	Document prepareResponseDoc=null;
	String strEventType="AMZCONN_CANCEL_ORDER" ;
	String strInputTypeResponse="RESPONSE";
	String strErrorResponse = "ERROR";
	String strInputTypeRequest="REQUEST";
	String strSuccessResponse= "SUCCESS";
	String strErrorMessage=null;
	String strAmazonOrder=null;
	String strEntpriseCode=null;
	Document inputDoc=null;
	Document returnOutDoc=null;
	Map<String, String> genricPropertiesMap = new HashMap<>();
	@Override
	public void setProperties(Properties properties) throws Exception {
		this.properties = properties;
		
	}
	/**
	 * @param env
	 * @param indoc
	 * @return
	 * @throws Exception
	 * This method is the core logic for processing an order cancellation request. It validates the cancellation request, prepares the necessary data, and sends the request to Amazon’s service for order cancellation. It extracts cancellation details (such as reason and comments) from the input document, 
	 * checks for valid order lines, and sends the cancellation request to Amazon for processing
	 */	
	public Document cancelOrderInAmazon(YFSEnvironment env, Document indoc) throws Exception {
	    logger.beginTimer("class: AmzRequestOrderCancellation | method: cancelOrderInAmazon -- Starts");
	    logger.info("class: AmzRequestOrderCancellation | method: cancelOrderInAmazon -- Starts");
	    logger.debug("class: AmzRequestOrderCancellation | method: cancelOrderInAmazon input doc is: " + AmzXMLUtil.getString(indoc));
	  
	    inputDoc = indoc;
	    try {
	    	
	    	// Validate Order information,Order Lines and cancel reason code info is available
	        validateInputRequest(indoc);

	        // Create and set up return document
	        returnOutDoc = prepareResponseDoc (indoc);

	        // Extract necessary elements
	        Element eleOrd = indoc.getDocumentElement();
	        strEntpriseCode=eleOrd.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
	        Element eleCancelRequestDetails = AmzXMLUtil.getChildElement(eleOrd, AmzLiterals.E_CANCEL_REQ_DETAILS);
	        Element eleOrdLines = AmzXMLUtil.getChildElement(eleOrd, AmzLiterals.E_ORDER_LINES);

	        
	        // Extract cancellation reason and comments
	         strReason = eleCancelRequestDetails.getAttribute(AmzLiterals.A_CANCEL_REASON);
	         strComments = eleCancelRequestDetails.getAttribute(AmzLiterals.A_COMMENTS);

	        boolean reasonCodeFlag = validateCancelReasonCode(env, strReason,indoc);
	        logger.debug("class: AmzRequestOrderCancellation | method: cancelOrderInAmazon |reasonCodeFlag is: " + reasonCodeFlag);
	         

	        if (reasonCodeFlag) {
	            findAmazonOrderId(env, indoc, returnOutDoc, eleOrdLines, strReason, strComments);
	        } else {
	        	YFSException yfse = new YFSException();
	 			yfse.setErrorCode(AmzLiterals.STR_INVALID_CANCEL_REQ_ERROR_CODE+"INVALID_REASON_CODE");
	 			yfse.setErrorDescription("The cancellation request does not contain valid reason code.");
	 			prepareAndLogMessage(indoc, strEventType, strInputTypeResponse, strErrorResponse, strAmazonOrder, yfse.getErrorDescription(), 0);
	 			throw yfse;	
	        }
	        
	    } catch (YFSException e) {
	    	prepareAndLogMessage(indoc, strEventType, strInputTypeResponse, strErrorResponse, strAmazonOrder, e.getMessage(), 0);
	    	throw e;
	    } 
	    catch(Exception e) {
	    	  e.printStackTrace();
            YFSException yfse = new YFSException();
            yfse.setErrorCode(AmzLiterals.STR_INVALID_CANCEL_REQ_ERROR_CODE+"EXCEPTION");
            yfse.setErrorDescription(e.getMessage());
            prepareAndLogMessage(indoc, strEventType, strInputTypeResponse, strErrorResponse, strAmazonOrder, e.getMessage(), 0);
            throw yfse;
	    }

	     logger.debug("AmzRequestOrderCancellation.cancelOrderInAmazon.outDocRes: " + SCXmlUtil.getString(returnOutDoc));
	    logger.endTimer("class: AmzRequestOrderCancellation | method: cancelOrderInAmazon -- Ends");
	    logger.info("class: AmzRequestOrderCancellation | method: cancelOrderInAmazon -- Ends");
		 
	    return returnOutDoc;
	}
	
	

	/**
	 * @param indoc
	 * @throws YFSException
	 * This method validate order level information and OrderLines and Cancel Reason code.
	 * If any information is missing then throw exception.
	 */
	private void validateInputRequest(Document indoc) throws YFSException {
		logger.beginTimer("class: AmzRequestOrderCancellation | method: validateInputRequest -- Starts");
		logger.info("class: AmzRequestOrderCancellation | method: validateInputRequest -- Starts");
		Element eleOrd = indoc.getDocumentElement();
		Element eleCancelRequestDetails = AmzXMLUtil.getChildElement(eleOrd,AmzLiterals.E_CANCEL_REQ_DETAILS);
		Element eleOrdLines = AmzXMLUtil.getChildElement(eleOrd,AmzLiterals.E_ORDER_LINES);
		String strOrderNo = null;
		String strOrderHKey = null;
		String strEntCode = null;
		String strDocumentType = null;
		strOrderNo = eleOrd.getAttribute(AmzLiterals.A_ORDER_NO);
		strOrderHKey = eleOrd.getAttribute(AmzLiterals.A_ORDER_HEADER_KEY);
		strEntCode = eleOrd.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
		strDocumentType = eleOrd.getAttribute(AmzLiterals.A_DOCUMENT_TYPE);
		YFSException yfse = new YFSException();

		if ((YFCObject.isVoid(strOrderHKey)	&& (YFCObject.isVoid(strOrderNo) || YFCObject.isVoid(strEntCode)|| YFCObject.isVoid(strDocumentType)))) {
			yfse.setErrorCode(AmzLiterals.STR_INVALID_CANCEL_REQ_ERROR_CODE+ "ORDER_INFO_MISSING");
			yfse.setErrorDescription("The cancellation request does not contain Order Header Key or Document Type and Sales Order No and EnterpriseCode.");
			prepareAndLogMessage(indoc, strEventType, strInputTypeResponse,strErrorResponse, strAmazonOrder,yfse.getErrorDescription(), 0);
			throw yfse;
		 
		}else if (YFCObject.isVoid(eleCancelRequestDetails)){
				yfse.setErrorCode(AmzLiterals.STR_INVALID_CANCEL_REQ_ERROR_CODE	+ "REASON_CODE_MISSING");
				yfse.setErrorDescription("The cancellation request does not contain reason code.");
				prepareAndLogMessage(indoc, strEventType, strInputTypeResponse,strErrorResponse, strAmazonOrder,
				yfse.getErrorDescription(), 0);
			    throw yfse;
		} else if (YFCObject.isVoid(eleOrdLines)) {
				yfse.setErrorCode(AmzLiterals.STR_INVALID_CANCEL_REQ_ERROR_CODE	+ "ORDERLINES_MISSING");
				yfse.setErrorDescription("The cancellation request does not contain order lines ");
				prepareAndLogMessage(indoc, strEventType, strInputTypeResponse,	strErrorResponse, strAmazonOrder,yfse.getErrorDescription(), 0);
				throw yfse;
		}
		logger.info("class: AmzRequestOrderCancellation | method: validateInputRequest -- Ends");
		logger.endTimer("class: AmzRequestOrderCancellation | method: validateInputRequest -- Ends");

	}


	/**
	 * @param indoc
	 * @return
	 * Prepare returnResultDoc documents for returning 
	 */
	private Document prepareResponseDoc (Document indoc) {
		logger.beginTimer("class: AmzRequestOrderCancellation | method: createReturnDocument -- Starts");
		logger.info("class: AmzRequestOrderCancellation | method: createReturnDocument -- Starts");
		String strOrderNo = indoc.getDocumentElement().getAttribute(AmzLiterals.A_ORDER_NO);
	    String strEntCode = indoc.getDocumentElement().getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
	    String strOrderHKey = indoc.getDocumentElement().getAttribute(AmzLiterals.A_ORDER_HEADER_KEY);
	    String strDocumentType = indoc.getDocumentElement().getAttribute(AmzLiterals.A_DOCUMENT_TYPE);
	    Document responseOutDoc = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER);
	    Element eleResponseOutDocc = responseOutDoc.getDocumentElement();
	    eleResponseOutDocc.setAttribute(AmzLiterals.A_ORDER_NO, strOrderNo);
	    eleResponseOutDocc.setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEntCode);
	    eleResponseOutDocc.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY, strOrderHKey);
	    eleResponseOutDocc.setAttribute(AmzLiterals.A_DOCUMENT_TYPE, strDocumentType);
	    SCXmlUtil.createChild(eleResponseOutDocc, AmzLiterals.E_ORDER_LINES);
	    logger.info("class: AmzRequestOrderCancellation | method: createReturnDocument -- Ends");
	    logger.endTimer("class: AmzRequestOrderCancellation | method: createReturnDocument -- Ends");
		
	    return responseOutDoc;
	}
		
	/**
	 * @param env
	 * @param indoc
	 * @param returnOutDoc
	 * @param eleOrdLines
	 * @param strReason
	 * @param strComments
	 * @throws Exception
	 * This method call getOrderList API and store amazon Order in HashMap
	 */
	private void findAmazonOrderId(YFSEnvironment env, Document indoc,	Document returnOutDoc, Element eleOrdLines, String strReason,String strComments) throws Exception {
		logger.beginTimer("class: AmzRequestOrderCancellation | method: findAmazonOrderId -- Starts");
		logger.info("class: AmzRequestOrderCancellation | method: findAmazonOrderId -- Starts");
		NodeList nOrderLine = eleOrdLines.getElementsByTagName(AmzLiterals.E_ORDER_LINE);
		if (nOrderLine.getLength() > 0) {
			Document getOrdListInDoc = createGetOrderListRequest(indoc.getDocumentElement());
			logger.debug("class: AmzRequestOrderCancellation | method: findAmazonOrderId | getOrderList input Document: "+ AmzXMLUtil.getString(getOrdListInDoc));
			Document getOrderListOutDoc = AmzCommonUtil.invokeAPI(env,AmzCommonConstants.TEMPLATE_GET_ORDER_LIST_FOR_AMZ_CANCEL_ORDER,
					AmzCommonConstants.API_GET_ORDER_LIST, getOrdListInDoc);

			logger.debug("class: AmzRequestOrderCancellation | method: findAmazonOrderId | getOrderListOutDoc: "	+ SCXmlUtil.getString(getOrderListOutDoc));
			if (getOrderListOutDoc.getDocumentElement().getChildNodes()	.getLength() == 0) {
				YFSException yfse = new YFSException();
				yfse.setErrorCode(AmzLiterals.STR_INVALID_CANCEL_REQ_ERROR_CODE+"GET_ORDER_LIST");
				yfse.setErrorDescription("Unable to retrieve the order information because getOrderList doesnt provide return result");
				prepareAndLogMessage(indoc, strEventType, strInputTypeResponse, strErrorResponse, strAmazonOrder, yfse.getErrorDescription(), 0);
				throw yfse;					
			}

			HashSet<String> amazonOrderList = getAmazonOrderId(env,indoc,getOrderListOutDoc, indoc,	 returnOutDoc);
			if (amazonOrderList.isEmpty()) {
				YFSException yfse = new YFSException();
				yfse.setErrorCode(AmzLiterals.STR_INVALID_CANCEL_REQ_ERROR_CODE+"INVALID_ORDER_LINE");
				yfse.setErrorDescription("The cancellation request does not contain a valid order line or Amazon Order ID  or Order status is already in In-Trasit or Cancelled.");
				prepareAndLogMessage(indoc, strEventType, strInputTypeResponse, strErrorResponse, strAmazonOrder, yfse.getErrorDescription(), 0);
				throw yfse;	
			}
			
			for (String strAmazonOrderId : amazonOrderList) {
				 prepareAndLogMessage(indoc,strEventType,strInputTypeRequest,null,strAmazonOrder,null,0);
               
				logger.debug("class: AmzRequestOrderCancellation | method: findAmazonOrderId | Prepare amazon cancellation request for AmazonOrderId: "+ strAmazonOrderId);
				handleCancelReqForOrder(env,strAmazonOrderId, strReason,strComments,getOrderListOutDoc, returnOutDoc);
			}
		} else {
			YFSException yfse = new YFSException();
			yfse.setErrorCode(AmzLiterals.STR_INVALID_CANCEL_REQ_ERROR_CODE+"ORDER_LINE_MISSING");
			yfse.setErrorDescription("The cancellation request does not contain order line ");
			prepareAndLogMessage(indoc, strEventType, strInputTypeResponse, strErrorResponse, strAmazonOrder, yfse.getErrorDescription(), 0);
			throw yfse;	
		}
		logger.info("class: AmzRequestOrderCancellation | method: findAmazonOrderId -- Ends");
		logger.endTimer("class: AmzRequestOrderCancellation | method: findAmazonOrderId -- Ends");
		
	}
	
	/**
	 * @param env
	 * @param strAmazonOrderId
	 * @param strReason
	 * @param strComments
	 * @param getOrderListOutDoc
	 * @param returnOutDoc
	 * @throws Exception
	 *  Method to send request to Amazon and Validate output message
	 */
	private void handleCancelReqForOrder(YFSEnvironment env,String strAmazonOrderId,String strReason, String strComments, Document getOrderListOutDoc, Document returnOutDoc)throws Exception {
		logger.beginTimer("class: AmzRequestOrderCancellation | method: handleCancelReqForOrder -- Starts");
		logger.info("class: AmzRequestOrderCancellation | method: handleCancelReqForOrder -- Starts");
		String strInputQuery = prepareCancellationQueryRequest(strAmazonOrderId,strReason, strComments);
		JSONObject payload = new JSONObject();
		payload.put(AmzLiterals.A_JS_QUERY, strInputQuery);
		logger.debug("class: AmzRequestOrderCancellation | method: handleCancelReqForOrder | Payload to CancelOrder: " + payload.toString());
		
		String output=callAmazonOrderAPI(payload,strAmazonOrderId,inputDoc);
		
		logger.debug("class: AmzRequestOrderCancellation | method: handleCancelReqForOrder | AmazonCancelOrder Response: " + output);

		JSONObject jsonObject = new JSONObject(output);
		validateResponseMessage(output);
		prepareAndLogMessage(inputDoc,strEventType,strInputTypeResponse,strSuccessResponse,strAmazonOrder,"Order Cancelled Succesfully",200);
		
		String strState = jsonObject.getJSONObject("data").getJSONObject("cancelOrder").getJSONObject("cancellation").getString("state");
		logger.debug("class: AmzRequestOrderCancellation | method: handleCancelReqForOrder | AmazonOutResponse State: " + strState);

		String strId = jsonObject.getJSONObject("data")	.getJSONObject("cancelOrder").getJSONObject("cancellation").getString("id");
		logger.debug("class: AmzRequestOrderCancellation | method: handleCancelReqForOrder | AmazonOutResponse Id: " + strId);

		Document outDocForOrder = processCancellationState(env, strState,strAmazonOrderId, strId, getOrderListOutDoc);
		logger.debug("class: AmzRequestOrderCancellation | method: handleCancelReqForOrder | outDocForOrder: " + SCXmlUtil.getString(outDocForOrder));

		if (outDocForOrder != null) {
			prepareResponseDoc = prepareResponseMessage(outDocForOrder,strState);
			logger.debug("class: AmzRequestOrderCancellation | method: handleCancelReqForOrder |prepareResponseDoc: "+ SCXmlUtil.getString(prepareResponseDoc));
			appendOrderLinesToResponse(returnOutDoc,prepareResponseDoc);
		}
		logger.info("class: AmzRequestOrderCancellation | method: handleCancelReqForOrder -- Ends");
		logger.endTimer("class: AmzRequestOrderCancellation | method: handleCancelReqForOrder -- Ends");
	}
	

	/**
	 * @param payload
	 * @param strAmazonOrderId
	 * @param inputDoc
	 * @return
	 * This method will invoke AmazonCancelOrder API from another class and handle if any exception occurred.
	 */
	private String callAmazonOrderAPI(JSONObject payload,String strAmazonOrderId , Document inputDoc) {
		logger.info("class: AmzRequestOrderCancellation | method: callAmazonOrderAPI -- Starts");
		logger.beginTimer("class: AmzRequestOrderCancellation | method: callAmazonOrderAPI -- Starts");
	
		String output=null;
		try {
			 logger.debug("class: AmzRequestOrderCancellation | method: callAmazonOrderAPI | Payload to CancelOrder: " + payload.toString());
			  output = AmzCancelOrderInAmazon.invokeAmazonPostCall(payload,inputDoc);
			 logger.debug("class: AmzRequestOrderCancellation | method: callAmazonOrderAPI | Amazon Order API Response: " + output);
				
			}catch (SocketTimeoutException e) {
				YFSException yfse = new YFSException();
				yfse.setErrorCode(AmzLiterals.STR_INVALID_CANCEL_REQ_ERROR_CODE+"SOCKET_TIME_OUT");
				yfse.setErrorDescription("The request timed out while trying to communicate with the service. Please check the network connectivity or service status.");
				prepareAndLogMessage(inputDoc,strEventType,strInputTypeResponse,strErrorResponse,strAmazonOrderId,yfse.getErrorDescription(),500);
				logger.error("SocketTimeoutException: occurred during communication with the service", e);
				throw yfse;	 
		    }
			 catch (IOException e) {
			      	logger.error("IOException occurred during communication with the service", e);
			 		YFSException yfse = new YFSException();
			 		yfse.setErrorCode(AmzLiterals.STR_INVALID_CANCEL_REQ_ERROR_CODE+"IO_EXCEPTION");
			 		yfse.setErrorDescription("IOException occurred during communication with the Amazon API");
			 		prepareAndLogMessage(inputDoc,strEventType,strInputTypeResponse,strErrorResponse,strAmazonOrder,yfse.getErrorDescription(),500);
			 		throw yfse;	 
			}
			catch (Exception e) {
				logger.error("Unexpected error occurred", e);
				YFSException yfse = new YFSException();
				yfse.setErrorCode(AmzLiterals.STR_INVALID_CANCEL_REQ_ERROR_CODE+"EXCEPTION");
				yfse.setErrorDescription("An unexpected error occurred during the operation");
				prepareAndLogMessage(inputDoc,strEventType,strInputTypeResponse,strErrorResponse,strAmazonOrder,yfse.getErrorDescription(),500);
				throw yfse;	 
		    }
		logger.info("class: AmzRequestOrderCancellation | method: callAmazonOrderAPI -- Ends");
		logger.endTimer("class: AmzRequestOrderCancellation | method: callAmazonOrderAPI -- Ends");
		return output;
	}
	
	// Helper method to create the request for getting the order list
	/**
	 * @param env
	 * @param eleOrd
	 * @return
	 * 
	 */
		private Document createGetOrderListRequest(Element eleOrd) {
		logger.beginTimer("class: AmzRequestOrderCancellation | method: createGetOrderListRequest -- Starts");
		logger.info("class: AmzRequestOrderCancellation | method: createGetOrderListRequest -- Starts");
		Document getOrdListInDoc = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER);
		Element eleOrderInDoc = getOrdListInDoc.getDocumentElement();
		eleOrderInDoc.setAttribute(AmzLiterals.A_ORDER_NO,eleOrd.getAttribute(AmzLiterals.A_ORDER_NO));
		eleOrderInDoc.setAttribute(AmzLiterals.A_ENTERPRISE_CODE,eleOrd.getAttribute(AmzLiterals.A_ENTERPRISE_CODE));
		eleOrderInDoc.setAttribute(AmzLiterals.A_DOCUMENT_TYPE,eleOrd.getAttribute(AmzLiterals.A_DOCUMENT_TYPE));
		eleOrderInDoc.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY,eleOrd.getAttribute(AmzLiterals.A_ORDER_HEADER_KEY));
		logger.info("class: AmzRequestOrderCancellation | method: createGetOrderListRequest -- Ends");
		logger.endTimer("class: AmzRequestOrderCancellation | method: createGetOrderListRequest -- Ends");
		return getOrdListInDoc;
		}

	// Helper method to process the cancellation state and return updated document

	/**
	 * @param env
	 * @param strState
	 * @param strAmazonOrderId
	 * @param strId
	 * @param getOrderListOutDoc
	 * @return
	 * @throws RemoteException
	 * @throws YIFClientCreationException
	 * @throws YFSException
	 * @throws XPathExpressionException
	 */
	private Document processCancellationState(YFSEnvironment env, String strState, String strAmazonOrderId, String strId,Document getOrderListOutDoc) throws RemoteException, YIFClientCreationException, YFSException, XPathExpressionException {
		logger.info("class: AmzRequestOrderCancellation | method: processCancellationState -- Starts");
		logger.beginTimer("class: AmzRequestOrderCancellation | method: processCancellationState -- Starts");
		 
		Document outDocForOrder = null;
	    switch (strState) {
	        case "SUCCESS":
	            outDocForOrder = getOrderLineDetails(env,strAmazonOrderId, strState, strId, getOrderListOutDoc);
	             logger.debug("class: AmzRequestOrderCancellation | method: processCancellationState ||SUCCESS - outDocForOrder" + SCXmlUtil.getString(outDocForOrder));
	            AmzCommonUtil.callAPI(env, outDocForOrder, AmzCommonConstants.API_CHANGE_ORDER, null);
	            break;
	            
	        case "REJECTED":
	            outDocForOrder = getOrderLineDetails(env, strAmazonOrderId, strState, strId, getOrderListOutDoc);
	             logger.debug("class: AmzRequestOrderCancellation | method: processCancellationState ||REJECTED - outDocForOrder" + SCXmlUtil.getString(outDocForOrder));
	            createNewException(env, outDocForOrder, strAmazonOrderId,inputDoc);
	            break;
	            
	        case "PENDING":
	        	outDocForOrder = getOrderLineDetails(env, strAmazonOrderId, strState, strId,  getOrderListOutDoc); 
	        	logger.debug("class: AmzRequestOrderCancellation | method: processCancellationState ||PENDING - outDocForOrder" + SCXmlUtil.getString(outDocForOrder));
	        	AmzCommonUtil.callAPI(env, outDocForOrder, AmzCommonConstants.API_CHANGE_ORDER, null);
	            break;
	        default:
	            logger.debug("class: AmzRequestOrderCancellation | method: processCancellationState  | Unknown state" +strState);
	            break;
	    }
	    logger.info("class: AmzRequestOrderCancellation | method: processCancellationState -- Ends");
	     logger.debug("class: AmzRequestOrderCancellation | method: processCancellationState ||Return- outDocForOrder" + SCXmlUtil.getString(outDocForOrder));
	    logger.endTimer("class: AmzRequestOrderCancellation | method: processCancellationState -- Ends");
		  
	    return outDocForOrder;
	}

	
	/**
	 * @param eleOutDocResOrderLines
	 * @param outDocResponse
	 * // Helper method to append OrderLine elements from outDocResponse to the response document
	 */
	private void appendOrderLinesToResponse(Document returnOutDoc, Document outDocResponse) {
		logger.info("class: AmzRequestOrderCancellation | method: appendOrderLinesToResponse -- Starts");
		logger.beginTimer("class: AmzRequestOrderCancellation | method: appendOrderLinesToResponse -- Starts");
		Element eleOutDocResOrderLines = (Element) returnOutDoc.getDocumentElement().getElementsByTagName("OrderLines").item(0);
		NodeList nlOrderLine = outDocResponse.getElementsByTagName(AmzLiterals.E_ORDER_LINE);
	    for (int i = 0; i < nlOrderLine.getLength(); i++) {
	        Element eleOrderLine = (Element) nlOrderLine.item(i);	        
	        Element importedOrderLine = (Element) returnOutDoc.importNode(eleOrderLine, true);
	     // Remove the Extn element from the imported OrderLine element
	        NodeList extnElements = importedOrderLine.getElementsByTagName(AmzLiterals.E_EXTN);
	        for (int j = 0; j < extnElements.getLength(); j++) {
	            importedOrderLine.removeChild(extnElements.item(j)); // Remove the Extn element
	        }
	        eleOutDocResOrderLines.appendChild(importedOrderLine);   
	    }
	    logger.info("class: AmzRequestOrderCancellation | method: appendOrderLinesToResponse -- Ends");
	    logger.endTimer("class: AmzRequestOrderCancellation | method: appendOrderLinesToResponse -- Ends");
	    
		}

	// Helper method to create and exception if Amazon order state is cancelled.
	/**
	 * @param env
	 * @param outDocForOrder
	 * @param amazonOrderId
	 * @throws RemoteException
	 * @throws YIFClientCreationException
	 * @throws JSONException
	 */
	private void createNewException(YFSEnvironment env, Document outDocForOrder, String amazonOrderId, Document indoc) throws RemoteException, YIFClientCreationException {
	    logger.info("class: AmzRequestOrderCancellation | method: createNewException -- Starts");
	    logger.beginTimer("class: AmzRequestOrderCancellation | method: createNewException -- Starts");
	    
	    String strExceptionType = "AmazonCancelOrderException";	    
	    // Extracting the root element of the order document
	    Element eleOutDocForOrder = outDocForOrder.getDocumentElement();	    
	    // Ensure the attributes are present in outDocForOrder
	    String strOrderHeaderKey = eleOutDocForOrder.getAttribute(AmzLiterals.A_ORDER_HEADER_KEY);
	    String strOrderNo = eleOutDocForOrder.getAttribute(AmzLiterals.A_ORDER_NO);
	     // Create the new exception document
	    Document docInCreateException = SCXmlUtil.createDocument(AmzLiterals.E_INBOX);
	    Element eleInbox = docInCreateException.getDocumentElement();
	    
	    // Set the attributes for the exception
	    eleInbox.setAttribute(AmzLiterals.A_ACTIVE_FLAG, AmzLiterals.STR_VAL_Y);
	    eleInbox.setAttribute(AmzLiterals.A_EXCEPTION_TYPE, strExceptionType);
	    eleInbox.setAttribute(AmzLiterals.A_CONSOLIDATE, AmzLiterals.STR_VAL_Y);
	    eleInbox.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY, strOrderHeaderKey);
	    eleInbox.setAttribute(AmzLiterals.A_ORDER_NO, strOrderNo);
	    
	    // Construct the description for the exception
	    String detailDesc = "Amazon Order : " + amazonOrderId + " Cancel Request Rejected by Amazon";
	    eleInbox.setAttribute(AmzLiterals.A_DETAIL_DESC, detailDesc);
	    eleInbox.setAttribute(AmzLiterals.A_DESC, detailDesc);
	    
	    // Log the input to the createException API
	     logger.debug("class: AmzRequestOrderCancellation | method: createNewException | Input to createException API is :: " + SCXmlUtil.getString(docInCreateException));
	    
	    try {
	        // Call the API to create the exception
	        AmzCommonUtil.callAPI(env, docInCreateException, AmzCommonConstants.API_CREATE_EXCEPTION, null);
	    } catch (YFSException e) {
	    	YFSException yfse = new YFSException();
			yfse.setErrorCode(AmzLiterals.STR_INVALID_CANCEL_REQ_ERROR_CODE+"CREATE_EXCEPTION_API");
			yfse.setErrorDescription("The cancellation request does not contain valid order lines. No amazon order is associated with it.");
			prepareAndLogMessage(indoc, strEventType, strInputTypeResponse, strErrorResponse, strAmazonOrder, yfse.getErrorDescription(), 0);
			throw yfse;	
	    }
	    logger.info("class: AmzRequestOrderCancellation | method: createNewException");	    
	    logger.endTimer("class: AmzRequestOrderCancellation | method: createNewException -- Ends");
	    
	}

	// Helper method to append and remove attributes Order Line elements from outDocResponse to the response document
	/**
	 * @param env
	 * @param outDocForOrder
	 * @param strState
	 * @return
	 */
	private Document prepareResponseMessage(Document outDocForOrder, String strState) {
		 logger.beginTimer("class: AmzRequestOrderCancellation | method: prepareResponseMessage -- Starts");
		 logger.info("class: AmzRequestOrderCancellation | method: prepareResponseMessage -- Starts");
		NodeList orderLineNodes = outDocForOrder.getElementsByTagName(AmzLiterals.E_ORDER_LINE);
        for (int i = 0; i < orderLineNodes.getLength(); i++) {
            Node orderLineNode = orderLineNodes.item(i);
            if (orderLineNode.getNodeType() == Node.ELEMENT_NODE) {
                Element orderLineElement = (Element) orderLineNode;
                if (orderLineElement.hasAttribute(AmzLiterals.A_ACTION)) {
                    orderLineElement.removeAttribute(AmzLiterals.A_ACTION);
                }
                // Update the AmzCancellationState attribute
                orderLineElement.setAttribute("AmzCancellationReqState", strState);
            }
        }

         logger.debug("class: AmzRequestOrderCancellation | method: prepareResponseMessage.outDocForOrder" +SCXmlUtil.getString(outDocForOrder));
        logger.info("class: AmzRequestOrderCancellation | method: prepareResponseMessage -- Ends");
        logger.endTimer("class: AmzRequestOrderCancellation | method: prepareResponseMessage -- Ends");
        
        // Return the modified document
        return outDocForOrder;    
	}
	
		
	/**
	 * @param env
	 * @param strAmazonOrderId
	 * @param strReason
	 * @param strComments
	 * @return
	 * This method constructs a GraphQL mutation query to cancel an order on Amazon. 
	 * It takes the Amazon order ID, cancellation reason, and additional comments as input and generates a mutation query that is then sent to Amazon's backend API. 
	 *
	 */
	
	private String prepareCancellationQueryRequest(String strAmazonOrderId, String strReason,
			String strComments) {
		 logger.beginTimer("class: AmzRequestOrderCancellation | method: PrepareCancellationQueryRequest -- Starts");
		 logger.info("class: AmzRequestOrderCancellation | method: PrepareCancellationQueryRequest -- Starts");
		  logger.debug("AmzRequestOrderCancellation.PrepareCancellationQueryRequest value of strAmazonOrderId: " + strAmazonOrderId);
		  logger.debug("AmzRequestOrderCancellation.PrepareCancellationQueryRequest value of strReason: " + strReason);
		  logger.debug("AmzRequestOrderCancellation.PrepareCancellationQueryRequest value of strComments: " + strComments);
		 String mutationQuery = 
			        "mutation cancelOrder {\n" +
			        "  cancelOrder(\n" +
			        "    orderIdentifier: {\n" +
			        "      orderId: \"" + strAmazonOrderId + "\"\n" +
			        "    }\n" +
			        "    input: {\n" +
			        "      aliases: {\n" +
			        "        aliasId: \"1234\",\n" + 
			        "        aliasType: \"EXTERNAL_CANCELLATION_ID\"\n" +
			        "      }\n" +
			        "      reason: " + strReason + "\n" +
			        "      additionalComments: \"" + strComments + "\"\n" +
			        "    }\n" +
			        "  ) {\n" +
			        "    cancellation {\n" +
			        "      id\n" +
			        "      state\n" +
			        "      aliases {\n" +
			        "        aliasId\n" +
			        "        aliasType\n" +
			        "      }\n" +
			        "      reason\n" +
			        "      additionalComments\n" +
			        "      createdAt\n" +
			        "      updatedAt\n" +
			        "      requestedBy\n" +
			        "      canceledFor {\n" +
			        "        orderLineItems {\n" +
			        "          lineItem {\n" +
			        "            id\n" +
			        "          }\n" +
			        "          amount {\n" +
			        "            value\n" +
			        "            unit\n" +
			        "          }\n" +
			        "        }\n" +
			        "      }\n" +
			        "    }\n" +
			        "  }\n" +
			        "}";

			    // Log the generated query
			    logger.debug("class: AmzRequestOrderCancellation | method: PrepareCancellationQueryRequest.mutationQuery Query is :: \n" +mutationQuery);
			   logger.info("class: AmzRequestOrderCancellation | method: PrepareCancellationQueryRequest -- Ends");
			   logger.endTimer("class: AmzRequestOrderCancellation | method: PrepareCancellationQueryRequest -- Ends");
			    
				return mutationQuery;	
	}
	
	
	/**
	 * @param env
	 * @param inDoc
	 * @param getOrderListOutDoc
	 * @return
	 * @throws Exception
	 * This method compares the order line keys between the input document (inDoc) and the output document getOrderListOutDoc. 
	 * It ensures that the order line items match, and extracts the ExtnAmazonOrderId for the cancellation process. 
	 * If there are mismatches or missing information, an exception is thrown.
	 */
	private HashSet<String> getAmazonOrderId(YFSEnvironment env ,Document inDoc, Document getOrderListOutDoc,Document indoc,Document returnOutDoc) throws Exception {
		logger.beginTimer("class: AmzRequestOrderCancellation | method: compareOrderLineKeys -- Starts");
		logger.info("class: AmzRequestOrderCancellation | method: compareOrderLineKeys -- Starts");
	     HashSet<String> getExtnAmzOrdIdMap = new HashSet<>();
        // Extract OrderLine elements from inDoc
        NodeList nInDocOrderLines = inDoc.getElementsByTagName(AmzLiterals.E_ORDER_LINE);

        // Loop through the OrderLines in the input document
        for (int i = 0; i < nInDocOrderLines.getLength(); i++) {
            Element eleInDocOrderLine = (Element) nInDocOrderLines.item(i);
            String strInDocOrderLineKey = eleInDocOrderLine.getAttribute(AmzLiterals.A_ORDER_LINE_KEY);
            String strInDocOLPrimeLineNo = eleInDocOrderLine.getAttribute(AmzLiterals.A_PRIME_LINE_NO);
            String strInDocOLSubLineNo = eleInDocOrderLine.getAttribute(AmzLiterals.A_SUB_LINE_NO);
            //validate InDoc OrderLineKey
            validateOrderLineInfo(strInDocOrderLineKey,strInDocOLPrimeLineNo,strInDocOLSubLineNo,inDoc);
            
            // Find matching eleGOLOrderLine in the output document
            	Element eleGetOrderList= getOrderListOutDoc.getDocumentElement();
            	String enterpriseCode = SCXmlUtil.getXpathAttribute(eleGetOrderList, "/OrderList/Order/@EnterpriseCode");
                Element eleGOLOrderLine = SCXmlUtil.getXpathElement(eleGetOrderList, "/OrderList/Order/OrderLines/OrderLine[( @OrderLineKey='" + strInDocOrderLineKey + "' ) or ( @PrimeLineNo='" + strInDocOLPrimeLineNo + "' and @SubLineNo='" + strInDocOLSubLineNo + "' )]");
                if (!YFCObject.isVoid(eleGOLOrderLine)) {
                String strGOLOrderLineKey = eleGOLOrderLine.getAttribute(AmzLiterals.A_ORDER_LINE_KEY);
                String strGOLOLinePrimeNo = eleGOLOrderLine.getAttribute(AmzLiterals.A_PRIME_LINE_NO);
                String strGOLOLineSubLineNo = eleGOLOrderLine.getAttribute(AmzLiterals.A_SUB_LINE_NO);
                eleGOLOrderLine.setAttribute(AmzLiterals.A_ENTERPRISE_CODE, enterpriseCode);
                                
             // Check if Order Line Key is not void and matches with Indoc
            	boolean isOrderLineKeyValid = !YFCObject.isVoid(strGOLOrderLineKey) && strInDocOrderLineKey.equalsIgnoreCase(strGOLOrderLineKey);
             // Check if Prime Line No is not void and matches with Indoc
            	boolean isPrimeLineNoValid = !YFCObject.isVoid(strGOLOLinePrimeNo) && strInDocOLPrimeLineNo.equalsIgnoreCase(strGOLOLinePrimeNo);
           	// Check if Sub Line No is not void and matches with Indoc
            	boolean isSubLineNoValid = !YFCObject.isVoid(strGOLOLineSubLineNo) && strInDocOLSubLineNo.equalsIgnoreCase(strGOLOLineSubLineNo);

            	if (isOrderLineKeyValid || (isPrimeLineNoValid && isSubLineNoValid)) {
            		validateOrderLineExtn(env,eleGOLOrderLine, getExtnAmzOrdIdMap,inDoc,returnOutDoc);
            	}
            	}else {
            		Element eleOutDocResOrderLines = (Element) returnOutDoc.getDocumentElement().getElementsByTagName("OrderLines").item(0);
        			Element eleOrderLine= SCXmlUtil.createChild(eleOutDocResOrderLines, AmzLiterals.E_ORDER_LINE);
        			eleOrderLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, strInDocOrderLineKey);
        			eleOrderLine.setAttribute(AmzLiterals.A_PRIME_LINE_NO, strInDocOLPrimeLineNo);
        			eleOrderLine.setAttribute(AmzLiterals.A_SUB_LINE_NO, strInDocOLSubLineNo);
        			eleOrderLine.setAttribute(AmzLiterals.STR_ERROR_MSG, "Invalid OrderLine Information");
        			eleOrderLine.setAttribute(AmzLiterals.A_ERROR_CODE, "INVALID_ORDERLINE");
        		
            	}
             
               
        }  
        logger.info("class: AmzRequestOrderCancellation | method: compareOrderLineKeys -- Ends");
        logger.endTimer("class: AmzRequestOrderCancellation | method: compareOrderLineKeys -- Ends");
		
        return getExtnAmzOrdIdMap;
    }


		/**
		 * @param orderLineKey
		 * @param primeLineNo
		 * @param subLineNo
		 * @param indoc
		 * @throws Exception
		 * This method validate OrderLine Level information, if orderLineKey or PrimeLineNo and SubLineNo is not available then throw exception
		 */
		private void validateOrderLineInfo(String orderLineKey, String primeLineNo, String subLineNo,Document indoc) throws Exception {
			logger.beginTimer("class: AmzRequestOrderCancellation | method: validateOrderLineInfo -- Starts");
			logger.info("class: AmzRequestOrderCancellation | method: validateOrderLineInfo -- Starts");
		if ((YFCObject.isVoid(orderLineKey)) && (YFCObject.isVoid(primeLineNo) || YFCObject.isVoid(subLineNo))) {
			YFSException yfse = new YFSException();
			yfse.setErrorCode(AmzLiterals.STR_INVALID_CANCEL_REQ_ERROR_CODE+"ORDER_LINE_INFO_MISSING");
			yfse.setErrorDescription("The cancellation request does not contain valid order line. OrderLineKey, PrimeLineNo and SubLineNo is missing");
			prepareAndLogMessage(indoc, strEventType, strInputTypeResponse, strErrorResponse, strAmazonOrder, yfse.getErrorDescription(), 0);
			throw yfse;	
			}
		logger.info("class: AmzRequestOrderCancellation | method: validateOrderLineInfo -- Ends");
        logger.endTimer("class: AmzRequestOrderCancellation | method: validateOrderLineInfo -- Ends");
	   }
	
	
	/**
	 * @param eleGOLOrderLine
	 * @param getExtnAmzOrdIdMap
	 * @throws Exception
	 */
		// Helper method to validate Order Line EXTN attributes
		
	private void validateOrderLineExtn(YFSEnvironment env,Element eleGOLOrderLine, HashSet<String> getExtnAmzOrdIdMap,Document indoc,Document returnOutDoc) throws Exception {
		 logger.beginTimer("class: AmzRequestOrderCancellation | method: validateOrderLineExtn -- Starts");
		 logger.info("class: AmzRequestOrderCancellation | method: validateOrderLineExtn -- Starts");
				
		if (!YFCObject.isVoid(eleGOLOrderLine)) {
			logger.debug("class: AmzRequestOrderCancellation | method: validateOrderLineExtn | eleGOLOrderLine:: " +SCXmlUtil.getString(eleGOLOrderLine) );
			String strGOLOrderLineKey = eleGOLOrderLine.getAttribute(AmzLiterals.A_ORDER_LINE_KEY);
			String strGOLPrimeLineNo = eleGOLOrderLine.getAttribute(AmzLiterals.A_PRIME_LINE_NO);
			String strGOLSubLineNo = eleGOLOrderLine.getAttribute(AmzLiterals.A_SUB_LINE_NO);
			String strEnterpriseCode = eleGOLOrderLine.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
			
			String strExtnAmzOrdId=null;
			String strIsPrimeEligible=null;
			String strIsAmazonFulfillable=null;
			Element eleOrdLineExtn = AmzXMLUtil.getChildElement(eleGOLOrderLine, AmzLiterals.E_EXTN);
			logger.debug("class: AmzRequestOrderCancellation | method: validateOrderLineExtn | eleOrdLineExtn:: " +SCXmlUtil.getString(eleOrdLineExtn) );
			
		if (!YFCObject.isVoid(eleOrdLineExtn)) {
		 strExtnAmzOrdId = eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID);
		 strIsPrimeEligible=eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_IS_PRIME_ELIGIBLE);
		 strIsAmazonFulfillable=eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_IS_AMAZON_FULFILLABLE);
		}
		logger.debug("class: AmzRequestOrderCancellation | method: validateOrderLineExtn | strExtnAmzOrdId:: " + strExtnAmzOrdId );
		logger.debug("class: AmzRequestOrderCancellation | method: validateOrderLineExtn | strIsPrimeEligible:: " +strIsPrimeEligible );
		logger.debug("class: AmzRequestOrderCancellation | method: validateOrderLineExtn | strIsAmazonFulfillable:: " +strIsAmazonFulfillable );
		
		if (!YFCObject.isVoid(strExtnAmzOrdId) &&((!YFCObject.isVoid(strIsAmazonFulfillable) && AmzLiterals.STR_VAL_Y.equalsIgnoreCase(strIsAmazonFulfillable)) || 
        		AmzLiterals.STR_VAL_Y.equalsIgnoreCase(strIsPrimeEligible))) {  
        		String amazonOrderStatus= validateAmazonOrderStatus(env,strExtnAmzOrdId,strEnterpriseCode);
        		logger.debug("class: AmzRequestOrderCancellation | method: validateOrderLineExtn | amazonOrderStatus:: " +amazonOrderStatus );
        		if("PENDING".equalsIgnoreCase(amazonOrderStatus)) {
        		getExtnAmzOrdIdMap.add(strExtnAmzOrdId);
        		}else {
        			Element eleOutDocResOrderLines = (Element) returnOutDoc.getDocumentElement().getElementsByTagName("OrderLines").item(0);
        			Element eleOrderLine= SCXmlUtil.createChild(eleOutDocResOrderLines, AmzLiterals.E_ORDER_LINE);
        			eleOrderLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, strGOLOrderLineKey);
        			eleOrderLine.setAttribute(AmzLiterals.A_PRIME_LINE_NO, strGOLPrimeLineNo);
        			eleOrderLine.setAttribute(AmzLiterals.A_SUB_LINE_NO, strGOLSubLineNo);
        			eleOrderLine.setAttribute(AmzLiterals.STR_ERROR_MSG, "The order "+strExtnAmzOrdId+" ,is either in a 'Cancelled' or 'In-Transit' state.");
        			eleOrderLine.setAttribute(AmzLiterals.A_ERROR_CODE, "INVALID_ORDERLINE");
        		}
        }
        // Check if Amazon ID is missing
        else if (YFCObject.isVoid(strExtnAmzOrdId)) {
        	YFSException yfse = new YFSException();
			yfse.setErrorCode(AmzLiterals.STR_INVALID_CANCEL_REQ_ERROR_CODE+"AMAZON_ORDER_NO");
			yfse.setErrorDescription("The cancellation request does not contain valid order line. Missing Amazon Order Id for OrderLineKey:" +strGOLOrderLineKey);
			prepareAndLogMessage(indoc, strEventType, strInputTypeResponse, strErrorResponse, strAmazonOrder, yfse.getErrorDescription(), 0);
			throw yfse;	
            }
        // Case when both attributes PrimeEligible is N and AmazonFulfillable is N
        else if (AmzLiterals.STR_VAL_N.equalsIgnoreCase(strIsPrimeEligible) &&AmzLiterals.STR_VAL_N.equalsIgnoreCase(strIsAmazonFulfillable)) {
        	YFSException yfse = new YFSException();
			yfse.setErrorCode(AmzLiterals.STR_INVALID_CANCEL_REQ_ERROR_CODE+"INVALID_OL_KEY");
			yfse.setErrorDescription("The cancellation request contain Invalid order line. Line is associated with merchant line: OrderLineKey: " +strGOLOrderLineKey);
			prepareAndLogMessage(indoc, strEventType, strInputTypeResponse, strErrorResponse, strAmazonOrder, yfse.getErrorDescription(), 0);
			throw yfse;	
        }
		}
		logger.info("class: AmzRequestOrderCancellation | method: validateOrderLineExtn -- Ends");
        logger.endTimer("class: AmzRequestOrderCancellation | method: validateOrderLineExtn -- Ends");
			
		}
	


	
	/**
	 * @param env
	 * @param amazonOrderId
	 * @return
	 * @throws YFSException
	 * @throws RemoteException
	 * @throws YIFClientCreationException
	 * This method will call amazon service to get the order status and return the amazon order status.
	 */
	private String validateAmazonOrderStatus(YFSEnvironment env,String amazonOrderId, String enterpriseCode) throws YFSException, RemoteException, YIFClientCreationException {
		logger.info("class: AmzRequestOrderCancellation | method: validateAmazonOrderStatus -- Starts");
        logger.endTimer("class: AmzRequestOrderCancellation | method: validateAmazonOrderStatus -- Starts");
		
		Document getOrderDetailsAmazonOutDoc=null;
		Document getOrderDetailsAmazonInDoc = SCXmlUtil.createDocument(AmzLiterals.STR_ORDER);
	    getOrderDetailsAmazonInDoc.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMZ_ORDER_ID, amazonOrderId);
	    getOrderDetailsAmazonInDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, enterpriseCode);
 	    getOrderDetailsAmazonOutDoc= AmzCommonUtil.callService(env, getOrderDetailsAmazonInDoc, AmzCommonConstants.SERVICE_AMAZON_GET_ORDER_DETAILS, null);
 	    Element eleGetOrderDetailsAmazonOutDoc= getOrderDetailsAmazonOutDoc.getDocumentElement();
 	    String strAmazonOrderStatus = SCXmlUtil.getXpathAttribute(eleGetOrderDetailsAmazonOutDoc,"/Root/data/order/packageInformation/details[not(@state='CANCELLED' or @state='IN_TRANSIT')]/@state" );
 	    logger.info("class: AmzRequestOrderCancellation | method: validateAmazonOrderStatus -- Ends");
 	    logger.endTimer("class: AmzRequestOrderCancellation | method: validateAmazonOrderStatus -- Ends");
		
 	    return strAmazonOrderStatus;
	}
	/**
	 * @param env
	 * @param strReason
	 * @return
	 * @throws YFSException
	 * @throws RemoteException
	 * @throws YIFClientCreationException
	 * This method validates the cancellation reason by checking it against a list of predefined common codes.
	 * If the reason provided by the user is valid, it returns true;
	 * otherwise,it returns false. This ensures that only acceptable cancellation reasons are processed.
	 */
	private Boolean validateCancelReasonCode(YFSEnvironment env, String strReason,Document indoc) throws YFSException, RemoteException, YIFClientCreationException {
		logger.beginTimer("class: AmzRequestOrderCancellation | method: validateCancelReasonCode -- Starts");
		logger.info("class: AmzRequestOrderCancellation | method: validateCancelReasonCode -- Starts");
		Boolean validateReasonCodeFlag=false;
		Document apiOutput=null;
		Document commonCodeInput = SCXmlUtil.createDocument(AmzLiterals.E_COMMON_CODE);
		commonCodeInput.getDocumentElement().setAttribute(AmzLiterals.A_CODE_TYPE,AmzLiterals.STR_VAL_AMZ_ORD_CANCEL_REASONS);
		try {
		apiOutput = AmzCommonUtil.callAPI(env, commonCodeInput, AmzCommonConstants.GET_COMMON_CODE_LIST, null);
		}catch (YFSException e) {
	    	 
			YFSException yfse = new YFSException();
			yfse.setErrorCode(AmzLiterals.STR_INVALID_CANCEL_REQ_ERROR_CODE+"COMMON_CODE_API");
			yfse.setErrorDescription(e.getErrorDescription());
			prepareAndLogMessage(indoc, strEventType, strInputTypeResponse, strErrorResponse, strAmazonOrder, yfse.getErrorDescription(), 0);
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
		logger.info("class: AmzRequestOrderCancellation | method: validateCancelReasonCode -- Ends");
		logger.endTimer("class: AmzRequestOrderCancellation | method: validateCancelReasonCode -- Ends");
		return validateReasonCodeFlag;
	}	
	
	
	/**
	 * @return
	 */
	// Helper method to get current time in yyyy-MM-dd'T'HH:mm:ss" format
	
	public static String getCurrentDateTime(){
		DateFormat dateFormat = new SimpleDateFormat(AmzCommonConstants.STR_DATE_FORMAT);
		String strDate = dateFormat.format(Calendar.getInstance().getTime());
		logger.debug("Current Date " + strDate);
		return strDate;
	}
	
	
	/**
	 * @param env
	 * @param strAmazonOrderId
	 * @param strStatus
	 * @param strIdValue
	 * @param indoc
	 * @param getOrderListOutDoc
	 * @return
	 * @throws YIFClientCreationException 
	 * @throws RemoteException 
	 * @throws YFSException 
	 * @throws XPathExpressionException 
	 * Method to prepare change order document when Amazon order state is Pending or Success
	 */
	
	private Document getOrderLineDetails(YFSEnvironment env, String strAmazonOrderId,
			String strStatus, String strIdValue, Document getOrderListOutDoc) throws YFSException, RemoteException, YIFClientCreationException, XPathExpressionException {
		logger.info("class: AmzRequestOrderCancellation | method: CompareAndGetOrderLineforAmazonOrderId -- Starts");
		logger.beginTimer("class: AmzRequestOrderCancellation | method: CompareAndGetOrderLineforAmazonOrderId -- Starts");
		
		 	Document outDoc = SCXmlUtil.createDocument(); 
            NodeList getOrderListOrderNode = getOrderListOutDoc.getElementsByTagName(AmzLiterals.E_ORDER);
            Element eleOrderGetOrderList = (Element) getOrderListOrderNode.item(0);
           // Create Order result element and copy Order attributes
            Element eleOutDoc = outDoc.createElement(AmzLiterals.E_ORDER);
            eleOutDoc.setAttribute(AmzLiterals.A_ORDER_NO, eleOrderGetOrderList.getAttribute(AmzLiterals.A_ORDER_NO));
            eleOutDoc.setAttribute(AmzLiterals.A_ENTERPRISE_CODE, eleOrderGetOrderList.getAttribute(AmzLiterals.A_ENTERPRISE_CODE));
            eleOutDoc.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY, eleOrderGetOrderList.getAttribute(AmzLiterals.A_ORDER_HEADER_KEY));
            eleOutDoc.setAttribute(AmzLiterals.A_OVERRIDE,AmzLiterals.STR_VAL_Y);
            Element eleOutDocOrderLines = outDoc.createElement(AmzLiterals.E_ORDER_LINES);
            eleOutDoc.appendChild(eleOutDocOrderLines);
            // Start : get value of amzConn.defaultRequestedBy
	        Document propertyDoc = SCXmlUtil.createDocument(AmzLiterals.E_PROPERTIES);
	        propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEntpriseCode);
	        genricPropertiesMap = AmzGetGenericProperty.getGenericProperties(env , propertyDoc);
	        String strRequestedBy = genricPropertiesMap.get(AmzCommonConstants.STR_DEFAULT_REQUESTED_BY_CODE_VAL);
		  // End: get value of amzConn.defaultRequestedBy
	        
            //GetOrderList OrderLines
            NodeList getOrderListOLinesNodes = eleOrderGetOrderList.getElementsByTagName(AmzLiterals.E_ORDER_LINE);

            for (int j = 0; j < getOrderListOLinesNodes.getLength(); j++) {
                Element eleOrderLineGetOrdList = (Element) getOrderListOLinesNodes.item(j);
                Element eleOrderLineGetOrdListExtn= AmzXMLUtil.getChildElement(eleOrderLineGetOrdList, AmzLiterals.E_EXTN);
                String strExtnAmazonOrderId = eleOrderLineGetOrdListExtn.getAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID);
                String strIsPrimeEligible = eleOrderLineGetOrdListExtn.getAttribute(AmzLiterals.A_EXTN_IS_PRIME_ELIGIBLE);
                String strIsAmazonFulfillable = eleOrderLineGetOrdListExtn.getAttribute(AmzLiterals.A_EXTN_IS_AMAZON_FULFILLABLE);
               
                if (!YFCObject.isVoid(strExtnAmazonOrderId) && (strAmazonOrderId.equals(strExtnAmazonOrderId)) &&
                        ((!YFCObject.isVoid(strIsAmazonFulfillable) && AmzLiterals.STR_VAL_Y.equalsIgnoreCase(strIsAmazonFulfillable)) || 
                        		AmzLiterals.STR_VAL_Y.equalsIgnoreCase(strIsPrimeEligible))) {  
                
                   // If there's a match, add this OrderLine to the result
                    Element eleOutDocOrderLine = outDoc.createElement(AmzLiterals.E_ORDER_LINE);
                    eleOutDocOrderLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, eleOrderLineGetOrdList.getAttribute(AmzLiterals.A_ORDER_LINE_KEY));
                    eleOutDocOrderLine.setAttribute(AmzLiterals.A_PRIME_LINE_NO, eleOrderLineGetOrdList.getAttribute(AmzLiterals.A_PRIME_LINE_NO));
                    eleOutDocOrderLine.setAttribute(AmzLiterals.A_SUB_LINE_NO, eleOrderLineGetOrdList.getAttribute("SubLineNo"));
                       // Add custom mappings when status is SUCCESS, if status is SUCCESS then cancel the order.
                    if (strStatus.equals(AmzLiterals.STR_SUCCESS)) {
                    	Element extnResult = outDoc.createElement(AmzLiterals.E_EXTN);
                    	// Map id, requestBy, and status
                    	eleOutDocOrderLine.setAttribute(AmzLiterals.A_ACTION,AmzLiterals.STR_CANCEL);
                    	extnResult.setAttribute(AmzLiterals.A_EXTN_AMZ_CANCEL_ID, strIdValue);
                    	extnResult.setAttribute(AmzLiterals.A_EXTN_AMZ_CANCEL_REQUESTBY, strRequestedBy);
                    	extnResult.setAttribute(AmzLiterals.A_EXTN_AMZ_CANCEL_STATUS, strStatus);
                    	String strDateTime= getCurrentDateTime();
                    	extnResult.setAttribute(AmzLiterals.A_EXTN_AMZ_CANCEL_CREATETS,strDateTime);
                    	extnResult.setAttribute(AmzLiterals.A_EXTN_AMZ_CANCEL_UPDATETS,strDateTime);
                    	eleOutDocOrderLine.appendChild(extnResult);
                    }
                    // Add custom mappings when status is PENDING, if status is PENDING then don't cancel the order and update the orderline Extn.
                     else if (strStatus.equals(AmzLiterals.STR_PENDING) ) {
                    	Element extnResult = outDoc.createElement(AmzLiterals.E_EXTN);
                    	extnResult.setAttribute(AmzLiterals.A_EXTN_AMZ_CANCEL_ID, strIdValue);
                    	extnResult.setAttribute(AmzLiterals.A_EXTN_AMZ_CANCEL_REQUESTBY, strRequestedBy);
                    	extnResult.setAttribute(AmzLiterals.A_EXTN_AMZ_CANCEL_STATUS, strStatus);
                    	String strDateTime= getCurrentDateTime();
                    	extnResult.setAttribute(AmzLiterals.A_EXTN_AMZ_CANCEL_CREATETS,strDateTime);
                    	extnResult.setAttribute(AmzLiterals.A_EXTN_AMZ_CANCEL_UPDATETS,strDateTime);
                    	eleOutDocOrderLine.appendChild(extnResult);
                    }
                    

                    eleOutDocOrderLines.appendChild(eleOutDocOrderLine);
                }             
           
            }

            if (eleOutDocOrderLines.getChildNodes().getLength() > 0) {
                outDoc.appendChild(eleOutDoc);
            }
        
         logger.debug("class: AmzRequestOrderCancellation | method: CompareAndGetOrderLineforAmazonOrderId | resultDoc" +SCXmlUtil.getString(outDoc));
 	    logger.info("class: AmzRequestOrderCancellation | method: CompareAndGetOrderLineforAmazonOrderId -- Ends");
 	    logger.endTimer("class: AmzRequestOrderCancellation | method: CompareAndGetOrderLineforAmazonOrderId -- Ends");
       
        return outDoc;
		
	}
	
	/**
	 * @param inDoc
	 * @param strEventType
	 * @param strAction
	 * @param strResponse
	 * @param strAmazonOrderId
	 * @param strErrorMessage
	 * @param httpResponse
	 * This method to prepare log message
	 */
	public void prepareAndLogMessage(Document inDoc, String strEventType, String strAction,String strResponse,String strAmazonOrderId, String strErrorMessage, int httpResponse) {
		String sMethodName = "AmzRequestOrderCancellation" + ".prepareAndLogMessage";
		logger.beginTimer(sMethodName);
		StringBuilder sBuilder = new StringBuilder(AmzCommonConstants.PIPE).append(strEventType)
				.append(AmzCommonConstants.PIPE);		
		Element eleOrd = inDoc.getDocumentElement();
		String strDateTime= getCurrentDateTime();
	    String strOrderNo=eleOrd.getAttribute(AmzLiterals.A_ORDER_NO);
	    String strEntCode=eleOrd.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
	    String strOHKey=eleOrd.getAttribute(AmzLiterals.A_ORDER_HEADER_KEY);	    		
	    appendIfNotVoid(sBuilder, AmzLiterals.A_ENTERPRISE_CODE, strEntCode);
	    appendIfNotVoid(sBuilder, "Event", strAction);
	    appendIfNotVoid(sBuilder, "EventTime", strDateTime);
	    appendIfNotVoid(sBuilder, "OMSOrderNo", strOrderNo);
	    appendIfNotVoid(sBuilder, AmzLiterals.A_ORDER_HEADER_KEY, strOHKey);
	    appendIfNotVoid(sBuilder, "AmazonOrderID", strAmazonOrderId);
	    
	    if (shouldAppendResponseDetails(strAction, strResponse)) {
	        appendResponseDetails(sBuilder, strResponse, strErrorMessage, httpResponse);
	    }
	    
	   	logger.debug(sMethodName +sBuilder.toString()); 
	   	 logger.debug(sMethodName +sBuilder.toString());
		logger.info(sBuilder.toString());
		logger.endTimer(sMethodName);
		}
	
	private void appendIfNotVoid(StringBuilder sBuilder, String key, String value) {
	    if (!YFCObject.isVoid(value)) {
	    	AmzCommonUtil.appendEntity(sBuilder, key, value);
	    }
	}
	private boolean shouldAppendResponseDetails(String strAction, String strResponse) {
	    return !YFCObject.isVoid(strAction) && strAction.equalsIgnoreCase(AmzLiterals.STR_RESPONSE) && !YFCObject.isVoid(strResponse);
	}

	private void appendResponseDetails(StringBuilder sBuilder, String strResponse, String strErrorMessage, int httpResponse) {
		AmzCommonUtil.appendEntity(sBuilder, "Response", strResponse);
	    
	    if (httpResponse != 0) {
	    	AmzCommonUtil.appendEntity(sBuilder, AmzLiterals.ATTR_HTTP_CODE, String.valueOf(httpResponse));
	    }
	    
	    if (strResponse.equalsIgnoreCase(AmzLiterals.STR_ERROR) && !YFCObject.isVoid(strErrorMessage)) {
	    	AmzCommonUtil.appendEntity(sBuilder, AmzLiterals.STR_ERROR_MSG, strErrorMessage);
	    }
	}

	/**
	 * @param jsonResponse
	 * @throws JSONException
	 * this method validate response code of cancel Order API(Amazon), if out message has error then throw exception.
	 */
	private static void validateResponseMessage(String jsonResponse) throws JSONException {
		    logger.beginTimer("class: AmzRequestOrderCancellation | method: validateResponseMessage -- Starts");
		    logger.info("class: AmzRequestOrderCancellation | method: validateResponseMessage -- Starts");
		    logger.debug("class: AmzRequestOrderCancellation | method: validateResponseMessage input doc is: " + jsonResponse);
		    Document outDocError = null;
		    JSONObject json = new JSONObject(jsonResponse);
			 if (json.has("errors")) {
		      try {
	            // Parse the JSON response
	            // Create the Sterling XML document
	            outDocError = SCXmlUtil.createDocument(AmzLiterals.E_ERRORS);	            
	            // Get the errors array from the JSON
	            JSONArray errors = json.getJSONArray("errors");
	            String errorMessage=null;
	            // Loop through the errors array and extract necessary information
	            for (int i = 0; i < errors.length(); i++) {
	                JSONObject error = errors.getJSONObject(i);
	                 errorMessage = error.optString("message", "Unknown error occurred");
	                JSONObject classification = error.optJSONObject("extensions").optJSONObject("classification");
	                if (classification != null) {
	                    String errorCode = classification.optString("errorCode", "Unknown code");
	                    String errorDescription = classification.optString("type", "Unknown type") + ": " + errorMessage;
	                    String code= classification.optString("code", "Unknown error type");
	                    // Create the error element in the Sterling XML document
	                    Element eleError = SCXmlUtil.createChild(outDocError.getDocumentElement(), "Error");
	                    eleError.setAttribute(AmzLiterals.A_ERROR_CODE, errorCode + ":" + code);
	                    eleError.setAttribute(AmzLiterals.A_ERROR_DESC,errorDescription);
	                }
	            }
	            
	        } catch (Exception exp) {
	            // Log error if JSON parsing or XML creation fails
	             logger.error("class: AmzRequestOrderCancellation | method: validateResponseMessage Exception: " + exp);
	 		  }
		      logger.info("class: AmzRequestOrderCancellation | method: validateResponseMessage -- Ends");
		      logger.endTimer("class: AmzRequestOrderCancellation | method: validateResponseMessage -- Ends");
				  
		      throwCustomExecption(outDocError);
	}
	}
	
	/**
	 * @param outDocError
	 * @return
	 * This method is helper method of validateResponseMessage to throw an exception
	 */
	public static Document throwCustomExecption(Document outDocError ) {
		logger.beginTimer("class: AmzCommonUtil | method: throwCustomExecption -- Starts");
		logger.info("class: AmzCommonUtil | method: throwCustomExecption -- Starts");
		String strErrorXML = SCXmlUtil.getString(outDocError);
		YFSException customYFSException = new YFSException(strErrorXML);
		logger.info("class: AmzCommonUtil | method: throwCustomExecption -- Ends");
		logger.endTimer("class: AmzCommonUtil | method: throwCustomExecption -- Ends");
		throw customYFSException;
	}
	
}

