package com.rajat.rent_anything.item.infrastructure;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "item_images", schema = "item_schema")
@Getter
@Setter
public class ItemImageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long itemId;

    private String imageKey;

    private Integer displayOrder;

    private boolean isThumbnail;

    private LocalDateTime createdAt;
}