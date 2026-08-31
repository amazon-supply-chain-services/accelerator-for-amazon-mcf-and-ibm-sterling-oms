package com.amazon.integrator.returns.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.json.JSONException;
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
import com.amazon.integrator.common.util.AmzPrepareAmazonExternalRefundRequest;
import com.amazon.oms.returns.api.AmzUpdateOrdInvWithAmazonExtRefundInfo;
import com.ibm.sterling.afc.jsonutil.PLTJSONUtils;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/*
 * This class will get invoked from AmzConnSyncExternalReturnToAmazonAsync service
 * to Add external refund for the External amazon returns On Refund invoice collection
 */

public class AmzAddExternalReturnRefundInAmazon implements YIFCustomApi {
	private Properties props;
	final YFCLogCategory logger = YFCLogCategory.instance(AmzAddExternalReturnRefundInAmazon.class);
	Map<String, String> mapBWPIntegProperties = null;

	/*
	 * This method to Add external refund for the External amazon returns after
	 * Refund invoice collection
	 */
	public Document addExternalReturnRefundInAmazon(YFSEnvironment env, Document indoc) throws Exception {
		logger.beginTimer(
				"class: AmzAddExternalReturnRefundInAmazon | method: addExternalReturnRefundInAmazon -- Starts");
		logger.info("class: AmzAddExternalReturnRefundInAmazon | method: addExternalReturnRefundInAmazon -- Starts");

		try {
			prepareAndLogRequest(indoc);
			List<String> amzExternalRefundElgPrimeLineNo = new ArrayList<>();
			List<String> uniqueAmazonOrderId = new ArrayList<>();

			String strEnterpriseCode = AmzXMLUtil.getXpathAttribute(indoc.getDocumentElement(),
					AmzLiterals.XPATH_ORD_INV_DET_ENTERPRISE_CODE);
			Document inDocGetBWPIntegProps = AmzXMLUtil.createDocument(AmzLiterals.E_PROPERTIES);
			inDocGetBWPIntegProps.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);
			mapBWPIntegProperties = AmzGetGenericProperty.getBWPIntegProperties(inDocGetBWPIntegProps);
			logger.debug("mapBWPIntegProperties is: " + mapBWPIntegProperties);
			String targetId = mapBWPIntegProperties.get(AmzCommonConstants.AMZ_TARGETID);
			logger.debug("targetId is: " + targetId);

			String postURL = mapBWPIntegProperties.get(AmzCommonConstants.AMZ_POST_URL);
			logger.debug("postURL is: " + postURL);

			String apiAccessKey = mapBWPIntegProperties.get(AmzCommonConstants.AMZ_API_ACCESS_KEY);
			logger.debug("apiAccessKey is: " + apiAccessKey);

			String apiVersion = mapBWPIntegProperties.get(AmzCommonConstants.AMZ_API_VERSION);
			logger.debug("apiVersion is: " + apiVersion);

			getExternalRefundElgLines(amzExternalRefundElgPrimeLineNo, indoc, uniqueAmazonOrderId);
			logger.debug("amzExternalRefundElgPrimeLineNo is: " + amzExternalRefundElgPrimeLineNo);
			int iExternalrefundElgPrimeLineNos = amzExternalRefundElgPrimeLineNo.size();
			logger.debug("iExternalrefundElgPrimeLineNos is: " + iExternalrefundElgPrimeLineNos);

			int iUniqueAmazonOrderIdLen = uniqueAmazonOrderId.size();
			for (int j = 0; j < iUniqueAmazonOrderIdLen; j++) {
				String strAmazonOrderId = uniqueAmazonOrderId.get(j);
				AmzPrepareAmazonExternalRefundRequest amzPrepareAmazonExtRefReq = new AmzPrepareAmazonExternalRefundRequest();
				JSONObject variables = amzPrepareAmazonExtRefReq.prepareAmazonExternalRefudReq(env, indoc,
						strAmazonOrderId);
				String query = AmzOrderMutations.AMZ_ADD_EXTERNAL_REFUND;
				JSONObject payload = new JSONObject();
				payload.put(AmzLiterals.A_JS_QUERY, query);

				payload.put(AmzLiterals.A_JS_VARIABLES, variables);
				logger.debug("AmzAddExternalReturnRefundInAmazon. createOrder variable is : " + variables);
				logger.debug("AmzAddExternalReturnRefundInAmazon. Payload is : " + payload.toString());
				StringEntity requestEntity = new StringEntity(payload.toString(), ContentType.APPLICATION_JSON);
				Map<String, String> headerMap = new HashMap<>();
				headerMap.put(AmzLiterals.A_JS_CONTENTS_TYPE, AmzLiterals.A_JS_APPLICATION_JSON);
				headerMap.put(AmzLiterals.A_JS_AUTHORIZATION,
						"Bearer" + " " + AmzRestWebserviceUtil.getAuthorizationToken(strEnterpriseCode));
				headerMap.put(AmzLiterals.A_JS_X_OMNI_TARGETID, targetId);
				headerMap.put(AmzLiterals.A_JS_X_API_ACCESS_KEY, apiAccessKey);
				headerMap.put(AmzLiterals.A_JS_X_API_VERSION, apiVersion);
				String output = AmzRestWebserviceUtil.invokePost(postURL, 10, requestEntity, headerMap);
				logger.debug("AmzAddExternalReturnRefundInAmazon. createReturnOrder response is : " + output);
				if (!YFCObject.isVoid(output)) {
					JSONObject outputJson = new JSONObject(output);

					if (!outputJson.has("errors")) {
						prepareAndLogResponse(AmzLiterals.STR_SUCCESS, indoc, output);
						AmzUpdateOrdInvWithAmazonExtRefundInfo amzUpdateOrdInvWithAmazonExtRefundInfo = new AmzUpdateOrdInvWithAmazonExtRefundInfo();
						amzUpdateOrdInvWithAmazonExtRefundInfo.updateordInvWithAmazonExtRefundId(env, indoc, output);
					} else if (outputJson.has("errors")) {
						AmzCommonUtil.validateResponseMessage(output);
						prepareAndLogResponse(AmzLiterals.STR_ERROR, indoc, output);

					}

				}

			}

		} catch (Exception e) {

			prepareAndLogResponse(AmzLiterals.STR_ERROR, indoc, e.getMessage());

			e.printStackTrace();
			YFSException ex = new YFSException();
			ex.setErrorCode("ADD_EXTERNAL_REFUND_TO_EXT_RETURN_IN_AMAZON_FAILED");
			ex.setErrorDescription("Exception Add External Refund to External Return in Amazon" + e.getMessage());
			logger.error(
					"Exception in class: AmzAddExternalReturnRefundInAmazon | method: addExternalReturnRefundInAmazon: "
							+ ExceptionUtils.getStackTrace(ex));
			throw ex;
		}
		logger.info("class: AmzAddExternalReturnRefundInAmazon | method: addExternalReturnRefundInAmazon -- Ends");
		logger.endTimer("class: AmzAddExternalReturnRefundInAmazon | method: addExternalReturnRefundInAmazon -- Ends");
		return indoc;

	}

	/*
	 * This method is to get the External Refund eligible lines
	 */
	private void getExternalRefundElgLines(List<String> amzExternalRefundElgPrimeLineNo, Document indoc,
			List<String> uniqueAmazonOrderId) throws XPathExpressionException {
		logger.beginTimer("class: AmzAddExternalReturnRefundInAmazon | method: getExternalRefundElgLines -- Starts");
		logger.info("class: AmzAddExternalReturnRefundInAmazon | method: getExternalRefundElgLines -- Starts");
		Element eleOrdInvDetList = indoc.getDocumentElement();
		NodeList nOrdInvDetails = eleOrdInvDetList.getElementsByTagName(AmzLiterals.E_ORDER_INVOICE_DETAIL);
		int iOrdInvDetLen = nOrdInvDetails.getLength();
		for (int i = 0; i < iOrdInvDetLen; i++) {
			Element eleOrderInvoiceDet = (Element) nOrdInvDetails.item(i);
			Element eleOrderLine = AmzXMLUtil.getChildElement(eleOrderInvoiceDet, AmzLiterals.E_ORDER_LINE);
			Element eleOrderLineExtn = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_EXTN);
			if (!YFCObject.isVoid(eleOrderLineExtn)) {
				String sExtnIsAmazonInitReturn = eleOrderLineExtn
						.getAttribute(AmzLiterals.A_EXTN_IS_AMAZON_INIT_RETURN);
				logger.debug("sExtnIsAmazonInitReturn is: " + sExtnIsAmazonInitReturn);
				String sExtnAmazonReturnId = eleOrderLineExtn.getAttribute(AmzLiterals.A_EXTN_AMAZON_RETURN_ORDER_ID);
				logger.debug("sExtnAmazonReturnId is: " + sExtnAmazonReturnId);
				if (!YFCObject.isVoid(sExtnIsAmazonInitReturn) && !YFCObject.isVoid(sExtnAmazonReturnId)
						&& AmzCommonConstants.STR_VAL_N.equalsIgnoreCase(sExtnIsAmazonInitReturn)) {
					amzExternalRefundElgPrimeLineNo.add(eleOrderLine.getAttribute(AmzLiterals.A_PRIME_LINE_NO));
					String sExtnAmazonOrderId = AmzXMLUtil.getXpathAttribute(eleOrderLine,
							"DerivedFromOrderLine/Extn/@ExtnAmazonOrderId");
					logger.debug("sExtnAmazonOrderId is: " + sExtnAmazonOrderId);
					if (!YFCObject.isVoid(sExtnAmazonOrderId) && !uniqueAmazonOrderId.contains(sExtnAmazonOrderId)) {
						uniqueAmazonOrderId.add(sExtnAmazonOrderId);
					}

				}
			}

		}
		logger.debug("uniqueAmazonOrderId is: " + uniqueAmazonOrderId);
		logger.info("class: AmzAddExternalReturnRefundInAmazon | method: getExternalRefundElgLines -- Ends");
		logger.endTimer("class: AmzAddExternalReturnRefundInAmazon | method: getExternalRefundElgLines -- Ends");
	}

	/*
	 * This method is to log the request before from adding a external refund in
	 * amazon
	 */
	private void prepareAndLogRequest(Document indoc) throws XPathExpressionException {
		logger.beginTimer("class: AmzAddExternalReturnRefundInAmazon | method: prepareAndLogRequest -- Starts");
		logger.info("class: AmzAddExternalReturnRefundInAmazon | method: prepareAndLogRequest -- Starts");
		String strAmazonOrderid = null;
		String strAmazonReturnOrderid = null;
		String strEnterpriseCode = AmzXMLUtil.getXpathAttribute(indoc.getDocumentElement(),
				AmzLiterals.XPATH_ORD_INV_DET_ENTERPRISE_CODE);
		logger.debug("strEnterpriseCode is: " + strEnterpriseCode);
		String strOrderNo = AmzXMLUtil.getXpathAttribute(indoc.getDocumentElement(),
				"OrderInvoiceDetail/InvoiceHeader/Order/@OrderNo");
		logger.debug("strOrderNo is: " + strOrderNo);
		NodeList nOrdInvDetls = AmzXMLUtil.getXpathNodes(indoc.getDocumentElement(),
				AmzLiterals.E_ORDER_INVOICE_DETAIL);
		int iOrdInvDetls = nOrdInvDetls.getLength();
		for (int i = 0; i < iOrdInvDetls; i++) {
			Element eleOrdInvDet = (Element) nOrdInvDetls.item(i);
			String strExtnIsAmazonInitReturn = AmzXMLUtil.getXpathAttribute(eleOrdInvDet,
					"OrderLine/Extn/@ExtnIsAmazonInitReturn");
			String strExtnIsPrimeElg = AmzXMLUtil.getXpathAttribute(eleOrdInvDet,
					"OrderLine/DerivedFromOrderLine/Extn/@ExtnIsPrimeEligible");
			if (!YFCObject.isVoid(strExtnIsAmazonInitReturn) && !YFCObject.isVoid(strExtnIsPrimeElg)
					&& AmzCommonConstants.STR_VAL_Y.equalsIgnoreCase(strExtnIsPrimeElg)
					&& AmzCommonConstants.STR_VAL_N.equalsIgnoreCase(strExtnIsAmazonInitReturn)) {
				strAmazonOrderid = AmzXMLUtil.getAttribute(eleOrdInvDet,
						"OrderLine/DerivedFromOrderLine/Extn/@ExtnAmazonOrderId");
				logger.debug("strAmazonOrderid is: " + strAmazonOrderid);
				strAmazonReturnOrderid = AmzXMLUtil.getXpathAttribute(eleOrdInvDet,
						"OrderLine/Extn/@ExtnAmazonReturnOrderId");
				logger.debug("strAmazonReturnOrderid is: " + strAmazonReturnOrderid);
				break;
			}
		}
		String strInvoiceNo = AmzXMLUtil.getXpathAttribute(indoc.getDocumentElement(), AmzLiterals.XPATH_INVOICE_NO);
		logger.debug("strInvoiceNo is: " + strInvoiceNo);

		String strInvoiceType = AmzXMLUtil.getXpathAttribute(indoc.getDocumentElement(),
				AmzLiterals.XPATH_INVOICE_TYPE);

		logger.debug("strInvoiceType is: " + strInvoiceType);
		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_DESC,
				AmzCommonConstants.STR_AMZCONN_ADD_EXT_RETURN_REFUND);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, strAmazonOrderid);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_AMAZON_RETURN_ORDER_ID, strAmazonReturnOrderid);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE, strEnterpriseCode);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO, strOrderNo);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_INVOICE_NO, strInvoiceNo);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_INVOICE_TYPE, strInvoiceType);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_PRIME_ELIGIBLE, AmzCommonConstants.STR_VAL_Y);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_AMAZON_INIT_RETURN, AmzCommonConstants.STR_VAL_N);
		logger.debug("logInput is: " + SCXmlUtil.getString(logInput));
		AmzCommonUtil.logAmzConnRequest(logInput);
		logger.info("class: AmzAddExternalReturnRefundInAmazon | method: prepareAndLogRequest -- Ends");
		logger.endTimer("class: AmzAddExternalReturnRefundInAmazon | method: prepareAndLogRequest -- Ends");

	}

	/*
	 * This method is to log the response after adding external refund in amazon
	 * order
	 */
	private void prepareAndLogResponse(String processStatus, Document indoc, String output)
			throws JSONException, XPathExpressionException {
		logger.beginTimer("class: AmzAddExternalReturnRefundInAmazon | method: prepareAndLogResponse -- Starts");
		logger.info("class: AmzAddExternalReturnRefundInAmazon | method: prepareAndLogResponse -- Starts");

		String strAmazonReturnOrderid = null;
		String strAmazonOrderid = null;
		String strEnterpriseCode = AmzXMLUtil.getXpathAttribute(indoc.getDocumentElement(),
				AmzLiterals.XPATH_ORD_INV_DET_ENTERPRISE_CODE);
		logger.debug("strEnterpriseCode is: " + strEnterpriseCode);
		String strOrderNo = AmzXMLUtil.getXpathAttribute(indoc.getDocumentElement(),
				"OrderInvoiceDetail/InvoiceHeader/Order/@OrderNo");
		logger.debug("strOrderNo is: " + strOrderNo);
		NodeList nOrdInvDetls = AmzXMLUtil.getXpathNodes(indoc.getDocumentElement(),
				AmzLiterals.E_ORDER_INVOICE_DETAIL);
		int iOrdInvDetls = nOrdInvDetls.getLength();
		for (int i = 0; i < iOrdInvDetls; i++) {
			Element eleOrdInvDet = (Element) nOrdInvDetls.item(i);
			String strExtnIsAmazonInitReturn = AmzXMLUtil.getXpathAttribute(eleOrdInvDet,
					"OrderLine/Extn/@ExtnIsAmazonInitReturn");
			String strExtnIsPrimeElg = AmzXMLUtil.getXpathAttribute(eleOrdInvDet,
					"OrderLine/DerivedFromOrderLine/Extn/@ExtnIsPrimeEligible");
			if (!YFCObject.isVoid(strExtnIsAmazonInitReturn) && !YFCObject.isVoid(strExtnIsPrimeElg)
					&& AmzCommonConstants.STR_VAL_Y.equalsIgnoreCase(strExtnIsPrimeElg)
					&& AmzCommonConstants.STR_VAL_N.equalsIgnoreCase(strExtnIsAmazonInitReturn)) {
				strAmazonOrderid = AmzXMLUtil.getXpathAttribute(eleOrdInvDet,
						"OrderLine/DerivedFromOrderLine/Extn/@ExtnAmazonOrderId");
				logger.debug("strAmazonOrderid is: " + strAmazonOrderid);
				strAmazonReturnOrderid = AmzXMLUtil.getXpathAttribute(eleOrdInvDet,
						"OrderLine/Extn/@ExtnAmazonReturnOrderId");
				logger.debug("strAmazonReturnOrderid is: " + strAmazonReturnOrderid);
				break;
			}
		}
		String strRefundId = "";
		if (processStatus.equalsIgnoreCase(AmzLiterals.STR_SUCCESS)) {
			strRefundId = getRefundId(indoc, output);
		}
		String strInvoiceNo = AmzXMLUtil.getXpathAttribute(indoc.getDocumentElement(), AmzLiterals.XPATH_INVOICE_NO);
		logger.debug("strInvoiceNo is: " + strInvoiceNo);

		String strInvoiceType = AmzXMLUtil.getXpathAttribute(indoc.getDocumentElement(),
				AmzLiterals.XPATH_INVOICE_TYPE);
		logger.debug("strInvoiceType is: " + strInvoiceType);

		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE,
				AmzCommonConstants.STR_AMZCONN_ADD_EXT_RETURN_REFUND);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE, strOrderNo);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO, strEnterpriseCode);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, strAmazonOrderid);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_AMAZON_RETURN_ORDER_ID, strAmazonReturnOrderid);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_AMAZON_REFUND_ID, strRefundId);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_PRIME_ELIGIBLE, AmzCommonConstants.STR_VAL_Y);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_AMAZON_INIT_RETURN, AmzCommonConstants.STR_VAL_N);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_INVOICE_NO, strInvoiceNo);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_INVOICE_TYPE, strInvoiceType);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_HTTP_MESSAGE, AmzCommonConstants.STR_OK);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_HTTP_CODE, AmzCommonConstants.STR_HTTP_STATUS_OK);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_PROCESS_STATUS, AmzLiterals.STR_SUCCESS);
		if (!YFCObject.isVoid(output)) {
			if (processStatus.equalsIgnoreCase(AmzLiterals.STR_SUCCESS)) {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_MESSAGE, null);
			} else {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_ERROR_MSG, output);
			}
		}
		logger.debug("logInput is: " + SCXmlUtil.getString(logInput));
		AmzCommonUtil.logAmzConnResponse(logInput);

		logger.info("class: AmzAddExternalReturnRefundInAmazon | method: prepareAndLogResponse -- Ends");
		logger.endTimer("class: AmzAddExternalReturnRefundInAmazon | method: prepareAndLogResponse -- Ends");

	}

	private String getRefundId(Document indoc, String output) throws XPathExpressionException, JSONException {
		String strRefundId = null;
		String strInvoiceNo = AmzXMLUtil.getXpathAttribute(indoc.getDocumentElement(),
				"OrderInvoiceDetail/InvoiceHeader/@InvoiceNo");
		if (!YFCObject.isVoid(output)) {
			Document amzUpdOrdOutDoc = PLTJSONUtils.getXmlFromJSON(output, "Root");
			logger.debug("amzUpdOrdOutDoc is: " + AmzXMLUtil.getString(amzUpdOrdOutDoc));
			if (!YFCObject.isVoid(amzUpdOrdOutDoc)) {
				NodeList nRefundDetails = AmzXMLUtil.getXpathNodes(amzUpdOrdOutDoc.getDocumentElement(),
						"data/updateOrder/order/refunds/details");
				int idetails = nRefundDetails.getLength();
				for (int i = 0; i < idetails; i++) {
					Element eleDetails = (Element) nRefundDetails.item(i);
					String strAliasId = AmzXMLUtil.getXpathAttribute(eleDetails, "aliases/@aliasId");
					logger.debug("strAliasId is: " + strAliasId);

					if (!YFCObject.isVoid(strInvoiceNo) && !YFCObject.isVoid(strAliasId)
							&& strInvoiceNo.equalsIgnoreCase(strAliasId)) {
						strRefundId = eleDetails.getAttribute(AmzLiterals.A_JS_ID);
					}
				}
			}
		}
		return strRefundId;
	}

	@Override
	public void setProperties(Properties inProps) {
		this.props = inProps;
	}

}
