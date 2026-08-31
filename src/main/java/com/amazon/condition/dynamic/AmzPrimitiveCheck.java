package com.amazon.condition.dynamic;

import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzEnterpriseBPIDXRefUtil;
import com.amazon.common.util.AmzLiterals;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.ycp.japi.YCPDynamicConditionEx;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.dom.YFCDocument;
import com.yantra.yfc.dom.YFCElement;
import com.yantra.yfc.dom.YFCNodeList;
import com.yantra.yfs.japi.YFSEnvironment;

public class AmzPrimitiveCheck implements YCPDynamicConditionEx {

	private Map<String, String> conditionProps = null;

	@Override
	public boolean evaluateCondition(YFSEnvironment env, String arg1, @SuppressWarnings("rawtypes") Map arg2,
			Document inDoc){
		
		String primiviteValue = "";
		try {
			String primitiveName = this.conditionProps.get("PRIMITIVE_NAME");
			
			Element eleInputDoc = inDoc.getDocumentElement();
			
			String enterpriseCode = eleInputDoc.getAttribute(AmzLiterals.A_ENTERPRISE_CODE);
			String businessProductID = "";
			
			if(YFCObject.isVoid(enterpriseCode)) {
			Element eleResources = SCXmlUtil.getChildElement(eleInputDoc, "resources");
			if(!YFCObject.isVoid(eleResources)) {
			String strResources = eleResources.getTextContent();
			String[] splitResources = strResources.split("/");
			businessProductID = splitResources[1];
			}		
			enterpriseCode = AmzEnterpriseBPIDXRefUtil.getEnterpriseCode(env, businessProductID);
			
			}
			
			if(YFCObject.isVoid(enterpriseCode)) {
				enterpriseCode = AmzLiterals.STR_ORG_DEFAULT;
			}
			
			Document getPrimitivesInDoc = SCXmlUtil.createDocument("Primitive");
			getPrimitivesInDoc.getDocumentElement().setAttribute(AmzLiterals.A_ENTERPRISE_CODE, enterpriseCode);
			
			Document getPrimitive = AmzCommonUtil.callService(env, getPrimitivesInDoc, "AmzConnGetPrimitives", null);			
			
			YFCElement primitivesElement = YFCDocument.getDocumentFor(getPrimitive).getDocumentElement();
			YFCNodeList<YFCElement> primitiveList = primitivesElement.getElementsByTagName("Primitive");
			
			for (YFCElement tempElement : primitiveList) {
				String strName = tempElement.getAttribute(AmzCommonConstants.STR_NAME);

				if (primitiveName.equals(strName)) {
					primiviteValue = tempElement.getAttribute(AmzCommonConstants.STR_VALUE);
				}
			}			
		
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
		return "Y".equalsIgnoreCase(primiviteValue);
	}

	@Override
	public void setProperties(@SuppressWarnings("rawtypes") Map conditionProps) {
		if (conditionProps != null)
			this.conditionProps = conditionProps;
	}
}
