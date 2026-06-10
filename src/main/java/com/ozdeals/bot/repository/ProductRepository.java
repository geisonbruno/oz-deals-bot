package com.ozdeals.bot.repository;

import com.ozdeals.bot.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, String> {
}
