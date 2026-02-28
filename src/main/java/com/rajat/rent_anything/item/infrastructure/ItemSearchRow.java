package com.rajat.rent_anything.item.infrastructure;

public interface ItemSearchRow {
    Long getItemId();
    Long getOwnerId();
    String getTitle();
    String getDescription();
    Double getPricePerDay();
    Double getDistance();
    Double getTextScore();
}
