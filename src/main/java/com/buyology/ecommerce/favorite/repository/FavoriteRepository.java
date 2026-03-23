package com.buyology.ecommerce.favorite.repository;

import com.buyology.ecommerce.favorite.domain.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite, UUID> {

    List<Favorite> findByAuthCredential_Id(UUID authCredentialId);

    Optional<Favorite> findByAuthCredential_IdAndProduct_Id(UUID authCredentialId, UUID productId);

    boolean existsByAuthCredential_IdAndProduct_Id(UUID authCredentialId, UUID productId);

    void deleteByAuthCredential_IdAndProduct_Id(UUID authCredentialId, UUID productId);

    // Admin: all favorites across all users, paginated
    @Query("SELECT f FROM Favorite f JOIN FETCH f.authCredential JOIN FETCH f.product")
    Page<Favorite> findAllWithDetails(Pageable pageable);

    // Admin: count how many users favorited a given product
    @Query("SELECT COUNT(f) FROM Favorite f WHERE f.product.id = :productId")
    long countByProductId(@Param("productId") UUID productId);
}
