package com.buyology.ecommerce.product.service;

import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.domain.GlobalSpecGroup;
import com.buyology.ecommerce.product.domain.GlobalSpecGroupTranslation;
import com.buyology.ecommerce.product.domain.GlobalSpecOption;
import com.buyology.ecommerce.product.domain.GlobalSpecOptionTranslation;
import com.buyology.ecommerce.product.dto.CreateGlobalSpecGroupRequest;
import com.buyology.ecommerce.product.dto.GlobalSpecGroupResponse;
import com.buyology.ecommerce.product.repository.GlobalSpecGroupRepository;
import com.buyology.ecommerce.product.repository.GlobalSpecGroupTranslationRepository;
import com.buyology.ecommerce.product.repository.GlobalSpecOptionRepository;
import com.buyology.ecommerce.product.repository.GlobalSpecOptionTranslationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GlobalSpecService {

    private final GlobalSpecGroupRepository groupRepository;
    private final GlobalSpecGroupTranslationRepository groupTranslationRepository;
    private final GlobalSpecOptionRepository optionRepository;
    private final GlobalSpecOptionTranslationRepository optionTranslationRepository;

    public GlobalSpecService(
            GlobalSpecGroupRepository groupRepository,
            GlobalSpecGroupTranslationRepository groupTranslationRepository,
            GlobalSpecOptionRepository optionRepository,
            GlobalSpecOptionTranslationRepository optionTranslationRepository) {
        this.groupRepository = groupRepository;
        this.groupTranslationRepository = groupTranslationRepository;
        this.optionRepository = optionRepository;
        this.optionTranslationRepository = optionTranslationRepository;
    }

    @Transactional
    public ResponseEntity<ApiResponse<GlobalSpecGroupResponse>> createGroup(CreateGlobalSpecGroupRequest request) {
        if (groupRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException(
                    "Global spec group with code '" + request.getCode() + "' already exists");
        }

        GlobalSpecGroup group = groupRepository.save(new GlobalSpecGroup(request.getCode()));

        List<GlobalSpecGroupTranslation> groupTranslations = groupTranslationRepository.saveAll(List.of(
                new GlobalSpecGroupTranslation(group, "AZ", request.getNameAz()),
                new GlobalSpecGroupTranslation(group, "EN", request.getNameEn()),
                new GlobalSpecGroupTranslation(group, "AR", request.getNameAr())));

        List<GlobalSpecGroupResponse.OptionDto> optionDtos = List.of();
        if (request.getOptions() != null && !request.getOptions().isEmpty()) {
            optionDtos = request.getOptions().stream()
                    .map(o -> saveOption(group, o))
                    .toList();
        }

        return ApiResponse.created(toGroupResponse(group, groupTranslations, optionDtos),
                "Global spec group created successfully");
    }

    public ResponseEntity<ApiResponse<List<GlobalSpecGroupResponse>>> getAllGroups() {
        List<GlobalSpecGroupResponse> groups = groupRepository.findAll().stream()
                .map(g -> {
                    List<GlobalSpecGroupTranslation> groupTr = groupTranslationRepository.findAllByGroup_Id(g.getId());
                    List<GlobalSpecGroupResponse.OptionDto> opts = optionRepository.findByGroupId(g.getId()).stream()
                            .map(o -> {
                                List<GlobalSpecOptionTranslation> optTr =
                                        optionTranslationRepository.findAllByOption_Id(o.getId());
                                return toOptionDto(o, optTr);
                            })
                            .toList();
                    return toGroupResponse(g, groupTr, opts);
                })
                .toList();
        return ApiResponse.success(groups, "Global spec groups fetched successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<GlobalSpecGroupResponse.OptionDto>> addOption(
            UUID groupId, CreateGlobalSpecGroupRequest.OptionRequest request) {
        GlobalSpecGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Global spec group not found: " + groupId));
        GlobalSpecGroupResponse.OptionDto dto = saveOption(group, request);
        return ApiResponse.created(dto, "Option added successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteOption(UUID optionId) {
        GlobalSpecOption option = optionRepository.findById(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Global spec option not found: " + optionId));
        optionTranslationRepository.deleteAll(
                optionTranslationRepository.findAllByOption_Id(option.getId()));
        optionRepository.delete(option);
        return ApiResponse.success(null, "Option deleted successfully");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private GlobalSpecGroupResponse.OptionDto saveOption(
            GlobalSpecGroup group, CreateGlobalSpecGroupRequest.OptionRequest req) {
        GlobalSpecOption option = optionRepository.save(new GlobalSpecOption(group, req.getUnit()));
        List<GlobalSpecOptionTranslation> translations = optionTranslationRepository.saveAll(List.of(
                new GlobalSpecOptionTranslation(option, Language.AZ, req.getValueAz()),
                new GlobalSpecOptionTranslation(option, Language.EN, req.getValueEn()),
                new GlobalSpecOptionTranslation(option, Language.AR, req.getValueAr())));
        return toOptionDto(option, translations);
    }

    private GlobalSpecGroupResponse toGroupResponse(
            GlobalSpecGroup group,
            List<GlobalSpecGroupTranslation> translations,
            List<GlobalSpecGroupResponse.OptionDto> options) {
        List<GlobalSpecGroupResponse.TranslationDto> trDtos = translations.stream()
                .map(t -> new GlobalSpecGroupResponse.TranslationDto(t.getLanguage(), t.getName()))
                .toList();
        return new GlobalSpecGroupResponse(group.getId(), group.getCode(), trDtos, options);
    }

    private GlobalSpecGroupResponse.OptionDto toOptionDto(
            GlobalSpecOption option, List<GlobalSpecOptionTranslation> translations) {
        List<GlobalSpecGroupResponse.OptionTranslationDto> trDtos = translations.stream()
                .map(t -> new GlobalSpecGroupResponse.OptionTranslationDto(t.getLanguage().name(), t.getValue()))
                .toList();
        return new GlobalSpecGroupResponse.OptionDto(option.getId(), option.getUnit(), trDtos);
    }
}
