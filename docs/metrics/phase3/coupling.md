# Class coupling of the five hotspots (phase3, counted by hand)

Lizard does not measure coupling. The spec asks for it on the five hotspots only, counted by hand as the number of distinct classes a class references directly. The same rule is used for phase4-refactored and phase4.

## Counting rule

### Java
A type is counted once, however often it is used, if the class names it in any of these places:
fields, constructor and method parameters, return types, local variables, `new` expressions, static calls, `implements` / `extends`, thrown or caught exceptions, generic arguments, and annotations.

| Counted | Not counted |
|---|---|
| Project types (`com.midnightmunchies..`), including enums, records, interfaces and exceptions | `java.lang` types (`String`, `Object`, `IllegalArgumentException`, `IllegalStateException`, `Override`, `FunctionalInterface`) |
| A nested type of another class, counted as its own type (for example `MontrealServiceArea.Place`, `OrdService.OrderLineRequest`) | JDK library types (`java.util`, `java.time`, `java.math`: `List`, `Optional`, `UUID`, `Instant`, `Clock`, `BigDecimal`, ...) |
| Framework types: Spring (`org.springframework..`) and Jakarta (`jakarta..`), including annotations | Types declared inside the class itself (its own nested records and interfaces) |

Coupling is reported as project types + framework types = total.

### JavaScript / JSX
A JSX file has no classes. The unit is the file, and coupling is the number of distinct modules it imports and uses: project files plus external packages. Stylesheet imports (`.css`) are not counted.

## Results

| # | Class | Project types | Framework types | Coupling (total) | Packages reached |
|---|---|---|---|---|---|
| H1 | CourierService | 25 | 2 | **27** | 8 |
| H2 | RealOrdService | 25 | 3 | **28** | 6 |
| H3 | AuthorizedOrdService | 13 | 3 | **16** | 4 |
| H4 | OrdController | 13 | 16 | **29** | 4 |
| H5 | order_history.jsx | 5 | 1 | **6** | n/a |

## Referenced types, by package

### H1 CourierService (27)
Own nested types, not counted: `ActiveDeliveryView`, `AssignmentView`, `CourierDashboard`, `OperatorDeliveriesView`, `ReassignmentView`.

| Package | Types |
|---|---|
| application.dlv (3) | DeliveryConflictException, DeliveryNotFoundException, DispatchQueueService |
| application.ord (2) | AuthorizationException, OrderTransitions |
| application.reg (1) | AuthenticationRequiredException |
| domain.dlv (6) | Assignment, AssignmentStatus, Courier, CourierState, DispatchRule, Location |
| domain.geo (2) | MontrealServiceArea, MontrealServiceArea.Place |
| domain.ord (7) | Actor, Order, OrderEvent, OrderEventListener, OrderRepository, OrderStatus, RestaurantRepository |
| domain.reg (2) | UserAccount, UserRole |
| technicalservices (2) | AssignmentRepository, CourierRepository |
| Framework (2) | Autowired, Service |

### H2 RealOrdService (28)
| Package | Types |
|---|---|
| application.ord (5) | MenuItemNotFoundException, OrdService, OrdService.OrderLineRequest, OrderNotFoundException, PreparationQueue |
| application.prc (1) | OrderLedgerService |
| application.reg (1) | AuthenticationRequiredException |
| domain.ord (14) | Actor, IllegalOrderTransitionException, MenuItem, MenuItemRepository, MenuItemStatus, Order, OrderEvent, OrderEventLog, OrderLine, OrderRepository, OrderStatus, Restaurant, RestaurantRepository, RestaurantStatus |
| domain.reg (2) | UserAccount, UserRole |
| technicalservices (2) | InMemoryOrderEventLog, LedgerRepository |
| Framework (3) | Autowired, Service, HttpSession |

### H3 AuthorizedOrdService (16)
| Package | Types |
|---|---|
| application.ord (3) | AuthorizationException, OrdService, OrdService.OrderLineRequest |
| application.reg (1) | AuthenticationRequiredException |
| domain.ord (7) | MenuItem, MenuItemStatus, Order, OrderEvent, Restaurant, RestaurantRepository, RestaurantStatus |
| domain.reg (2) | UserAccount, UserRole |
| Framework (3) | Service, Qualifier, HttpSession |

### H4 OrdController (29)
Own nested types, not counted: `AuthorizedOperation`, `ErrorResponse`, `MenuItemRequest`, `MenuItemStatusRequest`, `PlaceOrderRequest`, `RejectOrderRequest`, `RestaurantStatusRequest`.

| Package | Types |
|---|---|
| application.ord (7) | AuthorizationException, AuthorizedOrdService, EmptyPreparationQueueException, MenuItemNotFoundException, OrdService, OrdService.OrderLineRequest, OrderNotFoundException |
| application.reg (1) | AuthenticationRequiredException |
| domain.ord (4) | DuplicateNameException, IllegalOrderTransitionException, MenuItemStatus, RestaurantStatus |
| technicalservices (1) | CatalogAccessException |
| Framework (16) | RestController, RequestMapping, CrossOrigin, GetMapping, PostMapping, PutMapping, PatchMapping, DeleteMapping, PathVariable, RequestBody, ExceptionHandler, ResponseEntity, HttpStatus, HttpSession, HttpMessageNotReadableException, MethodArgumentTypeMismatchException |

### H5 order_history.jsx (6)
| Kind | Modules (bindings used) |
|---|---|
| Project (5) | `../../api` (`api`: `orderHistory`, `cancelOrder`, `refundOrder`), `./menu_page/menu_format` (`formatPrice`), `./order_event_history` (`OrderEventHistory`), `./order_summary_page` (`OrderSummaryPage`), `./output_console/command_log` (`useCommandLog`) |
| External (1) | `react` (`React`, `useCallback`, `useEffect`, `useRef`, `useState`) |
| Not counted | `./order_history.css` |

## Observations
- **CourierService** reaches 8 packages across all three layers, from the user account (`domain.reg`) to the map geometry (`domain.geo`) and two repositories. This matches the Divergent Change smell: each package is a separate reason to change.
- **RealOrdService** creates two technical-services classes itself (`new InMemoryOrderEventLog()`, `new LedgerRepository()`) inside its telescoping constructors. An application-layer class therefore depends directly on concrete infrastructure that Spring already injects.
- **OrdController**: 8 of its 13 project types are exceptions caught in the 6-branch `execute` ladder and turned into HTTP status codes. Every new refusal adds one more.
- **AuthorizedOrdService** has the lowest Java coupling, but it is coupled to the full `OrdService` interface: all 16 commands. Its smell is the number of places to edit, not the number of types.
- **order_history.jsx** has low module coupling. Its problem is internal: duplicated action handlers and high complexity, not dependencies.
