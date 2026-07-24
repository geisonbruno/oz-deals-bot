package com.ozdeals.bot.repository;

import com.ozdeals.bot.entity.Deal;
import com.ozdeals.bot.entity.DealStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DealRepository extends JpaRepository<Deal, Long> {

    Optional<Deal> findBySlug(String slug);

    List<Deal> findByStatusOrderByCreatedAtDesc(DealStatus status);

    List<Deal> findByStatusAndExpiresAtBefore(DealStatus status, LocalDateTime dateTime);

    Optional<Deal> findTopByProduct_IdOrderByCreatedAtDesc(Long productId);
}
