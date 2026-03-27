package io.github.johneliud.rating_service.service;

import io.github.johneliud.rating_service.client.MovieServiceClient;
import io.github.johneliud.rating_service.dto.RatingRequest;
import io.github.johneliud.rating_service.dto.RatingResponse;
import io.github.johneliud.rating_service.entity.Rating;
import io.github.johneliud.rating_service.repository.RatingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private MovieServiceClient movieServiceClient;

    @InjectMocks
    private RatingService ratingService;

    private static final String USER_ID = UUID.randomUUID().toString();
    private static final String MOVIE_ID = UUID.randomUUID().toString();
    private static final String RATING_ID = UUID.randomUUID().toString();

    private Rating rating() {
        return Rating.builder()
                .id(RATING_ID)
                .userId(USER_ID)
                .movieId(MOVIE_ID)
                .score(4)
                .ratedAt(LocalDateTime.now())
                .build();
    }

    private RatingRequest request() {
        return new RatingRequest(MOVIE_ID, 4);
    }

    @Test
    void create_savesAndReturnsRatingResponse() {
        when(movieServiceClient.movieExists(MOVIE_ID)).thenReturn(true);
        when(ratingRepository.existsByUserIdAndMovieId(USER_ID, MOVIE_ID)).thenReturn(false);
        when(ratingRepository.save(any(Rating.class))).thenReturn(rating());
        when(ratingRepository.findAverageScoreByMovieId(MOVIE_ID)).thenReturn(4.0);

        RatingResponse response = ratingService.create(USER_ID, request());

        assertThat(response.id()).isEqualTo(RATING_ID);
        assertThat(response.score()).isEqualTo(4);
        assertThat(response.userId()).isEqualTo(USER_ID);
        verify(ratingRepository).save(any(Rating.class));
        verify(movieServiceClient).updateAverageRating(MOVIE_ID, 4.0);
    }

    @Test
    void create_throwsIllegalArgument_whenMovieNotFound() {
        when(movieServiceClient.movieExists(MOVIE_ID)).thenReturn(false);

        assertThatThrownBy(() -> ratingService.create(USER_ID, request()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(MOVIE_ID);

        verify(ratingRepository, never()).save(any());
    }

    @Test
    void create_throwsIllegalArgument_whenDuplicateRating() {
        when(movieServiceClient.movieExists(MOVIE_ID)).thenReturn(true);
        when(ratingRepository.existsByUserIdAndMovieId(USER_ID, MOVIE_ID)).thenReturn(true);

        assertThatThrownBy(() -> ratingService.create(USER_ID, request()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already rated");

        verify(ratingRepository, never()).save(any());
    }

    @Test
    void findByMovie_returnsRatings() {
        when(ratingRepository.findByMovieId(MOVIE_ID)).thenReturn(List.of(rating()));

        List<RatingResponse> responses = ratingService.findByMovie(MOVIE_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).movieId()).isEqualTo(MOVIE_ID);
    }

    @Test
    void findByUser_returnsRatings() {
        when(ratingRepository.findByUserId(USER_ID)).thenReturn(List.of(rating()));

        List<RatingResponse> responses = ratingService.findByUser(USER_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).userId()).isEqualTo(USER_ID);
    }

    @Test
    void update_updatesAndReturnsRatingResponse() {
        Rating existing = rating();
        when(ratingRepository.findById(RATING_ID)).thenReturn(Optional.of(existing));
        when(ratingRepository.save(existing)).thenReturn(existing);
        when(ratingRepository.findAverageScoreByMovieId(MOVIE_ID)).thenReturn(5.0);

        RatingResponse response = ratingService.update(RATING_ID, USER_ID, 5);

        assertThat(existing.getScore()).isEqualTo(5);
        verify(ratingRepository).save(existing);
        verify(movieServiceClient).updateAverageRating(MOVIE_ID, 5.0);
    }

    @Test
    void update_throwsIllegalArgument_whenNotFound() {
        when(ratingRepository.findById(RATING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ratingService.update(RATING_ID, USER_ID, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(RATING_ID);
    }

    @Test
    void update_throwsAccessDenied_whenNotOwner() {
        when(ratingRepository.findById(RATING_ID)).thenReturn(Optional.of(rating()));

        assertThatThrownBy(() -> ratingService.update(RATING_ID, "other-user-id", 5))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void delete_deletesRating() {
        when(ratingRepository.findById(RATING_ID)).thenReturn(Optional.of(rating()));
        when(ratingRepository.findAverageScoreByMovieId(MOVIE_ID)).thenReturn(null);

        ratingService.delete(RATING_ID, USER_ID);

        verify(ratingRepository).deleteById(RATING_ID);
        verify(movieServiceClient).updateAverageRating(MOVIE_ID, null);
    }

    @Test
    void delete_throwsIllegalArgument_whenNotFound() {
        when(ratingRepository.findById(RATING_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ratingService.delete(RATING_ID, USER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(RATING_ID);
    }

    @Test
    void delete_throwsAccessDenied_whenNotOwner() {
        when(ratingRepository.findById(RATING_ID)).thenReturn(Optional.of(rating()));

        assertThatThrownBy(() -> ratingService.delete(RATING_ID, "other-user-id"))
                .isInstanceOf(AccessDeniedException.class);

        verify(ratingRepository, never()).deleteById(anyString());
    }
}