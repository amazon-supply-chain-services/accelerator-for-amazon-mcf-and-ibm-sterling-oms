package com.yantra.yfs.japi;

/**
 * OFFLINE TEST STUB of IBM Sterling's {@code com.yantra.yfs.japi.YFSEnvironment}.
 *
 * <p>NOT IBM code and contains no IBM proprietary material. Declares only the transaction-object
 * accessors the accelerator touches on the create/reconcile paths. Tests pass {@code null} for the
 * environment and never reach a point that dereferences it before the Sterling boundary. Test
 * harness use only; the real class is supplied by a licensed IBM Sterling OMS at runtime.
 */
public interface YFSEnvironment {

	Object getTxnObject(String key);

	void setTxnObject(String key, Object value);
}
