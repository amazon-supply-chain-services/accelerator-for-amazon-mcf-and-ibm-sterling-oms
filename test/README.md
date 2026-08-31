# Testing & Validation

This folder contains an **offline test harness** and sample payloads that validate the
MCF + IBM Sterling OMS integration flows against the Amazon SP-API **2026-07-04** format.

**The tests run entirely offline** — no IBM Sterling OMS runtime, no OMS database, no Amazon
SP-API, and no network. They exercise the *actual* production classes from `../src/main/java`
against the sample fixtures and verify that each flow processes its input into the expected result.

> You do **not** need access to Amazon or IBM Sterling OMS to run these tests.

> **See also:** [`TEST_REPORT.md`](TEST_REPORT.md) — the flow-by-flow test report (design,
> coverage, fixtures, results, and scope/boundary).

> **Note on inbound events.** This connector does **not** consume Amazon notifications directly.
> For MCF, the **reconciliation agent** periodically pulls the 2026-07-04 order snapshot and
> internally generates event-shaped snapshots for the shipment, package-status (milestone), and
> cancel scenarios, which it hands to the connector's event processors. The tests therefore drive
> those three scenarios **through the real reconciliation path** — there is no separate "consume an
> Amazon event" flow.

---

## The flows

| # | Flow | Direction | Input fixture | Real classes exercised | Verified outcome |
|---|------|-----------|---------------|------------------------|------------------|
| 1 | **Create Fulfillment Order** | OMS → Amazon | `samples/order_from_sterling.xml` | `MCFCreateFulfillmentOrderInAmazon` → `MCFPrepareSPAPICreateFulfillmentOrderRequest` | Produced SP-API `createFulfillmentOrder` JSON **equals** `expected/expected_createOrder.json` |
| 2 | **Reconciliation → shipment** | Amazon → OMS | `samples/recon_shipment_snapshot.json` (+ scripted OMS responses) | `ListMCFOrders` → `AmzReconcileMCFOrderSnapshot` → `AmzMCFSnapshotPayloadBuilder` → `AmzProcessMCFShipmentEvent` | **Builds the OMS `confirmShipment` request**; matches `expected/expected_confirmShipment.xml` |
| 3 | **Reconciliation → milestone** | Amazon → OMS | `samples/recon_milestone_snapshot.json` (+ scripted OMS responses) | `ListMCFOrders` → `AmzReconcileMCFOrderSnapshot` → `AmzMCFSnapshotPayloadBuilder` → `AmzProcessMCFPackageStatusEvent` | Reaches the OMS milestone update (`AmzConnUpdateMilestonesRecordInOMS`) |
| 4 | **Reconciliation → cancel** | Amazon → OMS | `samples/recon_cancel_snapshot.json` (+ scripted OMS response) | `ListMCFOrders` → `AmzReconcileMCFOrderSnapshot` → `AmzMCFSnapshotPayloadBuilder` → `AmzProcessMCFOrderStatusChangeEvent` | **Builds the OMS `changeRelease` request**; matches `expected/expected_changeRelease.xml` |

Flows 2–4 are all the **reconciliation** flow — each run feeds the agent a different 2026-07-04
snapshot so it generates one scenario (shipment, milestone, or cancel) and drives the matching real
processor deep to its OMS operation. This is the way production actually produces these updates.

The reconciliation snapshots are returned by the **2026-07-04 Fulfillment Outbound create/list
API** (`createFulfillmentOrder` / `ListFulfillmentOrders`), which is live. The reconciliation agent
reads each snapshot and converts it into **internal** event-shaped snapshots for the
shipment/milestone/cancel scenarios (an internal representation used to invoke the processors).

### Flow 1 — Create Fulfillment Order

The Sterling ReleaseOrder XML is fed to the real `MCFCreateFulfillmentOrderInAmazon` (invoked in
production by `AmzProcessReleaseOrderMessageWithMCF`), which uses the real request builder to
produce the SP-API JSON and POST it to Amazon. Offline, the SP-API HTTP client is stubbed so the
POST is **captured** instead of sent, and the captured request body is compared field-by-field to
`expected/expected_createOrder.json`. A null release key is used (offline path), so `orderId` is
the base `OrderNo` without a release suffix.

### Flows 2–4 — Reconciliation (drives shipment / milestone / cancel)

Each reconciliation test stubs the SP-API GET to return a 2026-07-04 snapshot; the real
`ListMCFOrders` converts it (via `PLTJSONUtils`) and extracts the `orders` snapshot, and the real
`AmzReconcileMCFOrderSnapshot` reconciles it against OMS. Based on the snapshot's state (and the
scripted OMS lookups), the agent generates exactly one internal event via
`AmzMCFSnapshotPayloadBuilder` and invokes the matching real processor:

- **Shipment** (`recon_shipment_*`): with `getShipmentContainerList` scripted empty, the agent
  generates the shipment event and the shipment processor **builds the `confirmShipment` request**
  (verified against `expected/expected_confirmShipment.xml`).
- **Milestone** (`recon_milestone_*`): with the container scripted populated (shipment already
  recorded, so that branch skips) and the milestone-list scripted empty, the agent generates the
  package-status event and the processor reaches the OMS milestone update.
- **Cancel** (`recon_cancel_*`): with the OMS line scripted Released and a cancelled delta > 0, the
  agent generates the order-status event and the cancel processor **builds the `changeRelease`
  request** (verified against `expected/expected_changeRelease.xml`).

The OMS lookups the agent and processors perform are **scripted** with sanitized, real-shaped
responses (see `samples/oms/`), so the code runs *past* those lookups up to the point where a step
would change OMS state.

### The Sterling boundary

Each flow runs the real production code through its OMS lookups (scripted offline) up to the point
where a step would change OMS state — `confirmShipment`, `changeRelease`, `changeOrder`, milestone
updates — which require a licensed IBM Sterling OMS. The tests verify the request documents the
connector builds for those operations; executing the operations themselves is the final,
integration-time gate.

---

## How it works

The production classes import IBM Sterling utility types (`SCXmlUtil`, `YFCObject`, the
`com.yantra.*` / `com.ibm.sterling.*` families) and connector helpers that only exist inside a
licensed IBM Sterling OMS and are **not** redistributable. To run the real classes offline, the
harness provides small **clean-room stubs** of only the methods used, under `stubs/`:

- `com/ibm/sterling/afc/jsonutil/PLTJSONUtils` — JSON→XML conversion (scalars→attributes,
  objects/arrays→child elements), backed by the bundled `org.json`
- `com/amazon/common/util/AmzRestWebserviceUtil` — SP-API HTTP client; returns scripted sample
  responses and captures the POSTed request body so the produced JSON can be asserted
- `com/amazon/common/util/AmzCommonUtil` — the OMS-invocation helper; scriptable per OMS
  API/service name, captures the input document, and throws a tagged boundary marker at the chosen
  stop (or any un-scripted OMS call)
- `com/amazon/common/util/{AmzXMLUtil,AmzGetGenericProperty}` — clean-room helpers
- `com/sterlingcommerce/baseutil/SCXmlUtil`, `com/yantra/yfc/core/YFCObject`,
  `com/yantra/yfc/log/YFCLogCategory`, `com/yantra/yfc/dom/{YFCDocument,YFCElement,YFCNodeList}`,
  `com/yantra/yfs/japi/{YFSEnvironment,YFSException}`, `com/yantra/interop/japi/{YIFCustomApi,
  YIFClientCreationException}` — thin JDK-XML-backed Sterling stubs
- `org/apache/http/entity/{StringEntity,ContentType}` — minimal placeholders for the request entity

> `AmzLiterals` and `AmzCommonConstants` are pure constant classes (no imports), so the harness
> compiles the **real** production classes to guarantee exact constant values.

The Gradle build copies the named production source files into a generated source dir and compiles
them with the stubs — so the tests exercise the genuine production code, not a copy.

---

## Prerequisites

- **JDK 17–21** (Java 21 recommended). **Gradle 8.8 does not support JDK 24+** — set `JAVA_HOME`
  to a 17–21 JDK.
- A Gradle wrapper (`./gradlew`) is included.
- Internet access is needed **only the first time**, so Gradle can download JUnit from Maven
  Central. `org.json` and `commons-lang3` come from the accelerator's bundled `../lib`.

---

## Running the tests

From this `test/` directory:

```bash
export JAVA_HOME=/path/to/jdk-21     # e.g. Corretto 21; Gradle 8.8 rejects JDK 24+
./gradlew test
```

A successful run reports **4 tests** passing: create-order plus the three reconciliation scenarios
(shipment, milestone, cancel). HTML report: `build/reports/tests/test/index.html`. A written
summary of the run is in [`TEST_REPORT.md`](TEST_REPORT.md).

Run a single flow:
```bash
./gradlew test --tests "com.amazon.mcf.ibm.test.ReconShipmentTest"
```

---

## Folder layout

```
test/
├── gradlew / gradlew.bat   # Gradle wrapper (Gradle 8.8)
├── gradle/wrapper/
├── build.gradle            # stages named production sources + compiles with stubs; JUnit 5
├── settings.gradle
├── README.md               # this file
├── samples/                # 2026-07-04 input fixtures
│   ├── order_from_sterling.xml                 # Flow 1 input (Sterling ReleaseOrder XML)
│   ├── recon_shipment_snapshot.json          # Flow 2 input (recon snapshot → shipment)
│   ├── recon_milestone_snapshot.json         # Flow 3 input (recon snapshot → milestone)
│   ├── recon_cancel_snapshot.json            # Flow 4 input (recon snapshot → cancel)
│   ├── listorders_from_amazon_2026-07-04.json  # reference ListFulfillmentOrders snapshot
│   └── oms/                                    # scripted OMS responses (sanitized real captures)
│       ├── getShipmentContainerList_empty_output.xml
│       ├── getShipmentContainerList_populated_output.xml
│       ├── getAmzConnContainerMilestonesList_empty_output.xml
│       ├── getOrderLineList_output.xml
│       └── getOrderLineList_recon_output.xml
├── expected/               # expected produced documents
│   ├── expected_createOrder.json               # Flow 1 — SP-API create request JSON
│   ├── expected_confirmShipment.xml            # Flow 2 — OMS confirmShipment request
│   └── expected_changeRelease.xml              # Flow 4 — OMS changeRelease request
├── stubs/                  # clean-room stubs of Sterling/accelerator helper types
└── src/test/java/com/amazon/mcf/ibm/test/
    ├── CreateOrderTest.java         # Flow 1 — Create Fulfillment Order
    ├── ReconShipmentTest.java       # Flow 2 — Reconciliation → shipment (builds confirmShipment)
    ├── ReconMilestoneTest.java      # Flow 3 — Reconciliation → milestone (reaches OMS update)
    ├── ReconCancelTest.java         # Flow 4 — Reconciliation → cancel (builds changeRelease)
    ├── EventTestSupport.java        # shared helper: OMS boundary assertion
    └── OmsTestSupport.java          # shared helpers: fixture load + semantic XML compare
```

---

## Scope

Covers the connector's core flows on SP-API 2026-07-04: outbound create-fulfillment-order
(produced JSON compared to a golden file), and the reconciliation flow driving the three inbound
scenarios (shipment, milestone, and cancel — each generated by the reconciliation agent and handed
to its real processor). Each is driven from its real entry point and run through its OMS lookups;
the shipment and cancel scenarios additionally build and verify the OMS request document they
produce (`confirmShipment` / `changeRelease`). Execution stops at the first live OMS call. Beyond
that boundary the flows require a licensed IBM Sterling OMS; a full IBM Sterling build and
integration run remains the final gate before release and cannot be performed offline.
