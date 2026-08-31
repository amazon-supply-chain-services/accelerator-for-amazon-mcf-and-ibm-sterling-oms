package com.amazon.oms.order.api;

import java.util.ArrayList;
import java.util.List;
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
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;

/*
 * This class process the releaseOrder On Success message, 
 * Verify is there any eligible MCF line to create order in amazon and return the getOrderList output
 */
public class AmzVerifyReleaseOrderMessage {
	static final YFCLogCategory logger = YFCLogCategory.instance(AmzVerifyReleaseOrderMessage.class);
	List<String> amzCreateOrdElgPrimeLineNo = new ArrayList<>();
	List<String> amzUpdateAmzOrdElgPrimeLineNo = new ArrayList<>();
	Map<String, String> genricPropsMap = null;

	/*
	 * this method will get invoked from Release order on success to create amazon
	 * order for amazon fulfillable MCF line
	 */
	public Document verifyReleaseOrderMessage(YFSEnvironment env, Document doc) throws XPathExpressionException {
		logger.timer("class: AmzVerifyReleaseOrderMessage | method: verifyReleaseOrderMessage -- Starts");
		logger.info("class: AmzVerifyReleaseOrderMessage | method: verifyReleaseOrderMessage -- Starts");
		logger.debug("class: AmzVerifyReleaseOrderMessage | method: verifyReleaseOrderMessage -- "
				+ AmzXMLUtil.getString(doc));

		Element eleOrd = doc.getDocumentElement();

		Element eleOrderExtn = SCXmlUtil.getChildElement(eleOrd, AmzLiterals.E_EXTN);
		String sExtnOrderCountry = eleOrderExtn.getAttribute(AmzLiterals.A_EXTN_ORDER_COUNTRY);
		String strEnterPriseCode = eleOrd.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
		logger.debug("strEnterPriseCode is: " + strEnterPriseCode);
		Document inDocGetGenrcProperty = AmzXMLUtil.createDocument(AmzLiterals.E_PROPERTIES);
		inDocGetGenrcProperty.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterPriseCode);
		genricPropsMap = AmzGetGenericProperty.getGenericProperties(env, inDocGetGenrcProperty);
		String strAmzShipNode = genricPropsMap.get(AmzCommonConstants.PROP_AMZ_SHIP_NODE + sExtnOrderCountry);
		logger.debug("AmzVerifyReleaseOrderMessage.strAmzShipNode is: " + strAmzShipNode);

		Element eleOrdLines = AmzXMLUtil.getChildElement(eleOrd, AmzLiterals.E_ORDER_LINES);

		NodeList nOrderLine = eleOrdLines.getElementsByTagName(AmzLiterals.E_ORDER_LINE);
		int iOrderLineLen = nOrderLine.getLength();
		for (int i = 0; i < iOrderLineLen; i++) {
			Element eleOrderLine = (Element) nOrderLine.item(i);

			Element eleOrdLineExtn = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_EXTN);
			if (!YFCObject.isVoid(eleOrdLineExtn) && !YFCObject.isVoid(strAmzShipNode)) {
				String strExtnAmzOrderID = eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID);
				String strExtnAmzFulfillable = eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_IS_AMAZON_FULFILLABLE);
				String strExtnIssPrimeElg = eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_IS_PRIME_ELIGIBLE);
				Element eleStatus = AmzXMLUtil.getXpathElement(eleOrderLine,
						"OrderStatuses/OrderStatus[@Status='3200' and @ShipNode='" + strAmzShipNode + "']");
				if (!YFCObject.isVoid(eleStatus)) {
					String strShipNode = eleStatus.getAttribute(AmzLiterals.A_SHIP_NODE);
					String strStatus = eleStatus.getAttribute(AmzLiterals.A_STATUS);
					logger.debug("AmzCreateOrder.strShipNode is: " + strShipNode);
					logger.debug("AmzCreateOrder.strExtnAmzOrderID is: " + strExtnAmzOrderID);
					logger.debug("AmzCreateOrder.strExtnAmzFulfillable is: " + strExtnAmzFulfillable);
					logger.debug("AmzCreateOrder.strExtnIssPrimeElg is: " + strExtnIssPrimeElg);
					logger.debug("AmzCreateOrder.strStatus is: " + strStatus);
					logger.debug("AmzCreateOrder.eleStatus is: " + AmzXMLUtil.getString(eleStatus));
					if (!YFCObject.isVoid(strAmzShipNode) && !YFCObject.isVoid(strShipNode)
							&& strShipNode.equalsIgnoreCase(strAmzShipNode)
							&& AmzCommonConstants.STR_RELEASED_ORDER_STATUS.equalsIgnoreCase(strStatus)
							&& AmzCommonConstants.STR_VAL_Y.equalsIgnoreCase(strExtnAmzFulfillable)
							&& YFCObject.isVoid(strExtnAmzOrderID)) {
						doc.getDocumentElement().setAttribute("CreateOrder", AmzCommonConstants.STR_VAL_Y);
						doc.getDocumentElement().setAttribute("Transaction", "ReleaseOrder");
						String strPrimeLineNo = eleOrderLine.getAttribute(AmzLiterals.A_PRIME_LINE_NO);
						amzCreateOrdElgPrimeLineNo.add(strPrimeLineNo);

					}
					if (!YFCObject.isVoid(strExtnAmzOrderID) && !YFCObject.isVoid(strExtnAmzFulfillable)
							&& AmzCommonConstants.STR_VAL_Y.equalsIgnoreCase(strExtnAmzFulfillable)
							&& !amzUpdateAmzOrdElgPrimeLineNo.contains(strExtnAmzOrderID)) {
						logger.debug("AmzCreateOrder.strExtnAmzOrdId is: " + strExtnAmzOrderID);
						doc.getDocumentElement().setAttribute("CreateOrder", AmzCommonConstants.STR_VAL_Y);
						doc.getDocumentElement().setAttribute("Transaction", "ReleaseOrder");
						amzUpdateAmzOrdElgPrimeLineNo.add(strExtnAmzOrderID);
						
					}

				}

			}
		}
		int iamzCreateOrdElgPrimeLineNo = amzCreateOrdElgPrimeLineNo.size();
		logger.debug("AmzCreateOrder.iamzOrderIdList is: " + amzCreateOrdElgPrimeLineNo);
		int iamzUpdateAmzOrdElgPrimeLineNo = amzUpdateAmzOrdElgPrimeLineNo.size();
		logger.debug("AmzCreateOrder.amzUpdateAmzOrdElgPrimeLineNo is: " + amzUpdateAmzOrdElgPrimeLineNo);

		if (iamzCreateOrdElgPrimeLineNo > 0 || iamzUpdateAmzOrdElgPrimeLineNo > 0) {
			AmzCommonUtil.invokeService(env, "AmzConnPostCreateReleaseOrdMsgToQ", doc);
		} else {
			logger.info("OrderNo :" + eleOrd.getAttribute(AmzLiterals.A_ORDER_NO)
					+ "| There are No eligible lines to create order in amazon ");
		}

		logger.info("class: AmzVerifyReleaseOrderMessage | method: verifyReleaseOrderMessage -- Ends");
		logger.timer("class: AmzVerifyReleaseOrderMessage | method: verifyReleaseOrderMessage -- Ends");
		logger.debug("class: AmzVerfiyCreateOrderMessage | method: verfiyCreateOrderMessage : outDoc: "
				+ AmzXMLUtil.getString(doc));
		return doc;
	}

}
