package com.ozdeals.bot.repository;

import com.ozdeals.bot.entity.TelegramAdminConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TelegramAdminConversationRepository extends JpaRepository<TelegramAdminConversation, Long> {

    Optional<TelegramAdminConversation> findByTelegramUserId(Long telegramUserId);

    void deleteByTelegramUserId(Long telegramUserId);
}
