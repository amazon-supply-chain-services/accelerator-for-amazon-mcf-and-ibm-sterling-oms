package com.amazon.common.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.core.YFSSystem;
import com.yantra.yfs.japi.YFSEnvironment;

/*
 * This Class return the generic CommonCode for Specific EnterpriseCode and
 *  integration properties for Specific EnterpriseCode as Map
 */
public class AmzGetGenericProperty {
	static final YFCLogCategory logger = YFCLogCategory.instance(AmzGetGenericProperty.class);

	/*
	 * The Input to this method is: <Properties EnterpriseCode="DEFAULT"/>
	 * 
	 * This Method invoke the getCommonCodeList with the below input <CommonCode
	 * CodeType="AMZCONN_GENRL_PROPS" OrganizationCode="DEFAULT"/>
	 * 
	 * and return CodeValue and CodeLongDescription as Map Key and value
	 * 
	 */
	public static Map<String, String> getGenericProperties(YFSEnvironment env, Document indoc)
			throws XPathExpressionException {
		logger.timer("class: AmzGetGenericProperty | method: getGenericProperties -- Starts");
		logger.info("class: AmzGetGenericProperty | method: getGenericProperties -- Starts");
		logger.debug(
				"Input Document for class:AmzGetGenericProperty | method:getGenericProperties to get generic properties"
						+ AmzXMLUtil.getString(indoc));
		HashMap<String, String> genricCommonCodeMap = new HashMap<>();

		String strEnterpriceCode = null;
		if (!YFCObject.isVoid(indoc)) {
			Element eleProperties = indoc.getDocumentElement();
			strEnterpriceCode = eleProperties.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
		}
		if (YFCObject.isVoid(strEnterpriceCode)) {
			strEnterpriceCode = AmzCommonConstants.STR_DEFAULT;
		}
		logger.debug("EnterpriceCode for getGenericProperties is: " + strEnterpriceCode);
		Document inDocGetCommonCodeList = AmzXMLUtil.createDocument(AmzLiterals.E_COMMON_CODE);
		Element eleCommonCodeIn = inDocGetCommonCodeList.getDocumentElement();

		// eleCommonCodeIn.setAttribute(AmzLiterals.A_ORGANIZATION_CODE, strEnterpriceCode);

		eleCommonCodeIn.setAttribute(AmzLiterals.A_CODE_TYPE, AmzCommonConstants.STR_AMZCONN_GENRL_PROPS);
		logger.debug("Input Document for getCommonCodeList to get generic properties"
				+ AmzXMLUtil.getString(inDocGetCommonCodeList));
		Document getCommonCodeListOutDoc = AmzCommonUtil.invoke(env, AmzCommonConstants.GET_COMMON_CODE_LIST,
				inDocGetCommonCodeList);
		logger.debug("Input Document for getCommonCodeList to get generic properties"
				+ AmzXMLUtil.getString(getCommonCodeListOutDoc));
		if (!YFCObject.isVoid(getCommonCodeListOutDoc)) {
			NodeList nCommonCode = getCommonCodeListOutDoc.getDocumentElement()
					.getElementsByTagName(AmzLiterals.E_COMMON_CODE);
			int iCommonCodeLen = nCommonCode.getLength();
			List<String> amzUniqueGenricCodeValueList = new ArrayList<>();
			for (int i = 0; i < iCommonCodeLen; i++) {
				Element eleCommonCode = (Element) nCommonCode.item(i);
				String strOrgCode = eleCommonCode.getAttribute(AmzLiterals.A_ORGANIZATION_CODE);
				logger.debug("strOrgCode is: " + strOrgCode);
				String strCodeValue = eleCommonCode.getAttribute(AmzLiterals.A_CODE_VALUE);
				logger.debug("strCodeValue is: " + strCodeValue);
				if (!amzUniqueGenricCodeValueList.contains(strCodeValue)) {
					amzUniqueGenricCodeValueList.add(strCodeValue);
				}

			}
			logger.debug("amzUniqueGenricCodeValueList is: " + amzUniqueGenricCodeValueList);

			returnGenricProperties(getCommonCodeListOutDoc, strEnterpriceCode, genricCommonCodeMap,
					amzUniqueGenricCodeValueList);

		}
		logger.info("class: AmzGetGenericProperty | method: getGenericProperties -- Ends");
		logger.timer("class: AmzGetGenericProperty | method: getGenericProperties -- Ends");
		return genricCommonCodeMap;

	}

	/*
	 * This method verify if the codevalue CodeLongDescription present for the given
	 * EnterpriseCode, return the codevalue and CodeLongDescription of given
	 * Organization if not null or else return the codevalue and CodeLongDescription
	 * of Default Org
	 */
	private static void returnGenricProperties(Document getCommonCodeListOutDoc, String strEnterpriceCode,
			HashMap<String, String> genricCommonCodeMap, List<String> amzUniqueGenricCodeValueList)
			throws XPathExpressionException {
		logger.timer("class: AmzGetGenericProperty | method: returnGenricProperties -- Starts");
		logger.info("class: AmzGetGenericProperty | method: returnGenricProperties -- Starts");
		logger.debug(
				"Input Document for class:AmzGetGenericProperty | method:returnGenricProperties to return generic properties"
						+ AmzXMLUtil.getString(getCommonCodeListOutDoc));

		int amzUniqueGenricCodeValueLen = amzUniqueGenricCodeValueList.size();
		for (int j = 0; j < amzUniqueGenricCodeValueLen; j++) {
			String sCodeValue = amzUniqueGenricCodeValueList.get(j);
			Element eleCommonCode = AmzXMLUtil.getXpathElement(getCommonCodeListOutDoc.getDocumentElement(),
					"CommonCode[@OrganizationCode='" + strEnterpriceCode + "' and @CodeValue='" + sCodeValue + "']");
			if (!YFCObject.isVoid(eleCommonCode)) {
				String strLongDesc = eleCommonCode.getAttribute(AmzLiterals.A_CODE_LONG_DESCRIPTION);
				logger.debug("strLongDesc is: " + strLongDesc);
				if (!YFCObject.isVoid(strLongDesc)) {
					genricCommonCodeMap.put(sCodeValue, strLongDesc);
				}
			} else {
				Element eleDefaultOrgCommonCode = AmzXMLUtil
						.getXpathElement(getCommonCodeListOutDoc.getDocumentElement(), "CommonCode[@OrganizationCode='"
								+ AmzCommonConstants.STR_DEFAULT + "' and @CodeValue='" + sCodeValue + "']");
				if (!YFCObject.isVoid(eleDefaultOrgCommonCode)) {
					String strDefaultOrgLongDesc = eleDefaultOrgCommonCode
							.getAttribute(AmzLiterals.A_CODE_LONG_DESCRIPTION);
					logger.debug("strDefaultOrgLongDesc is: " + strDefaultOrgLongDesc);

					if (!YFCObject.isVoid(strDefaultOrgLongDesc)) {
						genricCommonCodeMap.put(sCodeValue, strDefaultOrgLongDesc);
					}
				}
			}
		}

		logger.debug("class: AmzGetGenericProperty | method: returnGenricProperties: genricCommonCodeMap is: "
				+ genricCommonCodeMap);
		logger.info("class: AmzGetGenericProperty | method: returnGenricProperties -- Ends");
		logger.timer("class: AmzGetGenericProperty | method: returnGenricProperties -- Ends");
	}

	/*
	 * The Input to this method is: <Properties EnterpriseCode="DEFAULT"/>
	 * 
	 * This Method get all BWP api integration the property name and value as Map
	 * key and Value.
	 */
	public static Map<String, String> getBWPIntegProperties(Document inDoc) {
		logger.timer("class: AmzGetGenericProperty | method: getBWPIntegProperties -- Starts");
		logger.info("class: AmzGetGenericProperty | method: getBWPIntegProperties -- Starts");
		logger.debug(
				"Input Document for class:AmzGetGenericProperty | method:getBWPIntegProperties to get generic properties"
						+ AmzXMLUtil.getString(inDoc));
		HashMap<String, String> bwpIntegProps = new HashMap<>();

		String strEnterpriceCode = null;
		if (!YFCObject.isVoid(inDoc)) {
			Element eleProperties = inDoc.getDocumentElement();
			strEnterpriceCode = eleProperties.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
		}
		if (YFCObject.isVoid(strEnterpriceCode)) {
			strEnterpriceCode = AmzCommonConstants.STR_DEFAULT;
		}
		logger.debug("strEnterpriceCode is: " + strEnterpriceCode);

		ArrayList<String> bwpIntegPropsList = new ArrayList<>();
		bwpIntegPropsList.add(AmzCommonConstants.AMZ_CLIENT_ID);
		bwpIntegPropsList.add(AmzCommonConstants.AMZ_CLIENT_SECRET);
		bwpIntegPropsList.add(AmzCommonConstants.AMZ_TOKEN_URL);
		bwpIntegPropsList.add(AmzCommonConstants.AMZ_GRANT_TYPE);
		bwpIntegPropsList.add(AmzCommonConstants.AMZ_TIME_OUT);
		bwpIntegPropsList.add(AmzCommonConstants.AMZ_TARGETID);
		bwpIntegPropsList.add(AmzCommonConstants.AMZ_API_ACCESS_KEY);
		bwpIntegPropsList.add(AmzCommonConstants.AMZ_API_VERSION);
		bwpIntegPropsList.add(AmzCommonConstants.AMZ_POST_URL);

		int ibwpIntegPropsListLen = bwpIntegPropsList.size();
		for (int i = 0; i < ibwpIntegPropsListLen; i++) {
			String sPropertyName = bwpIntegPropsList.get(i);
			logger.debug("sPropertyName is: " + sPropertyName);
			String sPropertyNameEnt = String.format("%s.%s", sPropertyName, strEnterpriceCode);
			logger.debug("sPropertyNameEnt is: " + sPropertyNameEnt);
			String propValue = YFSSystem.getProperty(sPropertyNameEnt);
			logger.debug("propValue is: " + propValue);
			if (!YFCObject.isVoid(propValue)) {
				bwpIntegProps.put(sPropertyName, propValue);
			} else {
				String sDefaultOrgPropertyNameEnt = String.format("%s.%s", sPropertyName,
						AmzCommonConstants.STR_DEFAULT);
				logger.debug("sDefaultOrgPropertyNameEnt is: " + sDefaultOrgPropertyNameEnt);
				String defaultPropsValue = YFSSystem.getProperty(sDefaultOrgPropertyNameEnt);
				logger.debug("defaultPropsValue is: " + defaultPropsValue);
				if (!YFCObject.isVoid(defaultPropsValue)) {
					bwpIntegProps.put(sPropertyName, defaultPropsValue);
				}
			}

		}
		logger.debug("bwpIntegProps is: " + bwpIntegProps);
		logger.info("class: AmzGetGenericProperty | method: getBWPIntegProperties -- Ends");
		logger.timer("class: AmzGetGenericProperty | method: getBWPIntegProperties -- Ends");
		return bwpIntegProps;
	}

	/*
	 * The Input to this method is: <Properties EnterpriseCode="DEFAULT"/>
	 * 
	 * This Method get all SP api integration the property name and value as Map key
	 * and Value.
	 */
	public static Map<String, String> getSPIntegProperties(Document inDoc) {
		logger.timer("class: AmzGetGenericProperty | method: getSPIntegProperties -- Starts");
		logger.info("class: AmzGetGenericProperty | method: getSPIntegProperties -- Starts");
		logger.debug(
				"Input Document for class:AmzGetGenericProperty | method:getSPIntegProperties to get generic properties"
						+ AmzXMLUtil.getString(inDoc));
		HashMap<String, String> spApiIntegProps = new HashMap<>();

		String strEnterpriceCode = null;
		if (!YFCObject.isVoid(inDoc)) {
			Element eleProperties = inDoc.getDocumentElement();
			strEnterpriceCode = eleProperties.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
		}
		if (YFCObject.isVoid(strEnterpriceCode)) {
			strEnterpriceCode = AmzCommonConstants.STR_DEFAULT;
		}
		logger.debug("strEnterpriceCode is: " + strEnterpriceCode);

		ArrayList<String> spIntegPropsList = new ArrayList<>();
		spIntegPropsList.add(AmzCommonConstants.SP_CLIENT_ID);
		spIntegPropsList.add(AmzCommonConstants.SP_CLIENT_SECRET);
		spIntegPropsList.add(AmzCommonConstants.SP_TOKEN_URL);
		spIntegPropsList.add(AmzCommonConstants.SP_GRANT_TYPE);
		spIntegPropsList.add(AmzCommonConstants.AMZ_TIME_OUT);
		spIntegPropsList.add(AmzCommonConstants.SP_GET_URL);
		spIntegPropsList.add(AmzCommonConstants.SP_REFRESH_TOKEN);
		spIntegPropsList.add(AmzCommonConstants.SP_GET_V2OUTBOUND_URL);
		spIntegPropsList.add(AmzCommonConstants.SP_MCF_GET_FULFILLMENT_ORDER_URL);
		spIntegPropsList.add(AmzCommonConstants.SP_MCF_LIST_FULFILLMENT_ORDERS_URL);

		int ibwpIntegPropsListLen = spIntegPropsList.size();
		for (int i = 0; i < ibwpIntegPropsListLen; i++) {
			String sPropertyName = spIntegPropsList.get(i);
			logger.debug("sPropertyName is: " + sPropertyName);
			String sPropertyNameEnt = String.format("%s.%s", sPropertyName, strEnterpriceCode);
			logger.debug("sPropertyNameEnt is: " + sPropertyNameEnt);

			String propValue = YFSSystem.getProperty(sPropertyNameEnt);
			logger.debug("propValue is: " + propValue);
			if (!YFCObject.isVoid(propValue)) {
				spApiIntegProps.put(sPropertyName, propValue);
			} else {
				String sDefaultOrgPropertyNameEnt = String.format("%s.%s", sPropertyName,
						AmzCommonConstants.STR_DEFAULT);
				logger.debug("sDefaultOrgPropertyNameEnt is: " + sDefaultOrgPropertyNameEnt);
				String defaultPropsValue = YFSSystem.getProperty(sDefaultOrgPropertyNameEnt);
				if (!YFCObject.isVoid(defaultPropsValue)) {
					spApiIntegProps.put(sPropertyName, defaultPropsValue);
				}
			}
		}
		logger.debug("bwpIntegProps is: " + spApiIntegProps);
		logger.info("class: AmzGetGenericProperty | method: getSPIntegProperties -- Ends");
		logger.timer("class: AmzGetGenericProperty | method: getSPIntegProperties -- Ends");
		return spApiIntegProps;
	}

}
