package com.amazon.oms.returns.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;

/*
 * This class will get invoked from the createReturn and confirm Draft Return On Success event, 
 * if amzConn.return.prime.externalSync primitive is enabled,
 * verifies createReturn and confirm draft return ON Success message, is the return is merchant initiated,
 * if there are any lines BWP lines present as part of the return then post the Message to internal Queue
 * to create external return in amazon for BWP return lines 
 */
public class AmzVerifyCreateRetMsgToSyncExtRetInAmazon implements YIFCustomApi {
	private Properties props;
	final YFCLogCategory logger = YFCLogCategory.instance(AmzVerifyCreateRetMsgToSyncExtRetInAmazon.class);

	/*
	 * This method verify if return order message is eligible to create a external
	 * return in amazon, if yes then post the message into internal queue to create
	 * a external return in amazon.
	 */
	public Document verifyMsgToCreateExternalReturnInAmazon(YFSEnvironment env, Document indoc)
			throws XPathExpressionException {
		logger.beginTimer(
				"class: AmzVerifyCreateRetMsgToSyncExtRetInAmazon | method: verifyMsgToCreateExternalReturnInAmazon -- Starts");
		logger.info(
				"class: AmzVerifyReturnInvToAddExtRefundInAmazon | method: verifyMsgToCreateExternalReturnInAmazon -- Starts");

		List<String> amzCreateReturnOrdElgPrimeLineNo = new ArrayList<>();
		Element eleInOrder = indoc.getDocumentElement();
		String strOrderNo = eleInOrder.getAttribute(AmzLiterals.A_ORDER_NO);
		logger.debug("strOrderNo is: " + strOrderNo);
		String strDocType = eleInOrder.getAttribute(AmzLiterals.A_DOCUMENT_TYPE);
		logger.debug("strDocType is: " + strDocType);
		String strEnterpriseCode = eleInOrder.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
		logger.debug("strEnterpriseCode is: " + strEnterpriseCode);
		if (AmzCommonConstants.STR_RETURN_DOCUMENT_TYPE.equalsIgnoreCase(strDocType)) {
			Document inDocGetOrdList = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER);
			Element eleOrdInGetOrdList = inDocGetOrdList.getDocumentElement();
			eleOrdInGetOrdList.setAttribute(AmzLiterals.A_ORDER_NO, strOrderNo);
			eleOrdInGetOrdList.setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);
			eleOrdInGetOrdList.setAttribute(AmzLiterals.A_DOCUMENT_TYPE, strDocType);
			logger.debug("Input Document to getOrderList is: " + AmzXMLUtil.getString(inDocGetOrdList));
			Document outDocGetOrdList = AmzCommonUtil.invokeAPI(env,
					AmzCommonConstants.TEMPLATE_GET_ORDER_LIST_FOR_CREATE_EXT_RETURN,
					AmzCommonConstants.API_GET_ORDER_LIST, inDocGetOrdList);
			logger.debug("output Document of getOrderList is: " + AmzXMLUtil.getString(outDocGetOrdList));
			Element eleOutGetOrdList = outDocGetOrdList.getDocumentElement();
			Element eleOutOrder = AmzXMLUtil.getChildElement(eleOutGetOrdList, AmzLiterals.E_ORDER);
			if (!YFCObject.isVoid(eleOutOrder)) {
				Element eleOrderExtn = AmzXMLUtil.getChildElement(eleOutOrder, AmzLiterals.E_EXTN);
				if (!YFCObject.isVoid(eleOrderExtn)) {
					String strExtnAmazonReturnOrderId = eleOrderExtn
							.getAttribute(AmzLiterals.A_EXTN_AMAZON_RETURN_ORDER_ID);
					logger.debug("strExtnAmazonReturnOrderId is: " + strExtnAmazonReturnOrderId);
					if (YFCObject.isVoid(strExtnAmazonReturnOrderId)) {
						getBWPExternalReturnEligibleLines(eleOutOrder, amzCreateReturnOrdElgPrimeLineNo);
					}

				}
				logger.debug("amzCreateReturnOrdElgPrimeLineNo is: " + amzCreateReturnOrdElgPrimeLineNo);
				int iamzCreateReturnOrdElgPrimeLineNoSize = amzCreateReturnOrdElgPrimeLineNo.size();
				if (iamzCreateReturnOrdElgPrimeLineNoSize > 0 && !YFCObject.isVoid(eleOutOrder)) {

					Document outDoc = prepareDocToCreateExtReturnInAmazon(eleOutOrder);
					logger.debug("Input Document to AmzCreateExternalReturnInAmazonSync is: "
							+ AmzXMLUtil.getString(outDoc));
					outDoc.getDocumentElement().setAttribute(AmzLiterals.A_TASK_TYPE,
							AmzCommonConstants.STR_AMZ_CREATE_EXTERNAL_RETURN);
					AmzCommonUtil.invokeService(env, AmzCommonConstants.SERVICE_AMZ_CONN_POST_EXTERNAL_RETURN_MSG_TO_Q,
							outDoc);

				}
			}
		}
		logger.info(
				"class: AmzVerifyCreateRetMsgToSyncExtRetInAmazon | method: verifyMsgToCreateExternalReturnInAmazon -- End");
		logger.endTimer(
				"class: AmzVerifyCreateRetMsgToSyncExtRetInAmazon | method: verifyMsgToCreateExternalReturnInAmazon -- End");
		return indoc;

	}

	/*
	 * This method prepare a input XML document to post the message into internal
	 * queue, to create external return in Amazon
	 */
	public Document prepareDocToCreateExtReturnInAmazon(Element eleOrder) {
		logger.beginTimer(
				"class: AmzVerifyCreateRetMsgToSyncExtRetInAmazon | method: prepareDocToCreateExtReturnInAmazon -- Starts");
		logger.info(
				"class: AmzVerifyReturnInvToAddExtRefundInAmazon | method: prepareDocToCreateExtReturnInAmazon -- Starts");

		Document outDoc = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER);
		Element eleOutOrd = outDoc.getDocumentElement();
		AmzXMLUtil.copyAttributes(eleOutOrd, eleOrder);

		Element eleOutOrderLines = AmzXMLUtil.getChildElement(eleOrder, AmzLiterals.E_ORDER_LINES);
		if (!YFCObject.isVoid(eleOutOrderLines)) {
			Element eleOutOrdLines = (Element) outDoc.importNode(eleOutOrderLines, true);
			eleOutOrd.appendChild(eleOutOrdLines);
		}

		Element eleOutOrderExtn = AmzXMLUtil.getChildElement(eleOrder, AmzLiterals.E_EXTN);
		if (!YFCObject.isVoid(eleOutOrderExtn)) {
			Element eleOutOrdExtn = (Element) outDoc.importNode(eleOutOrderExtn, true);
			eleOutOrd.appendChild(eleOutOrdExtn);
		}

		Element eleOutOrderPriceInfo = AmzXMLUtil.getChildElement(eleOrder, AmzLiterals.E_PRICE_INFO);
		if (!YFCObject.isVoid(eleOutOrderPriceInfo)) {
			Element eleOutOrdPriceInfo = (Element) outDoc.importNode(eleOutOrderPriceInfo, true);
			eleOutOrd.appendChild(eleOutOrdPriceInfo);
		}

		logger.info(
				"class: AmzVerifyCreateRetMsgToSyncExtRetInAmazon | method: prepareDocToCreateExtReturnInAmazon -- End");
		logger.endTimer(
				"class: AmzVerifyCreateRetMsgToSyncExtRetInAmazon | method: prepareDocToCreateExtReturnInAmazon -- End");
		return outDoc;

	}

	/*
	 * This method verify first if it a merchant initiated return, if so the
	 * retrieve the BWP line PrimeLineNo which are eligible to create a external
	 * return in amazon
	 */
	public void getBWPExternalReturnEligibleLines(Element eleOutOrder, List<String> amzCreateReturnOrdElgPrimeLineNo)
			throws XPathExpressionException {
		logger.beginTimer(
				"class: AmzVerifyCreateRetMsgToSyncExtRetInAmazon | method: getBWPExternalReturnEligibleLines -- Starts");
		logger.info(
				"class: AmzVerifyReturnInvToAddExtRefundInAmazon | method: getBWPExternalReturnEligibleLines -- Starts");

		Element eleOrderLines = AmzXMLUtil.getChildElement(eleOutOrder, AmzLiterals.E_ORDER_LINES);
		NodeList nOrderLine = eleOrderLines.getElementsByTagName(AmzLiterals.E_ORDER_LINE);
		for (int i = 0; i < nOrderLine.getLength(); i++) {
			Element eleOrderLine = (Element) nOrderLine.item(i);
			logger.debug(" eleOrderLine is: " + AmzXMLUtil.getString(eleOrderLine));
			Element eleOrderLineExtn = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_EXTN);
			logger.debug("eleOrderLineExtn  is: " + AmzXMLUtil.getString(eleOrderLineExtn));
			if (!YFCObject.isVoid(eleOrderLineExtn)) {
				String strExtnIsAmazonInitReturn = eleOrderLineExtn
						.getAttribute(AmzLiterals.A_EXTN_IS_AMAZON_INIT_RETURN);
				logger.debug("strExtnIsAmazonInitReturn is: " + strExtnIsAmazonInitReturn);
				String strPrimeLineNo = eleOrderLine.getAttribute(AmzLiterals.A_PRIME_LINE_NO);
				logger.debug("strPrimeLineNo is: " + strPrimeLineNo);

				Element eleDerivedOrderLineExtn = AmzXMLUtil.getXpathElement(eleOrderLine, "DerivedFromOrderLine/Extn");
				if (!YFCObject.isVoid(eleDerivedOrderLineExtn)) {
					String strExtnIsPrimeEligible = eleDerivedOrderLineExtn
							.getAttribute(AmzLiterals.A_EXTN_IS_PRIME_ELIGIBLE);
					logger.debug("strExtnIsPrimeEligible is: " + strExtnIsPrimeEligible);

					if (!YFCObject.isVoid(strExtnIsPrimeEligible) && !YFCObject.isVoid(strExtnIsAmazonInitReturn)
							&& AmzCommonConstants.STR_VAL_Y.equalsIgnoreCase(strExtnIsPrimeEligible)
							&& AmzCommonConstants.STR_VAL_N.equalsIgnoreCase(strExtnIsAmazonInitReturn)) {
						amzCreateReturnOrdElgPrimeLineNo.add(strPrimeLineNo);
					}
				}

			}
		}
		logger.info(
				"class: AmzVerifyCreateRetMsgToSyncExtRetInAmazon | method: getBWPExternalReturnEligibleLines -- End");
		logger.endTimer(
				"class: AmzVerifyCreateRetMsgToSyncExtRetInAmazon | method: getBWPExternalReturnEligibleLines -- End");
	}

	@Override
	public void setProperties(Properties inProps) {
		this.props = inProps;
	}

}
