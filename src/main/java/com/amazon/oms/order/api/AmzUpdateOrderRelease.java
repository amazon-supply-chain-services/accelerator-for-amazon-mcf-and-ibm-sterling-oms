package com.amazon.oms.order.api;

import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPathExpressionException;

import org.json.JSONException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzGetGenericProperty;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.yantra.interop.japi.YIFClientCreationException;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;

/*
 * This class invoke the OMS changeRelease api to update the amazonOrderOrder ID 
 * of MCF orderline for which amazon order created from OMS
 * Invoke the changeRelease to cancel the release.
 */
public class AmzUpdateOrderRelease {
	 final YFCLogCategory logger = YFCLogCategory.instance(AmzUpdateOrderRelease.class);

	/*
	 * This method invoke the OMS changeRelease api to update the amazonOrderOrder
	 * ID of MCF orderline for which amazon order created from OMS
	 */
	public  void updateOMSOrderRelease(YFSEnvironment env, Element eleOutOrder, String strAmazonOrderid,
			String strOrdReleaseKey) {

		logger.timer("class: AmzUpdateOrderRelease | method: updateOMSOrderRelease -- Starts");
		logger.info("class: AmzUpdateOrderRelease | method: updateOMSOrderRelease -- Starts");
		Document inChangeReleaseDoc = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER_RELEASE);
		Element eleInChgRelease = inChangeReleaseDoc.getDocumentElement();
		eleInChgRelease.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY,
				eleOutOrder.getAttribute(AmzLiterals.A_ORDER_HEADER_KEY));
		eleInChgRelease.setAttribute(AmzLiterals.A_ORDER_RELEASE_KEY, strOrdReleaseKey);
		eleInChgRelease.setAttribute(AmzLiterals.A_OVERRIDE, AmzCommonConstants.STR_VAL_Y);
		eleInChgRelease.setAttribute(AmzLiterals.A_SELECT_METHOD, AmzCommonConstants.STR_WAIT);
		Element eleOrderReleaseExtn = AmzXMLUtil.createChild(eleInChgRelease, AmzLiterals.E_EXTN);
		eleOrderReleaseExtn.setAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID, strAmazonOrderid);
		logger.debug("AmzUpdateOrderRelease inDoc is: " + AmzXMLUtil.getString(inChangeReleaseDoc));
		AmzCommonUtil.invokeService(env, AmzCommonConstants.SERVICE_AMZ_CHANGE_RELEASE, inChangeReleaseDoc);

		logger.info("class: AmzUpdateOrderRelease | method: updateOMSOrderRelease -- Ends");
		logger.timer("class: AmzUpdateOrderRelease | method: updateOMSOrderRelease -- Ends");

	}

	/*
	 * Invoke changeRelease api to cancel the release if amazon order creation
	 * failed for MCF lines.
	 */
	public  void invokeChangeReleaseToCancel(YFSEnvironment env, Element eleOutOrder, String strOrdReleaseKey,
			String output, List<String> amzCreateOrdElgOrderLineKey)
			throws RemoteException, JSONException, YIFClientCreationException, XPathExpressionException {

		logger.timer("class: AmzUpdateOrderRelease | method: InvokeChangeReleaseToCancel -- Starts");
		logger.info("class: AmzUpdateOrderRelease | method: InvokeChangeReleaseToCancel -- Starts");
		Document inDocGetGenrcProperty = AmzXMLUtil.createDocument(AmzLiterals.E_PROPERTIES);
		inDocGetGenrcProperty.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE,
				eleOutOrder.getAttribute(AmzLiterals.A_ENTERPRISE_CODE));
		Map<String, String> mapGenricProps = AmzGetGenericProperty.getGenericProperties(env, inDocGetGenrcProperty);
		Document inChangeReleaseDoc = AmzXMLUtil.createDocument(AmzLiterals.E_ORDER_RELEASE);
		Element eleInChgRelease = inChangeReleaseDoc.getDocumentElement();
		String strOrderNo = eleOutOrder.getAttribute(AmzLiterals.A_ORDER_NO);
		String strOhKey = eleOutOrder.getAttribute(AmzLiterals.A_ORDER_HEADER_KEY);
		eleInChgRelease.setAttribute(AmzLiterals.A_ORDER_HEADER_KEY, strOhKey);
		eleInChgRelease.setAttribute(AmzLiterals.A_ORDER_RELEASE_KEY, strOrdReleaseKey);
		eleInChgRelease.setAttribute(AmzLiterals.A_OVERRIDE, AmzCommonConstants.STR_VAL_Y);
		eleInChgRelease.setAttribute(AmzLiterals.A_SELECT_METHOD, AmzCommonConstants.STR_WAIT);

		Element eleOrderLines = AmzXMLUtil.createChild(eleInChgRelease, AmzLiterals.E_ORDER_LINES);
		int iOrderLineKeys = amzCreateOrdElgOrderLineKey.size();
		for (int i = 0; i < iOrderLineKeys; i++) {
			Element eleOrderLine = AmzXMLUtil.createChild(eleOrderLines, AmzLiterals.E_ORDER_LINE);
			eleOrderLine.setAttribute(AmzLiterals.A_ORDER_LINE_KEY, amzCreateOrdElgOrderLineKey.get(i));

			if (AmzCommonConstants.STR_VAL_Y
					.equalsIgnoreCase(mapGenricProps.get(AmzCommonConstants.STR_AMZCONN_MCF_BACKORDERCANCELLEDLINE))) {
				eleOrderLine.setAttribute(AmzLiterals.A_ACTION, AmzLiterals.STR_BACKORDER);
			} else {
				eleOrderLine.setAttribute(AmzLiterals.A_ACTION, AmzLiterals.STR_CANCEL);
			}

		}
		logger.debug(
				"AmzUpdateOrderRelease inDoc to cancel the release is: " + AmzXMLUtil.getString(inChangeReleaseDoc));
		AmzCommonUtil.invokeService(env, AmzCommonConstants.SERVICE_AMZ_CHANGE_RELEASE, inChangeReleaseDoc);
		logger.info("class: AmzUpdateOrderRelease | method: InvokeChangeReleaseToCancel -- Ends");
		logger.timer("class: AmzUpdateOrderRelease | method: InvokeChangeReleaseToCancel -- Ends");
		AmzUpdateOrdWithAmazonOrdInfo amzUpdateOrdWithAmazonOrdInfo = new AmzUpdateOrdWithAmazonOrdInfo();
		amzUpdateOrdWithAmazonOrdInfo.createNewException(env, strOrderNo, strOhKey, output);

	}

}
