package gov.openpto.odp.service;

import gov.openpto.odp.dto.AdminUserResponse;
import gov.openpto.odp.dto.PageParams;
import gov.openpto.odp.dto.PageResponse;
import gov.openpto.odp.mapper.AccountMapper;
import gov.openpto.odp.model.User;
import gov.openpto.odp.repository.ApiKeyCounts;
import gov.openpto.odp.repository.ApiKeyRepository;
import gov.openpto.odp.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin users page: one query for the page, one for roles (batch fetch), one for key counts. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository users;
    private final ApiKeyRepository keys;
    private final AccountMapper mapper;

    public PageResponse<AdminUserResponse> list(String emailContains, PageParams paging) {
        Pageable pageable = PageRequest.of(
                paging.pageOrDefault(), paging.sizeOrDefault(), Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        Page<User> page = emailContains == null || emailContains.isBlank()
                ? users.findAll(pageable)
                : users.findByEmailContainingIgnoreCase(emailContains.trim(), pageable);
        List<UUID> ids = page.getContent().stream().map(User::getId).toList();
        Map<UUID, ApiKeyCounts> counts = ids.isEmpty()
                ? Map.of()
                : keys.countsForUsers(ids).stream().collect(Collectors.toMap(ApiKeyCounts::userId, Function.identity()));
        return PageResponse.of(page, u -> {
            ApiKeyCounts c = counts.get(u.getId());
            return new AdminUserResponse(
                    u.getId(),
                    u.getEmail(),
                    u.getDisplayName(),
                    mapper.roleNames(u.getRoles()),
                    u.isEnabled(),
                    u.getCreatedAt(),
                    u.getLastLoginAt(),
                    u.getLockedUntil(),
                    c == null ? 0 : c.active(),
                    c == null ? 0 : c.total());
        });
    }
}
