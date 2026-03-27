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

    public List<RatingResponse> findByMovie(String movieId) {
        log.debug("Fetching ratings for movieId: {}", movieId);
        return ratingRepository.findByMovieId(movieId).stream().map(this::toResponse).toList();
    }

    public List<RatingResponse> findByUser(String userId) {
        log.debug("Fetching ratings for userId: {}", userId);
        return ratingRepository.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public RatingResponse update(String id, String userId, Integer score) {
        log.debug("Updating rating - id: {}, userId: {}, score: {}", id, userId, score);

        Rating rating = ratingRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Rating not found - id: {}", id);
                    return new IllegalArgumentException("Rating not found with id: " + id);
                });

        if (!rating.getUserId().equals(userId)) {
            log.warn("Ownership check failed - ratingId: {}, requestUserId: {}", id, userId);
            throw new AccessDeniedException("You are not allowed to update this rating");
        }

        rating.setScore(score);
        Rating saved = ratingRepository.save(rating);
        log.info("Rating updated - id: {}, score: {}", id, score);

        refreshAverageRating(rating.getMovieId());
        return toResponse(saved);
    }

    @Transactional
    public void delete(String id, String userId) {
        log.debug("Deleting rating - id: {}, userId: {}", id, userId);

        Rating rating = ratingRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Rating not found - id: {}", id);
                    return new IllegalArgumentException("Rating not found with id: " + id);
                });

        if (!rating.getUserId().equals(userId)) {
            log.warn("Ownership check failed - ratingId: {}, requestUserId: {}", id, userId);
            throw new AccessDeniedException("You are not allowed to delete this rating");
        }

        String movieId = rating.getMovieId();
        ratingRepository.deleteById(id);
        log.info("Rating deleted - id: {}", id);

        refreshAverageRating(movieId);
    }

    private void refreshAverageRating(String movieId) {
        Double avg = ratingRepository.findAverageScoreByMovieId(movieId);
        movieServiceClient.updateAverageRating(movieId, avg);
    }

    private RatingResponse toResponse(Rating rating) {
        return new RatingResponse(
                rating.getId(),
                rating.getUserId(),
                rating.getMovieId(),
                rating.getScore(),
                rating.getRatedAt());
    }
}