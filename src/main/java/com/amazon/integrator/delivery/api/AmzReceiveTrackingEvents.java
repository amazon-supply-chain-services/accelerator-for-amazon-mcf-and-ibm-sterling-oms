package com.amazon.integrator.delivery.api;


import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Properties;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.interop.japi.YIFClientCreationException;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCCommon;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/*
 * This class is invoked from AmazonReceiveTrackingUpdates service
 * This class is called when Tracking milestones update events such as PACKAGE_TRACKER_MILESTONE_CHANGED are published from Amazon to OMS,
 * Validate the input and convert into the required OMS XML format and post into the internal Queue for processing
 * 
 * input message to this class is:
 * <?xml version="1.0" encoding="UTF-8"?>
		<AmzConnReceiveEventsFromAmazon apiVersion="2024-11-01" eventDescriptor="PACKAGE_TRACKER_MILESTONE_CHANGED" eventId="625ce02b-f6fa-e43a-f0ad-df329ddac8ac" eventTime="2025-02-20T12:12:55Z" idempotencyKey="YWIzODlhZjktYzAxYy00YjRkLTk3MjItZjVlMjcxOGM5MjVmIzYyNWNlMDJiLWY2ZmEtZTQzYS1mMGFkLWRmMzI5ZGRhYzhhYw==" subscriptionId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx">
			<data/>
			<resources>businessProduct/bp-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/order/322-86T6-8X4JBH/carrier/ups/tracker/1Z679A7B7F643440BC80AF5461C8D7C2EB</resources>
		</AmzConnReceiveEventsFromAmazon>	
		
		output XML from this class is:
		<Shipment AmazonOrderID="322-86T6-8X4JBH" EventTime="2025-02-20T09:16:00Z" EventId="09485e0d-3d77-96f6-aa2c-7f8dbc647768" 
			IdempotencyKey="YWIzODlhZjktYzAxYy00YjRkLTk3MjItZjVlMjcxOGM5MjVmIzg1YWQwZjkxLWFmMmEtNjE5OS1mMDYxLWRiMTMzNGRjMjEwZQ==" 
			SubscriptionId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" 
			Resources="businessProduct/bp-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/order/322-GU8T-FB2RXF/carrier/ups/tracker/1Z4BD5B8F4915149298BF35705A5D346D3">
			<Containers>
        		<Container TrackingNo="1ZBAC8EA382C7C405EA32066B9F8BD2Csbfsv">
        		</Container>
			</Containers>
		</Shipment> 

 */
public class AmzReceiveTrackingEvents {

	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzReceiveTrackingEvents.class);
	private Properties props;

	/*
	 * This processDelUpdateInputAndPostToQueue method is called when Tracking
	 * milestones update events such as PACKAGE_TRACKER_MILESTONE_CHANGED are
	 * published from Amazon to OMS
	 */
	public Document receiveTrackingEventAndPostToQueue(YFSEnvironment env, Document inputDoc)
			throws RemoteException, YIFClientCreationException {
		logger.beginTimer("class: AmzReceiveTrackingEvents | method: receiveTrackingEventAndPostToQueue -- Starts");
		logger.info("class: AmzReceiveTrackingEvents | method: receiveTrackingEventAndPostToQueue -- Starts");
		logger.info("class: AmzReceiveTrackingEvents | method: receiveTrackingEventAndPostToQueue -- Starts InDoc is:"
				+ AmzXMLUtil.getString(inputDoc));
		AmzCommonUtil.logAmzConnRequest(inputDoc);
		Document outDoc = null;
		Document responseDoc = null;
		try {

			HashMap<String, String> errorMap = validateInputMsg(inputDoc);

			if ("Y".equals(errorMap.get(AmzLiterals.STR_IS_VLD_MSG))) {
				Element eleInputDoc = inputDoc.getDocumentElement();
				outDoc = SCXmlUtil.createDocument("Shipment");
				Element eleShipment = outDoc.getDocumentElement();
				eleShipment.setAttribute("EventType", eleInputDoc.getAttribute("eventDescriptor"));
				eleShipment.setAttribute("EventId", eleInputDoc.getAttribute("eventId"));
				eleShipment.setAttribute("EventTime", eleInputDoc.getAttribute("eventTime"));
				eleShipment.setAttribute("IdempotencyKey", eleInputDoc.getAttribute("idempotencyKey"));
				eleShipment.setAttribute("SubscriptionId", eleInputDoc.getAttribute("subscriptionId"));
				Element eleContainers = SCXmlUtil.createChild(eleShipment, "Containers");
				Element eleContainer = SCXmlUtil.createChild(eleContainers, "Container");

				Element eleResources = SCXmlUtil.getChildElement(eleInputDoc, "resources");
				String strResources = eleResources.getTextContent();
				eleShipment.setAttribute("Resources", strResources);
				String[] splitResources = strResources.split("/");
				String strTrackingNo = splitResources[splitResources.length - 1];
				logger.debug("strTrackingNo is: " + strTrackingNo);

				String strAmzOrderId = splitResources[splitResources.length - 5];
				logger.debug("strAmzOrderId is: " + strAmzOrderId);

				eleShipment.setAttribute("AmazonOrderId", strAmzOrderId);
				eleContainer.setAttribute("TrackingNo", strTrackingNo);
				String businessProductID = splitResources[1];
                eleShipment.setAttribute("BusinessProductID", businessProductID);
				String serviceName = props.getProperty("ServiceName");
				logger.debug("outDoc is: " + AmzXMLUtil.getString(outDoc));

				AmzCommonUtil.callService(env, outDoc, serviceName, null);

				responseDoc = SCXmlUtil.createDocument("Response");
				responseDoc.getDocumentElement().setAttribute("status", AmzCommonConstants.STR_HTTP_STATUS_OK);
				responseDoc.getDocumentElement().setAttribute("message", AmzCommonConstants.STR_OK);

			} else {
				YFSException yfsException = new YFSException();
				yfsException.setErrorCode("INPUT_ERR_MANDATORY_ATTR_MISSING");
				yfsException.setErrorDescription(errorMap.get("ErrorMsg"));
				logger.error("Exception in AmzReceiveTrackingEvents.receiveTrackingEventAndPostToQueue Method: "
						+ ExceptionUtils.getStackTrace(yfsException));
				prepareAndErrorLogResponse(inputDoc, yfsException.getErrorCode(), yfsException.getErrorDescription());
				throw yfsException;
			}
		} catch (YFSException excep) {
			throw AmzCommonUtil.createException(excep);
		}
		logger.endTimer("class: AmzReceiveTrackingEvents | method: receiveTrackingEventAndPostToQueue -- Ends");
		logger.info("class: AmzReceiveTrackingEvents | method: receiveTrackingEventAndPostToQueue -- Ends");
		return responseDoc;
	}

	/*
	 * This method validate all necessary data for package tracker milestone event
	 * from amazon is present or not
	 */
	private HashMap<String, String> validateInputMsg(Document inDoc) {
		logger.beginTimer("class: AmzReceiveTrackingEvents | method: validateInputMsg -- Starts");
		logger.info("class: AmzReceiveTrackingEvents | method: validateInputMsg -- Starts");
		HashMap<String, String> errorMap = new HashMap<>();
		try {
			Element inDocEle = inDoc.getDocumentElement();
			Element resourcesEle = SCXmlUtil.getChildElement(inDocEle, "resources");
			String resource = "";
			int isplitResourceLen = 0;
			if (!YFCCommon.isVoid(resourcesEle)) {
				resource = resourcesEle.getTextContent();
				String[] splitResources = resource.split("/");
				isplitResourceLen = splitResources.length;
			}

			String strTrackingNoPrefix = props.getProperty(AmzCommonConstants.AMZ_TRACKING_NO_PREFIX);
			String strOrdIdPrefix = props.getProperty(AmzCommonConstants.AMZ_ORDER_ID_PREFIX);
			String strEventDescriptor = inDocEle.getAttribute("eventDescriptor");
			logger.debug("strEventDescriptor is: " + (strEventDescriptor));

			boolean isValidMsg = true;
			String strErrorMsg = "";

			if (YFCCommon.isVoid(strEventDescriptor)) {
				isValidMsg = false;
				strErrorMsg = "Event Descriptor is blank in the input";
			} else if (YFCCommon.isVoid(resource)) {
				isValidMsg = false;
				strErrorMsg = "Resource is blank in the input resources filed value";
			} else if (!resource.contains(strOrdIdPrefix)) {
				isValidMsg = false;
				strErrorMsg = "Order ID is blank in the input resources filed value";
			} else if (!resource.contains(strTrackingNoPrefix)) {
				isValidMsg = false;
				strErrorMsg = "Tracking No is blank";
			} else if (isplitResourceLen < 8) {
				isValidMsg = false;
				strErrorMsg = " Incorrect Resource Date in the input resources filed value";
			}

			if (isValidMsg) {
				errorMap.put(AmzLiterals.STR_IS_VLD_MSG, "Y");
			} else {
				errorMap.put(AmzLiterals.STR_IS_VLD_MSG, "N");
			}

			errorMap.put("ErrorMsg", strErrorMsg);

			logger.info("class: AmzReceiveTrackingEvents | method: validateInputMsg -- Ends");
			logger.endTimer("class: AmzReceiveTrackingEvents | method: validateInputMsg -- Ends");
		} catch (Exception ex) {
			YFSException yfsException = new YFSException();
			yfsException.setErrorCode("INPUT_ERR_WHILE_VALIDATING");
			yfsException.setErrorDescription("Error While Validating the Input.");
			logger.error("Exception in AmzReceiveTrackingEvents.formatInputJson Method: "
                    + ExceptionUtils.getStackTrace(yfsException));
			throw AmzCommonUtil.createException(yfsException);
		}
		return errorMap;
	}
	
	/*
	 * This method is to prepare and log the Error Response
	 */
	private void prepareAndErrorLogResponse(Document inputDoc, String errorCode, String errorDescription) {
		logger.beginTimer("class: AmzReceiveTrackingEvents | method: prepareAndErrorLogResponse -- Starts");
		logger.info("class: AmzReceiveTrackingEvents | method: prepareAndErrorLogResponse -- Starts");
		Element eleroot = inputDoc.getDocumentElement();
		eleroot.setAttribute(AmzLiterals.ATTR_HTTP_CODE, errorCode);
		eleroot.setAttribute(AmzLiterals.ATTR_HTTP_MESSAGE, errorDescription);
		AmzCommonUtil.logAmzConnResponse(inputDoc);
		logger.endTimer("class: AmzReceiveTrackingEvents | method: prepareAndErrorLogResponse -- Ends");
		logger.info("class: AmzReceiveTrackingEvents | method: prepareAndErrorLogResponse -- Ends");
	}

	public void setProperties(Properties inProps) {
		this.props = inProps;
	}

}
