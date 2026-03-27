package io.github.johneliud.rating_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class MovieServiceClient {

    private final RestClient restClient;

    public MovieServiceClient(@Value("${movie.service.url}") String movieServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(movieServiceUrl)
                .build();
    }

    public boolean movieExists(String movieId) {
        try {
            restClient.get()
                    .uri("/api/movies/{id}", movieId)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                return false;
            }
            log.error("Unexpected error checking movie existence - movieId: {}, status: {}", movieId, ex.getStatusCode());
            throw new IllegalStateException("Movie service is unavailable. Please try again later.");
        } catch (Exception ex) {
            log.error("Failed to reach movie service for movieId: {}", movieId, ex);
            throw new IllegalStateException("Movie service is unavailable. Please try again later.");
        }
    }

    public void updateAverageRating(String movieId, Double averageRating) {
        try {
            restClient.patch()
                    .uri("/api/movies/{id}/average-rating", movieId)
                    .body(Map.of("averageRating", averageRating != null ? averageRating : 0.0))
                    .retrieve()
                    .toBodilessEntity();
            log.debug("Updated averageRating for movieId: {} to {}", movieId, averageRating);
        } catch (Exception ex) {
            log.error("Failed to update averageRating for movieId: {}", movieId, ex);
        }
    }
}