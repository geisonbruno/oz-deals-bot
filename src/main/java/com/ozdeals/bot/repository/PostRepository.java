package com.ozdeals.bot.repository;

import com.ozdeals.bot.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PostRepository extends JpaRepository<Post, Long> {

    Optional<Post> findTopByAsinAndPostedAtAfter(String asin, LocalDateTime since);
}
