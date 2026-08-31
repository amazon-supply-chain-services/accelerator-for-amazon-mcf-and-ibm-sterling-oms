package com.amazon.common.util;
import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.interop.japi.YIFApi;
import com.yantra.interop.japi.YIFClientCreationException;
import com.yantra.interop.japi.YIFClientFactory;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.dom.YFCDocument;
import com.yantra.yfc.dom.YFCElement;
import com.yantra.yfc.dom.YFCNodeList;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCCommon;
import com.yantra.yfc.util.YFCException;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;



/**
 * A helper class providing methods for XML document processing. All methods are static, object of
 * this class cannot be created.
 */

public class AmzCommonUtil {

	  // initialize logger
	  private static final YFCLogCategory logger = YFCLogCategory.instance(AmzCommonUtil.class);

	  private AmzCommonUtil() {

	  }
	  private static YIFApi api;
	  //Call the services
	  
	  public static Document callService(final YFSEnvironment env,
				final Document input, final String serviceName,
				final String templateName) throws YIFClientCreationException,
				YFSException, RemoteException {

			if (templateName != null) {

				env.setApiTemplate(serviceName, templateName);
			}

			YIFApi api = YIFClientFactory.getInstance().getApi();
			Document outDoc = api.executeFlow(env, serviceName, input);
			env.clearApiTemplate(serviceName);
			return outDoc;

		}
	  
	  //call the API
	  
	  public static Document callAPI(final YFSEnvironment env, final Document input,
				final String apiName, final Document templateDoc)
						throws YIFClientCreationException, YFSException, RemoteException {
			Document outputDoc = null;
			if (templateDoc != null) {

				env.setApiTemplate(apiName, templateDoc);
			}

			YIFApi api = YIFClientFactory.getInstance().getApi();
			
			outputDoc = api.invoke(env, apiName, input);
			
			env.clearApiTemplate(apiName);
			
			return outputDoc;
		}
	  
	  public static Document invoke(YFSEnvironment env, String apiName, Document inDoc)  {

			Document returnDoc=null;
			try {
				api = YIFClientFactory.getInstance().getApi();
				returnDoc = api.invoke(env, apiName, inDoc);
			} catch (RemoteException e) {
				logger.error(e);
				throw new YFCException(e);
			} catch (YIFClientCreationException e) {
				logger.error(e);
				throw new YFCException(e);
			}
			return returnDoc;
		}
	  
	  
	  
	  
	  public static Document invokeAPI(YFSEnvironment env, Document template,
				String apiName, Document inDoc)  {

			env.setApiTemplate(apiName, template);
			Document returnDoc = AmzCommonUtil.invoke(env, apiName, inDoc);
			env.clearApiTemplate(apiName);
			return returnDoc;
		}
	  
	  public static Document invokeAPI(YFSEnvironment env, String templateName,
				String apiName, Document inDoc)  {

			env.setApiTemplate(apiName, templateName);
			Document returnDoc = AmzCommonUtil.invoke(env, apiName, inDoc);
			env.clearApiTemplate(apiName);

			return returnDoc;
		}
	  
	  public static Document invokeService(YFSEnvironment env,
				String serviceName, Document inDoc)  {
			return AmzCommonUtil.executeFlow(env, serviceName, inDoc);
	}
	 
	
	  public static Document executeFlow(YFSEnvironment env,
				String serviceName, Document inDoc)  {

			Document returnDoc=null;
			try {
				api = YIFClientFactory.getInstance().getApi();
				returnDoc= api.executeFlow(env, serviceName, inDoc);
			} catch (YFSException e) {
				logger.error(e);
				throw new YFCException(e);
			} catch (RemoteException e) {
				logger.error(e);
				throw new YFCException(e);
			} catch (YIFClientCreationException e) {
				logger.error(e);
				throw new YFCException(e);
			}
			return returnDoc;
		}
	  
	  public static YFSException createException(YFSException ex) {
		  
			YFSException yfsException = new YFSException();
			yfsException.setErrorCode(ex.getErrorCode());
			yfsException.setErrorDescription(ex.getErrorDescription());
			logger.error(ExceptionUtils.getStackTrace(ex));
			return yfsException;
			
		}
	  
		/**
		 * This method fetch the country or node of the order.
		 * 
		 * 
		 * @param env
		 * @param string
		 * @return string
		 */
		public static String getShipNode(YFSEnvironment env, String extnOrderCountryCode, String enterpriseCode) throws Exception {

			
		logger.beginTimer("AmzBeforeCreateOrderUE:getShipNode:start");
			

			String shipNode = "";
			Document commonCodeInput = SCXmlUtil.createDocument(AmzLiterals.E_COMMON_CODE);
			commonCodeInput.getDocumentElement().setAttribute(AmzLiterals.A_CODE_TYPE, AmzCommonConstants.STR_AMZCONN_OMS_SHIPNOD);

			Document apiOutput = AmzCommonUtil.callAPI(env, commonCodeInput, AmzCommonConstants.GET_COMMON_CODE_LIST, null);

			YFCElement commonCodeElement = YFCDocument.getDocumentFor(apiOutput).getDocumentElement();
			YFCNodeList<YFCElement> commonCodeList = commonCodeElement.getElementsByTagName(AmzLiterals.E_COMMON_CODE);
			for (YFCElement tempElement : commonCodeList) {
				String codeValue = tempElement.getAttribute(AmzLiterals.A_CODE_VALUE);
				String codeLongDescription = tempElement.getAttribute(AmzLiterals.A_CODE_LONG_DESCRIPTION);
				if (extnOrderCountryCode.equals(codeValue) && enterpriseCode.equals(codeLongDescription)) {
					shipNode = tempElement.getAttribute(AmzLiterals.A_CODE_SHORT_DESCRIPTION);
				}
			}

			logger.endTimer("AmzBeforeCreateOrderUE:getShipNode:end");
			

			return shipNode;
		}
		
		/*
		 * This method to check response message, if contain error then throw exception
		 */
		public static void validateResponseMessage(String output) throws JSONException {
			logger.timer("class: AmzCommonUtil | method: validateResponseMessage -- Starts");
			logger.info("class: AmzCommonUtil | method: validateResponseMessage -- Starts");
			JSONObject outputJson = new JSONObject(output);
			 if (outputJson.has("errors")) {
				 JSONArray errors = outputJson.getJSONArray("errors");
				 if (errors.length() > 0) {
				JSONObject error = errors.getJSONObject(0); 
				 String strErrorMessage = error.optString("message", "Unknown error occurred");
				 JSONObject extensions = error.optJSONObject("extensions");
				 String strErrorCode = null;
		         String strErrorType = null;
		         if (extensions != null && extensions.has("classification")) {
		                JSONObject classification = extensions.getJSONObject("classification");
		                strErrorCode = classification.optString("errorCode", "Unknown code");
		                strErrorType = classification.optString("errorType", "Unknown error type");
		            }
		         logger.debug("class: AmzCommonUtil | method: validateResponseMessage | errorMessage ::" +strErrorMessage+'\n');
		         logger.debug("class: AmzCommonUtil | method: validateResponseMessage | errorCode ::" +strErrorCode+'\n');
		         logger.debug("class: AmzCommonUtil | method: validateResponseMessage | errorType ::" +strErrorType+'\n');
		        
		         throwCustomExecption(strErrorMessage,strErrorCode,strErrorType);
		         logger.info("class: AmzCommonUtil | method: validateResponseMessage -- Ends");
			 	 logger.timer("class: AmzCommonUtil | method: validateResponseMessage -- Ends");
				
		        	 }
				 }
			
		}
		
		/**
		 * This method is used to throw the custom exception
		 * 
		 * @param strErrorMessage
		 * @param strErrorCode
		 * @param strErrorType
		 * @return
		 * @throws Exception
		 */
		public static Document throwCustomExecption(String strErrorMessage, String strErrorCode, String strErrorType) {
			logger.timer("class: AmzCommonUtil | method: throwCustomExecption -- Starts");
			logger.info("class: AmzCommonUtil | method: throwCustomExecption -- Starts");
			Document outDocError = null;
			try {
				outDocError = SCXmlUtil.createDocument("Errors");
				Element eleError = SCXmlUtil.createChild(outDocError.getDocumentElement(), "Error");
				eleError.setAttribute("ErrorCode", strErrorCode);
				eleError.setAttribute("ErrorDescription", strErrorMessage);
				eleError.setAttribute("ErrorType", strErrorType);
				logger.error("Error document for YFS Exception::\n" + SCXmlUtil.getString(outDocError));
			} catch (Exception exp) {
				logger.error("Error document for YFS Exception::\n" + exp);
			}		
			String strErrorXML = SCXmlUtil.getString(outDocError);
			YFSException customYFSException = new YFSException(strErrorXML);
			logger.info("class: AmzCommonUtil | method: throwCustomExecption -- Ends");
			logger.timer("class: AmzCommonUtil | method: throwCustomExecption -- Ends");
			throw customYFSException;
		}
		
		
			 		
		/**
		 * @param output
		 * @return
		 * @throws JSONException
		 * Method to get error code and details	
		 */
		public static HashMap<String, String> getErrorCodeAndDetails(String output) {
		logger.timer("class: AmzCommonUtil | method: getErrorCodeAndDetails -- Starts");
		logger.info("class: AmzCommonUtil | method: getErrorCodeAndDetails -- Starts");
		// Create a new HashMap to store the error code and details
		HashMap<String, String> stringMap = new HashMap<String, String>();

		// Parse the JSON string into a JSONObject
		JSONObject jsonObject;
		try {
			jsonObject = new JSONObject(output);
			JSONArray errors = jsonObject.getJSONArray("errors");

			// Loop through the errors array to extract values
			for (int i = 0; i < errors.length(); i++) {
				JSONObject error = errors.getJSONObject(i);

				// Extract message
				String strMessage = error.getString("message");

				// Extract errorType and errorCode from the extensions -> classification
				JSONObject extensions = error.getJSONObject("extensions");
				JSONObject classification = extensions.getJSONObject("classification");
				String strErrorType = classification.getString("errorType");
				logger.debug("strErrorType is: "+strErrorType);
				int intErrorCode = classification.getInt("errorCode");
				logger.debug("intErrorCode is: "+intErrorCode);
				String strCode = null;
				if (!YFCObject.isVoid(intErrorCode)) {
					String strErrorCode = Integer.toString(intErrorCode);
					logger.debug("strErrorCode is: "+strErrorCode);

					if (strErrorCode.startsWith("5")) {
						YFSException ex = new YFSException();
						ex.setErrorCode("ErrorCode: " + strErrorCode + " strErrorType: " + strErrorType);
						ex.setErrorDescription(
								"Exception in class: AmzCommonUtil | method: getErrorCodeAndDetails : Message is: "
										+ strMessage);
						throw ex;
					} else if(classification.has("code")){
						strCode = classification.getString("code");
						logger.debug("strCode is: "+strCode);
					}
				}

				// Add extracted values to the HashMap
				stringMap.put("ErrorMessage " + (i + 1), strMessage); // Key: ErrorMessage 1, ErrorMessage 2, ...
				stringMap.put("ErrorType " + (i + 1), strErrorType); // Key: ErrorType 1, ErrorType 2, ...
				stringMap.put("ErrorCode " + (i + 1), String.valueOf(intErrorCode)); // Key: ErrorCode 1, ErrorCode 2,
				if (!YFCObject.isVoid(strCode)) {
					stringMap.put("Code " + (i + 1), strCode);
				}
			}
		} catch (JSONException e) {
			e.printStackTrace();
			YFSException ex = new YFSException();
			ex.setErrorCode("JSON_ERROR_FROM_GET_ERRORCODE_AND_DETAILS");
			ex.setErrorDescription(
					"Exception in class: AmzCommonUtil | method: getErrorCodeAndDetails : " + e.getMessage());
			logger.error("Exception in class: AmzCommonUtil | method: getErrorCodeAndDetails : "
					+ ExceptionUtils.getStackTrace(ex));
			throw ex;

		}

		// Get the "errors" array

		logger.timer("class: AmzCommonUtil | method: getErrorCodeAndDetails -- Ends");
		logger.info("class: AmzCommonUtil | method: getErrorCodeAndDetails -- Ends");
		return stringMap;
	}
		
		public static StringBuilder logAmzConnRequest(Document logInput) {
		
			logger.beginTimer("class: AmzCommonUtil | method: logAmzConnRequest -- Starts");

			String sAction = "REQUEST";
			Element eleLogInput = logInput.getDocumentElement();
			
			String eventType = eleLogInput.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_DESC);
			String eventId = eleLogInput.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_ID);
			String idempotencyKey = eleLogInput.getAttribute(AmzLiterals.ATTR_AMAZON_IDEMPOTENCY_KEY);
			String omsOrderNo = eleLogInput.getAttribute(AmzLiterals.ATTR_OMS_ORDER_NO);
			String enterpriseCode = eleLogInput.getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE);
			String primeEligible = eleLogInput.getAttribute(AmzLiterals.A_PRIME_ELIGIBLE);
			String fulfillableByAmazon = eleLogInput.getAttribute(AmzLiterals.A_FULFILLABLE_BY_AMAZON);
			String invokedEventType = eleLogInput.getAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE);
			String subscriptionId = eleLogInput.getAttribute(AmzLiterals.ATTR_AMAZON_SUBSCRIPTION_ID);
			Element resourcesEle = SCXmlUtil.getChildElement(logInput.getDocumentElement(), AmzLiterals.ATTR_AMAZON_RESOURCES);
			String resources = "";
			if (!YFCCommon.isVoid(resourcesEle)) {
			resources = resourcesEle.getTextContent();
			}

			Date currentDate = new Date();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");
			sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
			
			String eventTime = sdf.format(currentDate);
			
			StringBuilder sBuilder = new StringBuilder(AmzCommonConstants.PIPE);
			
			if (!YFCObject.isVoid(eventType)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_AMAZON_EVENT_TYPE, eventType);
			}
			if (!YFCObject.isVoid(sAction)) {
				appendEntity(sBuilder, AmzLiterals.A_ACTION, sAction);
			}			
			if (!YFCObject.isVoid(eventTime)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_EVENT_TIME, eventTime);
			}			
			if (!YFCObject.isVoid(eventId)) {
				appendEntity(sBuilder, "EventID", eventId);
			}			
			if (!YFCObject.isVoid(eventId)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_AMAZON_EVENT_ID, eventId);
			}
			if (!YFCObject.isVoid(idempotencyKey)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_AMAZON_IDEMPOTENCY_KEY, idempotencyKey);
			}
			if (!YFCObject.isVoid(resources)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_AMAZON_RESOURCES, resources);
			}
			if (!YFCObject.isVoid(omsOrderNo)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_OMS_ORDER_NO, omsOrderNo);
			}
			if (!YFCObject.isVoid(enterpriseCode)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_ENTERPRISE_CODE, enterpriseCode);
			}
			if (!YFCObject.isVoid(fulfillableByAmazon)) {
				appendEntity(sBuilder, AmzLiterals.A_FULFILLABLE_BY_AMAZON, fulfillableByAmazon);
			}
			if (!YFCObject.isVoid(primeEligible)) {
				appendEntity(sBuilder, AmzLiterals.A_PRIME_ELIGIBLE, primeEligible);
			}
			if (!YFCObject.isVoid(invokedEventType)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_INVOKED_EVENT_TYPE, invokedEventType);
			}
			if (!YFCObject.isVoid(subscriptionId)) {
			appendEntity(sBuilder, AmzLiterals.ATTR_AMAZON_SUBSCRIPTION_ID, subscriptionId);
			}

			logger.info(sBuilder.toString());
			logger.endTimer("class: AmzCommonUtil | method: logAmzConnRequest -- Ends");
			return sBuilder;
		}
		
		public static StringBuilder logAmzConnResponse(Document logInput ) {
			
			logger.beginTimer("class: AmzCommonUtil | method: logAmzConnResponse -- Starts");

			
			Element eleLogInput = logInput.getDocumentElement();
			
			String eventType = eleLogInput.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE);
			String amazonOrderId = eleLogInput.getAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID);
			String enterpriseCode = eleLogInput.getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE);
			String deliveryId = eleLogInput.getAttribute(AmzLiterals.ATTR_DELIVERY_ID);
			String omsOrderNo =  eleLogInput.getAttribute(AmzLiterals.ATTR_OMS_ORDER_NO);
			String omsShipmentNo = eleLogInput.getAttribute(AmzLiterals.ATTR_OMS_SHIPMENT_NO);
			String omsContainerNo = eleLogInput.getAttribute(AmzLiterals.ATTR_OMS_CONTAINER_NO);
			String processStatus = eleLogInput.getAttribute(AmzLiterals.ATTR_PROCESS_STATUS);
			String errorMsg = eleLogInput.getAttribute(AmzLiterals.STR_ERROR_MSG);
			String msg = eleLogInput.getAttribute(AmzLiterals.STR_MESSAGE);
			String sAction =  eleLogInput.getAttribute(AmzLiterals.A_ACTION);
			String invokedEventType =  eleLogInput.getAttribute(AmzLiterals.ATTR_INVOKED_EVENT_TYPE);
			String event =  eleLogInput.getAttribute(AmzLiterals.ATTR_EVENT);
			String httpCode =  eleLogInput.getAttribute(AmzLiterals.ATTR_HTTP_CODE);
			String httpMsg =  eleLogInput.getAttribute(AmzLiterals.ATTR_HTTP_MESSAGE);
			String trackingNo = eleLogInput.getAttribute(AmzLiterals.ATTR_TRACKING_NO);
			String marketPlaceID =  eleLogInput.getAttribute(AmzLiterals.AMZ_ATTRIBUTE_MARKETPLACE_ID);
			String primeEligible = eleLogInput.getAttribute(AmzLiterals.A_PRIME_ELIGIBLE);
			String fulfillableByAmazon = eleLogInput.getAttribute(AmzLiterals.A_FULFILLABLE_BY_AMAZON);
			String amazonReturnOrderId = eleLogInput.getAttribute(AmzLiterals.A_AMAZON_RETURN_ORDER_ID);
			String amazonRefundId = eleLogInput.getAttribute(AmzLiterals.A_AMAZON_REFUND_ID);
			
			String invoiceNo=eleLogInput.getAttribute(AmzLiterals.A_INVOICE_NO);
			String invoiceType=eleLogInput.getAttribute(AmzLiterals.A_INVOICE_TYPE);
			String orderheaderKey=eleLogInput.getAttribute(AmzLiterals.A_ORDER_HEADER_KEY);
			String orderInvoiceKey=eleLogInput.getAttribute(AmzLiterals.A_ORDER_INVOICE_KEY);
			String dateInvoiced=eleLogInput.getAttribute(AmzLiterals.A_DATE_INVOICED);
			String receiptNo=eleLogInput.getAttribute(AmzLiterals.A_RECEIPT_NO);
			String receiptHeaderKey=eleLogInput.getAttribute(AmzLiterals.A_RECEIPT_HEADER_KEY);
			String shipmentKey=eleLogInput.getAttribute(AmzLiterals.ATTR_SHIPMENT_KEY);
			String sellerorgCode=eleLogInput.getAttribute(AmzLiterals.ATTR_SELLER_ORG_CODE);
			String eventDesc = eleLogInput.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_DESC);
			String eventId = eleLogInput.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_ID);
			String idempotencyKey = eleLogInput.getAttribute(AmzLiterals.ATTR_AMAZON_IDEMPOTENCY_KEY);
			String subscriptionId = eleLogInput.getAttribute(AmzLiterals.ATTR_AMAZON_SUBSCRIPTION_ID);
			Element resourcesEle = SCXmlUtil.getChildElement(logInput.getDocumentElement(),
					AmzLiterals.ATTR_AMAZON_RESOURCES);
			String resources = "";
			if (!YFCCommon.isVoid(resourcesEle)) {
				resources = resourcesEle.getTextContent();
			}
			
			String sellerSku = eleLogInput.getAttribute(AmzLiterals.A_SELLER_SKU);
			
			Date currentDate = new Date();
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");
			sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
			
			String eventTime = sdf.format(currentDate);
			
			StringBuilder sBuilder = new StringBuilder(AmzCommonConstants.PIPE);
			
			if (!YFCObject.isVoid(eventType)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_AMAZON_EVENT_TYPE, eventType);
			}
			if (!YFCObject.isVoid(enterpriseCode)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_ENTERPRISE_CODE, enterpriseCode);
			}
			if (!YFCObject.isVoid(sAction)) {
				appendEntity(sBuilder, AmzLiterals.A_ACTION, sAction);
			}
			if (!YFCObject.isVoid(eventTime)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_EVENT_TIME, eventTime);
			}
			if (!YFCObject.isVoid(omsOrderNo)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_OMS_ORDER_NO, omsOrderNo);
			}
			if (!YFCObject.isVoid(omsContainerNo)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_OMS_CONTAINER_NO, omsContainerNo);
			}
			if (!YFCObject.isVoid(omsShipmentNo)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_OMS_SHIPMENT_NO, omsShipmentNo);
			}
			if (!YFCObject.isVoid(amazonOrderId)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_AMAZON_ORDER_ID, amazonOrderId);
			}
			if (!YFCObject.isVoid(deliveryId)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_DELIVERY_ID, deliveryId);
			}
			if (!YFCObject.isVoid(processStatus)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_PROCESS_STATUS, processStatus);
			}if (!YFCObject.isVoid(trackingNo)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_TRACKING_NO, trackingNo);
			}
			if(!YFCObject.isVoid(msg)) {
				appendEntity(sBuilder, AmzLiterals.STR_MESSAGE, msg);
			}
			if(!YFCObject.isVoid(errorMsg)) {
				appendEntity(sBuilder, AmzLiterals.STR_ERROR_MSG, errorMsg);
			}
			if(!YFCObject.isVoid(invokedEventType)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_INVOKED_EVENT_TYPE, invokedEventType);
			}
			if(!YFCObject.isVoid(event)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_EVENT, event);
			}
			if(!YFCObject.isVoid(marketPlaceID)) {
				appendEntity(sBuilder, AmzLiterals.AMZ_ATTRIBUTE_MARKETPLACE_ID, marketPlaceID);
			}
			if (!YFCObject.isVoid(fulfillableByAmazon)) {
				appendEntity(sBuilder, AmzLiterals.A_FULFILLABLE_BY_AMAZON, fulfillableByAmazon);
			}
			if (!YFCObject.isVoid(primeEligible)) {
				appendEntity(sBuilder, AmzLiterals.A_PRIME_ELIGIBLE, primeEligible);
			}
			if (!YFCObject.isVoid(amazonReturnOrderId)) {
				appendEntity(sBuilder, AmzLiterals.A_AMAZON_RETURN_ORDER_ID, amazonReturnOrderId);
			}
			if (!YFCObject.isVoid(amazonRefundId)) {
				appendEntity(sBuilder, AmzLiterals.A_AMAZON_REFUND_ID, amazonRefundId);
			}
			if(!YFCObject.isVoid(httpCode)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_HTTP_CODE, httpCode);
			}
			if(!YFCObject.isVoid(httpMsg)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_HTTP_MESSAGE, httpMsg);
			}
			if (!YFCObject.isVoid(eventDesc)) {
			appendEntity(sBuilder, AmzLiterals.ATTR_AMAZON_EVENT_DESC, eventDesc);
			}
			if (!YFCObject.isVoid(idempotencyKey)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_AMAZON_IDEMPOTENCY_KEY, idempotencyKey);
			}
			if (!YFCObject.isVoid(resources)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_AMAZON_RESOURCES, resources);
			}
			if (!YFCObject.isVoid(subscriptionId)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_AMAZON_SUBSCRIPTION_ID, subscriptionId);
			}
			if (!YFCObject.isVoid(eventId)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_AMAZON_EVENT_ID, eventId);
			}
			if (!YFCObject.isVoid(invoiceNo)) {
				appendEntity(sBuilder, AmzLiterals.A_INVOICE_NO, invoiceNo);
			}
			if (!YFCObject.isVoid(invoiceType)) {
				appendEntity(sBuilder, AmzLiterals.A_INVOICE_TYPE, invoiceType);
			}
			if (!YFCObject.isVoid(orderheaderKey)) {
				appendEntity(sBuilder, AmzLiterals.A_ORDER_HEADER_KEY, orderheaderKey);
			}
			if (!YFCObject.isVoid(orderInvoiceKey)) {
				appendEntity(sBuilder, AmzLiterals.A_ORDER_INVOICE_KEY, orderInvoiceKey);
			}
			if (!YFCObject.isVoid(dateInvoiced)) {
				appendEntity(sBuilder, AmzLiterals.A_DATE_INVOICED, dateInvoiced);
			}
			if (!YFCObject.isVoid(receiptNo)) {
				appendEntity(sBuilder, AmzLiterals.A_RECEIPT_NO, receiptNo);
			}
			if (!YFCObject.isVoid(receiptHeaderKey)) {
				appendEntity(sBuilder, AmzLiterals.A_RECEIPT_HEADER_KEY, receiptHeaderKey);
			}
			if (!YFCObject.isVoid(shipmentKey)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_SHIPMENT_KEY, shipmentKey);
			}
			if (!YFCObject.isVoid(sellerorgCode)) {
				appendEntity(sBuilder, AmzLiterals.ATTR_SELLER_ORG_CODE, sellerorgCode);
			}

			logger.info(sBuilder.toString());
			logger.endTimer("class: AmzCommonUtil | method: logAmzConnResponse -- Ends");
			return sBuilder;
		}
		
		public static void appendEntity(StringBuilder sBuilder, String name, String value) {
	        if (sBuilder == null) {
	            sBuilder = new StringBuilder();
	        }
	        sBuilder.append(name).append(AmzCommonConstants.EQUAL).append(value).append(AmzCommonConstants.PIPE);

	    }
		
		/**
		 * This method is to validate condition
		 * 
		 * @param env
		 * @param enterpriseCode
		 * @return
		 * @throws YIFClientCreationException 
		 * @throws RemoteException 
		 * @throws YFSException 
		 * @throws Exception
		 */
		public static boolean invokeCondition(YFSEnvironment env, String enterpriseCode, String strConditionId,
			String processType) throws YFSException, RemoteException, YIFClientCreationException {
		boolean status = false;
		logger.beginTimer("class: AmzCommonUtil | method: invokeCondition -- Starts");
		logger.info("class: AmzCommonUtil | method: invokeCondition -- Starts");
		Document conditionInDoc = AmzXMLUtil.createDocument(AmzLiterals.E_CONDITION);
		Element eleCondition = conditionInDoc.getDocumentElement();
		eleCondition.setAttribute(AmzLiterals.A_CONDITION_ID, strConditionId);
		eleCondition.setAttribute(AmzLiterals.A_ORGANIZATION_CODE, AmzCommonConstants.STR_DEFAULT);
		eleCondition.setAttribute(AmzLiterals.A_PROCESS_TYPE_KEY, processType);
		Element eleInput = AmzXMLUtil.createChild(eleCondition, AmzLiterals.E_INPUT);
		Element eleOrder = AmzXMLUtil.createChild(eleInput, AmzLiterals.E_ORDER);
		eleOrder.setAttribute(AmzLiterals.A_ENTERPRISE_CODE, enterpriseCode);
		logger.debug("Input Document for evaluateCondition is: " + AmzXMLUtil.getString(conditionInDoc));
		Document conditionOut = AmzCommonUtil.callAPI(env, conditionInDoc, AmzCommonConstants.API_EVALUATE_CONDITION,
				null);
		logger.debug(
				"conditionOut after invoking isFulfillmentInitializationOn:  " + AmzXMLUtil.getString(conditionOut));
		if (!YFCObject.isVoid(conditionOut)) {
			Element eleOutput = AmzXMLUtil.getChildElement(conditionOut.getDocumentElement(), "Output");
			if (!YFCObject.isVoid(eleOutput)) {
				logger.debug("EleOutput not null");
				String value = eleOutput.getAttribute("Value");
				if ("true".equals(value)) {
					status = true;
				}
			}
		}
		logger.debug("status is: " + status);
		logger.info("class: AmzCommonUtil | method: invokeCondition -- End");
		logger.endTimer("class: AmzCommonUtil | method: invokeCondition -- End");

		return status;
	}
		
}
