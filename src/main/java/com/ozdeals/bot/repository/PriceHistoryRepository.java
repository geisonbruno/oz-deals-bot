package com.ozdeals.bot.repository;

import com.ozdeals.bot.entity.PriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    @Query("SELECT MIN(ph.price) FROM PriceHistory ph WHERE ph.asin = :asin")
    Optional<BigDecimal> findMinPriceByAsin(@Param("asin") String asin);
}
