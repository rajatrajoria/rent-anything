package com.rajat.rent_anything.item.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemImageRepository  extends JpaRepository<ItemImageEntity, Long> {

    List<ItemImageEntity> findByItemIdOrderByDisplayOrderAsc(Long itemId);

    long countByItemId(Long itemId);

    Optional<ItemImageEntity> findByItemIdAndIsThumbnailTrue(Long itemId);

    void deleteByItemId(Long itemId);
}