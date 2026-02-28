package com.rajat.rent_anything.item.infrastructure;

import com.rajat.rent_anything.item.domain.Item;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

public class ItemMapper {

    private static final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    public static ItemEntity toEntity(Item item) {
        ItemEntity entity = new ItemEntity();
        entity.setId(item.getId());
        entity.setOwnerId(item.getOwnerId());
        entity.setCategoryId(item.getCategoryId());
        entity.setTitle(item.getTitle());
        entity.setDescription(item.getDescription());
        entity.setDepositAmount(item.getDepositAmount());
        entity.setPricePerDay(item.getPricePerDay());
        entity.setStatus(item.getStatus());
        entity.setCreatedAt(item.getCreatedAt());
        entity.setUpdatedAt(item.getUpdatedAt());
        entity.setAvailableFrom(item.getAvailableFrom());
        entity.setAvailableTo(item.getAvailableTo());
        Coordinate coordinate = new Coordinate(item.getLongitude(), item.getLatitude());
        Point point = geometryFactory.createPoint(coordinate);
        point.setSRID(4326);
        entity.setLocation(point);
        return entity;
    }

    public static Item toDomain(ItemEntity entity) {
        Point point = entity.getLocation();
        double longitude = 0.0;
        double latitude = 0.0;
        if (point != null) {
            latitude = point.getY();   // Y = latitude
            longitude = point.getX();  // X = longitude
        }
        return Item.rehydrate(
                entity.getId(),
                entity.getOwnerId(),
                entity.getCategoryId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getPricePerDay(),
                entity.getDepositAmount(),
                entity.getStatus(),
                entity.getAvailableFrom(),
                entity.getAvailableTo(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                longitude,
                latitude
        );
    }
}