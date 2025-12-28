package com.uit.transactionservice.mapper;

import com.uit.transactionservice.dto.response.TransactionFeeResponse;
import com.uit.transactionservice.dto.response.TransactionLimitResponse;
import com.uit.transactionservice.dto.response.TransactionResponse;
import com.uit.transactionservice.entity.Transaction;
import com.uit.transactionservice.entity.TransactionFee;
import com.uit.transactionservice.entity.TransactionLimit;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(target = "requireFaceAuth", ignore = true)
    TransactionResponse toResponse(Transaction transaction);

    @Mapping(target = "dailyRemaining", expression = "java(limit.getDailyLimit().subtract(limit.getDailyUsed()))")
    @Mapping(target = "monthlyRemaining", expression = "java(limit.getMonthlyLimit().subtract(limit.getMonthlyUsed()))")
    TransactionLimitResponse toLimitResponse(TransactionLimit limit);

    @Mapping(source = "feeId", target = "fee_id")
    @Mapping(source = "feeAmount", target = "fixedFee")
    @Mapping(target = "feePercentage", ignore = true)
    @Mapping(target = "minFee", ignore = true)
    @Mapping(target = "maxFee", ignore = true)
    TransactionFeeResponse toFeeResponse(TransactionFee fee);
}
