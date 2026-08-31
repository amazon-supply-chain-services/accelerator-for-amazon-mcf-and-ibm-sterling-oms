package com.amazon.oms.returns.api;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.json.JSONException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.amazon.integrator.common.util.AmzPrepareAmazonSyncExtReturnRequest;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.interop.japi.YIFClientCreationException;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/*
 * This class will get invoked from AmzConnSyncExternalReturnToAmazonAsync service
 * After creating a external return in amazon for merchant initiated BWP lines,
 * This class will stamp the amazon returnid in the OMS at return yfs_order_line level as ExtnAmazonReturnOrderId.
 */

public class AmzUpdateReturnWithAmazonExtReturnInfo implements YIFCustomApi {
	private Properties props;
	final YFCLogCategory logger = YFCLogCategory.instance(AmzPrepareAmazonSyncExtReturnRequest.class);

	/*
	 * This Method will get invoked from AmzCreateExternalReturnInAmazon class To
	 * Stamp the Amazon external return id in OMS at return orderLine level as
	 * ExtnAmazonReturnOrderId.
	 */
	public Document updateReturnWithAmazonExtReturnInfo(YFSEnvironment env, Element eleOrder, Document indoc,
			List<String> amzCreateReturnOrdElgPrimeLineNo, String strAmazonOrderId)
			throws XPathExpressionException, RemoteException, YIFClientCreationException {
		logger.beginTimer(
				"class: AmzUpdateReturnWithAmazonExtReturnInfo | method: updateReturnWithAmazonExtReturnInfo -- Starts");
		logger.info(
				"class: AmzUpdateReturnWithAmazonExtReturnInfo | method: updateReturnWithAmazonExtReturnInfo -- Starts");

		try {
			String sAmzExternalReturnId = null;
			String strOrderNo = eleOrder.getAttribute(AmzLiterals.A_ORDER_NO);
			Document inDocToUpdateReturnOrd = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER);
			Element eleInUpdateReturnOrd = inDocToUpdateReturnOrd.getDocumentElement();
			eleInUpdateReturnOrd.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY,
					eleOrder.getAttribute(AmzLiterals.A_ORDER_HEADER_KEY));
			eleInUpdateReturnOrd.setAttribute(AmzLiterals.A_OVERRIDE, AmzCommonConstants.STR_VAL_Y);
			Element eleInUpdReturnOrdLines = AmzXMLUtil.createChild(eleInUpdateReturnOrd, AmzLiterals.E_ORDER_LINES);
			logger.debug("inDocToUpdateReturnOrd is : " + AmzXMLUtil.getString(inDocToUpdateReturnOrd));
			NodeList nReturnsDetails = AmzXMLUtil.getXpathNodes(indoc.getDocumentElement(),
					"data/updateOrder/order/returns/details");
			int idetails = nReturnsDetails.getLength();
			for (int i = 0; i < idetails; i++) {
				Element eleDetails = (Element) nReturnsDetails.item(i);
				String strAliasId = AmzXMLUtil.getXpathAttribute(eleDetails, "aliases/@aliasId");
				logger.debug("strAliasId is: " + strAliasId);

				if (!YFCObject.isVoid(strOrderNo) && !YFCObject.isVoid(strOrderNo)
						&& strOrderNo.equalsIgnoreCase(strAliasId)) {
					sAmzExternalReturnId = eleDetails.getAttribute(AmzLiterals.A_JS_ID);
				}
			}
			perpareChangeReturnOrderDoc(eleOrder, amzCreateReturnOrdElgPrimeLineNo, eleInUpdReturnOrdLines,
					sAmzExternalReturnId, strAmazonOrderId);
			logger.debug("inDocToUpdateReturnOrd is : " + AmzXMLUtil.getString(inDocToUpdateReturnOrd));
			if (!amzCreateReturnOrdElgPrimeLineNo.isEmpty()) {
				logger.debug("input document to update OMS Return with amazon return info is: "
						+ AmzXMLUtil.getString(inDocToUpdateReturnOrd));
				AmzCommonUtil.callAPI(env, inDocToUpdateReturnOrd, AmzCommonConstants.API_CHANGE_ORDER, null);
			}

			logger.info(
					"class: AmzUpdateReturnWithAmazonExtReturnInfo | method: updateReturnWithAmazonExtReturnInfo -- End");
			logger.endTimer(
					"class: AmzUpdateReturnWithAmazonExtReturnInfo | method: updateReturnWithAmazonExtReturnInfo -- End");
		} catch (Exception e) {

			e.printStackTrace();
			YFSException ex = new YFSException();
			ex.setErrorCode("ERROR_WHILE_UPDATING_RETURN_ORDER_IN_OMS");
			ex.setErrorDescription(
					"Exception in class: AmzUpdateReturnWithAmazonExtReturnInfo | method: updateReturnWithAmazonExtReturnInfo: "
							+ e.getMessage());
			logger.error(
					"Exception in class: AmzUpdateReturnWithAmazonExtReturnInfo | method: updateReturnWithAmazonExtReturnInfo: "
							+ ExceptionUtils.getStackTrace(ex));
			throw ex;
		}
		return indoc;

	}

	/*
	 * This method will prepare in XML document to invoke changeReturn to update the
	 * amazon external return id in OMS at return Orderline level as ExtnAmazonReturnOrderId
	 */
	private void perpareChangeReturnOrderDoc(Element eleOrder, List<String> amzCreateReturnOrdElgPrimeLineNo,
			Element eleInUpdReturnOrdLines, String sAmzExternalReturnId, String strAmazonOrderId)
			throws XPathExpressionException {
		logger.beginTimer(
				"class: AmzUpdateReturnWithAmazonExtReturnInfo | method: perpareChangeReturnOrderDoc -- Starts");
		logger.info("class: AmzUpdateReturnWithAmazonExtReturnInfo | method: perpareChangeReturnOrderDoc -- Starts");
		int iAmzReturnOrdElgPrimeLineNoSize = amzCreateReturnOrdElgPrimeLineNo.size();
		logger.debug("iAmzReturnOrdElgPrimeLineNoSize is: " + iAmzReturnOrdElgPrimeLineNoSize);
		for (int i = 0; i < iAmzReturnOrdElgPrimeLineNoSize; i++) {
			String strReturnElgPrimeLineNo = amzCreateReturnOrdElgPrimeLineNo.get(i);
			logger.debug("strReturnElgPrimeLineNo is: " + strReturnElgPrimeLineNo);
			Element eleReturnElgOrdLine = AmzXMLUtil.getXpathElement(eleOrder,
					"OrderLines/OrderLine[@PrimeLineNo='" + strReturnElgPrimeLineNo + "']");
			String sOrderLineKey = eleReturnElgOrdLine.getAttribute(AmzLiterals.A_ORDER_LINE_KEY);
			logger.debug("sOrderLineKey is: " + sOrderLineKey);
			String sAmazonLineItemAlias = AmzXMLUtil.getXpathAttribute(eleReturnElgOrdLine,
					"DerivedFromOrderLine/Extn/@ExtnAmazonLineItemAlias");
			logger.debug("sAmazonLineItemAlias is: " + sAmazonLineItemAlias);
			String sAmazonOrderid = AmzXMLUtil.getXpathAttribute(eleReturnElgOrdLine,
					"DerivedFromOrderLine/Extn/@ExtnAmazonOrderId");
			logger.debug("sAmazonOrderid is: " + sAmazonOrderid);
			if (!YFCObject.isVoid(sAmazonLineItemAlias) && !YFCObject.isVoid(sAmazonOrderid)
					&& sAmazonOrderid.equalsIgnoreCase(strAmazonOrderId)) {
				Element eleInUpdRetOrdLine = AmzXMLUtil.createChild(eleInUpdReturnOrdLines, AmzLiterals.E_ORDER_LINE);
				eleInUpdRetOrdLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, sOrderLineKey);
				Element eleInUpdRetOrdLineExtn = AmzXMLUtil.createChild(eleInUpdRetOrdLine, AmzLiterals.E_EXTN);
				eleInUpdRetOrdLineExtn.setAttribute(AmzLiterals.A_EXTN_AMAZON_SO_LINE_ITEM_ALIAS, sAmazonLineItemAlias);
				if (!YFCObject.isVoid(sAmzExternalReturnId)) {
					eleInUpdRetOrdLineExtn.setAttribute(AmzLiterals.A_EXTN_AMAZON_RETURN_ORDER_ID,
							sAmzExternalReturnId);
				}

			}
		}
		logger.info("class: AmzUpdateReturnWithAmazonExtReturnInfo | method: perpareChangeReturnOrderDoc -- End");
		logger.endTimer("class: AmzUpdateReturnWithAmazonExtReturnInfo | method: perpareChangeReturnOrderDoc -- End");
	}

	/**
	 * This method create a Alert exception if external return order creation failed
	 * from amazon.
	 * 
	 * @param env
	 * @param strOrderNo
	 * @param strOrderHeaderKey
	 * @param strOrderLineKey
	 * @param output
	 * @throws RemoteException
	 * @throws YIFClientCreationException
	 * @throws JSONException              This method to create an exception after
	 *                                    order gets cancelled.
	 */
	public void createNewException(YFSEnvironment env, String strOrderNo, String strOrderHeaderKey, String output)
			throws RemoteException, YIFClientCreationException, JSONException {
		logger.beginTimer("class: AmzUpdateReturnWithAmazonExtReturnInfo | method: createNewException -- Starts");
		logger.info("class: AmzUpdateReturnWithAmazonExtReturnInfo | method: createNewException -- Starts");
		String strExceptionType = "AmazonCreateExternalReturnException";
		HashMap<String, String> errorDetails = AmzCommonUtil.getErrorCodeAndDetails(output);
		String strMessage = null;
		String strErrorCode = null;
		String strErrorType = null;
		String strCode = null;
		for (int i = 0; i < errorDetails.size() / 4; i++) {
			strMessage = errorDetails.get("ErrorMessage " + (i + 1));
			strErrorType = errorDetails.get("ErrorType " + (i + 1));
			strErrorCode = errorDetails.get("ErrorCode " + (i + 1));
			strCode = errorDetails.get("Code " + (i + 1));

			logger.debug(
					"class: AmzUpdateOMSReturnWithAmazonExtReturnInfo | method: createNewException | strMessage :: "
							+ strMessage);
			logger.debug(
					"class: AmzUpdateOMSReturnWithAmazonExtReturnInfo | method: createNewException | strErrorType :: "
							+ strErrorType);
			logger.debug(
					"class: AmzUpdateOMSReturnWithAmazonExtReturnInfo | method: createNewException | strErrorCode :: "
							+ strErrorCode);
			logger.debug("class: AmzUpdateOMSReturnWithAmazonExtReturnInfo | method: createNewException | strCode :: "
					+ strCode);

			Document docInCreateException = null;
			docInCreateException = SCXmlUtil.createDocument(AmzLiterals.E_INBOX);
			Element eleInbox = docInCreateException.getDocumentElement();
			eleInbox.setAttribute(AmzLiterals.A_ACTIVE_FLAG, AmzLiterals.STR_VAL_Y);
			eleInbox.setAttribute(AmzLiterals.A_EXCEPTION_TYPE, strExceptionType);
			eleInbox.setAttribute(AmzLiterals.A_CONSOLIDATE, AmzLiterals.STR_VAL_Y);
			eleInbox.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY, strOrderHeaderKey);
			eleInbox.setAttribute(AmzLiterals.A_ORDER_NO, strOrderNo);
			eleInbox.setAttribute(AmzLiterals.A_DETAIL_DESC, strMessage);
			String strDesc = "Error " + strErrorCode + ":" + strErrorType + "-" + strCode;
			eleInbox.setAttribute(AmzLiterals.A_DESC, strDesc);
			logger.debug("Input to createException API is :: " + SCXmlUtil.getString(docInCreateException));

			try {
				AmzCommonUtil.callAPI(env, docInCreateException, AmzCommonConstants.API_CREATE_EXCEPTION, null);
			} catch (YFSException e) {
				YFSException yfsException = new YFSException();
				yfsException.setErrorCode("ERROR_WHILE_CREATING_ALERT_FROM_UPDATE_OMS_ORDER");
				yfsException.setErrorDescription(
						"Error While invoking createException api to Create a alert during update order due to amazon order creation failure");
				logger.error("Exception in AmzUpdateOMSReturnWithAmazonExtReturnInfo.createNewException Method: "
						+ ExceptionUtils.getStackTrace(yfsException));
				throw AmzCommonUtil.createException(e);
			}

		}
		logger.info("class: AmzUpdateReturnWithAmazonExtReturnInfo | method: createNewException -- Ends");
		logger.endTimer("class:AmzUpdateReturnWithAmazonExtReturnInfo | method: createNewException -- Ends");
	}

	public void setProperties(Properties inProps) {
		this.props = inProps;
	}
}
