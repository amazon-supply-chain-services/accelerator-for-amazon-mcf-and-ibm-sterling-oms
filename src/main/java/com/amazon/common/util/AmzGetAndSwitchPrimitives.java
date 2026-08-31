package com.amazon.common.util;

import java.util.ArrayList;
import java.util.List;

import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.dom.YFCDocument;
import com.yantra.yfc.dom.YFCElement;
import com.yantra.yfc.dom.YFCNodeList;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSException;

public class AmzGetAndSwitchPrimitives {
	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzGetAndSwitchPrimitives.class);

	/*
	 * This method is used to switch primitive value.
	 * Input format: <Primitive Name="" Value="" EnterpriseCode=""/>
	 */
	public Document switchPrimitives(YFSEnvironment env, Document inputDoc) throws Exception {
		logger.info("class: AmzSwitchPrimitives | method: switchPrimitives -- Starts");
		if (inputDoc != null && inputDoc.getDocumentElement() != null) {
			String primitiveName = inputDoc.getDocumentElement().getAttribute(AmzCommonConstants.STR_NAME);
			String value = inputDoc.getDocumentElement().getAttribute(AmzCommonConstants.STR_VALUE);
			String enterpriseCode = inputDoc.getDocumentElement().getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
			validateInput(primitiveName, value);
			/* Set the value in Common Code here  */
			
			if(YFCObject.isVoid(enterpriseCode)) {
				enterpriseCode = AmzLiterals.STR_ORG_DEFAULT;
			}
			Document manageCommonCodeInput = SCXmlUtil.createDocument(AmzCommonConstants.STR_COMMON_CODE);
			manageCommonCodeInput.getDocumentElement().setAttribute(AmzLiterals.A_ACTION, "Manage");
			manageCommonCodeInput.getDocumentElement().setAttribute(AmzCommonConstants.STR_CODE_TYPE, AmzCommonConstants.STR_AMZCONN_PRIMITIVES);
			manageCommonCodeInput.getDocumentElement().setAttribute(AmzLiterals.A_ORGANIZATION_CODE, enterpriseCode);
			manageCommonCodeInput.getDocumentElement().setAttribute(AmzCommonConstants.STR_CODE_VALUE, primitiveName);
			manageCommonCodeInput.getDocumentElement().setAttribute(AmzCommonConstants.STR_CODE_SHORT_DESC, value);
			
			Document apiOutput = AmzCommonUtil.callAPI(env, manageCommonCodeInput, "manageCommonCode", null);
			
			logger.debug("manageCommonCode output document: "+ SCXmlUtil.getString(apiOutput));
			
		} else {
			throwException("Blank or Null Input");
		}

		logger.info("class: AmzSwitchPrimitives | method: switchPrimitives -- Ends");
		return YFCDocument.getDocumentFor("<ApiSuccess/>").getDocument();
	}

	/*
	 * This method is used to get the values of all primitives from Common Code here and return in the below form
	 * <Primitives> <Primitive Name="" Value="" EnterpriseCode=""/> </Primitives>
	 */ 
	public Document getPrimitives(YFSEnvironment env, Document inputDoc) throws Exception {
		logger.info("class: AmzSwitchPrimitives | method: getPrimitives -- Starts");
		
		Document outDoc = SCXmlUtil.createDocument("Primitives");
		
		
		String enterpriseCode = inputDoc.getDocumentElement().getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
		if(YFCObject.isVoid(enterpriseCode)) {
			enterpriseCode = AmzLiterals.STR_ORG_DEFAULT;
		}
		Document commonCodeInput = SCXmlUtil.createDocument(AmzCommonConstants.STR_COMMON_CODE);
		commonCodeInput.getDocumentElement().setAttribute(AmzCommonConstants.STR_CODE_TYPE, AmzCommonConstants.STR_AMZCONN_PRIMITIVES);

		Document getCommonCodeListOutDoc = AmzCommonUtil.callAPI(env, commonCodeInput, AmzCommonConstants.GET_COMMON_CODE_LIST, null);

		List<String> amzUniquePrimitiveCodeValueList = new ArrayList<>();
		
		YFCElement commonCodeElement = YFCDocument.getDocumentFor(getCommonCodeListOutDoc).getDocumentElement();
		YFCNodeList<YFCElement> commonCodeList = commonCodeElement.getElementsByTagName(AmzCommonConstants.STR_COMMON_CODE);
		for (YFCElement tempElement : commonCodeList) {
			String codeValue = tempElement.getAttribute(AmzCommonConstants.STR_CODE_VALUE);			
			if (!amzUniquePrimitiveCodeValueList.contains(codeValue)) {
				amzUniquePrimitiveCodeValueList.add(codeValue);
			}
		}
		logger.debug("getCommonCodeList output document: "+ SCXmlUtil.getString(outDoc));
		
		returnPrimitiveProperties(getCommonCodeListOutDoc, enterpriseCode, outDoc,
				amzUniquePrimitiveCodeValueList);
		
		logger.info("class: AmzSwitchPrimitives | method: getPrimitives -- Ends");
		return outDoc;
	}

	/*
	 * This method verify if the codevalue CodeLongDescription present for the given
	 * EnterpriseCode, return the codevalue and CodeLongDescription of given
	 * Organization if not null or else return the codevalue and CodeLongDescription
	 * of Default Org
	 */
	private static void returnPrimitiveProperties(Document getCommonCodeListOutDoc, String strEnterpriceCode,
			Document outDoc, List<String> amzUniquePrimitiveCodeValueList)
			throws XPathExpressionException {
		logger.timer("class: AmzSwitchPrimitives | method: returnPrimitiveProperties -- Starts");
		logger.info("class: AmzSwitchPrimitives | method: returnPrimitiveProperties -- Starts");
		logger.debug(
				"Input Document for class:AmzSwitchPrimitives | method:returnPrimitiveProperties to return generic properties"
						+ AmzXMLUtil.getString(getCommonCodeListOutDoc));

		Element elePrimitives = outDoc.getDocumentElement();
		int amzUniquePrimitiveCodeValueLen = amzUniquePrimitiveCodeValueList.size();
		for (int j = 0; j < amzUniquePrimitiveCodeValueLen; j++) {
			String sCodeValue = amzUniquePrimitiveCodeValueList.get(j);
			Element eleCommonCode = AmzXMLUtil.getXpathElement(getCommonCodeListOutDoc.getDocumentElement(),
					"CommonCode[@OrganizationCode='" + strEnterpriceCode + "' and @CodeValue='" + sCodeValue + "']");
			if (!YFCObject.isVoid(eleCommonCode)) {
				String strShortDesc = eleCommonCode.getAttribute(AmzLiterals.A_CODE_SHORT_DESCRIPTION);
				if (!YFCObject.isVoid(strShortDesc)) {
					Element elePrimitive = SCXmlUtil.createChild(elePrimitives, "Primitive");
					elePrimitive.setAttribute(AmzLiterals.A_ENTERPRISE_CODE, strEnterpriceCode);
					elePrimitive.setAttribute(AmzCommonConstants.STR_NAME, sCodeValue);
					elePrimitive.setAttribute(AmzCommonConstants.STR_VALUE, strShortDesc);
				}
			} else {
				Element eleDefaultOrgCommonCode = AmzXMLUtil
						.getXpathElement(getCommonCodeListOutDoc.getDocumentElement(), "CommonCode[@OrganizationCode='"
								+ AmzCommonConstants.STR_DEFAULT + "' and @CodeValue='" + sCodeValue + "']");
				if (!YFCObject.isVoid(eleDefaultOrgCommonCode)) {
					String strDefaultOrgShortDesc = eleDefaultOrgCommonCode
							.getAttribute(AmzLiterals.A_CODE_SHORT_DESCRIPTION);
					logger.debug("strDefaultOrgLongDesc is: " + strDefaultOrgShortDesc);

					if (!YFCObject.isVoid(strDefaultOrgShortDesc)) {
						Element elePrimitive = SCXmlUtil.createChild(elePrimitives, "Primitive");
						elePrimitive.setAttribute(AmzLiterals.A_ENTERPRISE_CODE, AmzCommonConstants.STR_DEFAULT);
						elePrimitive.setAttribute(AmzCommonConstants.STR_NAME, sCodeValue);
						elePrimitive.setAttribute(AmzCommonConstants.STR_VALUE, strDefaultOrgShortDesc);
					}
				}
			}
		}

		logger.debug("class: AmzSwitchPrimitives | method: returnPrimitiveProperties: outDoc is: "
				+ SCXmlUtil.getString(outDoc));
		logger.info("class: AmzSwitchPrimitives | method: returnPrimitiveProperties -- Ends");
		logger.timer("class: AmzSwitchPrimitives | method: returnPrimitiveProperties -- Ends");
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
	
	private void throwException(String msg) throws YFSException {
		YFSException ex = new YFSException();
		ex.setErrorCode("PRIMITIVE_VALIDATION_ERROR");
		ex.setErrorDescription(msg);
		throw ex;
	}
}
