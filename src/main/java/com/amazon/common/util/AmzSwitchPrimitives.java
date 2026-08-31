package com.amazon.common.util;

import java.rmi.RemoteException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.interop.japi.YIFClientCreationException;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.dom.YFCDocument;
import com.yantra.yfc.dom.YFCElement;
import com.yantra.yfc.dom.YFCNodeList;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

public class AmzSwitchPrimitives {
	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzSwitchPrimitives.class);

	/*
	 * Input format: <Primitive Name="" Value=""/>
	 */
	public Document switchPrimitives(YFSEnvironment env, Document inputDoc) throws Exception {
		logger.info("class: AmzSwitchPrimitives | method: switchPrimitives -- Starts");
		if (inputDoc != null && inputDoc.getDocumentElement() != null) {
			String primitiveName = inputDoc.getDocumentElement().getAttribute(AmzCommonConstants.STR_NAME);
			String value = inputDoc.getDocumentElement().getAttribute(AmzCommonConstants.STR_VALUE);
			validateInput(primitiveName, value);
			/* Set the value in Common Code here  */
			String orgCode = inputDoc.getDocumentElement().getAttribute(AmzCommonConstants.STR_ORG_CODE);
			if(YFCObject.isVoid(orgCode)) {
				orgCode = "DEFAULT";
			}
			Document manageCommonCodeInput = SCXmlUtil.createDocument(AmzCommonConstants.STR_COMMON_CODE);
			manageCommonCodeInput.getDocumentElement().setAttribute("Action", "Manage");
			manageCommonCodeInput.getDocumentElement().setAttribute(AmzCommonConstants.STR_CODE_TYPE, AmzCommonConstants.STR_AMZ_PRIMITIVES);
			manageCommonCodeInput.getDocumentElement().setAttribute(AmzCommonConstants.STR_CODE_VALUE, primitiveName);
			manageCommonCodeInput.getDocumentElement().setAttribute(AmzCommonConstants.STR_CODE_SHORT_DESC, value);
			manageCommonCodeInput.getDocumentElement().setAttribute(AmzCommonConstants.STR_ORG_CODE, orgCode);
			
			Document apiOutput = AmzCommonUtil.callAPI(env, manageCommonCodeInput, "manageCommonCode", null);
			
			logger.debug("manageCommonCode output document: "+ SCXmlUtil.getString(apiOutput));
			
		} else {
			throwException("Blank or Null Input");
		}

		logger.info("class: AmzSwitchPrimitives | method: switchPrimitives -- Ends");
		return YFCDocument.getDocumentFor("<ApiSuccess/>").getDocument();
	}

	public Document getPrimitives(YFSEnvironment env, Document inputDoc) throws Exception {
		logger.info("class: AmzSwitchPrimitives | method: getPrimitives -- Starts");
		/*
		 * Get the value of all primitives from Common Code here and return in the below form
		 * <Primitives> <Primitive Name="" Value=""/> </Primitives>
		 */ 
		Document outDoc = SCXmlUtil.createDocument("Primitives");
		Element elePrimitives = outDoc.getDocumentElement();
		
		Document commonCodeInput = SCXmlUtil.createDocument(AmzCommonConstants.STR_COMMON_CODE);
		commonCodeInput.getDocumentElement().setAttribute(AmzCommonConstants.STR_CODE_TYPE, AmzCommonConstants.STR_AMZ_PRIMITIVES);

		Document apiOutput = AmzCommonUtil.callAPI(env, commonCodeInput, AmzCommonConstants.GET_COMMON_CODE_LIST, null);

		YFCElement commonCodeElement = YFCDocument.getDocumentFor(apiOutput).getDocumentElement();
		YFCNodeList<YFCElement> commonCodeList = commonCodeElement.getElementsByTagName(AmzCommonConstants.STR_COMMON_CODE);
		for (YFCElement tempElement : commonCodeList) {
			String codeValue = tempElement.getAttribute(AmzCommonConstants.STR_CODE_VALUE);
			String codeShortDescription = tempElement.getAttribute(AmzCommonConstants.STR_CODE_SHORT_DESC);
			Element elePrimitive = SCXmlUtil.createChild(elePrimitives, "Primitive");
			elePrimitive.setAttribute(AmzCommonConstants.STR_NAME, codeValue);
			elePrimitive.setAttribute(AmzCommonConstants.STR_VALUE, codeShortDescription);
		}
		logger.debug("getCommonCodeList output document: "+ SCXmlUtil.getString(outDoc));
		
		logger.info("class: AmzSwitchPrimitives | method: getPrimitives -- Ends");
		return outDoc;
	}

	private void validateInput(String primitiveName, String value) {
		if (YFCObject.isVoid(primitiveName)) {
			throwException("Blank or Null Primitive Name");
		}
		if (YFCObject.isVoid(value)) {
			throwException("Blank or Null Primitive Value");
		}
		if (!"Y".equals(value) && !"N".equals(value)) {
			throwException("Primitive Value can only be Y or N");
		}
	}
	
	public String getCommonCodeList(YFSEnvironment env, String primitiveName)
			throws YFSException, RemoteException, YIFClientCreationException {
		logger.beginTimer("class: AmzSwitchPrimitives | method: getCommonCodeList -- Starts");
		logger.info("class: AmzSwitchPrimitives | method: getCommonCodeList -- Starts");
		String shipNode = "";
		Document commonCodeInput = SCXmlUtil.createDocument("CommonCode");
		commonCodeInput.getDocumentElement().setAttribute("CodeType", "AMZ_PRIMITIVES");

		Document apiOutput = AmzCommonUtil.callAPI(env, commonCodeInput, "getCommonCodeList", null);

		YFCElement commonCodeElement = YFCDocument.getDocumentFor(apiOutput).getDocumentElement();
		YFCNodeList<YFCElement> commonCodeList = commonCodeElement.getElementsByTagName("CommonCode");
		for (YFCElement tempElement : commonCodeList) {
			String codeValue = tempElement.getAttribute("CodeValue");
			if (primitiveName.equals(codeValue)) {
				shipNode = tempElement.getAttribute("CodeShortDescription");
			}
		}
		logger.info("class: AmzSwitchPrimitives | method: getCommonCodeList -- Ends");
		logger.endTimer("class: AmzSwitchPrimitives | method: getCommonCodeList -- Ends");
		return shipNode;
	}
	

	private void throwException(String msg) throws YFSException {
		YFSException ex = new YFSException();
		ex.setErrorCode("PRIMITIVE_VALIDATION_ERROR");
		ex.setErrorDescription(msg);
		throw ex;
	}
}
