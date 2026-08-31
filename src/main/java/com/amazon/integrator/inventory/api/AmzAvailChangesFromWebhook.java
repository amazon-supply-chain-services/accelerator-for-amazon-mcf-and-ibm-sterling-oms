package com.amazon.integrator.inventory.api;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Properties;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzLiterals;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.interop.japi.YIFClientCreationException;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfc.util.YFCCommon;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/**
 * This class is invoked through a sync service 'AmzConnAvailChangesFromWebhook'
 * which will be invoked by Amazon webhook to send the INVENTORY_CHANGES event's
 * JSON to OMS. This method validates for mandatory attributes in the input, and
 * transform to OMS payload and return it. The returned XML will be dropped to
 * an internal queue to process by an integration server. In case of missing
 * mandatory fields, It will throw error message.
 */

public class AmzAvailChangesFromWebhook implements YIFCustomApi {
	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzAvailChangesFromWebhook.class);
	private Properties props;

	public Document processInputAndPostToQueue(YFSEnvironment env, Document inputDoc) throws RemoteException, YIFClientCreationException {
		logger.beginTimer("class: AmzAvailChangesFromWebhook | method: processInputAndPostToQueue -- Starts");
		logger.info("class: AmzAvailChangesFromWebhook | method: processInputAndPostToQueue -- Starts");
		Document outDoc = null;
		Document responseDoc = null;
		try {
			
			AmzCommonUtil.logAmzConnRequest(inputDoc);
			
			HashMap<String, String> errorMap = validateInputMsg(inputDoc);

			if ("Y".equals(errorMap.get(AmzLiterals.STR_IS_VLD_MSG))) {
				Element eleInputDoc = inputDoc.getDocumentElement();
				outDoc = SCXmlUtil.createDocument("InventoryItems");
				Element eleInventoryItems = outDoc.getDocumentElement();
				eleInventoryItems.setAttribute("EventType", eleInputDoc.getAttribute("eventDescriptor"));
				eleInventoryItems.setAttribute("ShipNode", "");
				eleInventoryItems.setAttribute("DateTime", eleInputDoc.getAttribute("time"));
				eleInventoryItems.setAttribute("IsAvailableQtyExist", "N");
				Element eleItem = SCXmlUtil.createChild(eleInventoryItems, "Item");
				eleItem.setAttribute("ItemID", "");

				Element eleResources = SCXmlUtil.getChildElement(eleInputDoc, "resources");
				String strResources = eleResources.getTextContent();
				String[] splitResources = strResources.split("/");
				String inventoryItemId = splitResources[splitResources.length - 1];
				String businessProductID = splitResources[1];
				eleInventoryItems.setAttribute("BusinessProductID", businessProductID);

				eleItem.setAttribute("InventoryItemId", inventoryItemId);
				eleItem.setAttribute("AvailableQty", "");
				eleItem.setAttribute("IsAvailableQtyExist", "N");
				eleItem.setAttribute("UnitOfMeasure", "");
				eleItem.setAttribute("ProductClass", "");
			
				AmzCommonUtil.callService(env, outDoc, "AmzPostMsgToInternalQueue", null);
				
				responseDoc = SCXmlUtil.createDocument("Response");
				responseDoc.getDocumentElement().setAttribute("status", AmzCommonConstants.STR_HTTP_STATUS_OK);
				responseDoc.getDocumentElement().setAttribute("message", AmzCommonConstants.STR_OK);
				
			} else {
				YFSException yfsException = new YFSException();
				yfsException.setErrorCode("INPUT_ERROR_001");
				yfsException.setErrorDescription(errorMap.get("ErrorMsg"));
				logger.error("Exception in AmzAvailChangesFromWebhook.formatInputJson Method: "
						+ ExceptionUtils.getStackTrace(yfsException));
				throw yfsException;
			}
		} catch (YFSException excep) {
			throw AmzCommonUtil.createException(excep);
		}
		logger.endTimer("class: AmzAvailChangesFromWebhook | method: processInputAndPostToQueue -- Ends");
		logger.info("class: AmzAvailChangesFromWebhook | method: processInputAndPostToQueue -- Ends");
		return responseDoc;
	}

	private HashMap<String, String> validateInputMsg(Document inDoc) {
		logger.beginTimer("class: AmzAvailChangesFromWebhook | method: validateInputMsg -- Starts");
		logger.info("class: AmzAvailChangesFromWebhook | method: validateInputMsg -- Starts");
		HashMap<String, String> errorMap = new HashMap<>();
		try {
		Element inDocEle = inDoc.getDocumentElement();
		Element resourcesEle = SCXmlUtil.getChildElement(inDocEle, "resources");
		String resource = "";
		String businessProductID = "";
		if (!YFCCommon.isVoid(resourcesEle)) {
		resource = resourcesEle.getTextContent();
		String[] splitResources = resource.split("/");
		businessProductID = splitResources[1];
		}
		
		String strEventType = inDocEle.getAttribute("eventDescriptor");
		int startIndex = resource.indexOf("/inventory-item/");
		String inventoryItemId = "";
		if (startIndex != -1) {
        	startIndex += "/inventory-item/".length();
        	inventoryItemId = resource.substring(startIndex);
        }
		
		boolean isValidMsg = true;
		String strErrorMsg = "";

		if (YFCCommon.isVoid(strEventType)) {
			isValidMsg = false;
			strErrorMsg = "Event type is blank";
		} else if (YFCCommon.isVoid(resource)) {
			isValidMsg = false;
			strErrorMsg = "Resource is blank";
		} else if (YFCCommon.isVoid(inventoryItemId)) {
			isValidMsg = false;
			strErrorMsg = "Inventory Item ID is blank";
		}else if (YFCCommon.isVoid(businessProductID)) {
			isValidMsg = false;
			strErrorMsg = "Business product ID is blank";
		}

		if (isValidMsg) {
			errorMap.put(AmzLiterals.STR_IS_VLD_MSG, "Y");
		} else {
			errorMap.put(AmzLiterals.STR_IS_VLD_MSG, "N");
		}

		errorMap.put("ErrorMsg", strErrorMsg);

		logger.info("class: AmzAvailChangesFromWebhook | method: validateInputMsg -- Ends");
		logger.endTimer("class: AmzAvailChangesFromWebhook | method: validateInputMsg -- Ends");
		}
		catch(Exception ex) {
			YFSException yfsException = new YFSException();
			yfsException.setErrorCode("INPUT_ERROR_001");
			yfsException.setErrorDescription("Invalid Input.");
			throw yfsException;
		}
		return errorMap;
	}	

	@Override
	public void setProperties(Properties inProps) {
		this.props = inProps;
	}
}
