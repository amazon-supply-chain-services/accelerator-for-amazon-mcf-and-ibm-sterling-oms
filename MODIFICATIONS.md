# Modifications Notice

This repository contains integration code derived from the
[Amazon Buy with Prime Accelerator for IBM Sterling OMS](https://perfaware.com/amazon-buy-with-prime-accelerator/),
originally published under the Apache License 2.0.

All files in this repository have been modified by Amazon unless otherwise noted.
Modifications include:

- Removed hardcoded security credentials, API tokens, and environment-specific endpoints — replaced with placeholders
- Generalized merchant-specific references to support any enterprise deployment
- Retained original merchant installation and configuration documentation
- Added install addendum with updated build and configuration instructions
- Upgraded code to support Multi-Channel Fulfillment (MCF) via the SP-API Fulfillment Outbound (2026-07-04) API (see [UPGRADE_NOTES.md](UPGRADE_NOTES.md))
- Applied bug fixes to cancel, refund, return, and tracking flows

## Test Harness (Amazon — original work)

The offline test harness under `test/` is original Amazon work. To execute the
production transformation classes locally — without a licensed IBM Sterling OMS
runtime, and without any IBM or Amazon connectivity — it includes:

- Minimal clean-room re-implementations of the third-party API **shapes** the
  production classes `import` and must compile against: IBM Sterling types (for
  example `com.sterlingcommerce.baseutil.SCXmlUtil`; `com.yantra.yfc.core.YFCObject`;
  `com.yantra.yfc.log.YFCLogCategory`; `com.yantra.yfc.dom.YFCDocument` / `YFCElement` /
  `YFCNodeList`; `com.yantra.yfs.japi.YFSEnvironment` / `YFSException`;
  `com.yantra.interop.japi.YIFCustomApi` / `YIFClientCreationException`;
  `com.ibm.sterling.afc.jsonutil.PLTJSONUtils`), the accelerator's own helper classes
  (`AmzXMLUtil`, `AmzCommonUtil`, `AmzGetGenericProperty`, `AmzRestWebserviceUtil`), and
  minimal Apache HttpComponents placeholders (`org.apache.http.entity.StringEntity` /
  `ContentType`). Only the class/API names and the method signatures the code uses are
  reproduced; the implementations are entirely original Amazon work, backed by the
  standard JDK XML/JSON APIs. **No IBM Sterling-authored code, headers, or libraries are
  copied, included, or redistributed.**

The harness depends only on JUnit (EPL-2.0), downloaded from Maven Central at build
time, and `org.json` (Apache-2.0) bundled under `lib/`. See [NOTICE](NOTICE),
"Test Harness".

Original source: https://perfaware.com/amazon-buy-with-prime-accelerator/
Original license: Apache-2.0
