package com.amazon.oms.order.api;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.amazon.common.util.AmzCommonConstants;
import com.amazon.common.util.AmzCommonUtil;
import com.amazon.common.util.AmzLiterals;
import com.amazon.common.util.AmzXMLUtil;
import com.sterlingcommerce.baseutil.SCXmlUtil;
import com.yantra.yfc.core.YFCObject;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;

/**
 * This class processes CREATE_ORDER events from Amazon
 */
public class AmzProcessCreateOrderEvent {
    
    final YFCLogCategory logger = YFCLogCategory.instance(AmzProcessCreateOrderEvent.class);
    
    /**
     * Process CREATE_ORDER event from Amazon
     */
    public Document processCreateOrderEvent(YFSEnvironment env, Document indoc) throws Exception {
        
        logger.beginTimer("class: AmzProcessCreateOrderEvent | method: processCreateOrderEvent -- Starts");
        logger.info("class: AmzProcessCreateOrderEvent | method: processCreateOrderEvent -- Starts");
        logger.debug("AmzProcessCreateOrderEvent.processCreateOrderEvent input doc is: " + AmzXMLUtil.getString(indoc));
        
        prepareAndLogRequest(indoc);
        
        Element eleRoot = indoc.getDocumentElement();
        String strEventType = eleRoot.getAttribute(AmzLiterals.ATTR_AMAZON_EVENT_TYPE);
        
        if (!YFCObject.isVoid(strEventType) && "CREATE_ORDER".equalsIgnoreCase(strEventType)) {
            logger.debug("Processing CREATE_ORDER event");
            // Process the create order event logic here
            processOrderCreation(eleRoot);
        }
        
        prepareAndLogResponse(AmzLiterals.STR_SUCCESS, indoc, null);
        
        logger.info("class: AmzProcessCreateOrderEvent | method: processCreateOrderEvent -- Ends");
        logger.endTimer("class: AmzProcessCreateOrderEvent | method: processCreateOrderEvent -- Ends");
        
        return indoc;
    }
    
    /**
     * Process order creation logic
     */
    private void processOrderCreation(Element eleRoot) {
        logger.beginTimer("class: AmzProcessCreateOrderEvent | method: processOrderCreation -- Starts");
        logger.info("class: AmzProcessCreateOrderEvent | method: processOrderCreation -- Starts");
        
        String strOrderId = eleRoot.getAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID);
        logger.debug("Processing order creation for Amazon Order ID: " + strOrderId);
        
        // Add minimal order creation processing logic here
        
        logger.info("class: AmzProcessCreateOrderEvent | method: processOrderCreation -- Ends");
        logger.endTimer("class: AmzProcessCreateOrderEvent | method: processOrderCreation -- Ends");
    }
    
    /**
     * Log the request before processing
     */
    private void prepareAndLogRequest(Document indoc) {
        logger.beginTimer("class: AmzProcessCreateOrderEvent | method: prepareAndLogRequest -- Starts");
        logger.info("class: AmzProcessCreateOrderEvent | method: prepareAndLogRequest -- Starts");
        
        Element eleRoot = indoc.getDocumentElement();
        Document logInput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
        logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_DESC, "CREATE_ORDER_EVENT");
        logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID, 
            eleRoot.getAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID));
        logInput.getDocumentElement().setAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE,
            eleRoot.getAttribute(AmzLiterals.ATTR_ENTERPRISE_CODE));
        
        AmzCommonUtil.logAmzConnRequest(logInput);
        
        logger.info("class: AmzProcessCreateOrderEvent | method: prepareAndLogRequest -- Ends");
        logger.endTimer("class: AmzProcessCreateOrderEvent | method: prepareAndLogRequest -- Ends");
    }
    
    /**
     * Log the response after processing
     */
    private void prepareAndLogResponse(String processStatus, Document indoc, String message) {
        logger.beginTimer("class: AmzProcessCreateOrderEvent | method: prepareAndLogResponse -- Starts");
        logger.info("class: AmzProcessCreateOrderEvent | method: prepareAndLogResponse -- Starts");
        
        Element eleRoot = indoc.getDocumentElement();
        Document logOutput = SCXmlUtil.createDocument(AmzLiterals.STR_LOG_INPUT);
        logOutput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_EVENT_DESC, "CREATE_ORDER_EVENT");
        logOutput.getDocumentElement().setAttribute(AmzLiterals.ATTR_PROCESS_STATUS, processStatus);
        logOutput.getDocumentElement().setAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID,
            eleRoot.getAttribute(AmzLiterals.ATTR_AMAZON_ORDER_ID));
        
        if (!YFCObject.isVoid(message)) {
            logOutput.getDocumentElement().setAttribute(AmzLiterals.STR_MESSAGE, message);
        }
        
        AmzCommonUtil.logAmzConnRequest(logOutput);
        
        logger.info("class: AmzProcessCreateOrderEvent | method: prepareAndLogResponse -- Ends");
        logger.endTimer("class: AmzProcessCreateOrderEvent | method: prepareAndLogResponse -- Ends");
    }
}