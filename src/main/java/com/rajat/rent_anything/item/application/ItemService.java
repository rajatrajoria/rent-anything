package com.rajat.rent_anything.item.application;

import com.rajat.rent_anything.booking.domain.BookingStatus;
import com.rajat.rent_anything.booking.infrastructure.BookingEntity;
import com.rajat.rent_anything.booking.infrastructure.BookingRepository;
import com.rajat.rent_anything.common.enums.ErrorCode;
import com.rajat.rent_anything.item.application.commands.CreateItemCommand;
import com.rajat.rent_anything.item.domain.Item;
import com.rajat.rent_anything.item.domain.ItemStatus;
import com.rajat.rent_anything.item.dto.ItemDetailsResponseDto;
import com.rajat.rent_anything.item.dto.ItemImageResponseDto;
import com.rajat.rent_anything.item.dto.ItemSearchResponseDto;
import com.rajat.rent_anything.item.exceptions.IllegalItemModificationException;
import com.rajat.rent_anything.item.exceptions.InvalidItemException;
import com.rajat.rent_anything.item.exceptions.ItemNotFoundException;
import com.rajat.rent_anything.item.infrastructure.ItemEntity;
import com.rajat.rent_anything.item.infrastructure.ItemMapper;
import com.rajat.rent_anything.item.infrastructure.ItemRepository;
import com.rajat.rent_anything.user.application.TrustGateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service responsible for item lifecycle management and item discovery.
 * <p>
 * Responsibilities:
 * - Create item listings
 * - Activate and deactivate listings
 * - Update item pricing
 * - Update item availability
 * - Search available items
 * <p>
 * Business Rules:
 * - Only trusted users can create or manage items.
 * - Only item owners can modify their listings.
 * - Availability cannot be reduced if it conflicts with active bookings.
 * - Items must have valid pricing.
 * <p>
 * This service acts as the primary entry point for all
 * item-related business operations.
 */
@Service
@Slf4j
public class ItemService {

    /**
     * Repository responsible for item persistence operations.
     */
    private final ItemRepository itemRepository;

    /**
     * Repository used for booking lookups when validating
     * item availability changes.
     */
    private final BookingRepository bookingRepository;

    /**
     * Service responsible for enforcing trust-based access rules.
     * <p>
     * Only trusted users are allowed to perform item-related
     * marketplace operations.
     */
    private final TrustGateService trustGateService;

    private final ItemImageService itemImageService;

    public ItemService(ItemRepository itemRepository, BookingRepository bookingRepository, TrustGateService trustGateService, ItemImageService itemImageService) {
        this.itemRepository = itemRepository;
        this.bookingRepository = bookingRepository;
        this.trustGateService = trustGateService;
        this.itemImageService = itemImageService;
    }

    /**
     * Creates a new item listing.
     * <p>
     * Workflow:
     * 1. Verify user is trusted.
     * 2. Create domain item.
     * 3. Persist item.
     * 4. Return generated item id.
     *
     * @param command item creation request
     * @param ownerId owner creating the item
     * @return newly created item id
     */
    @Transactional
    public Long createItem(CreateItemCommand command, Long ownerId) {
        trustGateService.ensureUserIsTrusted(ownerId);
        Item item = Item.create(ownerId, command.categoryId(), command.title(), command.description(), command.pricePerDay(), command.depositAmount(), command.availableFrom(), command.availableTo(), command.longitude(), command.latitude());
        ItemEntity entity = ItemMapper.toEntity(item);
        ItemEntity saved = itemRepository.save(entity);
        return saved.getId();
    }

    /**
     * Activates an item listing.
     * <p>
     * Activated items become discoverable and available
     * for future rental requests.
     * <p>
     * Business Rules:
     * - User must be trusted.
     * - User must own the item.
     *
     * @param itemId item identifier
     * @param userId requesting user
     */
    @Transactional
    public void activateItem(Long itemId, Long userId) {
        trustGateService.ensureUserIsTrusted(userId);
        ItemEntity entity = itemRepository.findById(itemId).orElseThrow(() -> new ItemNotFoundException("Item not found"));
        validateOwnership(entity.getOwnerId(), userId);
        long imageCount = itemImageService.getImageCount(itemId);
        if (imageCount < 2) {
            throw new IllegalStateException("At least 2 images are required before activating an item");
        }
        Item item = ItemMapper.toDomain(entity);
        item.setStatus(ItemStatus.ACTIVE);
        item.setUpdatedAt(LocalDateTime.now());
        ItemEntity updatedEntity = ItemMapper.toEntity(item);
        itemRepository.save(updatedEntity);
        log.info("Activated item with id {} by user {}", itemId, userId);
    }

    /**
     * Deactivates an item listing.
     * <p>
     * Deactivated items will no longer appear in search results
     * and cannot receive new booking requests.
     * <p>
     * Business Rules:
     * - User must be trusted.
     * - User must own the item.
     *
     * @param itemId item identifier
     * @param userId requesting user
     */
    @Transactional
    public void deactivateItem(Long itemId, Long userId) {
        trustGateService.ensureUserIsTrusted(userId);
        ItemEntity entity = itemRepository.findById(itemId).orElseThrow(() -> new ItemNotFoundException("Item not found"));
        validateOwnership(entity.getOwnerId(), userId);
        Item item = ItemMapper.toDomain(entity);
        item.setStatus(ItemStatus.INACTIVE);
        ItemEntity updatedEntity = ItemMapper.toEntity(item);
        itemRepository.save(updatedEntity);
        log.info("Deactivated item with id {} by user {}", itemId, userId);
    }

    /**
     * Updates the rental price of an item.
     * <p>
     * Business Rules:
     * - User must be trusted.
     * - User must own the item.
     * - Price must be greater than zero.
     *
     * @param itemId   item identifier
     * @param newPrice new rental price
     * @param userId   requesting user
     */
    public void updatePrice(Long itemId, double newPrice, Long userId) {
        trustGateService.ensureUserIsTrusted(userId);
        ItemEntity entity = itemRepository.findById(itemId).orElseThrow(() -> new ItemNotFoundException("Item not found"));
        validateOwnership(entity.getOwnerId(), userId);
        if (newPrice <= 0) {
            throw new InvalidItemException(ErrorCode.INVALID_ITEM_PRICING, "Price must be positive");
        }
        Item item = ItemMapper.toDomain(entity);
        item.setPricePerDay(newPrice);
        item.setUpdatedAt(LocalDateTime.now());
        ItemEntity updatedEntity = ItemMapper.toEntity(item);
        itemRepository.save(updatedEntity);
        log.info("Updated price for item with id {} by user {}. New price: {}", itemId, userId, newPrice);
    }

    /**
     * Updates an item's availability window.
     * <p>
     * Business Rules:
     * - User must be trusted.
     * - User must own the item.
     * - Availability cannot conflict with active bookings.
     * <p>
     * To prevent breaking existing rentals, the system checks
     * all active bookings before allowing the availability window
     * to be reduced.
     *
     * @param itemId item identifier
     * @param from   new availability start date
     * @param to     new availability end date
     * @param userId requesting user
     */
    @Transactional
    public void updateAvailability(Long itemId, LocalDate from, LocalDate to, Long userId) {
        trustGateService.ensureUserIsTrusted(userId);
        ItemEntity item = itemRepository.findById(itemId).orElseThrow(() -> new ItemNotFoundException("Item not found"));
        validateOwnership(item.getOwnerId(), userId);
        List<BookingEntity> bookings = bookingRepository.findByItemIdAndStatusIn(itemId, List.of(BookingStatus.PENDING, BookingStatus.CONFIRMED));
        boolean conflict = bookings.stream().anyMatch(b -> b.getStartDate().isBefore(from) || b.getEndDate().isAfter(to));
        if (conflict) {
            throw new InvalidItemException(ErrorCode.INVALID_AVAILABILITY_WINDOW, "Cannot shrink availability. Active bookings exist.");
        }
        item.setAvailableFrom(from);
        item.setAvailableTo(to);
        itemRepository.save(item);
        log.info("Updated availability for item with id {} by user {}. New availability: {} to {}", itemId, userId, from, to);
    }

    /**
     * Searches for available items within a geographic radius.
     * <p>
     * Search Filters:
     * - Geographic location
     * - Search radius
     * - Availability dates
     * - Optional keyword
     * <p>
     * Results are paginated using limit and offset parameters.
     * <p>
     * Distance is returned in kilometers.
     *
     * @param latitude  search latitude
     * @param longitude search longitude
     * @param radiusKm  search radius in kilometers
     * @param startDate desired rental start date
     * @param endDate   desired rental end date
     * @param keyword   optional keyword filter
     * @param limit     maximum results to return
     * @param offset    pagination offset
     * @return matching available items
     */
    @Transactional(readOnly = true)
    public List<ItemSearchResponseDto> searchAvailableItemsWithKeywordAndWithinGivenLocation(double latitude, double longitude, double radiusKm, LocalDate startDate, LocalDate endDate, String keyword, int limit, int offset) {
        double radiusMeters = radiusKm * 1000;
        return itemRepository.searchAvailableItemsWithinRadiusAndWithKeywords(latitude, longitude, radiusMeters, startDate, endDate, keyword, limit, offset).stream().map(row -> {
            String thumbnailUrl = null;
            var thumbnail = itemImageService.getThumbnail(row.getItemId());
            if (thumbnail != null) {
                thumbnailUrl = thumbnail.imageUrl();
            }
            return new ItemSearchResponseDto(row.getItemId(), row.getOwnerId(), row.getTitle(), row.getDescription(), row.getPricePerDay(), row.getDistance() / 1000, row.getTextScore(), thumbnailUrl);
        }).toList();
    }

    @Transactional(readOnly = true)
    public ItemDetailsResponseDto getItemDetails(Long itemId) {
        ItemEntity item = itemRepository.findById(itemId).orElseThrow(() -> new ItemNotFoundException("Item not found"));
        ItemImageResponseDto thumbnail = itemImageService.getThumbnail(itemId);
        List<ItemImageResponseDto> images = itemImageService.getItemImages(itemId);
        return new ItemDetailsResponseDto(item.getId(), item.getOwnerId(), item.getCategoryId(), item.getTitle(), item.getDescription(), item.getPricePerDay(), item.getDepositAmount(), item.getStatus().name(), item.getAvailableFrom(), item.getAvailableTo(), thumbnail != null ? thumbnail.imageUrl() : null, images);
    }

    private void validateOwnership(Long ownerId, Long userId) {
        if (!ownerId.equals(userId)) {
            throw new IllegalItemModificationException("You are not allowed to modify this item");
        }
    }
}
