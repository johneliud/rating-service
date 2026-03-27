package io.github.johneliud.rating_service.repository;

import io.github.johneliud.rating_service.entity.Rating;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RatingRepository extends Neo4jRepository<Rating, String> {

    List<Rating> findByMovieId(String movieId);

    List<Rating> findByUserId(String userId);

    Optional<Rating> findByUserIdAndMovieId(String userId, String movieId);

    boolean existsByUserIdAndMovieId(String userId, String movieId);

    @Query("MATCH (r:Rating {movieId: $movieId}) RETURN avg(r.score)")
    Double findAverageScoreByMovieId(@Param("movieId") String movieId);
}