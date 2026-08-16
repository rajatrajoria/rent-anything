package com.rajat.rent_anything.item.application.commands;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

public record CreateItemCommand(
        @NotNull Long categoryId,
        @NotBlank String title,
        String description,
        @NotNull @Positive Double pricePerDay,
        @NotNull @PositiveOrZero Double depositAmount,
        @NotNull @FutureOrPresent LocalDate availableFrom,
        @NotNull LocalDate availableTo,
        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude
) {}
