package com.betai.integration;

import com.betai.domain.history.UserSavedBatch;
import com.betai.domain.history.UserSavedBatchItem;
import com.betai.domain.market.MarketCode;
import com.betai.domain.odds.ValueRating;
import com.betai.domain.prediction.PredictionConfidenceBand;
import com.betai.domain.user.Role;
import com.betai.domain.user.User;
import com.betai.repository.UserRepository;
import com.betai.repository.UserSavedBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class UserHistoryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserSavedBatchRepository userSavedBatchRepository;

    @BeforeEach
    void setUp() {
        userSavedBatchRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void userCanRetrieveOwnHistoryButNotOthers() throws Exception {
        User user1 = userRepository.save(new User()
                .setUsername("user1")
                .setPasswordHash(passwordEncoder.encode("password"))
                .setRole(Role.ROLE_USER)
                .setEnabled(true));
        User user2 = userRepository.save(new User()
                .setUsername("user2")
                .setPasswordHash(passwordEncoder.encode("password"))
                .setRole(Role.ROLE_USER)
                .setEnabled(true));

        UserSavedBatch ownBatch = new UserSavedBatch()
                .setUser(user1)
                .setBatchName("own batch");
        ownBatch.addItem(new UserSavedBatchItem()
                .setMarketCode(MarketCode.HOME_WIN)
                .setLeagueCode("PREMIER_LEAGUE")
                .setFixture("Home vs Away")
                .setMarketName("Home Win")
                .setPredictedValue("HOME")
                .setTeamOrPlayer("Home")
                .setTunedProbability(new BigDecimal("0.820000"))
                .setConfidenceBand(PredictionConfidenceBand.HIGH)
                .setValueRating(ValueRating.NO_ODDS)
                .setGeneratedAt(OffsetDateTime.parse("2026-08-01T12:00:00Z")));
        userSavedBatchRepository.save(ownBatch);

        UserSavedBatch otherBatch = new UserSavedBatch()
                .setUser(user2)
                .setBatchName("other batch");
        otherBatch.addItem(new UserSavedBatchItem()
                .setMarketCode(MarketCode.AWAY_WIN)
                .setLeagueCode("LA_LIGA")
                .setFixture("Other Home vs Other Away")
                .setMarketName("Away Win")
                .setPredictedValue("AWAY")
                .setTeamOrPlayer("Other Away")
                .setTunedProbability(new BigDecimal("0.770000"))
                .setConfidenceBand(PredictionConfidenceBand.MEDIUM)
                .setValueRating(ValueRating.NO_ODDS)
                .setGeneratedAt(OffsetDateTime.parse("2026-08-01T12:00:00Z")));
        userSavedBatchRepository.save(otherBatch);

        mockMvc.perform(get("/api/v1/users/me/history")
                        .with(user(new com.betai.security.CustomUserDetails(user1.getId(), "user1", "password", true, true, java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"))))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("own batch")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("other batch"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("passwordHash"))));
    }
}
