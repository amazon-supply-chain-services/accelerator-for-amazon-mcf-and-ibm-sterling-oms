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
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;

public class AmzMassagReleaseOrderMsg {
	static final YFCLogCategory logger = YFCLogCategory.instance(AmzMassagReleaseOrderMsg.class);

	public Document massageReleaseOrdMsg(YFSEnvironment env, Document doc) {
		logger.timer("class: AmzMassagReleaseOrderMsg | method: massageReleaseOrdMsg -- Starts");
		logger.info("class: AmzMassagReleaseOrderMsg | method: massageReleaseOrdMsg -- Starts");
		logger.debug("class: AmzMassagReleaseOrderMsg | method: massageReleaseOrdMsg -- " + AmzXMLUtil.getString(doc));
		Document outDoc = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER);

		// Change for Merchant
		Element eleOrderRelease = doc.getDocumentElement();
		Element eleOrd = AmzXMLUtil.getChildElement(eleOrderRelease, AmzLiterals.E_ORDER);

		Document getOrdListInDoc = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER);
		Element eleOrderInDoc = getOrdListInDoc.getDocumentElement();
		eleOrderInDoc.setAttribute(AmzLiterals.A_ORDER_NO, eleOrd.getAttribute(AmzLiterals.A_ORDER_NO));
		logger.debug("AmzMassagReleaseOrderMsg.massageReleaseOrdMsg.getOrderList input Document is: "
				+ AmzXMLUtil.getString(getOrdListInDoc));
		Document getOrderListOutDoc = AmzCommonUtil.invokeAPI(env,
				AmzCommonConstants.TEMPLATE_GET_ORDER_LIST_FOR_AMZ_CREATE_ORDER, AmzCommonConstants.API_GET_ORDER_LIST,
				getOrdListInDoc);
		Element eleOutOrderList = getOrderListOutDoc.getDocumentElement();
		logger.debug("AmzMassagReleaseOrderMsg.massageReleaseOrdMsg.getOrderList input Document is: "
				+ AmzXMLUtil.getString(eleOutOrderList));
		Element eleOutOrder = AmzXMLUtil.getChildElement(eleOutOrderList, AmzLiterals.E_ORDER);
		AmzVerfiyCreateOrderMessage amzVerfiyCreateOrderMessage = new AmzVerfiyCreateOrderMessage();
		amzVerfiyCreateOrderMessage.prepareOrderDocument(outDoc, eleOutOrder);
		logger.info("class: AmzMassagReleaseOrderMsg | method: massageReleaseOrdMsg -- Ends");
		logger.timer("class: AmzMassagReleaseOrderMsg | method: massageReleaseOrdMsg -- Ends");
		logger.debug("class: AmzMassagReleaseOrderMsg | method: massageReleaseOrdMsg : outDoc: "
				+ AmzXMLUtil.getString(outDoc));
		return outDoc;
	}

	/*
	 * This method retain orderlines of current release only in the outDoc
	 *
	 */
	private Document removeOtherReleaseOrderLines(Document outDoc, Document doc) {
		logger.timer("class: AmzMassagReleaseOrderMsg | method: removeOtherReleaseOrderLines -- Starts");
		logger.info("class: AmzMassagReleaseOrderMsg | method: removeOtherReleaseOrderLines -- Starts");
		List<String> amzPrimeLineNoNotLinkedToRelease = new ArrayList<>();
		String strOrderReleaseKey = doc.getDocumentElement().getAttribute(AmzLiterals.A_ORDER_RELEASE_KEY);
		logger.debug("class: AmzMassagReleaseOrderMsg | method: removeOtherReleaseOrderLines :strOrderReleaseKey is:  "
				+ strOrderReleaseKey);
		Element eleOrderLines = SCXmlUtil.getChildElement(outDoc.getDocumentElement(), AmzLiterals.E_ORDER_LINES);
		NodeList nOrderLines = eleOrderLines.getElementsByTagName(AmzLiterals.E_ORDER_LINE);
		int iOrderLineLen = nOrderLines.getLength();
		for (int i = 0; i < iOrderLineLen; i++) {
			Element eleOrderLine = (Element) nOrderLines.item(i);
			String strPrimeLineNo = eleOrderLine.getAttribute(AmzLiterals.A_PRIME_LINE_NO);
			Element eleOrderStatus = SCXmlUtil.getXpathElement(eleOrderLine,
					"OrderStatuses/OrderStatus[@OrderReleaseKey='" + strOrderReleaseKey + "']");
			if (YFCObject.isVoid(eleOrderStatus)) {
				amzPrimeLineNoNotLinkedToRelease.add(strPrimeLineNo);
			}
		}
		logger.debug(
				"class: AmzMassagReleaseOrderMsg | method: removeOtherReleaseOrderLines :amzPrimeLineNoNotLinkedToRelease is:  "
						+ amzPrimeLineNoNotLinkedToRelease);
		for (int i = 0; i < amzPrimeLineNoNotLinkedToRelease.size(); i++) {
			String strPrimeLineNo = amzPrimeLineNoNotLinkedToRelease.get(i);
			Element eleOrderLine = SCXmlUtil.getXpathElement(eleOrderLines,
					"OrderLine[@PrimeLineNo='" + strPrimeLineNo + "']");
			eleOrderLines.removeChild(eleOrderLine);
		}
		logger.info("class: AmzMassagReleaseOrderMsg | method: removeOtherReleaseOrderLines -- Ends");
		logger.timer("class: AmzMassagReleaseOrderMsg | method: removeOtherReleaseOrderLines -- Ends");
		return outDoc;
	}
}
