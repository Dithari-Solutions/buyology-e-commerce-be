package com.buyology.ecommerce.warranty;

import com.buyology.ecommerce.role.PermissionConstants;
import com.buyology.ecommerce.role.RoleDataInitializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A permission code that is used in a guard but missing from the seed list never reaches the
 * database, so no role can be granted it and the endpoint is SUPERADMIN-only by accident.
 */
class WarrantyPermissionsTest {

    private static final List<String> WARRANTY_CODES = List.of(
            PermissionConstants.PRODUCT_WARRANTY_READ,
            PermissionConstants.PRODUCT_WARRANTY_CREATE,
            PermissionConstants.PRODUCT_WARRANTY_UPDATE,
            PermissionConstants.PRODUCT_WARRANTY_DELETE,
            PermissionConstants.ORDER_WARRANTY_UPDATE);

    @SuppressWarnings("unchecked")
    private static List<String> seeded(String field) throws Exception {
        var f = RoleDataInitializer.class.getDeclaredField(field);
        f.setAccessible(true);
        return (List<String>) f.get(null);
    }

    @Test
    void everyWarrantyCodeIsSeededAndGivenToTheStarterAdminRole() throws Exception {
        List<String> all = seeded("ALL_PERMISSIONS");
        List<String> admin = seeded("ADMIN_PERMISSIONS");
        for (String code : WARRANTY_CODES) {
            assertTrue(all.contains(code), code + " must be seeded");
            assertTrue(admin.contains(code), code + " belongs to day-to-day admin work");
        }
    }

    @Test
    void codesFollowTheModuleResourceActionScheme() {
        for (String code : WARRANTY_CODES) {
            assertTrue(code.matches("[a-z]+:warranty:[a-z]+"), code);
        }
    }
}
