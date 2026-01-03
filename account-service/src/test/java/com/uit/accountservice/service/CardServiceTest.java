package com.uit.accountservice.service;

import com.uit.accountservice.client.UserClient;
import com.uit.accountservice.dto.CardDto;
import com.uit.accountservice.dto.response.UserResponse;
import com.uit.accountservice.entity.Account;
import com.uit.accountservice.entity.Card;
import com.uit.accountservice.entity.enums.CardStatus;
import com.uit.accountservice.entity.enums.CardType;
import com.uit.accountservice.repository.AccountRepository;
import com.uit.accountservice.repository.CardRepository;
import com.uit.sharedkernel.api.ApiResponse;
import com.uit.sharedkernel.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CardService
 * Tests card issuance, card listing, card lock/unlock
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CardService Unit Tests")
class CardServiceTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private CardService cardService;

    private String testUserId;
    private String testAccountId;
    private Account testAccount;
    private Card testCard;

    @BeforeEach
    void setUp() {
        testUserId = "user-123";
        testAccountId = "account-456";

        testAccount = Account.builder()
                .accountId(testAccountId)
                .userId(testUserId)
                .accountNumber("1234567890")
                .balance(BigDecimal.valueOf(10000))
                .build();

        testCard = Card.builder()
                .cardId("card-789")
                .accountId(testAccountId)
                .cardNumber("6886123456789012")
                .cardHolderName("JOHN DOE")
                .cvvHash("$2a$10$hashedCVV")
                .expirationDate(LocalDate.now().plusYears(5))
                .cardType(CardType.VIRTUAL)
                .status(CardStatus.ACTIVE)
                .build();
    }

    // ===== getCardsByAccountId Tests =====

    @Test
    @DisplayName("getCardsByAccountId() should return list of cards for valid account")
    void testGetCardsByAccountId_Success() {
        // Given
        Card card2 = Card.builder()
                .cardId("card-999")
                .accountId(testAccountId)
                .cardNumber("6886999988887777")
                .cardHolderName("JOHN DOE")
                .expirationDate(LocalDate.now().plusYears(3))
                .cardType(CardType.VIRTUAL)
                .status(CardStatus.LOCKED)
                .build();

        when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));
        when(cardRepository.findByAccountId(testAccountId)).thenReturn(Arrays.asList(testCard, card2));

        // When
        List<CardDto> result = cardService.getCardsByAccountId(testAccountId, testUserId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCardNumber()).isEqualTo("6886123456789012");
        assertThat(result.get(0).getStatus()).isEqualTo("ACTIVE");
        assertThat(result.get(1).getCardId()).isEqualTo("card-999");
        assertThat(result.get(1).getStatus()).isEqualTo("LOCKED");
        verify(accountRepository).findById(testAccountId);
        verify(cardRepository).findByAccountId(testAccountId);
    }

    @Test
    @DisplayName("getCardsByAccountId() should return empty list when account has no cards")
    void testGetCardsByAccountId_NoCards() {
        // Given
        when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));
        when(cardRepository.findByAccountId(testAccountId)).thenReturn(List.of());

        // When
        List<CardDto> result = cardService.getCardsByAccountId(testAccountId, testUserId);

        // Then
        assertThat(result).isEmpty();
        verify(cardRepository).findByAccountId(testAccountId);
    }

    @Test
    @DisplayName("getCardsByAccountId() should throw exception when account not found")
    void testGetCardsByAccountId_AccountNotFound() {
        // Given
        when(accountRepository.findById(testAccountId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> cardService.getCardsByAccountId(testAccountId, testUserId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Account not found");

        verify(cardRepository, never()).findByAccountId(any());
    }

    @Test
    @DisplayName("getCardsByAccountId() should throw exception when user does not own account")
    void testGetCardsByAccountId_NotOwner() {
        // Given
        String otherUserId = "user-999";
        when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));

        // When & Then
        assertThatThrownBy(() -> cardService.getCardsByAccountId(testAccountId, otherUserId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("You do not have permission to perform this action");

        verify(cardRepository, never()).findByAccountId(any());
    }

    // ===== issueCard Tests =====

    @Test
    @DisplayName("issueCard() should issue new card with user's full name")
    void testIssueCard_Success() {
        // Given
        UserResponse userResponse = new UserResponse(
                testUserId,
                "johndoe",
                "john@example.com",
                "John Doe",
                null, null, null, null
        );
        ApiResponse<UserResponse> apiResponse = ApiResponse.success(userResponse);

        when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));
        when(userClient.getUserById(testUserId)).thenReturn(apiResponse);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedCVV");
        when(cardRepository.save(any(Card.class))).thenReturn(testCard);

        // When
        CardDto result = cardService.issueCard(testUserId, testAccountId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCardNumber()).isNotNull();
        assertThat(result.getCardType()).isEqualTo("VIRTUAL");
        assertThat(result.getStatus()).isEqualTo("ACTIVE");

        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(cardCaptor.capture());
        Card savedCard = cardCaptor.getValue();
        assertThat(savedCard.getAccountId()).isEqualTo(testAccountId);
        assertThat(savedCard.getCardHolderName()).isEqualTo("JOHN DOE");
        assertThat(savedCard.getCardType()).isEqualTo(CardType.VIRTUAL);
        assertThat(savedCard.getStatus()).isEqualTo(CardStatus.ACTIVE);
    }

    @Test
    @DisplayName("issueCard() should use default name when user service fails")
    void testIssueCard_UserServiceFails() {
        // Given
        when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));
        when(userClient.getUserById(testUserId)).thenThrow(new RuntimeException("Service unavailable"));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedCVV");
        when(cardRepository.save(any(Card.class))).thenReturn(testCard);

        // When
        CardDto result = cardService.issueCard(testUserId, testAccountId);

        // Then
        assertThat(result).isNotNull();
        
        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(cardCaptor.capture());
        Card savedCard = cardCaptor.getValue();
        assertThat(savedCard.getCardHolderName()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("issueCard() should throw exception when account not found")
    void testIssueCard_AccountNotFound() {
        // Given
        when(accountRepository.findById(testAccountId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> cardService.issueCard(testUserId, testAccountId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Account not found");

        verify(cardRepository, never()).save(any());
    }

    @Test
    @DisplayName("issueCard() should throw exception when user does not own account")
    void testIssueCard_NotOwner() {
        // Given
        String otherUserId = "user-999";
        when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));

        // When & Then
        assertThatThrownBy(() -> cardService.issueCard(otherUserId, testAccountId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("You do not have permission to perform this action");

        verify(cardRepository, never()).save(any());
    }

    // ===== createInitialCard Tests =====

    @Test
    @DisplayName("createInitialCard() should create card with provided full name")
    void testCreateInitialCard_WithFullName() {
        // Given
        String fullName = "Jane Smith";
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedCVV");

        // When
        cardService.createInitialCard(testAccount, fullName);

        // Then
        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(cardCaptor.capture());
        Card savedCard = cardCaptor.getValue();
        
        assertThat(savedCard.getAccountId()).isEqualTo(testAccountId);
        assertThat(savedCard.getCardHolderName()).isEqualTo("JANE SMITH");
        assertThat(savedCard.getCardType()).isEqualTo(CardType.VIRTUAL);
        assertThat(savedCard.getStatus()).isEqualTo(CardStatus.ACTIVE);
        assertThat(savedCard.getExpirationDate()).isAfter(LocalDate.now());
    }

    @Test
    @DisplayName("createInitialCard() should use default name when fullName is null")
    void testCreateInitialCard_NoFullName() {
        // Given
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedCVV");

        // When
        cardService.createInitialCard(testAccount, null);

        // Then
        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(cardCaptor.capture());
        Card savedCard = cardCaptor.getValue();
        
        assertThat(savedCard.getCardHolderName()).isEqualTo("VALUED CUSTOMER");
    }

    @Test
    @DisplayName("createInitialCard() should use default name when fullName is empty")
    void testCreateInitialCard_EmptyFullName() {
        // Given
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedCVV");

        // When
        cardService.createInitialCard(testAccount, "");

        // Then
        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(cardCaptor.capture());
        Card savedCard = cardCaptor.getValue();
        
        assertThat(savedCard.getCardHolderName()).isEqualTo("VALUED CUSTOMER");
    }

    // ===== toggleCardLock Tests =====

    @Test
    @DisplayName("toggleCardLock() should lock an active card")
    void testToggleCardLock_LockActiveCard() {
        // Given
        when(cardRepository.findById("card-789")).thenReturn(Optional.of(testCard));
        when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));
        when(cardRepository.save(any(Card.class))).thenReturn(testCard);

        // When
        cardService.toggleCardLock("card-789", testUserId);

        // Then
        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(cardCaptor.capture());
        Card savedCard = cardCaptor.getValue();
        
        assertThat(savedCard.getStatus()).isEqualTo(CardStatus.LOCKED);
    }

    @Test
    @DisplayName("toggleCardLock() should unlock a locked card")
    void testToggleCardLock_UnlockLockedCard() {
        // Given
        testCard.setStatus(CardStatus.LOCKED);
        when(cardRepository.findById("card-789")).thenReturn(Optional.of(testCard));
        when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));
        when(cardRepository.save(any(Card.class))).thenReturn(testCard);

        // When
        cardService.toggleCardLock("card-789", testUserId);

        // Then
        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(cardCaptor.capture());
        Card savedCard = cardCaptor.getValue();
        
        assertThat(savedCard.getStatus()).isEqualTo(CardStatus.ACTIVE);
    }

    @Test
    @DisplayName("toggleCardLock() should throw exception when card not found")
    void testToggleCardLock_CardNotFound() {
        // Given
        when(cardRepository.findById("card-999")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> cardService.toggleCardLock("card-999", testUserId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Card not found");

        verify(cardRepository, never()).save(any());
    }

    @Test
    @DisplayName("toggleCardLock() should throw exception when user does not own card's account")
    void testToggleCardLock_NotOwner() {
        // Given
        String otherUserId = "user-999";
        when(cardRepository.findById("card-789")).thenReturn(Optional.of(testCard));
        when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));

        // When & Then
        assertThatThrownBy(() -> cardService.toggleCardLock("card-789", otherUserId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("You do not have permission to perform this action");

        verify(cardRepository, never()).save(any());
    }

    // ===== issueCardInternal Tests =====

    @Test
    @DisplayName("issueCardInternal() should issue card with provided full name")
    void testIssueCardInternal_Success() {
        // Given
        String fullName = "Alice Johnson";
        when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedCVV");
        when(cardRepository.save(any(Card.class))).thenReturn(testCard);

        // When
        CardDto result = cardService.issueCardInternal(testAccountId, fullName);

        // Then
        assertThat(result).isNotNull();
        
        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(cardCaptor.capture());
        Card savedCard = cardCaptor.getValue();
        
        assertThat(savedCard.getAccountId()).isEqualTo(testAccountId);
        assertThat(savedCard.getCardHolderName()).isEqualTo("ALICE JOHNSON");
        assertThat(savedCard.getCardType()).isEqualTo(CardType.VIRTUAL);
        assertThat(savedCard.getStatus()).isEqualTo(CardStatus.ACTIVE);
    }

    @Test
    @DisplayName("issueCardInternal() should use UNKNOWN when fullName is null")
    void testIssueCardInternal_NoFullName() {
        // Given
        when(accountRepository.findById(testAccountId)).thenReturn(Optional.of(testAccount));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashedCVV");
        when(cardRepository.save(any(Card.class))).thenReturn(testCard);

        // When
        CardDto result = cardService.issueCardInternal(testAccountId, null);

        // Then
        assertThat(result).isNotNull();
        
        ArgumentCaptor<Card> cardCaptor = ArgumentCaptor.forClass(Card.class);
        verify(cardRepository).save(cardCaptor.capture());
        Card savedCard = cardCaptor.getValue();
        
        assertThat(savedCard.getCardHolderName()).isEqualTo("UNKNOWN");
    }

    @Test
    @DisplayName("issueCardInternal() should throw exception when account not found")
    void testIssueCardInternal_AccountNotFound() {
        // Given
        when(accountRepository.findById(testAccountId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> cardService.issueCardInternal(testAccountId, "Name"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Account not found");

        verify(cardRepository, never()).save(any());
    }
}
