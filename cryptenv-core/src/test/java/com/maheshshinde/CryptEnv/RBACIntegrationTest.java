package com.maheshshinde.CryptEnv;

import com.maheshshinde.CryptEnv.model.Permission;
import com.maheshshinde.CryptEnv.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RBACIntegrationTest {

    @Test
    void testOwnerHasAllPermissions() {
        assertTrue(Permission.hasPermission(Role.OWNER, Permission.SECRET_READ));
        assertTrue(Permission.hasPermission(Role.OWNER, Permission.SECRET_WRITE));
        assertTrue(Permission.hasPermission(Role.OWNER, Permission.SECRET_DELETE));
        assertTrue(Permission.hasPermission(Role.OWNER, Permission.WORKSPACE_READ));
        assertTrue(Permission.hasPermission(Role.OWNER, Permission.WORKSPACE_WRITE));
        assertTrue(Permission.hasPermission(Role.OWNER, Permission.WORKSPACE_DELETE));
        assertTrue(Permission.hasPermission(Role.OWNER, Permission.WORKSPACE_MANAGE_MEMBERS));
        assertTrue(Permission.hasPermission(Role.OWNER, Permission.USER_READ));
        assertTrue(Permission.hasPermission(Role.OWNER, Permission.USER_WRITE));
        assertTrue(Permission.hasPermission(Role.OWNER, Permission.USER_DELETE));
        assertTrue(Permission.hasPermission(Role.OWNER, Permission.AUDIT_READ));
        assertTrue(Permission.hasPermission(Role.OWNER, Permission.AUDIT_DELETE));
    }

    @Test
    void testAdminHasMostPermissions() {
        assertTrue(Permission.hasPermission(Role.ADMIN, Permission.SECRET_READ));
        assertTrue(Permission.hasPermission(Role.ADMIN, Permission.SECRET_WRITE));
        assertTrue(Permission.hasPermission(Role.ADMIN, Permission.SECRET_DELETE));
        assertTrue(Permission.hasPermission(Role.ADMIN, Permission.WORKSPACE_READ));
        assertTrue(Permission.hasPermission(Role.ADMIN, Permission.WORKSPACE_WRITE));
        assertTrue(Permission.hasPermission(Role.ADMIN, Permission.WORKSPACE_DELETE));
        assertTrue(Permission.hasPermission(Role.ADMIN, Permission.WORKSPACE_MANAGE_MEMBERS));
        assertTrue(Permission.hasPermission(Role.ADMIN, Permission.USER_READ));
        assertTrue(Permission.hasPermission(Role.ADMIN, Permission.USER_WRITE));
        assertTrue(Permission.hasPermission(Role.ADMIN, Permission.USER_DELETE));
        assertTrue(Permission.hasPermission(Role.ADMIN, Permission.AUDIT_READ));
        assertFalse(Permission.hasPermission(Role.ADMIN, Permission.AUDIT_DELETE));
    }

    @Test
    void testDeveloperHasLimitedPermissions() {
        assertTrue(Permission.hasPermission(Role.DEVELOPER, Permission.SECRET_READ));
        assertTrue(Permission.hasPermission(Role.DEVELOPER, Permission.SECRET_WRITE));
        assertFalse(Permission.hasPermission(Role.DEVELOPER, Permission.SECRET_DELETE));
        assertTrue(Permission.hasPermission(Role.DEVELOPER, Permission.WORKSPACE_READ));
        assertFalse(Permission.hasPermission(Role.DEVELOPER, Permission.WORKSPACE_WRITE));
        assertFalse(Permission.hasPermission(Role.DEVELOPER, Permission.WORKSPACE_DELETE));
        assertFalse(Permission.hasPermission(Role.DEVELOPER, Permission.WORKSPACE_MANAGE_MEMBERS));
        assertFalse(Permission.hasPermission(Role.DEVELOPER, Permission.USER_READ));
        assertFalse(Permission.hasPermission(Role.DEVELOPER, Permission.USER_WRITE));
        assertFalse(Permission.hasPermission(Role.DEVELOPER, Permission.USER_DELETE));
        assertFalse(Permission.hasPermission(Role.DEVELOPER, Permission.AUDIT_READ));
        assertFalse(Permission.hasPermission(Role.DEVELOPER, Permission.AUDIT_DELETE));
    }

    @Test
    void testAuditorHasReadOnlyPermissions() {
        assertTrue(Permission.hasPermission(Role.AUDITOR, Permission.SECRET_READ));
        assertFalse(Permission.hasPermission(Role.AUDITOR, Permission.SECRET_WRITE));
        assertFalse(Permission.hasPermission(Role.AUDITOR, Permission.SECRET_DELETE));
        assertTrue(Permission.hasPermission(Role.AUDITOR, Permission.WORKSPACE_READ));
        assertFalse(Permission.hasPermission(Role.AUDITOR, Permission.WORKSPACE_WRITE));
        assertFalse(Permission.hasPermission(Role.AUDITOR, Permission.WORKSPACE_DELETE));
        assertFalse(Permission.hasPermission(Role.AUDITOR, Permission.WORKSPACE_MANAGE_MEMBERS));
        assertFalse(Permission.hasPermission(Role.AUDITOR, Permission.USER_READ));
        assertFalse(Permission.hasPermission(Role.AUDITOR, Permission.USER_WRITE));
        assertFalse(Permission.hasPermission(Role.AUDITOR, Permission.USER_DELETE));
        assertTrue(Permission.hasPermission(Role.AUDITOR, Permission.AUDIT_READ));
        assertFalse(Permission.hasPermission(Role.AUDITOR, Permission.AUDIT_DELETE));
    }

    @Test
    void testRoleEnumValues() {
        assertEquals(4, Role.values().length);
        assertEquals(Role.OWNER, Role.valueOf("OWNER"));
        assertEquals(Role.ADMIN, Role.valueOf("ADMIN"));
        assertEquals(Role.DEVELOPER, Role.valueOf("DEVELOPER"));
        assertEquals(Role.AUDITOR, Role.valueOf("AUDITOR"));
    }

    @Test
    void testPermissionEnumValues() {
        assertEquals(13, Permission.values().length);
        assertEquals(Permission.SECRET_READ, Permission.valueOf("SECRET_READ"));
        assertEquals(Permission.SECRET_WRITE, Permission.valueOf("SECRET_WRITE"));
        assertEquals(Permission.SECRET_DELETE, Permission.valueOf("SECRET_DELETE"));
        assertEquals(Permission.WORKSPACE_READ, Permission.valueOf("WORKSPACE_READ"));
        assertEquals(Permission.WORKSPACE_WRITE, Permission.valueOf("WORKSPACE_WRITE"));
        assertEquals(Permission.WORKSPACE_DELETE, Permission.valueOf("WORKSPACE_DELETE"));
        assertEquals(Permission.WORKSPACE_MANAGE_MEMBERS, Permission.valueOf("WORKSPACE_MANAGE_MEMBERS"));
        assertEquals(Permission.USER_READ, Permission.valueOf("USER_READ"));
        assertEquals(Permission.USER_WRITE, Permission.valueOf("USER_WRITE"));
        assertEquals(Permission.USER_DELETE, Permission.valueOf("USER_DELETE"));
        assertEquals(Permission.AUDIT_READ, Permission.valueOf("AUDIT_READ"));
        assertEquals(Permission.AUDIT_DELETE, Permission.valueOf("AUDIT_DELETE"));
    }
}
