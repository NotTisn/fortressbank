package com.uit.referenceservice.mapper;

import com.uit.referenceservice.dto.response.BankResponse;
import com.uit.referenceservice.entity.Bank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {BankMapperImpl.class})
@DisplayName("BankMapper Unit Tests")
class BankMapperTest {

    @Autowired
    private BankMapper mapper;

    @Test
    @DisplayName("toDto() maps Bank to BankResponse")
    void testToDto() {
        Bank bank = new Bank();
        bank.setBankCode("VCB");
        bank.setBankName("Vietcombank");
        bank.setStatus("active");

        BankResponse dto = mapper.toDto(bank);

        assertThat(dto).isNotNull();
        assertThat(dto.getBankCode()).isEqualTo(bank.getBankCode());
        assertThat(dto.getBankName()).isEqualTo(bank.getBankName());
    }

    @Test
    @DisplayName("toDtoList() maps list of Banks")
    void testToDtoList() {
        Bank bank1 = new Bank();
        bank1.setBankCode("VCB");
        Bank bank2 = new Bank();
        bank2.setBankCode("ACB");

        List<BankResponse> dtos = mapper.toDtoList(Arrays.asList(bank1, bank2));

        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).getBankCode()).isEqualTo("VCB");
        assertThat(dtos.get(1).getBankCode()).isEqualTo("ACB");
    }
}

