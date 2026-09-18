package com.buyology.ecommerce.giveaway.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.giveaway.dto.GiveawayEntryAdminResponse;
import com.buyology.ecommerce.giveaway.service.GiveawayEntriesWorkbook;
import com.buyology.ecommerce.giveaway.service.GiveawayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import com.buyology.ecommerce.giveaway.domain.GiveawayCampaign;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The entry list the team draws the winner from. Read-only on purpose: entries are the
 * customers' own submissions and nothing should be able to edit them after the fact.
 * SUPERADMIN-only — no permission code, because a winner list is not a desk's daily work.
 */
@RestController
@RequestMapping("/api/admin/giveaway")
public class AdminGiveawayController {

    private static final Logger log = LoggerFactory.getLogger(AdminGiveawayController.class);

    private final GiveawayService giveawayService;

    public AdminGiveawayController(GiveawayService giveawayService) {
        this.giveawayService = giveawayService;
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('giveaway:entry:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/entries")
    public ResponseEntity<ApiResponse<Page<GiveawayEntryAdminResponse>>> entries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(giveawayService.listAll(page, size), "Giveaway entries fetched");
    }

    /**
     * Every entry as an Excel workbook, for the draw. All of them in one file, where the list above
     * is paged — and the same data the list shows, so the same permission.
     */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('giveaway:entry:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/entries/export")
    public ResponseEntity<byte[]> exportEntries(@AuthenticationPrincipal UUID adminId) {
        byte[] file = giveawayService.exportEntries();
        log.info("[GIVEAWAY] entries exported by {} ({} bytes)", adminId, file.length);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(GiveawayEntriesWorkbook.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("giveaway-entries.xlsx").build().toString())
                .body(file);
    }

    /** Current open/closed state, so the dashboard shows the truth before anyone touches it. */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('giveaway:entry:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/campaign")
    public ResponseEntity<ApiResponse<GiveawayCampaign>> campaign() {
        return ApiResponse.success(giveawayService.campaign(), "Giveaway campaign fetched");
    }

    /**
     * Opens or closes the draw.
     *
     * <p>Closing removes every entry surface from the storefront and the app and makes the entry
     * endpoint refuse — but it does not touch the entries themselves. Those are the draw; the point
     * of closing is to stop taking more, not to throw away the ones you have.
     */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('giveaway:campaign:update') or @rbacPolicy.legacyAdmin()")
    @PutMapping("/campaign")
    public ResponseEntity<ApiResponse<GiveawayCampaign>> setOpen(
            @AuthenticationPrincipal UUID adminId,
            @RequestParam boolean open) {
        return ApiResponse.success(giveawayService.setOpen(open, adminId),
                open ? "Giveaway reopened" : "Giveaway closed");
    }
}
