package com.rajat.rent_anything.item.infrastructure;

import com.rajat.rent_anything.item.domain.Item;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
/**
 * Mapper responsible for converting between:
 *
 * - Item domain objects
 * - Item persistence entities
 *
 * The domain model represents business concepts and rules,
 * while the entity model represents how data is stored in
 * the database.
 *
 * This mapper also handles conversion between:
 * - Latitude/Longitude coordinates in the domain model
 * - PostGIS Point objects used by the persistence layer
 */
public class ItemMapper {

    /**
     * Geometry factory used for creating PostGIS Point objects.
     *
     * SRID 4326 corresponds to the WGS84 coordinate system,
     * which is the standard format used by GPS coordinates.
     */
    private static final GeometryFactory geometryFactory =
            new GeometryFactory(new PrecisionModel(), 4326);

    /**
     * Converts a domain Item into a persistence entity.
     *
     * During this conversion:
     * - Business fields are copied directly.
     * - Latitude and longitude are converted into a
     *   PostGIS Point for geospatial storage and querying.
     *
     * Coordinate Convention:
     * - X = Longitude
     * - Y = Latitude
     *
     * @param item domain item
     * @return persistence entity
     */
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

        // PostGIS expects coordinates in the format:
        // X = Longitude
        // Y = Latitude
        Coordinate coordinate =
                new Coordinate(
                        item.getLongitude(),
                        item.getLatitude()
                );

        Point point = geometryFactory.createPoint(coordinate);

        point.setSRID(4326);

        entity.setLocation(point);

        return entity;
    }

    /**
     * Converts a persistence entity into a domain Item.
     *
     * During this conversion:
     * - Stored PostGIS coordinates are extracted.
     * - Point geometry is converted back into
     *   latitude and longitude values.
     *
     * @param entity persistence entity
     * @return domain item
     */
    public static Item toDomain(ItemEntity entity) {

        Point point = entity.getLocation();

        double longitude = 0.0;
        double latitude = 0.0;

        if (point != null) {

            // PostGIS stores:
            // X = Longitude
            // Y = Latitude
            latitude = point.getY();
            longitude = point.getX();
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