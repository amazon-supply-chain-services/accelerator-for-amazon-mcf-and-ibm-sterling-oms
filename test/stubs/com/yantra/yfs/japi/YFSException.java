package com.yantra.yfs.japi;

/**
 * OFFLINE TEST STUB of IBM Sterling's {@code com.yantra.yfs.japi.YFSException}.
 *
 * <p>NOT IBM code and contains NO IBM proprietary material. A minimal checked exception carrying an
 * error code and description, matching the accessors the accelerator's event processors use. Used
 * only by the offline test harness; the real class is supplied by a licensed IBM Sterling OMS at
 * runtime.
 */
public class YFSException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private String errorCode;
	private String errorDescription;

	public YFSException() {
		super();
	}

	public void setErrorCode(String errorCode) {
		this.errorCode = errorCode;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public void setErrorDescription(String errorDescription) {
		this.errorDescription = errorDescription;
	}

	public String getErrorDescription() {
		return errorDescription;
	}

	@Override
	public String getMessage() {
		return "[" + errorCode + "] " + errorDescription;
	}
}
