package com.amazon.oms.order.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/*
 * This will get Invoked from Schedule Order On Success event,
 * This class will verify if any line as part of current Schedule with ExtnIsPrimeEligible, and ExtnIsAmazonFulfillable value as N,
 * got scheduled from Amazon ship node then throw exception in order stop regular merchant lines getting fulfilled by Amazon shipnode 
 */

public class AmzProcessScheduleOnSuccessMsg implements YIFCustomApi {
	private Properties props;
	final YFCLogCategory logger = YFCLogCategory.instance(AmzProcessScheduleOnSuccessMsg.class);
	Map<String, String> genricPropsMap = null;
	String strOrderNo = null;

	/*
	 * This Method invoke the getOrderList with OrderNo from the scheduleOrder On
	 * Success Message, invoke the verifyMerchScheduledLineNode method to verify any
	 * regular merchant line of current schedule got scheduled from amazon Shipnode
	 */
	public Document verifyScheduleOrdMsgAndThrowException(YFSEnvironment env, Document doc)
			throws XPathExpressionException {
		logger.timer("class: AmzProcessScheduleOnSuccessMsg | method: verifyScheduleOrdMsgAndThrowException -- Starts");
		logger.info("class: AmzProcessScheduleOnSuccessMsg | method: verifyScheduleOrdMsgAndThrowException -- Starts");
		logger.debug("ScheduleOrderOnSuccess indoc is: " + AmzXMLUtil.getString(doc));
		List<String> merchLineSchduledToAmazonList = new ArrayList<>();
		Element eleOrd = doc.getDocumentElement();
		strOrderNo = eleOrd.getAttribute(AmzLiterals.A_ORDER_NO);
		Document getOrdListInDoc = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER);
		Element eleOrderInDoc = getOrdListInDoc.getDocumentElement();
		eleOrderInDoc.setAttribute(AmzLiterals.A_ORDER_NO, eleOrd.getAttribute(AmzLiterals.A_ORDER_NO));
		eleOrderInDoc.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY, eleOrd.getAttribute(AmzLiterals.A_ORDER_HEADER_KEY));
		logger.debug("AmzMassagReleaseOrderMsg.massageReleaseOrdMsg.getOrderList input Document is: "
				+ AmzXMLUtil.getString(getOrdListInDoc));
		Document getOrderListOutDoc = AmzCommonUtil.invokeAPI(env,
				AmzCommonConstants.TEMPLATE_GET_ORDER_LIST_FOR_AMZ_CREATE_ORDER, AmzCommonConstants.API_GET_ORDER_LIST,
				getOrdListInDoc);
		Element eleOutOrderList = getOrderListOutDoc.getDocumentElement();
		logger.debug("AmzMassagReleaseOrderMsg.massageReleaseOrdMsg.getOrderList input Document is: "
				+ AmzXMLUtil.getString(eleOutOrderList));
		String strEnterPriseCode = AmzXMLUtil.getXpathAttribute(eleOutOrderList, "Order/@EnterpriseCode");
		logger.debug("strEnterPriseCode is: " + strEnterPriseCode);
		String sExtnOrderCountry = AmzXMLUtil.getXpathAttribute(eleOutOrderList, "Order/Extn/@ExtnOrderCountry");
		Document inDocGetGenrcProperty = AmzXMLUtil.createDocument(AmzLiterals.E_PROPERTIES);
		inDocGetGenrcProperty.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterPriseCode);
		genricPropsMap = AmzGetGenericProperty.getGenericProperties(env, inDocGetGenrcProperty);
		String strAmzShipNode = genricPropsMap.get(AmzCommonConstants.PROP_AMZ_SHIP_NODE + sExtnOrderCountry);
		logger.debug("AmzProcessScheduleOnSuccessMsg.strAmzShipNode is: " + strAmzShipNode);
		Element eleOrdLines = AmzXMLUtil.getChildElement(eleOrd, AmzLiterals.E_ORDER_LINES);
		NodeList nOrderLine = eleOrdLines.getElementsByTagName(AmzLiterals.E_ORDER_LINE);
		int iOrderLineLen = nOrderLine.getLength();
		for (int j = 0; j < iOrderLineLen; j++) {
			Element eleOrdLine = (Element) nOrderLine.item(j);
			String strPrimeLineNo = eleOrdLine.getAttribute(AmzLiterals.A_PRIME_LINE_NO);
			Element eleOutOrderLine = AmzXMLUtil.getXpathElement(eleOutOrderList,
					"Order/OrderLines/OrderLine[@PrimeLineNo='" + strPrimeLineNo + "']");
			verifyMerchScheduledLineNode(eleOutOrderLine, strAmzShipNode, merchLineSchduledToAmazonList);
		}
		logger.debug(
				"AmzProcessScheduleOnSuccessMsg.merchLineSchduledToAmazonList is: " + merchLineSchduledToAmazonList);
		throwException(merchLineSchduledToAmazonList, strAmzShipNode);

		logger.info("class: AmzProcessScheduleOnSuccessMsg | method: verifyScheduleOrdMsgAndThrowException -- Ends");
		logger.timer("class: AmzProcessScheduleOnSuccessMsg | method: verifyScheduleOrdMsgAndThrowException -- Ends");

		return doc;

	}

	/*
	 * This method verify if any regular merchant line as part of current schedule
	 * got scheduled from amazon shipnode, if any merchant line got scheduled from
	 * amazon ship node then invoke a method throwException to throw the exception.
	 */
	private void verifyMerchScheduledLineNode(Element eleOutOrderLine, String strAmzShipNode,
			List<String> merchLineSchduledToAmazonList) throws XPathExpressionException {
		logger.timer("class: AmzProcessScheduleOnSuccessMsg | method: verifyMerchScheduledLineNode -- Starts");
		logger.info("class: AmzProcessScheduleOnSuccessMsg | method: verifyMerchScheduledLineNode -- Starts");
		String strPrimeLineNo = eleOutOrderLine.getAttribute(AmzLiterals.A_PRIME_LINE_NO);
		if (!YFCObject.isVoid(strAmzShipNode)) {
			Element eleSchedule = AmzXMLUtil.getXpathElement(eleOutOrderLine,
					"Schedules/Schedule[@ShipNode='" + strAmzShipNode + "']");

			Element eleOrdLineExtn = AmzXMLUtil.getChildElement(eleOutOrderLine, AmzLiterals.E_EXTN);
			if (!YFCObject.isVoid(eleOrdLineExtn)) {
				String strExtnAmzFulfillable = eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_IS_AMAZON_FULFILLABLE);
				logger.debug("strExtnAmzFulfillable is: " + strExtnAmzFulfillable);
				String strExtnIssPrimeElg = eleOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_IS_PRIME_ELIGIBLE);
				logger.debug("strExtnIssPrimeElg is: " + strExtnIssPrimeElg);

				if (!YFCObject.isVoid(eleSchedule)
						&& (YFCObject.isVoid(strExtnIssPrimeElg)
								|| AmzCommonConstants.STR_VAL_N.equalsIgnoreCase(strExtnIssPrimeElg))
						&& (YFCObject.isVoid(strExtnAmzFulfillable)
								|| AmzCommonConstants.STR_VAL_N.equalsIgnoreCase(strExtnAmzFulfillable))) {
					merchLineSchduledToAmazonList.add(strPrimeLineNo);
				}
			} else if (!YFCObject.isVoid(eleSchedule)) {
				merchLineSchduledToAmazonList.add(strPrimeLineNo);
			}
		}
		logger.info("class: AmzProcessScheduleOnSuccessMsg | method: verifyMerchScheduledLineNode -- Ends");
		logger.timer("class: AmzProcessScheduleOnSuccessMsg | method: verifyMerchScheduledLineNode -- Ends");
	}

	/*
	 * This method throwException with merchant line PrimeLineNo which scheduled
	 * from amazon ship node. To stop regular merchant getting fulfilled by amazon
	 */
	private void throwException(List<String> merchLineSchduledToAmazonList, String strAmzShipNode) {
		logger.timer("class: AmzProcessScheduleOnSuccessMsg | method: throwException -- Starts");
		logger.info("class: AmzProcessScheduleOnSuccessMsg | method: throwException -- Starts");
		int imerchLineSchduledToAmazonList = merchLineSchduledToAmazonList.size();
		logger.debug("AmzCreateOrder.iamzOrderIdList is: " + imerchLineSchduledToAmazonList);
		StringBuilder sMerchPrimeLineBuilder = new StringBuilder(AmzCommonConstants.PIPE);
		if (imerchLineSchduledToAmazonList > 0) {
			for (int k = 0; k < imerchLineSchduledToAmazonList; k++) {
				String strPrimeLine = merchLineSchduledToAmazonList.get(k);
				if (!YFCObject.isVoid(strPrimeLine)) {
					appendEntity(sMerchPrimeLineBuilder, AmzLiterals.A_PRIME_LINE_NO, strPrimeLine);
				}

			}
			logger.debug("sMerchPrimeLineBuilder is: " + sMerchPrimeLineBuilder);
			String sErrorCode = AmzCommonConstants.STR_MERCH_LINE_SCHEDULE_ERROR;
			String sErrorMessage = strOrderNo + " Merchant Regular Lines With " + sMerchPrimeLineBuilder
					+ " Scheduled to Amazon ShipNode: " + strAmzShipNode + " Hence Throwing Exception";
			logger.debug("sErrorMessage is: " + sErrorMessage);
			YFSException yfse = new YFSException();
			yfse.setErrorCode(sErrorCode);
			yfse.setErrorDescription(sErrorMessage);
			throw AmzCommonUtil.createException(yfse);

		} else {
			logger.info("None of the Merchant Lines Scheduled to Amazon ShipNode");
		}
		logger.info("class: AmzProcessScheduleOnSuccessMsg | method: throwException -- Ends");
		logger.timer("class: AmzProcessScheduleOnSuccessMsg | method: throwException -- Ends");
	}

	public static void appendEntity(StringBuilder sBuilder, String name, String value) {
		if (sBuilder == null) {
			sBuilder = new StringBuilder();
		}
		sBuilder.append(name).append(AmzCommonConstants.EQUAL).append(value).append(AmzCommonConstants.PIPE);

	}

	@Override
	public void setProperties(Properties inProps) {
		this.props = inProps;
	}

}
