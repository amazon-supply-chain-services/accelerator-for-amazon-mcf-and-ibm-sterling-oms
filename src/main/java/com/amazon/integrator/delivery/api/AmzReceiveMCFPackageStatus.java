package com.amazon.integrator.delivery.api;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Properties;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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

/**
 * This class receives MCF FULFILLMENT_ORDER_STATUS notifications
 * for SHIPMENT_PACKAGE_STATUS_CHANGED events only, and posts the event XML
 * as-is to the internal queue for processing by AmzProcessMCFPackageStatusEvent.
 *
 * Input Sample - SHIPMENT_PACKAGE_STATUS_CHANGED (Delivered):
 * <AmzProcessShipmentPackageStatusChangedEvent eventType="SHIPMENT_PACKAGE_STATUS_CHANGED"
 *     fulfillentServiceId="FS01-o8vlfw8b1tiu1" merchantId="merchantId">
 *   <order orderId="Y100003115-01" status="COMPLETE">
 *     <shipments amazonShipmentId="09581f64ed04398cb1953e2ca550809b0" status="SHIPPED">
 *       <amazonFacility facilityId="FC456"/>
 *       <items lineItemId="202603151921549163264" packageId="13153368">
 *         <amount unit="EACHES" value="1.0"/>
 *         <productIdentifier amazonSku="Deep_bwp_MCF_6"/>
 *       </items>
 *       <packages packageId="13153368" status="DELIVERED">
 *         <tracking>
 *           <carrier carrierCode="FEDEX" trackingNumber="123456789"/>
 *           <amazon trackingNumber="234567890"/>
 *         </tracking>
 *       </packages>
 *     </shipments>
 *   </order>
 * </AmzProcessShipmentPackageStatusChangedEvent>
 *
 * Input Sample - SHIPMENT_PACKAGE_STATUS_CHANGED (Delayed):
 * <AmzProcessShipmentPackageStatusChangedEvent eventType="SHIPMENT_PACKAGE_STATUS_CHANGED"
 *     fulfillentServiceId="FS01-o8vlfw8b1tiu1" merchantId="merchantId">
 *   <order orderId="Y100003115-01" status="PROCESSING">
 *     <shipments amazonShipmentId="09581f64ed04398cb1953e2ca550809b0" status="SHIPPED">
 *       <amazonFacility facilityId="FC456"/>
 *       <items lineItemId="202603151921549163264" packageId="13153368">
 *         <amount unit="EACHES" value="1.0"/>
 *         <productIdentifier amazonSku="Deep_bwp_MCF_6"/>
 *       </items>
 *       <packages packageId="13153368" status="DELAYED">
 *         <tracking>
 *           <carrier carrierCode="FEDEX" trackingNumber="123456789"/>
 *           <amazon trackingNumber="234567890"/>
 *         </tracking>
 *       </packages>
 *     </shipments>
 *   </order>
 * </AmzProcessShipmentPackageStatusChangedEvent>
 *
 * Input Sample - SHIPMENT_PACKAGE_STATUS_CHANGED (Undeliverable):
 * <AmzProcessShipmentPackageStatusChangedEvent eventType="SHIPMENT_PACKAGE_STATUS_CHANGED"
 *     fulfillentServiceId="FS01-o8vlfw8b1tiu1" merchantId="merchantId">
 *   <order orderId="Y100003115-01" status="PROCESSING">
 *     <shipments amazonShipmentId="09581f64ed04398cb1953e2ca550809b0" status="SHIPPED">
 *       <amazonFacility facilityId="FC456"/>
 *       <items lineItemId="202603151921549163264" packageId="13153368">
 *         <amount unit="EACHES" value="1.0"/>
 *         <productIdentifier amazonSku="Deep_bwp_MCF_6"/>
 *       </items>
 *       <packages packageId="13153368" status="UNDELIVERABLE">
 *         <tracking>
 *           <carrier carrierCode="FEDEX" trackingNumber="123456789"/>
 *           <amazon trackingNumber="234567890"/>
 *         </tracking>
 *       </packages>
 *     </shipments>
 *   </order>
 * </AmzProcessShipmentPackageStatusChangedEvent>
 */
public class AmzReceiveMCFPackageStatus implements YIFCustomApi {
	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzReceiveMCFPackageStatus.class);
	private static final String EXPECTED_EVENT_TYPE = "SHIPMENT_PACKAGE_STATUS_CHANGED";
	private Properties props;

	public Document processEventAndPostToQueue(YFSEnvironment env, Document inputDoc)
			throws RemoteException, YIFClientCreationException {
		logger.beginTimer("class: AmzReceiveMCFPackageStatus | method: processEventAndPostToQueue -- Starts");
		logger.info("class: AmzReceiveMCFPackageStatus | method: processEventAndPostToQueue -- Starts");
		logger.info("class: AmzReceiveMCFPackageStatus | method: processEventAndPostToQueue Input Doc is:"
				+ AmzXMLUtil.getString(inputDoc));
		AmzCommonUtil.logAmzConnRequest(inputDoc);
		Document responseDoc = null;
		try {
			HashMap<String, String> errorMap = validateInputMsg(inputDoc);

			if ("Y".equals(errorMap.get(AmzLiterals.STR_IS_VLD_MSG))) {

				String serviceName = props.getProperty("ServiceName");
				AmzCommonUtil.callService(env, inputDoc, serviceName, null);

				responseDoc = SCXmlUtil.createDocument("Response");
				responseDoc.getDocumentElement().setAttribute("status", AmzCommonConstants.STR_HTTP_STATUS_OK);
				responseDoc.getDocumentElement().setAttribute("message", AmzCommonConstants.STR_OK);

			} else {
				YFSException yfsException = new YFSException();
				yfsException.setErrorCode("INPUT_ERR_MANDATORY_ATTR_MISSING");
				yfsException.setErrorDescription(errorMap.get("ErrorMsg"));
				logger.error("Exception in AmzReceiveMCFPackageStatus.processEventAndPostToQueue Method: "
						+ ExceptionUtils.getStackTrace(yfsException));
				prepareAndErrorLogResponse(inputDoc, yfsException.getErrorCode(), yfsException.getErrorDescription());
				throw yfsException;
			}
		} catch (YFSException excep) {
			throw AmzCommonUtil.createException(excep);
		}
		logger.endTimer("class: AmzReceiveMCFPackageStatus | method: processEventAndPostToQueue -- Ends");
		logger.info("class: AmzReceiveMCFPackageStatus | method: processEventAndPostToQueue -- Ends");
		return responseDoc;
	}

	private void prepareAndErrorLogResponse(Document inputDoc, String errorCode, String errorDescription) {
		logger.beginTimer("class: AmzReceiveMCFPackageStatus | method: prepareAndErrorLogResponse -- Starts");
		Element eleroot = inputDoc.getDocumentElement();
		eleroot.setAttribute(AmzLiterals.ATTR_HTTP_CODE, errorCode);
		eleroot.setAttribute(AmzLiterals.ATTR_HTTP_MESSAGE, errorDescription);
		AmzCommonUtil.logAmzConnResponse(inputDoc);
		logger.endTimer("class: AmzReceiveMCFPackageStatus | method: prepareAndErrorLogResponse -- Ends");
	}

	private HashMap<String, String> validateInputMsg(Document inDoc) {
		logger.beginTimer("class: AmzReceiveMCFPackageStatus | method: validateInputMsg -- Starts");
		HashMap<String, String> errorMap = new HashMap<>();
		try {
			Element eleEvent = inDoc.getDocumentElement();
			String eventType = eleEvent.getAttribute("eventType");
			String merchantId = eleEvent.getAttribute("merchantId");

			Element eleFulfillmentOrder = SCXmlUtil.getChildElement(eleEvent, "order");
			String orderId = "";
			boolean hasShipment = false;

			if (!YFCCommon.isVoid(eleFulfillmentOrder)) {
				orderId = eleFulfillmentOrder.getAttribute("orderId");
				NodeList shipmentNodes = eleFulfillmentOrder.getElementsByTagName("shipments");
				for (int i = 0; i < shipmentNodes.getLength(); i++) {
					Element eleShipment = (Element) shipmentNodes.item(i);
					if (!YFCCommon.isVoid(eleShipment.getAttribute("amazonShipmentId"))) {
						hasShipment = true;
						break;
					}
				}
			}

			boolean isValidMsg = true;
			String strErrorMsg = "";

			if (YFCCommon.isVoid(eventType)) {
				isValidMsg = false;
				strErrorMsg = "eventType is blank in the input";
			} else if (!EXPECTED_EVENT_TYPE.equals(eventType)) {
				isValidMsg = false;
				strErrorMsg = "Unsupported eventType: " + eventType
						+ ". Only SHIPMENT_PACKAGE_STATUS_CHANGED is accepted.";
			} else if (YFCCommon.isVoid(merchantId)) {
				isValidMsg = false;
				strErrorMsg = "merchantId is blank in the input";
			} else if (YFCCommon.isVoid(eleFulfillmentOrder)) {
				isValidMsg = false;
				strErrorMsg = "fulfillmentOrder element is missing in the input";
			} else if (YFCCommon.isVoid(orderId)) {
				isValidMsg = false;
				strErrorMsg = "orderId is blank in fulfillmentOrder";
			} else if (!hasShipment) {
				isValidMsg = false;
				strErrorMsg = "No Shipment with amazonShipmentId found in the input";
			}

			errorMap.put(AmzLiterals.STR_IS_VLD_MSG, isValidMsg ? "Y" : "N");
			errorMap.put("ErrorMsg", strErrorMsg);

			logger.endTimer("class: AmzReceiveMCFPackageStatus | method: validateInputMsg -- Ends");
		} catch (Exception ex) {
			YFSException yfsException = new YFSException();
			yfsException.setErrorCode("INPUT_ERR_WHILE_VALIDATING");
			yfsException.setErrorDescription("Error While Validating the Input");
			logger.error("Exception in AmzReceiveMCFPackageStatus.validateInputMsg Method: "
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
