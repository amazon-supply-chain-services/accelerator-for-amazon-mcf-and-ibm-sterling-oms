package com.amazon.integrator.returns.api;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzOrderMutations;
import com.amazon.common.util.AmzRestWebserviceUtil;
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
 * to update external return in amazon for merchant initiated prime line items returns
 *  On cancellation or return receive Success in OMS
 */
public class AmzUpdateExternalReturnsInAmazon implements YIFCustomApi {
	private Properties props;
	final YFCLogCategory logger = YFCLogCategory.instance(AmzUpdateExternalReturnsInAmazon.class);
	Map<String, String> mapBWPIntegProperties = null;
	String strTaskType = null;
	Map<String, String> mapGenericProps = null;

	/*
	 * This method verify and send the eligible line to update in amazon external
	 * return
	 */
	public Document verifyAndUpdateExternalReturnsInAmazon(YFSEnvironment env, Document indoc)
			throws XPathExpressionException {
		logger.beginTimer(
				"class: AmzUpdateExternalReturnsInAmazon | method: verifyAndUpdateExternalReturnsInAmazon -- Starts");
		logger.info(
				"class: AmzUpdateExternalReturnsInAmazon | method: verifyAndUpdateExternalReturnsInAmazon -- Starts");
		Element eleOrder = null;
		try {
			prepareAndLogRequest(indoc);
			List<String> uniqueUpdateElgAmazonOrderId = new ArrayList<>();
			List<String> uniqueUpdateElgAmazonReturnId = new ArrayList<>();
			eleOrder = indoc.getDocumentElement();
			strTaskType = indoc.getDocumentElement().getAttribute(AmzLiterals.A_TASK_TYPE);
			getElgExtReturnIdAndAmazonOrderIdToUpdate(indoc, uniqueUpdateElgAmazonOrderId,
					uniqueUpdateElgAmazonReturnId);
			Document inDocGetBWPIntegProps = AmzXMLUtil.createDocument(AmzLiterals.E_PROPERTIES);
			inDocGetBWPIntegProps.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE,
					eleOrder.getAttribute(AmzLiterals.A_ENTERPRISE_CODE));
			mapBWPIntegProperties = AmzGetGenericProperty.getBWPIntegProperties(inDocGetBWPIntegProps);
			logger.debug("mapBWPIntegProperties is: " + mapBWPIntegProperties);
			logger.debug("uniqueUpdateElgAmazonOrderId is: " + uniqueUpdateElgAmazonOrderId);
			int iUniqueAmazonOrderIdLen = uniqueUpdateElgAmazonOrderId.size();
			for (int j = 0; j < iUniqueAmazonOrderIdLen; j++) {
				String strAmazonOrderId = uniqueUpdateElgAmazonOrderId.get(j);
				logger.debug("AmazonOrderId is: " + strAmazonOrderId);
				List<String> elgReturnUpdatePrimeLineNo = new ArrayList<>();
				getElgReturnUpdatePrimeLineNo(env, indoc, strAmazonOrderId, uniqueUpdateElgAmazonReturnId,
						elgReturnUpdatePrimeLineNo);
				logger.debug("elgReturnUpdatePrimeLineNo is: " + elgReturnUpdatePrimeLineNo);
				if (!elgReturnUpdatePrimeLineNo.isEmpty()) {

					updateExternalReturnsInAmazon(indoc, elgReturnUpdatePrimeLineNo, strAmazonOrderId);
				}
			}

		} catch (Exception e) {
			if (!YFCObject.isVoid(eleOrder)) {
				prepareAndLogResponse(AmzLiterals.STR_ERROR, indoc, e.getMessage(), null);
			}
			e.printStackTrace();
			YFSException ex = new YFSException();
			ex.setErrorCode("UPDATE_EXTERNAL_RETURN_ORDER_IN_AMAZON_FAILED");
			ex.setErrorDescription("Exception While updating the External return in Amazon" + e.getMessage());
			logger.error(
					"Exception in class: AmzUpdateExternalReturnsInAmazon | method: verifyAndUpdateExternalReturnsInAmazon: "
							+ ExceptionUtils.getStackTrace(ex));
			throw ex;
		}
		logger.info("class: AmzUpdateExternalReturnsInAmazon | method: verifyAndUpdateExternalReturnsInAmazon -- Ends");
		logger.endTimer(
				"class: AmzUpdateExternalReturnsInAmazon | method: verifyAndUpdateExternalReturnsInAmazon -- Ends");
		return indoc;

	}

	/*
	 * This method get the Return update eligible PrimeLineNo to update the external
	 * return in amazon
	 */
	private void getElgReturnUpdatePrimeLineNo(YFSEnvironment env, Document indoc, String strAmazonOrderId,
			List<String> uniqueUpdateElgAmazonReturnId, List<String> elgReturnUpdatePrimeLineNo)
			throws YFSException, RemoteException, YIFClientCreationException, XPathExpressionException {
		logger.beginTimer("class: AmzUpdateExternalReturnsInAmazon | method: getElgReturnUpdatePrimeLineNo -- Starts");
		logger.info("class: AmzUpdateExternalReturnsInAmazon | method: getElgReturnUpdatePrimeLineNo -- Starts");
		logger.debug("strAmazonOrderId is: " + strAmazonOrderId);
		String strEnterpriseCode = indoc.getDocumentElement().getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
		Document getOrdInDoc = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER);
		Element ordInDocEle = getOrdInDoc.getDocumentElement();
		ordInDocEle.setAttribute(AmzLiterals.ATTR_AMZ_ORDER_ID, strAmazonOrderId);
		ordInDocEle.setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE,
				AmzCommonConstants.STR_AMZCONN_UPDATE_EXT_RETURN_ORDER);
		ordInDocEle.setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);
		Document outDoc = AmzCommonUtil.callService(env, getOrdInDoc,
				AmzCommonConstants.SERVICE_AMAZON_GET_ORDER_DETAILS, null);
		getAmzOrderIdLinkedUpdElgPrimeLineNo(indoc, uniqueUpdateElgAmazonReturnId, strAmazonOrderId,
				elgReturnUpdatePrimeLineNo, outDoc);
		logger.info("class: AmzUpdateExternalReturnsInAmazon | method: getElgReturnUpdatePrimeLineNo -- Ends");
		logger.endTimer("class: AmzUpdateExternalReturnsInAmazon | method: getElgReturnUpdatePrimeLineNo -- Ends");
	}

	/*
	 * This Method return all the AmazonOrderId linked Eligible PrimeLineNo to
	 * update the External return in amazon.
	 */
	private void getAmzOrderIdLinkedUpdElgPrimeLineNo(Document indoc, List<String> uniqueUpdateElgAmazonReturnId,
			String strExtnAmazonOrderId, List<String> elgReturnUpdatePrimeLineNo, Document outDoc)
			throws XPathExpressionException {
		logger.beginTimer("class: AmzUpdateExternalReturnsInAmazon | method: getReturnUpdateElgPrimeLineNo -- Starts");
		logger.info("class: AmzUpdateExternalReturnsInAmazon | method: getReturnUpdateElgPrimeLineNo -- Starts");
		List<String> amazonOrderIdLinkedreturnId = new ArrayList<>();

		List<String> amazonOrderLinkedPrimeLineNo = new ArrayList<>();

		Element eleOutOrderLines = AmzXMLUtil.getChildElement(indoc.getDocumentElement(), AmzLiterals.E_ORDER_LINES);
		NodeList nOrderLine = eleOutOrderLines.getElementsByTagName(AmzLiterals.E_ORDER_LINE);
		int iOrderLineLen = nOrderLine.getLength();
		for (int j = 0; j < iOrderLineLen; j++) {

			Element eleOrderLine = (Element) nOrderLine.item(j);
			Element eleOrdLineExtn = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_EXTN);
			if (!YFCObject.isVoid(eleOrdLineExtn)) {
				String sExtnAmazonReturnOrderId = eleOrdLineExtn
						.getAttribute(AmzLiterals.A_EXTN_AMAZON_RETURN_ORDER_ID);
				logger.debug("sExtnAmazonReturnOrderId   is: " + sExtnAmazonReturnOrderId);

				String sExtnAmazonOrderId = AmzXMLUtil.getXpathAttribute(eleOrderLine,
						"DerivedFromOrderLine/Extn/@ExtnAmazonOrderId");
				logger.debug("strExtnAmazonOrderId   is: " + strExtnAmazonOrderId);
				if (!YFCObject.isVoid(sExtnAmazonOrderId) && !YFCObject.isVoid(sExtnAmazonReturnOrderId)
						&& strExtnAmazonOrderId.equalsIgnoreCase(sExtnAmazonOrderId)
						&& uniqueUpdateElgAmazonReturnId.contains(sExtnAmazonReturnOrderId)) {
					if (!amazonOrderLinkedPrimeLineNo
							.contains(eleOrderLine.getAttribute(AmzLiterals.A_PRIME_LINE_NO))) {
						amazonOrderLinkedPrimeLineNo.add(eleOrderLine.getAttribute(AmzLiterals.A_PRIME_LINE_NO));
					}
					if (!amazonOrderIdLinkedreturnId.contains(sExtnAmazonReturnOrderId)) {
						amazonOrderIdLinkedreturnId.add(sExtnAmazonReturnOrderId);
					}
				}
			}
		}
		logger.debug("amazonOrderLinkedPrimeLineNo   is: " + amazonOrderLinkedPrimeLineNo);
		logger.debug("amazonOrderIdLinkedreturnId   is: " + amazonOrderIdLinkedreturnId);

		getReturnIdLinkedPrimeLineNo(amazonOrderIdLinkedreturnId, eleOutOrderLines, elgReturnUpdatePrimeLineNo, outDoc);
		logger.info("class: AmzUpdateExternalReturnToSyncInAmazon | method: getReturnUpdateElgPrimeLineNo -- Ends");
		logger.endTimer("class: AmzUpdateExternalReturnToSyncInAmazon | method: getReturnUpdateElgPrimeLineNo -- Ends");
	}

	/*
	 * This Method return all the ReturnId linked Eligible PrimeLineNo to update the
	 * External return in amazon.
	 */
	private void getReturnIdLinkedPrimeLineNo(List<String> amazonOrderIdLinkedreturnId, Element eleOrderLines,
			List<String> elgReturnUpdatePrimeLineNo, Document outDoc) throws XPathExpressionException {
		logger.beginTimer("class: AmzUpdateExternalReturnsInAmazon | method: getReturnIdLinkedPrimeLineNo -- Starts");
		logger.info("class: AmzUpdateExternalReturnsInAmazon | method: getReturnIdLinkedPrimeLineNo -- Starts");
		int iamazonOrderIdLinkedreturnIds = amazonOrderIdLinkedreturnId.size();
		for (int i = 0; i < iamazonOrderIdLinkedreturnIds; i++) {
			String strExtnAmazonReturnOrderId = amazonOrderIdLinkedreturnId.get(i);
			logger.debug("strExtnAmazonReturnOrderId   is: " + strExtnAmazonReturnOrderId);
			List<String> returnIdLinkedPrimeLineNo = new ArrayList<>();
			NodeList nOrderLine = eleOrderLines.getElementsByTagName(AmzLiterals.E_ORDER_LINE);
			int iOrderLineLen = nOrderLine.getLength();
			for (int k = 0; k < iOrderLineLen; k++) {
				Element eleOrderLine = (Element) nOrderLine.item(k);

				Element eleOrdLineExtn = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_EXTN);
				if (!YFCObject.isVoid(eleOrdLineExtn)) {
					String sExtnAmazonReturnOrderId = eleOrdLineExtn
							.getAttribute(AmzLiterals.A_EXTN_AMAZON_RETURN_ORDER_ID);
					logger.debug("sExtnAmazonReturnOrderId   is: " + sExtnAmazonReturnOrderId);

					if (!YFCObject.isVoid(sExtnAmazonReturnOrderId) && !YFCObject.isVoid(strExtnAmazonReturnOrderId)
							&& strExtnAmazonReturnOrderId.equalsIgnoreCase(sExtnAmazonReturnOrderId)) {
						returnIdLinkedPrimeLineNo.add(eleOrderLine.getAttribute(AmzLiterals.A_PRIME_LINE_NO));
					}
				}
			}
			logger.debug("returnIdLinkedPrimeLineNo   is: " + returnIdLinkedPrimeLineNo);

			verifyReturnStatusAndUpdate(returnIdLinkedPrimeLineNo, eleOrderLines, elgReturnUpdatePrimeLineNo, outDoc,
					strExtnAmazonReturnOrderId);

		}
		logger.info("class: AmzUpdateExternalReturnToSyncInAmazon | method: getReturnIdLinkedPrimeLineNo -- Ends");
		logger.endTimer("class: AmzUpdateExternalReturnToSyncInAmazon | method: getReturnIdLinkedPrimeLineNo -- Ends");
	}

	/*
	 * This method verify the external return state before updating the External
	 * return. Update the external return only if the state is CREATED or else do
	 * nothing.
	 */
	private void verifyReturnStatusAndUpdate(List<String> returnIdLinkedPrimeLineNo, Element eleOrderLines,
			List<String> elgReturnUpdatePrimeLineNo, Document outDoc, String strExtnAmazonReturnOrderId)
			throws XPathExpressionException {
		logger.beginTimer("class: AmzUpdateExternalReturnsInAmazon | method: verifyReturnStatusAndUpdate -- Starts");
		logger.info("class: AmzUpdateExternalReturnsInAmazon | method: verifyReturnStatusAndUpdate -- Starts");
		String sState = AmzXMLUtil.getXpathAttribute(outDoc.getDocumentElement(),
				"data/order/returns/details[@id='" + strExtnAmazonReturnOrderId + "']/@state");
		logger.debug("sState   is: " + sState);

		if (!YFCObject.isVoid(sState) && sState.equalsIgnoreCase(AmzCommonConstants.STR_CREATED)) {

			if (!YFCObject.isVoid(strTaskType)
					&& AmzCommonConstants.STR_AMZ_COMPLETE_EXTERNAL_RETURN.equalsIgnoreCase(strTaskType)) {
				verifyReturnCompleteInOMS(returnIdLinkedPrimeLineNo, eleOrderLines, elgReturnUpdatePrimeLineNo);
			}
			if (!YFCObject.isVoid(strTaskType)
					&& AmzCommonConstants.STR_AMZ_CANCEL_EXTERNAL_RETURN.equalsIgnoreCase(strTaskType)) {
				verifyEntireReturnCancelledInOMS(returnIdLinkedPrimeLineNo, eleOrderLines, elgReturnUpdatePrimeLineNo);
			}
		}
		logger.info("class: AmzUpdateExternalReturnToSyncInAmazon | method: verifyReturnStatusAndUpdate -- Ends");
		logger.endTimer("class: AmzUpdateExternalReturnToSyncInAmazon | method: verifyReturnStatusAndUpdate -- Ends");
	}

	/*
	 * This Method verify Return Is eligible to COMPLETE in amazon, is yes then add
	 * the PrimeLineNo to the elgReturnUpdatePrimeLineNo List
	 */
	private void verifyReturnCompleteInOMS(List<String> returnIdLinkedPrimeLineNo, Element eleOrderLines,
			List<String> elgReturnUpdatePrimeLineNo) throws XPathExpressionException {
		logger.beginTimer("class: AmzUpdateExternalReturnsInAmazon | method: verifyReturnCompleteInOMS -- Starts");
		logger.info("class: AmzUpdateExternalReturnsInAmazon | method: verifyReturnCompleteInOMS -- Starts");
		boolean isElgToComplete = false;
		int ireturnIdLinkedPrimeLineNos = returnIdLinkedPrimeLineNo.size();
		for (int j = 0; j < ireturnIdLinkedPrimeLineNos; j++) {
			String sPrimeLineNo = returnIdLinkedPrimeLineNo.get(j);
			logger.debug("sPrimeLineNo   is: " + sPrimeLineNo);

			String sStatusQty = AmzXMLUtil.getXpathAttribute(eleOrderLines,
					"OrderLine[@PrimeLineNo='" + sPrimeLineNo + "']/OrderStatuses/OrderStatus[@Status='"
							+ AmzCommonConstants.STR_RETURN_RECEIVED_STATUS + "']/@StatusQty");
			logger.debug("sStatusQty To Complete is: " + sStatusQty);
			if (!YFCObject.isVoid(sStatusQty)) {

				isElgToComplete = true;
				break;

			}
		}

		if (isElgToComplete) {
			for (int i = 0; i < ireturnIdLinkedPrimeLineNos; i++) {
				String sPrimeLineNo = returnIdLinkedPrimeLineNo.get(i);
				elgReturnUpdatePrimeLineNo.add(sPrimeLineNo);
			}
		}
		logger.debug("elgReturnUpdatePrimeLineNo  is: " + elgReturnUpdatePrimeLineNo);
		logger.info("class: AmzUpdateExternalReturnToSyncInAmazon | method: verifyReturnCompleteInOMS -- Ends");
		logger.endTimer("class: AmzUpdateExternalReturnToSyncInAmazon | method: verifyReturnCompleteInOMS -- Ends");
	}

	/*
	 * This method verify the return is Eligible to Cancel if Yes then add all
	 * eligible PrimeLineNo to elgReturnUpdatePrimeLineNo list
	 */
	private void verifyEntireReturnCancelledInOMS(List<String> returnIdLinkedPrimeLineNo, Element eleOrderLines,
			List<String> elgReturnUpdatePrimeLineNo) throws XPathExpressionException {
		logger.beginTimer(
				"class: AmzUpdateExternalReturnsInAmazon | method: verifyEntireReturnCancelledInOMS -- Starts");
		logger.info("class: AmzUpdateExternalReturnsInAmazon | method: verifyEntireReturnCancelledInOMS -- Starts");
		boolean isElgToCancel = true;
		int ireturnIdLinkedPrimeLineNos = returnIdLinkedPrimeLineNo.size();
		for (int j = 0; j < ireturnIdLinkedPrimeLineNos; j++) {
			String sPrimeLineNo = returnIdLinkedPrimeLineNo.get(j);
			logger.debug("sPrimeLineNo   is: " + sPrimeLineNo);

			String sStatusQty = AmzXMLUtil.getXpathAttribute(eleOrderLines,
					"OrderLine[@PrimeLineNo='" + sPrimeLineNo + "']/OrderStatuses/OrderStatus[@Status='"
							+ AmzCommonConstants.STR_CANCELLED_STATUS + "']/@StatusQty");
			logger.debug("sStatusQty To Cancel is: " + sStatusQty);

			String sTotalQuantity = AmzXMLUtil.getXpathAttribute(eleOrderLines,
					"OrderLine[@PrimeLineNo='" + sPrimeLineNo + "']/OrderStatuses/OrderStatus[@Status='"
							+ AmzCommonConstants.STR_CANCELLED_STATUS + "']/@TotalQuantity");
			logger.debug("sTotalQuantity  is: " + sTotalQuantity);
			if (!YFCObject.isVoid(sStatusQty) && !YFCObject.isVoid(sTotalQuantity)) {
				double dStatusQty = Double.parseDouble(sStatusQty);
				double dTotalQuantity = Double.parseDouble(sTotalQuantity);
				if (dStatusQty != dTotalQuantity) {
					isElgToCancel = false;
				}
			} else if (YFCObject.isVoid(sStatusQty) && YFCObject.isVoid(sTotalQuantity)) {
				isElgToCancel = false;

			}
			if (!isElgToCancel) {
				break;
			}
		}
		if (isElgToCancel) {
			for (int i = 0; i < ireturnIdLinkedPrimeLineNos; i++) {
				String sPrimeLineNo = returnIdLinkedPrimeLineNo.get(i);
				elgReturnUpdatePrimeLineNo.add(sPrimeLineNo);
			}
		}
		logger.debug("elgReturnUpdatePrimeLineNo  is: " + elgReturnUpdatePrimeLineNo);
		logger.info("class: AmzUpdateExternalReturnToSyncInAmazon | method: verifyEntireReturnCancelledInOMS -- Ends");
		logger.endTimer(
				"class: AmzUpdateExternalReturnToSyncInAmazon | method: verifyEntireReturnCancelledInOMS -- Ends");
	}

	/*
	 * This method returns the uniqueAmazonOrderId and unique external return id
	 * present in the return order which are prime eligible and initiated from the
	 * merchants
	 */
	private void getElgExtReturnIdAndAmazonOrderIdToUpdate(Document indoc, List<String> uniqueUpdateElgAmazonOrderId,
			List<String> uniqueUpdateElgAmazonReturnId) throws XPathExpressionException {
		logger.beginTimer(
				"class: AmzUpdateExternalReturnsInAmazon | method: getElgExtReturnIdAndAmazonOrderIdToUpdate -- Starts");
		logger.info(
				"class: AmzUpdateExternalReturnsInAmazon | method: getElgExtReturnIdAndAmazonOrderIdToUpdate -- Starts");
		Element eleOutOrderLines = AmzXMLUtil.getChildElement(indoc.getDocumentElement(), AmzLiterals.E_ORDER_LINES);
		NodeList nOrderLine = eleOutOrderLines.getElementsByTagName(AmzLiterals.E_ORDER_LINE);

		int iOrderLineLen = nOrderLine.getLength();
		for (int i = 0; i < iOrderLineLen; i++) {

			Element eleOrderLine = (Element) nOrderLine.item(i);
			Element eleOrdLineExtn = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_EXTN);
			if (!YFCObject.isVoid(eleOrdLineExtn)) {
				String strExtnAmazonReturnOrderId = eleOrdLineExtn
						.getAttribute(AmzLiterals.A_EXTN_AMAZON_RETURN_ORDER_ID);
				logger.debug("strExtnAmazonReturnOrderId   is: " + strExtnAmazonReturnOrderId);
				String strExtnIsAmazonInitReturn = eleOrdLineExtn
						.getAttribute(AmzLiterals.A_EXTN_IS_AMAZON_INIT_RETURN);
				logger.debug("strExtnIsAmazonInitReturn   is: " + strExtnIsAmazonInitReturn);
				String strExtnAmazonOrderId = AmzXMLUtil.getXpathAttribute(eleOrderLine,
						"DerivedFromOrderLine/Extn/@ExtnAmazonOrderId");
				logger.debug("strExtnAmazonOrderId   is: " + strExtnAmazonOrderId);

				String strExtnIsPrimeEligible = AmzXMLUtil.getXpathAttribute(eleOrderLine,
						"DerivedFromOrderLine/Extn/@ExtnIsPrimeEligible");
				logger.debug("strExtnIsPrimeEligible   is: " + strExtnIsPrimeEligible);

				if (!YFCObject.isVoid(strExtnAmazonReturnOrderId) && !YFCObject.isVoid(strExtnAmazonOrderId)
						&& !YFCObject.isVoid(strExtnIsPrimeEligible)
						&& AmzCommonConstants.STR_VAL_N.equalsIgnoreCase(strExtnIsAmazonInitReturn)
						&& AmzCommonConstants.STR_VAL_Y.equalsIgnoreCase(strExtnIsPrimeEligible)) {
					if (!uniqueUpdateElgAmazonOrderId.contains(strExtnAmazonOrderId)) {
						uniqueUpdateElgAmazonOrderId.add(strExtnAmazonOrderId);
					}
					if (!uniqueUpdateElgAmazonReturnId.contains(strExtnAmazonReturnOrderId)) {
						uniqueUpdateElgAmazonReturnId.add(strExtnAmazonReturnOrderId);
					}

				}
			}
		}
		logger.info(
				"class: AmzUpdateExternalReturnToSyncInAmazon | method: getElgExtReturnIdAndAmazonOrderIdToUpdate -- Ends");
		logger.endTimer(
				"class: AmzUpdateExternalReturnToSyncInAmazon | method: getElgExtReturnIdAndAmazonOrderIdToUpdate -- Ends");
	}

	/*
	 * This method will invoke amazon api to update the external return to cancel or
	 * Complete in amazon.
	 */
	public Document updateExternalReturnsInAmazon(Document indoc, List<String> elgReturnUpdatePrimeLineNo,
			String strAmazonOrderId) throws XPathExpressionException {
		logger.beginTimer("class: AmzUpdateExternalReturnsInAmazon | method: updateExternalReturnsInAmazon -- Starts");
		logger.info("class: AmzUpdateExternalReturnsInAmazon | method: updateExternalReturnsInAmazon -- Starts");

		Element eleOrder = null;
		try {

			eleOrder = indoc.getDocumentElement();

			String targetId = mapBWPIntegProperties.get(AmzCommonConstants.AMZ_TARGETID);
			logger.debug("targetId is: " + targetId);

			String postURL = mapBWPIntegProperties.get(AmzCommonConstants.AMZ_POST_URL);
			logger.debug("postURL is: " + postURL);

			String apiAccessKey = mapBWPIntegProperties.get(AmzCommonConstants.AMZ_API_ACCESS_KEY);
			logger.debug("apiAccessKey is: " + apiAccessKey);

			String apiVersion = mapBWPIntegProperties.get(AmzCommonConstants.AMZ_API_VERSION);
			logger.debug("apiVersion is: " + apiVersion);

			logger.debug("strAmazonOrderId is: " + strAmazonOrderId);

			AmzPrepareAmazonSyncExtReturnRequest amzPrepareAmzCreateExtRetReq = new AmzPrepareAmazonSyncExtReturnRequest();
			JSONObject variables = amzPrepareAmzCreateExtRetReq.prepareAmazonSyncExtReturnRequest(eleOrder,
					elgReturnUpdatePrimeLineNo, strAmazonOrderId);
			String query = AmzOrderMutations.AMZ_CREATE_EXTERNAL_RETURN;
			JSONObject payload = new JSONObject();
			payload.put("query", query);

			payload.put("variables", variables);
			logger.debug("AmzUpdateExternalReturnsInAmazon. UpdateOrder variable is : " + variables);
			logger.debug("AmzUpdateExternalReturnsInAmazon. Payload is : " + payload.toString());
			StringEntity requestEntity = new StringEntity(payload.toString(), ContentType.APPLICATION_JSON);
			Map<String, String> headerMap = new HashMap<>();
			headerMap.put(AmzLiterals.A_JS_CONTENTS_TYPE, AmzLiterals.A_JS_APPLICATION_JSON);
			headerMap.put(AmzLiterals.A_JS_AUTHORIZATION, "Bearer" + " " + AmzRestWebserviceUtil
					.getAuthorizationToken(eleOrder.getAttribute(AmzLiterals.A_ENTERPRISE_CODE)));
			headerMap.put(AmzLiterals.A_JS_X_OMNI_TARGETID, targetId);
			headerMap.put(AmzLiterals.A_JS_X_API_ACCESS_KEY, apiAccessKey);
			headerMap.put(AmzLiterals.A_JS_X_API_VERSION, apiVersion);
			String output;

			output = AmzRestWebserviceUtil.invokePost(postURL, 10, requestEntity, headerMap);
			if (!YFCObject.isVoid(output)) {
				AmzCommonUtil.validateResponseMessage(output);
			}
			logger.debug("AmzUpdateExternalReturnsInAmazon. updateExternalReturnsInAmazon response is : " + output);
			prepareAndLogResponse(AmzLiterals.STR_SUCCESS, indoc, null, elgReturnUpdatePrimeLineNo);
		} catch (Exception e) {
			if (!YFCObject.isVoid(eleOrder)) {
				prepareAndLogResponse(AmzLiterals.STR_ERROR, indoc, e.getMessage(), null);
			}
			e.printStackTrace();
			YFSException ex = new YFSException();
			ex.setErrorCode("UPDATE_EXTERNAL_RETURN_ORDER_IN_AMAZON_FAILED");
			ex.setErrorDescription("Exception While updating the External return in Amazon" + e.getMessage());
			logger.error(
					"Exception in class: AmzUpdateExternalReturnsInAmazon | method: updateExternalReturnsInAmazon: "
							+ ExceptionUtils.getStackTrace(ex));
			throw ex;
		}
		logger.info("class: AmzUpdateExternalReturnsInAmazon | method: updateExternalReturnsInAmazon -- Ends");
		logger.endTimer("class: AmzUpdateExternalReturnsInAmazon | method: updateExternalReturnsInAmazon -- Ends");
		return indoc;

	}

	/*
	 * This method is to log the request before updating the external return in
	 * amazon
	 */
	private void prepareAndLogRequest(Document indoc) throws XPathExpressionException {
		logger.beginTimer("class: AmzUpdateExternalReturnToSyncInAmazon | method: prepareAndLogRequest -- Starts");
		logger.info("class: AmzUpdateExternalReturnToSyncInAmazon | method: prepareAndLogRequest -- Starts");
		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_DESC,
				AmzCommonConstants.STR_AMZCONN_UPDATE_EXT_RETURN_ORDER);
		String strAmazonReturnId = AmzXMLUtil.getXpathAttribute(indoc.getDocumentElement(),
				"OrderLines/OrderLine/Extn/@ExtnAmazonReturnOrderId");
		String strAmazonOrderId = AmzXMLUtil.getXpathAttribute(indoc.getDocumentElement(),
				"OrderLines/OrderLine/DerivedFromOrderLine/Extn/@ExtnAmazonOrderId");
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_AMAZON_RETURN_ORDER_ID, strAmazonReturnId);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, strAmazonOrderId);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,
				indoc.getDocumentElement().getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE));
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO,
				indoc.getDocumentElement().getAttribute(AmzLiterals.A_ORDER_NO));
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_PRIME_ELIGIBLE, AmzCommonConstants.STR_VAL_Y);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_AMAZON_INIT_RETURN, AmzCommonConstants.STR_VAL_N);
		logger.debug("logInput is: " + SCXmlUtil.getString(logInput));
		AmzCommonUtil.logAmzConnRequest(logInput);
		logger.info("class: AmzUpdateExternalReturnToSyncInAmazon | method: prepareAndLogRequest -- Ends");
		logger.endTimer("class: AmzUpdateExternalReturnToSyncInAmazon | method: prepareAndLogRequest -- Ends");

	}

	/*
	 * This method is to log the response after updating the external return in
	 * amazon order
	 */
	private void prepareAndLogResponse(String processStatus, Document indoc, String message,
			List<String> elgReturnUpdatePrimeLineNo) throws XPathExpressionException {
		logger.beginTimer("class: AmzUpdateExternalReturnToSyncInAmazon | method: prepareAndLogResponse -- Starts");
		logger.info("class: AmzUpdateExternalReturnToSyncInAmazon | method: prepareAndLogResponse -- Starts");
		String strAmazonReturnId = null;
		String strAmazonOrderId = null;
		if (!YFCObject.isVoid(elgReturnUpdatePrimeLineNo) && !elgReturnUpdatePrimeLineNo.isEmpty()) {
			String strPrimeLineNo = elgReturnUpdatePrimeLineNo.get(0);
			strAmazonReturnId = AmzXMLUtil.getXpathAttribute(indoc.getDocumentElement(),
					"OrderLines/OrderLine[@PrimeLineNo='" + strPrimeLineNo + "']/Extn/@ExtnAmazonReturnOrderId");
			strAmazonOrderId = AmzXMLUtil.getXpathAttribute(indoc.getDocumentElement(),
					"OrderLines/OrderLine[@PrimeLineNo='" + strPrimeLineNo
							+ "']/DerivedFromOrderLine/Extn/@ExtnAmazonOrderId");
		}

		Element eleOrder = indoc.getDocumentElement();
		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE,
				AmzCommonConstants.STR_AMZCONN_UPDATE_EXT_RETURN_ORDER);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,
				eleOrder.getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE));
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO,
				eleOrder.getAttribute(AmzLiterals.A_ORDER_NO));
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_AMAZON_RETURN_ORDER_ID, strAmazonReturnId);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, strAmazonOrderId);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_PRIME_ELIGIBLE, AmzCommonConstants.STR_VAL_Y);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_AMAZON_INIT_RETURN, AmzCommonConstants.STR_VAL_N);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_HTTP_MESSAGE, AmzCommonConstants.STR_OK);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_HTTP_CODE, AmzCommonConstants.STR_HTTP_STATUS_OK);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_PROCESS_STATUS, AmzLiterals.STR_SUCCESS);
		if (!YFCObject.isVoid(message)) {
			if (processStatus.equalsIgnoreCase(AmzLiterals.STR_SUCCESS)) {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_MESSAGE, null);
			} else {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_ERROR_MSG, message);
			}
		}
		logger.debug("logInput is: " + SCXmlUtil.getString(logInput));
		AmzCommonUtil.logAmzConnResponse(logInput);

		logger.info("class: AmzUpdateExternalReturnToSyncInAmazon | method: prepareAndLogResponse -- Ends");
		logger.endTimer("class: AmzUpdateExternalReturnToSyncInAmazon | method: prepareAndLogResponse -- Ends");

	}

	@Override
	public void setProperties(Properties inProps) {
		this.props = inProps;
	}

}
