package io.github.johneliud.rating_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.johneliud.rating_service.dto.RatingRequest;
import io.github.johneliud.rating_service.dto.RatingResponse;
import io.github.johneliud.rating_service.exception.GlobalExceptionHandler;
import io.github.johneliud.rating_service.service.RatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RatingControllerTest {

    @Mock
    private RatingService ratingService;

    @InjectMocks
    private RatingController ratingController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final String USER_ID = UUID.randomUUID().toString();
    private static final String MOVIE_ID = UUID.randomUUID().toString();
    private static final String RATING_ID = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(ratingController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
        objectMapper = new ObjectMapper();
    }

    private MockHttpServletRequestBuilder withUserPrincipal(MockHttpServletRequestBuilder builder) {
        var auth = new UsernamePasswordAuthenticationToken(USER_ID, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return builder.principal(auth);
    }

    private RatingResponse ratingResponse() {
        return new RatingResponse(RATING_ID, USER_ID, MOVIE_ID, 4, LocalDateTime.now());
    }

    @Test
    void create_returnsCreated_withValidRequest() throws Exception {
        when(ratingService.create(eq(USER_ID), any(RatingRequest.class))).thenReturn(ratingResponse());

        mockMvc.perform(withUserPrincipal(
                        post("/api/ratings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new RatingRequest(MOVIE_ID, 4)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(RATING_ID))
                .andExpect(jsonPath("$.score").value(4));
    }

    @Test
    void create_returnsBadRequest_whenScoreIsZero() throws Exception {
        mockMvc.perform(withUserPrincipal(
                        post("/api/ratings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new RatingRequest(MOVIE_ID, 0)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returnsBadRequest_whenScoreExceedsFive() throws Exception {
        mockMvc.perform(withUserPrincipal(
                        post("/api/ratings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new RatingRequest(MOVIE_ID, 6)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returnsBadRequest_whenMovieIdBlank() throws Exception {
        mockMvc.perform(withUserPrincipal(
                        post("/api/ratings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(new RatingRequest("", 4)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void findByMovie_returnsOk() throws Exception {
        when(ratingService.findByMovie(MOVIE_ID)).thenReturn(List.of(ratingResponse()));

        mockMvc.perform(get("/api/ratings/movie/{movieId}", MOVIE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].movieId").value(MOVIE_ID));
    }

    @Test
    void findByUser_returnsOk() throws Exception {
        when(ratingService.findByUser(USER_ID)).thenReturn(List.of(ratingResponse()));

        mockMvc.perform(get("/api/ratings/user/{userId}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(USER_ID));
    }

    @Test
    void update_returnsOk() throws Exception {
        when(ratingService.update(RATING_ID, USER_ID, 5)).thenReturn(ratingResponse());

        mockMvc.perform(withUserPrincipal(
                        put("/api/ratings/{id}", RATING_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("5")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(RATING_ID));
    }

    @Test
    void update_returnsForbidden_whenNotOwner() throws Exception {
        when(ratingService.update(eq(RATING_ID), eq(USER_ID), anyInt()))
                .thenThrow(new AccessDeniedException("You are not allowed to update this rating"));

        mockMvc.perform(withUserPrincipal(
                        put("/api/ratings/{id}", RATING_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("5")))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        doNothing().when(ratingService).delete(RATING_ID, USER_ID);

        mockMvc.perform(withUserPrincipal(delete("/api/ratings/{id}", RATING_ID)))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returnsForbidden_whenNotOwner() throws Exception {
        doThrow(new AccessDeniedException("You are not allowed to delete this rating"))
                .when(ratingService).delete(RATING_ID, USER_ID);

        mockMvc.perform(withUserPrincipal(delete("/api/ratings/{id}", RATING_ID)))
                .andExpect(status().isForbidden());
    }
}