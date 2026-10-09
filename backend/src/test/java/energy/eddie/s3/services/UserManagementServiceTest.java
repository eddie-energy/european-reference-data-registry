package energy.eddie.s3.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import energy.eddie.s3.exceptions.ForbiddenException;
import energy.eddie.s3.exceptions.NotFoundException;
import energy.eddie.s3.generated.model.CreateManagementUserRequest;
import energy.eddie.s3.generated.model.Nation;
import energy.eddie.s3.generated.model.OrganizationRefDto;
import energy.eddie.s3.generated.model.UpdateManagementUserRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {
    @Mock KeycloakUserDirectory directory;
    @InjectMocks UserManagementService service;

    private final UUID userId = UUID.randomUUID();
    private final OrganizationRefDto first = organization("eda");
    private final OrganizationRefDto second = organization("fhooe");

    @Test
    void listCombinesCountryAndOrganization() {
        when(directory.listOrganizations()).thenReturn(List.of(first, second));
        when(directory.organizationsByUser(List.of(first, second))).thenReturn(Map.of(userId, List.of(first)));
        when(directory.listUsers()).thenReturn(List.of(
                new KeycloakUserDirectory.KeycloakUser(userId, "member", Nation.AUT),
                new KeycloakUserDirectory.KeycloakUser(UUID.randomUUID(), "loner", null)));

        var users = service.list();

        assertThat(users).hasSize(2);
        assertThat(users.get(0).getCountry()).isEqualTo(Nation.AUT);
        assertThat(users.get(0).getOrganization()).isEqualTo(first);
        assertThat(users.get(1).getOrganization()).isNull();
    }

    @Test
    void listMarksProtectedUserNotEditable() {
        when(directory.listOrganizations()).thenReturn(List.of());
        when(directory.organizationsByUser(List.of())).thenReturn(Map.of());
        when(directory.listUsers()).thenReturn(List.of(new KeycloakUserDirectory.KeycloakUser(userId, "ceeds", null)));

        assertThat(service.list()).singleElement().satisfies(user -> assertThat(user.getEditable()).isFalse());
    }

    @Test
    void updateMovesUserToSingleChosenOrganization() {
        var user = new KeycloakUserDirectory.KeycloakUser(userId, "member", Nation.FRA);
        when(directory.findUser(userId)).thenReturn(Optional.of(user));
        when(directory.listOrganizations()).thenReturn(List.of(first, second));
        when(directory.userOrganizationIds(userId)).thenReturn(List.of(first.getId()));

        var result = service.update(userId, new UpdateManagementUserRequest()
                .country(Nation.FRA)
                .organizationId(second.getId()));

        verify(directory).updateCountry(userId, Nation.FRA);
        verify(directory).addMember(second.getId(), userId);
        verify(directory).removeMember(first.getId(), userId);
        assertThat(result.getOrganization()).isEqualTo(second);
    }

    @Test
    void updateWithoutOrganizationRemovesAllMemberships() {
        when(directory.findUser(userId)).thenReturn(Optional.of(new KeycloakUserDirectory.KeycloakUser(userId, "member", null)));
        when(directory.listOrganizations()).thenReturn(List.of(first));
        when(directory.userOrganizationIds(userId)).thenReturn(List.of(first.getId()));

        var result = service.update(userId, new UpdateManagementUserRequest());

        verify(directory).removeMember(first.getId(), userId);
        verify(directory, never()).addMember(any(), any());
        assertThat(result.getOrganization()).isNull();
    }

    @Test
    void updateKeepsExistingMembershipUntouched() {
        when(directory.findUser(userId)).thenReturn(Optional.of(new KeycloakUserDirectory.KeycloakUser(userId, "member", null)));
        when(directory.listOrganizations()).thenReturn(List.of(first));
        when(directory.userOrganizationIds(userId)).thenReturn(List.of(first.getId()));

        service.update(userId, new UpdateManagementUserRequest().organizationId(first.getId()));

        verify(directory, never()).addMember(any(), any());
        verify(directory, never()).removeMember(any(), any());
    }

    @Test
    void updateRejectsProtectedUser() {
        when(directory.findUser(userId)).thenReturn(Optional.of(new KeycloakUserDirectory.KeycloakUser(userId, "ceeds", null)));

        var request = new UpdateManagementUserRequest();
        assertThatThrownBy(() -> service.update(userId, request))
                .isInstanceOf(ForbiddenException.class);
        verify(directory, never()).updateCountry(any(), any());
    }

    @Test
    void updateRejectsUnknownOrganization() {
        when(directory.findUser(userId)).thenReturn(Optional.of(new KeycloakUserDirectory.KeycloakUser(userId, "member", null)));
        when(directory.listOrganizations()).thenReturn(List.of(first));

        var request = new UpdateManagementUserRequest().organizationId(UUID.randomUUID());
        assertThatThrownBy(() -> service.update(userId, request))
                .isInstanceOf(NotFoundException.class);
        verify(directory, never()).updateCountry(any(), any());
    }

    @Test
    void createAddsMembershipAndReturnsUser() {
        var user = new KeycloakUserDirectory.KeycloakUser(userId, "new.user", Nation.ESP);
        when(directory.listOrganizations()).thenReturn(List.of(first, second));
        when(directory.createUser("new.user", "s3cret-pass", Nation.ESP)).thenReturn(userId);
        when(directory.findUser(userId)).thenReturn(Optional.of(user));

        var result = service.create(createRequest(first.getId()));

        verify(directory).addMember(first.getId(), userId);
        assertThat(result.getOrganization()).isEqualTo(first);
    }

    @Test
    void createWithoutOrganizationAddsNoMembership() {
        when(directory.listOrganizations()).thenReturn(List.of(first));
        when(directory.createUser(any(), any(), any())).thenReturn(userId);
        when(directory.findUser(userId)).thenReturn(Optional.of(new KeycloakUserDirectory.KeycloakUser(userId, "new.user", null)));

        service.create(createRequest(null));

        verify(directory, never()).addMember(any(), any());
    }

    @Test
    void createRejectsUnknownOrganizationBeforeCreatingUser() {
        when(directory.listOrganizations()).thenReturn(List.of(first));

        var request = createRequest(UUID.randomUUID());
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(NotFoundException.class);
        verify(directory, never()).createUser(any(), any(), any());
    }

    @Test
    void createDeletesUserWhenMembershipFails() {
        when(directory.listOrganizations()).thenReturn(List.of(first));
        when(directory.createUser(any(), any(), any())).thenReturn(userId);
        doThrow(new IllegalStateException("boom")).when(directory).addMember(first.getId(), userId);

        var request = createRequest(first.getId());
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(IllegalStateException.class);
        verify(directory).deleteUser(userId);
    }

    private static CreateManagementUserRequest createRequest(UUID organizationId) {
        return new CreateManagementUserRequest()
                .username("new.user")
                .temporaryPassword("s3cret-pass")
                .country(Nation.ESP)
                .organizationId(organizationId);
    }

    private static OrganizationRefDto organization(String alias) {
        return new OrganizationRefDto().id(UUID.randomUUID()).alias(alias).name(alias);
    }
}
