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
 * This class receives MCF ORDER_STATUS_CHANGED notifications and posts
 * the event XML as-is to the internal queue for processing by
 * AmzProcessMCFOrderStatusChangeEvent.
 *
 * Input Sample 1 - Processing (no cancellations yet):
 * <AmzProcessMCFOrderStatusChangedEvent eventType="ORDER_STATUS_CHANGED"
 *     fulfillentServiceId="FS01-o8vlfw8b1tiu1" merchantId="merchantId">
 *   <order orderId="Y100003115-01" status="PROCESSING">
 *     <lineItems lineItemId="202603151921549163264">
 *       <product>
 *         <perUnitDeclaredValue amount="14.99" currencyCode="USD"/>
 *         <productIdentifier amazonSku="Deep_bwp_MCF_6"/>
 *       </product>
 *       <amount unit="EACHES" value="2.0"/>
 *       <unfulfillableAmount unit="EACHES" value="0"/>
 *       <cancelledAmount unit="EACHES" value="0"/>
 *     </lineItems>
 *   </order>
 * </AmzProcessMCFOrderStatusChangedEvent>
 *
 * Input Sample 2 - Cancelled (FulfillmentOrder status=COMPLETE):
 * <AmzProcessMCFOrderStatusChangedEvent eventType="ORDER_STATUS_CHANGED"
 *     fulfillentServiceId="FS01-o8vlfw8b1tiu1" merchantId="merchantId">
 *   <order orderId="Y100003115-01" status="COMPLETE">
 *     <lineItems lineItemId="202603151921549163264">
 *       <product>
 *         <perUnitDeclaredValue amount="14.99" currencyCode="USD"/>
 *         <productIdentifier amazonSku="Deep_bwp_MCF_6"/>
 *       </product>
 *       <amount unit="EACHES" value="2.0"/>
 *       <unfulfillableAmount unit="EACHES" value="0"/>
 *       <cancelledAmount unit="EACHES" value="2.0"/>
 *     </lineItems>
 *   </order>
 * </AmzProcessMCFOrderStatusChangedEvent>
 *
 * Input Sample 3 - Partially Fulfilled (FulfillmentOrder status=COMPLETE_PARTIAL):
 * <AmzProcessMCFOrderStatusChangedEvent eventType="ORDER_STATUS_CHANGED"
 *     fulfillentServiceId="FS01-o8vlfw8b1tiu1" merchantId="merchantId">
 *   <order orderId="Y100003115-01" status="COMPLETE_PARTIAL">
 *     <lineItems lineItemId="202603151921549163264">
 *       <product>
 *         <perUnitDeclaredValue amount="14.99" currencyCode="USD"/>
 *         <productIdentifier amazonSku="Deep_bwp_MCF_6"/>
 *       </product>
 *       <amount unit="EACHES" value="2.0"/>
 *       <unfulfillableAmount unit="EACHES" value="1.0"/>
 *       <cancelledAmount unit="EACHES" value="0"/>
 *     </lineItems>
 *   </order>
 * </AmzProcessMCFOrderStatusChangedEvent>
 *
 * Input Sample 4 - Unfulfillable (FulfillmentOrder status=UNFULFILLABLE):
 * <AmzProcessMCFOrderStatusChangedEvent eventType="ORDER_STATUS_CHANGED"
 *     fulfillentServiceId="FS01-o8vlfw8b1tiu1" merchantId="merchantId">
 *   <order orderId="Y100003115-01" status="UNFULFILLABLE">
 *     <lineItems lineItemId="202603151921549163264">
 *       <product>
 *         <perUnitDeclaredValue amount="14.99" currencyCode="USD"/>
 *         <productIdentifier amazonSku="Deep_bwp_MCF_6"/>
 *       </product>
 *       <amount unit="EACHES" value="2.0"/>
 *       <unfulfillableAmount unit="EACHES" value="2.0"/>
 *       <cancelledAmount unit="EACHES" value="0"/>
 *     </lineItems>
 *   </order>
 * </AmzProcessMCFOrderStatusChangedEvent>
 */
public class AmzReceiveMCFOrderStatusUpdates implements YIFCustomApi {
	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzReceiveMCFOrderStatusUpdates.class);
	private Properties props;

	public Document processEventAndPostToQueue(YFSEnvironment env, Document inputDoc)
			throws RemoteException, YIFClientCreationException {
		logger.beginTimer("class: AmzReceiveMCFOrderStatusUpdates | method: processEventAndPostToQueue -- Starts");
		logger.info("class: AmzReceiveMCFOrderStatusUpdates | method: processEventAndPostToQueue -- Starts");
		logger.info("class: AmzReceiveMCFOrderStatusUpdates | method: processEventAndPostToQueue Input Doc is:"
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
				logger.error("Exception in AmzReceiveMCFOrderStatusUpdates.processEventAndPostToQueue Method: "
						+ ExceptionUtils.getStackTrace(yfsException));
				prepareAndErrorLogResponse(inputDoc, yfsException.getErrorCode(), yfsException.getErrorDescription());
				throw yfsException;
			}
		} catch (YFSException excep) {
			throw AmzCommonUtil.createException(excep);
		}
		logger.endTimer("class: AmzReceiveMCFOrderStatusUpdates | method: processEventAndPostToQueue -- Ends");
		logger.info("class: AmzReceiveMCFOrderStatusUpdates | method: processEventAndPostToQueue -- Ends");
		return responseDoc;
	}

	private void prepareAndErrorLogResponse(Document inputDoc, String errorCode, String errorDescription) {
		logger.beginTimer("class: AmzReceiveMCFOrderStatusUpdates | method: prepareAndErrorLogResponse -- Starts");
		Element eleroot = inputDoc.getDocumentElement();
		eleroot.setAttribute(AmzLiterals.ATTR_HTTP_CODE, errorCode);
		eleroot.setAttribute(AmzLiterals.ATTR_HTTP_MESSAGE, errorDescription);
		AmzCommonUtil.logAmzConnResponse(inputDoc);
		logger.endTimer("class: AmzReceiveMCFOrderStatusUpdates | method: prepareAndErrorLogResponse -- Ends");
	}

	private HashMap<String, String> validateInputMsg(Document inDoc) {
		logger.beginTimer("class: AmzReceiveMCFOrderStatusUpdates | method: validateInputMsg -- Starts");
		HashMap<String, String> errorMap = new HashMap<>();
		try {
			Element eleEvent = inDoc.getDocumentElement();
			String eventType = eleEvent.getAttribute("eventType");
			String merchantId = eleEvent.getAttribute("merchantId");

			Element eleFulfillmentOrder = SCXmlUtil.getChildElement(eleEvent, "order");
			String orderId = "";
			boolean hasLineItem = false;

			if (!YFCCommon.isVoid(eleFulfillmentOrder)) {
				orderId = eleFulfillmentOrder.getAttribute("orderId");
				NodeList lineItemNodes = eleFulfillmentOrder.getElementsByTagName("lineItems");
				if (lineItemNodes.getLength() > 0) {
					hasLineItem = true;
				}
			}

			boolean isValidMsg = true;
			String strErrorMsg = "";

			if (YFCCommon.isVoid(eventType)) {
				isValidMsg = false;
				strErrorMsg = "eventType is blank in the input";
			} else if (YFCCommon.isVoid(merchantId)) {
				isValidMsg = false;
				strErrorMsg = "merchantId is blank in the input";
			} else if (YFCCommon.isVoid(eleFulfillmentOrder)) {
				isValidMsg = false;
				strErrorMsg = "fulfillmentOrder element is missing in the input";
			} else if (YFCCommon.isVoid(orderId)) {
				isValidMsg = false;
				strErrorMsg = "orderId is blank in fulfillmentOrder";
			} else if (!hasLineItem) {
				isValidMsg = false;
				strErrorMsg = "No LineItem found in the input";
			}

			errorMap.put(AmzLiterals.STR_IS_VLD_MSG, isValidMsg ? "Y" : "N");
			errorMap.put("ErrorMsg", strErrorMsg);

			logger.endTimer("class: AmzReceiveMCFOrderStatusUpdates | method: validateInputMsg -- Ends");
		} catch (Exception ex) {
			YFSException yfsException = new YFSException();
			yfsException.setErrorCode("INPUT_ERR_WHILE_VALIDATING");
			yfsException.setErrorDescription("Error While Validating the Input");
			logger.error("Exception in AmzReceiveMCFOrderStatusUpdates.validateInputMsg Method: "
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
