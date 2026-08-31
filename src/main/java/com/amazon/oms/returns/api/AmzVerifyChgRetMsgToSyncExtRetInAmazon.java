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
import com.yantra.yfs.japi.YFSException;

/*
 * This class will get invoked from the ChangeReturn On Success event,
 * if amzConn.return.prime.externalSync primitive is enabled, 
 * verifies order audits is for Return Receive or Cancel
 * Is there any Merchant Initiated BWP return line received or Cancelled.
 */
public class AmzVerifyChgRetMsgToSyncExtRetInAmazon implements YIFCustomApi {

	private Properties props;
	final YFCLogCategory logger = YFCLogCategory.instance(AmzVerifyChgRetMsgToSyncExtRetInAmazon.class);

	/*
	 * This method verify the change Return ON Success event message, If any change
	 * return is for receive or cancel.
	 * 
	 */
	public Document verifychgRetMsgToSyncExtRetInAmazon(YFSEnvironment env, Document indoc) throws Exception {
		logger.beginTimer(
				"class: AmzVerifyChgRetMsgToSyncExtRetInAmazon | method: verifychgRetMsgToSyncExtRetInAmazon -- Starts");
		logger.info(
				"class: AmzVerifyChgRetMsgToSyncExtRetInAmazon | method: verifychgRetMsgToSyncExtRetInAmazon -- Starts");

		boolean isCancelOrderLineAudit = false;
		boolean isRetReceiveOrderLineAudit = false;
		Document outDocGetOrdList = null;
		Element eleOrder = indoc.getDocumentElement();
		List<String> amzCancelOrderLineKey = new ArrayList<>();
		List<String> amzReceivedOrderLineKey = new ArrayList<>();
		logger.debug("EnterpriseCode" + eleOrder.getAttribute(AmzLiterals.A_ENTERPRISE_CODE));

		NodeList nOrderAuditLevel = AmzXMLUtil.getXpathNodes(eleOrder, "OrderAudit/OrderAuditLevels/OrderAuditLevel");
		int iOrderAuditLevelLen = nOrderAuditLevel.getLength();
		for (int i = 0; i < iOrderAuditLevelLen; i++) {
			Element eleOrderAuditLevel = (Element) nOrderAuditLevel.item(i);
			String strOrderLinekey = eleOrderAuditLevel.getAttribute(AmzLiterals.A_ORDER_LINE_KEY);
			logger.debug("strOrderLinekey   is: " + strOrderLinekey);
			Element eleAttrReceivedQty = AmzXMLUtil.getXpathElement(eleOrderAuditLevel,
					"OrderAuditDetails/OrderAuditDetail/Attributes/Attribute[@Name='"
							+ AmzCommonConstants.STR_RECEIVED_QTY + "']");
			Element eleModificationTypes = AmzXMLUtil.getChildElement(eleOrderAuditLevel, "ModificationTypes");
			Element eleModificationType = AmzXMLUtil.getChildElement(eleModificationTypes, "ModificationType");
			String strName = eleModificationType.getAttribute(AmzLiterals.A_NAME);
			logger.debug("strName   is: " + strName);
			String strScreenName = eleModificationType.getAttribute("ScreenName");
			logger.debug("strScreenName   is: " + strScreenName);
			if (!YFCObject.isVoid(strName) && !YFCObject.isVoid(strScreenName)
					&& ("CANCEL".equalsIgnoreCase(strName) || "Cancel".equalsIgnoreCase(strScreenName))) {
				isCancelOrderLineAudit = true;
				amzCancelOrderLineKey.add(strOrderLinekey);

			}
			if (!YFCObject.isVoid(eleAttrReceivedQty)) {
				isRetReceiveOrderLineAudit = true;
				amzReceivedOrderLineKey.add(strOrderLinekey);
			}

		}
		if (isCancelOrderLineAudit || isRetReceiveOrderLineAudit) {
			Document inDocGetOrdList = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER);
			Element eleInOrder = inDocGetOrdList.getDocumentElement();
			eleInOrder.setAttribute(AmzLiterals.A_ORDER_NO, eleOrder.getAttribute(AmzLiterals.A_ORDER_NO));
			eleInOrder.setAttribute(AmzLiterals.A_DOCUMENT_TYPE, eleOrder.getAttribute(AmzLiterals.A_DOCUMENT_TYPE));
			eleInOrder.setAttribute(AmzLiterals.A_ENTERPRISE_CODE,
					eleOrder.getAttribute(AmzLiterals.A_ENTERPRISE_CODE));
			logger.debug("Input Document for getOrderList is: " + AmzXMLUtil.getString(inDocGetOrdList));
			outDocGetOrdList = AmzCommonUtil.invokeAPI(env,
					AmzCommonConstants.TEMPLATE_GET_ORDER_LIST_FOR_CREATE_EXT_RETURN,
					AmzCommonConstants.API_GET_ORDER_LIST, inDocGetOrdList);
			logger.debug("Output Document for getOrderList is: " + AmzXMLUtil.getString(outDocGetOrdList));

			verifyLinesToSyncExtReturnInAmazon(env, outDocGetOrdList, amzCancelOrderLineKey, amzReceivedOrderLineKey);
		}
		logger.info(
				"class: AmzVerifyChgRetMsgToSyncExtRetInAmazon | method: verifychgRetMsgToSyncExtRetInAmazon -- End");
		logger.endTimer(
				"class: AmzVerifyChgRetMsgToSyncExtRetInAmazon | method: verifychgRetMsgToSyncExtRetInAmazon -- End");
		return indoc;

	}

	/*
	 * This method verify eligible lines, is eligible lines are greater than 0 then
	 * depending on the changeReturn order audits append the TaskType while posting
	 * the message to Internal Queue to update the amazon external return
	 */
	private void verifyLinesToSyncExtReturnInAmazon(YFSEnvironment env, Document outDocGetOrdList,
			List<String> amzCancelOrderLineKey, List<String> amzReceivedOrderLineKey)
			throws XPathExpressionException, YFSException {
		logger.beginTimer(
				"class: AmzVerifyChgRetMsgToSyncExtRetInAmazon | method: verifyLinesToSyncExtReturnInAmazon -- Starts");
		logger.info(
				"class: AmzVerifyChgRetMsgToSyncExtRetInAmazon | method: verifyLinesToSyncExtReturnInAmazon -- Starts");
		List<String> elgReturnIdsToScInAmazon = new ArrayList();
		logger.debug("amzCancelOrderLineKey   is: " + amzCancelOrderLineKey);
		if (!amzCancelOrderLineKey.isEmpty()) {
			elgReturnIdsToScInAmazon = verifyElgReturnIdsToSyncInAmazon(outDocGetOrdList, amzCancelOrderLineKey);

		}
		logger.debug("amzReceivedOrderLineKey   is: " + amzReceivedOrderLineKey);
		if (!amzReceivedOrderLineKey.isEmpty()) {
			elgReturnIdsToScInAmazon = verifyElgReturnIdsToSyncInAmazon(outDocGetOrdList, amzReceivedOrderLineKey);

		}
		logger.debug("elgReturnIdsToScInAmazon   is: " + elgReturnIdsToScInAmazon);
		if (!elgReturnIdsToScInAmazon.isEmpty()) {
			Document outDoc = prepareDocToSyncExtReturnInAmazon(outDocGetOrdList.getDocumentElement(),
					elgReturnIdsToScInAmazon);

			if (!amzCancelOrderLineKey.isEmpty()) {
				outDoc.getDocumentElement().setAttribute(AmzLiterals.A_TASK_TYPE,
						AmzCommonConstants.STR_AMZ_CANCEL_EXTERNAL_RETURN);
			} else if (!amzReceivedOrderLineKey.isEmpty()) {
				outDoc.getDocumentElement().setAttribute(AmzLiterals.A_TASK_TYPE,
						AmzCommonConstants.STR_AMZ_COMPLETE_EXTERNAL_RETURN);
			}

			logger.debug("Input Document to AmzCreateExternalReturnInAmazonSync is: " + AmzXMLUtil.getString(outDoc));
			AmzCommonUtil.invokeService(env, AmzCommonConstants.SERVICE_AMZ_CONN_POST_EXTERNAL_RETURN_MSG_TO_Q, outDoc);
		}
		logger.info(
				"class: AmzVerifyChgRetMsgToSyncExtRetInAmazon | method: verifyLinesToSyncExtReturnInAmazon -- End");
		logger.endTimer(
				"class: AmzVerifyChgRetMsgToSyncExtRetInAmazon | method: verifyLinesToSyncExtReturnInAmazon -- End");
	}

	/*
	 * This method retrieve the eligible returnId for return update in amazon which
	 * are merchants initiated BWP return to update the return in amazon
	 */
	private List<String> verifyElgReturnIdsToSyncInAmazon(Document outDocGetOrdList, List<String> amzRetOrderLineKey)
			throws XPathExpressionException, YFSException {
		logger.beginTimer(
				"class: AmzVerifyChgRetMsgToSyncExtRetInAmazon | method: verifyElgReturnIdsToSyncInAmazon -- Starts");
		logger.info(
				"class: AmzVerifyChgRetMsgToSyncExtRetInAmazon | method: verifyElgReturnIdsToSyncInAmazon -- Starts");
		List<String> elgReturnIdListToComplete = new ArrayList();

		int iReceiedLines = amzRetOrderLineKey.size();
		Element eleOrderlist = outDocGetOrdList.getDocumentElement();
		Element eleOrder = AmzXMLUtil.getChildElement(eleOrderlist, AmzLiterals.E_ORDER);
		for (int i = 0; i < iReceiedLines; i++) {
			String strReceivedLineKey = amzRetOrderLineKey.get(i);
			logger.debug("strReceivedLineKey   is: " + strReceivedLineKey);
			Element eleOrderLine = AmzXMLUtil.getXpathElement(eleOrder,
					"OrderLines/OrderLine[@OrderLineKey='" + strReceivedLineKey + "']");
			logger.debug("eleOrderLine  is: " + AmzXMLUtil.getString(eleOrderLine));
			Element eleOrderLineExtn = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_EXTN);
			logger.debug("eleOrderLineExtn  is: " + AmzXMLUtil.getString(eleOrderLineExtn));
			if (!YFCObject.isVoid(eleOrderLineExtn)) {
				String strExtnIsAmazonInitReturn = eleOrderLineExtn
						.getAttribute(AmzLiterals.A_EXTN_IS_AMAZON_INIT_RETURN);
				logger.debug("strExtnIsAmazonInitReturn   is: " + strExtnIsAmazonInitReturn);
				String strExtnAmazonReturnOrderId = eleOrderLineExtn
						.getAttribute(AmzLiterals.A_EXTN_AMAZON_RETURN_ORDER_ID);
				logger.debug("strExtnAmazonReturnOrderId   is: " + strExtnAmazonReturnOrderId);
				String strIsPrimeElg = AmzXMLUtil.getXpathAttribute(eleOrderLine,
						"DerivedFromOrderLine/Extn/@ExtnIsPrimeEligible");
				logger.debug("strNamstrIsPrimeElg   is: " + strIsPrimeElg);
				String strAmazonOrderId = AmzXMLUtil.getXpathAttribute(eleOrderLine,
						"DerivedFromOrderLine/Extn/@ExtnAmazonOrderId");
				if (!YFCObject.isVoid(strExtnAmazonReturnOrderId) && !YFCObject.isVoid(strExtnIsAmazonInitReturn)
						&& !YFCObject.isVoid(strIsPrimeElg) && !YFCObject.isVoid(strAmazonOrderId)
						&& !elgReturnIdListToComplete.contains(strExtnAmazonReturnOrderId)
						&& AmzCommonConstants.STR_VAL_N.equalsIgnoreCase(strExtnIsAmazonInitReturn)
						&& AmzCommonConstants.STR_VAL_Y.equalsIgnoreCase(strIsPrimeElg)) {
					elgReturnIdListToComplete.add(strExtnAmazonReturnOrderId);
				}
			}

		}
		logger.debug("elgReturnIdListToComplete   is: " + elgReturnIdListToComplete);
		logger.info("class: AmzVerifyChgRetMsgToSyncExtRetInAmazon | method: verifyElgReturnIdsToSyncInAmazon -- End");
		logger.endTimer(
				"class: AmzVerifyChgRetMsgToSyncExtRetInAmazon | method: verifyElgReturnIdsToSyncInAmazon -- End");
		return elgReturnIdListToComplete;
	}

	/*
	 * This method prepare the input XML document to post into the Queue to update the external return in amazon
	 */

	public Document prepareDocToSyncExtReturnInAmazon(Element eleOrderList, List<String> elgReturnIdsToScInAmazon) {
		logger.beginTimer(
				"class: AmzVerifyChgRetMsgToSyncExtRetInAmazon | method: prepareDocToSyncExtReturnInAmazon -- Starts");
		logger.info(
				"class: AmzVerifyChgRetMsgToSyncExtRetInAmazon | method: prepareDocToSyncExtReturnInAmazon -- Starts");

		Document outDoc = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER);
		Element eleOutOrd = outDoc.getDocumentElement();
		Element eleOrder = AmzXMLUtil.getChildElement(eleOrderList, AmzLiterals.E_ORDER);
		AmzXMLUtil.copyAttributes(eleOutOrd, eleOrder);

		Element eleOutOrderLines = AmzXMLUtil.createChild(eleOutOrd, AmzLiterals.E_ORDER_LINES);
		Element eleOrderLines = AmzXMLUtil.getChildElement(eleOrder, AmzLiterals.E_ORDER_LINES);
		NodeList nOrderLine = eleOrderLines.getElementsByTagName(AmzLiterals.E_ORDER_LINE);

		int iOrderLineLen = nOrderLine.getLength();
		for (int i = 0; i < iOrderLineLen; i++) {

			Element eleOrderLine = (Element) nOrderLine.item(i);
			Element eleOrdLineExtn = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_EXTN);
			if (!YFCObject.isVoid(eleOrdLineExtn)) {
				String strExtnAmazonReturnOrderId = eleOrdLineExtn
						.getAttribute(AmzLiterals.A_EXTN_AMAZON_RETURN_ORDER_ID);
				logger.debug("strExtnAmazonReturnOrderId   is: " + strExtnAmazonReturnOrderId);
				if (!YFCObject.isVoid(eleOrderLine) && !YFCObject.isVoid(strExtnAmazonReturnOrderId)
						&& elgReturnIdsToScInAmazon.contains(strExtnAmazonReturnOrderId)) {
					Element eleOutOrdLine = (Element) outDoc.importNode(eleOrderLine, true);
					eleOutOrderLines.appendChild(eleOutOrdLine);
				}
			}
		}

		Element eleOrderExtn = AmzXMLUtil.getChildElement(eleOrder, AmzLiterals.E_EXTN);
		if (!YFCObject.isVoid(eleOrderExtn)) {
			Element eleOutOrdExtn = (Element) outDoc.importNode(eleOrderExtn, true);
			eleOutOrd.appendChild(eleOutOrdExtn);
		}

		Element eleOrderPriceInfo = AmzXMLUtil.getChildElement(eleOrder, AmzLiterals.E_PRICE_INFO);
		if (!YFCObject.isVoid(eleOrderPriceInfo)) {
			Element eleOutOrdPriceInfo = (Element) outDoc.importNode(eleOrderPriceInfo, true);
			eleOutOrd.appendChild(eleOutOrdPriceInfo);
		}

		logger.info("class: AmzVerifyChgRetMsgToSyncExtRetInAmazon | method: prepareDocToSyncExtReturnInAmazon -- End");
		logger.endTimer(
				"class: AmzVerifyChgRetMsgToSyncExtRetInAmazon | method: prepareDocToSyncExtReturnInAmazon -- End");
		return outDoc;

	}

	@Override
	public void setProperties(Properties inProps) {
		this.props = inProps;
	}

}
