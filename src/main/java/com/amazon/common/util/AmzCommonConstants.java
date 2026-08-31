package com.amazon.common.util;

public class AmzCommonConstants {
	public static final String AMZ_TOKEN_URL = "amzConn.bwp_api_oauth_url";
	public static final String AMZ_CLIENT_ID = "amzConn.bwp_api_client_id";
	public static final String AMZ_CLIENT_SECRET = "amzConn.bwp_api_client_secret";
	public static final String AMZ_GRANT_TYPE = "amzConn.bwp_api_grant_type";
	public static final String AMZ_TIME_OUT = "amzConn.api_time_out";
	public static final String AMZ_TARGETID = "amzConn.bwp_api_target_id";
	public static final String AMZ_API_ACCESS_KEY = "amzConn.bwp_api_access_key";
	public static final String AMZ_API_VERSION = "amzConn.bwp_api_version";
	public static final String AMZ_POST_URL = "amzConn.bwp_api_post_url";
	
	public static final String AMZ_ATTRIBUTE_MARKETPLACE_ID = "marketPlaceId";
	public static final String AMZ_ATTRIBUTE_GRANULARITY_ID = "granularityID";
	public static final String AMZ_ATTRIBUTE_NEXT_TOKEN = "nextToken";
	public static final String AMZ_ATTRIBUTE_SELLER_SKU = "sellerSku";
	
	public static final String AMZ_ELEM_PAGINATION = "pagination";
	public static final String AMZ_ELEM_PAYLOAD = "payload";
	public static final String AMZ_ELEM_INVENTORY_SUMMARIES = "inventorySummaries";
	public static final String AMZ_ELEM_ITEM="Item";
	public static final String AMZ_ELEM_INVENTORY_DETAILS="inventoryDetails";
	public static final String AMZ_ELEM_INVENTORY_ITEMS="InventoryItems";
	
	public static final String ATTR_MARKETPLACEID="MarketplaceID";
	public static final String ATTR_SHIPNODE="ShipNode";
	public static final String ATTR_FULFILLABLE_QTY="fulfillableQuantity";
	public static final String ATTR_UOM="UnitOfMeasure";
	public static final String ATTR_PRODUCT_CLASS="ProductClass";
	public static final String ATTR_ITEMID="ItemID";
	public static final String ATTR_AVAILABLE_QTY="AvailableQty";
	
	public static final String PROP_DEFAULT_UOM = "amzConn.item.UOM";
	public static final String PROP_DEFAULT_PRODUCT_CLASS = "amzConn.item.productClass";
	public static final String MARKETPLACE_ID = "amzConn.US.marketplaceId";
	public static final String IV_PHASE2_ENABLED = "amzConn.isIVPhase2Enabled";
	public static final String IV_BASE_URL = "amzConn.iv.baseurl";
	public static final String IV_TENANT_ID = "amzConn.iv.tenantID";
	public static final String AMZ_DEMAND_TYPES = "amzConn.demandTypes";
	public static final String AMZ_ITEMID_PREFIX = "ItemIDPrefix";
	public static final String AMZ_GRANULARITY_TYPE = "amzConn.fullsync.granularityType";
	
	public static final String SP_TOKEN_URL = "amzConn.sp_api_oauth_url";
	public static final String SP_REFRESH_TOKEN = "amzConn.sp_api_refresh_token";
	public static final String SP_CLIENT_ID = "amzConn.sp_api_client_id";
	public static final String SP_CLIENT_SECRET = "amzConn.sp_api_client_secret";
	public static final String SP_GRANT_TYPE = "amzConn.sp_api_grant_type";
	public static final String SP_GET_URL = "amzConn.GetInventorySummaries.api_url";
	public static final String SP_GET_V2OUTBOUND_URL = "amzConn.MCF.CreateFulfillmentOrder.api_url";
	public static final String SP_MCF_GET_FULFILLMENT_ORDER_URL = "amzConn.MCF.GetFulfillmentOrder.api_url";
	public static final String SP_MCF_LIST_FULFILLMENT_ORDERS_URL = "amzConn.MCF.ListFulfillmentOrders.api_url";
	
	public static final String GET_COMMON_CODE_LIST = "getCommonCodeList";
	public static final String STR_AMAZON = "AMAZON";
	public static final String STR_MERCHANT = "MERCHANT"; 
	public static final String STR_EXTERNAL_ORDER_ID = "EXTERNAL_ORDER_ID";
	public static final String STR_VAL_Y = "Y";
	public static final String SERVICE_AMZ_CHANGE_ORDER_SERVICE= "AmzChangeOrderService";
	public static final String STR_SCHEDULED_ORDER_STATUS= "1500";
	public static final String API_GET_ORDER_LIST = "getOrderList";
	public static final String TEMPLATE_GET_ORDER_LIST_FOR_AMZ_CREATE_ORDER = "/global/template/api/getOrderList_OP_CreateAmazonOrder.xml";
	public static final String AMZ_SHIPPING_CHARGE_CATEGORY= "amzConn.shippingChargeCategory";
	public static final String AMZ_SHIPPING_CHARGE_NAME	= "amzConn.shippingChargeName";
	public static final String AMZ_SHIPPING_DISCOUNT_CHARGE_CATEGORY	= "amzConn.shippingDiscountChargeCategory";
	public static final String AMZ_UPDORD_DESIRED_EXECUTION_STATE = "amzConn.updOrd.desiredExecutionState";
	public static final String AMZ_SHIPPING_DISCOUNT_CHARGE_NAME = "amzConn.shippingDiscountChargeName";
	public static final String AMZ_SHIPPING_TAX_CHARGE_CATEGORY = "amzConn.shippingTaxChargeCategory";
	public static final String AMZ_SHIPPING_TAX_CHARGE_NAME = "amzConn.shippingTaxChargeName";
	public static final String AMZ_SHIPPING_TAX_NAME = "amzConn.shippingTaxName";
	public static final String AMZ_EXECUTION_STATE_NOT_STARTED = "NOT_STARTED";


	public static final String STR_VAL_N = "N";
	public static final String STR_RELEASED_ORDER_STATUS= "3200";
	public static final String STR_RELEASED_ORDER = "ReleaseOrder";
	public static final String STR_TRANSACTION = "Transaction";
	public static final String SERVICE_AMZ_CHANGE_RELEASE = "AmzChangeRelease";
	
	public static final String API_CREATE_EXCEPTION = "createException";
	public static final String API_CHANGE_ORDER = "changeOrder";
	public static final String AMZ_DELIVERY_PROVIDER = "amzConn.deliveryProvider";
	public static final String STR_ERRORS = "errors";
	
	public static final String TEMPLATE_GET_ORDER_RELEASE_LIST_FOR_AMZ_CREATE_ORDER = "/global/template/api/getOrderReleaseList_OP_AmazonCreateOrder.xml";
	public static final String API_GET_ORDER_RELEASE_LIST = "getOrderReleaseList";
	
	public static final String STR_HTTP_STATUS_OK = "200";
	public static final String STR_OK = "OK";
	
	public static final String TEMPLATE_GET_ORDER_LIST_FOR_AMZ_CANCEL_ORDER = "/global/template/api/getOrderList_OP_CancelAmazonOrder.xml";
	public static final String STR_WAIT = "WAIT";

	public static final String STR_NAME = "Name";
	public static final String STR_VALUE = "Value";
	public static final String STR_AMZ_PRIMITIVES = "AMZ_PRIMITIVES";
	public static final String STR_COMMON_CODE = "CommonCode";
	public static final String STR_CODE_TYPE = "CodeType";
	public static final String STR_CODE_VALUE = "CodeValue";
	public static final String STR_CODE_SHORT_DESC = "CodeShortDescription";
	public static final String STR_ORG_CODE = "OrganizationCode";
	public static final String STR_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
	public static final String PROP_MCF_FULFILLMENT_TYPE = "amzConn.mcf.fulfillmentType";
	
	public static final String AMZ_ORDER_ID = "AmzOrderID";	
	public static final String PIPE = " | ";
	public static final String EQUAL = "=";

	public static final String AMZ_ORDER_ID_PREFIX = "OrderIDPrefix";
	public static final String AMZ_DELIVERY_ID_PREFIX = "DeliveryIDPrefix";
	public static final String AMZ_TRACKING_NO_PREFIX = "TrackingNoPrefix";
	public static final String TEMPLATE_GET_SHIP_CONTAINER_LIST_FOR_TRACKING_UPDATED = "/global/template/api/getShipmentContainerList_for_TrackingUpdates.xml"; 
	public static final String SERVICE_AMAZON_GET_ORDER_DETAILS = "AmzConnGetOrderDetails";
	public static final String API_GET_SHIPMENT_CONTAINER_LIST = "getShipmentContainerList";
	public static final String STR_STERLING_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";
	public static final String SERVICE_AMZ_CREATE_AMZ_CONN_CONATINER_MILESTONES= "AmzConnCreateContainerMilestones";
	public static final String API_MULTI_API = "multiApi";
	public static final String STR_MILESTONES_TABLE = "MilestonesTable";
	public static final String STR_DATE_FOR_YYYYMMDD = "yyyyMMdd";
	public static final String STR_VAL_GT = "GT";
	public static final String SERVICE_GET_AMZ_CONN_CONTAINER_MILESTONES_LIST="AmzConnGetContainerMilestonesList";
	public static final String STR_EMPTY = "EMPTY";
	public static final String API_GET_SHIPMENT_LIST = "getShipmentList";
	public static final String TEMPLATE_GET_SHIPMENT_LIST_FOR_TRACKING_MILESTONE_PURGE = "/global/template/api/getShipmentList_for_TrackingMilestone_Purge.xml"; 
	public static final String PROP_TRACKING_RETENTION_DAYS = "amzConn.tracking.defaultRetentionDays";
	public static final String STR_DEFAULT = "DEFAULT";
	public static final String STR_ORDER_FULFILLMENT = "ORDER_FULFILLMENT";
	public static final String API_EVALUATE_CONDITION = "evaluateCondition";
	public static final String API_CHANGE_RELEASE = "changeRelease";
	public static final String API_CHANGE_SHIPMENT_STATUS = "changeShipmentStatus";
	public static final String API_GET_COMPLETE_ORDER_LINE_LIST ="getCompleteOrderLineList";
	
	 public static final String STR_OMS_PROCESS = "OMS-PROCESS";
    public static final String STR_AMZCONN_CREATE_ORDER = "AMZCONN_CREATE_ORDER";
    public static final String STR_REQUEST = "REQUEST";
    public static final String STR_RESPONSE = "RESPONSE";
    public static final String STR_AMZCONN_UPDATE_ORDER = "AMZCONN_OMS_TO_AMAZON_UPDATE_ORDER";
    public static final String STR_GENERAL = "GENERAL";
    public static final String SERVICE_AMZ_CONN_UPDATE_MILESTONES_RECORD_IN_OMS = "AmzConnUpdateMilestonesRecordInOMS";
	public static final String API_GET_ORDER_LINE_LIST ="getOrderLineList";
    //Multi-brand changes
    public static final String STR_AMZCONN_ENT_BPID_XREF = "AMZCONN_ENT_BPID_XR";
    public static final String STR_CODE_LONG_DESC = "CodeLongDescription";
    public static final String STR_AMZCONN_PRIMITIVES = "AMZCONN_PRIMITIVES";
    public static final String STR_AMZCONN_OMS_SHIPNOD = "AMZCONN_OMS_SHIPNOD";
    public static final String STR_AMZCONN_GENRL_PROPS = "AMZCONN_GENRL_PROPS";
    public static final String STR_AMZCONN_MCF_BACKORDERCANCELLEDLINE= "amzConn.MCF.BackOrderCancelledLine";
    public static final String PROP_SO_PIPELINE_CONDI_ATTR= "amzConn.so.pipelineConditionAttr";
    public static final String PROP_SO_PIPELINE_CONDI_ATTR_VALUE="amzConn.so.pipelineConditionAttrValue";
    public static final String STR_DEFAULT_REQUESTED_BY_CODE_VAL ="amzConn.defaultRequestedBy";

	public static final String PROP_MARKETPLACE_ID= "amzConn.marketplaceId";
    public static final String PROP_AMZ_SHIP_NODE= "amzConn.amazonShipNode.";
    public static final String PROP_AMZ_OMS_ITEMID_XREF_AMAZONCATALOG= "amzConn.oms.ItemID.xref.amazonCatalog";
    public static final int INT_PRODUCTS_BATCH_SIZE= 20;
	// Refund Requested Event
    public static final String STR_REFUND_ID_PRIFIX = "RefundIDPrefix";
    public static final String SERVICE_AMAZON_GET_REFUND_ORDER_DETAILS = "AmzConnGetOrderDetailsWithRefund";
    public static final String API_RECEIVE_ORDER = "receiveOrder";
    public static final String API_RECORD_INVOICE_CREATION = "recordInvoiceCreation";
    public static final String API_GET_ORDER_INVOICE_LIST ="getOrderInvoiceList";
    public static final String SERVICE_AMAZON_POST_MSGTO_FUFILLMENT_EVENT_Q ="AmzConnPostFulfillmentEventsToQ";
    public static final String API_CLOSE_RECEIPT= "closeReceipt";
    public static final String API_GET_RECEIPT_LINE_LIST ="getReceiptLineList";
    public static final String API_GET_ORDER_INVOICE_DET_LIST ="getOrderInvoiceDetailList";
    public static final String API_CREATE_ORDER_INVOICE= "createOrderInvoice";
    public static final String API_CHANGE_ORDER_INVOICE= "changeOrderInvoice";
    public static final String SERVICE_AMZ_CONN_POST_COMPLETE_REFUND_REQ_MSG_TO_Q ="AmzConnPostCompleteRefundReqToQ";
    public static final String STR_DELIVERY_CANCELLED_STATUS ="3700.9000";
	//Returns sync updates Starts
	public static final String AMZ_RETURN_ID_PREFIX = "ReturnIDPrefix";
	public static final String AMZ_RETURN_DELIVERY_PREFIX = "ReturnDeliveryPrefix";
	public static final String AMZ_RETURN_LINE_ITEM_PREFIX = "ReturnLineItemPrefix";
	public static final String PROP_RO_PIPELINE_CONDI_ATTR= "amzConn.ro.pipelineConditionAttr";
	public static final String PROP_RO_PIPELINE_CONDI_ATTR_VALUE="amzConn.ro.pipelineConditionAttrValue";
	public static final String PROP_DROPSTATUS_INTRANSIT="amzConn.ro.baseDropStatusInTransit";
	public static final String PROP_DROPSTATUS_DELIVERED="amzConn.ro.baseDropStatusDelivered";
	public static final String PROP_DROPSTATUS_FAILED="amzConn.ro.baseDropStatusFailed";
	public static final String PROP_DROPSTATUS_ITEMGRADED="amzConn.ro.baseDropStatusItemGraded";
	public static final String PROP_STATUS_RETURN_RECEIVED="amzConn.ro.statusReturnReceived";
	public static final String PROP_RETUND_EVENT="amzConn.ro.refundEvent";
	//Returns sync updates Ends

	
	//External returns Sync start
	//External returns sync constants
	public static final String STR_RETURN_DOCUMENT_TYPE = "0003";
	public static final String STR_AMZCONN_CREATE_EXT_RETURN_ORDER = "AMZCONN_CREATE_EXT_RETURN_ORDER";	
	public static final String STR_AMZCONN_ADD_EXT_RETURN_REFUND = "AMZCONN_ADD_EXT_RETURN_REFUND";
	public static final String STR_AMZ_ADD_EXTERNAL_REFUND = "AmzAddExternalRefund";
	public static final String STR_AMZ_CREATE_EXTERNAL_RETURN = "AmzCreateExternalReturn";
	public static final String STR_AMZ_CANCEL_EXTERNAL_RETURN = "AmzCancelExternalReturn";
	public static final String STR_AMZ_COMPLETE_EXTERNAL_RETURN = "AmzCompleteExternalReturn";
	public static final String STR_AMZCONN_UPDATE_EXT_RETURN_REFUND = "AMZCONN_UPDATE_EXT_RETURN_REFUND";
	public static final String STR_CREATED = "CREATED";
	public static final String STR_COMPLETED = "COMPLETED";
	public static final String STR_CANCELLED = "CANCELLED";
	public static final String STR_AMZCONN_UPDATE_EXT_RETURN_ORDER = "AMZCONN_OMS_TO_AMAZON_UPDATE_EXT_RETURN_ORDER";
	public static final String STR_EXTERNAL_REFUND_ID = "EXTERNAL_REFUND_ID";
	public static final String STR_EXTERNAL_RETURN_ID = "EXTERNAL-RETURN-ID";
	public static final String STR_RETURN = "RETURN";
	public static final String STR_RECEIVED_QTY = "ReceivedQty";
	public static final String STR_RETURN_RECEIVED_STATUS = "3900";
	public static final String STR_CANCELLED_STATUS = "9000";
	public static final String STR_CREDIT_MEMO = "CREDIT_MEMO";
	public static final String TEMP_GET_ORDER_LINE_LIST_AMZ_INIT_RETURN ="/global/template/api/getOrderLineList_For_AmazonInitReturn.xml";
	public static final String TEMP_GET_ORDER_LIST_AMZ_INIT_RETURN ="/global/template/api/getOrderList_For_AmazonInitReturn.xml";
	public static final String STR_SALES_ORDER_DOCUMENT_TYPE = "0001";
	
	
	//External return sync Templates:
	public static final String TEMPLATE_GET_ORDER_LIST_FOR_CREATE_EXT_RETURN = "/global/template/api/getOrderList_For_AmazonReturnCreation.xml";
	public static final String TEMPLATE_GET_ORD_INV_DET_FOR_AMAZON_EXT_REFUND = "/global/template/api/getOrdInvDet_For_AmazonExtRefund.xml";
	public static final String TEMPLATE_GET_ORD_INV_DET_LIST_FOR_AMAZON_EXT_REFUND = "/global/template/api/getOrdInvDetList_For_AmazonExtRefund.xml";
	public static final String TEMPLATE_GET_ORDER_LIST_TO_ADD_EXT_RETURN = "getOrderList_To_AddExternalRefund";

	//External returns Sync api and service:
	public static final String SERVICE_AMZ_CONN_POST_EXTERNAL_RETURN_MSG_TO_Q = "AmzConnPostExternalReturnMsgToQ";
	public static final String SERVICE_AMZCONN_PROCESS_INVOICE_DETAILS="AmzConnProcessInvoiceDetails";
	public static final String API_GET_ORDER_INVOICE_DETAILS = "getOrderInvoiceDetails";
	public static final String API_GET_ORDER_INVOICE_DETAIL_LIST = "getOrderInvoiceDetailList";


	//External returns sync generic properties
	public static final String PROPS_EXT_REFUND_PAYMENT_TYPE = "amzConn.ext.refund.payment.type";
	public static final String PROPS_EXT_REFUND_PAYMENT_DISPLAY_STRING = "amzConn.ext.refund.payment.displayString";
	public static final String PROPS_EXT_REFUND_PAYMENT_STATE = "amzConn.ext.refund.payment.state";
	public static final String PROPS_EXT_REFUND_PAYMENT_ID = "amzConn.ext.refund.payment.id";
	//External returns Sync end
	
	
	//Throw exception on regular merchant lines scheduled from amazon shipnode
	public static final String STR_MERCH_LINE_SCHEDULE_ERROR = "MERCH_LINE_SCHEDULE_ERROR";
	public static final String AMZ_EXECUTION_STATE_NOT_STARTED_BWP = "amzConn.updOrd.desiredExecutionState.bwp";
	public static final String AMZ_EXECUTION_STATE_STARTED_MCF = "amzConn.updOrd.desiredExecutionState.mcf";
	
	
}

