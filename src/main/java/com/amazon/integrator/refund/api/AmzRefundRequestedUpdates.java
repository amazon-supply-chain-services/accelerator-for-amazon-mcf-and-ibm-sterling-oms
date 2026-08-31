package com.amazon.integrator.refund.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzEnterpriseBPIDXRefUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCCommon;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/*
 * * This class is invoked when a Refund Requested event is triggered by a DELIVERY_CANCELLED or Return order published from Amazon to OMS.
   * It processes the incoming event message from Amazon by converting it into OMS XML format and then posts it to the Internals Queue for further processing.
   * Input to this class:
   * <AmzConnReceiveEventsFromAmazon apiVersion="2024-11-01" eventDescriptor="REFUND_REQUESTED" eventId="6db37637-9836-118b-33c0-a22b159bd85c" eventTime="2025-03-24T17:51:36Z" idempotencyKey="NDBiZTg1ZDMtNjQyZC00ZWY3LTkxMzUtNmJlOGY0ZmZmOTgyIzZkYjM3NjM3LTk4MzYtMTE4Yi0zM2MwLWEyMmIxNTliZDg1Yw==" subscriptionId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"> <data/> <resources>businessProduct/bp-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/order/322-95KP-FGEPY5/refund/d057ea61954b11ee56fe2dd4bfbb05610b1ba3a1fd7ad0c0cd064c0fba848684</resources> </AmzConnReceiveEventsFromAmazon>
   output message posted to Queue from this class format:
    <?xml version="1.0" encoding="UTF-8"?>
	<Refund BusinessProductID="bp-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
    	EventId="6db37637-9836-118b-33c0-a22b159bd85c"
    	EventTime="2025-03-24T17:51:36Z" EventType="REFUND_REQUESTED"
    	IdempotencyKey="NDBiZTg1ZDMtNjQyZC00ZWY3LTkxMzUtNmJlOGY0ZmZmOTgyIzZkYjM3NjM3LTk4MzYtMTE4Yi0zM2MwLWEyMmIxNTliZDg1Yw=="
    	Resources="businessProduct/bp-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/order/322-95KP-FGEPY5/refund/d057ea61954b11ee56fe2dd4bfbb05610b1ba3a1fd7ad0c0cd064c0fba848684" SubscriptionId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx">
    	<RefundDetails>
        <RefundDetail AmazonOrderId="322-95KP-FGEPY5" RefundId="d057ea61954b11ee56fe2dd4bfbb05610b1ba3a1fd7ad0c0cd064c0fba848684"/>
    	</RefundDetails>
	</Refund>
 */
public class AmzRefundRequestedUpdates implements YIFCustomApi {
	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzRefundRequestedUpdates.class);
	private Properties props;
	Map<String, String> genricPropertiesMap = new HashMap<>();
	/*
	 * * This class is invoked when a Refund Requested event is triggered by a DELIVERY_CANCELLED or Return order published from Amazon to OMS.
	 * It processes the incoming event message from Amazon by converting it into OMS XML format and then posts it to the Internals Queue for further processing.
	 */
	public Document processRefundRequestInputAndPostToQueue(YFSEnvironment env, Document inputDoc)
			throws Exception {
		logger.beginTimer("class: AmzRefundRequestedUpdates | method: processRefundRequestInputAndPostToQueue -- Starts");
		logger.info("class: AmzRefundRequestedUpdates | method: processRefundRequestInputAndPostToQueue -- Starts");
		logger.debug("class: AmzRefundRequestedUpdates | method: processRefundRequestInputAndPostToQueue Input Doc is:"	+ AmzXMLUtil.getString(inputDoc));
		AmzCommonUtil.logAmzConnRequest(inputDoc);
		Document outDoc = null;
		Document responseDoc = null;
		try {
			AmzCommonUtil.logAmzConnRequest(inputDoc);
			HashMap<String, String> errorMap = validateInputMsg(inputDoc);

			if (AmzLiterals.STR_VAL_Y.equals(errorMap.get(AmzLiterals.STR_IS_VLD_MSG))) {

				Element eleInputDoc = inputDoc.getDocumentElement();
				String strEventDescriptor = eleInputDoc.getAttribute("eventDescriptor");
				outDoc = SCXmlUtil.createDocument("Refund");
				Element eleRefund = outDoc.getDocumentElement();
				eleRefund.setAttribute("EventType", strEventDescriptor);
				eleRefund.setAttribute("EventId", eleInputDoc.getAttribute("eventId"));
				eleRefund.setAttribute("EventTime", eleInputDoc.getAttribute("eventTime"));
				eleRefund.setAttribute("IdempotencyKey", eleInputDoc.getAttribute("idempotencyKey"));
				eleRefund.setAttribute("SubscriptionId", eleInputDoc.getAttribute("subscriptionId"));
				Element eleRefundDetails = SCXmlUtil.createChild(eleRefund, "RefundDetails");
				Element eleRefundDetail = null;
				
				NodeList nlResources = inputDoc.getDocumentElement().getElementsByTagName("resources");
				for (int k = 0; k < nlResources.getLength(); k++) {
					Element eleResources = (Element) nlResources.item(k);
					String strResources = eleResources.getTextContent();
					eleRefund.setAttribute("Resources", strResources);
					String[] splitResources = strResources.split("/");
					String strRefundId = splitResources[splitResources.length - 1];
					logger.debug("RefundId is: " + strRefundId);
					String strAmzOrderId = splitResources[splitResources.length - 3];
					logger.debug("AmazonOrderId is: " + strAmzOrderId);
					eleRefundDetail = SCXmlUtil.createChild(eleRefundDetails, "RefundDetail");
					eleRefundDetail.setAttribute("AmazonOrderId", strAmzOrderId);
					eleRefundDetail.setAttribute("RefundId", strRefundId);
					String businessProductID = splitResources[1];
					eleRefund.setAttribute("BusinessProductID", businessProductID);
				}
				
				/*DROP3- DEFECT No-3 Starts
				String strEnterpriseCode = AmzEnterpriseBPIDXRefUtil.getEnterpriseCode(env,eleRefund.getAttribute("BusinessProductID"));
				 Fetch enterprise level generic properties
				Document propertyDoc = SCXmlUtil.createDocument(AmzLiterals.E_PROPERTIES);
				propertyDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);
				genricPropertiesMap = AmzGetGenericProperty.getGenericProperties(env, propertyDoc);
				String serviceName = props.getProperty("ServiceName");
				if (strEventDescriptor.equalsIgnoreCase(genricPropertiesMap.get(AmzCommonConstants.PROP_RETUND_EVENT))) {
				AmzCommonUtil.callService(env, outDoc, serviceName, null);
				}
				DROP3- DEFECT No-3 Ends*/
				
				String serviceName = props.getProperty("ServiceName");
				AmzCommonUtil.callService(env, outDoc, serviceName, null);
				
				
				responseDoc = SCXmlUtil.createDocument("Response");
				responseDoc.getDocumentElement().setAttribute("status", AmzCommonConstants.STR_HTTP_STATUS_OK);
				responseDoc.getDocumentElement().setAttribute("message", AmzCommonConstants.STR_OK);

			} else {

				YFSException yfsException = new YFSException();
				yfsException.setErrorCode("INPUT_ERR_MANDATORY_ATTR_MISSING");
				yfsException.setErrorDescription(errorMap.get("ErrorMsg"));
				logger.error("Exception in AmzRefundRequestedUpdates.processRefundRequestInputAndPostToQueue Method: "
						+ ExceptionUtils.getStackTrace(yfsException));
				prepareAndErrorLogResponse(inputDoc, yfsException.getErrorCode(), yfsException.getErrorDescription());
				throw yfsException;
			}
		} catch (YFSException excep) {
			throw AmzCommonUtil.createException(excep);
		}
		logger.endTimer("class: AmzRefundRequestedUpdates | method: processRefundRequestInputAndPostToQueue -- Ends");
		logger.info("class: AmzRefundRequestedUpdates | method: processRefundRequestInputAndPostToQueue -- Ends");
		return responseDoc;
	}

	/*
	 * This method is to prepare and log the Error Response
	 */
	private void prepareAndErrorLogResponse(Document inputDoc, String errorCode, String errorDescription) {
		logger.beginTimer("class: AmzRefundRequestedUpdates | method: prepareAndErrorLogResponse -- Starts");
		logger.info("class: AmzRefundRequestedUpdates | method: prepareAndErrorLogResponse -- Starts");
		Element eleroot = inputDoc.getDocumentElement();
		eleroot.setAttribute(AmzLiterals.ATTR_HTTP_CODE, errorCode);
		eleroot.setAttribute(AmzLiterals.ATTR_HTTP_MESSAGE, errorDescription);
		AmzCommonUtil.logAmzConnResponse(inputDoc);
		logger.endTimer("class: AmzRefundRequestedUpdates | method: prepareAndErrorLogResponse -- Ends");
		logger.info("class: AmzRefundRequestedUpdates | method: prepareAndErrorLogResponse -- Ends");
	}

	/*
	 * This method validate all necessary data for delivery update from amazon is
	 * present or not
	 */
	private HashMap<String, String> validateInputMsg(Document inDoc) {
		logger.beginTimer("class: AmzRefundRequestedUpdates | method: validateInputMsg -- Starts");
		logger.info("class: AmzRefundRequestedUpdates | method: validateInputMsg -- Starts");
		HashMap<String, String> errorMap = new HashMap<>();
		try {
			Element inDocEle = inDoc.getDocumentElement();	
			String resource = "";
			String businessProductID = "";
			int isplitResourceLen = 0;
			NodeList nlResources = inDoc.getDocumentElement().getElementsByTagName("resources");
			for (int k = 0; k < nlResources.getLength(); k++) {
				Element resourcesEle = (Element) nlResources.item(k);
				if (!YFCCommon.isVoid(resourcesEle)) {
					resource = resourcesEle.getTextContent();
					String[] splitResources = resource.split("/");
					isplitResourceLen = splitResources.length;
					businessProductID = splitResources[1];
				}
				}

			String strRefundIdPrefix = props.getProperty(AmzCommonConstants.STR_REFUND_ID_PRIFIX);
			String strOrdIdPrefix = props.getProperty(AmzCommonConstants.AMZ_ORDER_ID_PREFIX);
			String streventDescriptor = inDocEle.getAttribute("eventDescriptor");

			boolean isValidMsg = true;
			String strErrorMsg = "";

			if (YFCCommon.isVoid(streventDescriptor)) {
				isValidMsg = false;
				strErrorMsg = "Event Descriptor is blank in the input";
			} else if (YFCCommon.isVoid(resource)) {
				isValidMsg = false;
				strErrorMsg = "Resource is blank in the input resources filed value";
			} else if (YFCCommon.isVoid(resource.substring(strOrdIdPrefix.length()))) {
				isValidMsg = false;
				strErrorMsg = "Order ID is blank in the input resources filed value";
			} else if (!resource.contains(strRefundIdPrefix)) {
				isValidMsg = false;
				strErrorMsg = "Refund ID is blank in the input resources filed value";
			} else if (isplitResourceLen < 6) {
				isValidMsg = false;
				strErrorMsg = " Incorrect Resource Data in the input resources filed value";
			}else if (YFCCommon.isVoid(businessProductID)) {
				isValidMsg = false;
				strErrorMsg = "Business product ID is blank in the input resources filed value";
			}
			if (isValidMsg) {
				errorMap.put(AmzLiterals.STR_IS_VLD_MSG, AmzLiterals.STR_VAL_Y);
			} else {
				errorMap.put(AmzLiterals.STR_IS_VLD_MSG, AmzLiterals.STR_VAL_N);
			}

			errorMap.put("ErrorMsg", strErrorMsg);

			logger.info("class: AmzRefundRequestedUpdates | method: validateInputMsg -- Ends");
			logger.endTimer("class: AmzRefundRequestedUpdates | method: validateInputMsg -- Ends");
		} catch (Exception ex) {
			YFSException yfsException = new YFSException();
			yfsException.setErrorCode("INPUT_ERR_WHILE_VALIDATING");
			yfsException.setErrorDescription("Error While Validating the Input");
			logger.error("Exception in AmzRefundRequestedUpdates.formatInputJson Method: "
                    + ExceptionUtils.getStackTrace(yfsException));
			throw AmzCommonUtil.createException(yfsException);
		}
		return errorMap;
	}

	@Override
	public void setProperties(Properties inProps) {
		this.props = inProps;
	}
}
