package com.midnightmunchies.application.dash;

import com.midnightmunchies.application.AuthService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DashControllerTests {

    private static final String MAP = "/api/dash/map";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Test
    void mapRequiresLogin() throws Exception {
        mockMvc.perform(get(MAP))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void customersAreForbiddenFromTheMap() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser", authService.authenticate("customer@example.com", "password"));

        mockMvc.perform(get(MAP).session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Customers cannot view the city map"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "restaurant@example.com",
            "courier@example.com",
            "operator@example.com"
    })
    void staffCouriersAndOperatorsCanViewTheSeededCityMap(String username) throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser", authService.authenticate(username, "password"));

        mockMvc.perform(get(MAP).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bounds.minLat").isNumber())
                .andExpect(jsonPath("$.bounds.maxLon").isNumber())
                .andExpect(jsonPath("$.restaurants[?(@.name == 'Bobs Burgers')].latitude").value(hasItem(45.5048)))
                .andExpect(jsonPath("$.restaurants[?(@.name == 'Pizza Palace')].longitude").value(hasItem(-73.5848)))
                .andExpect(jsonPath("$.restaurants[?(@.name == 'Bobs Burgers')].band").value(hasItem("GREEN")))
                .andExpect(jsonPath("$.couriers").isArray());
    }
}
