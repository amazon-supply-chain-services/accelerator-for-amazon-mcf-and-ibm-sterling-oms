# Accelerator for Amazon MCF and IBM Sterling OMS

Sample integration source code for connecting IBM Sterling Order Management System (OMS) to Amazon
Multi-Channel Fulfillment (MCF) and Buy with Prime (BwP), using the
[Selling Partner API (SP-API)](https://developer-docs.amazon.com/sp-api/) for MCF and the Buy with
Prime APIs for BwP.

## About Amazon Supply Chain Services

[Amazon Supply Chain Services](https://supplychain.amazon.com/) provides end-to-end supply chain
solutions, from global shipping and bulk storage to fulfillment and last-mile delivery, enabling
merchants to leverage Amazon's logistics network for orders across any sales channel. Key services
include:

- **Multi-Channel Fulfillment (MCF)** — Fast, reliable fulfillment for orders from any ecommerce channel
- **Buy with Prime (BwP)** — The Prime shopping experience (fast, free delivery and a trusted checkout) on a merchant's own site
- **Amazon Warehousing & Distribution (AWD)** — Upstream bulk inventory storage with auto-replenishment to fulfillment centers
- **Multi-Channel Distribution (MCD)** — Bulk inventory distribution to wholesalers and B2B partners

This repository is maintained by the Amazon Supply Chain Services (ASCS) Solutions Architecture team.

## Overview

This repository builds on the **Buy with Prime Accelerator for IBM Sterling OMS** base package
originally published by [Perfaware](https://perfaware.com/amazon-buy-with-prime-accelerator/). The
base package provides Buy with Prime integration for IBM Sterling OMS using the Buy with Prime APIs.
This repository adds **native Amazon MCF support via SP-API** on top of that base.

Unlike a linked/imported base package, **the BwP base is included directly in this repository's
`src/` tree** and has been extended in place with the MCF additions. The result is a single merged
Sterling extensions codebase:

- **Base (BwP)** — the Perfaware Buy with Prime accelerator, generalized to remove merchant-specific
  references and credentials. Provides the full set of BwP flows.
- **Add-on (MCF)** — new classes and services that add native Amazon MCF via SP-API, layered on top
  of the base and reusing the same OMS-side plumbing (services, agents, common utilities).

Both integrations run in the same Sterling OMS runtime and share the OMS order/shipment model; they
differ in how they talk to Amazon.  BwP uses the Buy with Prime APIs, MCF uses SP-API.

## Architecture

```
  IBM Sterling OMS                                           Amazon
  (this codebase, in the OMS runtime)                        Buy with Prime & MCF
  ┌───────────────────────┐                                 ┌───────────────────────┐
  │                       │  ── outbound: create / cancel ──►│                       │
  │  IBM Sterling OMS +   │                                  │   Amazon Buy with     │
  │  this accelerator     │◄─ inbound: fulfillment updates ──│   Prime and Multi-    │
  │  (processors, agents, │    (shipment / delivery / cancel)│   Channel Fulfillment │
  │   services)           │◄─ inbound: inventory sync ───────│   (MCF)               │
  │                       │    (real-time + full-sync)       │                       │
  └───────────────────────┘         REST / JSON, HTTPS       └───────────────────────┘
```

- **Outbound (OMS → Amazon):** orders released in Sterling are transformed and submitted to Amazon
  (BwP `createOrder` GraphQL mutation, or MCF SP-API `createFulfillmentOrder`). The Amazon order ID
  is stamped back onto the OMS order/line to mark it processed.
- **Inbound — fulfillment updates (Amazon → OMS):** shipment, delivery/milestone, and cancel/status
  updates are applied in Sterling (confirm shipment, update tracking/milestones, cancel quantities).
  *(MCF)* a **reconciliation agent** periodically pulls an order snapshot from Amazon and generates
  these events, which drive the connector's event interface. The connector does not consume Amazon
  notifications directly; the reconciliation agent is the source of these events. The event
  processors are designed to also support a notification-based path (receiver classes are present
  in the codebase), so adopting MCF notifications in the future requires minimal rework.
- **Inbound — inventory sync (Amazon → OMS):** the **full-sync agent** periodically pulls Amazon's
  inventory summary (SP-API `getInventorySummaries`) and updates availability in Sterling. *(BwP)*
  additionally receives real-time inventory-change events from Amazon into the OMS.

## Integration Flows

**Base (BwP)** = provided by the Perfaware base package (Buy with Prime APIs), included in `src/`.
**MCF (SP-API)** = added in this repository for native Amazon MCF via SP-API.

The MCF add-on covers the core order lifecycle and inventory flows. The full returns/refunds and
BwP-specific cancellation paths are provided by the BwP base.

### Order Creation

| Flow | Description | Base (BwP) | MCF (SP-API) |
|------|-------------|:----------:|:------------:|
| Create Fulfillment Order in Amazon | Creates a fulfillment order in Amazon when an order is released in Sterling; stamps the Amazon order ID back on the OMS order line | ✅ | ✅ |

### Delivery & Fulfillment Events (generated by the reconciliation agent)

For MCF, the reconciliation agent generates these events from the Amazon order snapshot and triggers
the connector's event interface to apply the corresponding update in Sterling.

| Flow | Description | Base (BwP) | MCF (SP-API) |
|------|-------------|:----------:|:------------:|
| Shipment event | Confirms the shipment (creates the container) in Sterling | ✅ | ✅ |
| Milestone events | Records package delivery milestone / status updates against the container | ✅ | ✅ |
| Cancel event | Applies cancelled / unfulfillable quantities in Sterling | ✅ | ✅ |

### Reconciliation (inbound) — MCF only

| Flow | Description | Base (BwP) | MCF (SP-API) |
|------|-------------|:----------:|:------------:|
| Reconciliation agent | For MCF, periodically pulls an order snapshot from Amazon and generates the shipment / milestone / cancel events that drive the corresponding updates in Sterling | | ✅ |

> **How MCF inbound updates work.** The reconciliation agent is the source of the inbound updates: it
> polls Amazon for an order snapshot and generates the shipment, milestone, and cancel events that the
> connector's event interface then applies in Sterling.

### Inventory

| Flow | Description | Base (BwP) | MCF (SP-API) |
|------|-------------|:----------:|:------------:|
| Real-time inventory | Receives real-time inventory-change events from Amazon and updates availability in Sterling | ✅ | |
| Inventory full-sync agent | Periodically pulls Amazon's inventory summary (SP-API `getInventorySummaries`) and updates availability in Sterling | ✅ | ✅ |

### Order Cancellation (outbound, OMS → Amazon)

| Flow | Description | Base (BwP) | MCF (SP-API) |
|------|-------------|:----------:|:------------:|
| Cancel from OMS | Cancels an Amazon order when it is cancelled in Sterling | ✅ | |

### Returns & Refunds

| Flow | Description | Base (BwP) | MCF (SP-API) |
|------|-------------|:----------:|:------------:|
| Return — receive return events | Receives return request notifications from Amazon | ✅ | |
| Return — sync return updates | Syncs return status between Amazon and Sterling | ✅ | |
| Return — create / update external return | Creates and updates the external return in Amazon | ✅ | |
| Return — validate invoice details | Validates return/invoice details for refund eligibility | ✅ | |
| Refund — receive refund notification | Receives refund request notifications from Amazon | ✅ | |
| Refund — issue / complete refund | Issues refunds from Sterling and reports completion to Amazon | ✅ | |

## Prerequisites

> **You must supply your own licensed IBM Sterling OMS.** This repository contains Amazon MCF /
> Buy with Prime integration **sample source code only**. It does **not** include, and is not a
> substitute for, any IBM Sterling Order Management System software, libraries, SDK, JARs, or other
> IBM proprietary materials. You are responsible for obtaining and licensing IBM Sterling OMS (and
> its SDK / build toolchain) directly from IBM in order to build and run this connector.

- IBM Sterling OMS (OMoC or On-Premise) — **licensed separately by you, from IBM**
- Java JDK and the Sterling OMS SDK / build toolchain — **provided by IBM under your IBM license**
- Amazon Selling Partner API credentials (for MCF flows)
- Amazon Buy with Prime API credentials (for BwP base flows)

## Repository layout

```
.
├── src/main/java/com/amazon/   # merged BwP base + MCF add-on source
│   ├── integrator/             # Amazon-facing calls (SP-API for MCF, BwP APIs for BwP)
│   ├── oms/                    # OMS-side processors, agents, and services
│   └── common/util/            # shared constants and utilities
├── files/                      # Sterling extension XML / configuration artifacts
├── lib/                        # bundled third-party libraries (org.json, commons-*)
├── docs/                       # install and reference documentation
└── test/                       # offline test harness (see test/README.md)
```

## Installation

This package is distributed as **source code**. Build the Sterling binaries using IBM's standard
extensions approach:

1. Merge the source into your Sterling extensions project (`src/main/java/`), and the extension
   XML / properties into your Sterling runtime configuration.
2. Build the JAR using your standard IBM Sterling OMS build process.
3. Deploy the JAR to your Sterling OMS runtime and import the configuration package via CDT.
4. Configure credentials and properties (SP-API for MCF, Buy with Prime OAuth for BwP) and enable
   the flows you need.

See the documents under `docs/` for detailed build, deploy, and configuration steps (services,
queues, actions, events, and per-flow properties).

## Documentation

- **[Install Addendum — Build & Deploy](docs/Install_Addendum_v0.1.md)** — Step-by-step build and deploy instructions for the MCF add-on on top of the Buy with Prime base package, including prerequisites and configuration.
- **[Technical Design](docs/MCF_IBM_Sterling_OMS_Integration_Flows_Technical_Design.md)** — MCF integration flow architecture, OMS API/method calls per flow, and sequence diagrams (create fulfillment order, reconciliation agent, and inventory full-sync).
- **[Testing & Validation](test/README.md)** — Offline test harness (no licensed IBM Sterling OMS and no Amazon access needed) plus sample payloads validating the MCF create-order and reconciliation flows.

## Testing

> **Experimental / development use only.** The offline test harness under `test/` is intended for
> experimental and development use — to validate the connector's transformation logic before
> deploying to a licensed IBM Sterling OMS runtime. It is **not** a runtime substitute for IBM
> Sterling OMS. For production use, you are expected to obtain and operate a licensed IBM Sterling
> OMS environment (see [Prerequisites](#prerequisites)).

An offline test harness is included under [`test/`](test/README.md). It validates the MCF SP-API
**2026-07-04** integration logic **without a licensed IBM Sterling OMS and without any Amazon
connectivity** — no OMS runtime, no database, no network.

The harness compiles the **real** production classes from `src/main/java` and drives each MCF flow
(create order; reconciliation; and the shipment / milestone / cancel event processing the
reconciliation agent triggers) from its real entry point,
verifying the request documents the connector produces. Because those classes import IBM Sterling
utility types that are only available in a licensed IBM Sterling OMS and are not redistributable,
the harness supplies small **clean-room stubs** of just the methods used (under `test/stubs/`),
backed by the standard JDK XML/JSON APIs. Only API names/signatures are reproduced — the
implementations are original Amazon work, and **no IBM Sterling code or libraries are included or
redistributed** (see [NOTICE](NOTICE), "Test Harness").

Run it (JDK 17–21; Gradle 8.8 does not support JDK 24+):

```
cd test
export JAVA_HOME=/path/to/jdk-21
./gradlew test
```

See [`test/README.md`](test/README.md) and [`test/TEST_REPORT.md`](test/TEST_REPORT.md) for the full
flow-by-flow coverage and scope.

## Third-Party Attribution

This repository includes modified code from the Amazon Buy with Prime Accelerator for IBM Sterling
OMS, originally published under the Apache License 2.0. See [NOTICE](NOTICE) and
[MODIFICATIONS.md](MODIFICATIONS.md) for details.

## Security

This project does not accept external contributions at this time. To report a security issue, please
refer to [CONTRIBUTING.md](CONTRIBUTING.md).

## Disclaimer

This repository contains **sample code** provided for reference and educational purposes only. It is
**not** an official Amazon product and is not covered by any Amazon support agreement or
service-level commitment.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES, OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT, OR OTHERWISE, ARISING FROM, OUT OF, OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

You are solely responsible for testing, validating, securing, and operating this code in your own
environment, including compliance with all applicable IBM and Amazon license terms.

## License

This project is licensed under the Apache License 2.0. See [LICENSE](LICENSE).
