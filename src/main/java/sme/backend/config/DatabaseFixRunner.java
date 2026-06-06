package sme.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseFixRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            log.info("Checking and fixing database constraints...");
            // Drop outdated check constraints that prevent inserting new enum values
            jdbcTemplate.execute("ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check");
            jdbcTemplate.execute("ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_payment_status_check");
            jdbcTemplate.execute("ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_type_check");
            
            // Also drop constraints on the Envers audit table
            jdbcTemplate.execute("ALTER TABLE orders_audit DROP CONSTRAINT IF EXISTS orders_audit_status_check");
            jdbcTemplate.execute("ALTER TABLE orders_audit DROP CONSTRAINT IF EXISTS orders_audit_payment_status_check");
            jdbcTemplate.execute("ALTER TABLE orders_audit DROP CONSTRAINT IF EXISTS orders_audit_type_check");
            
            // Also drop constraints on order_status_history just in case
            jdbcTemplate.execute("ALTER TABLE order_status_history DROP CONSTRAINT IF EXISTS order_status_history_new_status_check");
            jdbcTemplate.execute("ALTER TABLE order_status_history DROP CONSTRAINT IF EXISTS order_status_history_old_status_check");
            
            log.info("Successfully cleaned up outdated enum constraints in database.");

            // Phase 5: Database Index Optimization for Search Performance
            log.info("Creating database indexes for performance optimization...");
            
            // Products
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_products_search ON products (name, sku, isbn_barcode)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_products_category ON products (category_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_products_is_active ON products (is_active)");

            // Customers
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_customers_search ON customers (phone_number, full_name)");

            // Invoices
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_invoices_customer ON invoices (customer_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_invoices_code ON invoices (code)");

            // Orders
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_orders_customer ON orders (customer_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_orders_code ON orders (code)");

            log.info("Successfully created database indexes.");

        } catch (Exception e) {
            log.warn("Could not execute constraint cleanup: {}", e.getMessage());
        }
    }
}
