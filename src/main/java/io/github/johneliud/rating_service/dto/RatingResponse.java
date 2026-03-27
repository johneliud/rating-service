package io.github.johneliud.rating_service.dto;

import java.time.LocalDateTime;

public record RatingResponse(
        String id,
        String userId,
        String movieId,
        Integer score,
        LocalDateTime ratedAt
) {}