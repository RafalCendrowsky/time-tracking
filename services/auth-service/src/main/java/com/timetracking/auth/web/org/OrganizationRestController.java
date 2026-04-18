package com.timetracking.auth.web.org;

import com.timetracking.auth.service.OrganizationService;
import com.timetracking.auth.web.org.dto.OrganizationDetailResponse;
import com.timetracking.auth.web.org.dto.OrganizationRequest;
import com.timetracking.auth.web.org.dto.OrganizationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class OrganizationRestController {

    private final OrganizationService organizationService;

    @GetMapping
    public List<OrganizationResponse> findAll() {
        return organizationService.findAll().stream().map(OrganizationResponse::from).toList();
    }

    @GetMapping("/{id}")
    public OrganizationDetailResponse findById(@PathVariable String id) {
        return OrganizationDetailResponse.from(organizationService.findById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationDetailResponse create(@Valid @RequestBody OrganizationRequest request) {
        return OrganizationDetailResponse.from(organizationService.create(request));
    }

    @PutMapping("/{id}")
    public OrganizationDetailResponse update(@PathVariable String id, @Valid @RequestBody OrganizationRequest request) {
        return OrganizationDetailResponse.from(organizationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        organizationService.delete(id);
    }
}

