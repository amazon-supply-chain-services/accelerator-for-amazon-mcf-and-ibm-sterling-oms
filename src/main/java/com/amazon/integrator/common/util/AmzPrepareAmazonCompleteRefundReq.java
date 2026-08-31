package com.amazon.integrator.common.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import com.amazon.common.util.AmzLiterals;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.log.YFCLogCategory;

/**
 * This class is invoked by {@code AmzUpdateCompleteRefundStatusToAmazon}.
 * It prepares a JSON input based on the provided request and returns 
 * the JSON to {@code AmzUpdateCompleteRefundStatusToAmazon}.
 */
public class AmzPrepareAmazonCompleteRefundReq {
	private static final YFCLogCategory logger = YFCLogCategory.instance(AmzPrepareAmazonCompleteRefundReq.class);
	
	/**
	 * @param inDoc
	 * @return
	 * @throws JSONException
	 *  This method prepares the JSON message based on the provided input
	 */
	public static JSONObject prepareCompleteRefundReqJSON(Document inDoc ) throws JSONException  {
	    logger.beginTimer("class: AmzPrepareAmazonCompleteRefundReq | method: prepareCompleteRefundReqJSON -- Starts");
	    logger.info("class: AmzPrepareAmazonCompleteRefundReq | method: prepareCompleteRefundReqJSON -- Starts");
	    Element eleOrderInvoice= inDoc.getDocumentElement();
		String strCurreny= eleOrderInvoice.getAttribute(AmzLiterals.A_CURRENCY); 
		logger.debug("class: AmzPrepareAmazonCompleteRefundReq | method: prepareCompleteRefundReqJSON | strCurreny : " +strCurreny);
	    
		String strtotalAmount= eleOrderInvoice.getAttribute(AmzLiterals.A_TOTAL_AMOUNT); 
		String stotalAmount =  String.format("%.2f", Math.abs(Double.parseDouble(strtotalAmount)));
		logger.debug("class: AmzPrepareAmazonCompleteRefundReq | method: prepareCompleteRefundReqJSON | stotalAmount : " +stotalAmount);
	    
		Element eleOrderInvoiceExtn = SCXmlUtil.getChildElement(eleOrderInvoice,AmzLiterals.E_EXTN);
		
		String strExtnAmazonRefundId = eleOrderInvoiceExtn.getAttribute(AmzLiterals.A_EXTN_AMZ_REFUND_ID);
		logger.debug("class: AmzPrepareAmazonCompleteRefundReq | method: prepareCompleteRefundReqJSON | strExtnAmazonRefundId : " +strExtnAmazonRefundId);
	    
		String strExtnAmazonOrderId= eleOrderInvoiceExtn.getAttribute(AmzLiterals.A_EXTN_AMAZON_ORDER_ID);
		logger.debug("class: AmzPrepareAmazonCompleteRefundReq | method: prepareCompleteRefundReqJSON | strExtnAmazonOrderId : " +strExtnAmazonOrderId);
		
		//DROP-3 UAT without Invoice Cancellation Defect STARTS
		 String strisFailure=eleOrderInvoice.getAttribute("isFailure");
		logger.debug("class: AmzPrepareAmazonCompleteRefundReq | method: prepareCompleteRefundReqJSON | strReference1 : " +strisFailure);
		//DROP-3 UAT without Invoice Cancellation Defect ENDS
		
		
		
	 // orderIdentifier
        JSONObject orderIdentifier = new JSONObject();
        orderIdentifier.put("orderId", strExtnAmazonOrderId);
	    
     // totalAmount
        JSONObject totalAmount = new JSONObject();
        totalAmount.put("amount", stotalAmount);
        totalAmount.put("currencyCode", strCurreny);
        
     // refundTotal
        JSONObject refundTotal = new JSONObject();
        refundTotal.put("totalAmount", totalAmount);
        
     // refund detail
        JSONObject refundDetail = new JSONObject();
        refundDetail.put("id",strExtnAmazonRefundId );
        
      //DROP-3 UAT without Invoice Cancellation Defect STARTS		
        if ("Y".equalsIgnoreCase(strisFailure)){
        	refundDetail.put("state", "FAILURE");
        }else {
        refundDetail.put("state", "SUCCESS");
        }
        
      //DROP-3 UAT without Invoice Cancellation Defect ENDS
		
        refundDetail.put("refundTotal", refundTotal);
        
     // details array
        JSONArray details = new JSONArray();
        details.put(refundDetail);

        // refunds
        JSONObject refunds = new JSONObject();
        refunds.put("details", details);
        
        // input
        JSONObject input = new JSONObject();
        input.put("refunds", refunds);

        // root variables object
        JSONObject variables = new JSONObject();
        variables.put("orderIdentifier", orderIdentifier);
        variables.put("input", input);        
	    logger.debug("class: AmzPrepareAmazonCompleteRefundReq | method: prepareCompleteRefundReqJSON | variable" +variables.toString(4));
	    logger.info("class: AmzPrepareAmazonCompleteRefundReq | method: prepareCompleteRefundReqJSON -- Ends");
	    logger.endTimer("class: AmzPrepareAmazonCompleteRefundReq | method: prepareCompleteRefundReqJSON -- Ends");

	    return variables;
	}
	}
		