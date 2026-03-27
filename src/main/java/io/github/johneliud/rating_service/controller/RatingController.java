package io.github.johneliud.rating_service.controller;

import io.github.johneliud.rating_service.dto.RatingRequest;
import io.github.johneliud.rating_service.dto.RatingResponse;
import io.github.johneliud.rating_service.service.RatingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/ratings")
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    @PostMapping
    public ResponseEntity<RatingResponse> create(Authentication authentication,
                                                  @Valid @RequestBody RatingRequest request) {
        String userId = authentication.getName();
        log.info("POST /api/ratings - userId: {}, movieId: {}", userId, request.movieId());
        RatingResponse response = ratingService.create(userId, request);
        log.info("POST /api/ratings - Rating created with id: {}", response.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/movie/{movieId}")
    public ResponseEntity<List<RatingResponse>> findByMovie(@PathVariable String movieId) {
        log.info("GET /api/ratings/movie/{} - Fetching ratings", movieId);
        return ResponseEntity.ok(ratingService.findByMovie(movieId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<RatingResponse>> findByUser(@PathVariable String userId) {
        log.info("GET /api/ratings/user/{} - Fetching ratings", userId);
        return ResponseEntity.ok(ratingService.findByUser(userId));
    }
}