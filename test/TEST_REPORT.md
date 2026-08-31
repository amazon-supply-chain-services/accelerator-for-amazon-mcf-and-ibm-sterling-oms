# Test Report — MCF + IBM Sterling OMS Accelerator

This report describes the offline test suite for the Amazon Multi-Channel Fulfillment (MCF)
integration with IBM Sterling Order Management System (OMS), validated against the Amazon
SP-API **2026-07-04** format.

- **Suite type:** offline unit/integration harness (JUnit 5, Gradle)
- **Runtime required:** JDK 17–21 only — no IBM Sterling OMS, no Amazon SP-API, no network
- **Result:** ✅ BUILD SUCCESSFUL — **4 tests, 4 passed, 0 failed**
- **Environment used for this report:** Gradle 8.8, JDK 21 (Amazon Corretto 21.0.11)

> **Inbound events.** This connector does not consume Amazon notifications directly. For MCF, the
> reconciliation agent pulls the 2026-07-04 order snapshot and internally generates event-shaped
> snapshots for the shipment, package-status (milestone), and cancel scenarios, which it hands to
> the connector's event processors. The three inbound scenarios below are therefore driven **through
> the reconciliation agent**, which is how production produces them.

---

## 1. Design

The connector's production classes are written against IBM Sterling and Amazon SP-API runtimes.
To validate their logic without those licensed/remote systems, the harness:

1. **Compiles the real production classes** from `../src/main/java` (unmodified) into the test
   build.
2. **Links them against small clean-room stubs** (in `stubs/`) of the third-party types they
   import — IBM Sterling platform classes (`SCXmlUtil`, `YFC*`, `YFS*`, `PLTJSONUtils`, …), the
   SP-API HTTP client, and a couple of connector helpers. The stubs reproduce only the observable
   behavior each production class depends on, backed by the standard JDK XML/JSON APIs.
3. **Drives each flow with real, sanitized fixtures** and verifies the outcome.

**Deep (past-the-boundary) verification.** The OMS-invocation helper stub is *scriptable*: a test
can register canned OMS responses (sanitized real captures) for specific OMS operations, and choose
the operation at which to stop and capture the request document the connector built. This lets the
tests run the genuine connector logic **through** its first OMS lookups and assert the **actual OMS
request documents it produces** (e.g. `confirmShipment`, `changeRelease`) — not merely that it
reached an OMS call. Where a downstream step needs OMS-internal behavior that cannot be reproduced
offline, the test stops at that operation.

---

## 2. Coverage — the connector flows

There are two production entry points exercised: outbound **create fulfillment order**, and inbound
**reconciliation**. The reconciliation flow is run three times with different 2026-07-04 snapshots
so the agent generates one scenario per run (shipment, milestone, cancel) and drives the matching
real processor deep to its OMS operation.

### Flow 1 — Create Fulfillment Order (OMS → Amazon)

| Aspect | Detail |
|--------|--------|
| Test class | `CreateOrderTest` |
| Input | `samples/order_from_sterling.xml` |
| Real code exercised | `MCFCreateFulfillmentOrderInAmazon` → `MCFPrepareSPAPICreateFulfillmentOrderRequest` |
| Depth | The SP-API HTTP POST is captured (client stubbed) |
| Verified | The produced `createFulfillmentOrder` request JSON matches `expected/expected_createOrder.json` **field-by-field** |

### Flow 2 — Reconciliation → shipment (Amazon → OMS) — deep

| Aspect | Detail |
|--------|--------|
| Test class | `ReconShipmentTest` |
| Input | `samples/recon_shipment_snapshot.json` (snapshot with a shipped shipment/package) |
| Real code exercised | `ListMCFOrders` → `AmzReconcileMCFOrderSnapshot` → `AmzMCFSnapshotPayloadBuilder` → `AmzProcessMCFShipmentEvent` |
| Scripted OMS responses | `getShipmentContainerList_empty_output.xml` (no container → create shipment), `getOrderLineList_recon_output.xml` (Released line) |
| Depth | Reconciliation generates the shipment event; the processor runs through the OMS lookups and **builds the `confirmShipment` request**, stopping at the `confirmShipment` call |
| Verified | The produced `confirmShipment` document matches `expected/expected_confirmShipment.xml` (tracking, delivery id, carrier, quantity from the snapshot; OrderNo/OrderReleaseKey/OrderLineKey/ShipNode from the OMS order-line response) |

### Flow 3 — Reconciliation → milestone (package status) (Amazon → OMS) — deep

| Aspect | Detail |
|--------|--------|
| Test class | `ReconMilestoneTest` |
| Input | `samples/recon_milestone_snapshot.json` (snapshot with a delivered package) |
| Scripted OMS responses | `getShipmentContainerList_populated_output.xml` (container exists → shipment branch skips), `getAmzConnContainerMilestonesList_empty_output.xml` (milestone not yet recorded), `getOrderLineList_recon_output.xml` |
| Depth | Reconciliation generates the package-status event; the processor proceeds to the OMS milestone update, stopping at `AmzConnUpdateMilestonesRecordInOMS` |
| Verified | The reconciliation chain generates and processes the milestone event and reaches the OMS milestone-update operation |

### Flow 4 — Reconciliation → cancel (order status) (Amazon → OMS) — deep

| Aspect | Detail |
|--------|--------|
| Test class | `ReconCancelTest` |
| Input | `samples/recon_cancel_snapshot.json` (snapshot with a cancelled quantity) |
| Scripted OMS response | `getOrderLineList_recon_output.xml` (Released line; cancelled delta > 0) |
| Depth | Reconciliation generates the order-status event; the processor runs through the order-line lookup and **builds the `changeRelease` request**, stopping at the `AmzChangeRelease` call |
| Verified | The produced `changeRelease` document matches `expected/expected_changeRelease.xml` (Override, OrderHeaderKey/OrderReleaseKey, Action=CANCEL, negative ChangeInQuantity, ShipNode) |

---

## 3. Fixtures

All values are sample/non-real; the OMS responses are sanitized captures of real IBM Sterling OMS
output (structure preserved, identifiers replaced with sample values).

**Inputs**

| Fixture | Used by |
|---------|---------|
| `samples/order_from_sterling.xml` | Flow 1 |
| `samples/recon_shipment_snapshot.json` | Flow 2 |
| `samples/recon_milestone_snapshot.json` | Flow 3 |
| `samples/recon_cancel_snapshot.json` | Flow 4 |
| `samples/listorders_from_amazon_2026-07-04.json` | reference ListFulfillmentOrders snapshot |

**Scripted OMS responses** (`samples/oms/`)

| Fixture | Role |
|---------|------|
| `getShipmentContainerList_empty_output.xml` | No existing container → confirm a new shipment |
| `getShipmentContainerList_populated_output.xml` | Existing container → milestone update path |
| `getAmzConnContainerMilestonesList_empty_output.xml` | No milestone recorded yet → milestone needed |
| `getOrderLineList_output.xml` | OMS order line (Released) reference |
| `getOrderLineList_recon_output.xml` | OMS order line aligned to the reconciliation snapshot |

**Expected produced documents** (`expected/`)

| Fixture | Role |
|---------|------|
| `expected_createOrder.json` | Expected SP-API create request (Flow 1) |
| `expected_confirmShipment.xml` | Expected OMS confirmShipment request (Flow 2) |
| `expected_changeRelease.xml` | Expected OMS changeRelease request (Flow 4) |

---

## 4. Results

```
CreateOrderTest    > Sterling Order XML -> SP-API createFulfillmentOrder JSON (2026-07-04)              PASSED
ReconShipmentTest  > Reconciliation -> shipment scenario -> builds the OMS confirmShipment request      PASSED
ReconMilestoneTest > Reconciliation -> milestone scenario -> reaches the OMS milestone update           PASSED
ReconCancelTest    > Reconciliation -> cancel scenario -> builds the OMS changeRelease request           PASSED

BUILD SUCCESSFUL — 4 tests, 4 passed, 0 failed
```

The produced-document comparisons are semantic (element + attribute values, order/whitespace
insensitive) and were confirmed to fail on any mismatch (negative-control checked), so a passing
result reflects a genuine match.

---

## 5. How to run

From this `test/` directory:

```bash
export JAVA_HOME=/path/to/jdk-21     # JDK 17–21; Gradle 8.8 does not support JDK 24+
./gradlew test
```

Run a single flow, e.g. the reconciliation shipment scenario:

```bash
./gradlew test --tests "com.amazon.mcf.ibm.test.ReconShipmentTest"
```

The HTML report is written to `build/reports/tests/test/index.html`.

---

## 6. Scope & boundary (what these tests do and do not cover)

**Covered:** the connector's own logic — 2026-07-04 JSON/XML parsing, the create-order request JSON
produced, the reconciliation agent's generation of the shipment/milestone/cancel events from a
snapshot, and the OMS request documents the connector builds (`confirmShipment`, `changeRelease`)
verified against sanitized real-shaped references, plus the control flow that drives each path to
its OMS operation.

**Not covered (by design — requires a licensed IBM Sterling OMS):** the behavior *inside* the OMS
operations (what `confirmShipment` / `changeRelease` / `changeOrder` / the milestone services do to
OMS state) and the internal behavior of the real IBM Sterling utility classes. The scripted OMS
responses reproduce the observable shape the connector reads, but they are not the IBM
implementations.

**Final gate before release:** a full build against a real IBM Sterling OMS and an end-to-end
integration run. That step is outside this offline suite.
