package com.rajat.rent_anything.item.api;

import com.rajat.rent_anything.common.model.ApiResponse;
import com.rajat.rent_anything.item.application.ItemService;
import com.rajat.rent_anything.item.application.commands.CreateItemCommand;
import com.rajat.rent_anything.item.dto.ItemSearchResponseDto;
import com.rajat.rent_anything.security.CustomUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/items")
public class ItemController {

    private final ItemService itemService;

    public ItemController(ItemService itemService) {
        this.itemService = itemService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createItem(@RequestBody CreateItemCommand command, @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("User {} is creating an item with details: {}", userDetails.getUsername(), command);
        Long ownerId = userDetails.getDomainUser().getId();
        Long itemId = itemService.createItem(command, ownerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(itemId));
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<Void>> activateItem(@PathVariable("id") Long id, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getDomainUser().getId();
        itemService.activateItem(id, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivateItem(@PathVariable("id") Long id, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getDomainUser().getId();
        itemService.deactivateItem(id, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PutMapping("/{id}/updatePrice")
    public ResponseEntity<ApiResponse<Void>> price(@PathVariable("id") Long id, @RequestParam("price") Double price, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getDomainUser().getId();
        itemService.updatePrice(id, price, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }
    @PutMapping("/{id}/updateAvailability")
    public ResponseEntity<ApiResponse<Void>> availability(@PathVariable("id") Long id, @RequestParam("from") LocalDate from, @RequestParam("to") LocalDate to, @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = userDetails.getDomainUser().getId();
        itemService.updateAvailability(id, from, to, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ItemSearchResponseDto>>> search(
            @RequestParam("lat") double lat,
            @RequestParam("lon") double lon,
            @RequestParam("radiusKm") double radius,
            @RequestParam("startDate") LocalDate startDate,
            @RequestParam("endDate") LocalDate endDate,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset
    ) {
        List<ItemSearchResponseDto> result = itemService.searchAvailableItemsWithKeywordAndWithinGivenLocation(
                lat,
                lon,
                radius,
                startDate,
                endDate,
                keyword,
                limit,
                offset
        );
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}