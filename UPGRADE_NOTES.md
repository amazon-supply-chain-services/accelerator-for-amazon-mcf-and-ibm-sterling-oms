# MCF Upgrade Notes

This document details the changes made to the IBM Sterling OMS accelerator to add Multi-Channel Fulfillment (MCF) support via the Selling Partner API (SP-API) Fulfillment Outbound (2026-07-04).

---

## 1. New Files (MCF via SP-API Fulfillment Outbound)

### Order Creation
| File | Layer | Description |
|------|-------|-------------|
| `MCFPrepareSPAPICreateFulfillmentOrderRequest.java` | integrator/common/util | Builds SP-API CreateFulfillmentOrder (2026-07-04) request payload |
| `MCFCreateFulfillmentOrderInAmazon.java` | integrator/order/api | Invokes SP-API to create MCF fulfillment order |
| `AmzMassagReleaseOrderMsgWithMCF.java` | oms/order/api | Prepares release order message for MCF flow |
| `AmzProcessReleaseOrderMessageWithMCF.java` | oms/order/api | Processes release order with MCF routing logic |

### Reconciliation Agent

> **Note:** For MCF, this connector does not consume Amazon notifications directly. The
> **reconciliation agent** polls the SP-API order snapshot (2026-07-04 Fulfillment Outbound) and
> generates the delivery events below (order status/cancel, shipment, package), driving the
> corresponding receivers/processors. The three event areas that follow are all reached this way.

| File | Layer | Description |
|------|-------|-------------|
| `GetMCFOrder.java` | integrator/delivery/api | Retrieves single MCF order details via SP-API |
| `ListMCFOrders.java` | integrator/delivery/api | Lists MCF fulfillment orders via SP-API (supports updatedAfter filter) |
| `AmzMCFOrderReconciliationAgent.java` | oms/order/agent | Sterling Agent that polls Amazon for order status changes |
| `AmzReconcileMCFOrderSnapshot.java` | oms/order/agent | Compares Amazon order snapshot against Sterling state and generates the delivery events below |

#### Order Status & Cancellation
| File | Layer | Description |
|------|-------|-------------|
| `AmzReceiveMCFOrderStatusUpdates.java` | integrator/delivery/api | Receiver for order status (cancel) events |
| `AmzProcessMCFOrderStatusChangeEvent.java` | oms/delivery/api | Async processor for order status changes from internal queue |

#### Shipment Tracking
| File | Layer | Description |
|------|-------|-------------|
| `AmzReceiveMCFShipmentStatus.java` | integrator/delivery/api | Receiver for shipment status events |
| `AmzProcessMCFShipmentEvent.java` | oms/delivery/api | Async processor for shipment events from internal queue |

#### Package Delivery
| File | Layer | Description |
|------|-------|-------------|
| `AmzReceiveMCFPackageStatus.java` | integrator/delivery/api | Receiver for package status events |
| `AmzProcessMCFPackageStatusEvent.java` | oms/tracking/api | Async processor for package milestone events from internal queue |

### Inventory Full Sync
| File | Layer | Description |
|------|-------|-------------|
| `AmzMCFProcessFullSyncAgent.java` | inventory/agent | Sterling Agent that performs periodic full inventory reconciliation between Sterling OMS and Amazon MCF |
| `AmzProcessInventoryChange.java` | oms/inventory/api | Processes inventory deltas from Amazon (shared with BwP, enhanced for MCF) |

### SP-API Authentication
| File | Layer | Description |
|------|-------|-------------|
| `SPApiTokenGenerationUtil.java` | common/util | Generates SP-API access tokens using LWA refresh token flow |

---

## 2. Modified Files — Bug Fixes & Refactoring

### AmzCommonConstants.java
**Change:** Added 3 new SP-API URL constants:
- `SP_GET_V2OUTBOUND_URL` — MCF CreateFulfillmentOrder API
- `SP_MCF_GET_FULFILLMENT_ORDER_URL` — MCF GetFulfillmentOrder API
- `SP_MCF_LIST_FULFILLMENT_ORDERS_URL` — MCF ListFulfillmentOrders API

### AmzGetGenericProperty.java
**Change:** Registered the 3 new MCF URL properties so they load at runtime.

### AmzLiterals.java
**Change:** Added `A_SALES_ORDER_NO = "SalesOrderNo"` literal. Renamed "Merchant Literals" section to "CLOB column to Custom table changes".

### AmzInvokeAmazonCancelRequest.java
**Change:** Replaced hardcoded `amzConn.Merchant.*` property keys with generic `amzConn.Merchant.*` keys. Makes cancel logic merchant-agnostic.

### AmzProcessDeliveryCancelledEvent.java
**Change:** Fixed cancel quantity handling — uses `ChangeInQty` with negative delta instead of overwriting `OrderedQty`. This is the correct Sterling OMS approach for reducing release quantities.

### AmzMassagReleaseOrderMsg.java
**Change:** Removed `removeOtherReleaseOrderLines()` method. All order lines are now sent to Amazon regardless of release association. Required because MCF creates fulfillment orders at the full order level.

### AmzUpdateOrdWithAmazonOrdInfo.java
**Change:** Removed `SelectMethod="WAIT"` — avoids synchronous blocking on changeOrder API calls.

### AmzProcessRefundRequestedEvent.java
**Change:** Unknown reason codes now default to "UNKNOWN" instead of throwing exceptions. Changed `UseOrderLineCharges` from N to Y. More resilient to unexpected Amazon reason codes.

### AmzProcessReturnUpdates.java
**Change:** Removed hardcoded `DocumentType=RO`. Restored `EntryType=WEB_CHANNEL`. Added `ExtnOMSManagedOrder="Y"` for downstream processing.

### AmzUpdateTrackingMilestones.java
**Change:** Added null check on milestone message element to prevent NullPointerException.

---

## 3. Configuration Changes

New services, queues, actions, and events required for MCF are documented in the Install Addendum (`doc/Install_Addendum_v0.1.md`).

Key additions:
- Services for MCF order creation routing (via `AmzProcessCreateAndReleaseOrdMsgAsync`)
- Sync and async cancel services for MCF
- Event database services (AmzConnGetEventsList, Create, Update)
- `ON_RELEASE_CREATION_OR_CHANGE` event handler for MCF order routing
- Queue: `AMZ.CONN.CANCEL.ORDER.INT.Q`
- Integration server: `AmzConnProcessOrderCancelMsgFromOMSInteg`

---

## 4. Architecture Pattern

MCF events follow an async webhook pattern:

```
Amazon SP-API → Webhook (Receiver class) → JMS Queue → Async Processor → Sterling OMS API
```

Each MCF event type (order status, shipment, package) has:
1. A **receiver** class (integrator layer) — validates and enqueues
2. A **processor** class (oms layer) — dequeues and updates Sterling

This decouples webhook response time from OMS processing, ensuring Amazon webhooks receive timely HTTP 200 responses.

---

## 5. Order Reconciliation (Polling)

In addition to real-time webhooks, the MCF integration includes a polling-based order reconciliation agent that provides a safety net for missed events.

| File | Layer | Description |
|------|-------|-------------|
| `AmzMCFOrderReconciliationAgent.java` | oms/order/agent | Sterling Agent that periodically polls Amazon via `ListMCFOrders` (SP-API) using an `updatedAfter` timestamp to find orders with status changes |
| `AmzReconcileMCFOrderSnapshot.java` | oms/order/agent | Compares Amazon's order snapshot against Sterling OMS state and triggers updates for any discrepancies |
| `ListMCFOrders.java` | integrator/delivery/api | Calls SP-API ListFulfillmentOrders with filters (updatedAfter, status) |
| `GetMCFOrder.java` | integrator/delivery/api | Calls SP-API GetFulfillmentOrder for a single order's full details |

**How it works:**
1. Agent runs on a configurable schedule (Sterling Agent framework)
2. Calls `ListMCFOrders` with `updatedAfter` = last successful run timestamp
3. For each returned order, compares Amazon status against Sterling OMS status
4. If discrepancy found, processes the update as if it were a webhook event
5. Updates the `updatedAfter` watermark for next run

**Use case:** Catches order status changes (shipped, cancelled, delivered) that may have been missed due to webhook delivery failures, network issues, or system downtime. Recommended to run every 15-30 minutes.

---

## 6. Inventory Full Sync

The `AmzMCFProcessFullSyncAgent` is a Sterling Agent that runs on a configurable schedule to reconcile inventory between Sterling OMS and Amazon MCF. It:

1. Queries Sterling for current available inventory across configured items
2. Compares against Amazon's inventory summary (via SP-API GetInventorySummaries)
3. Generates adjustment events for discrepancies
4. Processes adjustments through `AmzProcessInventoryChange`

This provides a safety net beyond real-time inventory change events, catching drift from missed events, timing issues, or system failures.

---

## 7. SP-API Fulfillment Outbound

The MCF additions use SP-API **Fulfillment Outbound** (2026-07-04):
- `POST /fulfillment/outbound/2026-07-04/orders` — Create fulfillment order
- `GET /fulfillment/outbound/2026-07-04/orders/{orderId}` — Get order
- `GET /fulfillment/outbound/2026-07-04/orders` — List orders

Authentication uses LWA (Login with Amazon) refresh token flow via `SPApiTokenGenerationUtil`.
