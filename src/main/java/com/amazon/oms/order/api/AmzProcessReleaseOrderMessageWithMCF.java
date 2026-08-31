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
import com.amazon.integrator.order.api.AmzCreateOrderInAmazon;
import com.amazon.integrator.order.api.AmzUpdateAmazonOrderExecutionState;
import com.amazon.integrator.order.api.MCFCreateFulfillmentOrderInAmazon;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;

/*
 * This class Read the Release Order On Success Message from the Queue
 */
public class AmzProcessReleaseOrderMessageWithMCF {
	final YFCLogCategory logger = YFCLogCategory.instance(AmzProcessReleaseOrderMessageWithMCF.class);
	List<String> amzCreateOrdElgOrderLineKey = new ArrayList<>();
	List<String> amzCreateOrdElgPrimeLineNo = new ArrayList<>();
	String strOrdReleaseKey = null;
	Map<String, String> genricPropsMap = null;
	
	/*
	 * This method process the release order ON Success message from queue
	 */
	public Document processReleaseOrderMessage(YFSEnvironment env, Document indoc) throws Exception {

		logger.timer("class: AmzProcessReleaseOrderMessageWithMCF | method: processReleaseOrderMessage -- Starts");
		logger.info("class: AmzProcessReleaseOrderMessageWithMCF | method: processReleaseOrderMessage -- Starts");
		logger.debug("AmzProcessReleaseOrderMessageWithMCF.processReleaseOrderMessage input doc is: "
				+ AmzXMLUtil.getString(indoc));
		prepareAndLogRequest(indoc);
		Element eleOrder = indoc.getDocumentElement();
		String strEnterPriseCode = eleOrder.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
		logger.debug("strEnterPriseCode is: " + strEnterPriseCode);
		Document inDocGetGenrcProperty = AmzXMLUtil.createDocument(AmzLiterals.E_PROPERTIES);
		inDocGetGenrcProperty.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterPriseCode);
		genricPropsMap = AmzGetGenericProperty.getGenericProperties(env, inDocGetGenrcProperty);
		Element eleOrderExtn = SCXmlUtil.getChildElement(eleOrder, AmzLiterals.E_EXTN);
		if (!YFCObject.isVoid(eleOrderExtn)) {
			String sExtnOrderCountry = eleOrderExtn.getAttribute(AmzLiterals.A_EXTN_ORDER_COUNTRY);
			String strAmzShipNode = genricPropsMap.get(AmzCommonConstants.PROP_AMZ_SHIP_NODE + sExtnOrderCountry);
			logger.debug("AmzProcessReleaseOrderMessageWithMCF.strAmzShipNode is: " + strAmzShipNode);
			Element eleOrdLines = AmzXMLUtil.getChildElement(eleOrder, AmzLiterals.E_ORDER_LINES);

			NodeList nOrderLine = eleOrdLines.getElementsByTagName(AmzLiterals.E_ORDER_LINE);
			verifyMCFCreateOrdEligibleLines(nOrderLine, strAmzShipNode);
		}

		// Update order for BwP items
		AmzUpdateAmazonOrderExecutionState amzUpdateAmazonOrderExecutionState = new AmzUpdateAmazonOrderExecutionState();
        amzUpdateAmazonOrderExecutionState.verifyAndUpdateAmzOrdExecutionState(env, indoc);


		logger.debug("AmzProcessReleaseOrderMessageWithMCF.amzCreateOrdElgPrimeLineNo is: " + amzCreateOrdElgPrimeLineNo);
		int iamzCreateOrdElgPrimeLineNo = amzCreateOrdElgPrimeLineNo.size();
		if (iamzCreateOrdElgPrimeLineNo > 0) {
			env.setTxnObject(AmzCommonConstants.STR_TRANSACTION, AmzCommonConstants.STR_RELEASED_ORDER);

			//New call via SP-API for MCF items
			MCFCreateFulfillmentOrderInAmazon mCFCreateFulfillmentOrderInAmazon = new MCFCreateFulfillmentOrderInAmazon();
			mCFCreateFulfillmentOrderInAmazon.createFulfillmentOrderInAmazon(env, eleOrder, amzCreateOrdElgPrimeLineNo,
					amzCreateOrdElgOrderLineKey, strOrdReleaseKey);

		}
		logger.info("class: AmzProcessReleaseOrderMessageWithMCF | method: processReleaseOrderMessage -- Ends");
		logger.timer("class: AmzProcessReleaseOrderMessageWithMCF | method: processReleaseOrderMessage -- Ends");
		return indoc;

	}

	/*
	 * This method verify if there are any MCF eligible lines are present to create
	 * order in amazon.
	 */
	private void verifyMCFCreateOrdEligibleLines(NodeList nOrderLine, String strAmzShipNode)
			throws XPathExpressionException {
		logger.timer("class: AmzProcessReleaseOrderMessageWithMCF | method: verifyMCFCreateOrdEligibleLines -- Starts");
		logger.info("class: AmzProcessReleaseOrderMessageWithMCF | method: verifyMCFCreateOrdEligibleLines -- Starts");
		int iOrderLineLen = nOrderLine.getLength();
		for (int i = 0; i < iOrderLineLen; i++) {
			Element eleOrderLine = (Element) nOrderLine.item(i);

			Element eleOrdLineExtn = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_EXTN);
			if (!YFCObject.isVoid(eleOrdLineExtn)) {
				String strExtnAmzOrderID = eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID);
				String strExtnAmzFulfillable = eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_IS_AMAZON_FULFILLABLE);
				String strExtnIssPrimeElg = eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_IS_PRIME_ELIGIBLE);
				Element eleStatus = AmzXMLUtil.getXpathElement(eleOrderLine,
						"OrderStatuses/OrderStatus[@Status='3200' and @ShipNode='" + strAmzShipNode + "']");
				if (!YFCObject.isVoid(eleStatus)) {
					String strShipNode = eleStatus.getAttribute(AmzLiterals.A_SHIP_NODE);
					String strStatus = eleStatus.getAttribute(AmzLiterals.A_STATUS);
					logger.debug("AmzProcessReleaseOrderMessageWithMCF.strShipNode is: " + strShipNode);
					logger.debug("AmzProcessReleaseOrderMessageWithMCF.strExtnAmzOrderID is: " + strExtnAmzOrderID);
					logger.debug("AmzProcessReleaseOrderMessageWithMCF.strExtnAmzFulfillable is: " + strExtnAmzFulfillable);
					logger.debug("AmzProcessReleaseOrderMessageWithMCF.strExtnIssPrimeElg is: " + strExtnIssPrimeElg);
					logger.debug("AmzProcessReleaseOrderMessageWithMCF.strStatus is: " + strStatus);
					logger.debug("AmzProcessReleaseOrderMessageWithMCF.eleStatus is: " + AmzXMLUtil.getString(eleStatus));
					if (strShipNode.equalsIgnoreCase(strAmzShipNode)
							&& AmzCommonConstants.STR_RELEASED_ORDER_STATUS.equalsIgnoreCase(strStatus)
							&& AmzCommonConstants.STR_VAL_Y.equalsIgnoreCase(strExtnAmzFulfillable)
							&& AmzCommonConstants.STR_VAL_N.equalsIgnoreCase(strExtnIssPrimeElg)
							&& YFCObject.isVoid(strExtnAmzOrderID)) {
						String strPrimeLineNo = eleOrderLine.getAttribute(AmzLiterals.A_PRIME_LINE_NO);
						logger.debug("AmzCreateOrder.strPrimeLineNo is: " + strPrimeLineNo);
						amzCreateOrdElgPrimeLineNo.add(eleOrderLine.getAttribute(AmzLiterals.A_PRIME_LINE_NO));
						amzCreateOrdElgOrderLineKey.add(eleOrderLine.getAttribute(AmzLiterals.A_ORDER_LINE_KEY));
						strOrdReleaseKey = eleStatus.getAttribute(AmzLiterals.A_ORDER_RELEASE_KEY);
					}
				}

			}
		}
		logger.info("class: AmzProcessReleaseOrderMessageWithMCF | method: verifyMCFCreateOrdEligibleLines -- Ends");
		logger.timer("class: AmzProcessReleaseOrderMessageWithMCF | method: verifyMCFCreateOrdEligibleLines -- Ends");
	}

	/*
	 * This method is to log the request before from create order on Success
	 */
	private void prepareAndLogRequest(Document indoc) {
		logger.beginTimer("class: AmzProcessReleaseOrderMessageWithMCF | method: prepareAndLogRequest -- Starts");
		logger.info("class: AmzProcessReleaseOrderMessageWithMCF | method: prepareAndLogRequest -- Starts");
		logger.debug("class: AmzProcessReleaseOrderMessageWithMCF | method: prepareAndLogRequest: inDoc is: "
				+ AmzXMLUtil.getString(indoc));
		Element eleOrder = indoc.getDocumentElement();
		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_DESC,
				AmzCommonConstants.STR_AMZCONN_CREATE_ORDER);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,
				eleOrder.getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE));
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO,
				eleOrder.getAttribute(AmzLiterals.A_ORDER_NO));
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_PRIME_ELIGIBLE, AmzCommonConstants.STR_VAL_N);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_FULFILLABLE_BY_AMAZON, AmzCommonConstants.STR_VAL_Y);
		AmzCommonUtil.logAmzConnRequest(logInput);
		logger.endTimer("class: AmzProcessReleaseOrderMessageWithMCF | method: prepareAndLogRequest -- Ends");
		logger.info("class: AmzProcessReleaseOrderMessageWithMCF | method: prepareAndLogRequest -- Ends");

	}

}
