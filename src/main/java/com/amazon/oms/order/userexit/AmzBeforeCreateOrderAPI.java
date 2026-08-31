package com.amazon.oms.order.userexit;

import java.util.Map;

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

public class AmzBeforeCreateOrderAPI {
	final YFCLogCategory logger = YFCLogCategory.instance(AmzBeforeCreateOrderAPI.class);
	Map<String, String> genricPropsMap = null;

	/**
	 * This method does required changes to the create order XML on create order
	 * API. By adding required attributes.
	 * 
	 * @param env
	 * @param inDoc
	 * @return inDoc
	 */
	public Document beforeCreateOrder(YFSEnvironment env, Document inDoc) throws YFSException{

		logger.beginTimer("AmzBeforeCreateOrderAPI:beforeCreateOrder:start");
		logger.info("Input XML to AmzBeforeCreateOrderAPI:beforeCreateOrder:::" + SCXmlUtil.getString(inDoc));

		try {

			Element rootEle = inDoc.getDocumentElement() ;
			String strEnterPriseCode = rootEle.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
			logger.debug("strEnterPriseCode is: " + strEnterPriseCode);
			Document inDocGetGenrcProperty = AmzXMLUtil.createDocument(AmzLiterals.E_PROPERTIES);
			inDocGetGenrcProperty.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterPriseCode);
			genricPropsMap = AmzGetGenericProperty.getGenericProperties(env, inDocGetGenrcProperty);

			boolean isFulfillmentInitializationOn = AmzCommonUtil.invokeCondition(env, strEnterPriseCode,
					"Is Order Fulfillment Initialization ON?",AmzCommonConstants.STR_ORDER_FULFILLMENT);
			logger.debug("isFulfillmentInitializationON is: " + isFulfillmentInitializationOn);
			boolean isFulfillmentRoutingOn = AmzCommonUtil.invokeCondition(env, strEnterPriseCode,
					"Is Order Fulfillment Routing ON?",AmzCommonConstants.STR_ORDER_FULFILLMENT);
			logger.debug("isFulfillmentInitializationON is: " + isFulfillmentRoutingOn);
			if (isFulfillmentInitializationOn || isFulfillmentRoutingOn) {
				Element eleOrderExtn = SCXmlUtil.getChildElement(rootEle, AmzLiterals.E_EXTN);
				String sExtnOrderCountry = eleOrderExtn.getAttribute(AmzLiterals.A_EXTN_ORDER_COUNTRY);
				logger.debug("sExtnOrderCountry is: " + sExtnOrderCountry);
				String sNode = genricPropsMap.get(AmzCommonConstants.PROP_AMZ_SHIP_NODE + sExtnOrderCountry);
				logger.debug("sNode is: " + sNode);

				Element eleOrderLines = SCXmlUtil.getChildElement(rootEle, AmzLiterals.E_ORDER_LINES);

				if (!YFCObject.isVoid(eleOrderLines)) {

					logger.debug("Element OrderLines is  :: "
							+ SCXmlUtil.getChildElement(rootEle, AmzLiterals.E_ORDER_LINES));

					Element eleOrderLineSourcingControls = null;
					Element eleOrderLineSourcingCntrl = null;

					NodeList nOrderLine = eleOrderLines.getElementsByTagName(AmzLiterals.E_ORDER_LINE);
					verifyandUpdateAmzElgLines(nOrderLine, sNode, eleOrderLineSourcingControls,
							eleOrderLineSourcingCntrl);

				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			YFSException yfse = new YFSException();
			yfse.setErrorCode("Exception in AmzBeforeCreateOrderAPI.beforeCreateOrder");
			yfse.setErrorDescription(e.getMessage());
			throw yfse;
		}
		logger.info("AmzBeforeCreateOrderAPI:beforeCreateOrder:end::" + SCXmlUtil.getString(inDoc));
		logger.endTimer("AmzBeforeCreateOrderAPI:beforeCreateOrder:end");

		return inDoc;
	}

	/*
	 * This method verify and update the ShipNode and OrderLineSourcingCntrl for
	 * Amazon Fulfillable lines
	 * 
	 * @param NodeList
	 * 
	 * @param string
	 * 
	 * @param Element
	 * 
	 * @param Element
	 */
	private void verifyandUpdateAmzElgLines(NodeList nOrderLine, String sNode, Element eleOrderLineSourcingControls,
			Element eleOrderLineSourcingCntrl) {
		logger.beginTimer("AmzBeforeCreateOrderAPI:verifyandUpdateAmzElgLines:start");
		logger.info("Input XML to AmzBeforeCreateOrderAPI:verifyandUpdateAmzElgLines:::");
		int iOrderLineLen = nOrderLine.getLength();
		for (int i = 0; i < iOrderLineLen; i++) {
			Element eleOrderLine = (Element) nOrderLine.item(i);
			Element eleOrderLineExtn = SCXmlUtil.getChildElement(eleOrderLine, AmzLiterals.E_EXTN);
			if (!YFCObject.isVoid(eleOrderLineExtn)) {
				String sExtnAmazonOrderId = eleOrderLineExtn.getAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID);
				String sExtnIsAmazonFulfillable = eleOrderLineExtn
						.getAttribute(AmzLiterals.A_EXTN_IS_AMAZON_FULFILLABLE);
				String sExtnIsPrimeEligible = eleOrderLineExtn.getAttribute(AmzLiterals.A_EXTN_IS_PRIME_ELIGIBLE);
				
				if (!YFCObject.isVoid(sExtnIsAmazonFulfillable)&& AmzLiterals.STR_VAL_Y.equalsIgnoreCase(sExtnIsAmazonFulfillable)) {
					eleOrderLine.setAttribute(genricPropsMap.get(AmzCommonConstants.PROP_SO_PIPELINE_CONDI_ATTR),genricPropsMap.get(AmzCommonConstants.PROP_SO_PIPELINE_CONDI_ATTR_VALUE));
                }
				if ((!YFCObject.isVoid(sExtnAmazonOrderId) && !YFCObject.isVoid(sExtnIsAmazonFulfillable)
						&& sExtnIsAmazonFulfillable.equalsIgnoreCase(AmzLiterals.STR_VAL_Y))
						|| (!YFCObject.isVoid(sExtnIsPrimeEligible)
								&& sExtnIsPrimeEligible.equalsIgnoreCase(AmzLiterals.STR_VAL_Y))) {
					eleOrderLine.setAttribute(AmzLiterals.A_SHIP_NODE, sNode);

					eleOrderLineSourcingControls = SCXmlUtil.createChild(eleOrderLine,
							AmzLiterals.E_ORDER_LINE_SOURCING_CONTROLS);
					eleOrderLineSourcingCntrl = SCXmlUtil.createChild(eleOrderLineSourcingControls,
							AmzLiterals.E_ORDER_LINE_SOURCING_CNTRL);
					eleOrderLineSourcingCntrl.setAttribute(AmzLiterals.A_INVENTORY_CHECK_CODE,
							AmzLiterals.STR_VAL_INFINITE_INV);
					eleOrderLineSourcingCntrl.setAttribute(AmzLiterals.A_NODE, sNode);
					eleOrderLineSourcingCntrl.setAttribute(AmzLiterals.A_SUPPRESS_SOURCING, AmzLiterals.STR_VAL_Y);
				}

				if (((!YFCObject.isVoid(sExtnIsAmazonFulfillable)) && (!YFCObject.isVoid(sExtnIsPrimeEligible)))
						&& ((sExtnIsAmazonFulfillable.equalsIgnoreCase(AmzLiterals.STR_VAL_Y))
								&& (sExtnIsPrimeEligible.equalsIgnoreCase(AmzLiterals.STR_VAL_N)))) {

					eleOrderLine.setAttribute(AmzLiterals.A_FULFILLMENT_TYPE,
							genricPropsMap.get(AmzCommonConstants.PROP_MCF_FULFILLMENT_TYPE));
				}
			}
		}
		logger.info("AmzBeforeCreateOrderAPI:verifyandUpdateAmzElgLines:end::");
		logger.endTimer("AmzBeforeCreateOrderAPI:verifyandUpdateAmzElgLines:end");
	}
	
}
