package com.midnightmunchies.application.rvw;

import com.midnightmunchies.application.ord.AuthorizationException;
import com.midnightmunchies.application.ord.OrderNotFoundException;
import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.domain.dlv.Assignment;
import com.midnightmunchies.domain.dlv.AssignmentStatus;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderRepository;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.domain.rev.DeliveryReview;
import com.midnightmunchies.domain.rev.ReviewTarget;
import com.midnightmunchies.technicalservices.AssignmentRepository;
import com.midnightmunchies.technicalservices.CourierRepository;
import com.midnightmunchies.technicalservices.ReviewRepository;
import com.midnightmunchies.domain.ord.RestaurantRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReviewService {
    private final OrderRepository orders;
    private final AssignmentRepository assignments;
    private final RestaurantRepository restaurants;
    private final CourierRepository couriers;
    private final ReviewRepository reviews;

    public ReviewService(OrderRepository orders, AssignmentRepository assignments,
                         RestaurantRepository restaurants, CourierRepository couriers,
                         ReviewRepository reviews) {
        this.orders = orders;
        this.assignments = assignments;
        this.restaurants = restaurants;
        this.couriers = couriers;
        this.reviews = reviews;
    }

    public OrderReviewStatus orderReviews(UUID orderId, UserAccount account) {
        Order order = customerOrder(orderId, account);
        if (!wasDelivered(order.getStatus())) {
            return new OrderReviewStatus(orderId, false, order.getRestaurantId(), null, null, null);
        }
        Assignment delivery = deliveredAssignment(orderId);
        return new OrderReviewStatus(
                orderId,
                true,
                order.getRestaurantId(),
                delivery.courierId(),
                view(reviews.findByOrderIdAndTarget(orderId, ReviewTarget.RESTAURANT)),
                view(reviews.findByOrderIdAndTarget(orderId, ReviewTarget.COURIER))
        );
    }

    public ReviewView submit(UUID orderId, ReviewTarget target, Integer rating, String text, UserAccount account) {
        Order order = customerOrder(orderId, account);
        if (!wasDelivered(order.getStatus())) {
            throw new IllegalStateException("Reviews are available only after an order is delivered");
        }
        if (target == null) {
            throw new IllegalArgumentException("Review target must be RESTAURANT or COURIER");
        }
        Assignment delivery = deliveredAssignment(orderId);
        UUID targetId = switch (target) {
            case RESTAURANT -> order.getRestaurantId();
            case COURIER -> delivery.courierId();
        };
        if (rating == null) {
            throw new IllegalArgumentException("Rating is required");
        }
        DeliveryReview saved = reviews.save(new DeliveryReview(orderId, UUID.fromString(account.id()), targetId,
                target, rating, text, Instant.now()));
        return ReviewView.from(saved);
    }

    public ReviewSummary restaurantReviews(UUID restaurantId, UserAccount account) {
        requireAuthenticated(account);
        if (restaurants.findById(restaurantId).isEmpty()) {
            throw new IllegalArgumentException("Restaurant does not exist");
        }
        return summary(ReviewTarget.RESTAURANT, restaurantId);
    }

    public ReviewSummary courierReviews(UUID courierId, UserAccount account) {
        requireAuthenticated(account);
        if (couriers.findById(courierId).isEmpty()) {
            throw new IllegalArgumentException("Courier does not exist");
        }
        return summary(ReviewTarget.COURIER, courierId);
    }

    private ReviewSummary summary(ReviewTarget target, UUID targetId) {
        List<DeliveryReview> matching = reviews.findByTarget(target, targetId);
        BigDecimal average = matching.isEmpty() ? null : BigDecimal.valueOf(
                matching.stream().mapToInt(DeliveryReview::rating).sum())
                .divide(BigDecimal.valueOf(matching.size()), 1, RoundingMode.HALF_UP);
        return new ReviewSummary(targetId, average, matching.size(), matching.stream().map(ReviewView::from).toList());
    }

    private Order customerOrder(UUID orderId, UserAccount account) {
        requireCustomer(account);
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order does not exist"));
        if (!order.getCustomerId().equals(UUID.fromString(account.id()))) {
            throw new OrderNotFoundException("Order does not exist");
        }
        return order;
    }

    private Assignment deliveredAssignment(UUID orderId) {
        return assignments.findDeliveredByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("Delivered order has no completed courier assignment"));
    }

    private boolean wasDelivered(OrderStatus status) {
        return status == OrderStatus.DELIVERED || status == OrderStatus.REFUNDED;
    }

    private ReviewView view(Optional<DeliveryReview> review) {
        return review.map(ReviewView::from).orElse(null);
    }

    private void requireCustomer(UserAccount account) {
        requireAuthenticated(account);
        if (account.role() != UserRole.CUSTOMER) {
            throw new AuthorizationException("Only customers can review delivered orders");
        }
    }

    private void requireAuthenticated(UserAccount account) {
        if (account == null) {
            throw new AuthenticationRequiredException("Authentication is required");
        }
    }

    public record OrderReviewStatus(UUID orderId, boolean eligible, UUID restaurantId, UUID courierId,
                                    ReviewView restaurantReview, ReviewView courierReview) {
    }

    public record ReviewView(UUID orderId, UUID targetId, int rating, String text, Instant createdAt) {
        private static ReviewView from(DeliveryReview review) {
            return new ReviewView(review.orderId(), review.targetId(), review.rating(), review.text(), review.createdAt());
        }
    }

    public record ReviewSummary(UUID targetId, BigDecimal averageRating, int reviewCount, List<ReviewView> reviews) {
        public ReviewSummary {
            reviews = List.copyOf(reviews);
        }
    }
}
