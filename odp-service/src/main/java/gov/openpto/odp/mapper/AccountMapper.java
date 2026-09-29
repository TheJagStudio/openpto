package gov.openpto.odp.mapper;

import gov.openpto.odp.dto.ApiKeyResponse;
import gov.openpto.odp.dto.UserResponse;
import gov.openpto.odp.model.ApiKey;
import gov.openpto.odp.model.Role;
import gov.openpto.odp.model.User;

import java.util.Collection;
import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    UserResponse toResponse(User user);

    @Mapping(target = "key", ignore = true)
    ApiKeyResponse toResponse(ApiKey key);

    List<ApiKeyResponse> toResponses(List<ApiKey> keys);

    /** Roles in a stable order: USER before ADMIN. */
    default List<String> roleNames(Collection<Role> roles) {
        return roles == null ? List.of() : roles.stream().sorted().map(Role::name).toList();
    }
}
