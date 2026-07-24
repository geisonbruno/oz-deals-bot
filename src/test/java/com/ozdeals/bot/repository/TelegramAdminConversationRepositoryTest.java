package com.ozdeals.bot.repository;

import com.ozdeals.bot.entity.Deal;
import com.ozdeals.bot.entity.DealStatus;
import com.ozdeals.bot.entity.ManualDealInputStep;
import com.ozdeals.bot.entity.TelegramAdminConversation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
class TelegramAdminConversationRepositoryTest {

    @Autowired
    private TelegramAdminConversationRepository conversationRepository;

    @Autowired
    private DealRepository dealRepository;

    private Deal draftDeal() {
        return dealRepository.save(Deal.builder().status(DealStatus.DRAFT).build());
    }

    @Test
    void save_newConversation_setsTimestampsAndStep() {
        TelegramAdminConversation saved = conversationRepository.save(TelegramAdminConversation.builder()
                .telegramUserId(111L)
                .deal(draftDeal())
                .currentStep(ManualDealInputStep.SOURCE_URL)
                .build());

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getCurrentStep()).isEqualTo(ManualDealInputStep.SOURCE_URL);
    }

    @Test
    void findByTelegramUserId_existing_returnsConversation() {
        conversationRepository.save(TelegramAdminConversation.builder()
                .telegramUserId(222L)
                .deal(draftDeal())
                .currentStep(ManualDealInputStep.SOURCE_URL)
                .build());

        Optional<TelegramAdminConversation> result = conversationRepository.findByTelegramUserId(222L);

        assertThat(result).isPresent();
    }

    @Test
    void findByTelegramUserId_missing_returnsEmpty() {
        Optional<TelegramAdminConversation> result = conversationRepository.findByTelegramUserId(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void save_duplicateTelegramUserId_throwsConstraintViolation() {
        conversationRepository.saveAndFlush(TelegramAdminConversation.builder()
                .telegramUserId(333L)
                .deal(draftDeal())
                .currentStep(ManualDealInputStep.SOURCE_URL)
                .build());

        TelegramAdminConversation duplicate = TelegramAdminConversation.builder()
                .telegramUserId(333L)
                .deal(draftDeal())
                .currentStep(ManualDealInputStep.SOURCE_URL)
                .build();

        assertThatThrownBy(() -> conversationRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteByTelegramUserId_removesConversation() {
        conversationRepository.saveAndFlush(TelegramAdminConversation.builder()
                .telegramUserId(444L)
                .deal(draftDeal())
                .currentStep(ManualDealInputStep.SOURCE_URL)
                .build());

        conversationRepository.deleteByTelegramUserId(444L);

        assertThat(conversationRepository.findByTelegramUserId(444L)).isEmpty();
    }
}
