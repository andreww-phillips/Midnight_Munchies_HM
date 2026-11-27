package com.midnightmunchies.application.rev;

import com.midnightmunchies.domain.dlv.Assignment;
import com.midnightmunchies.domain.dlv.Courier;
import com.midnightmunchies.domain.dlv.Location;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderRepository;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantRepository;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.AssignmentRepository;
import com.midnightmunchies.technicalservices.CourierRepository;
import com.midnightmunchies.technicalservices.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"logging.level.org.springframework=ERROR", "logging.level.root=ERROR"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReviewControllerTests {
    private static final String REVIEWS = "/api/reviews";
    private static final Instant CREATED = Instant.parse("2026-11-20T12:00:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private OrderRepository orders;
    @Autowired private RestaurantRepository restaurants;
    @Autowired private AssignmentRepository assignments;
    @Autowired private CourierRepository couriers;
    @Autowired private ReviewRepository reviews;

    private UserAccount customer;
    private UserAccount otherCustomer;
    private UserAccount courier;
    private UUID restaurantId;
    private Order delivered;
    private MockHttpSession customerSession;

    @BeforeEach
    void setUp() {
        reviews.clear();
        customer = user(UserRole.CUSTOMER);
        otherCustomer = user(UserRole.CUSTOMER);
        courier = user(UserRole.COURIER);
        restaurantId = UUID.randomUUID();
        restaurants.save(new Restaurant(restaurantId, UUID.randomUUID(), "Review diner",
                RestaurantStatus.OPEN, 45.5, -73.6));
        courierId();
        customerSession = session(customer);
        delivered = deliveredOrder(customer);
    }

    @Test
    void customerCanReviewBothTargetsOnceAndReadTheirAggregates() throws Exception {
        mvc.perform(get(REVIEWS + "/orders/{id}", delivered.getId()).session(customerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.restaurantId").value(restaurantId.toString()))
                .andExpect(jsonPath("$.courierId").value(courier.id()));

        submit(delivered, "RESTAURANT", 4, "Great meal and quick pickup.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.targetId").value(restaurantId.toString()));
        submit(delivered, "COURIER", 5, "Friendly and careful delivery.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.targetId").value(courier.id()));

        submit(delivered, "RESTAURANT", 3, "A second review is not allowed.")
                .andExpect(status().isConflict());
        mvc.perform(get(REVIEWS + "/orders/{id}", delivered.getId()).session(customerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantReview.rating").value(4))
                .andExpect(jsonPath("$.courierReview.rating").value(5));

        Order second = deliveredOrder(customer);
        submit(second, "RESTAURANT", 5, "Another good meal.")
                .andExpect(status().isCreated());
        mvc.perform(get(REVIEWS + "/restaurants/{id}", restaurantId).session(customerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(4.5))
                .andExpect(jsonPath("$.reviewCount").value(2))
                .andExpect(jsonPath("$.reviews.length()").value(2));
        mvc.perform(get(REVIEWS + "/couriers/{id}", UUID.fromString(courier.id())).session(customerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.reviewCount").value(1));
    }

    @Test
    void reviewsRequireAnOwnedDeliveredOrderAndCustomerRole() throws Exception {
        mvc.perform(get(REVIEWS + "/orders/{id}", delivered.getId()).session(session(otherCustomer)))
                .andExpect(status().isNotFound());
        mvc.perform(post(REVIEWS + "/orders/{id}", delivered.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"RESTAURANT\",\"rating\":5,\"text\":\"Unauthorized\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(REVIEWS + "/orders/{id}", delivered.getId()).session(session(user(UserRole.COURIER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"RESTAURANT\",\"rating\":5,\"text\":\"Unauthorized\"}"))
                .andExpect(status().isForbidden());

        Order placed = orders.save(new Order(UUID.randomUUID(), UUID.fromString(customer.id()), restaurantId,
                List.of(new OrderLine(UUID.randomUUID(), "Meal", 1, new BigDecimal("8.50"))), CREATED));
        mvc.perform(get(REVIEWS + "/orders/{id}", placed.getId()).session(customerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false));
        submit(placed, "RESTAURANT", 5, "Too early.")
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidRatingAndBlankReviewText() throws Exception {
        submit(delivered, "RESTAURANT", 6, "Out of range.")
                .andExpect(status().isBadRequest());
        submit(delivered, "COURIER", 5, "   ")
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions submit(
            Order order, String target, int rating, String text
    ) throws Exception {
        return mvc.perform(post(REVIEWS + "/orders/{id}", order.getId()).session(customerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"target\":\"" + target + "\",\"rating\":" + rating + ",\"text\":\"" + text + "\"}"));
    }

    private Order deliveredOrder(UserAccount owner) {
        UUID orderId = UUID.randomUUID();
        Order order = Order.restore(orderId, UUID.fromString(owner.id()), restaurantId,
                List.of(new OrderLine(UUID.randomUUID(), "Meal", 1, new BigDecimal("8.50"))),
                CREATED, OrderStatus.DELIVERED, null);
        orders.save(order);
        assignments.save(Assignment.offer(orderId, UUID.fromString(courier.id()), CREATED)
                .accept(CREATED.plusSeconds(1))
                .pickUp(CREATED.plusSeconds(2))
                .deliver(CREATED.plusSeconds(3)));
        return order;
    }

    private void courierId() {
        UUID id = UUID.fromString(courier.id());
        couriers.save(new Courier(id, new Location(45.5, -73.6)));
    }

    private UserAccount user(UserRole role) {
        String id = UUID.randomUUID().toString();
        return new UserAccount(id, "review-" + id, "unused-test-hash", role);
    }

    private MockHttpSession session(UserAccount account) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser", account);
        return session;
    }
}
