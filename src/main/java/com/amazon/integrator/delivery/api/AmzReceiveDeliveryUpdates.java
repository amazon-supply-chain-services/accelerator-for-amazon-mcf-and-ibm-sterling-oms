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
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCCommon;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/*
 * This class is called  when Delivery update events such as PACKAGE_DELIVERY_IN_TRANSIT, PACKAGE_DELIVERED, DELIVERY_CANCELLED are published from Amazon to OMS,
 * This class will convert the incoming event message from Amazon into OMS XML and post it into the Internals Queue for processing
 * 
 * Input to this class:
 *      <?xml version="1.0" encoding="UTF-8"?>
 		<AmzConnReceiveEventsFromAmazon apiVersion="2024-11-01" eventDescriptor="PACKAGE_DELIVERY_IN_TRANSIT" eventId="09485e0d-3d77-96f6-aa2c-7f8dbc647768" eventTime="2025-02-20T09:16:00Z" idempotencyKey="YzQ1N2VmNTYtZGRmNi00NTYwLWFjZjQtZTYzMTdlYTVjMjUxIzA5NDg1ZTBkLTNkNzctOTZmNi1hYTJjLTdmOGRiYzY0Nzc2OA==" subscriptionId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx">
			<data/>
			<resources>businessProduct/bp-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/order/322-43Q4-R3S5J8/delivery/95c933bf-81af-4493-81ed-4774fb67ed52</resources>
		</AmzConnReceiveEventsFromAmazon>
		
		output message posted to Queue from this class format:
		<?xml version="1.0" encoding="UTF-8"?>
		<Shipment AmazonOrderId="322-43Q4-R3S5J8" EventTime="2025-02-20T09:16:00Z" EventId="09485e0d-3d77-96f6-aa2c-7f8dbc647768"
    		EventType="PACKAGE_DELIVERY_IN_TRANSIT" IdempotencyKey="YWIzODlhZjktYzAxYy00YjRkLTk3MjItZjVlMjcxOGM5MjVmIzg1YWQwZjkxLWFmMmEtNjE5OS1mMDYxLWRiMTMzNGRjMjEwZQ==" 
				SubscriptionId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" 
				Resources="businessProduct/bp-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/order/322-43Q4-R3S5J8/delivery/95c933bf-81af-4493-81ed-4774fb67ed52">
		    <Containers>
		        <Container ExtnAmazonDeliveryID="95c933bf-81af-4493-81ed-4774fb67ed52"/>
		    </Containers>
		</Shipment>

 */
public class AmzReceiveDeliveryUpdates implements YIFCustomApi {
	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzReceiveDeliveryUpdates.class);
	private Properties props;

	/*
	 * processDelUpdateInputAndPostToQueue method is called when Delivery update
	 * events such as PACKAGE_DELIVERY_IN_TRANSIT, PACKAGE_DELIVERED,
	 * DELIVERY_CANCELLED are published from Amazon to OMS, This method convert the
	 * event message from amazon to required OMS format. post it into the Internals
	 * Queue for processing
	 */
	public Document processDelUpdateInputAndPostToQueue(YFSEnvironment env, Document inputDoc)
			throws RemoteException, YIFClientCreationException {
		logger.beginTimer("class: AmzReceiveDeliveryUpdates | method: processDelUpdateInputAndPostToQueue -- Starts");
		logger.info("class: AmzReceiveDeliveryUpdates | method: processDelUpdateInputAndPostToQueue -- Starts");
		logger.info("class: AmzReceiveDeliveryUpdates | method: processDelUpdateInputAndPostToQueue Input Doc is:"
				+ AmzXMLUtil.getString(inputDoc));
		AmzCommonUtil.logAmzConnRequest(inputDoc);
		Document outDoc = null;
		Document responseDoc = null;
		try {
			AmzCommonUtil.logAmzConnRequest(inputDoc);
			HashMap<String, String> errorMap = validateInputMsg(inputDoc);

			if ("Y".equals(errorMap.get(AmzLiterals.STR_IS_VLD_MSG))) {

				Element eleInputDoc = inputDoc.getDocumentElement();
				String strEventDescriptor = eleInputDoc.getAttribute("eventDescriptor");
				outDoc = SCXmlUtil.createDocument("Shipment");
				Element eleShipment = outDoc.getDocumentElement();
				eleShipment.setAttribute("EventType", strEventDescriptor);
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
				String strDeliveryId = splitResources[splitResources.length - 1];
				logger.debug("AmazonDeliveryId is: " + strDeliveryId);
				String strAmzOrderId = splitResources[splitResources.length - 3];
				logger.debug("AmazonOrderId is: " + strAmzOrderId);
				eleShipment.setAttribute("AmazonOrderId", strAmzOrderId);
				eleContainer.setAttribute("ExtnAmazonDeliveryID", strDeliveryId);
				
				String businessProductID = splitResources[1];
				eleShipment.setAttribute("BusinessProductID", businessProductID);

				String serviceName = props.getProperty("ServiceName");

				AmzCommonUtil.callService(env, outDoc, serviceName, null);

				responseDoc = SCXmlUtil.createDocument("Response");
				responseDoc.getDocumentElement().setAttribute("status", AmzCommonConstants.STR_HTTP_STATUS_OK);
				responseDoc.getDocumentElement().setAttribute("message", AmzCommonConstants.STR_OK);

			} else {

				YFSException yfsException = new YFSException();
				yfsException.setErrorCode("INPUT_ERR_MANDATORY_ATTR_MISSING");
				yfsException.setErrorDescription(errorMap.get("ErrorMsg"));
				logger.error("Exception in AmzReceiveDeliveryUpdates.processDelUpdateInputAndPostToQueue Method: "
						+ ExceptionUtils.getStackTrace(yfsException));
				prepareAndErrorLogResponse(inputDoc, yfsException.getErrorCode(), yfsException.getErrorDescription());
				throw yfsException;
			}
		} catch (YFSException excep) {
			throw AmzCommonUtil.createException(excep);
		}
		logger.endTimer("class: AmzReceiveDeliveryUpdates | method: processDelUpdateInputAndPostToQueue -- Ends");
		logger.info("class: AmzReceiveDeliveryUpdates | method: processDelUpdateInputAndPostToQueue -- Ends");
		return responseDoc;
	}

	/*
	 * This method is to prepare and log the Error Response
	 */
	private void prepareAndErrorLogResponse(Document inputDoc, String errorCode, String errorDescription) {
		logger.beginTimer("class: AmzReceiveDeliveryUpdates | method: prepareAndErrorLogResponse -- Starts");
		logger.info("class: AmzReceiveDeliveryUpdates | method: prepareAndErrorLogResponse -- Starts");
		Element eleroot = inputDoc.getDocumentElement();
		eleroot.setAttribute(AmzLiterals.ATTR_HTTP_CODE, errorCode);
		eleroot.setAttribute(AmzLiterals.ATTR_HTTP_MESSAGE, errorDescription);
		AmzCommonUtil.logAmzConnResponse(inputDoc);
		logger.endTimer("class: AmzReceiveDeliveryUpdates | method: prepareAndErrorLogResponse -- Ends");
		logger.info("class: AmzReceiveDeliveryUpdates | method: prepareAndErrorLogResponse -- Ends");
	}

	/*
	 * This method validate all necessary data for delivery update from amazon is
	 * present or not
	 */
	private HashMap<String, String> validateInputMsg(Document inDoc) {
		logger.beginTimer("class: AmzReceiveDeliveryUpdates | method: validateInputMsg -- Starts");
		logger.info("class: AmzReceiveDeliveryUpdates | method: validateInputMsg -- Starts");
		HashMap<String, String> errorMap = new HashMap<>();
		try {
			Element inDocEle = inDoc.getDocumentElement();
			Element resourcesEle = SCXmlUtil.getChildElement(inDocEle, "resources");
			String resource = "";
			String businessProductID = "";
			int isplitResourceLen = 0;
			if (!YFCCommon.isVoid(resourcesEle)) {
				resource = resourcesEle.getTextContent();
				String[] splitResources = resource.split("/");
				isplitResourceLen = splitResources.length;
				businessProductID = splitResources[1];
			}

			String strDelIdPrefix = props.getProperty(AmzCommonConstants.AMZ_DELIVERY_ID_PREFIX);
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
			} else if (!resource.contains(strDelIdPrefix)) {
				isValidMsg = false;
				strErrorMsg = "Delivery ID is blank in the input resources filed value";
			} else if (isplitResourceLen < 6) {
				isValidMsg = false;
				strErrorMsg = " Incorrect Resource Data in the input resources filed value";
			}else if (YFCCommon.isVoid(businessProductID)) {
				isValidMsg = false;
				strErrorMsg = "Business product ID is blank in the input resources filed value";
			}
			if (isValidMsg) {
				errorMap.put(AmzLiterals.STR_IS_VLD_MSG, "Y");
			} else {
				errorMap.put(AmzLiterals.STR_IS_VLD_MSG, "N");
			}

			errorMap.put("ErrorMsg", strErrorMsg);

			logger.info("class: AmzReceiveDeliveryUpdates | method: validateInputMsg -- Ends");
			logger.endTimer("class: AmzReceiveDeliveryUpdates | method: validateInputMsg -- Ends");
		} catch (Exception ex) {
			YFSException yfsException = new YFSException();
			yfsException.setErrorCode("INPUT_ERR_WHILE_VALIDATING");
			yfsException.setErrorDescription("Error While Validating the Input");
			logger.error("Exception in AmzReceiveDeliveryUpdates.formatInputJson Method: "
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
