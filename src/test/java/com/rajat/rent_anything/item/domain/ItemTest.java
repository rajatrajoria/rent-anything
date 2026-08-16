package com.rajat.rent_anything.item.domain;

import com.rajat.rent_anything.item.exceptions.InvalidItemException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ItemTest {

    private static final Long OWNER_ID = 1L;
    private static final Long CATEGORY_ID = 2L;
    private static final LocalDate FROM = LocalDate.of(2026, 1, 1);
    private static final LocalDate TO = LocalDate.of(2026, 12, 31);

    private static Item validItem() {
        return Item.create(OWNER_ID, CATEGORY_ID, "Camera", "A nice camera", 100.0, 50.0, FROM, TO, 12.34, 56.78);
    }

    @Test
    void create_succeedsAndStartsInactive() {
        Item item = validItem();

        assertThat(item.getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.INACTIVE);
        assertThat(item.getCreatedAt()).isNotNull();
        assertThat(item.getUpdatedAt()).isNotNull();
    }

    @Test
    void create_latitudeBelowMinimum_throws() {
        assertThrows(InvalidItemException.class, () ->
                Item.create(OWNER_ID, CATEGORY_ID, "x", "y", 10.0, 0.0, FROM, TO, 0.0, -90.1));
    }

    @Test
    void create_latitudeAboveMaximum_throws() {
        assertThrows(InvalidItemException.class, () ->
                Item.create(OWNER_ID, CATEGORY_ID, "x", "y", 10.0, 0.0, FROM, TO, 0.0, 90.1));
    }

    @Test
    void create_longitudeBelowMinimum_throws() {
        assertThrows(InvalidItemException.class, () ->
                Item.create(OWNER_ID, CATEGORY_ID, "x", "y", 10.0, 0.0, FROM, TO, -180.1, 0.0));
    }

    @Test
    void create_longitudeAboveMaximum_throws() {
        assertThrows(InvalidItemException.class, () ->
                Item.create(OWNER_ID, CATEGORY_ID, "x", "y", 10.0, 0.0, FROM, TO, 180.1, 0.0));
    }

    @Test
    void create_zeroPrice_throws() {
        assertThrows(InvalidItemException.class, () ->
                Item.create(OWNER_ID, CATEGORY_ID, "x", "y", 0.0, 0.0, FROM, TO, 0.0, 0.0));
    }

    @Test
    void create_negativePrice_throws() {
        assertThrows(InvalidItemException.class, () ->
                Item.create(OWNER_ID, CATEGORY_ID, "x", "y", -5.0, 0.0, FROM, TO, 0.0, 0.0));
    }

    @Test
    void create_negativeDeposit_throws() {
        assertThrows(InvalidItemException.class, () ->
                Item.create(OWNER_ID, CATEGORY_ID, "x", "y", 10.0, -1.0, FROM, TO, 0.0, 0.0));
    }

    @Test
    void create_availableFromAfterAvailableTo_throws() {
        assertThrows(InvalidItemException.class, () ->
                Item.create(OWNER_ID, CATEGORY_ID, "x", "y", 10.0, 0.0, TO, FROM, 0.0, 0.0));
    }

    @Test
    void activate_setsStatusActiveAndUpdatesTimestamp() {
        Item item = validItem();

        item.activate();

        assertThat(item.getStatus()).isEqualTo(ItemStatus.ACTIVE);
        assertThat(item.isActive()).isTrue();
    }

    @Test
    void isAvailableFor_rangeWithinAvailability_returnsTrue() {
        Item item = validItem();

        assertThat(item.isAvailableFor(FROM.plusDays(1), TO.minusDays(1))).isTrue();
    }

    @Test
    void isAvailableFor_rangeStartingBeforeAvailability_returnsFalse() {
        Item item = validItem();

        assertThat(item.isAvailableFor(FROM.minusDays(1), TO)).isFalse();
    }

    @Test
    void isAvailableFor_rangeEndingAfterAvailability_returnsFalse() {
        Item item = validItem();

        assertThat(item.isAvailableFor(FROM, TO.plusDays(1))).isFalse();
    }

    @Test
    void isActive_whenInactive_returnsFalse() {
        Item item = validItem();

        assertThat(item.isActive()).isFalse();
    }
}
