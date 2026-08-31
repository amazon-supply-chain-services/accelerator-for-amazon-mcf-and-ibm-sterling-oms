package com.amazon.common.util;

import org.w3c.dom.Document;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.dom.YFCDocument;
import com.yantra.yfc.dom.YFCElement;
import com.yantra.yfc.dom.YFCNodeList;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;

/*
 * This class is getting used to for Enterprise - BusinessProductID cross reference. 
 */

public class AmzEnterpriseBPIDXRefUtil {
	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzEnterpriseBPIDXRefUtil.class);

	/*
	 * This method used to get EnterpriseCode based on BusinessProductID. 
	 */
	public static String getEnterpriseCode(YFSEnvironment env, String targetID) throws Exception {
		logger.info("class: AmzEnterpriseBPIDXRefUtil | method: getEnterpriseCode -- Starts");
		String orgCode = "";
		Document commonCodeInput = SCXmlUtil.createDocument(AmzCommonConstants.STR_COMMON_CODE);
		commonCodeInput.getDocumentElement().setAttribute(AmzCommonConstants.STR_CODE_TYPE, AmzCommonConstants.STR_AMZCONN_ENT_BPID_XREF);

		Document apiOutput = AmzCommonUtil.callAPI(env, commonCodeInput, AmzCommonConstants.GET_COMMON_CODE_LIST, null);

		YFCElement commonCodeElement = YFCDocument.getDocumentFor(apiOutput).getDocumentElement();
		YFCNodeList<YFCElement> commonCodeList = commonCodeElement.getElementsByTagName(AmzCommonConstants.STR_COMMON_CODE);
		for (YFCElement tempElement : commonCodeList) {
			String codeValue = tempElement.getAttribute(AmzCommonConstants.STR_CODE_VALUE);
			if (targetID.equals(codeValue)) {
				orgCode = tempElement.getAttribute(AmzCommonConstants.STR_CODE_LONG_DESC);
			}

		}
		logger.debug("getCommonCodeList output document: "+ SCXmlUtil.getString(apiOutput));
		
		logger.info("class: AmzEnterpriseBPIDXRefUtil | method: getEnterpriseCode -- Ends");
		return orgCode;
	}

	/*
	 * This method used to get MarketPlaceID based on BusinessProductID. 
	 */
	public static String getMarketPlaceID(YFSEnvironment env, String targetID) throws Exception {
		logger.info("class: AmzEnterpriseBPIDXRefUtil | method: getMarketPlaceID -- Starts");
		String marketPlaceID = "";
		Document commonCodeInput = SCXmlUtil.createDocument(AmzCommonConstants.STR_COMMON_CODE);
		commonCodeInput.getDocumentElement().setAttribute(AmzCommonConstants.STR_CODE_TYPE, AmzCommonConstants.STR_AMZCONN_ENT_BPID_XREF);

		Document apiOutput = AmzCommonUtil.callAPI(env, commonCodeInput, AmzCommonConstants.GET_COMMON_CODE_LIST, null);

		YFCElement commonCodeElement = YFCDocument.getDocumentFor(apiOutput).getDocumentElement();
		YFCNodeList<YFCElement> commonCodeList = commonCodeElement.getElementsByTagName(AmzCommonConstants.STR_COMMON_CODE);
		for (YFCElement tempElement : commonCodeList) {
			String codeValue = tempElement.getAttribute(AmzCommonConstants.STR_CODE_VALUE);
			if (targetID.equals(codeValue)) {
				marketPlaceID = tempElement.getAttribute(AmzCommonConstants.STR_CODE_SHORT_DESC);
			}

		}
		logger.debug("getCommonCodeList output document: "+ SCXmlUtil.getString(apiOutput));
		
		logger.info("class: AmzEnterpriseBPIDXRefUtil | method: getMarketPlaceID -- Ends");
		return marketPlaceID;
	}
	
}
