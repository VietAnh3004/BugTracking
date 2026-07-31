package vn.hust.datn.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import vn.hust.datn.evidence.AbstractIntegrationTest;
import vn.hust.datn.evidence.domain.Project;
import vn.hust.datn.evidence.domain.User;
import vn.hust.datn.evidence.dto.CreateProjectRequest;
import vn.hust.datn.evidence.exception.ResourceNotFoundException;
import vn.hust.datn.evidence.testsupport.TestFixtures;

/**
 * Quy tắc authorization duy nhất của {@link ProjectService}: tài nguyên không thuộc về bạn ⇒ 404
 * (qua {@link ResourceNotFoundException}), không phải 403 — không tạo oracle cho phép dò sự tồn
 * tại của project người khác.
 */
class ProjectAuthorizationTest extends AbstractIntegrationTest {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private TestFixtures fixtures;

    @Test
    void taoDuAnGanDungOwnerId() {
        User owner = fixtures.user("owner-%s@test.local".formatted(UUID.randomUUID()));

        Project project = projectService.create(new CreateProjectRequest("p-" + UUID.randomUUID(), null), owner.getId());

        assertThat(project.getOwnerId()).isEqualTo(owner.getId());
    }

    @Test
    void chuSoHuuThayDuocDuAnCuaMinh() {
        User owner = fixtures.user("owner-%s@test.local".formatted(UUID.randomUUID()));
        Project project = fixtures.project("p-" + UUID.randomUUID(), owner);

        Project found = projectService.get(project.getId(), owner.getId());

        assertThat(found.getId()).isEqualTo(project.getId());
    }

    @Test
    void nguoiKhacKhongThayDuocDuAnTraVe404() {
        User owner = fixtures.user("owner-%s@test.local".formatted(UUID.randomUUID()));
        User attacker = fixtures.user("attacker-%s@test.local".formatted(UUID.randomUUID()));
        Project project = fixtures.project("p-" + UUID.randomUUID(), owner);

        assertThatThrownBy(() -> projectService.get(project.getId(), attacker.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void danhSachChiTraVeDuAnCuaChinhMinh() {
        User owner = fixtures.user("owner-%s@test.local".formatted(UUID.randomUUID()));
        User other = fixtures.user("other-%s@test.local".formatted(UUID.randomUUID()));
        Project ownedProject = fixtures.project("p-" + UUID.randomUUID(), owner);
        fixtures.project("p-" + UUID.randomUUID(), other);

        var page = projectService.list(PageRequest.of(0, 50), owner.getId());

        assertThat(page.getContent()).extracting(Project::getId).contains(ownedProject.getId());
        assertThat(page.getContent()).allMatch(p -> p.getOwnerId().equals(owner.getId()));
    }
}
