package com.rajat.rent_anything.booking.application.commands;

import lombok.NonNull;

import java.time.LocalDate;

public record CreateBookingCommand(
        @NonNull Long itemId,
        @NonNull Long renterId,
        @NonNull LocalDate startDate,
        @NonNull LocalDate endDate
) {}
