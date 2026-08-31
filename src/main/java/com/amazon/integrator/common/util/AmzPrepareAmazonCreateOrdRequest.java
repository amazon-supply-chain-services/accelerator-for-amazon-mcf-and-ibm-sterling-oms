package com.amazon.integrator.common.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

public class AmzPrepareAmazonCreateOrdRequest {
	/*
	 * This Class is used to prepare the json input request to invoke the amazon api
	 * using OMS order XML
	 */

	final YFCLogCategory logger = YFCLogCategory.instance(AmzPrepareAmazonCreateOrdRequest.class);

	double dReleasedQty = 1;
	double dOrderedQty = 1;
	double dLineDeliveryChargeAmount = 0;
	double dLineDeliveryChargeDiscount = 0;
	double dLineDeliveryChargeTax = 0;
	double dLineExtendedPrice = 0;
	double dLineDiscountAmount = 0;
	double dLineItemTaxAmount = 0;
	double dOverallTotal = 0;
	String strExtnlwaAccessToken = null;
	boolean isBWPline = false;
	HashMap<String, String> lineKeyAmzonReleaseQty = new HashMap<>();
	String strAmzShipNode = null;
	Map<String, String> mapGenericProps = null;
	boolean isOrderRelease = false;
	int iBwpElgPrimeLineNo = 0;

	/*
	 * This method is invoked to prepare the input json request for amazon create
	 * order.
	 */
	public JSONObject prepareAmzCreateOrderVariableJSON(YFSEnvironment env, Element eleOrder,
			List<String> amzCreateOrdElgPrimeLineNo, String strOrdReleaseKey) throws Exception {

		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: prepareAmzCreateOrderVariableJSON -- Starts");
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: prepareAmzCreateOrderVariableJSON -- Starts");
		String sEnterpriseCode = eleOrder.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
		Document inDocGetGenrcProperty = AmzXMLUtil.createDocument(AmzLiterals.E_PROPERTIES);
		inDocGetGenrcProperty.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, sEnterpriseCode);
		mapGenericProps = AmzGetGenericProperty.getGenericProperties(env, inDocGetGenrcProperty);
		Element eleOrderExtn = SCXmlUtil.getChildElement(eleOrder, AmzLiterals.E_EXTN);
		String sExtnOrderCountry = eleOrderExtn.getAttribute(AmzLiterals.A_EXTN_ORDER_COUNTRY);
		strAmzShipNode = mapGenericProps.get(AmzCommonConstants.PROP_AMZ_SHIP_NODE + sExtnOrderCountry);

		Element eleOrdLines = AmzXMLUtil.getChildElement(eleOrder, AmzLiterals.E_ORDER_LINES);
		Element elePersonInfo = null;
		Element elePriceInfo = AmzXMLUtil.getChildElement(eleOrder, AmzLiterals.E_PRICE_INFO);

		String strCurrency = elePriceInfo.getAttribute(AmzLiterals.A_CURRENCY);
		logger.debug("strCurrency is: " + strCurrency);
		JSONObject variables = new JSONObject();

		// Create the "input" object
		JSONObject input = new JSONObject();

		// Create the "customer" object with nested "contact" and "emailData"
		prepareCustomJSONObject(input, eleOrder);

		// Create the "lineItems" array and add line item object

		JSONArray lineItems = new JSONArray();
		int iAmzCreateOrdElgLines = amzCreateOrdElgPrimeLineNo.size();
		for (int i = 0; i < iAmzCreateOrdElgLines; i++) {
			String strPrimeLineNo = amzCreateOrdElgPrimeLineNo.get(i);
			Element eleOrderLine = AmzXMLUtil.getXpathElement(eleOrdLines,
					"OrderLine[@PrimeLineNo='" + strPrimeLineNo + "']");
			updateLineItemToCreateOrder(eleOrderLine, lineItems, strCurrency);
			if (YFCObject.isVoid(elePersonInfo)) {
				elePersonInfo = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_PERSON_INFO_SHIP_TO);
			}

		}
		// Add desiredExecutionState to the input
		if (isOrderRelease) {
			input.put("desiredExecutionState", mapGenericProps.get(AmzCommonConstants.AMZ_EXECUTION_STATE_STARTED_MCF));
		} else {
			input.put("desiredExecutionState",
					mapGenericProps.get(AmzCommonConstants.AMZ_EXECUTION_STATE_NOT_STARTED_BWP));

		}

		// Add lineItems to input
		input.put("lineItems", lineItems);

		// Create the AmzLiterals.A_JS_ALIASES array and add the alias object
		JSONArray aliases = new JSONArray();
		JSONObject alias = new JSONObject();
		if (!YFCObject.isVoid(strOrdReleaseKey) && Boolean.FALSE.equals(isBWPline)) {
			String strReleaseNo = getReleaseNo(env, strOrdReleaseKey);
			if (!YFCObject.isVoid(strReleaseNo)) {
				int iReleaseNo = Integer.parseInt(strReleaseNo);
				String strPaddedReleaseNo = String.format("%02d", iReleaseNo);
				logger.debug(" strPaddedReleaseNo is : " + strPaddedReleaseNo);
				String strAliasId = eleOrder.getAttribute(AmzLiterals.A_ORDER_NO) + "-" + strPaddedReleaseNo;
				alias.put(AmzLiterals.A_JS_ALIAS_ID, strAliasId);
			}
		} else {
			alias.put(AmzLiterals.A_JS_ALIAS_ID, eleOrder.getAttribute(AmzLiterals.A_ORDER_NO));
		}
		alias.put(AmzLiterals.A_JS_ALIAS_TYPE, AmzCommonConstants.STR_EXTERNAL_ORDER_ID);
		aliases.put(alias);

		// Add aliases to the input
		input.put(AmzLiterals.A_JS_ALIASES, aliases);

		// Create the "recipient" object with "deliveryAddress"
		if (!YFCObject.isVoid(elePersonInfo)) {
			preparerecipientJSONObject(input, elePersonInfo);
		}

		// Create the "shopperIdentity" object with "lwaAccessToken"
		prepareShopperIdentityJsnObject(input);

		// Create the "totalPrice" object
		preapreTotalPriceJsonObject(input, strCurrency);

		// Add input to variables
		variables.put("input", input);

		// Print the final JSON structure
		logger.debug("AmzPrepareAmazonCreateOrdRequest. prepareAmzCreateOrderVariableJSON.variables is: " + variables);
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: prepareAmzCreateOrderVariableJSON -- Ends");
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: prepareAmzCreateOrderVariableJSON -- Ends");
		return variables;

	}

	/*
	 * This method is prepare the customer json object to amazon create order input
	 * json
	 */
	private void prepareCustomJSONObject(JSONObject input, Element eleOutOrder) throws JSONException {
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: prepareCustomJSONObject -- Starts");
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: prepareCustomJSONObject -- Starts");
		String strCustomerEmail = eleOutOrder.getAttribute(AmzLiterals.A_CUSTOMER_EMAIL_ID);
		String strCustFirstName = eleOutOrder.getAttribute(AmzLiterals.A_CUSTOMER_FIRST_NAME);
		String strCustLastName = eleOutOrder.getAttribute(AmzLiterals.A_CUSTOMER_LAST_NAME);
		String strCustomerName = strCustFirstName + strCustLastName;

		if (!YFCObject.isVoid(strCustomerEmail) || !YFCObject.isVoid(strCustomerName)) {
			JSONObject customer = new JSONObject();
			JSONObject contact = new JSONObject();
			JSONObject emailData = new JSONObject();

			if (!YFCObject.isVoid(strCustomerEmail)) {
				emailData.put("email", eleOutOrder.getAttribute(AmzLiterals.A_CUSTOMER_EMAIL_ID));
			}
			if (!YFCObject.isVoid(strCustomerName)) {
				emailData.put("name", strCustomerName);
			}
			contact.put("emailData", emailData);
			customer.put("contact", contact);
			input.put("customer", customer);
		}
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: prepareCustomJSONObject -- Ends");
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: prepareCustomJSONObject -- Ends");
	}

	/*
	 * This method prepare itemLine json input for amazon create order
	 */
	private void updateLineItemToCreateOrder(Element eleOrderLine, JSONArray lineItems, String strCurrency)
			throws XPathExpressionException, JSONException {
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: updateLineItemToCreateOrder -- Starts");
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: updateLineItemToCreateOrder -- Starts");
		logger.debug("class: AmzPrepareAmazonCreateOrdRequest | method: updateLineItemToCreateOrder . InDoc is: "
				+ AmzXMLUtil.getString(eleOrderLine));
		dLineDeliveryChargeAmount = 0;
		dReleasedQty = 1;
		dOrderedQty = 1;
		dLineDeliveryChargeDiscount = 0;
		dLineItemTaxAmount = 0;
		dLineDiscountAmount = 0;
		dLineExtendedPrice = 0;
		isBWPline = false;
		Element eleExtnOrdLin = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_EXTN);
		Element eleScheduledOrderStatus = AmzXMLUtil.getXpathElement(eleOrderLine,
				"OrderStatuses/OrderStatus[@Status='3200' and @ShipNode='" + strAmzShipNode + "']");

		if (!YFCObject.isVoid(eleScheduledOrderStatus) && !isOrderRelease) {
			isOrderRelease = true;
		}
		Element eleLineOverallTotals = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_LINE_OVERALL_TOTALS);
		String sFulfillmentType = eleOrderLine.getAttribute(AmzLiterals.A_FULFILLMENT_TYPE);
		logger.debug("sFulfillmentType is: " + sFulfillmentType);
		if (!YFCObject.isVoid(eleScheduledOrderStatus)) {
			String strReleasedQty = eleScheduledOrderStatus.getAttribute(AmzLiterals.A_STATUS_QTY);
			logger.debug("strScheduledQty is: " + strReleasedQty);
			if (!YFCObject.isVoid(strReleasedQty)) {
				dReleasedQty = Double.parseDouble(strReleasedQty);
				lineKeyAmzonReleaseQty.put(eleOrderLine.getAttribute(AmzLiterals.A_ORDER_LINE_KEY), strReleasedQty);
			}
		}
		String strOrderedQty = eleOrderLine.getAttribute(AmzLiterals.A_ORDERED_QTY);
		if (!YFCObject.isVoid(strOrderedQty)) {
			dOrderedQty = Double.parseDouble(strOrderedQty);
		}
		logger.debug("dScheduledStatusQty is: " + dReleasedQty);
		logger.debug("dOrderedQty is: " + dOrderedQty);
		if (!YFCObject.isVoid(eleExtnOrdLin)) {
			strExtnlwaAccessToken = eleExtnOrdLin.getAttribute(AmzLiterals.EXTN_LWA_ACCESS_TOKEN);
			String sExtnIsPrimeElg = eleExtnOrdLin.getAttribute(AmzLiterals.A_EXTN_IS_PRIME_ELIGIBLE);
			String sExtnIsAmazonFulFillable = eleExtnOrdLin.getAttribute(AmzLiterals.A_EXTN_IS_AMAZON_FULFILLABLE);
			if (!YFCObject.isVoid(sExtnIsPrimeElg) && AmzCommonConstants.STR_VAL_Y.equalsIgnoreCase(sExtnIsPrimeElg)
					&& !YFCObject.isVoid(sExtnIsAmazonFulFillable)
					&& AmzCommonConstants.STR_VAL_Y.equalsIgnoreCase(sExtnIsAmazonFulFillable)) {
				isBWPline = true;
				iBwpElgPrimeLineNo = iBwpElgPrimeLineNo + 1;
			}
		}
		Element eleItem = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_ITEM);
		JSONObject lineItem = new JSONObject();

		JSONObject product = new JSONObject();
		JSONObject identifier = new JSONObject();
		String sOMSItemIDPrefAmazonCatalog = mapGenericProps
				.get(AmzCommonConstants.PROP_AMZ_OMS_ITEMID_XREF_AMAZONCATALOG);
		logger.debug("amzConn.oms.ItemID.xref.amazonCatalog generic property value is: " + sOMSItemIDPrefAmazonCatalog);
		if (AmzLiterals.A_JS_EXTERNAL_ID.equalsIgnoreCase(sOMSItemIDPrefAmazonCatalog)) {
			identifier.put(AmzLiterals.A_JS_EXTERNAL_ID, eleItem.getAttribute(AmzLiterals.A_ITEM_ID));
		} else if (AmzLiterals.A_JS_AMAZON_SKU.equalsIgnoreCase(sOMSItemIDPrefAmazonCatalog)) {
			JSONObject amazonSku = new JSONObject();
			amazonSku.put(AmzLiterals.A_JS_VALUE, eleItem.getAttribute(AmzLiterals.A_ITEM_ID));
			identifier.put(AmzLiterals.A_JS_AMAZON_SKU, amazonSku);
		} else {
			YFSException yfsException = new YFSException();
			yfsException.setErrorCode("PROP_AMZ_OMS_ITEMID_XREF_AMAZONCATALOG_IS_NULL");
			yfsException.setErrorDescription("Error While prepareing JSON Input to create Order In Amazon");
			logger.error("Exception in AmzPrepareAmazonCreateOrdRequest.updateLineItemToCreateOrder Method: "
					+ ExceptionUtils.getStackTrace(yfsException));
			throw AmzCommonUtil.createException(yfsException);
		}
		product.put("identifier", identifier);
		JSONObject price = new JSONObject();
		String strExtendedPrice = eleLineOverallTotals.getAttribute(AmzLiterals.A_EXTENDED_PRICE);
		String strUnitPrice = eleLineOverallTotals.getAttribute(AmzLiterals.A_UNIT_PRICE);
		logger.debug("strUnitPrice is: " + strUnitPrice);
		double dExtendedPrice = 0;
		if (!YFCObject.isVoid(strExtendedPrice)) {
			dExtendedPrice = Double.parseDouble(strExtendedPrice);
		}
		// Add amount and clientDetails to the line item
		JSONObject amount = new JSONObject();
		if (isBWPline) {
			dLineExtendedPrice = dExtendedPrice;
			amount.put(AmzLiterals.A_JS_VALUE, eleOrderLine.getAttribute(AmzLiterals.A_ORDERED_QTY));
		} else {
			dLineExtendedPrice = ((dExtendedPrice / dOrderedQty) * dReleasedQty);
			amount.put(AmzLiterals.A_JS_VALUE, dReleasedQty);
		}
		lineItem.put(AmzLiterals.A_JS_AMOUNT, amount);

		logger.debug("dLineExtendedPrice is: " + dLineExtendedPrice);
		price.put(AmzLiterals.A_JS_AMOUNT, strUnitPrice);
		price.put(AmzLiterals.A_JS_CURRENCY_CODE, strCurrency);
		if (!YFCObject.isVoid(eleItem.getAttribute(AmzLiterals.A_ITEM_DESC))) {
			product.put("title", eleItem.getAttribute(AmzLiterals.A_ITEM_DESC));
		} else if (!YFCObject.isVoid(eleItem.getAttribute(AmzLiterals.A_ITEM_SHORT_DESC))) {
			product.put("title", eleItem.getAttribute(AmzLiterals.A_ITEM_SHORT_DESC));
		}
		product.put("price", price);
		lineItem.put("product", product);

		// Add selectedDeliveryOffer
		JSONObject selectedDeliveryOffer = new JSONObject();
		JSONObject details = new JSONObject();

		prepareDeliveryTermsJSONObject(details, eleExtnOrdLin);

		Element eleLineCharges = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_LINE_CHARGES);
		updatedelChgAnddiscounts(details, eleLineCharges, strCurrency);

		Element eleLineTaxes = AmzXMLUtil.getChildElement(eleOrderLine, AmzLiterals.E_LINE_TAXES);
		updateDeliveryOfferTaxes(details, eleLineTaxes, strCurrency);

		updateSalesTaxesAndDiscounts(lineItem, eleLineOverallTotals, eleOrderLine, strCurrency);
		selectedDeliveryOffer.put("details", details);
		lineItem.put("selectedDeliveryOffer", selectedDeliveryOffer);
		prepareLineItemAliasArray(lineItem, eleOrderLine);

		// Add lineItem to lineItems array
		lineItems.put(lineItem);

		dOverallTotal += dLineExtendedPrice + dLineItemTaxAmount + dLineDeliveryChargeAmount + dLineDeliveryChargeTax
				- dLineDiscountAmount - dLineDeliveryChargeDiscount;
		logger.debug("dOverallTotal is : " + dOverallTotal);
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: updateLineItemToCreateOrder -- Ends");
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: updateLineItemToCreateOrder -- Ends");

	}

	/*
	 * This method add the deliveryTerms for lineitem to the amazon create order
	 * input json
	 */
	private void prepareDeliveryTermsJSONObject(JSONObject details, Element eleExtnOrdLin) throws JSONException {
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: prepareDeliveryTermsJSONObject -- Starts");
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: prepareDeliveryTermsJSONObject -- Starts");
		JSONObject deliveryTerms = new JSONObject();
		if (isBWPline) {
			deliveryTerms.put("isPrimeEligible", Boolean.TRUE);
			details.put("deliveryPreviewId", eleExtnOrdLin.getAttribute(AmzLiterals.A_EXTN_AMAZON_DEL_PERVIEW_ID));
			details.put("id", eleExtnOrdLin.getAttribute(AmzLiterals.EXTN_AMAZON_DELIVERY_OFFER_ID));
		} else {
			deliveryTerms.put("isPrimeEligible", Boolean.FALSE);
		}
		details.put("deliveryProvider", mapGenericProps.get(AmzCommonConstants.AMZ_DELIVERY_PROVIDER));
		details.put("deliveryTerms", deliveryTerms);
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: prepareDeliveryTermsJSONObject -- Ends");
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: prepareDeliveryTermsJSONObject -- Ends");
	}

	/*
	 * This method prepare the Delivery charge and discount json object for the
	 * amazon create order json input
	 */
	private void updatedelChgAnddiscounts(JSONObject details, Element eleLineCharges, String strCurrency)
			throws JSONException {
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: updatedelChgAnddiscounts -- Starts");
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: updatedelChgAnddiscounts -- Starts");
		String sdeliveryChargeCat = mapGenericProps.get(AmzCommonConstants.AMZ_SHIPPING_CHARGE_CATEGORY);
		String sdeliveryChargeName = mapGenericProps.get(AmzCommonConstants.AMZ_SHIPPING_CHARGE_NAME);
		String strShippingDiscountChargeCategory = mapGenericProps
				.get(AmzCommonConstants.AMZ_SHIPPING_DISCOUNT_CHARGE_CATEGORY);
		String strShippingDiscountChargeName = mapGenericProps
				.get(AmzCommonConstants.AMZ_SHIPPING_DISCOUNT_CHARGE_NAME);
		NodeList nLineCharge = eleLineCharges.getElementsByTagName(AmzLiterals.E_LINE_CHARGE);
		int iLineChargeLen = nLineCharge.getLength();
		for (int j = 0; j < iLineChargeLen; j++) {
			Element eleLineCharge = (Element) nLineCharge.item(j);
			String strChargeCategory = eleLineCharge.getAttribute(AmzLiterals.A_CHARGE_CATEGORY);
			String strChargeName = eleLineCharge.getAttribute(AmzLiterals.A_CHARGE_NAME);
			String strChargeAmount = eleLineCharge.getAttribute(AmzLiterals.A_CHARGE_AMOUNT);
			logger.debug(" strChargeCategory is: " + strChargeCategory);
			logger.debug(" strChargeName is: " + strChargeName);
			logger.debug(" strChargeAmount is: " + strChargeAmount);
			double dChargeAmount = 0;
			if (!YFCObject.isVoid(strChargeAmount)) {
				dChargeAmount = Double.parseDouble(strChargeAmount);
				logger.debug(" dChargeAmount is: " + dChargeAmount);
				if (!isBWPline) {
					dChargeAmount = ((dChargeAmount / dOrderedQty) * dReleasedQty);
				}
			}
			logger.debug(" dChargeAmount is: " + dChargeAmount);
			if (!YFCObject.isVoid(strChargeCategory) && !YFCObject.isVoid(strChargeName)
					&& sdeliveryChargeCat.equalsIgnoreCase(strChargeCategory)
					&& sdeliveryChargeName.equalsIgnoreCase(strChargeName)) {
				dLineDeliveryChargeAmount = dChargeAmount;
				logger.debug(" dLineDeliveryChargeAmount is: " + dLineDeliveryChargeAmount);
				JSONObject deliveryCharge = new JSONObject();
				deliveryCharge.put(AmzLiterals.A_JS_AMOUNT, String.format("%.2f", dLineDeliveryChargeAmount));
				deliveryCharge.put(AmzLiterals.A_JS_CURRENCY_CODE, strCurrency);
				details.put("deliveryCharge", deliveryCharge);
			}
			if (!YFCObject.isVoid(strChargeCategory) && !YFCObject.isVoid(strChargeName)
					&& !YFCObject.isVoid(strChargeAmount)
					&& strShippingDiscountChargeCategory.equalsIgnoreCase(strChargeCategory)
					&& strShippingDiscountChargeName.equalsIgnoreCase(strChargeName)) {
				dLineDeliveryChargeDiscount = dChargeAmount;
				logger.debug(" dLineDeliveryChargeDiscount is: " + dLineDeliveryChargeDiscount);
				JSONObject discounts = new JSONObject();
				JSONObject summary = new JSONObject();
				JSONObject discountsAmount = new JSONObject();
				discounts.put(AmzLiterals.A_JS_SUMMARY, summary);
				summary.put(AmzLiterals.A_JS_AMOUNT, discountsAmount);
				discountsAmount.put(AmzLiterals.A_JS_AMOUNT, String.format("%.2f", dLineDeliveryChargeDiscount));
				discountsAmount.put(AmzLiterals.A_JS_CURRENCY_CODE, strCurrency);
				details.put("discounts", discounts);
			}
		}
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: updatedelChgAnddiscounts -- Ends");
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: updatedelChgAnddiscounts -- Ends");

	}

	/*
	 * this method fetch and prepare the deliver taxes and discount object in the
	 * amazon create order json request
	 */
	private void updateDeliveryOfferTaxes(JSONObject details, Element eleLineTaxes, String strCurrency)
			throws JSONException {
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: updateDeliveryOfferTaxes -- Starts");
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: updateDeliveryOfferTaxes -- Starts");
		String strShippingTaxChargeName = mapGenericProps.get(AmzCommonConstants.AMZ_SHIPPING_TAX_CHARGE_NAME);
		String strShippingTaxName = mapGenericProps.get(AmzCommonConstants.AMZ_SHIPPING_TAX_NAME);
		String strShippingTaxChargeCategory = mapGenericProps.get(AmzCommonConstants.AMZ_SHIPPING_TAX_CHARGE_CATEGORY);
		NodeList nLineTax = eleLineTaxes.getElementsByTagName(AmzLiterals.E_LINE_TAX);
		int iLineTaxLen = nLineTax.getLength();
		for (int i = 0; i < iLineTaxLen; i++) {
			Element eleLineTax = (Element) nLineTax.item(i);
			String strChargeCategory = eleLineTax.getAttribute(AmzLiterals.A_CHARGE_CATEGORY);
			String strChargeName = eleLineTax.getAttribute(AmzLiterals.A_CHARGE_NAME);
			String strTaxName = eleLineTax.getAttribute(AmzLiterals.A_TAX_NAME);
			String strTax = eleLineTax.getAttribute(AmzLiterals.A_TAX);
			double dTaxAmt = 0;
			if (!YFCObject.isVoid(strTax)) {
				dTaxAmt = Double.parseDouble(strTax);
				logger.debug("dTaxAmt is: " + dTaxAmt);

				if (!isBWPline) {
					dTaxAmt = ((dTaxAmt / dOrderedQty) * dReleasedQty);
				}

				logger.debug("dTaxAmt is: " + dTaxAmt);

			}
			if (!YFCObject.isVoid(strChargeCategory) && !YFCObject.isVoid(strChargeName)
					&& !YFCObject.isVoid(strTaxName) && strShippingTaxName.equalsIgnoreCase(strTaxName)
					&& strShippingTaxChargeName.equalsIgnoreCase(strChargeName)
					&& strShippingTaxChargeCategory.equalsIgnoreCase(strChargeCategory)) {
				dLineDeliveryChargeTax = dTaxAmt;
				logger.debug("dLineDeliveryChargeTax is: " + dLineDeliveryChargeTax);
				JSONObject taxes = new JSONObject();
				JSONObject summary = new JSONObject();
				JSONObject collectableTaxAmount = new JSONObject();
				collectableTaxAmount.put(AmzLiterals.A_JS_AMOUNT, String.format("%.2f", dLineDeliveryChargeTax));
				collectableTaxAmount.put(AmzLiterals.A_JS_CURRENCY_CODE, strCurrency);
				summary.put("collectableTaxAmount", collectableTaxAmount);
				taxes.put(AmzLiterals.A_JS_SUMMARY, summary);
				details.put("taxes", taxes);
			}
		}
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: updateDeliveryOfferTaxes -- Ends");
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: updateDeliveryOfferTaxes -- Ends");

	}

	/*
	 * This method add the Sales Taxes and Discount to the amazon create order input
	 * json
	 */
	private void updateSalesTaxesAndDiscounts(JSONObject lineItem, Element eleLineOverallTotals, Element eleOrderLine,
			String strCurrency) throws JSONException {
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: updateSalesTaxesAndDiscounts -- Starts");
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: updateSalesTaxesAndDiscounts -- Starts");
		String sLineSalesTax = eleLineOverallTotals.getAttribute(AmzLiterals.A_TAX);
		String sLineSalesDiscount = eleLineOverallTotals.getAttribute(AmzLiterals.A_DISCOUNT);
		logger.debug("sLineSalesTax is: " + sLineSalesTax);
		logger.debug("sLineSalesDiscount is: " + sLineSalesDiscount);
		String sFulfillmentType = eleOrderLine.getAttribute(AmzLiterals.A_FULFILLMENT_TYPE);
		logger.debug("sFulfillmentType is: " + sFulfillmentType);
		if (!YFCObject.isVoid(sLineSalesTax)) {

			double dLineTax = Double.parseDouble(sLineSalesTax) - dLineDeliveryChargeTax;
			logger.debug("dLineTax is: " + dLineTax);
			if (!isBWPline) {
				dLineTax = ((dLineTax / dOrderedQty) * dReleasedQty);
			}

			logger.debug("dLineTax is: " + dLineTax);
			dLineItemTaxAmount = dLineTax;
			logger.debug("dLineItemTaxAmount is: " + dLineItemTaxAmount);
			JSONObject taxes = new JSONObject();
			JSONObject summary = new JSONObject();
			JSONObject collectableTaxAmount = new JSONObject();
			collectableTaxAmount.put(AmzLiterals.A_JS_AMOUNT, String.format("%.2f", dLineItemTaxAmount));
			collectableTaxAmount.put(AmzLiterals.A_JS_CURRENCY_CODE, strCurrency);
			summary.put("collectableTaxAmount", collectableTaxAmount);
			taxes.put(AmzLiterals.A_JS_SUMMARY, summary);
			lineItem.put("taxes", taxes);

		}
		if (!YFCObject.isVoid(sLineSalesDiscount)) {
			double dSalesDiscount = Double.parseDouble(sLineSalesDiscount) - dLineDeliveryChargeDiscount;
			logger.debug("dSalesDiscount is: " + dSalesDiscount);

			if (!isBWPline) {
				dSalesDiscount = ((dSalesDiscount / dOrderedQty) * dReleasedQty);
			}

			logger.debug("dSalesDiscount is: " + dSalesDiscount);
			dLineDiscountAmount = dSalesDiscount;
			logger.debug("dLineDiscountAmount is: " + dLineDiscountAmount);
			JSONObject discounts = new JSONObject();
			JSONObject summary = new JSONObject();
			JSONObject discountsAmount = new JSONObject();
			discounts.put(AmzLiterals.A_JS_SUMMARY, summary);
			summary.put(AmzLiterals.A_JS_AMOUNT, discountsAmount);
			discountsAmount.put(AmzLiterals.A_JS_AMOUNT, String.format("%.2f", dLineDiscountAmount));
			discountsAmount.put(AmzLiterals.A_JS_CURRENCY_CODE, strCurrency);
			lineItem.put("discounts", discounts);
		}
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: updateSalesTaxesAndDiscounts -- Ends");
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: updateSalesTaxesAndDiscounts -- Ends");

	}

	/*
	 * This method add the alias for the lineitem to the amazon create order input
	 * json
	 */
	private void prepareLineItemAliasArray(JSONObject lineItem, Element eleOrderLine) throws JSONException {
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: prepareLineItemAliasArray -- Starts");
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: prepareLineItemAliasArray -- Starts");
		JSONArray aliases = new JSONArray();
		JSONObject aliasPrimeLineNo = new JSONObject();
		aliasPrimeLineNo.put(AmzLiterals.A_JS_ALIAS_ID, eleOrderLine.getAttribute(AmzLiterals.A_ORDER_LINE_KEY));
		aliasPrimeLineNo.put(AmzLiterals.A_JS_ALIAS_TYPE, "OMS_ORDERLINE_KEY");
		aliases.put(aliasPrimeLineNo);
		JSONObject aliasOrdLineKey = new JSONObject();
		aliasOrdLineKey.put(AmzLiterals.A_JS_ALIAS_ID, eleOrderLine.getAttribute(AmzLiterals.A_PRIME_LINE_NO));
		aliasOrdLineKey.put(AmzLiterals.A_JS_ALIAS_TYPE, "OMS_PRIMELINE_NO");
		aliases.put(aliasOrdLineKey);
		lineItem.put(AmzLiterals.A_JS_ALIASES, aliases);
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: prepareLineItemAliasArray -- Ends");
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: prepareLineItemAliasArray -- Ends");
	}

	/*
	 * This method is prepare the recipient json object to amazon create order input
	 * json
	 */
	private void preparerecipientJSONObject(JSONObject input, Element elePersonInfo) throws JSONException {
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: preparerecipientJSONObject -- Starts");
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: preparerecipientJSONObject -- Starts");
		JSONObject recipient = new JSONObject();
		JSONObject deliveryAddress = new JSONObject();
		deliveryAddress.put("countryCode", elePersonInfo.getAttribute(AmzLiterals.A_COUNTRY));
		deliveryAddress.put("locality", elePersonInfo.getAttribute(AmzLiterals.A_CITY));
		deliveryAddress.put("name", elePersonInfo.getAttribute(AmzLiterals.A_FIRST_NAME) + " "
				+ elePersonInfo.getAttribute(AmzLiterals.A_LAST_NAME));
		deliveryAddress.put("postalCode", elePersonInfo.getAttribute(AmzLiterals.A_ZIP_CODE));
		deliveryAddress.put("region", elePersonInfo.getAttribute(AmzLiterals.A_STATE));
		deliveryAddress.put("streetAddress", elePersonInfo.getAttribute(AmzLiterals.A_ADDRESS_LINE1));
		recipient.put("deliveryAddress", deliveryAddress);
		input.put("recipient", recipient);

	}

	/*
	 * This method is prepare the shopperIdentity json object to amazon create order
	 * input json
	 */
	private void prepareShopperIdentityJsnObject(JSONObject input) throws JSONException {
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: prepareShopperIdentityJsnObject -- Starts");
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: prepareShopperIdentityJsnObject -- Starts");
		if (iBwpElgPrimeLineNo > 0) {
			JSONObject shopperIdentity = new JSONObject();
			JSONObject lwaAccessToken = new JSONObject();
			lwaAccessToken.put(AmzLiterals.A_JS_VALUE, strExtnlwaAccessToken);
			shopperIdentity.put("lwaAccessToken", lwaAccessToken);
			input.put("shopperIdentity", shopperIdentity);
		}
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: prepareShopperIdentityJsnObject -- Ends");
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: prepareShopperIdentityJsnObject -- Ends");
	}

	/*
	 * This method is prepare the totalPrice json object to amazon create order
	 * input json
	 */
	private void preapreTotalPriceJsonObject(JSONObject input, String strCurrency) throws JSONException {
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: preapreTotalPriceJsonObject -- Starts");
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: preapreTotalPriceJsonObject -- Starts");
		JSONObject totalPrice = new JSONObject();
		JSONObject value = new JSONObject();
		value.put(AmzLiterals.A_JS_AMOUNT, String.format("%.2f", dOverallTotal));
		value.put(AmzLiterals.A_JS_CURRENCY_CODE, strCurrency);
		totalPrice.put(AmzLiterals.A_JS_VALUE, value);
		input.put("totalPrice", totalPrice);
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: preapreTotalPriceJsonObject -- Ends");
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: preapreTotalPriceJsonObject -- Ends");
	}

	/*
	 * This method invoke the getOrderReleaseList with OrderReleaseKey return the
	 * ReleaseNo of the Release
	 */
	private String getReleaseNo(YFSEnvironment env, String strOrderReleaseKey) {
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: getReleaseNo -- Starts");
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: getReleaseNo -- Starts");
		Document inDocGetOrdReleaseList = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER_RELEASE);
		Element eleInOrdRelease = inDocGetOrdReleaseList.getDocumentElement();
		eleInOrdRelease.setAttribute(AmzLiterals.A_ORDER_RELEASE_KEY, strOrderReleaseKey);
		logger.debug("getOrderReleaseList input doc is: " + AmzXMLUtil.getString(inDocGetOrdReleaseList));
		Document outDocGetOrdReleaseList = AmzCommonUtil.invokeAPI(env,
				AmzCommonConstants.TEMPLATE_GET_ORDER_RELEASE_LIST_FOR_AMZ_CREATE_ORDER,
				AmzCommonConstants.API_GET_ORDER_RELEASE_LIST, inDocGetOrdReleaseList);
		logger.debug("getOrderReleaseList out doc is: " + AmzXMLUtil.getString(outDocGetOrdReleaseList));
		Element eleOutOrdRelList = outDocGetOrdReleaseList.getDocumentElement();
		Element eleOutOrdRelease = AmzXMLUtil.getChildElement(eleOutOrdRelList, AmzLiterals.E_ORDER_RELEASE);
		logger.debug("ReleaseNo is : " + eleOutOrdRelease.getAttribute(AmzLiterals.A_RELEASE_NO));
		logger.info("class: AmzPrepareAmazonCreateOrdRequest | method: getReleaseNo -- Ends");
		logger.timer("class: AmzPrepareAmazonCreateOrdRequest | method: getReleaseNo -- Ends");
		return eleOutOrdRelease.getAttribute(AmzLiterals.A_RELEASE_NO);

	}

}
