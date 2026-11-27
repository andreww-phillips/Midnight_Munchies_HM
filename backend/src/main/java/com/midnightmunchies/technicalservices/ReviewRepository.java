package com.midnightmunchies.technicalservices;

import org.springframework.stereotype.Repository;

import com.midnightmunchies.domain.rvw.DeliveryReview;
import com.midnightmunchies.domain.rvw.ReviewTarget;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ReviewRepository {
    private final Map<ReviewKey, DeliveryReview> reviews = new ConcurrentHashMap<>();

    public DeliveryReview save(DeliveryReview review) {
        ReviewKey key = new ReviewKey(review.orderId(), review.target());
        if (reviews.putIfAbsent(key, review) != null) {
            throw new IllegalStateException("A review for this order and target has already been submitted");
        }
        return review;
    }

    public Optional<DeliveryReview> findByOrderIdAndTarget(UUID orderId, ReviewTarget target) {
        return Optional.ofNullable(reviews.get(new ReviewKey(orderId, target)));
    }

    public List<DeliveryReview> findByTarget(ReviewTarget target, UUID targetId) {
        return reviews.values().stream()
                .filter(review -> review.target() == target && review.targetId().equals(targetId))
                .sorted(Comparator.comparing(DeliveryReview::createdAt).reversed()
                        .thenComparing(DeliveryReview::orderId))
                .toList();
    }

    public void clear() {
        reviews.clear();
    }

    private record ReviewKey(UUID orderId, ReviewTarget target) {
    }
}
