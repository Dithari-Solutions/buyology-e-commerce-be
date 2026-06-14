package com.buyology.ecommerce.product.service;

import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.domain.GlobalSpecGroup;
import com.buyology.ecommerce.product.domain.GlobalSpecGroupTranslation;
import com.buyology.ecommerce.product.domain.GlobalSpecOption;
import com.buyology.ecommerce.product.domain.GlobalSpecOptionTranslation;
import com.buyology.ecommerce.product.dto.CreateGlobalSpecGroupRequest;
import com.buyology.ecommerce.product.dto.GlobalSpecGroupResponse;
import com.buyology.ecommerce.product.dto.UpdateSpecGroupRequest;
import com.buyology.ecommerce.product.repository.GlobalSpecGroupRepository;
import com.buyology.ecommerce.product.repository.GlobalSpecGroupTranslationRepository;
import com.buyology.ecommerce.product.repository.GlobalSpecOptionRepository;
import com.buyology.ecommerce.product.repository.GlobalSpecOptionTranslationRepository;
import com.buyology.ecommerce.product.repository.ProductSpecGroupRepository;
import com.buyology.ecommerce.product.repository.ProductSpecOptionRepository;
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
    private final ProductSpecGroupRepository productSpecGroupRepository;
    private final ProductSpecOptionRepository productSpecOptionRepository;

    public GlobalSpecService(
            GlobalSpecGroupRepository groupRepository,
            GlobalSpecGroupTranslationRepository groupTranslationRepository,
            GlobalSpecOptionRepository optionRepository,
            GlobalSpecOptionTranslationRepository optionTranslationRepository,
            ProductSpecGroupRepository productSpecGroupRepository,
            ProductSpecOptionRepository productSpecOptionRepository) {
        this.groupRepository = groupRepository;
        this.groupTranslationRepository = groupTranslationRepository;
        this.optionRepository = optionRepository;
        this.optionTranslationRepository = optionTranslationRepository;
        this.productSpecGroupRepository = productSpecGroupRepository;
        this.productSpecOptionRepository = productSpecOptionRepository;
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
            List<CreateGlobalSpecGroupRequest.OptionRequest> reqOptions = request.getOptions();
            List<GlobalSpecGroupResponse.OptionDto> built = new java.util.ArrayList<>();
            for (int i = 0; i < reqOptions.size(); i++) {
                CreateGlobalSpecGroupRequest.OptionRequest o = reqOptions.get(i);
                int order = o.getDisplayOrder() != null ? o.getDisplayOrder() : i;
                built.add(saveOption(group, o, order));
            }
            optionDtos = built;
        }

        return ApiResponse.created(toGroupResponse(group, groupTranslations, optionDtos),
                "Global spec group created successfully");
    }

    public ResponseEntity<ApiResponse<List<GlobalSpecGroupResponse>>> getAllGroups() {
        List<GlobalSpecGroupResponse> groups = groupRepository.findAll().stream()
                .map(g -> {
                    List<GlobalSpecGroupTranslation> groupTr = groupTranslationRepository.findAllByGroup_Id(g.getId());
                    List<GlobalSpecGroupResponse.OptionDto> opts = optionRepository
                            .findByGroupIdOrderByDisplayOrderAscCreatedAtAsc(g.getId()).stream()
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
        int order = request.getDisplayOrder() != null
                ? request.getDisplayOrder()
                : optionRepository.findMaxDisplayOrder(groupId) + 1;
        GlobalSpecGroupResponse.OptionDto dto = saveOption(group, request, order);
        return ApiResponse.created(dto, "Option added successfully");
    }

    /**
     * Reorders the options of a group so their display order matches the order
     * of the supplied id list (index 0 = first). Ids must all belong to the group.
     */
    @Transactional
    public ResponseEntity<ApiResponse<List<GlobalSpecGroupResponse.OptionDto>>> reorderOptions(
            UUID groupId, List<UUID> orderedOptionIds) {
        if (!groupRepository.existsById(groupId)) {
            throw new IllegalArgumentException("Global spec group not found: " + groupId);
        }
        List<GlobalSpecOption> options = optionRepository
                .findByGroupIdOrderByDisplayOrderAscCreatedAtAsc(groupId);
        java.util.Map<UUID, GlobalSpecOption> byId = new java.util.HashMap<>();
        for (GlobalSpecOption o : options) {
            byId.put(o.getId(), o);
        }

        int index = 0;
        for (UUID optionId : orderedOptionIds) {
            GlobalSpecOption option = byId.get(optionId);
            if (option == null) {
                throw new IllegalArgumentException(
                        "Option " + optionId + " does not belong to group " + groupId);
            }
            option.setDisplayOrder(index++);
        }
        // Any option not referenced in the request keeps its relative order after the listed ones.
        for (GlobalSpecOption o : options) {
            if (!orderedOptionIds.contains(o.getId())) {
                o.setDisplayOrder(index++);
            }
        }
        optionRepository.saveAll(options);

        List<GlobalSpecGroupResponse.OptionDto> dtos = optionRepository
                .findByGroupIdOrderByDisplayOrderAscCreatedAtAsc(groupId).stream()
                .map(o -> toOptionDto(o, optionTranslationRepository.findAllByOption_Id(o.getId())))
                .toList();
        return ApiResponse.success(dtos, "Options reordered successfully");
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteOption(UUID optionId) {
        GlobalSpecOption option = optionRepository.findById(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Global spec option not found: " + optionId));
        productSpecOptionRepository.detachByGlobalOption(option.getId());
        optionTranslationRepository.deleteAll(
                optionTranslationRepository.findAllByOption_Id(option.getId()));
        optionRepository.delete(option);
        return ApiResponse.success(null, "Option deleted successfully");
    }

    /** Edit a group's display names (the code is immutable). */
    @Transactional
    public ResponseEntity<ApiResponse<GlobalSpecGroupResponse>> updateGroup(
            UUID groupId, UpdateSpecGroupRequest request) {
        GlobalSpecGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Global spec group not found: " + groupId));
        upsertGroupTranslation(group, "AZ", request.getNameAz());
        upsertGroupTranslation(group, "EN", request.getNameEn());
        upsertGroupTranslation(group, "AR", request.getNameAr());

        List<GlobalSpecGroupTranslation> groupTr = groupTranslationRepository.findAllByGroup_Id(groupId);
        List<GlobalSpecGroupResponse.OptionDto> opts = optionRepository
                .findByGroupIdOrderByDisplayOrderAscCreatedAtAsc(groupId).stream()
                .map(o -> toOptionDto(o, optionTranslationRepository.findAllByOption_Id(o.getId())))
                .toList();
        return ApiResponse.success(toGroupResponse(group, groupTr, opts), "Global spec group updated");
    }

    /** Delete a whole group (its options + translations). Products keep their copied specs. */
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteGroup(UUID groupId) {
        GlobalSpecGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Global spec group not found: " + groupId));

        // Null product references first so the FK constraints don't block the delete.
        productSpecOptionRepository.detachByGlobalGroup(groupId);
        productSpecGroupRepository.detachByGlobalGroup(groupId);

        List<GlobalSpecOption> options = optionRepository
                .findByGroupIdOrderByDisplayOrderAscCreatedAtAsc(groupId);
        for (GlobalSpecOption o : options) {
            optionTranslationRepository.deleteAll(
                    optionTranslationRepository.findAllByOption_Id(o.getId()));
        }
        optionRepository.deleteAll(options);
        groupTranslationRepository.deleteAll(groupTranslationRepository.findAllByGroup_Id(groupId));
        groupRepository.delete(group);
        return ApiResponse.success(null, "Global spec group deleted");
    }

    /** Edit an existing option's unit + value translations. */
    @Transactional
    public ResponseEntity<ApiResponse<GlobalSpecGroupResponse.OptionDto>> updateOption(
            UUID optionId, CreateGlobalSpecGroupRequest.OptionRequest request) {
        GlobalSpecOption option = optionRepository.findById(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Global spec option not found: " + optionId));
        option.setUnit(request.getUnit());
        if (request.getDisplayOrder() != null) {
            option.setDisplayOrder(request.getDisplayOrder());
        }
        optionRepository.save(option);
        upsertOptionTranslation(option, Language.AZ, request.getValueAz());
        upsertOptionTranslation(option, Language.EN, request.getValueEn());
        upsertOptionTranslation(option, Language.AR, request.getValueAr());

        List<GlobalSpecOptionTranslation> tr = optionTranslationRepository.findAllByOption_Id(optionId);
        return ApiResponse.success(toOptionDto(option, tr), "Option updated successfully");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void upsertGroupTranslation(GlobalSpecGroup group, String language, String name) {
        GlobalSpecGroupTranslation t = groupTranslationRepository
                .findByGroup_IdAndLanguageIgnoreCase(group.getId(), language)
                .orElseGet(() -> new GlobalSpecGroupTranslation(group, language, name));
        t.setName(name);
        groupTranslationRepository.save(t);
    }

    private void upsertOptionTranslation(GlobalSpecOption option, Language language, String value) {
        GlobalSpecOptionTranslation t = optionTranslationRepository
                .findByOption_IdAndLanguage(option.getId(), language)
                .orElseGet(() -> new GlobalSpecOptionTranslation(option, language, value));
        t.setValue(value);
        optionTranslationRepository.save(t);
    }

    private GlobalSpecGroupResponse.OptionDto saveOption(
            GlobalSpecGroup group, CreateGlobalSpecGroupRequest.OptionRequest req, int displayOrder) {
        GlobalSpecOption option = optionRepository.save(
                new GlobalSpecOption(group, req.getUnit(), displayOrder));
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
        return new GlobalSpecGroupResponse.OptionDto(
                option.getId(), option.getUnit(), option.getDisplayOrder(), trDtos);
    }
}
