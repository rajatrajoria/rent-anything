package com.rajat.rent_anything.item.api;

import com.rajat.rent_anything.common.model.ApiResponse;
import com.rajat.rent_anything.item.application.ItemService;
import com.rajat.rent_anything.item.application.commands.CreateItemCommand;
import com.rajat.rent_anything.item.dto.ItemDetailsResponseDto;
import com.rajat.rent_anything.item.dto.ItemSearchResponseDto;
import com.rajat.rent_anything.security.CustomUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller responsible for item management and discovery.
 * <p>
 * Responsibilities:
 * - Create rental listings
 * - Activate and deactivate listings
 * - Update item pricing
 * - Update item availability
 * - Search for available items
 * <p>
 * Most endpoints require an authenticated user and operate on
 * items owned by that user. The search endpoint is publicly
 * accessible and allows users to discover available items
 * within a specified location and date range.
 */
@Slf4j
@RestController
@RequestMapping("/items")
public class ItemController {

    /**
     * Service responsible for item-related business operations.
     */
    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    /**
     * Creates a new item listing.
     * <p>
     * The authenticated user becomes the owner of the item.
     * <p>
     * Workflow:
     * 1. Extract authenticated user.
     * 2. Create item listing.
     * 3. Associate item with owner.
     * 4. Return generated item id.
     *
     * @param command     item creation request
     * @param userDetails authenticated user
     * @return newly created item id
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createItem(@RequestBody CreateItemCommand command, @AuthenticationPrincipal CustomUserDetails userDetails) {

        log.info("User {} is creating an item with details: {}", userDetails.getUsername(), command);

        Long ownerId = userDetails.getDomainUser().getId();

        Long itemId = itemService.createItem(command, ownerId);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(itemId));
    }

    /**
     * Activates an item listing.
     * <p>
     * Activated items become available for discovery
     * and rental operations.
     * <p>
     * Only the item owner can perform this action.
     *
     * @param id          item identifier
     * @param userDetails authenticated user
     * @return success response
     */
    @PutMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activateItem(@PathVariable("id") Long id, @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getDomainUser().getId();

        itemService.activateItem(id, userId);

        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * Deactivates an item listing.
     * <p>
     * Deactivated items are hidden from search results
     * and cannot participate in new rental requests.
     * <p>
     * Only the item owner can perform this action.
     *
     * @param id          item identifier
     * @param userDetails authenticated user
     * @return success response
     */
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateItem(@PathVariable("id") Long id, @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getDomainUser().getId();

        itemService.deactivateItem(id, userId);

        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * Updates the rental price of an item.
     * <p>
     * Only the item owner can modify pricing.
     *
     * @param id          item identifier
     * @param price       new rental price
     * @param userDetails authenticated user
     * @return success response
     */
    @PutMapping("/{id}/updatePrice")
    public ResponseEntity<ApiResponse<Void>> price(@PathVariable("id") Long id, @RequestParam("price") Double price, @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getDomainUser().getId();

        itemService.updatePrice(id, price, userId);

        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * Updates the availability window of an item.
     * <p>
     * Availability determines the date range during which
     * the item can be rented.
     * <p>
     * Only the item owner can modify availability.
     *
     * @param id          item identifier
     * @param from        availability start date
     * @param to          availability end date
     * @param userDetails authenticated user
     * @return success response
     */
    @PutMapping("/{id}/updateAvailability")
    public ResponseEntity<ApiResponse<Void>> availability(@PathVariable("id") Long id, @RequestParam("from") LocalDate from, @RequestParam("to") LocalDate to, @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails.getDomainUser().getId();

        itemService.updateAvailability(id, from, to, userId);

        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * Searches for available items within a specified geographic area.
     * <p>
     * Search Criteria:
     * - Latitude and longitude
     * - Search radius
     * - Availability date range
     * - Optional keyword filter
     * <p>
     * Results are returned using pagination controls
     * through limit and offset parameters.
     * <p>
     * Typical Use Cases:
     * - Find items near a user's location
     * - Search for specific item types
     * - Discover available items for desired rental dates
     *
     * @param lat       search latitude
     * @param lon       search longitude
     * @param radius    search radius in kilometers
     * @param startDate desired rental start date
     * @param endDate   desired rental end date
     * @param keyword   optional keyword filter
     * @param limit     maximum results to return
     * @param offset    pagination offset
     * @return matching available items
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ItemSearchResponseDto>>> search(@RequestParam("lat") double lat, @RequestParam("lon") double lon, @RequestParam("radiusKm") double radius, @RequestParam("startDate") LocalDate startDate, @RequestParam("endDate") LocalDate endDate, @RequestParam(value = "keyword", required = false) String keyword, @RequestParam(value = "limit", defaultValue = "10") int limit, @RequestParam(value = "offset", defaultValue = "0") int offset) {
        List<ItemSearchResponseDto> result = itemService.searchAvailableItemsWithKeywordAndWithinGivenLocation(lat, lon, radius, startDate, endDate, keyword, limit, offset);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{itemId}")
    public ResponseEntity<ApiResponse<ItemDetailsResponseDto>> getItemDetails(@PathVariable Long itemId) {
        return ResponseEntity.ok(ApiResponse.success(itemService.getItemDetails(itemId)));
    }
}