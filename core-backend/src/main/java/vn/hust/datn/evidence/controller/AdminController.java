package vn.hust.datn.evidence.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import vn.hust.datn.evidence.config.security.CurrentUser;
import vn.hust.datn.evidence.dto.AdminProjectView;
import vn.hust.datn.evidence.dto.AdminUserView;
import vn.hust.datn.evidence.dto.UpdateProjectTierRequest;
import vn.hust.datn.evidence.service.admin.AdminProjectService;
import vn.hust.datn.evidence.service.admin.AdminUserService;

import lombok.RequiredArgsConstructor;

/**
 * Quản trị — CHỈ vai trò ADMIN. Quyền được chặn ở {@code SecurityConfig.apiChain} theo đường dẫn
 * {@code /api/v1/admin/**}, không phải ở đây: một endpoint admin mới thêm vào đúng đường dẫn này
 * tự được bảo vệ, không phụ thuộc người viết có nhớ gắn annotation hay không.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminUserService adminUserService;
    private final AdminProjectService adminProjectService;

    @GetMapping("/users")
    public List<AdminUserView> listUsers(@RequestParam(required = false) String q) {
        return adminUserService.list(q);
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable UUID id, @AuthenticationPrincipal CurrentUser admin) {
        adminUserService.delete(id, admin.id());
    }

    @GetMapping("/projects")
    public List<AdminProjectView> listProjects(@RequestParam(required = false) String q) {
        return adminProjectService.list(q);
    }

    /** Đổi hạng Free/Premium. Idempotent: đặt lại đúng hạng hiện tại không đổi gì. */
    @PatchMapping("/projects/{id}")
    public AdminProjectView changeProjectTier(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProjectTierRequest request,
            @AuthenticationPrincipal CurrentUser admin) {
        return adminProjectService.changeTier(id, request.tier(), admin.id());
    }
}
