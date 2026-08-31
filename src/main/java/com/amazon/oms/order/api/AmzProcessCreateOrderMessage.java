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
import com.amazon.integrator.order.api.AmzCreateOrderInAmazon;
import com.amazon.integrator.order.api.AmzUpdateAmazonOrderExecutionState;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;

/*
 * This class the Process Message from Create order on success message to create amazon order for BWP 
 * and update the DesiredExecutionState of Amazon order as STARTED.
 */
public class AmzProcessCreateOrderMessage {
	 final YFCLogCategory logger = YFCLogCategory.instance(AmzProcessCreateOrderMessage.class);
	List<String> amzCreateOrdElgOrderLineKey = new ArrayList<>();
	List<String> amzCreateOrdElgPrimeLineNo = new ArrayList<>();

	/*
	 * This method process Create Order on Success Message from the Queue
	 */
	public Document processCreateOrdMsg(YFSEnvironment env, Document indoc) throws Exception {

		logger.timer("class: AmzProcessCreateOrderMessage | method: processCreateOrdMsg -- Starts");
		logger.info("class: AmzProcessCreateOrderMessage | method: processCreateOrdMsg -- Starts");
		logger.debug("AmzProcessCreateOrderMessage.processCreateOrdMsg input doc is: " + AmzXMLUtil.getString(indoc));
		prepareAndLogRequest(indoc);

		Element eleOrder = indoc.getDocumentElement();
		Element eleOrdLines = AmzXMLUtil.getChildElement(eleOrder, AmzLiterals.E_ORDER_LINES);
		NodeList nOrderLine = eleOrdLines.getElementsByTagName(AmzLiterals.E_ORDER_LINE);
		verfiyBWPElgLineToCreateOrdInAmzon(nOrderLine);
		logger.debug("AmzProcessCreateOrderMessage.amzCreateOrdElgPrimeLineNo is: " + amzCreateOrdElgPrimeLineNo);
		int iamzCreateOrdElgPrimeLineNo = amzCreateOrdElgPrimeLineNo.size();
		if (iamzCreateOrdElgPrimeLineNo > 0) {
			AmzCreateOrderInAmazon amzCreateOrderInAmazon = new AmzCreateOrderInAmazon();
			amzCreateOrderInAmazon.createOrderInAmazon(env, eleOrder, amzCreateOrdElgPrimeLineNo,
					amzCreateOrdElgOrderLineKey, null);
		}
		logger.info("class: AmzProcessCreateOrderMessage | method: processCreateOrdMsg -- Ends");
		logger.timer("class: AmzProcessCreateOrderMessage | method: processCreateOrdMsg -- Ends");
		return indoc;

	}

	/*
	 * Verify the any BWP Eligible line are present to create order in amazon
	 */
	private void verfiyBWPElgLineToCreateOrdInAmzon(NodeList nOrderLine) {
		logger.timer("class: AmzProcessCreateOrderMessage | method: verfiyBWPElgLineToCreateOrdInAmzon -- Starts");
		logger.info("class: AmzProcessCreateOrderMessage | method: verfiyBWPElgLineToCreateOrdInAmzon -- Starts");
		int iOrderLineLen = nOrderLine.getLength();
		for (int i = 0; i < iOrderLineLen; i++) {
			Element eleOrderLine = (Element) nOrderLine.item(i);
			String strPrimeLineNo = eleOrderLine.getAttribute(AmzLiterals.A_PRIME_LINE_NO);
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
				logger.debug("AmzProcessCreateOrderMessage.strExtnAmzOrdId  is: " + strExtnAmzOrdId);
				logger.debug("AmzProcessCreateOrderMessage.strExtnIsPrimeElg is: " + strExtnIsPrimeElg);
				if (isBWPElgline && YFCObject.isVoid(strExtnAmzOrdId)) {
					logger.debug("AmzProcessCreateOrderMessage.strPrimeLineNo is: " + strPrimeLineNo);
					amzCreateOrdElgPrimeLineNo.add(eleOrderLine.getAttribute(AmzLiterals.A_PRIME_LINE_NO));
					amzCreateOrdElgOrderLineKey.add(eleOrderLine.getAttribute(AmzLiterals.A_ORDER_LINE_KEY));
				} else {
					logger.debug("AmzProcessCreateOrderMessage.strPrimeLineNo is: " + strPrimeLineNo
							+ " ExtnIsPrimeEligible is " + strExtnIsPrimeElg
							+ " Hence Orderline is not BWP eligible to create order in amazon");
				}
			}
		}
		logger.info("class: AmzProcessCreateOrderMessage | method: verfiyBWPElgLineToCreateOrdInAmzon -- Ends");
		logger.timer("class: AmzProcessCreateOrderMessage | method: verfiyBWPElgLineToCreateOrdInAmzon -- Ends");
	}

	/*
	 * This method is to log the request before from create order on Success
	 */
	private void prepareAndLogRequest(Document indoc) {
		logger.beginTimer("class: AmzProcessCreateOrderMessage | method: prepareAndLogRequest -- Starts");
		logger.info("class: AmzProcessCreateOrderMessage | method: prepareAndLogRequest -- Starts");
		logger.debug("class: AmzProcessCreateOrderMessage | method: prepareAndLogRequest: inDoc is: "
				+ AmzXMLUtil.getString(indoc));
		Element eleOrder = indoc.getDocumentElement();
		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_DESC,
				AmzCommonConstants.STR_AMZCONN_CREATE_ORDER);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,
				eleOrder.getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE));
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO,
				eleOrder.getAttribute(AmzLiterals.A_ORDER_NO));
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_PRIME_ELIGIBLE, AmzCommonConstants.STR_VAL_Y);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_FULFILLABLE_BY_AMAZON, AmzCommonConstants.STR_VAL_Y);
		AmzCommonUtil.logAmzConnRequest(logInput);
		logger.endTimer("class: AmzProcessCreateOrderMessage | method: prepareAndLogRequest -- Ends");
		logger.info("class: AmzProcessCreateOrderMessage | method: prepareAndLogRequest -- Ends");

	}

}
