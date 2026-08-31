package com.amazon.integrator.common.util;

import java.util.Map;
import java.util.Properties;

import javax.xml.xpath.XPathExpressionException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;

/*
 * This class will get invoked from AmzConnSyncExternalReturnToAmazonAsync service
 * to prepare a refund json request to add the external refund to amazon.
 */
public class AmzPrepareAmazonExternalRefundRequest implements YIFCustomApi {

	private Properties props;
	final YFCLogCategory logger = YFCLogCategory.instance(AmzPrepareAmazonExternalRefundRequest.class);
	Map<String, String> mapGenericProps = null;
	double sTotalrefundAmount = 0;

	/*
	 * This method will prepare and returns a refund request to
	 * AmzAddExternalReturnRefundInAmazon class add external refund to amazon
	 */
	public JSONObject prepareAmazonExternalRefudReq(YFSEnvironment env, Document indoc, String strAmazonOrderId)
			throws JSONException, XPathExpressionException {
		logger.beginTimer(
				"class: AmzPrepareAmazonExternalRefundRequest | method: prepareAmazonExternalRefudReq -- Starts");
		logger.info("class: AmzPrepareAmazonExternalRefundRequest | method: prepareAmazonExternalRefudReq -- Starts");

		String strEnterpriseCode = AmzXMLUtil.getXpathAttribute(indoc.getDocumentElement(),
				"OrderInvoiceDetail/InvoiceHeader/Order/@EnterpriseCode");
		logger.debug("strEnterpriseCode is: " + strEnterpriseCode);
		Document inDocGetGenrcProperty = AmzXMLUtil.createDocument(AmzLiterals.E_PROPERTIES);
		inDocGetGenrcProperty.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriseCode);
		mapGenericProps = AmzGetGenericProperty.getGenericProperties(env, inDocGetGenrcProperty);

		JSONObject variable = new JSONObject();
		JSONObject input = new JSONObject();
		JSONObject refunds = new JSONObject();
		JSONArray details = new JSONArray();
		JSONObject detail = new JSONObject();

		addAliasesToDetailsJSONObject(detail, indoc);

		addrefundForToDetailJSONObject(detail, indoc, strAmazonOrderId);

		addpaymentDetailsToDetailJSONObject(detail, indoc);
		addRefundTotalsToDetailJSONObject(detail, indoc);
		detail.put(AmzLiterals.A_JS_STATE, mapGenericProps.get(AmzCommonConstants.PROPS_EXT_REFUND_PAYMENT_STATE));
		details.put(detail);
		refunds.put(AmzLiterals.A_JS_DETAILS, details);
		input.put(AmzLiterals.A_JS_REFUNDS, refunds);
		variable.put(AmzLiterals.A_JS_INPUT, input);
		JSONObject orderIdentifier = new JSONObject();
		orderIdentifier.put(AmzLiterals.A_JS_ORDER_ID, strAmazonOrderId);
		variable.put(AmzLiterals.A_JS_ORDER_IDENTIFIER, orderIdentifier);
		logger.debug("variable is: " + variable);
		logger.info("class: AmzPrepareAmazonExternalRefundRequest | method: prepareAmazonExternalRefudReq -- Ends");
		logger.endTimer("class: AmzPrepareAmazonExternalRefundRequest | method: prepareAmazonExternalRefudReq -- Ends");
		return variable;

	}

	/*
	 * This method will create refundFor JSONObject and add refundFor JSONObject to
	 * detail JSONObject.
	 */
	private void addrefundForToDetailJSONObject(JSONObject detail, Document indoc, String strAmazonOrderId)
			throws JSONException {
		logger.beginTimer(
				"class: AmzPrepareAmazonExternalRefundRequest | method: addrefundForToDetailJSONObject -- Starts");
		logger.info("class: AmzPrepareAmazonExternalRefundRequest | method: addrefundForToDetailJSONObject -- Starts");

		JSONObject refundFor = new JSONObject();
		JSONArray orderLineItems = new JSONArray();

		Element eleOrdInvDetList = indoc.getDocumentElement();
		NodeList nOrdInvDetails = eleOrdInvDetList.getElementsByTagName(AmzLiterals.E_ORDER_INVOICE_DETAIL);
		int iOrdInvDetLen = nOrdInvDetails.getLength();
		for (int i = 0; i < iOrdInvDetLen; i++) {
			Element eleOrderInvoiceDet = (Element) nOrdInvDetails.item(i);
			Element eleOrderLine = AmzXMLUtil.getChildElement(eleOrderInvoiceDet, AmzLiterals.E_ORDER_LINE);
			String sExtnIsAmazonInitReturn = SCXmlUtil.getXpathAttribute(eleOrderLine, "Extn/@ExtnIsAmazonInitReturn");
			logger.debug("sExtnIsAmazonInitReturn is: " + sExtnIsAmazonInitReturn);

			Element eleDrivedFromOrdLineExtn = SCXmlUtil.getXpathElement(eleOrderLine, "DerivedFromOrderLine/Extn");
			logger.debug("eleDrivedFromOrdLineExtn is: " + SCXmlUtil.getString(eleDrivedFromOrdLineExtn));
			if (!YFCObject.isVoid(eleDrivedFromOrdLineExtn)) {
				String strExtnIsPrimeElg = eleDrivedFromOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_IS_PRIME_ELIGIBLE);
				logger.debug("strExtnIsPrimeElg is: " + strExtnIsPrimeElg);
				String strExtnAmazonOrderId = eleDrivedFromOrdLineExtn.getAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID);
				logger.debug("strExtnAmazonOrderId is: " + strExtnAmazonOrderId);
				String strExtnAmzLineItemAlias = eleDrivedFromOrdLineExtn
						.getAttribute(AmzLiterals.A_EXTN_AMZ_LINE_ITEM_ALIAS);
				logger.debug("strExtnAmzLineItemAlias is: " + strExtnAmzLineItemAlias);
				if (!YFCObject.isVoid(sExtnIsAmazonInitReturn) && !YFCObject.isVoid(strExtnIsPrimeElg)
						&& !YFCObject.isVoid(strExtnAmazonOrderId)
						&& strExtnAmazonOrderId.equalsIgnoreCase(strAmazonOrderId)
						&& AmzCommonConstants.STR_VAL_Y.equalsIgnoreCase(strExtnIsPrimeElg)
						&& AmzCommonConstants.STR_VAL_N.equalsIgnoreCase(sExtnIsAmazonInitReturn)) {
					JSONObject orderLineItem = new JSONObject();
					JSONObject lineItemId = new JSONObject();
					lineItemId.put(AmzLiterals.A_JS_LINE_ITEM_ID, strExtnAmzLineItemAlias);
					orderLineItem.put(AmzLiterals.A_JS_LINE_ITEM_ID, lineItemId);
					String squantity = eleOrderInvoiceDet.getAttribute(AmzLiterals.A_QUANTITY);
					logger.debug("squantity is: " + squantity);
					JSONObject refundedQuantity = new JSONObject();
					refundedQuantity.put(AmzLiterals.A_JS_AMOUNT, squantity);
					orderLineItem.put(AmzLiterals.A_JS_REFUNDED_QUANTITY, refundedQuantity);
					orderLineItems.put(orderLineItem);
					String strLineTotal = eleOrderInvoiceDet.getAttribute(AmzLiterals.A_LINE_TOTAL);
					logger.debug("strLineTotal is: " + strLineTotal);

					if (!YFCObject.isVoid(strLineTotal)) {
						double dLineTotal = -1 * Double.parseDouble(strLineTotal);
						sTotalrefundAmount = sTotalrefundAmount + dLineTotal;

					}
				}
			}

		}

		refundFor.put("orderLineItems", orderLineItems);
		detail.put("refundFor", refundFor);
		logger.info("class: AmzPrepareAmazonExternalRefundRequest | method: addrefundForToDetailJSONObject -- Ends");
		logger.endTimer(
				"class: AmzPrepareAmazonExternalRefundRequest | method: addrefundForToDetailJSONObject -- Ends");
	}

	/*
	 * This method create a paymentDetails JSONObject, and append the paymentDetails
	 * to the details JONSObject
	 */
	private void addpaymentDetailsToDetailJSONObject(JSONObject detail, Document indoc) throws JSONException {
		logger.beginTimer(
				"class: AmzPrepareAmazonExternalRefundRequest | method: addpaymentDetailsToDetailJSONObject -- Starts");
		logger.info(
				"class: AmzPrepareAmazonExternalRefundRequest | method: addpaymentDetailsToDetailJSONObject -- Starts");
		// Add PaymentDetails JSONArray to the details object
		JSONArray paymentDetails = new JSONArray();
		JSONObject paymentDetail = new JSONObject();
		paymentDetail.put(AmzLiterals.A_JS_ID, mapGenericProps.get(AmzCommonConstants.PROPS_EXT_REFUND_PAYMENT_ID));
		paymentDetail.put(AmzLiterals.A_JS_STATE,
				mapGenericProps.get(AmzCommonConstants.PROPS_EXT_REFUND_PAYMENT_STATE));

		// Add Amount JSONObject to the paymentDetails
		JSONObject amount = new JSONObject();
		String scurrencyCode = SCXmlUtil.getXpathAttribute(indoc.getDocumentElement(), "OrderInvoiceDetail/InvoiceHeader/Order/PriceInfo/@Currency");
		logger.debug("scurrencyCode is: " + scurrencyCode);
		amount.put(AmzLiterals.A_JS_CURRENCY_CODE, scurrencyCode);
		logger.debug("sTotalrefundAmount is: " + sTotalrefundAmount);
		amount.put(AmzLiterals.A_JS_AMOUNT, String.format("%.2f", sTotalrefundAmount));
		paymentDetail.put(AmzLiterals.A_JS_AMOUNT, amount);

		// Add paymentMethod JSONObject to the paymentDetails
		JSONObject paymentMethod = new JSONObject();
		paymentMethod.put(AmzLiterals.A_JS_DISPLAY_STRING,
				mapGenericProps.get(AmzCommonConstants.PROPS_EXT_REFUND_PAYMENT_DISPLAY_STRING));
		paymentMethod.put(AmzLiterals.A_JS_TYPE, mapGenericProps.get(AmzCommonConstants.PROPS_EXT_REFUND_PAYMENT_TYPE));
		paymentDetail.put(AmzLiterals.A_JS_PAYMENT_METHOD, paymentMethod);

		paymentDetails.put(paymentDetail);
		detail.put(AmzLiterals.A_JS_PAYMENT_DETAILS, paymentDetails);
		logger.info(
				"class: AmzPrepareAmazonExternalRefundRequest | method: addpaymentDetailsToDetailJSONObject -- Ends");
		logger.endTimer(
				"class: AmzPrepareAmazonExternalRefundRequest | method: addpaymentDetailsToDetailJSONObject -- Ends");
	}

	/*
	 * This method create Aliases JSONArray and append Aliases JSONArray to the
	 * Details JSONObject.
	 */
	private void addAliasesToDetailsJSONObject(JSONObject detail, Document indoc)
			throws JSONException, XPathExpressionException {
		// Add aliases JSONArray to the details object
		logger.beginTimer(
				"class: AmzPrepareAmazonExternalRefundRequest | method: addAliasesToDetailsJSONObject -- Starts");
		logger.info("class: AmzPrepareAmazonExternalRefundRequest | method: addAliasesToDetailsJSONObject -- Starts");
		JSONArray aliases = new JSONArray();
		JSONObject alias = new JSONObject();
		String strInvoiceNo = AmzXMLUtil.getXpathAttribute(indoc.getDocumentElement(), AmzLiterals.XPATH_INVOICE_NO);
		logger.debug("strInvoiceNo is: " + strInvoiceNo);
		alias.put(AmzLiterals.A_JS_ALIAS_ID, strInvoiceNo);
		alias.put(AmzLiterals.A_JS_ALIAS_TYPE, AmzCommonConstants.STR_EXTERNAL_REFUND_ID);
		aliases.put(alias);
		detail.put(AmzLiterals.A_JS_ALIASES, aliases);
		logger.info("class: AmzPrepareAmazonExternalRefundRequest | method: addAliasesToDetailsJSONObject -- Ends");
		logger.endTimer("class: AmzPrepareAmazonExternalRefundRequest | method: addAliasesToDetailsJSONObject -- Ends");
	}

	/*
	 * This method create refundTotal JSONObject and append it to the Details
	 * JSONObject.
	 */
	private void addRefundTotalsToDetailJSONObject(JSONObject detail, Document indoc) throws JSONException {
		logger.beginTimer(
				"class: AmzPrepareAmazonExternalRefundRequest | method: addRefundTotalsToDetailJSONObject -- Starts");
		logger.info(
				"class: AmzPrepareAmazonExternalRefundRequest | method: addRefundTotalsToDetailJSONObject -- Starts");
		// Add refunAmount JSONObject to the Details
		JSONObject refundTotal = new JSONObject();
		JSONObject totalAmount = new JSONObject();
		String scurrencyCode = SCXmlUtil.getXpathAttribute(indoc.getDocumentElement(), "OrderInvoiceDetail/InvoiceHeader/Order/PriceInfo/@Currency");
		logger.debug("scurrencyCode is: " + scurrencyCode);
		totalAmount.put(AmzLiterals.A_JS_CURRENCY_CODE, scurrencyCode);
		logger.debug("sTotalrefundAmount is: " + sTotalrefundAmount);
		totalAmount.put(AmzLiterals.A_JS_AMOUNT, String.format("%.2f", sTotalrefundAmount));
		refundTotal.put(AmzLiterals.A_JS_TOTAL_AMOUNT, totalAmount);
		detail.put(AmzLiterals.A_JS_REFUND_TOTAL, refundTotal);
		logger.info("class: AmzPrepareAmazonExternalRefundRequest | method: addRefundTotalsToDetailJSONObject -- Ends");
		logger.endTimer(
				"class: AmzPrepareAmazonExternalRefundRequest | method: addRefundTotalsToDetailJSONObject -- Ends");
	}

	public void setProperties(Properties inProps) {
		this.props = inProps;
	}

}
