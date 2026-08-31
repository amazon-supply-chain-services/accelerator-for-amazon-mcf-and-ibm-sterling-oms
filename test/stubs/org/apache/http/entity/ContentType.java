package org.apache.http.entity;

/**
 * OFFLINE TEST STUB of Apache HttpComponents {@code org.apache.http.entity.ContentType}.
 * Only the {@code APPLICATION_JSON} constant the accelerator references is provided. NOT Apache
 * code and contains no proprietary material; a placeholder so the real production classes compile
 * offline. Test harness use only; the real class comes from the httpclient jar at runtime.
 */
public final class ContentType {

	public static final ContentType APPLICATION_JSON = new ContentType("application/json");

	private final String mimeType;

	private ContentType(String mimeType) {
		this.mimeType = mimeType;
	}

	public String getMimeType() {
		return mimeType;
	}
}
