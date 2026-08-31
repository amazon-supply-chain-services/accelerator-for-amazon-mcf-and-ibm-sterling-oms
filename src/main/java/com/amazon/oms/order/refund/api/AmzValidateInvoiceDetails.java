package com.amazon.oms.order.refund.api;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzLiterals;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

public class AmzValidateInvoiceDetails{
	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzValidateInvoiceDetails.class);
	
		/**
		 * @param env
		 * @param inDoc
		 * @return
		 * @throws Exception
		 */
		public Document verifyInvoiceTypeAndPostMessageToQ(YFSEnvironment env, Document inDoc) throws Exception {
		logger.beginTimer("class: AmzValidateInvoiceDetails | method: verifyInvoiceTypeAndPostMessageToQ -- Starts");
		logger.info("class: AmzValidateInvoiceDetails | method: verifyInvoiceTypeAndPostMessageToQ -- Starts");
		String strExtnAmazonRefundId=null;
		String strExtnAmazonOrderId=null;
		String strInvoiceType=null;
		try {
		   Element eleInDoc = inDoc.getDocumentElement();
		   String strEnterpriseCode=eleInDoc.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
		   NodeList nlInvoiceCollection = eleInDoc.getElementsByTagName(AmzLiterals.E_INVOICE_COLLECTION);
   		   for (int k = 0; k < nlInvoiceCollection.getLength(); k++) {
   			 Element elenlInvoiceCollection = (Element) nlInvoiceCollection.item(k);
			 Element eleOrderInvoice=SCXmlUtil.getChildElement(elenlInvoiceCollection,AmzLiterals.E_ORDER_INVOICE);
			 strInvoiceType =eleOrderInvoice.getAttribute(AmzLiterals.A_INVOICE_TYPE);
			 Element eleOrderInvoiceExtn = SCXmlUtil.getChildElement(eleOrderInvoice,AmzLiterals.E_EXTN);
			 strExtnAmazonRefundId= eleOrderInvoiceExtn.getAttribute(AmzLiterals.A_EXTN_AMZ_REFUND_ID);
			 strExtnAmazonOrderId= eleOrderInvoiceExtn.getAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID);
			 if (!YFCObject.isVoid(strExtnAmazonRefundId) && !YFCObject.isVoid(strExtnAmazonOrderId) 
					    && ( AmzLiterals.STR_CREDIT_MEMO.equals(strInvoiceType) || AmzLiterals.STR_RETURN.equals(strInvoiceType))) {
				 Document orderInvoiceDoc = SCXmlUtil.createDocument(AmzLiterals.E_ORDER_INVOICE);
				 Element eleOrderInvoiceInDoc = (Element) orderInvoiceDoc.importNode(eleOrderInvoice, true);
				 //eleOrderInvoiceInDoc.setAttribute(AmzLiterals.A_TASK_TYPE, "AMZ_CON_REFUND_INITIATED");
				 eleOrderInvoiceInDoc.setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);
				 orderInvoiceDoc.replaceChild(eleOrderInvoiceInDoc, orderInvoiceDoc.getDocumentElement());
				 logger.debug("class: AmzValidateInvoiceDetails | method: verifyInvoiceTypeAndPostMessageToQ | orderInvoiceDoc ::: "+SCXmlUtil.getString(orderInvoiceDoc) );
				 AmzCommonUtil.invokeService(env, AmzCommonConstants.SERVICE_AMZ_CONN_POST_COMPLETE_REFUND_REQ_MSG_TO_Q, orderInvoiceDoc);
				 prepareAndLogResponse(AmzLiterals.STR_SUCCESS, inDoc, "Message Succesfully Posted to Complete Refund Q");
			 }
   		   }
			
			
		}catch (YFSException e) {
			YFSException yfse = new YFSException();
			yfse.setErrorCode(AmzLiterals.STR_COMPLETE_REF_UPDATE_REQ_ERROR_CODE+e.getErrorCode());
			yfse.setErrorDescription(e.getMessage());
			prepareAndLogResponse(AmzLiterals.STR_ERROR, inDoc, e.getMessage());
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			YFSException yfse = new YFSException();
			yfse.setErrorCode(AmzLiterals.STR_COMPLETE_REF_UPDATE_REQ_ERROR_CODE+"EXCEPTION");
			yfse.setErrorDescription(e.getMessage());
			prepareAndLogResponse(AmzLiterals.STR_ERROR, inDoc, e.getMessage());
			throw yfse;
		}
		logger.info("class: AmzValidateInvoiceDetails | method: verifyInvoiceTypeAndPostMessageToQ -- Ends");
		logger.beginTimer("class: AmzValidateInvoiceDetails | method: verifyInvoiceTypeAndPostMessageToQ -- Ends");
		return inDoc;
		 	 
	}
	
	
	/**
	 * @param processStatus
	 * @param inDoc
	 * @param message
	 *  This method is responsible for preparing a response document and logging it based on the provided information. 
	 */
	public static void prepareAndLogResponse(String processStatus, Document inDoc, String message) {
		logger.beginTimer("class: AmzValidateInvoiceDetails | method: prepareAndLogResponse -- Starts");
		logger.info("class: AmzValidateInvoiceDetails | method: prepareAndLogResponse -- Starts");
		Element eleIndoc=inDoc.getDocumentElement();
		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE, "UPDATE_REFUND_STATUS");
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE, "COMPLETE_REFUND_STATUS");
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_EVENT, AmzLiterals.STR_RESPONSE);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO,eleIndoc.getAttribute(AmzLiterals.A_ORDER_NO) );
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE,eleIndoc.getAttribute(AmzLiterals.A_ENTERPRISE_CODE) );
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_DOCUMENT_TYPE,eleIndoc.getAttribute(AmzLiterals.A_DOCUMENT_TYPE) );
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_ORDER_HEADER_KEY,eleIndoc.getAttribute(AmzLiterals.A_ORDER_HEADER_KEY) );
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_PROCESS_STATUS, processStatus);
		if (!YFCObject.isVoid(message)) {
			if (processStatus.equalsIgnoreCase(AmzLiterals.STR_SUCCESS)) {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_MESSAGE, message);
			} else {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_ERROR_MSG, message);
			}
		}
		AmzCommonUtil.logAmzConnResponse(logInput);
		logger.info("class: AmzValidateInvoiceDetails | method: prepareAndLogResponse -- Ends");
		logger.endTimer("class: AmzValidateInvoiceDetails | method: prepareAndLogResponse -- Ends");
	}
	
	
}
