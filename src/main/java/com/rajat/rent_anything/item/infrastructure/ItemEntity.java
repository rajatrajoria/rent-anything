package com.rajat.rent_anything.item.infrastructure;

import com.rajat.rent_anything.item.domain.ItemStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name="items", schema = "item_schema")
public class ItemEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long ownerId;
    private Long categoryId;
    private String title;
    private String description;
    private double pricePerDay;
    private double depositAmount;
    @Enumerated(EnumType.STRING)
    private ItemStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDate availableFrom;
    private LocalDate availableTo;
    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;
}
