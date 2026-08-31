/**
 * 
 */
package com.amazon.integrator.order.api;

import java.util.HashMap;
import java.util.Map;

import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.dom.YFCDocument;
import com.yantra.yfc.dom.YFCElement;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;

/**
 * @author Surajit 
 * This class will be called through Change Order On Cancel
 *         Event handler. This will form the input for calling Amazon Order
 *         Cancel Request.
 */
public class AmzInvokeAmazonCancelRequest {
	final YFCLogCategory logger = YFCLogCategory.instance(AmzInvokeAmazonCancelRequest.class);
	Map<String, String> mapGenericProps = null;
	/*
	 * This method invoke the amazon create order api
	 */
	public void prepareRequestAndInvoke(YFSEnvironment env, Document indoc) {
		logger.debug("Start Processing AmzInvokeAmazonCancelRequest:" + AmzXMLUtil.getString(indoc));
		boolean isCancelRequestRequired = false;

		// If Event trigerred from Amazon, then do not process
		String isCancelledFromAmazon = (String) env.getTxnObject("CancelledThroughDeliveryEvent");
		env.setTxnObject("CancelledThroughDeliveryEvent", null);
		
		if (!"Y".equalsIgnoreCase(isCancelledFromAmazon)) {
			// Get the Generic properties for Amazon
			Document inDocGetGenrcProperty = AmzXMLUtil.createDocument(AmzLiterals.E_PROPERTIES);
			inDocGetGenrcProperty.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, "DEFAULT");
			try {
				mapGenericProps = AmzGetGenericProperty.getGenericProperties(env,
						inDocGetGenrcProperty);
			} catch (XPathExpressionException e) {
				mapGenericProps = new HashMap<String, String>();
				mapGenericProps.put("amzConn.Merchant.RequestedBy", "MERCHANT");
				mapGenericProps.put("amzConn.Merchant.CancelComment", "Wrong item");
				mapGenericProps.put("amzConn.Merchant.RequestedBy", "ORDERED_BY_MISTAKE");
				e.printStackTrace();
			}

			// Form the Order Header Level Input
			Element eleOrder = indoc.getDocumentElement();
			YFCDocument amazonCancelRequestDoc = getAmazonCancelRequestDoc(eleOrder, mapGenericProps);

			// Check the lines and add to the input if BwP
			Element eleOrdLines = AmzXMLUtil.getChildElement(eleOrder, AmzLiterals.E_ORDER_LINES);
			NodeList nOrderLine = eleOrdLines.getElementsByTagName(AmzLiterals.E_ORDER_LINE);
			int iOrderLineLen = nOrderLine.getLength();
			for (int i = 0; i < iOrderLineLen; i++) {
				Element eleOrderLine = (Element) nOrderLine.item(i);
				Element eleOrdLineExtn = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_EXTN);
				if (!YFCObject.isVoid(eleOrdLineExtn)) {
					String strExtnAmzOrdId = eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID);
					String strExtnIsPrimeElg = eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_IS_PRIME_ELIGIBLE);
					String strExtnAmazonFulfillable = eleOrdLineExtn
							.getAttribute(AmzLiterals.A_EXTN_IS_AMAZON_FULFILLABLE);
					boolean isBWPElgline = false;
					if (!YFCObject.isVoid(strExtnIsPrimeElg) && !YFCObject.isVoid(strExtnAmazonFulfillable)
							&& AmzCommonConstants.STR_VAL_Y.equalsIgnoreCase(strExtnIsPrimeElg)
							&& AmzCommonConstants.STR_VAL_Y.equalsIgnoreCase(strExtnAmazonFulfillable)) {
						isBWPElgline = true;
					}
					logger.debug("OrderLineKey:" + eleOrderLine.getAttribute(AmzLiterals.A_ORDER_LINE_KEY));
					logger.debug("isBWPElgline:" + isBWPElgline);
					logger.debug("strExtnAmzOrdId:" + strExtnAmzOrdId);
					if (isBWPElgline) {
						// If a single BwP line exist, then we have to send the request to Amazon
						isCancelRequestRequired = true;
						// Add the Line item details
						logger.debug("Adding Line to Request");
						YFCElement cancelLineElem = amazonCancelRequestDoc.getDocumentElement()
								.getChildElement("OrderLines").createChild("OrderLine");
						cancelLineElem.setAttribute(AmzLiterals.A_PRIME_LINE_NO,
								eleOrderLine.getAttribute(AmzLiterals.A_PRIME_LINE_NO));
						cancelLineElem.setAttribute(AmzLiterals.A_SUB_LINE_NO,
								eleOrderLine.getAttribute(AmzLiterals.A_SUB_LINE_NO));
						cancelLineElem.setAttribute(AmzLiterals.A_ORDER_LINE_KEY,
								eleOrderLine.getAttribute(AmzLiterals.A_ORDER_LINE_KEY));
						cancelLineElem.setAttribute("RequestedBy",
								mapGenericProps.get("amzConn.Merchant.RequestedBy"));
					}
				}
			}

			if (isCancelRequestRequired) {
				// Send Cancellation Request to Amazon
				logger.debug("Call AmzConnReqToCancelAmzonOrder with Input:" + amazonCancelRequestDoc);
				AmzCommonUtil.invokeService(env, "AmzConnReqToCancelAmzonOrder", amazonCancelRequestDoc.getDocument());
			}
		}
	}

	// Create the Document that will be sent to Amazon and populate Header level
	// Elements and Attributes
	private YFCDocument getAmazonCancelRequestDoc(Element eleOrder, Map<String, String> mapGenericProps) {
		YFCDocument amazonCancelRequestDoc = YFCDocument.createDocument(AmzLiterals.E_ORDER);
		YFCElement amazonCancelRequestElem = amazonCancelRequestDoc.getDocumentElement();

		amazonCancelRequestElem.setAttribute(AmzLiterals.A_DOCUMENT_TYPE,
				eleOrder.getAttribute(AmzLiterals.A_DOCUMENT_TYPE));
		amazonCancelRequestElem.setAttribute(AmzLiterals.A_ENTERPRISE_CODE,
				eleOrder.getAttribute(AmzLiterals.A_ENTERPRISE_CODE));
		amazonCancelRequestElem.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY,
				eleOrder.getAttribute(AmzLiterals.A_ORDER_HEADER_KEY));
		amazonCancelRequestElem.setAttribute("OrderNo", eleOrder.getAttribute("OrderNo"));
		
		YFCElement cancelDetailsElem = amazonCancelRequestElem.createChild("CancelRequestDetails");
		if(isCustomerCancel(eleOrder)) {
			cancelDetailsElem.setAttribute("Comments", mapGenericProps.get("amzConn.Merchant.CancelComment"));
			cancelDetailsElem.setAttribute("CancelReason", mapGenericProps.get("amzConn.Merchant.CancelReason"));
		}else {
			cancelDetailsElem.setAttribute("Comments", "Non Customer Cancel");
			cancelDetailsElem.setAttribute("CancelReason", "OTHER");
		}

		amazonCancelRequestElem.createChild("OrderLines");

		return amazonCancelRequestDoc;
	}

	/**
	 * Return true if Reason Code in Note is "CustomerRequest"
	 * @param eleOrder
	 * @return
	 */
	private boolean isCustomerCancel(Element eleOrder) {
		Element eleOrdLines = AmzXMLUtil.getChildElement(eleOrder, AmzLiterals.E_ORDER_LINES);
		NodeList nOrderLine = eleOrdLines.getElementsByTagName(AmzLiterals.E_ORDER_LINE);
		for (int i = 0; i < nOrderLine.getLength(); i++) {
			Element eleOrderLine = (Element) nOrderLine.item(i);
			Element eleOrdLineNotes = AmzXMLUtil.getChildElement(eleOrderLine, "Notes");
			if (!YFCObject.isVoid(eleOrdLineNotes)) {
				NodeList nNote = eleOrdLineNotes.getElementsByTagName("Note");
				if(!YFCObject.isVoid(nNote)) {
					for (int j = 0; j < nNote.getLength(); j++) {
						Element eleNote = (Element) nNote.item(j);
						String noteReason = eleNote.getAttribute("ReasonCode");
						if("CustomerRequest".equals(noteReason)) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}
}
