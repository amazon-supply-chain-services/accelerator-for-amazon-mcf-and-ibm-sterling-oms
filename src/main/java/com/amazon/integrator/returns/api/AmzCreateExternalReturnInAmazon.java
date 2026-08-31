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

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzOrderMutations;
import com.amazon.common.util.AmzRestWebserviceUtil;
import com.amazon.common.util.AmzXMLUtil;
import com.amazon.integrator.common.util.AmzPrepareAmazonSyncExtReturnRequest;
import com.amazon.oms.returns.api.AmzUpdateReturnWithAmazonExtReturnInfo;
import com.amazon.oms.returns.api.AmzVerifyCreateRetMsgToSyncExtRetInAmazon;
import com.ibm.sterling.afc.jsonutil.PLTJSONUtils;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;


/*
 * This class will get invoked from AmzConnSyncExternalReturnToAmazonAsync service
 * to create external return in amazon for merchant initiated prime lineitems returns,
 * On success of create return in OMS.
 */
public class AmzCreateExternalReturnInAmazon implements YIFCustomApi {
	private Properties props;
	final YFCLogCategory logger = YFCLogCategory.instance(AmzCreateExternalReturnInAmazon.class);
	Map<String, String> mapBWPIntegProperties = null;

	/*
	 * This method is create external return in amazon for merchant initiated prime lineitem returns
	 */
	public Document createExternalReturnInAmazon(YFSEnvironment env, Document indoc) throws Exception {
		logger.beginTimer("class: AmzCreateExternalReturnInAmazon | method: createExternalReturnInAmazon -- Starts");
		logger.info("class: AmzCreateExternalReturnInAmazon | method: createExternalReturnInAmazon -- Starts");
		Element eleOrder = null;
		try {
			prepareAndLogRequest(indoc);
			List<String> amzCreateReturnOrdElgPrimeLineNo = new ArrayList<>();
			List<String> uniqueAmazonOrderId = new ArrayList<>();

			eleOrder = indoc.getDocumentElement();

			Document inDocGetBWPIntegProps = AmzXMLUtil.createDocument(AmzLiterals.E_PROPERTIES);
			inDocGetBWPIntegProps.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE,
					eleOrder.getAttribute(AmzLiterals.A_ENTERPRISE_CODE));
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
			AmzVerifyCreateRetMsgToSyncExtRetInAmazon amzVerifyCreateRetMsgToSycExtRetInAmazon = new AmzVerifyCreateRetMsgToSyncExtRetInAmazon();
			amzVerifyCreateRetMsgToSycExtRetInAmazon.getBWPExternalReturnEligibleLines(eleOrder,
					amzCreateReturnOrdElgPrimeLineNo);
			logger.debug("amzCreateReturnOrdElgPrimeLineNo is: " + amzCreateReturnOrdElgPrimeLineNo);
			int icreateReturOrdElgLineLen = amzCreateReturnOrdElgPrimeLineNo.size();
			for (int i = 0; i < icreateReturOrdElgLineLen; i++) {
				String strPrimeLineNo = amzCreateReturnOrdElgPrimeLineNo.get(i);
				String sAmazonOrderId = AmzXMLUtil.getXpathAttribute(eleOrder, "OrderLines/OrderLine[@PrimeLineNo='"
						+ strPrimeLineNo + "']/DerivedFromOrderLine/Extn/@ExtnAmazonOrderId");
				logger.debug("sAmazonOrderId is: " + sAmazonOrderId);

				if (!YFCObject.isVoid(sAmazonOrderId) && !uniqueAmazonOrderId.contains(sAmazonOrderId)) {
					uniqueAmazonOrderId.add(sAmazonOrderId);
				}
			}
			logger.debug("uniqueAmazonOrderId is: " + uniqueAmazonOrderId);

			logger.debug("icreateReturOrdElgLineLen is: " + icreateReturOrdElgLineLen);
			int iUniqueAmazonOrderIdLen = uniqueAmazonOrderId.size();
			for (int j = 0; j < iUniqueAmazonOrderIdLen; j++) {
				String strAmazonOrderId = uniqueAmazonOrderId.get(j);
				logger.debug("strAmazonOrderId is: " + strAmazonOrderId);

				AmzPrepareAmazonSyncExtReturnRequest amzPrepareAmzCreateExtRetReq = new AmzPrepareAmazonSyncExtReturnRequest();
				JSONObject variables = amzPrepareAmzCreateExtRetReq.prepareAmazonSyncExtReturnRequest(eleOrder,
						amzCreateReturnOrdElgPrimeLineNo, strAmazonOrderId);
				String query = AmzOrderMutations.AMZ_CREATE_EXTERNAL_RETURN;
				JSONObject payload = new JSONObject();
				payload.put(AmzLiterals.A_JS_QUERY, query);

				payload.put(AmzLiterals.A_JS_VARIABLES, variables);
				logger.debug("AmzCreateExternalReturnInAmazon. createOrder variable is : " + variables);
				logger.debug("AmzCreateExternalReturnInAmazon. Payload is : " + payload.toString());
				StringEntity requestEntity = new StringEntity(payload.toString(), ContentType.APPLICATION_JSON);
				Map<String, String> headerMap = new HashMap<>();
				headerMap.put(AmzLiterals.A_JS_CONTENTS_TYPE, AmzLiterals.A_JS_APPLICATION_JSON);
				headerMap.put(AmzLiterals.A_JS_AUTHORIZATION, "Bearer" + " " + AmzRestWebserviceUtil
						.getAuthorizationToken(eleOrder.getAttribute(AmzLiterals.A_ENTERPRISE_CODE)));
				headerMap.put(AmzLiterals.A_JS_X_OMNI_TARGETID, targetId);
				headerMap.put(AmzLiterals.A_JS_X_API_ACCESS_KEY, apiAccessKey);
				headerMap.put(AmzLiterals.A_JS_X_API_VERSION, apiVersion);
				String output = AmzRestWebserviceUtil.invokePost(postURL, 10, requestEntity, headerMap);
				logger.debug("AmzCreateExternalReturnInAmazon. createReturnOrder response is : " + output);
				if (!YFCObject.isVoid(output)) {
					handleAmazonCreateRetOrdResponse(env, eleOrder, output, amzCreateReturnOrdElgPrimeLineNo,
							strAmazonOrderId);
				}

			}
		} catch (Exception e) {
			if (!YFCObject.isVoid(eleOrder)) {
				prepareAndLogResponse(AmzLiterals.STR_ERROR, eleOrder, null, e.getMessage());
			}
			e.printStackTrace();
			YFSException ex = new YFSException();
			ex.setErrorCode("CREATE_EXTERNAL_RETURN_ORDER_IN_AMAZON_FAILED");
			ex.setErrorDescription(
					"Exception While Create External Return in Amazon" + e.getMessage());
			logger.error("Exception in class: AmzCreateExternalReturnInAmazon | method: createExternalReturnInAmazon: "
					+ ExceptionUtils.getStackTrace(ex));
			throw ex;
		}
		logger.info("class: AmzCreateExternalReturnInAmazon | method: createExternalReturnInAmazon -- Ends");
		logger.endTimer("class: AmzCreateExternalReturnInAmazon | method: createExternalReturnInAmazon -- Ends");
		return indoc;

	}

	/*
	 * This method is handle the create external return response from amazon
	 * send the response to AmzUpdateReturnWithAmazonExtReturnInfo to update the return id in OMS.
	 */
	private void handleAmazonCreateRetOrdResponse(YFSEnvironment env, Element eleOrder, String output,
			List<String> amzCreateReturnOrdElgPrimeLineNo, String strAmazonOrderId) throws Exception {
		logger.beginTimer(
				"class: AmzCreateExternalReturnInAmazon | method: handleAmazonCreateRetOrdResponse -- Starts");
		logger.info("class: AmzCreateExternalReturnInAmazon | method: handleAmazonCreateRetOrdResponse -- Starts");
		try {

			String sOrderNo = eleOrder.getAttribute(AmzLiterals.A_ORDER_NO);
			logger.debug("AmzCreateExternalReturnInAmazon sOrderNo is : " + sOrderNo);
			String sOrderHeaderKey = eleOrder.getAttribute(AmzLiterals.A_ORDER_HEADER_KEY);
			logger.debug("AmzCreateExternalReturnInAmazon sOrderHeaderKey is : " + sOrderHeaderKey);
			prepareAndLogResponse(AmzLiterals.STR_SUCCESS, eleOrder, output, null);
			JSONObject outputJson = new JSONObject(output);
			if (!YFCObject.isVoid(output) && (!outputJson.has(AmzLiterals.A_JS_ERRORS))) {
				Document createReturnOutDoc = PLTJSONUtils.getXmlFromJSON(output, AmzLiterals.E_ROOT);
				logger.debug("AmzCreateExternalReturnInAmazon createReturnOutDoc is: "
						+ AmzXMLUtil.getString(createReturnOutDoc));
				if (!YFCObject.isVoid(createReturnOutDoc)) {

					AmzUpdateReturnWithAmazonExtReturnInfo amzUpdateReturnWithAmzExtRetInfo = new AmzUpdateReturnWithAmazonExtReturnInfo();
					amzUpdateReturnWithAmzExtRetInfo.updateReturnWithAmazonExtReturnInfo(env, eleOrder,
							createReturnOutDoc, amzCreateReturnOrdElgPrimeLineNo, strAmazonOrderId);
				}
			} else if (outputJson.has(AmzLiterals.A_JS_ERRORS)) {
				AmzCommonUtil.validateResponseMessage(output);
			}

		} catch (JSONException e) {
			e.printStackTrace();
			YFSException ex = new YFSException();
			ex.setErrorCode("PROCESSING_CREATE_EXTERNAL_RETURN_ORDER_IN_AMAZON_RESPONSE_FAILED");
			ex.setErrorDescription(
					"Exception while processing the create external return order in amazon success response" + e.getMessage());
			logger.error(
					"Exception in class: AmzCreateExternalReturnInAmazon | method: handleAmazonCreateOrdSuccessResponse: "
							+ ExceptionUtils.getStackTrace(ex));
			throw ex;
		}
		logger.info("class: AmzCreateExternalReturnInAmazon | method: handleAmazonCreateRetOrdResponse -- Ends");
		logger.endTimer("class: AmzCreateExternalReturnInAmazon | method: handleAmazonCreateRetOrdResponse -- Ends");
	}

	/*
	 * This method is to log the request before creating a external return in amazon
	 */
	private void prepareAndLogRequest(Document indoc) {
		logger.beginTimer("class: AmzCreateExternalReturnInAmazon | method: prepareAndLogRequest -- Starts");
		logger.info("class: AmzCreateExternalReturnInAmazon | method: prepareAndLogRequest -- Starts");
		Element eleOrder = indoc.getDocumentElement();
		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_DESC,
				AmzCommonConstants.STR_AMZCONN_CREATE_EXT_RETURN_ORDER);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,
				eleOrder.getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE));
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO,
				eleOrder.getAttribute(AmzLiterals.A_ORDER_NO));
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_PRIME_ELIGIBLE, AmzCommonConstants.STR_VAL_Y);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_AMAZON_INIT_RETURN, AmzCommonConstants.STR_VAL_N);
		logger.debug("logInput is: " + SCXmlUtil.getString(logInput));
		AmzCommonUtil.logAmzConnRequest(logInput);
		logger.info("class: AmzCreateExternalReturnInAmazon | method: prepareAndLogRequest -- Ends");
		logger.endTimer("class: AmzCreateExternalReturnInAmazon | method: prepareAndLogRequest -- Ends");

	}

	/*
	 * This method is to log the response after creating a external return in amazon
	 * order
	 */
	private void prepareAndLogResponse(String processStatus, Element eleOrder, String output, String message)
			throws JSONException, XPathExpressionException {
		logger.beginTimer("class: AmzCreateExternalReturnInAmazon | method: prepareAndLogResponse -- Starts");
		logger.info("class: AmzCreateExternalReturnInAmazon | method: prepareAndLogResponse -- Starts");

		String strAmazonReturnOrderid = null;
		String strAmazonOrderId = null;
		if (!YFCObject.isVoid(output)) {
			Document outDoc = PLTJSONUtils.getXmlFromJSON(output, AmzLiterals.E_ROOT);
			logger.debug(
					"AmzCreateExternalReturnInAmazon. prepareAndLogResponse. outDoc:" + SCXmlUtil.getString(outDoc));

			Element eleReturns = AmzXMLUtil.getXpathElement(outDoc.getDocumentElement(),
					"data/updateOrder/order/returns");
			strAmazonOrderId = AmzXMLUtil.getXpathAttribute(outDoc.getDocumentElement(), "data/updateOrder/order/@id");
			if (!YFCObject.isVoid(eleReturns)) {
				Element eleDetails = AmzXMLUtil.getChildElement(eleReturns, AmzLiterals.A_JS_DETAILS);
				if (!YFCObject.isVoid(eleDetails)) {
					strAmazonReturnOrderid = eleDetails.getAttribute(AmzLiterals.A_JS_ID);
					logger.debug("sAmzExternalReturnId is: " + strAmazonReturnOrderid);

				}
			}
		}
		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE,
				AmzCommonConstants.STR_AMZCONN_CREATE_EXT_RETURN_ORDER);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,
				eleOrder.getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE));
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO,
				eleOrder.getAttribute(AmzLiterals.A_ORDER_NO));
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, strAmazonOrderId);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_AMAZON_RETURN_ORDER_ID, strAmazonReturnOrderid);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_PRIME_ELIGIBLE, AmzCommonConstants.STR_VAL_Y);
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_AMAZON_INIT_RETURN, AmzCommonConstants.STR_VAL_N);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_HTTP_MESSAGE, AmzCommonConstants.STR_OK);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_HTTP_CODE, AmzCommonConstants.STR_HTTP_STATUS_OK);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_PROCESS_STATUS, AmzLiterals.STR_SUCCESS);
		if (!YFCObject.isVoid(message)) {
			if (processStatus.equalsIgnoreCase(AmzLiterals.STR_SUCCESS)) {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_MESSAGE, message);
			} else {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_ERROR_MSG, message);
			}
		}
		logger.debug("logInput is: " + SCXmlUtil.getString(logInput));
		AmzCommonUtil.logAmzConnResponse(logInput);

		logger.info("class: AmzCreateExternalReturnInAmazon | method: prepareAndLogResponse -- Ends");
		logger.endTimer("class: AmzCreateExternalReturnInAmazon | method: prepareAndLogResponse -- Ends");

	}
	@Override
	public void setProperties(Properties inProps) {
		this.props = inProps;
	}

}
