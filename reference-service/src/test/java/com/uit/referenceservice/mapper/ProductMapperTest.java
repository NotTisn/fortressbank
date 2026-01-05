package com.uit.referenceservice.mapper;

import com.uit.referenceservice.dto.response.ProductResponse;
import com.uit.referenceservice.entity.ProductCatalog;
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
@ContextConfiguration(classes = {ProductMapperImpl.class})
@DisplayName("ProductMapper Unit Tests")
class ProductMapperTest {

    @Autowired
    private ProductMapper mapper;

    @Test
    @DisplayName("toDto() maps ProductCatalog to ProductResponse")
    void testToDto() {
        ProductCatalog product = new ProductCatalog();
        product.setProductId(1);
        product.setProductName("Gold Account");
        product.setStatus("active");

        ProductResponse dto = mapper.toDto(product);

        assertThat(dto).isNotNull();
        assertThat(dto.getProductId()).isEqualTo(product.getProductId());
        assertThat(dto.getProductName()).isEqualTo(product.getProductName());
    }

    @Test
    @DisplayName("toDtoList() maps list of Products")
    void testToDtoList() {
        ProductCatalog p1 = new ProductCatalog();
        p1.setProductId(1);
        ProductCatalog p2 = new ProductCatalog();
        p2.setProductId(2);

        List<ProductResponse> dtos = mapper.toDtoList(Arrays.asList(p1, p2));

        assertThat(dtos).hasSize(2);
        assertThat(dtos.get(0).getProductId()).isEqualTo(1);
    }
}

