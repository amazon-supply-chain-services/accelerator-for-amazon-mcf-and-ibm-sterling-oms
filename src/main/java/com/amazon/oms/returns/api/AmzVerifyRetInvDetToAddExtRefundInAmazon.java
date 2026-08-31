package com.amazon.oms.returns.api;

import java.util.Properties;

import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;

public class AmzVerifyRetInvDetToAddExtRefundInAmazon implements YIFCustomApi {
	private Properties props;
	final YFCLogCategory logger = YFCLogCategory.instance(AmzVerifyRetInvDetToAddExtRefundInAmazon.class);

	/*
	 * This method invoke the getOrderInvoiceDetailList api and pass the
	 * getOrderInvoiceDetailList output to massageOrderInvoiceDetails method
	 */
	public Document verifyAndMassageRetInvToAddExtRefund(YFSEnvironment env, Document indoc)
			throws XPathExpressionException {
		logger.beginTimer(
				"class: AmzVerifyRetInvDetToAddExtRefundInAmazon | method: verifyAndMassageRetInvToAddExtRefund -- Starts");
		logger.info(
				"class: AmzVerifyRetInvDetToAddExtRefundInAmazon | method: verifyAndMassageRetInvToAddExtRefund -- Starts");
		Element eleOrder = indoc.getDocumentElement();
		NodeList nInvoiceCollection = AmzXMLUtil.getXpathNodes(eleOrder, "InvoiceCollections/InvoiceCollection");
		int iOrderInvoiceLen = nInvoiceCollection.getLength();
		for (int i = 0; i < iOrderInvoiceLen; i++) {
			Element eleInvoiceCollection = (Element) nInvoiceCollection.item(i);
			String sOrderInvoiceKey = eleInvoiceCollection.getAttribute(AmzLiterals.A_ORDER_INVOICE_KEY);
			logger.debug("sOrderInvoiceKey is: " + sOrderInvoiceKey);
			Element eleOrderInvoice = AmzXMLUtil.getChildElement(eleInvoiceCollection, AmzLiterals.E_ORDER_INVOICE);
			if (!YFCObject.isVoid(eleOrderInvoice)) {
				String strInvoiceType = eleOrderInvoice.getAttribute(AmzLiterals.A_INVOICE_TYPE);
				if (!YFCObject.isVoid(sOrderInvoiceKey) && !YFCObject.isVoid(strInvoiceType)
						&& (AmzCommonConstants.STR_RETURN.equalsIgnoreCase(strInvoiceType))) {
					Document inDocGetOrdInvDetlist = SCXmlUtil.createDocument(AmzLiterals.E_ORDER_INVOICE_DETAIL);
					inDocGetOrdInvDetlist.getDocumentElement().setAttribute(AmzLiterals.A_ORDER_INVOICE_KEY,
							sOrderInvoiceKey);
					logger.debug("inDocGetOrdInvDetlist is: " + SCXmlUtil.getString(inDocGetOrdInvDetlist));
					Document outDocGetOrdInvDetList = AmzCommonUtil.invokeAPI(env,
							AmzCommonConstants.TEMPLATE_GET_ORD_INV_DET_LIST_FOR_AMAZON_EXT_REFUND,
							AmzCommonConstants.API_GET_ORDER_INVOICE_DETAIL_LIST, inDocGetOrdInvDetlist);
					logger.debug("outDocGetOrdInvDetList is: " + SCXmlUtil.getString(outDocGetOrdInvDetList));
					if (!YFCObject.isVoid(outDocGetOrdInvDetList)) {
						outDocGetOrdInvDetList.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE,
								indoc.getDocumentElement().getAttribute(AmzLiterals.A_ENTERPRISE_CODE));
						logger.debug("Input Document for AmzConnProcessInvoiceDetails is: "
								+ SCXmlUtil.getString(outDocGetOrdInvDetList));
						massageOrderInvoiceDetails(env, outDocGetOrdInvDetList);
					}
				}
			}
		}

		logger.info(
				"class: AmzVerifyRetInvDetToAddExtRefundInAmazon || method: verifyAndMassageRetInvToAddExtRefund -- End");
		logger.endTimer(
				"class: AmzVerifyRetInvDetToAddExtRefundInAmazon || method: verifyAndMassageRetInvToAddExtRefund -- End");
		return indoc;
	}

	/*
	 * This verify the getOrderInvoiceDetailList output and invoke getOrderLIst if
	 * Any merchant initiated return line are present, and massage the
	 * invoiceDetails with getOrderList output and pass the message to
	 * verifyReturnInvToAddExtRefInAmazon method
	 */
	private void massageOrderInvoiceDetails(YFSEnvironment env, Document indoc) throws XPathExpressionException {
		logger.beginTimer(
				"class: AmzVerifyRetInvDetToAddExtRefundInAmazon | method: verifyInvElgToAddExtRefund -- Starts");
		logger.info("class: AmzVerifyRetInvDetToAddExtRefundInAmazon | method: verifyInvElgToAddExtRefund -- Starts");
		Document outDocGetOrdList = null;

		NodeList nMerchInitOrdLine = AmzXMLUtil.getXpathNodes(indoc.getDocumentElement(),
				"OrderInvoiceDetail/OrderLine/Extn[@ExtnIsAmazonInitReturn='" + AmzCommonConstants.STR_VAL_N + "']");
		int iMerchInitOrderLine = nMerchInitOrdLine.getLength();
		logger.debug("iMerchInitOrderLine is: " + iMerchInitOrderLine);
		if (iMerchInitOrderLine > 0) {
			Element eleOrdInvDetList = indoc.getDocumentElement();
			String strOrderHeaderKey = AmzXMLUtil.getXpathAttribute(indoc.getDocumentElement(),
					"OrderInvoiceDetail/InvoiceHeader/Order/@OrderHeaderKey");
			logger.debug("strOrderHeaderKey is: " + strOrderHeaderKey);
			if (!YFCObject.isVoid(strOrderHeaderKey)) {
				Document getOrderListIndoc = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER);
				Element eleInOrder = getOrderListIndoc.getDocumentElement();
				eleInOrder.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY, strOrderHeaderKey);
				logger.debug("Input Document to getOrderList is: " + AmzXMLUtil.getString(getOrderListIndoc));
				outDocGetOrdList = AmzCommonUtil.invokeAPI(env,
						AmzCommonConstants.TEMPLATE_GET_ORDER_LIST_TO_ADD_EXT_RETURN,
						AmzCommonConstants.API_GET_ORDER_LIST, getOrderListIndoc);
				logger.debug("output Document of getOrderList is: " + AmzXMLUtil.getString(outDocGetOrdList));
			}
			NodeList nOrdInvDets = eleOrdInvDetList.getElementsByTagName(AmzLiterals.E_ORDER_INVOICE_DETAIL);
			int iOrdInvDets = nOrdInvDets.getLength();
			for (int j = 0; j < iOrdInvDets; j++) {
				Element eleOrderInvoiceDetail = (Element) nOrdInvDets.item(j);
				Element eleOrdInvOrderLine = AmzXMLUtil.getChildElement(eleOrderInvoiceDetail,
						AmzLiterals.E_ORDER_LINE);
				String sOrderLineKey = eleOrdInvOrderLine.getAttribute(AmzLiterals.A_ORDER_LINE_KEY);
				if (!YFCObject.isVoid(outDocGetOrdList)) {
					Element eleOrderLine = AmzXMLUtil.getXpathElement(outDocGetOrdList.getDocumentElement(),
							"Order/OrderLines/OrderLine[@OrderLineKey='" + sOrderLineKey + "']");
					if (!YFCObject.isVoid(eleOrderLine)) {
						Element eleDerivedFromOrderLine = AmzXMLUtil.getChildElement(eleOrderLine,
								AmzLiterals.E_DERIVED_FROM_ORDER_LINE);
						logger.debug("eleDerivedFromOrderLine is: " + AmzXMLUtil.getString(eleDerivedFromOrderLine));

						Element eleOutDerivedFromOrdLine = (Element) indoc.importNode(eleDerivedFromOrderLine, true);
						eleOrdInvOrderLine.appendChild(eleOutDerivedFromOrdLine);
						logger.debug("outDocGetOrdInvDetList is: " + AmzXMLUtil.getString(indoc));
					}

					
				}
			}
			verifyReturnInvToAddExtRefInAmazon(env, indoc);
		}
		logger.info("class: AmzVerifyRetInvDetToAddExtRefundInAmazon | method: verifyInvElgToAddExtRefund -- End");
		logger.endTimer("class: AmzVerifyRetInvDetToAddExtRefundInAmazon | method: verifyInvElgToAddExtRefund -- End");
	}

	/*
	 * This method verify the Return Invoice ON collection message, to verify the
	 * invoice as any Merchant initiated BWP lines
	 */
	public void verifyReturnInvToAddExtRefInAmazon(YFSEnvironment env, Document indoc)
			throws XPathExpressionException {
		logger.beginTimer(
				"class: AmzVerifyRetInvDetToAddExtRefundInAmazon | method: verifyReturnInvToAddExtRefInAmazon -- Starts");
		logger.info(
				"class: AmzVerifyRetInvDetToAddExtRefundInAmazon | method: verifyReturnInvToAddExtRefInAmazon -- Starts");
		Element eleOrdInvDetList = indoc.getDocumentElement();
		NodeList nOrdInvDets = eleOrdInvDetList.getElementsByTagName(AmzLiterals.E_ORDER_INVOICE_DETAIL);
		int iOrdInvDets = nOrdInvDets.getLength();
		for (int j = 0; j < iOrdInvDets; j++) {
			Element eleOrderInvoiceDetail = (Element) nOrdInvDets.item(j);
			String sExtnIsAmazonInitReturn = AmzXMLUtil.getXpathAttribute(eleOrderInvoiceDetail,
					"OrderLine/Extn/@ExtnIsAmazonInitReturn");
			logger.debug("sExtnIsAmazonInitReturn is: " + sExtnIsAmazonInitReturn);
			String strExtnIsPrimeElg = AmzXMLUtil.getXpathAttribute(eleOrderInvoiceDetail,
					"OrderLine/DerivedFromOrderLine/Extn/@ExtnIsPrimeEligible");
			logger.debug("strExtnIsPrimeElg is: " + strExtnIsPrimeElg);
			if (!YFCObject.isVoid(sExtnIsAmazonInitReturn) && !YFCObject.isVoid(strExtnIsPrimeElg)
					&& AmzCommonConstants.STR_VAL_Y.equalsIgnoreCase(strExtnIsPrimeElg)
					&& AmzCommonConstants.STR_VAL_N.equalsIgnoreCase(sExtnIsAmazonInitReturn)) {
				indoc.getDocumentElement().setAttribute(AmzLiterals.A_TASK_TYPE,
						AmzCommonConstants.STR_AMZ_ADD_EXTERNAL_REFUND);
				logger.debug("Input Document for AmzConnPostExternalReturnMsgToQ is: " + SCXmlUtil.getString(indoc));
				AmzCommonUtil.invokeService(env, AmzCommonConstants.SERVICE_AMZ_CONN_POST_EXTERNAL_RETURN_MSG_TO_Q,
						indoc);
				break;

			}
		}

		logger.info(
				"class: AmzVerifyRetInvDetToAddExtRefundInAmazon | method: verifyReturnInvToAddExtRefInAmazon -- End");
		logger.endTimer(
				"class: AmzVerifyRetInvDetToAddExtRefundInAmazon | method: verifyReturnInvToAddExtRefInAmazon -- End");
		
	}

	@Override
	public void setProperties(Properties inProps) {
		this.props = inProps;
	}
}
