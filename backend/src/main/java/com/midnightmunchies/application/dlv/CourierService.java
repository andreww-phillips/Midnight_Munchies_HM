package com.midnightmunchies.application.dlv;

import com.midnightmunchies.application.ord.OrderTransitions;
import com.midnightmunchies.domain.dlv.Assignment;
import com.midnightmunchies.domain.dlv.AssignmentStatus;
import com.midnightmunchies.domain.dlv.Courier;
import com.midnightmunchies.domain.dlv.CourierState;
import com.midnightmunchies.domain.dlv.DispatchRule;
import com.midnightmunchies.domain.dlv.Location;
import com.midnightmunchies.domain.ord.Actor;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderEventListener;
import com.midnightmunchies.domain.ord.OrderRepository;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.ord.RestaurantRepository;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.AssignmentRepository;
import com.midnightmunchies.technicalservices.CourierRepository;
import com.midnightmunchies.application.ord.AuthorizationException;
import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class CourierService implements OrderEventListener {
    private final CourierRepository couriers;
    private final AssignmentRepository assignments;
    private final OrderRepository orders;
    private final RestaurantRepository restaurants;
    private final DispatchQueueService dispatchQueue;
    private final OrderTransitions transitions;
    private final Clock clock;
    private final DispatchRule dispatchRule = new DispatchRule();
    private final Object dispatchLock = new Object();

    @Autowired
    public CourierService(CourierRepository couriers, AssignmentRepository assignments,
                          OrderRepository orders, RestaurantRepository restaurants,
                          DispatchQueueService dispatchQueue, OrderTransitions transitions) {
        this(couriers, assignments, orders, restaurants, dispatchQueue, transitions, Clock.systemUTC());
    }

    public AssignmentView decline(UserAccount account, UUID orderId) {
        Courier identity = requireCourier(account);
        synchronized (dispatchLock) {
            Assignment assignment = requireAssignment(orderId, identity.getId(), AssignmentStatus.OFFERED);
            Order order = findReadyOrder(orderId);
            dispatchQueue.enqueue(order);
            Assignment declined = assignments.save(assignment.decline(clock.instant()));
            retryDispatch(identity.getId());
            return new AssignmentView(declined, order);
        }
    }

    CourierService(CourierRepository couriers, AssignmentRepository assignments, OrderRepository orders,
                   RestaurantRepository restaurants, DispatchQueueService dispatchQueue,
                   OrderTransitions transitions, Clock clock) {
        this.couriers = Objects.requireNonNull(couriers, "couriers must not be null");
        this.assignments = Objects.requireNonNull(assignments, "assignments must not be null");
        this.orders = Objects.requireNonNull(orders, "orders must not be null");
        this.restaurants = Objects.requireNonNull(restaurants, "restaurants must not be null");
        this.dispatchQueue = Objects.requireNonNull(dispatchQueue, "dispatchQueue must not be null");
        this.transitions = Objects.requireNonNull(transitions, "transitions must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        restoreQueuedOrders();
    }

    public CourierDashboard view(UserAccount account) {
        Courier courier = requireCourier(account);
        synchronized (dispatchLock) {
            Courier current = findOrCreate(courier.getId());
            Optional<AssignmentView> active = assignments.findActiveByCourierId(current.getId())
                    .map(this::assignmentView);
            return new CourierDashboard(current.isOnDuty(), current.getState(), active.orElse(null));
        }
    }

    public CourierDashboard setDuty(UserAccount account, boolean onDuty) {
        Courier identity = requireCourier(account);
        synchronized (dispatchLock) {
            Courier courier = findOrCreate(identity.getId());
            try {
                if (onDuty) {
                    if (courier.getState() == CourierState.OFFLINE) {
                        courier.goOnDuty();
                    }
                } else {
                    Optional<Assignment> offer = assignments.findActiveByCourierId(courier.getId())
                            .filter(assignment -> assignment.status() == AssignmentStatus.OFFERED);
                    offer.ifPresent(assignment -> {
                        orders.findById(assignment.orderId())
                                .filter(order -> order.getStatus() == OrderStatus.READY)
                                .ifPresent(order -> {
                                    dispatchQueue.enqueue(order);
                                    assignments.save(assignment.decline(clock.instant()));
                                });
                    });
                    courier.goOffDuty();
                }
            } catch (IllegalStateException exception) {
                throw new DeliveryConflictException(exception.getMessage());
            }
            couriers.save(courier);
            retryDispatch(null);
            return view(account);
        }
    }

    public AssignmentView accept(UserAccount account, UUID orderId) {
        Courier identity = requireCourier(account);
        synchronized (dispatchLock) {
            Assignment assignment = findActiveAssignment(orderId);
            requireAssignedCourier(assignment, identity.getId());
            if (assignment.status() != AssignmentStatus.OFFERED) {
                throw new DeliveryConflictException("This delivery offer is no longer available");
            }
            Order order = findReadyOrder(orderId);
            Courier courier = findOrCreate(identity.getId());
            try {
                courier.acceptOrder(order);
            } catch (IllegalStateException exception) {
                throw new DeliveryConflictException(exception.getMessage());
            }
            couriers.save(courier);
            Assignment accepted = assignments.save(assignment.accept(clock.instant()));
            return new AssignmentView(accepted, order);
        }
    }

    public AssignmentView pickUp(UserAccount account, UUID orderId) {
        Courier identity = requireCourier(account);
        synchronized (dispatchLock) {
            Assignment assignment = requireAssignment(orderId, identity.getId(), AssignmentStatus.ACCEPTED);
            Order order = findReadyOrder(orderId);
            Courier courier = findOrCreate(identity.getId());
            try {
                courier.confirmPickup();
            } catch (IllegalStateException exception) {
                throw new DeliveryConflictException(exception.getMessage());
            }
            transitions.save(order, new Actor(identity.getId(), UserRole.COURIER));
            couriers.save(courier);
            Assignment pickedUp = assignments.save(assignment.pickUp(clock.instant()));
            return new AssignmentView(pickedUp, order);
        }
    }

    public AssignmentView deliver(UserAccount account, UUID orderId) {
        Courier identity = requireCourier(account);
        synchronized (dispatchLock) {
            Assignment assignment = requireAssignment(orderId, identity.getId(), AssignmentStatus.PICKED_UP);
            Order order = orders.findById(orderId)
                    .orElseThrow(() -> new DeliveryNotFoundException("Order does not exist"));
            Courier courier = findOrCreate(identity.getId());
            try {
                courier.confirmDelivery();
            } catch (IllegalStateException exception) {
                throw new DeliveryConflictException(exception.getMessage());
            }
            transitions.save(order, new Actor(identity.getId(), UserRole.COURIER));
            couriers.save(courier);
            Assignment delivered = assignments.save(assignment.deliver(clock.instant()));
            retryDispatch(null);
            return new AssignmentView(delivered, order);
        }
    }

    public ReassignmentView reassign(UserAccount account, UUID orderId) {
        String operatorId = requireOperator(account);
        synchronized (dispatchLock) {
            Order order = orders.findById(orderId)
                    .orElseThrow(() -> new DeliveryNotFoundException("Order does not exist"));
            Assignment previous = findActiveAssignment(orderId);
            if (order.getStatus() != OrderStatus.READY || previous.status() == AssignmentStatus.PICKED_UP) {
                throw new DeliveryConflictException("Only a ready order that has not been picked up can be reassigned");
            }
            Courier stalled = findOrCreate(previous.courierId());
            try {
                if (previous.status() == AssignmentStatus.ACCEPTED) {
                    stalled.releaseOrder(order);
                }
                if (stalled.getState() == CourierState.IDLE) {
                    stalled.goOffDuty();
                }
            } catch (IllegalStateException exception) {
                throw new DeliveryConflictException(exception.getMessage());
            }
            couriers.save(stalled);
            Assignment released = assignments.save(previous.reassign(operatorId, clock.instant()));
            dispatchQueue.enqueue(order);
            retryDispatch(null);
            Assignment replacement = assignments.findActiveByOrderId(orderId).orElse(null);
            return new ReassignmentView(released, replacement, order, replacement == null);
        }
    }

    public OperatorDeliveriesView operatorDeliveries(UserAccount account) {
        requireOperator(account);
        synchronized (dispatchLock) {
            Instant now = clock.instant();
            List<ActiveDeliveryView> active = assignments.findActive().stream()
                    .map(assignment -> activeDelivery(assignment, now))
                    .toList();
            return new OperatorDeliveriesView(active, dispatchQueue.view());
        }
    }

    private ActiveDeliveryView activeDelivery(Assignment assignment, Instant now) {
        Instant since = assignment.acceptedAt() != null ? assignment.acceptedAt() : assignment.offeredAt();
        return new ActiveDeliveryView(assignment.orderId(), assignment.courierId(), assignment.status(),
                assignment.offeredAt(), assignment.acceptedAt(), Duration.between(since, now).toSeconds(),
                assignment.status() != AssignmentStatus.PICKED_UP);
    }

    public List<UUID> dispatchQueue() {
        synchronized (dispatchLock) {
            return dispatchQueue.view();
        }
    }

    @Override
    public void onEvent(OrderEvent event) {
        if (event.toStatus() != OrderStatus.READY) {
            return;
        }
        synchronized (dispatchLock) {
            orders.findById(event.orderId())
                    .filter(order -> order.getStatus() == OrderStatus.READY)
                    .filter(order -> assignments.findActiveByOrderId(order.getId()).isEmpty())
                    .ifPresent(order -> {
                        dispatchQueue.enqueue(order);
                        retryDispatch(null);
                    });
        }
    }

    private void retryDispatch(UUID excludedCourierId) {
        Set<UUID> excluded = excludedCourierId == null ? Set.of() : Set.of(excludedCourierId);
        while (true) {
            Optional<UUID> nextOrderId = dispatchQueue.peek();
            if (nextOrderId.isEmpty()) {
                return;
            }
            UUID orderId = nextOrderId.get();
            Optional<Order> readyOrder = orders.findById(orderId);
            if (readyOrder.isEmpty()) {
                return;
            }
            if (readyOrder.get().getStatus() != OrderStatus.READY
                    || assignments.findActiveByOrderId(orderId).isPresent()) {
                dispatchQueue.remove(orderId);
                continue;
            }
            Optional<Courier> courier = selectCourier(readyOrder.get(), excluded);
            if (courier.isEmpty()) {
                return;
            }
            assignments.save(Assignment.offer(orderId, courier.get().getId(), clock.instant()));
            dispatchQueue.remove(orderId);
        }
    }

    private Optional<Courier> selectCourier(Order order, Set<UUID> excluded) {
        List<Courier> eligible = couriers.findAll().stream()
                .filter(courier -> !excluded.contains(courier.getId()))
                .filter(courier -> assignments.findActiveByCourierId(courier.getId()).isEmpty())
                .toList();
        return restaurants.findById(order.getRestaurantId())
                .map(restaurant -> new Location(restaurant.getLatitude(), restaurant.getLongitude()))
                .map(pickup -> dispatchRule.nearest(pickup, eligible))
                .orElseGet(() -> dispatchRule.lowestId(eligible));
    }

    private void restoreQueuedOrders() {
        for (Order queuedOrder : dispatchQueue.restoreOrders()) {
            Optional<Order> existing = orders.findById(queuedOrder.getId());
            if (existing.isEmpty()) {
                orders.save(queuedOrder);
            } else if (existing.get().getStatus() != OrderStatus.READY) {
                dispatchQueue.remove(queuedOrder.getId());
            }
        }
    }

    private AssignmentView assignmentView(Assignment assignment) {
        Order order = orders.findById(assignment.orderId())
                .orElseThrow(() -> new DeliveryNotFoundException("Order does not exist"));
        return new AssignmentView(assignment, order);
    }

    private Assignment findActiveAssignment(UUID orderId) {
        return assignments.findActiveByOrderId(orderId)
                .orElseThrow(() -> new DeliveryNotFoundException("No active courier assignment exists for this order"));
    }

    private Assignment requireAssignment(UUID orderId, UUID courierId, AssignmentStatus expected) {
        Assignment assignment = findActiveAssignment(orderId);
        requireAssignedCourier(assignment, courierId);
        if (assignment.status() != expected) {
            throw new DeliveryConflictException("Delivery is " + assignment.status() + ", not " + expected);
        }
        return assignment;
    }

    private void requireAssignedCourier(Assignment assignment, UUID courierId) {
        if (!assignment.courierId().equals(courierId)) {
            throw new AuthorizationException("This delivery is assigned to another courier");
        }
    }

    private Order findReadyOrder(UUID orderId) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new DeliveryNotFoundException("Order does not exist"));
        if (order.getStatus() != OrderStatus.READY) {
            throw new DeliveryConflictException("Only a ready order can be offered or accepted");
        }
        return order;
    }

    private Courier findOrCreate(UUID courierId) {
        return couriers.findById(courierId)
                .orElseGet(() -> couriers.save(new Courier(courierId, new Location(0, 0))));
    }

    private Courier requireCourier(UserAccount account) {
        UserAccount user = requireAuthenticated(account);
        if (user.role() != UserRole.COURIER) {
            throw new AuthorizationException("Only couriers can access courier delivery commands");
        }
        return findOrCreate(userId(user));
    }

    private String requireOperator(UserAccount account) {
        UserAccount user = requireAuthenticated(account);
        if (user.role() != UserRole.PLATFORM_OPERATOR) {
            throw new AuthorizationException("Only platform operators can manage deliveries");
        }
        return user.id();
    }

    private UserAccount requireAuthenticated(UserAccount account) {
        if (account == null) {
            throw new AuthenticationRequiredException("Authentication is required");
        }
        return account;
    }

    private UUID userId(UserAccount user) {
        try {
            return UUID.fromString(user.id());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Courier account has an invalid ID");
        }
    }

    public record CourierDashboard(boolean onDuty, CourierState state, AssignmentView assignment) {
    }

    public record AssignmentView(Assignment assignment, Order order) {
    }

    public record ActiveDeliveryView(UUID orderId, UUID courierId, AssignmentStatus status, Instant offeredAt,
                                     Instant acceptedAt, long waitingSeconds, boolean reassignable) {
    }

    public record OperatorDeliveriesView(List<ActiveDeliveryView> active, List<UUID> queued) {
    }

    public record ReassignmentView(Assignment previous, Assignment replacement, Order order, boolean queued) {
    }
}
