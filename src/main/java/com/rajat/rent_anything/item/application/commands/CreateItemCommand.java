package com.rajat.rent_anything.item.application.commands;

import java.time.LocalDate;

public record CreateItemCommand(
        Long categoryId,
        String title,
        String description,
        Double pricePerDay,
        Double depositAmount,
        LocalDate availableFrom,
        LocalDate availableTo,
        Double longitude,
        Double latitude
) {}