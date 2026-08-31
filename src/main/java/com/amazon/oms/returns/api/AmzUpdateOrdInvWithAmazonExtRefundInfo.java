package com.amazon.oms.returns.api;

import java.rmi.RemoteException;
import java.util.Properties;

import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.json.JSONException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.ibm.sterling.afc.jsonutil.PLTJSONUtils;
import com.yantra.interop.japi.YIFClientCreationException;
import com.yantra.interop.japi.YIFCustomApi;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

/*
 * This class will get invoked from AmzConnSyncExternalReturnToAmazonAsync service
 * After Adding a external refund in amazon for merchant initiated and refund invoiced BWP lines,
 * This class will stamp the amazon refundid in the OMS in orderInvoice table as ExtnAmazonRefundId.
 */

public class AmzUpdateOrdInvWithAmazonExtRefundInfo implements YIFCustomApi {
	private Properties props;
	final YFCLogCategory logger = YFCLogCategory.instance(AmzUpdateOrdInvWithAmazonExtRefundInfo.class);

	/*
	 * This Method will get invoked from AmzExternalReturnRefundInAmazon class To
	 * Stamp the Amazon external refundid in OMS in OrderInvoice table as
	 * ExtnAmazonRefundId.
	 */
	public void updateordInvWithAmazonExtRefundId(YFSEnvironment env, Document indoc, String output)
			throws XPathExpressionException, YFSException, RemoteException, YIFClientCreationException, JSONException {
		logger.beginTimer(
				"class: AmzUpdateOrdInvWithAmazonExtRefundInfo | method: updateordInvWithAmazonExtRefundId -- Starts");
		logger.info(
				"class: AmzUpdateOrdInvWithAmazonExtRefundInfo | method: updateordInvWithAmazonExtRefundId -- Starts");

		String strInvoiceNo = AmzXMLUtil.getXpathAttribute(indoc.getDocumentElement(),
				"OrderInvoiceDetail/InvoiceHeader/@InvoiceNo");
		logger.debug("strInvoiceNo is: " + strInvoiceNo);
		String strInvoiceKey = AmzXMLUtil.getXpathAttribute(indoc.getDocumentElement(),
				"OrderInvoiceDetail/InvoiceHeader/@OrderInvoiceKey");
		logger.debug("strInvoiceKey is: " + strInvoiceKey);

		String strRefundId = null;
		Document amzUpdOrdOutDoc = PLTJSONUtils.getXmlFromJSON(output, "Root");
		logger.debug("amzUpdOrdOutDoc is: " + AmzXMLUtil.getString(amzUpdOrdOutDoc));
		if (!YFCObject.isVoid(amzUpdOrdOutDoc)) {
			NodeList nRefundDetails = AmzXMLUtil.getXpathNodes(amzUpdOrdOutDoc.getDocumentElement(),
					"data/updateOrder/order/refunds/details");
			int idetails = nRefundDetails.getLength();
			for (int i = 0; i < idetails; i++) {
				Element eleDetails = (Element) nRefundDetails.item(i);
				String strAliasId = AmzXMLUtil.getXpathAttribute(eleDetails, "aliases/@aliasId");
				logger.debug("strAliasId is: " + strAliasId);

				if (!YFCObject.isVoid(strInvoiceNo) && !YFCObject.isVoid(strAliasId)
						&& strInvoiceNo.equalsIgnoreCase(strAliasId)) {
					strRefundId = eleDetails.getAttribute(AmzLiterals.A_JS_ID);
				}
			}
			if (!YFCObject.isVoid(strInvoiceKey) && !YFCObject.isVoid(strRefundId)) {
				Document inDocChgOrdInv = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER_INVOICE);
				Element eleOrdInv = inDocChgOrdInv.getDocumentElement();
				eleOrdInv.setAttribute(AmzLiterals.A_ORDER_INVOICE_KEY, strInvoiceKey);
				Element eleOrdInvExtn = AmzXMLUtil.createChild(eleOrdInv, AmzLiterals.E_EXTN);
				eleOrdInvExtn.setAttribute(AmzLiterals.A_EXTN_AMAZON_REFUND_ID, strRefundId);
				logger.debug("input document to changeOrderInvoice is: " + AmzXMLUtil.getString(inDocChgOrdInv));

				AmzCommonUtil.callAPI(env, inDocChgOrdInv, AmzCommonConstants.API_CHANGE_ORDER_INVOICE, null);
			}
		}
		logger.info("class: AmzUpdateOrdInvWithAmazonExtRefundInfo | method: updateordInvWithAmazonExtRefundId -- End");
		logger.endTimer(
				"class: AmzUpdateOrdInvWithAmazonExtRefundInfo | method: updateordInvWithAmazonExtRefundId -- End");
	}

	public void setProperties(Properties inProps) {
		this.props = inProps;
	}
}
