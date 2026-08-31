package com.amazon.integrator.order.api;

import java.rmi.RemoteException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.json.JSONException;
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
import com.amazon.integrator.common.util.AmzPrepareAmazonCreateOrdRequest;
import com.amazon.oms.order.api.AmzUpdateOrdWithAmazonOrdInfo;
import com.amazon.oms.order.api.AmzUpdateOrderRelease;
import com.ibm.sterling.afc.jsonutil.PLTJSONUtils;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.interop.japi.YIFClientCreationException;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/*
 * This class invoke the amazon create order api.
 */
public class AmzCreateOrderInAmazon {
	 final YFCLogCategory logger = YFCLogCategory.instance(AmzCreateOrderInAmazon.class);
	 Map<String, String> mapBWPIntegProperties = null;

	/*
	 * This method invoke the amazon create order api
	 */
	public  void createOrderInAmazon(YFSEnvironment env, Element eleOrder,
			List<String> amzCreateOrdElgPrimeLineNo, List<String> amzCreateOrdElgOrderLineKey, String strOrdReleaseKey)
			throws Exception {
		logger.timer("class: AmzCreateOrderInAmazon | method: createOrderInAmazon -- Starts");
		logger.info("class: AmzCreateOrderInAmazon | method: createOrderInAmazon -- Starts");

		try {
			prepareAndLogRequest(eleOrder);
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
			AmzPrepareAmazonCreateOrdRequest amzPrepareAmazonCreateOrdRequest = new AmzPrepareAmazonCreateOrdRequest();
			JSONObject variables = amzPrepareAmazonCreateOrdRequest.prepareAmzCreateOrderVariableJSON(env, eleOrder,
					amzCreateOrdElgPrimeLineNo, strOrdReleaseKey);
			logger.debug("Create Order Input Variable is: "+variables);
			JSONObject payload = new JSONObject();
			String query = AmzOrderMutations.AMZ_CREATE_ORDER_QUERY;

			payload.put("query", query);
			payload.put("variables", variables);
			logger.debug("AmzCreateOrderInAmazon. createOrder variable is : " + variables);
			logger.debug("AmzCreateOrderInAmazon. Payload is : " + payload.toString());
			StringEntity requestEntity = new StringEntity(payload.toString(), ContentType.APPLICATION_JSON);
			Map<String, String> headerMap = new HashMap<>();
			headerMap.put(AmzLiterals.A_JS_CONTENTS_TYPE, AmzLiterals.A_JS_APPLICATION_JSON);
			headerMap.put(AmzLiterals.A_JS_AUTHORIZATION, "Bearer" + " " + AmzRestWebserviceUtil
					.getAuthorizationToken(eleOrder.getAttribute(AmzLiterals.A_ENTERPRISE_CODE)));
			headerMap.put(AmzLiterals.A_JS_X_OMNI_TARGETID, targetId);
			headerMap.put(AmzLiterals.A_JS_X_API_ACCESS_KEY, apiAccessKey);
			headerMap.put(AmzLiterals.A_JS_X_API_VERSION, apiVersion);
			String output = AmzRestWebserviceUtil.invokePost(postURL, 10, requestEntity, headerMap);
			logger.debug("class: AmzCreateOrderInAmazon | method: createOrderInAmazon output is: " + output);
			prepareAndLogResponse(AmzLiterals.STR_SUCCESS, eleOrder, output, null);
			JSONObject outputJson = new JSONObject(output);
			if (!YFCObject.isVoid(output) && (!outputJson.has("errors"))) {
				handleAmazonCreateOrdSuccessResponse(env, eleOrder, output, amzCreateOrdElgPrimeLineNo,
						amzCreateOrdElgOrderLineKey, strOrdReleaseKey);
			} else if (outputJson.has("errors")) {
				handleAmazonCreateOrderErrors(env, eleOrder, amzCreateOrdElgPrimeLineNo, output, strOrdReleaseKey,
						amzCreateOrdElgOrderLineKey);
			}
		} catch (Exception e) {

			prepareAndLogResponse(AmzLiterals.STR_ERROR, eleOrder, null, e.getMessage());
			YFSException ex = new YFSException();
			ex.setErrorCode("AMAZON_ORDER_CREATION_FAILED");
			ex.setErrorDescription("Amazon Order Creation Failed" + e.getMessage());

			logger.error("Exception in class: AmzCreateOrderInAmazon | method: createOrderInAmazon: "
					+ ExceptionUtils.getStackTrace(ex));
			throw ex;
		}

		logger.info("class: AmzCreateOrderInAmazon | method: createOrderInAmazon -- Ends");
		logger.timer("class: AmzCreateOrderInAmazon | method: createOrderInAmazon -- Ends");

	}

	/*
	 * This method handles the amazon create order Success response
	 */
	private  void handleAmazonCreateOrdSuccessResponse(YFSEnvironment env, Element eleOutOrder, String output,
			List<String> amzCreateOrdElgPrimeLineNo, List<String> amzCreateOrdElgOrderLineKey, String strOrdReleaseKey)
			throws Exception {
		logger.timer("class: AmzCreateOrderInAmazon | method: handleAmazonCreateOrdSuccessResponse -- Starts");
		logger.info("class: AmzCreateOrderInAmazon | method: handleAmazonCreateOrdSuccessResponse -- Starts");

		logger.debug("AmzCreateOrderInAmazon. handleAmazonCreateOrdSuccessResponse output is: " + output);
		if (!YFCObject.isVoid(output)) {
			Document outDoc = PLTJSONUtils.getXmlFromJSON(output, "Root");
			logger.debug("AmzCreateOrderInAmazon. handleAmazonCreateOrdSuccessResponse. outDoc:"
					+ SCXmlUtil.getString(outDoc));

			Element eleAmzOrder = AmzXMLUtil.getXpathElement(outDoc.getDocumentElement(), "data/createOrder/order");
			logger.debug("AmzCreateOrderInAmazon. handleAmazonCreateOrdSuccessResponse.  eleAmzOrder:"
					+ SCXmlUtil.getString(eleAmzOrder));
			if (!YFCObject.isVoid(eleAmzOrder) && !YFCObject.isVoid(eleAmzOrder.getAttribute("id"))) {
				String strAmazonOrderid = eleAmzOrder.getAttribute("id");
				logger.debug("AmzCreateOrderInAmazon. handleAmazonCreateOrdSuccessResponse. strAmazonOrderid:"
						+ strAmazonOrderid);
				AmzUpdateOrdWithAmazonOrdInfo amzUpdateOrdWithAmazonOrdInfo = new AmzUpdateOrdWithAmazonOrdInfo();
				amzUpdateOrdWithAmazonOrdInfo.updateOMSOrderWithAmzResp(env, eleOutOrder, amzCreateOrdElgPrimeLineNo,
						strAmazonOrderid, amzCreateOrdElgOrderLineKey);
				Object objTransaction = env.getTxnObject(AmzCommonConstants.STR_TRANSACTION);
				if (!YFCObject.isVoid(objTransaction)) {
					String strTransaction = String.valueOf(objTransaction);
					if (AmzCommonConstants.STR_RELEASED_ORDER.equalsIgnoreCase(strTransaction)) {
						AmzUpdateOrderRelease amzUpdateOrderRelease  = new  AmzUpdateOrderRelease();
						amzUpdateOrderRelease.updateOMSOrderRelease(env, eleOutOrder, strAmazonOrderid,
								strOrdReleaseKey);
					}
				}
			}
		}
		logger.info("class: AmzCreateOrderInAmazon | method: handleAmazonCreateOrdSuccessResponse -- Ends");
		logger.timer("class: AmzCreateOrderInAmazon | method: handleAmazonCreateOrdSuccessResponse -- Ends");
	}

	/*
	 * This method handles the amazon create order Error response
	 */
	private  void handleAmazonCreateOrderErrors(YFSEnvironment env, Element eleOutOrder,
			List<String> amzCreateOrdElgPrimeLineNo, String output, String strOrdReleaseKey,
			List<String> amzCreateOrdElgOrderLineKey)
			throws RemoteException, JSONException, YIFClientCreationException, XPathExpressionException {
		logger.timer("class: AmzCreateOrderInAmazon | method: handleAmazonCreateOrderErrors -- Starts");
		logger.info("class: AmzCreateOrderInAmazon | method: handleAmazonCreateOrderErrors -- Starts");
		Object objTransaction = env.getTxnObject(AmzCommonConstants.STR_TRANSACTION);
		if (!YFCObject.isVoid(objTransaction) && !YFCObject.isVoid(output)) {
			String strTransaction = String.valueOf(objTransaction);
			if (AmzCommonConstants.STR_RELEASED_ORDER.equalsIgnoreCase(strTransaction) && !YFCObject.isVoid(output)) {
				AmzUpdateOrderRelease amzUpdateOrderRelease =  new AmzUpdateOrderRelease();
				amzUpdateOrderRelease.invokeChangeReleaseToCancel(env, eleOutOrder, strOrdReleaseKey, output,
						amzCreateOrdElgOrderLineKey);
			}
		} else if (!YFCObject.isVoid(output)) {
			AmzUpdateOrdWithAmazonOrdInfo amzUpdateOrdWithAmazonOrdInfo = new AmzUpdateOrdWithAmazonOrdInfo();
			Document outDocChangeOrder = amzUpdateOrdWithAmazonOrdInfo.invokeChangeOrderApi(env, eleOutOrder,
					amzCreateOrdElgPrimeLineNo, output);
			logger.debug("AmzCreateOrderInAmazon:handleAmazonCreateOrderErrors:outDocChangeOrder "
					+ SCXmlUtil.getString(outDocChangeOrder));
		}
		logger.info("class: AmzCreateOrderInAmazon | method: handleAmazonCreateOrderErrors -- Ends");
		logger.timer("class: AmzCreateOrderInAmazon | method: handleAmazonCreateOrderErrors -- Ends");
	}

	/*
	 * This method is to log the request before from amazon create order
	 */
	private  void prepareAndLogRequest(Element eleOrder) {
		logger.beginTimer("class: AmzProcessCreateOrderMessage | method: prepareAndLogRequest -- Starts");
		logger.info("class: AmzProcessCreateOrderMessage | method: prepareAndLogRequest -- Starts");
		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE,
				AmzCommonConstants.STR_AMZCONN_CREATE_ORDER);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,
				eleOrder.getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE));
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO,
				eleOrder.getAttribute(AmzLiterals.A_ORDER_NO));
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_FULFILLABLE_BY_AMAZON, AmzCommonConstants.STR_VAL_Y);
		AmzCommonUtil.logAmzConnRequest(logInput);
		logger.endTimer("class: AmzProcessCreateOrderMessage | method: prepareAndLogRequest -- Ends");
		logger.info("class: AmzProcessCreateOrderMessage | method: prepareAndLogRequest -- Ends");

	}

	/*
	 * This method is to log the response before from amazon create order
	 */
	private  void prepareAndLogResponse(String processStatus, Element eleOrder, String output, String message)
			throws Exception {
		logger.beginTimer("class: AmzProcessCreateOrderMessage | method: prepareAndLogResponse -- Starts");
		logger.info("class: AmzProcessCreateOrderMessage | method: prepareAndLogResponse -- Starts");
		String strAmazonOrderid = null;
		if (!YFCObject.isVoid(output)) {
			Document outDoc = PLTJSONUtils.getXmlFromJSON(output, "Root");
			logger.debug("AmzCreateOrderInAmazon. handleAmazonCreateOrdSuccessResponse. outDoc:"
					+ SCXmlUtil.getString(outDoc));

			Element eleAmzOrder = AmzXMLUtil.getXpathElement(outDoc.getDocumentElement(), "data/createOrder/order");
			logger.debug("AmzCreateOrderInAmazon. handleAmazonCreateOrdSuccessResponse.  eleAmzOrder:"
					+ SCXmlUtil.getString(eleAmzOrder));
			if (!YFCObject.isVoid(eleAmzOrder) && !YFCObject.isVoid(eleAmzOrder.getAttribute("id"))) {
				strAmazonOrderid = eleAmzOrder.getAttribute("id");
			}
		}
		Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE,
				AmzCommonConstants.STR_AMZCONN_CREATE_ORDER);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,
				eleOrder.getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE));
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, strAmazonOrderid);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_OMS_ORDER_NO,
				eleOrder.getAttribute(AmzLiterals.A_ORDER_NO));
		logInput.getDocumentElement().setAttribute(AmzLiterals.A_FULFILLABLE_BY_AMAZON, AmzCommonConstants.STR_VAL_Y);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_HTTP_MESSAGE, AmzCommonConstants.STR_OK);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_HTTP_CODE, AmzCommonConstants.STR_HTTP_STATUS_OK);
		logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_PROCESS_STATUS, "SUCCESS");
		if (!YFCObject.isVoid(message)) {
			if (processStatus.equalsIgnoreCase(AmzLiterals.STR_SUCCESS)) {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_MESSAGE, message);
			} else {
				logInput.getDocumentElement().setAttribute(AmzLiterals.STR_ERROR_MSG, message);
			}
		}
		AmzCommonUtil.logAmzConnResponse(logInput);

		logger.endTimer("class: AmzProcessCreateOrderMessage | method: prepareAndLogResponse -- Ends");
		logger.info("class: AmzProcessCreateOrderMessage | method: prepareAndLogResponse -- Ends");

	}

}
