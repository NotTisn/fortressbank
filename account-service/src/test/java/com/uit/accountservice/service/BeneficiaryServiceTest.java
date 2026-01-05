package com.uit.accountservice.service;

import com.uit.accountservice.client.UserClient;
import com.uit.accountservice.dto.request.BeneficiaryRequest;
import com.uit.accountservice.dto.request.BeneficiaryUpdateRequest;
import com.uit.accountservice.dto.response.UserResponse;
import com.uit.accountservice.entity.Account;
import com.uit.accountservice.entity.Beneficiary;
import com.uit.accountservice.repository.AccountRepository;
import com.uit.accountservice.repository.BeneficiaryRepository;
import com.uit.sharedkernel.api.ApiResponse;
import com.uit.sharedkernel.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BeneficiaryService
 * Tests CRUD operations for beneficiary management
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BeneficiaryService Unit Tests")
class BeneficiaryServiceTest {

    @Mock
    private BeneficiaryRepository beneficiaryRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserClient userClient;

    @InjectMocks
    private BeneficiaryService beneficiaryService;

    private String testUserId;
    private String testAccountNumber;
    private Beneficiary testBeneficiary;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        testUserId = "user-123";
        testAccountNumber = "1234567890";

        testAccount = Account.builder()
                .accountId("account-456")
                .userId("user-456")
                .accountNumber(testAccountNumber)
                .balance(BigDecimal.valueOf(1000.0))
                .build();

        testBeneficiary = Beneficiary.builder()
                .id(1L)
                .ownerId(testUserId)
                .accountNumber(testAccountNumber)
                .accountName("John Doe")
                .bankName("FortressBank")
                .nickName("John")
                .build();
    }

    // ===== getMyBeneficiaries Tests =====

    @Test
    @DisplayName("getMyBeneficiaries() should return list of beneficiaries for user")
    void testGetMyBeneficiaries_Success() {
        // Given
        Beneficiary beneficiary2 = Beneficiary.builder()
                .id(2L)
                .ownerId(testUserId)
                .accountNumber("0987654321")
                .accountName("Jane Smith")
                .bankName("ExternalBank")
                .build();

        List<Beneficiary> expectedList = Arrays.asList(testBeneficiary, beneficiary2);
        when(beneficiaryRepository.findByOwnerId(testUserId)).thenReturn(expectedList);

        // When
        List<Beneficiary> result = beneficiaryService.getMyBeneficiaries(testUserId);

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).contains(testBeneficiary, beneficiary2);
        verify(beneficiaryRepository).findByOwnerId(testUserId);
    }

    @Test
    @DisplayName("getMyBeneficiaries() should return empty list when user has no beneficiaries")
    void testGetMyBeneficiaries_EmptyList() {
        // Given
        when(beneficiaryRepository.findByOwnerId(testUserId)).thenReturn(List.of());

        // When
        List<Beneficiary> result = beneficiaryService.getMyBeneficiaries(testUserId);

        // Then
        assertThat(result).isEmpty();
        verify(beneficiaryRepository).findByOwnerId(testUserId);
    }

    // ===== addBeneficiary Tests =====

    @Test
    @DisplayName("addBeneficiary() should add internal bank beneficiary with auto-fetched name")
    void testAddBeneficiary_InternalBank_Success() {
        // Given
        BeneficiaryRequest request = new BeneficiaryRequest(
                testAccountNumber,
                null, // accountName will be auto-fetched
                "FortressBank",
                "John"
        );

        UserResponse userResponse = new UserResponse(
                "user-456",
                "johndoe",
                "johndoe@example.com",
                "John Doe",
                null, null, null, null
        );
        ApiResponse<UserResponse> apiResponse = ApiResponse.success(userResponse);

        when(beneficiaryRepository.existsByOwnerIdAndAccountNumber(testUserId, testAccountNumber))
                .thenReturn(false);
        when(accountRepository.findByAccountNumber(testAccountNumber))
                .thenReturn(Optional.of(testAccount));
        when(userClient.getUserById("user-456")).thenReturn(apiResponse);
        when(beneficiaryRepository.save(any(Beneficiary.class))).thenReturn(testBeneficiary);

        // When
        Beneficiary result = beneficiaryService.addBeneficiary(testUserId, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAccountNumber()).isEqualTo(testAccountNumber);
        verify(accountRepository).findByAccountNumber(testAccountNumber);
        verify(userClient).getUserById("user-456");
        verify(beneficiaryRepository).save(any(Beneficiary.class));
    }

    @Test
    @DisplayName("addBeneficiary() should add external bank beneficiary with provided name")
    void testAddBeneficiary_ExternalBank_Success() {
        // Given
        BeneficiaryRequest request = new BeneficiaryRequest(
                "9999999999",
                "External User",
                "ExternalBank",
                "External"
        );

        Beneficiary externalBeneficiary = Beneficiary.builder()
                .id(2L)
                .ownerId(testUserId)
                .accountNumber("9999999999")
                .accountName("External User")
                .bankName("ExternalBank")
                .nickName("External")
                .build();

        when(beneficiaryRepository.existsByOwnerIdAndAccountNumber(testUserId, "9999999999"))
                .thenReturn(false);
        when(beneficiaryRepository.save(any(Beneficiary.class))).thenReturn(externalBeneficiary);

        // When
        Beneficiary result = beneficiaryService.addBeneficiary(testUserId, request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getAccountNumber()).isEqualTo("9999999999");
        assertThat(result.getAccountName()).isEqualTo("External User");
        assertThat(result.getBankName()).isEqualTo("ExternalBank");
        verify(beneficiaryRepository).save(any(Beneficiary.class));
        verifyNoInteractions(accountRepository);
        verifyNoInteractions(userClient);
    }

    @Test
    @DisplayName("addBeneficiary() should default to FortressBank when bank name not provided")
    void testAddBeneficiary_DefaultToFortressBank() {
        // Given
        BeneficiaryRequest request = new BeneficiaryRequest(
                testAccountNumber,
                null,
                null, // bankName not provided
                "Friend"
        );

        UserResponse userResponse = new UserResponse(
                "user-456",
                "john",
                "john@example.com",
                "John Doe",
                null, null, null, null
        );
        ApiResponse<UserResponse> apiResponse = ApiResponse.success(userResponse);

        when(beneficiaryRepository.existsByOwnerIdAndAccountNumber(testUserId, testAccountNumber))
                .thenReturn(false);
        when(accountRepository.findByAccountNumber(testAccountNumber))
                .thenReturn(Optional.of(testAccount));
        when(userClient.getUserById("user-456")).thenReturn(apiResponse);
        when(beneficiaryRepository.save(any(Beneficiary.class))).thenReturn(testBeneficiary);

        // When
        Beneficiary result = beneficiaryService.addBeneficiary(testUserId, request);

        // Then
        assertThat(result).isNotNull();
        verify(accountRepository).findByAccountNumber(testAccountNumber);
    }

    @Test
    @DisplayName("addBeneficiary() should throw exception when account number is blank")
    void testAddBeneficiary_BlankAccountNumber() {
        // Given
        BeneficiaryRequest request = new BeneficiaryRequest(
                "",
                "Name",
                "Bank",
                "Nick"
        );

        // When & Then
        assertThatThrownBy(() -> beneficiaryService.addBeneficiary(testUserId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Account number is required");

        verifyNoInteractions(beneficiaryRepository);
    }

    @Test
    @DisplayName("addBeneficiary() should throw exception when beneficiary already exists")
    void testAddBeneficiary_DuplicateBeneficiary() {
        // Given
        BeneficiaryRequest request = new BeneficiaryRequest(
                testAccountNumber,
                "Name",
                "FortressBank",
                "Nick"
        );

        when(beneficiaryRepository.existsByOwnerIdAndAccountNumber(testUserId, testAccountNumber))
                .thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> beneficiaryService.addBeneficiary(testUserId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("already in your beneficiary list");

        verify(beneficiaryRepository).existsByOwnerIdAndAccountNumber(testUserId, testAccountNumber);
        verify(beneficiaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("addBeneficiary() should throw exception when internal account not found")
    void testAddBeneficiary_InternalAccountNotFound() {
        // Given
        BeneficiaryRequest request = new BeneficiaryRequest(
                "nonexistent123",
                null,
                "FortressBank",
                "Nick"
        );

        when(beneficiaryRepository.existsByOwnerIdAndAccountNumber(testUserId, "nonexistent123"))
                .thenReturn(false);
        when(accountRepository.findByAccountNumber("nonexistent123"))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> beneficiaryService.addBeneficiary(testUserId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Internal account number not found");

        verify(accountRepository).findByAccountNumber("nonexistent123");
        verify(beneficiaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("addBeneficiary() should throw exception when trying to add own account")
    void testAddBeneficiary_CannotAddOwnAccount() {
        // Given
        Account ownAccount = Account.builder()
                .accountId("account-123")
                .userId(testUserId) // Same as current user
                .accountNumber("1111111111")
                .build();

        BeneficiaryRequest request = new BeneficiaryRequest(
                "1111111111",
                null,
                "FortressBank",
                "My Account"
        );

        when(beneficiaryRepository.existsByOwnerIdAndAccountNumber(testUserId, "1111111111"))
                .thenReturn(false);
        when(accountRepository.findByAccountNumber("1111111111"))
                .thenReturn(Optional.of(ownAccount));

        // When & Then
        assertThatThrownBy(() -> beneficiaryService.addBeneficiary(testUserId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Cannot add your own account");

        verify(beneficiaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("addBeneficiary() should throw exception when external bank account name not provided")
    void testAddBeneficiary_ExternalBank_NoAccountName() {
        // Given
        BeneficiaryRequest request = new BeneficiaryRequest(
                "9999999999",
                null, // No account name
                "ExternalBank",
                "External"
        );

        when(beneficiaryRepository.existsByOwnerIdAndAccountNumber(testUserId, "9999999999"))
                .thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> beneficiaryService.addBeneficiary(testUserId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Account name is required for external bank accounts");

        verify(beneficiaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("addBeneficiary() should use fallback name when user service fails")
    void testAddBeneficiary_UserServiceFails_UseFallback() {
        // Given
        BeneficiaryRequest request = new BeneficiaryRequest(
                testAccountNumber,
                "Fallback Name",
                "FortressBank",
                "Friend"
        );

        when(beneficiaryRepository.existsByOwnerIdAndAccountNumber(testUserId, testAccountNumber))
                .thenReturn(false);
        when(accountRepository.findByAccountNumber(testAccountNumber))
                .thenReturn(Optional.of(testAccount));
        when(userClient.getUserById("user-456")).thenThrow(new RuntimeException("Service unavailable"));
        when(beneficiaryRepository.save(any(Beneficiary.class))).thenReturn(testBeneficiary);

        // When
        Beneficiary result = beneficiaryService.addBeneficiary(testUserId, request);

        // Then
        assertThat(result).isNotNull();
        verify(userClient).getUserById("user-456");
        verify(beneficiaryRepository).save(any(Beneficiary.class));
    }

    // ===== updateBeneficiary Tests =====

    @Test
    @DisplayName("updateBeneficiary() should update nickname successfully")
    void testUpdateBeneficiary_UpdateNickname_Success() {
        // Given
        BeneficiaryUpdateRequest request = new BeneficiaryUpdateRequest("New Nickname", null);

        when(beneficiaryRepository.findById(1L)).thenReturn(Optional.of(testBeneficiary));
        when(beneficiaryRepository.save(any(Beneficiary.class))).thenReturn(testBeneficiary);

        // When
        Beneficiary result = beneficiaryService.updateBeneficiary(1L, testUserId, request);

        // Then
        assertThat(result).isNotNull();
        verify(beneficiaryRepository).findById(1L);
        verify(beneficiaryRepository).save(any(Beneficiary.class));
    }

    @Test
    @DisplayName("updateBeneficiary() should update account name for external bank")
    void testUpdateBeneficiary_UpdateAccountName_ExternalBank() {
        // Given
        Beneficiary externalBeneficiary = Beneficiary.builder()
                .id(2L)
                .ownerId(testUserId)
                .accountNumber("9999999999")
                .accountName("Old Name")
                .bankName("ExternalBank")
                .build();

        BeneficiaryUpdateRequest request = new BeneficiaryUpdateRequest(null, "New Name");

        when(beneficiaryRepository.findById(2L)).thenReturn(Optional.of(externalBeneficiary));
        when(beneficiaryRepository.save(any(Beneficiary.class))).thenReturn(externalBeneficiary);

        // When
        Beneficiary result = beneficiaryService.updateBeneficiary(2L, testUserId, request);

        // Then
        assertThat(result).isNotNull();
        verify(beneficiaryRepository).save(any(Beneficiary.class));
    }

    @Test
    @DisplayName("updateBeneficiary() should NOT update account name for internal bank")
    void testUpdateBeneficiary_CannotUpdateAccountName_InternalBank() {
        // Given
        BeneficiaryUpdateRequest request = new BeneficiaryUpdateRequest(null, "New Name");

        when(beneficiaryRepository.findById(1L)).thenReturn(Optional.of(testBeneficiary));
        when(beneficiaryRepository.save(any(Beneficiary.class))).thenReturn(testBeneficiary);

        // When
        Beneficiary result = beneficiaryService.updateBeneficiary(1L, testUserId, request);

        // Then
        assertThat(result).isNotNull();
        // Account name should not be updated for internal bank
        verify(beneficiaryRepository).save(any(Beneficiary.class));
    }

    @Test
    @DisplayName("updateBeneficiary() should throw exception when beneficiary not found")
    void testUpdateBeneficiary_NotFound() {
        // Given
        BeneficiaryUpdateRequest request = new BeneficiaryUpdateRequest("New Nick", null);

        when(beneficiaryRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> beneficiaryService.updateBeneficiary(999L, testUserId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Beneficiary not found");

        verify(beneficiaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateBeneficiary() should throw exception when user does not own beneficiary")
    void testUpdateBeneficiary_NotOwner() {
        // Given
        BeneficiaryUpdateRequest request = new BeneficiaryUpdateRequest("New Nick", null);
        String otherUserId = "user-999";

        when(beneficiaryRepository.findById(1L)).thenReturn(Optional.of(testBeneficiary));

        // When & Then
        assertThatThrownBy(() -> beneficiaryService.updateBeneficiary(1L, otherUserId, request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("You do not own this beneficiary");

        verify(beneficiaryRepository, never()).save(any());
    }

    // ===== deleteBeneficiary Tests =====

    @Test
    @DisplayName("deleteBeneficiary() should delete beneficiary successfully")
    void testDeleteBeneficiary_Success() {
        // Given
        when(beneficiaryRepository.findById(1L)).thenReturn(Optional.of(testBeneficiary));

        // When
        beneficiaryService.deleteBeneficiary(1L, testUserId);

        // Then
        verify(beneficiaryRepository).findById(1L);
        verify(beneficiaryRepository).delete(testBeneficiary);
    }

    @Test
    @DisplayName("deleteBeneficiary() should throw exception when beneficiary not found")
    void testDeleteBeneficiary_NotFound() {
        // Given
        when(beneficiaryRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> beneficiaryService.deleteBeneficiary(999L, testUserId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Beneficiary not found");

        verify(beneficiaryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteBeneficiary() should throw exception when user does not own beneficiary")
    void testDeleteBeneficiary_NotOwner() {
        // Given
        String otherUserId = "user-999";

        when(beneficiaryRepository.findById(1L)).thenReturn(Optional.of(testBeneficiary));

        // When & Then
        assertThatThrownBy(() -> beneficiaryService.deleteBeneficiary(1L, otherUserId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("You do not own this beneficiary");

        verify(beneficiaryRepository, never()).delete(any());
    }
}
