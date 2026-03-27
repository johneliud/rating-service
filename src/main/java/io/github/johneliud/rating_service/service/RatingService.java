package io.github.johneliud.rating_service.service;

import io.github.johneliud.rating_service.client.MovieServiceClient;
import io.github.johneliud.rating_service.dto.RatingRequest;
import io.github.johneliud.rating_service.dto.RatingResponse;
import io.github.johneliud.rating_service.entity.Rating;
import io.github.johneliud.rating_service.repository.RatingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;
    private final MovieServiceClient movieServiceClient;

    @Transactional
    public RatingResponse create(String userId, RatingRequest request) {
        log.debug("Creating rating - userId: {}, movieId: {}, score: {}", userId, request.movieId(), request.score());

        if (!movieServiceClient.movieExists(request.movieId())) {
            log.warn("Movie not found - movieId: {}", request.movieId());
            throw new IllegalArgumentException("Movie not found with id: " + request.movieId());
        }

        if (ratingRepository.existsByUserIdAndMovieId(userId, request.movieId())) {
            log.warn("Duplicate rating - userId: {}, movieId: {}", userId, request.movieId());
            throw new IllegalArgumentException("You have already rated this movie");
        }

        Rating rating = Rating.builder()
                .userId(userId)
                .movieId(request.movieId())
                .score(request.score())
                .build();

        Rating saved = ratingRepository.save(rating);
        log.info("Rating created - id: {}, userId: {}, movieId: {}, score: {}", saved.getId(), userId, request.movieId(), request.score());

        refreshAverageRating(request.movieId());
        return toResponse(saved);
    }
}