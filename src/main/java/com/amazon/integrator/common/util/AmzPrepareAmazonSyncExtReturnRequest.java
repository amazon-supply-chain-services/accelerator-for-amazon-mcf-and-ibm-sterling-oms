package com.amazon.integrator.common.util;

import java.util.List;
import java.util.Properties;

import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Element;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSException;

/*
 * This class will get invoked from AmzConnSyncExternalReturnToAmazonAsync service
 * to prepare a external return create and update jsonobject request to add and update the external return to amazon.
 */
public class AmzPrepareAmazonSyncExtReturnRequest implements YIFCustomApi {
	private Properties props;
	final YFCLogCategory logger = YFCLogCategory.instance(AmzPrepareAmazonSyncExtReturnRequest.class);
	String strReturnOrderId = null;

	/*
	 * This method will prepare and return a returns json request to
	 * AmzCreateExternalReturnInAmazon and AmzUpdateExternalReturnsInAmazon class to
	 * create and update external returns to amazon
	 */
	public JSONObject prepareAmazonSyncExtReturnRequest(Element eleOrder, List<String> amzReturnOrdElgPrimeLineNo,
			String strAmazonOrderId) throws XPathExpressionException {
		logger.beginTimer(
				"class: AmzPrepareAmazonSyncExtReturnRequest | method: prepareAmazonSyncExtReturnRequest -- Starts");
		logger.info(
				"class: AmzPrepareAmazonSyncExtReturnRequest | method: prepareAmazonSyncExtReturnRequest -- Starts");

		JSONObject variable = new JSONObject();

		try {
			String strTaskType = eleOrder.getAttribute(AmzLiterals.A_TASK_TYPE);
			logger.debug("strTaskType is: " + strTaskType);

			JSONObject input = new JSONObject();
			JSONObject orderIdentifier = new JSONObject();
			if (!YFCObject.isVoid(strAmazonOrderId)) {
				orderIdentifier.put(AmzLiterals.A_JS_ORDER_ID, strAmazonOrderId);
			}
			variable.put(AmzLiterals.A_JS_ORDER_IDENTIFIER, orderIdentifier);
			JSONObject returns = new JSONObject();

			JSONArray details = new JSONArray();

			JSONObject detail = new JSONObject();
			// returnLineItems JSONArray to the details JSONObject
			JSONArray returnLineItems = new JSONArray();
			addReturnLineItem(returnLineItems, eleOrder, amzReturnOrdElgPrimeLineNo, strAmazonOrderId);

			if (!YFCObject.isVoid(strTaskType)
					&& AmzCommonConstants.STR_AMZ_CREATE_EXTERNAL_RETURN.equalsIgnoreCase(strTaskType)) {
				// Aliases Array to the details JSONObject
				JSONArray aliases = new JSONArray();
				JSONObject alias = new JSONObject();
				alias.put(AmzLiterals.A_JS_ALIAS_TYPE, AmzCommonConstants.STR_EXTERNAL_RETURN_ID);
				alias.put(AmzLiterals.A_JS_ALIAS_ID, eleOrder.getAttribute(AmzLiterals.A_ORDER_NO));
				aliases.put(alias);
				detail.put(AmzLiterals.A_JS_ALIASES, aliases);

				detail.put(AmzLiterals.A_JS_STATE, AmzCommonConstants.STR_CREATED);
			} else if (!YFCObject.isVoid(strReturnOrderId)) {
				JSONObject returnIdentifier = new JSONObject();
				returnIdentifier.put(AmzLiterals.A_JS_ID, strReturnOrderId);
				detail.put(AmzLiterals.A_JS_RETURN_IDENTIFIER, returnIdentifier);
				if (!YFCObject.isVoid(strTaskType)
						&& AmzCommonConstants.STR_AMZ_COMPLETE_EXTERNAL_RETURN.equalsIgnoreCase(strTaskType)) {
					detail.put(AmzLiterals.A_JS_STATE, AmzCommonConstants.STR_COMPLETED);
				} else if (!YFCObject.isVoid(strTaskType)
						&& AmzCommonConstants.STR_AMZ_CANCEL_EXTERNAL_RETURN.equalsIgnoreCase(strTaskType)) {
					detail.put(AmzLiterals.A_JS_STATE, AmzCommonConstants.STR_CANCELLED);
				}
			}

			detail.put(AmzLiterals.A_JS_RETURN_LINE_ITEMS, returnLineItems);
			details.put(detail);
			returns.put(AmzLiterals.A_JS_DETAILS, details);
			input.put(AmzLiterals.A_JS_RETURNS, returns);
			variable.put(AmzLiterals.A_JS_INPUT, input);

			logger.debug("variable is: " + variable);
		} catch (JSONException e) {
			e.printStackTrace();
			YFSException yfse = new YFSException();
			yfse.setErrorCode("JSON_ERROR_02");
			yfse.setErrorDescription(e.getMessage());
			logger.error("class: AmzPrepareAmazonCreateExtReturnRequest | method: prepareAmazonSyncExtReturnRequest :"
					+ ExceptionUtils.getStackTrace(yfse));
			throw yfse;
		}
		logger.info("class: AmzPrepareAmazonSyncExtReturnRequest | method: prepareAmazonSyncExtReturnRequest -- Ends");
		logger.endTimer(
				"class: AmzPrepareAmazonSyncExtReturnRequest | method: prepareAmazonSyncExtReturnRequest -- Ends");
		return variable;

	}

	/*
	 * This method create a returnLineItem JSONObject for create and update external
	 * returns
	 */
	public void addReturnLineItem(JSONArray returnLineItems, Element eleOrder,
			List<String> amzCreateReturnOrdElgPrimeLineNo, String strAmazonOrderId) throws XPathExpressionException {
		logger.beginTimer("class: AmzPrepareAmazonSyncExtReturnRequest | method: addReturnLineItem -- Starts");
		logger.info("class: AmzPrepareAmazonSyncExtReturnRequest | method: addReturnLineItem -- Starts");
		try {

			int iCreateReturnElgLineLen = amzCreateReturnOrdElgPrimeLineNo.size();
			for (int i = 0; i < iCreateReturnElgLineLen; i++) {
				String sReturnElgPrimeLineNo = amzCreateReturnOrdElgPrimeLineNo.get(i);
				logger.debug("sReturnElgPrimeLineNo is: " + sReturnElgPrimeLineNo);
				Element eleOrderLine = AmzXMLUtil.getXpathElement(eleOrder,
						"OrderLines/OrderLine[@PrimeLineNo='" + sReturnElgPrimeLineNo + "']");
				if (!YFCObject.isVoid(eleOrderLine)) {
					String sExtnAmazonLineItemAlias = AmzXMLUtil.getXpathAttribute(eleOrderLine,
							"DerivedFromOrderLine/Extn/@ExtnAmazonLineItemAlias");
					String sOrderedQty = AmzXMLUtil.getXpathAttribute(eleOrderLine,
							"OrderStatuses/OrderStatus/@TotalQuantity");
					logger.debug("sOrderedQty is: " + sOrderedQty);
					strReturnOrderId = AmzXMLUtil.getXpathAttribute(eleOrderLine, "Extn/@ExtnAmazonReturnOrderId");
					logger.debug("strReturnOrderId is: " + strReturnOrderId);
					String sExtnAmazonOrderId = AmzXMLUtil.getXpathAttribute(eleOrderLine,
							"DerivedFromOrderLine/Extn/@ExtnAmazonOrderId");
					logger.debug("sExtnAmazonOrderId is: " + sExtnAmazonOrderId);
					if (!YFCObject.isVoid(sExtnAmazonOrderId)
							&& sExtnAmazonOrderId.equalsIgnoreCase(strAmazonOrderId)) {
						JSONObject returnLineItem = new JSONObject();
						JSONObject returnFor = new JSONObject();
						JSONArray orderLineItemAmounts = new JSONArray();
						JSONObject orderLineItemAmount = new JSONObject();

						// amount JSON object
						JSONObject amount = new JSONObject();
						amount.put(AmzLiterals.A_JS_VALUE, sOrderedQty);
						orderLineItemAmount.put(AmzLiterals.A_JS_AMOUNT, amount);

						JSONObject lineItemId = new JSONObject();
						lineItemId.put(AmzLiterals.A_JS_LINE_ITEM_ID, sExtnAmazonLineItemAlias);

						orderLineItemAmount.put(AmzLiterals.A_JS_LINE_ITEM_ID, lineItemId);
						orderLineItemAmounts.put(orderLineItemAmount);
						returnFor.put(AmzLiterals.A_JS_ORDER_LINE_ITEM_AMOUNTS, orderLineItemAmounts);
						returnLineItem.put(AmzLiterals.A_JS_RETURN_FOR, returnFor);
						returnLineItems.put(returnLineItem);
						logger.debug("returnLineItem is: " + returnLineItem);
					}
				}
				logger.debug("returnLineItems is: " + returnLineItems);
			}
		} catch (JSONException e) {
			e.printStackTrace();
			YFSException yfse = new YFSException();
			yfse.setErrorCode("JSON_ERROR_02");
			yfse.setErrorDescription(e.getMessage());
			logger.error("class: AmzPrepareAmazonSyncExtReturnRequest | method: addReturnLineItem :"
					+ ExceptionUtils.getStackTrace(yfse));
			throw yfse;
		}
		logger.info("class: AmzPrepareAmazonSyncExtReturnRequest | method: addReturnLineItem -- Ends");
		logger.endTimer("class: AmzPrepareAmazonSyncExtReturnRequest | method: addReturnLineItem -- Ends");
	}

	public void setProperties(Properties inProps) {
		this.props = inProps;
	}

}
