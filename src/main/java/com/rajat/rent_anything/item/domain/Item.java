package com.rajat.rent_anything.item.domain;

import com.rajat.rent_anything.common.enums.ErrorCode;
import com.rajat.rent_anything.item.exceptions.InvalidItemException;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class Item {
    private Long id;
    private Long ownerId;
    private Long categoryId;

    private String title;
    private String description;

    private double pricePerDay;
    private double depositAmount;

    private ItemStatus status;

    private LocalDate availableFrom;
    private LocalDate availableTo;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Double longitude;
    private Double latitude;

    private Item(){}

    public static Item create(
            Long ownerId,
            Long categoryId,
            String title,
            String description,
            double pricePerDay,
            double depositAmount,
            LocalDate availableFrom,
            LocalDate availableTo,
            Double longitude,
            Double latitude
    ) {
        if (latitude < -90 || latitude > 90) {
            throw new InvalidItemException(ErrorCode.INVALID_ITEM_LOCATION, "Invalid latitude");
        }

        if (longitude < -180 || longitude > 180) {
            throw new InvalidItemException(ErrorCode.INVALID_ITEM_LOCATION, "Invalid longitude");
        }

        if (pricePerDay <= 0) {
            throw new InvalidItemException(ErrorCode.INVALID_ITEM_PRICING, "Price must be positive");
        }

        if (depositAmount < 0) {
            throw new InvalidItemException(ErrorCode.INVALID_ITEM_PRICING, "Deposit cannot be negative");
        }

        if (availableFrom.isAfter(availableTo)) {
            throw new InvalidItemException(ErrorCode.INVALID_AVAILABILITY_WINDOW, "Invalid availability window");
        }
        Item item = new Item();
        item.setOwnerId(ownerId);
        item.setCategoryId(categoryId);
        item.setTitle(title);
        item.setDescription(description);
        item.setPricePerDay(pricePerDay);
        item.status = ItemStatus.ACTIVE;
        item.createdAt = LocalDateTime.now();
        item.updatedAt = LocalDateTime.now();
        item.setDepositAmount(depositAmount);
        item.setAvailableFrom(availableFrom);
        item.setAvailableTo(availableTo);
        item.latitude = latitude;
        item.longitude = longitude;
        return item;
    }

    public static Item rehydrate(
            Long id,
            Long ownerId,
            Long categoryId,
            String title,
            String description,
            double pricePerDay,
            double depositAmount,
            ItemStatus status,
            LocalDate availableFrom,
            LocalDate availableTo,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Double longitude,
            Double latitude
    ) {
        Item item = new Item();

        item.id = id;
        item.ownerId = ownerId;
        item.categoryId = categoryId;
        item.title = title;
        item.description = description;
        item.pricePerDay = pricePerDay;
        item.depositAmount = depositAmount;
        item.status = status;
        item.availableFrom = availableFrom;
        item.availableTo = availableTo;
        item.createdAt = createdAt;
        item.updatedAt = updatedAt;
        item.longitude = longitude;
        item.latitude = latitude;
        return item;
    }



    public void activate() {
        this.status = ItemStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isAvailableFor(LocalDate start, LocalDate end) {
        return !start.isBefore(availableFrom) && !end.isAfter(availableTo);
    }

    public boolean isActive() {
        return status == ItemStatus.ACTIVE;
    }
}
