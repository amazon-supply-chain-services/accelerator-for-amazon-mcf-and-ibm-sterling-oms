package com.amazon.oms.order.userexit;

import java.util.Date;

import com.amazon.common.util.AmzLiterals;
import com.yantra.yfc.log.YFCLogCategory;
import com.yantra.yfs.japi.YFSEnvironment;
import com.yantra.yfs.japi.YFSExtnPaymentCollectionInputStruct;
import com.yantra.yfs.japi.YFSExtnPaymentCollectionOutputStruct;
import com.yantra.yfs.japi.YFSUserExitException;
import com.yantra.yfs.japi.ue.YFSCollectionCreditCardUE;


/***********************************************************************************************************************
 * Document            : AmzCollectionCreditCardUE.java
 * 
 * Invocation           : Payment Collection Agent
 *
 * Description          : AmzCollectionCreditCardUE for payment collection update.
 *                        When shipping order and creating credit memo then settle order (Scenario - Where direct delivery notification received)
 * 					
 * --------------------------------------------------------------------------------------------------------------------
 **********************************************************************************************************************/
public class AmzCollectionCreditCardUE implements YFSCollectionCreditCardUE{

	private static YFCLogCategory logger = YFCLogCategory.instance(AmzCollectionCreditCardUE.class);
	private static boolean isDebugEnabled = logger.isDebugEnabled();
	
	/**
	 * YFSCollectionCreditCardUE
	 * 
	 * @param env
	 * @param inDoc
	 * @return
	 * @throws Exception
	 */
	public YFSExtnPaymentCollectionOutputStruct collectionCreditCard(YFSEnvironment env, YFSExtnPaymentCollectionInputStruct inStruct) throws YFSUserExitException {
		
		if(isDebugEnabled) {
			logger.debug("\n****************** AmzCollectionCreditCardUE::collectionCreditCard ******************\n");
			printInStruct(inStruct);
			logger.debug("inStruct"+inStruct.toString());
		}
		
		if(isDebugEnabled) {
			logger.debug("--------------------------------------------------------------------------");
			logger.debug("Document Type === " + inStruct.documentType);
			logger.debug("Order Header Key === " + inStruct.orderNo);
			logger.debug("Payment Type === " + inStruct.paymentType);
			logger.debug("Enterprise Code === " + inStruct.enterpriseCode);
			logger.debug("Document Type === " + inStruct.documentType);
			logger.debug("Request Amount === " + inStruct.requestAmount);
			logger.debug("--------------------------------------------------------------------------");
		}
		YFSExtnPaymentCollectionOutputStruct outStruct = new YFSExtnPaymentCollectionOutputStruct();
		try{

			
			//updating out struct amount for success response
			Date collectionDate = new Date();
			Date executionDate = new Date();
			outStruct.collectionDate = collectionDate;
			outStruct.executionDate = executionDate;
			outStruct.authReturnFlag = AmzLiterals.STR_VAL_T;
			outStruct.PaymentReference2 = inStruct.paymentReference2;
			outStruct.PaymentReference1 = inStruct.paymentReference1;
			outStruct.authorizationAmount =inStruct.requestAmount;
			outStruct.authReturnCode = AmzLiterals.STR_ATTR_AUTH_RETURNCODE;
			outStruct.asynchRequestProcess = false;
			outStruct.tranReturnFlag = AmzLiterals.STR_VAL_T;
			outStruct.retryFlag=AmzLiterals.STR_VAL_N;	
			outStruct.tranAmount = inStruct.requestAmount;
			
		}catch(Exception ex){
			logger.info("An exception has occured ");
			throw new YFSUserExitException("AmzCollectionCreditCardUE::collectionCreditCard:: An exception has occured " + ex.getMessage());                       
		}
		if(isDebugEnabled) {
			printOutStruct(outStruct);
			logger.debug("\n********************** AmzCollectionCreditCardUE::collectionCreditCard End******************\n");
			
		}
		return outStruct;
	}
	
	
	private void printOutStruct(YFSExtnPaymentCollectionOutputStruct outStruct) {
		logger.debug("AmzCollectionCreditCardUE : printOutStruct :: enter");

		StringBuilder logString = new StringBuilder();

			logString.append("\noutStruct.authorizationAmount = ").append(outStruct.authorizationAmount)
					.append("\noutStruct.authorizationId = ").append(outStruct.authorizationId)
					.append("\noutStruct.authorizationExpirationDate = ").append(outStruct.authorizationExpirationDate)
					.append("\noutStruct.tranType = ").append(outStruct.tranType).append("\noutStruct.tranAmount = ")
					.append(outStruct.tranAmount).append("\noutStruct.bPreviousInvocationSuccessful = ")
					.append(outStruct.bPreviousInvocationSuccessful).append("\noutStruct.tranRequestTime = ")
					.append(outStruct.tranRequestTime).append("\noutStruct.tranReturnCode = ").append(outStruct.tranReturnCode)
					.append("\noutStruct.tranReturnMessage = ").append(outStruct.tranReturnMessage).append("\noutStruct.tranReturnFlag = ")
					.append(outStruct.tranReturnFlag).append("\noutStruct.requestID = ").append(outStruct.requestID)
					.append("\noutStruct.internalReturnCode = ").append(outStruct.internalReturnCode)
					.append("\noutStruct.internalReturnFlag = ").append(outStruct.internalReturnFlag)
					.append("\noutStruct.internalReturnMessage = ").append(outStruct.internalReturnMessage)
					.append("\noutStruct.authCode = ").append(outStruct.authCode).append("\noutStruct.authAVS = ")
					.append(outStruct.authAVS).append("\noutStruct.sCVVAuthCode = ").append(outStruct.sCVVAuthCode)
					.append("\noutStruct.collectionDate = ").append(outStruct.collectionDate).append("\noutStruct.executionDate = ")
					.append(outStruct.executionDate).append("\noutStruct.authReturnCode = ").append(outStruct.authReturnCode)
					.append("\noutStruct.authReturnFlag = ").append(outStruct.authReturnFlag).append("\noutStruct.authReturnMessage = ")
					.append(outStruct.authReturnMessage).append("\noutStruct.authTime = ").append(outStruct.authTime)
					.append("\noutStruct.retryFlag = ").append(outStruct.retryFlag).append("\noutStruct.holdOrderAndRaiseEvent = ")
					.append(outStruct.holdOrderAndRaiseEvent).append("\noutStruct.asynchRequestProcess = ")
					.append(outStruct.asynchRequestProcess).append("\noutStruct.holdReason = ").append(outStruct.holdReason)
					.append("\noutStruct.suspendPayment = ").append(outStruct.suspendPayment).append("\noutStruct.SvcNo = ")
					.append(outStruct.SvcNo).append("\noutStruct.DisplaySvcNo = ").append(outStruct.DisplaySvcNo)
					.append("\noutStruct.PaymentReference1 = ").append(outStruct.PaymentReference1)
					.append("\noutStruct.DisplayPaymentReference1 = ").append(outStruct.DisplayPaymentReference1)
					.append("\noutStruct.PaymentReference2 = ").append(outStruct.PaymentReference2)
					.append("\noutStruct.PaymentReference3 = ").append(outStruct.PaymentReference3)
					.append("\noutStruct.PaymentReference4 = ").append(outStruct.PaymentReference4)
					.append("\noutStruct.PaymentReference5 = ").append(outStruct.PaymentReference5)
					.append("\noutStruct.PaymentReference6 = ").append(outStruct.PaymentReference6)
					.append("\noutStruct.PaymentReference7 = ").append(outStruct.PaymentReference7)
					.append("\noutStruct.PaymentReference8 = ").append(outStruct.PaymentReference8)
					.append("\noutStruct.PaymentReference9 = ").append(outStruct.PaymentReference9)
					.append("\noutStruct.eleExtendedFields = ").append(outStruct.eleExtendedFields)
					.append("\noutStruct.RequiresCallForAuthorization = ").append(outStruct.RequiresCallForAuthorization)
					.append("\noutStruct.ConditionalCallForAuthorization = ").append(outStruct.ConditionalCallForAuthorization)
					.append("\noutStruct.OfflineStatus = ").append(outStruct.OfflineStatus)
					.append("\noutStruct.recordAdditionalTransactions = ").append(outStruct.recordAdditionalTransactions)
					.append("\noutStruct.PaymentTransactionError = ").append(outStruct.PaymentTransactionError)
					.append("\noutStruct.bRetryVoidAsRefund = ").append(outStruct.bRetryVoidAsRefund)
					.append("\noutStruct.HoldAgainstBook = ").append(outStruct.HoldAgainstBook)
					.append("\noutStruct.bChargeMayHaveOccurred = ").append(outStruct.bChargeMayHaveOccurred);
			logger.debug(logString.toString());
		
		logger.debug("AmzCollectionCreditCardUE : printOutStruct :: exit");
	}
	
	private void printInStruct(YFSExtnPaymentCollectionInputStruct inStruct) {
		logger.debug("AmzCollectionCreditCardUE : printInStruct :: enter");

		StringBuilder logString = new StringBuilder();

			logString.append("\ninStruct.bPreviouslyInvoked = ").append(inStruct.bPreviouslyInvoked).append("\ninStruct.requestAmount = ")
					.append(inStruct.requestAmount).append("\ninStruct.chargeType = ").append(inStruct.chargeType)
					.append("\ninStruct.paymentType = ").append(inStruct.paymentType).append("\ninStruct.authorizationId = ")
					.append(inStruct.authorizationId).append("\ninStruct.orderNo = ").append(inStruct.orderNo)
					.append("\ninStruct.shipTokey = ").append(inStruct.shipTokey).append("\ninStruct.billTokey = ")
					.append(inStruct.billTokey).append("\ninStruct.currency = ").append(inStruct.currency)
					.append("\ninStruct.creditCardNo = ").append(inStruct.creditCardNo).append("\ninStruct.creditCardExpirationDate = ")
					.append(inStruct.creditCardExpirationDate).append("\ninStruct.creditCardName = ").append(inStruct.creditCardName)
					.append("\ninStruct.svcNo = ").append(inStruct.svcNo).append("\ninStruct.debitCardNo = ").append(inStruct.debitCardNo)
					.append("\ninStruct.paymentReference1 = ").append(inStruct.paymentReference1).append("\ninStruct.paymentReference2 = ")
					.append(inStruct.paymentReference2).append("\ninStruct.paymentReference3 = ").append(inStruct.paymentReference3)
					.append("\ninStruct.paymentReference4 = ").append(inStruct.paymentReference4).append("\ninStruct.paymentReference5 = ")
					.append(inStruct.paymentReference5).append("\ninStruct.paymentReference6 = ").append(inStruct.paymentReference6)
					.append("\ninStruct.paymentReference7 = ").append(inStruct.paymentReference7).append("\ninStruct.paymentReference8 = ")
					.append(inStruct.paymentReference8).append("\ninStruct.paymentReference9 = ").append(inStruct.paymentReference9)
					.append("\ninStruct.eleExtendedFields = ").append(inStruct.eleExtendedFields).append("\ninStruct.customerAccountNo = ")
					.append(inStruct.customerAccountNo).append("\ninStruct.customerPONo = ").append(inStruct.customerPONo)
					.append("\ninStruct.merchantId = ").append(inStruct.merchantId).append("\ninStruct.shipToFirstName = ")
					.append(inStruct.shipToFirstName).append("\ninStruct.shipToLastName = ").append(inStruct.shipToLastName)
					.append("\ninStruct.shipToAddressLine1 = ").append(inStruct.shipToAddressLine1).append("\ninStruct.shipToCity = ")
					.append(inStruct.shipToCity).append("\ninStruct.shipToState = ").append(inStruct.shipToState)
					.append("\ninStruct.shipToZipCode = ").append(inStruct.shipToZipCode).append("\ninStruct.shipToCountry = ")
					.append(inStruct.shipToCountry).append("\ninStruct.shipToEmailId = ").append(inStruct.shipToEmailId)
					.append("\ninStruct.shipToDayPhone = ").append(inStruct.shipToDayPhone).append("\ninStruct.shipToId = ")
					.append(inStruct.shipToId).append("\ninStruct.billToFirstName = ").append(inStruct.billToFirstName)
					.append("\ninStruct.billToLastName = ").append(inStruct.billToLastName).append("\ninStruct.billToAddressLine1 = ")
					.append(inStruct.billToAddressLine1).append("\ninStruct.billToCity = ").append(inStruct.billToCity)
					.append("\ninStruct.billToState = ").append(inStruct.billToState).append("\ninStruct.billToZipCode = ")
					.append(inStruct.billToZipCode).append("\ninStruct.billToCountry = ").append(inStruct.billToCountry)
					.append("\ninStruct.billToEmailId = ").append(inStruct.billToEmailId).append("\ninStruct.billToDayPhone = ")
					.append(inStruct.billToDayPhone).append("\ninStruct.billToId = ").append(inStruct.billToId)
					.append("\ninStruct.chargeTransactionKey = ").append(inStruct.chargeTransactionKey)
					.append("\ninStruct.orderHeaderKey = ").append(inStruct.orderHeaderKey).append("\ninStruct.enterpriseCode = ")
					.append(inStruct.enterpriseCode).append("\ninStruct.paymentConfigOrganizationCode = ")
					.append(inStruct.paymentConfigOrganizationCode).append("\ninStruct.creditCardType = ").append(inStruct.creditCardType)
					.append("\ninStruct.documentType = ").append(inStruct.documentType).append("\ninStruct.firstName = ")
					.append(inStruct.firstName).append("\ninStruct.middleName = ").append(inStruct.middleName)
					.append("\ninStruct.lastName = ").append(inStruct.lastName).append("\ninStruct.secureAuthenticationCode = ")
					.append(inStruct.secureAuthenticationCode).append("\ninStruct.currentAuthorizationAmount = ")
					.append(inStruct.currentAuthorizationAmount).append("\ninStruct.currentAuthorizationExpirationDate = ")
					.append(inStruct.currentAuthorizationExpirationDate)
					.append("\ninStruct.currentAuthorizationCreditCardTransactions" + inStruct.currentAuthorizationCreditCardTransactions)
					.append("\ninStruct.paymentKey = ").append(inStruct.paymentKey).append("\ninStruct.bVoidTransaction = ")
					.append(inStruct.bVoidTransaction).append("\ninStruct.cashBackAmount = ").append(inStruct.cashBackAmount)
					.append("\ninStruct.chequeNo = ").append(inStruct.chequeNo).append("\ninStruct.chequeReference = ")
					.append(inStruct.chequeReference).append("\ninStruct.entryType = ").append(inStruct.entryType)
					.append("\ninStruct.voidTransactionStatus = ").append(inStruct.voidTransactionStatus)
					.append("\ninStruct.callForAuthorizationStatus = ").append(inStruct.callForAuthorizationStatus);
			logger.debug(logString.toString());
		logger.debug("AmzCollectionCreditCardUE : printInStruct :: exit");
	}

	

}
