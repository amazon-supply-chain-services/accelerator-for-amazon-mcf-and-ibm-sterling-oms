package com.amazon.integrator.returns.api;

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
 * This class is called  when Delivery update events such as RETURN_STARTED, RETURN_DELIVERY_IN_TRANSIT, RETURN_DELIVERY_FAILED,RETURN_DELIVERY_COMPLETED,RETURN_ITEM_GRADED,RETURN_COMPLETED are published from Amazon to OMS,
 * This class will convert the incoming event message from Amazon into OMS XML and post it into the Internals Queue for processing
 * 
 * Input to this class:
 *     <AmzConnReceiveEventsFromAmazon apiVersion="2024-11-01" eventDescriptor="RETURN_STARTED" eventId="f7e6b174-757c-9d01-5ef6-d784d4bb11a7" eventTime="2025-03-27T20:15:45Z" idempotencyKey="NjcyZjY1MjYtNWQ0ZS00ZGI3LWE2ZWMtYzI4MTk2ZDViYTUxI2Y3ZTZiMTc0LTc1N2MtOWQwMS01ZWY2LWQ3ODRkNGJiMTFhNw==" subscriptionId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx">
			<data/>
			<resources>businessProduct/bp-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/order/322-X2E9-U56245/return/04caec9f-7324-8458-3cda-80d823cf915c#RF</resources>
		</AmzConnReceiveEventsFromAmazon>	
		
		output message posted to Queue from this class format:
		<Order AmazonOrderId="322-CGCE-A7C2FN" AmazonReturnId="9e735c16-d769-49ee-a970-1d886937e0ba" BusinessProductID="bp-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" EventId="25b221c3-2447-f492-0723-9d168fa80f53" EventTime="2025-03-19T11:40:49Z" EventType="RETURN_STARTED" IdempotencyKey="YzQ1N2VmNTYtZGRmNi00NTYwLWFjZjQtZTYzMTdlYTVjMjUxIzI1YjIyMWMzLTI0NDctZjQ5Mi0wNzIzLTlkMTY4ZmE4MGY1Mw==" 
			Resources="businessProduct/bp-xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx/order/322-CGCE-A7C2FN/return/9e735c16-d769-49ee-a970-1d886937e0ba" SubscriptionId="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx">
		</Order>

 */
public class AmzReceiveReturnsUpdates implements YIFCustomApi {
	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzReceiveReturnsUpdates.class);
	private Properties props;

	/*
	 * processReturnsUpdateAndPostToQueue method is called when Delivery update
	 * events such as RETURN_STARTED, RETURN_DELIVERY_IN_TRANSIT, RETURN_DELIVERY_FAILED,RETURN_DELIVERY_COMPLETED,
	 * RETURN_ITEM_GRADED,RETURN_COMPLETED are published from Amazon to OMS, This method convert the
	 * event message from amazon to required OMS format. post it into the Internals
	 * Queue for processing
	 */
	public Document processReturnsUpdateAndPostToQueue(YFSEnvironment env, Document inputDoc)
			throws RemoteException, YIFClientCreationException, InterruptedException {
		logger.beginTimer("class: AmzReceiveReturnsUpdates | method: processReturnsUpdateAndPostToQueue -- Starts");
		logger.info("class: AmzReceiveReturnsUpdates | method: processReturnsUpdateAndPostToQueue -- Starts");
		logger.info("class: AmzReceiveReturnsUpdates | method: processReturnsUpdateAndPostToQueue Input Doc is:"
				+ AmzXMLUtil.getString(inputDoc));
		AmzCommonUtil.logAmzConnRequest(inputDoc);
		Document outDoc = null;
		Document responseDoc = null;
		try {
			AmzCommonUtil.logAmzConnRequest(inputDoc);
			
			HashMap<String, String> errorMap = validateInputMsg(inputDoc);

			if ("Y".equals(errorMap.get(AmzLiterals.STR_IS_VLD_MSG))) {

				Element eleInputDoc = inputDoc.getDocumentElement();
				String strEventDescriptor = eleInputDoc.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_DESC);
				outDoc = SCXmlUtil.createDocument(AmzLiterals.E_ORDER);
				Element eleOrder = outDoc.getDocumentElement();
				eleOrder.setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE, strEventDescriptor);
				eleOrder.setAttribute(AmzLiterals.ATTR_AMZ_EVENT_ID, eleInputDoc.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_ID));
				eleOrder.setAttribute(AmzLiterals.ATTR_EVENT_TIME, eleInputDoc.getAttribute("eventTime"));
				eleOrder.setAttribute("IdempotencyKey", eleInputDoc.getAttribute(AmzLiterals.ATTR_AMAZON_IDEMPOTENCY_KEY));
				eleOrder.setAttribute("SubscriptionId", eleInputDoc.getAttribute(AmzLiterals.ATTR_AMAZON_SUBSCRIPTION_ID));

				Element eleResources = SCXmlUtil.getChildElement(eleInputDoc, AmzLiterals.ATTR_AMAZON_RESOURCES);
				String strResources = eleResources.getTextContent();
				eleOrder.setAttribute("Resources", strResources);

				String[] splitResources = strResources.split("/");
				String strReturnId = splitResources[5];
				logger.debug("AmazonReturnId is: " + strReturnId);
				String strAmzOrderId = splitResources[3];
				logger.debug("AmazonOrderId is: " + strAmzOrderId);
				eleOrder.setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, strAmzOrderId);
				eleOrder.setAttribute(AmzLiterals.ATTR_AMAZON_RETURN_ID, strReturnId);
				
				String businessProductID = splitResources[1];
				eleOrder.setAttribute("BusinessProductID", businessProductID);

				String strReturnDelivery = "";
				if(strEventDescriptor.equalsIgnoreCase(AmzLiterals.STR_RETURN_PACKAGE_IN_TRANSIT) || strEventDescriptor.equalsIgnoreCase(AmzLiterals.STR_RETURN_PACKAGE_DELIVERY_FAILED) || strEventDescriptor.equalsIgnoreCase(AmzLiterals.STR_RETURN_PACKAGE_DELIVERED)) {
					strReturnDelivery = splitResources[splitResources.length - 1];
				}
				
				String strReturnLineItem = "";
				if(strEventDescriptor.equalsIgnoreCase(AmzLiterals.STR_RETURN_ITEM_GRADED)) {
					strReturnLineItem = splitResources[splitResources.length - 1];
				}
				
				eleOrder.setAttribute(AmzLiterals.ATTR_AMAZON_RETURN_LINE_ITEM, strReturnLineItem);
				eleOrder.setAttribute(AmzLiterals.ATTR_AMAZON_RETURN_DELIVERY, strReturnDelivery);
				
				String serviceName = props.getProperty("ServiceName");

				if(strEventDescriptor.equalsIgnoreCase(AmzLiterals.STR_RETURN_COMPLETED)) {
					Thread.sleep(1000);
				}
				AmzCommonUtil.callService(env, outDoc, serviceName, null);

				responseDoc = SCXmlUtil.createDocument("Response");
				responseDoc.getDocumentElement().setAttribute("status", AmzCommonConstants.STR_HTTP_STATUS_OK);
				responseDoc.getDocumentElement().setAttribute("message", AmzCommonConstants.STR_OK);

			} else {

				YFSException yfsException = new YFSException();
				yfsException.setErrorCode("INPUT_ERR_MANDATORY_ATTR_MISSING");
				yfsException.setErrorDescription(errorMap.get("ErrorMsg"));
				logger.error("Exception in AmzReceiveReturnsUpdates.processReturnsUpdateAndPostToQueue Method: "
						+ ExceptionUtils.getStackTrace(yfsException));
				prepareAndErrorLogResponse(inputDoc, yfsException.getErrorCode(), yfsException.getErrorDescription());
				throw yfsException;
			}
		} catch (YFSException excep) {
			throw AmzCommonUtil.createException(excep);
		}
		logger.endTimer("class: AmzReceiveReturnsUpdates | method: processReturnsUpdateAndPostToQueue -- Ends");
		logger.info("class: AmzReceiveReturnsUpdates | method: processReturnsUpdateAndPostToQueue -- Ends");
		return responseDoc;
	}

	/*
	 * This method is to prepare and log the Error Response
	 */
	private void prepareAndErrorLogResponse(Document inputDoc, String errorCode, String errorDescription) {
		logger.beginTimer("class: AmzReceiveReturnsUpdates | method: prepareAndErrorLogResponse -- Starts");
		logger.info("class: AmzReceiveReturnsUpdates | method: prepareAndErrorLogResponse -- Starts");
		Element eleroot = inputDoc.getDocumentElement();
		eleroot.setAttribute(AmzLiterals.ATTR_HTTP_CODE, errorCode);
		eleroot.setAttribute(AmzLiterals.ATTR_HTTP_MESSAGE, errorDescription);
		AmzCommonUtil.logAmzConnResponse(inputDoc);
		logger.endTimer("class: AmzReceiveReturnsUpdates | method: prepareAndErrorLogResponse -- Ends");
		logger.info("class: AmzReceiveReturnsUpdates | method: prepareAndErrorLogResponse -- Ends");
	}

	/*
	 * This method validate all necessary data for delivery update from amazon is
	 * present or not
	 */
	private HashMap<String, String> validateInputMsg(Document inDoc) {
		logger.beginTimer("class: AmzReceiveReturnsUpdates | method: validateInputMsg -- Starts");
		logger.info("class: AmzReceiveReturnsUpdates | method: validateInputMsg -- Starts");
		HashMap<String, String> errorMap = new HashMap<>();
		try {
			Element inDocEle = inDoc.getDocumentElement();
			Element resourcesEle = SCXmlUtil.getChildElement(inDocEle, AmzLiterals.ATTR_AMAZON_RESOURCES);
			String resource = "";
			String businessProductID = "";
			if (!YFCCommon.isVoid(resourcesEle)) {
				resource = resourcesEle.getTextContent();
				String[] splitResources = resource.split("/");
				businessProductID = splitResources[1];
			}

			String strRetIdPrefix = props.getProperty(AmzCommonConstants.AMZ_RETURN_ID_PREFIX);
			String strOrdIdPrefix = props.getProperty(AmzCommonConstants.AMZ_ORDER_ID_PREFIX);

			String streventDescriptor = inDocEle.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_DESC);

			boolean isValidMsg = true;
			String strErrorMsg = "";

			if (YFCCommon.isVoid(streventDescriptor)) {
				isValidMsg = false;
				strErrorMsg = "Event Descriptor is blank in the input";
			} else if (YFCCommon.isVoid(resource)) {
				isValidMsg = false;
				strErrorMsg = "Resource is blank in the input resources field value";
			} else if (YFCCommon.isVoid(!resource.contains(strOrdIdPrefix))) {
				isValidMsg = false;
				strErrorMsg = "Order ID is blank in the input resources field value";
			} else if (!resource.contains(strRetIdPrefix)) {
				isValidMsg = false;
				strErrorMsg = "Return ID is blank in the input resources field value";
			} else if (YFCCommon.isVoid(businessProductID)) {
				isValidMsg = false;
				strErrorMsg = "Business product ID is blank in the input resources field value";
			}
			
			if(streventDescriptor.equalsIgnoreCase(AmzLiterals.STR_RETURN_PACKAGE_IN_TRANSIT) || streventDescriptor.equalsIgnoreCase(AmzLiterals.STR_RETURN_PACKAGE_DELIVERY_FAILED) || streventDescriptor.equalsIgnoreCase(AmzLiterals.STR_RETURN_PACKAGE_DELIVERED)) {
				int startIndex = resource.indexOf("/returnDelivery/");
				String returnDelivery = "";
				if (startIndex != -1) {
		        	startIndex += "/returnDelivery/".length();
		        	returnDelivery = resource.substring(startIndex);
		        }
				
				if (YFCCommon.isVoid(returnDelivery)) {
					isValidMsg = false;
					strErrorMsg = "Return Delivery ID is blank in the input resources field value";
				}
			}
			
			if(streventDescriptor.equalsIgnoreCase(AmzLiterals.STR_RETURN_ITEM_GRADED)) {
				int startIndex = resource.indexOf("/returnLineItem/");
				String returnLineItem = "";
				if (startIndex != -1) {
		        	startIndex += "/returnLineItem/".length();
		        	returnLineItem = resource.substring(startIndex);
		        }
				
				if (YFCCommon.isVoid(returnLineItem)) {
					isValidMsg = false;
					strErrorMsg = "Return Line Item is blank in the input resources field value";
				}
			}
			
			if (isValidMsg) {
				errorMap.put(AmzLiterals.STR_IS_VLD_MSG, "Y");
			} else {
				errorMap.put(AmzLiterals.STR_IS_VLD_MSG, "N");
			}

			errorMap.put("ErrorMsg", strErrorMsg);

			logger.info("class: AmzReceiveReturnsUpdates | method: validateInputMsg -- Ends");
			logger.endTimer("class: AmzReceiveReturnsUpdates | method: validateInputMsg -- Ends");
		} catch (Exception ex) {
			YFSException yfsException = new YFSException();
			yfsException.setErrorCode("INPUT_VALIDATION_ERROR");
			yfsException.setErrorDescription("Error While Validating the Input");
			logger.error("Exception in AmzReceiveReturnsUpdates.formatInputJson Method: "
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
