package com.buyology.ecommerce.product.controller;

import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import com.buyology.ecommerce.product.repository.ProductMediaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * A permanent address for a product image.
 *
 * <p>The URLs the product API hands out are presigned and expire within hours, which is fine for a
 * page that re-fetches them but useless in a file someone keeps — a store's product export, say.
 * This resolves a media id to a freshly signed URL on every request and redirects to it, so a link
 * written into an export keeps working for as long as the image exists.
 *
 * <p>Public like the rest of {@code GET /api/product/**}: the images are the ones the storefront
 * already shows, and a media id is an unguessable UUID. Media of a deleted product is not served.
 */
@RestController
@RequestMapping("/api/product/media")
@Tag(name = "Product", description = "Public APIs for browsing active products")
public class ProductMediaController {

    // Comfortably inside the hours a cached product URL still has left to run.
    private static final CacheControl REDIRECT_CACHE = CacheControl.maxAge(30, TimeUnit.MINUTES).cachePublic();

    private final ProductMediaRepository mediaRepository;
    private final ContaboObjectService contaboObjectService;

    public ProductMediaController(ProductMediaRepository mediaRepository,
                                  ContaboObjectService contaboObjectService) {
        this.mediaRepository = mediaRepository;
        this.contaboObjectService = contaboObjectService;
    }

    @Operation(summary = "Redirect to a product image",
            description = "Stable link to a product image or video: redirects to a freshly signed URL.")
    @GetMapping("/{mediaId}")
    public ResponseEntity<Void> redirectToMedia(@PathVariable UUID mediaId) {
        return mediaRepository.findUrlOfLiveProductMedia(mediaId)
                .map(contaboObjectService::getPresignedUrl)
                .<ResponseEntity<Void>>map(url -> ResponseEntity.status(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, url)
                        .cacheControl(REDIRECT_CACHE)
                        .build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
