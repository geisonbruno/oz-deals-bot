package com.ozdeals.bot.repository;

import com.ozdeals.bot.ProductSource;
import com.ozdeals.bot.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySourceAndExternalId(ProductSource source, String externalId);

    boolean existsBySourceAndExternalId(ProductSource source, String externalId);
}
