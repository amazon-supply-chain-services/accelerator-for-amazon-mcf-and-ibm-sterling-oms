package com.amazon.oms.order.api;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;

/*
 * This class verify is there any BWP eligible line to create order in amazon and
 * Is there any AmazonOrders to update the DesiredExecutionState to STARTED
 * and return the orderlist output doc.
 */
public class AmzVerfiyCreateOrderMessage {
	static final YFCLogCategory logger = YFCLogCategory.instance(AmzVerfiyCreateOrderMessage.class);

	/*
	 * This verify the create order ON Success Message
	 */
	public Document verfiyCreateOrderMessage(YFSEnvironment env, Document indoc) {

		logger.timer("class: AmzVerfiyCreateOrderMessage | method: verfiyCreateOrderMessage -- Starts");
		logger.info("class: AmzVerfiyCreateOrderMessage | method: verfiyCreateOrderMessage -- Starts");
		logger.debug("AmzCreateOrder.createOrderInAmazon input doc is: " + AmzXMLUtil.getString(indoc));

		Document outDoc = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER);

		List<String> amzCreateOrdElgPrimeLineNo = new ArrayList<>();
		List<String> amzOrderIdList = new ArrayList<>();

		Element eleOrd = indoc.getDocumentElement();
		Document getOrdListInDoc = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER);
		Element eleOrderInDoc = getOrdListInDoc.getDocumentElement();
		String strOrderNo = eleOrd.getAttribute(AmzLiterals.A_ORDER_NO);
		String strEnterpriseCode = eleOrd.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
		String strDocumentType = eleOrd.getAttribute(AmzLiterals.A_DOCUMENT_TYPE);
		eleOrderInDoc.setAttribute(AmzLiterals.A_ORDER_NO, eleOrd.getAttribute(AmzLiterals.A_ORDER_NO));
		eleOrderInDoc.setAttribute(AmzLiterals.A_ENTERPRISE_CODE, eleOrd.getAttribute(AmzLiterals.A_ENTERPRISE_CODE));
		eleOrderInDoc.setAttribute(AmzLiterals.A_DOCUMENT_TYPE, eleOrd.getAttribute(AmzLiterals.A_DOCUMENT_TYPE));
		logger.debug("AmzCreateOrder.createOrderInAmazon.getOrderList input Document is: "
				+ AmzXMLUtil.getString(getOrdListInDoc));
		if (!YFCObject.isVoid(strDocumentType) && !YFCObject.isVoid(strEnterpriseCode)
				&& !YFCObject.isVoid(strOrderNo)) {
			Document getOrderListOutDoc = AmzCommonUtil.invokeAPI(env,
					AmzCommonConstants.TEMPLATE_GET_ORDER_LIST_FOR_AMZ_CREATE_ORDER, AmzCommonConstants.API_GET_ORDER_LIST,
					getOrdListInDoc);
			Element eleOutOrderList = getOrderListOutDoc.getDocumentElement();
			logger.debug("AmzCreateOrder.createOrderInAmazon.getOrderList input Document is: "
					+ AmzXMLUtil.getString(eleOutOrderList));
			Element eleOutOrder = AmzXMLUtil.getChildElement(eleOutOrderList, AmzLiterals.E_ORDER);

			prepareOrderDocument(outDoc, eleOutOrder);
			Element eleOrdLines = AmzXMLUtil.getChildElement(eleOutOrder, AmzLiterals.E_ORDER_LINES);
			NodeList nOrderLine = eleOrdLines.getElementsByTagName(AmzLiterals.E_ORDER_LINE);
			verfiyBWPElgLineToCreateOrdInAmzon(nOrderLine, amzCreateOrdElgPrimeLineNo, amzOrderIdList);
			logger.debug("AmzCreateOrder.amzCreateOrdElgPrimeLineNo is: " + amzCreateOrdElgPrimeLineNo);
			int iamzCreateOrdElgPrimeLineNo = amzCreateOrdElgPrimeLineNo.size();
			int iamzOrderIdList = amzOrderIdList.size();
			logger.debug("AmzCreateOrder.iamzOrderIdList is: " + amzCreateOrdElgPrimeLineNo);

			if (iamzCreateOrdElgPrimeLineNo > 0 || iamzOrderIdList > 0) {
				outDoc.getDocumentElement().setAttribute("CreateUpdateOrder", AmzCommonConstants.STR_VAL_Y);
				AmzCommonUtil.invokeService(env, "AmzConnPostCreateReleaseOrdMsgToQ", outDoc);
			} else {
				logger.info("OrderNo :" + strOrderNo
						+ "| There are No eligible lines to create order in amazon or to update the execution status");
			}
		} else {
			logger.info(
					"OrderNo or DocumentType or EnterpriseCode is Missing Hence igoner this CreateOrder On Success Message");
		}
		logger.info("class: AmzVerfiyCreateOrderMessage | method: verfiyCreateOrderMessage -- Ends");
		logger.timer("class: AmzVerfiyCreateOrderMessage | method: verfiyCreateOrderMessage -- Ends");
		logger.debug("class: AmzVerfiyCreateOrderMessage | method: verfiyCreateOrderMessage : outDoc: "
				+ AmzXMLUtil.getString(outDoc));
		return indoc;

	}

	void prepareOrderDocument(Document outDoc, Element eleOutOrder) {

		Element eleOutOrd = outDoc.getDocumentElement();
		AmzXMLUtil.copyAttributes(eleOutOrd, eleOutOrder);

		Element eleOutOrderLines = AmzXMLUtil.getChildElement(eleOutOrder, AmzLiterals.E_ORDER_LINES);
		if (!YFCObject.isVoid(eleOutOrderLines)) {
			Element eleOutOrdLines = (Element) outDoc.importNode(eleOutOrderLines, true);
			eleOutOrd.appendChild(eleOutOrdLines);
		}

		Element eleOutOrderExtn = AmzXMLUtil.getChildElement(eleOutOrder, AmzLiterals.E_EXTN);
		if (!YFCObject.isVoid(eleOutOrderExtn)) {
			Element eleOutOrdExtn = (Element) outDoc.importNode(eleOutOrderExtn, true);
			eleOutOrd.appendChild(eleOutOrdExtn);
		}

		Element eleOutOrderPriceInfo = AmzXMLUtil.getChildElement(eleOutOrder, AmzLiterals.E_PRICE_INFO);
		if (!YFCObject.isVoid(eleOutOrderPriceInfo)) {
			Element eleOutOrdPriceInfo = (Element) outDoc.importNode(eleOutOrderPriceInfo, true);
			eleOutOrd.appendChild(eleOutOrdPriceInfo);
		}

		Element eleOutOrderPersonInfoShipTo = AmzXMLUtil.getChildElement(eleOutOrder,
				AmzLiterals.E_PERSON_INFO_SHIP_TO);
		if (!YFCObject.isVoid(eleOutOrderPersonInfoShipTo)) {
			Element eleOutOrdPersonInfoShipTo = (Element) outDoc.importNode(eleOutOrderPersonInfoShipTo, true);
			eleOutOrd.appendChild(eleOutOrdPersonInfoShipTo);

		}

		Element eleOutOrderOverallTotals = AmzXMLUtil.getChildElement(eleOutOrder, AmzLiterals.E_OVERALL_TOTALS);
		if (!YFCObject.isVoid(eleOutOrderOverallTotals)) {
			Element eleOutOrdOverallTotals = (Element) outDoc.importNode(eleOutOrderOverallTotals, true);
			eleOutOrd.appendChild(eleOutOrdOverallTotals);
		}

		Element eleOutOrderHeaderCharges = AmzXMLUtil.getChildElement(eleOutOrder, AmzLiterals.E_HEADER_CHARGES);
		if (!YFCObject.isVoid(eleOutOrderHeaderCharges)) {
			Element eleOutOrdOverallTotals = (Element) outDoc.importNode(eleOutOrderHeaderCharges, true);
			eleOutOrd.appendChild(eleOutOrdOverallTotals);
		}

		Element eleOutOrderStatues = AmzXMLUtil.getChildElement(eleOutOrder, AmzLiterals.E_ORDER_STATUSES);
		if (!YFCObject.isVoid(eleOutOrderStatues)) {
			Element eleOutOrdStatues = (Element) outDoc.importNode(eleOutOrderStatues, true);
			eleOutOrd.appendChild(eleOutOrdStatues);
		}

	}

	/*
	 * This method verify is there any eligible BWP line to create order in amazon
	 */
	private void verfiyBWPElgLineToCreateOrdInAmzon(NodeList nOrderLine, List<String> amzCreateOrdElgPrimeLineNo,
			List<String> amzOrderIdList) {
		logger.timer("class: AmzVerfiyCreateOrderMessage | method: verfiyBWPElgLineToCreateOrdInAmzon -- Starts");
		logger.info("class: AmzVerfiyCreateOrderMessage | method: verfiyBWPElgLineToCreateOrdInAmzon -- Starts");
		int iOrderLineLen = nOrderLine.getLength();
		for (int i = 0; i < iOrderLineLen; i++) {
			Element eleOrderLine = (Element) nOrderLine.item(i);
			Element eleOrdLineExtn = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_EXTN);
			if (!YFCObject.isVoid(eleOrdLineExtn)) {
				String strExtnAmzOrdId = eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID);
				String strExtnIsPrimeElg = eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_IS_PRIME_ELIGIBLE);
				String strExtnAmazonFulfillable = eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_IS_AMAZON_FULFILLABLE);
				boolean isBWPElgline = false;
				if (!YFCObject.isVoid(strExtnIsPrimeElg) && !YFCObject.isVoid(strExtnAmazonFulfillable)
						&& AmzCommonConstants.STR_VAL_Y.equalsIgnoreCase(strExtnIsPrimeElg)
						&& AmzCommonConstants.STR_VAL_Y.equalsIgnoreCase(strExtnAmazonFulfillable)) {
					isBWPElgline = true;
				}
				logger.debug("AmzCreateOrder.strExtnAmzOrdId  is: " + strExtnAmzOrdId);
				logger.debug("AmzCreateOrder.strExtnIsPrimeElg is: " + strExtnIsPrimeElg);
				logger.debug("AmzCreateOrder.amzOrderIdList is: " + amzOrderIdList);
				if (isBWPElgline && YFCObject.isVoid(strExtnAmzOrdId)) {
					String strPrimeLineNo = eleOrderLine.getAttribute(AmzLiterals.A_PRIME_LINE_NO);
					logger.debug("AmzCreateOrder.strPrimeLineNo is: " + strPrimeLineNo);
					amzCreateOrdElgPrimeLineNo.add(eleOrderLine.getAttribute(AmzLiterals.A_PRIME_LINE_NO));
				}
				
			}
		}
		logger.info("class: AmzVerfiyCreateOrderMessage | method: verfiyBWPElgLineToCreateOrdInAmzon -- Ends");
		logger.timer("class: AmzVerfiyCreateOrderMessage | method: verfiyBWPElgLineToCreateOrdInAmzon -- Ends");
	}

}
