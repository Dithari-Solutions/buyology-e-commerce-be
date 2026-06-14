package com.buyology.ecommerce.product.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.domain.SpecCode;
import com.buyology.ecommerce.product.dto.SpecCodeRequest;
import com.buyology.ecommerce.product.dto.SpecCodeResponse;
import com.buyology.ecommerce.product.repository.SpecCodeRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SpecCodeService {

    private final SpecCodeRepository repo;

    public SpecCodeService(SpecCodeRepository repo) {
        this.repo = repo;
    }

    public ResponseEntity<ApiResponse<List<SpecCodeResponse>>> list() {
        List<SpecCodeResponse> codes = repo.findAllByOrderByDisplayOrderAscCodeAsc().stream()
                .map(SpecCodeResponse::from)
                .collect(Collectors.toList());
        return ApiResponse.success(codes, "Spec codes fetched");
    }

    @Transactional
    public ResponseEntity<ApiResponse<SpecCodeResponse>> create(SpecCodeRequest req) {
        String code = normalize(req.getCode());
        if (code.isBlank()) {
            throw new IllegalArgumentException("Spec code is required");
        }
        if (repo.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Spec code '" + code + "' already exists");
        }
        SpecCode c = new SpecCode();
        c.setCode(code);
        c.setLabelEn(req.getLabelEn());
        c.setLabelAz(req.getLabelAz());
        c.setLabelAr(req.getLabelAr());
        c.setFilterable(req.isFilterable());
        c.setDisplayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder() : nextDisplayOrder());
        return ApiResponse.created(SpecCodeResponse.from(repo.save(c)), "Spec code created");
    }

    /** Labels / filterable / order are editable; the code itself is immutable once created. */
    @Transactional
    public ResponseEntity<ApiResponse<SpecCodeResponse>> update(UUID id, SpecCodeRequest req) {
        SpecCode c = repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Spec code not found: " + id));
        c.setLabelEn(req.getLabelEn());
        c.setLabelAz(req.getLabelAz());
        c.setLabelAr(req.getLabelAr());
        c.setFilterable(req.isFilterable());
        if (req.getDisplayOrder() != null) {
            c.setDisplayOrder(req.getDisplayOrder());
        }
        return ApiResponse.success(SpecCodeResponse.from(repo.save(c)), "Spec code updated");
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> delete(UUID id) {
        if (!repo.existsById(id)) {
            throw new NoSuchElementException("Spec code not found: " + id);
        }
        repo.deleteById(id);
        return ApiResponse.success(null, "Spec code deleted");
    }

    private int nextDisplayOrder() {
        return repo.findAllByOrderByDisplayOrderAscCodeAsc().stream()
                .mapToInt(SpecCode::getDisplayOrder)
                .max()
                .orElse(-1) + 1;
    }

    private String normalize(String code) {
        if (code == null) return "";
        return code.trim().toLowerCase().replaceAll("\\s+", "_");
    }
}
