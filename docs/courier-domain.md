# Courier domain

`com.midnightmunchies.domain.dlv.Courier` has a UUID, a validated latitude/longitude
`Location`, a `CourierState`, and an optional assigned `Order`. New couriers start
`OFFLINE` with no assignment. `updateLocation` changes their coordinates without
changing their duty state or assignment.

| Command | Required courier state | Required order status | Result |
| --- | --- | --- | --- |
| `goOnDuty()` | `OFFLINE` | — | `IDLE` |
| `goOffDuty()` | `IDLE` | — | `OFFLINE` |
| `acceptOrder(order)` | `IDLE` | `READY` | `ASSIGNED`; retain the offered order |
| `confirmPickup()` | `ASSIGNED` | `READY` | `CARRYING`; order becomes `PICKED_UP` |
| `confirmDelivery()` | `CARRYING` | `PICKED_UP` | `IDLE`; order becomes `DELIVERED`; clear assignment |

Every other courier transition throws `IllegalCourierTransitionException` with
the attempted action and current state. Readiness failures also identify the
order and its status. Guards run before mutation; failed commands preserve the
courier's state and assignment. Assignment and pickup both check the order's
current readiness. Only idle couriers can go off duty, so assigned couriers must
complete their delivery before going offline.

Pickup and delivery use the existing ORD state pattern and record pending
`OrderTransition` values. Application services can persist and publish those
transitions through `OrderTransitions.save(order, actor)`, as with restaurant
commands. The courier domain itself handles no persistence, dispatch selection,
route planning, traffic, travel time, or assignment optimization.

Run the domain tests from `backend/` in PowerShell:

```powershell
.\mvnw.cmd '-Dtest=com.midnightmunchies.domain.dlv.*Tests,com.midnightmunchies.domain.ord.*Tests' test
```
