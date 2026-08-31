package org.apache.http.entity;

/**
 * OFFLINE TEST STUB of Apache HttpComponents {@code org.apache.http.entity.StringEntity}.
 *
 * <p>NOT Apache code and contains no proprietary material. It keeps just enough to let the real
 * production create-order path compile and run offline: it stores the request body string the
 * accelerator wraps, and exposes it via {@link #getContentString()} so the harness can assert the
 * exact SP-API request JSON that was produced. The real class comes from the httpclient jar at
 * runtime; this stub is used only by the test harness.
 */
public class StringEntity {

	private final String content;
	private final ContentType contentType;

	public StringEntity(String content) {
		this(content, null);
	}

	public StringEntity(String content, ContentType contentType) {
		this.content = content;
		this.contentType = contentType;
	}

	/** The request body string (used by tests to assert the produced SP-API JSON). */
	public String getContentString() {
		return content;
	}

	public ContentType getContentType() {
		return contentType;
	}
}
