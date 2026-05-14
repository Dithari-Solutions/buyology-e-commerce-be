package com.buyology.ecommerce.banner.service;

import com.buyology.ecommerce.banner.domain.Banner;
import com.buyology.ecommerce.banner.domain.BannerNotFoundException;
import com.buyology.ecommerce.banner.domain.BannerStatus;
import com.buyology.ecommerce.banner.domain.BannerTranslation;
import com.buyology.ecommerce.banner.dto.BannerAdminResponse;
import com.buyology.ecommerce.banner.dto.BannerResponse;
import com.buyology.ecommerce.banner.dto.BannerTranslationRequest;
import com.buyology.ecommerce.banner.dto.CreateBannerRequest;
import com.buyology.ecommerce.banner.dto.UpdateBannerRequest;
import com.buyology.ecommerce.banner.repository.BannerRepository;
import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.common.utils.FileValidationUtils;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class BannerService {

    private static final Pattern INTERNAL_PATH = Pattern.compile("^/[A-Za-z0-9\\-_/?=&%.#]*$");
    private static final Pattern EXTERNAL_URL = Pattern.compile("^https?://.+", Pattern.CASE_INSENSITIVE);

    private final BannerRepository bannerRepository;
    private final ContaboObjectService contaboObjectService;

    public BannerService(BannerRepository bannerRepository, ContaboObjectService contaboObjectService) {
        this.bannerRepository = bannerRepository;
        this.contaboObjectService = contaboObjectService;
    }

    @Transactional
    public Banner createBanner(CreateBannerRequest request, MultipartFile background, UUID createdBy) {
        FileValidationUtils.validateImage(background);
        validateButtonConfiguration(request.getButtonUrl(), request.getTranslation());

        Banner banner = new Banner(createdBy);
        banner.setButtonUrl(emptyToNull(request.getButtonUrl()));
        banner.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        if (request.getStatus() != null) {
            banner.setStatus(request.getStatus());
        }

        for (BannerTranslation t : buildTranslations(request.getTranslation())) {
            banner.addTranslation(t);
        }

        Banner saved = bannerRepository.save(banner);

        String key = uploadBackground(saved.getId(), background);
        saved.setBackgroundImageUrl(key);
        return bannerRepository.save(saved);
    }

    @Transactional
    public Banner updateBanner(UUID id, UpdateBannerRequest request, MultipartFile background) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new BannerNotFoundException(id));

        if (request.getTranslation() != null) {
            validateButtonConfiguration(
                    request.getButtonUrl() != null ? request.getButtonUrl() : banner.getButtonUrl(),
                    request.getTranslation());
            banner.clearTranslations();
            for (BannerTranslation t : buildTranslations(request.getTranslation())) {
                banner.addTranslation(t);
            }
        }
        if (request.getButtonUrl() != null) {
            banner.setButtonUrl(emptyToNull(request.getButtonUrl()));
        }
        if (request.getSortOrder() != null) {
            banner.setSortOrder(request.getSortOrder());
        }
        if (request.getStatus() != null) {
            banner.setStatus(request.getStatus());
        }

        if (background != null && !background.isEmpty()) {
            FileValidationUtils.validateImage(background);
            if (banner.getBackgroundImageUrl() != null) {
                contaboObjectService.deleteFile(banner.getBackgroundImageUrl());
            }
            banner.setBackgroundImageUrl(uploadBackground(banner.getId(), background));
        }

        return bannerRepository.save(banner);
    }

    @Transactional
    public void deleteBanner(UUID id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new BannerNotFoundException(id));
        contaboObjectService.deleteFolder("banners/" + id);
        bannerRepository.delete(banner);
    }

    @Transactional
    public void setStatus(UUID id, BannerStatus status) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new BannerNotFoundException(id));
        banner.setStatus(status);
        bannerRepository.save(banner);
    }

    @Transactional
    public void setSortOrder(UUID id, int sortOrder) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new BannerNotFoundException(id));
        banner.setSortOrder(sortOrder);
        bannerRepository.save(banner);
    }

    @Transactional(readOnly = true)
    public List<BannerResponse> listActiveForPublic(Language language) {
        return bannerRepository.findByStatusOrderBySortOrderAscCreatedAtDesc(BannerStatus.ACTIVE)
                .stream()
                .map(b -> toPublicResponse(b, language))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BannerAdminResponse> listForAdmin() {
        return bannerRepository.findAllByOrderBySortOrderAscCreatedAtDesc()
                .stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BannerAdminResponse getAdminById(UUID id) {
        Banner banner = bannerRepository.findById(id)
                .orElseThrow(() -> new BannerNotFoundException(id));
        return toAdminResponse(banner);
    }

    // ===== helpers =====

    private String uploadBackground(UUID bannerId, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String ext = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";
        String key = "banners/" + bannerId + "/background" + ext;
        return contaboObjectService.uploadFile(key, file);
    }

    private List<BannerTranslation> buildTranslations(BannerTranslationRequest tr) {
        BannerTranslation az = new BannerTranslation();
        az.setLanguage(Language.AZ);
        az.setText(emptyToNull(tr.getTextAz()));
        az.setButtonLabel(emptyToNull(tr.getButtonLabelAz()));

        BannerTranslation en = new BannerTranslation();
        en.setLanguage(Language.EN);
        en.setText(emptyToNull(tr.getTextEn()));
        en.setButtonLabel(emptyToNull(tr.getButtonLabelEn()));

        BannerTranslation ar = new BannerTranslation();
        ar.setLanguage(Language.AR);
        ar.setText(emptyToNull(tr.getTextAr()));
        ar.setButtonLabel(emptyToNull(tr.getButtonLabelAr()));

        return List.of(az, en, ar);
    }

    private void validateButtonConfiguration(String buttonUrl, BannerTranslationRequest tr) {
        String url = emptyToNull(buttonUrl);
        boolean anyLabel = tr != null && (
                hasText(tr.getButtonLabelAz())
                        || hasText(tr.getButtonLabelEn())
                        || hasText(tr.getButtonLabelAr()));

        if (url == null && anyLabel) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "buttonUrl is required when a button label is provided");
        }
        if (url != null && !anyLabel) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one button label is required when buttonUrl is set");
        }
        if (url != null && !INTERNAL_PATH.matcher(url).matches() && !EXTERNAL_URL.matcher(url).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "buttonUrl must be an internal path starting with '/' or an absolute http(s) URL");
        }
    }

    private BannerResponse toPublicResponse(Banner banner, Language language) {
        BannerResponse response = new BannerResponse();
        response.setId(banner.getId());
        response.setBackgroundImageUrl(contaboObjectService.getPresignedUrl(banner.getBackgroundImageUrl()));
        response.setButtonUrl(banner.getButtonUrl());
        response.setSortOrder(banner.getSortOrder());
        response.setStatus(banner.getStatus().name());
        response.setCreatedAt(banner.getCreatedAt());

        BannerTranslation tr = pickTranslation(banner, language);
        if (tr != null) {
            response.setText(tr.getText());
            response.setButtonLabel(tr.getButtonLabel());
        }
        return response;
    }

    private BannerAdminResponse toAdminResponse(Banner banner) {
        BannerAdminResponse response = new BannerAdminResponse();
        response.setId(banner.getId());
        response.setBackgroundImageUrl(contaboObjectService.getPresignedUrl(banner.getBackgroundImageUrl()));
        response.setButtonUrl(banner.getButtonUrl());
        response.setSortOrder(banner.getSortOrder());
        response.setStatus(banner.getStatus().name());
        response.setCreatedAt(banner.getCreatedAt());
        response.setUpdatedAt(banner.getUpdatedAt());

        BannerTranslationRequest tr = new BannerTranslationRequest();
        for (BannerTranslation t : banner.getTranslations()) {
            switch (t.getLanguage()) {
                case AZ -> { tr.setTextAz(t.getText()); tr.setButtonLabelAz(t.getButtonLabel()); }
                case EN -> { tr.setTextEn(t.getText()); tr.setButtonLabelEn(t.getButtonLabel()); }
                case AR -> { tr.setTextAr(t.getText()); tr.setButtonLabelAr(t.getButtonLabel()); }
            }
        }
        response.setTranslation(tr);
        return response;
    }

    private BannerTranslation pickTranslation(Banner banner, Language language) {
        BannerTranslation match = banner.getTranslations().stream()
                .filter(t -> t.getLanguage() == language)
                .findFirst()
                .orElse(null);
        if (match != null) return match;
        return banner.getTranslations().stream()
                .filter(t -> t.getLanguage() == Language.EN)
                .findFirst()
                .orElse(null);
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
