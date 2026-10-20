package com.modernshopflow.catalog.reviews;

import com.modernshopflow.catalog.api.PageResponse;
import com.modernshopflow.catalog.domain.CatalogNotFoundException;
import com.shopflow.backend.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products/{productId}")
public class ReviewController {
    private final JdbcTemplate jdbc;
    private final AccountService accounts;
    public ReviewController(JdbcTemplate jdbc, AccountService accounts) {
        this.jdbc = jdbc; this.accounts = accounts;
    }
    @GetMapping("/reviews")
    public PageResponse<Review> list(@PathVariable long productId, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="10") int size) {
        requireProduct(productId);
        if (page < 0 || size < 1 || size > 50 || page > Integer.MAX_VALUE / size) throw new IllegalArgumentException("Invalid review pagination");
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM product_reviews WHERE product_id=?", Long.class, productId);
        List<Review> items = jdbc.query("SELECT r.*, u.display_name FROM product_reviews r JOIN users u ON u.id=r.user_id WHERE r.product_id=? ORDER BY r.updated_at DESC,r.id DESC LIMIT ? OFFSET ?",
                (rs,row) -> new Review(rs.getLong("id"), rs.getString("display_name"), rs.getInt("rating"), rs.getString("comment"), rs.getObject("updated_at", OffsetDateTime.class), true), productId, size, page*size);
        return new PageResponse<>(items, page, size, total, (int)((total+size-1)/size));
    }
    @GetMapping("/review-eligibility")
    public Eligibility eligibility(@PathVariable long productId, Authentication auth) {
        requireProduct(productId);
        var user = accounts.requireUser(auth);
        var own = jdbc.query("SELECT rating,comment FROM product_reviews WHERE product_id=? AND user_id=?",
                (rs,row) -> new ReviewInput(rs.getInt("rating"),rs.getString("comment")), productId,user.getId());
        boolean eligible = "CUSTOMER".equals(user.getRole()) && purchased(productId, user.getId());
        return new Eligibility(eligible, own.isEmpty()?null:own.getFirst(), eligible?null:"Bạn có thể đánh giá sau khi mua và nhận sản phẩm này.");
    }
    @PutMapping("/reviews")
    @Transactional
    public ReviewInput save(@PathVariable long productId, Authentication auth, @Valid @RequestBody ReviewInput input) {
        requireProduct(productId);
        var user = accounts.requireUser(auth);
        if (!"CUSTOMER".equals(user.getRole()) || !purchased(productId,user.getId())) throw new AccessDeniedException("Chỉ khách đã mua và nhận sản phẩm được đánh giá.");
        // Serialize edits by the same account; unique constraint also prevents duplicates.
        jdbc.queryForObject("SELECT id FROM users WHERE id=? FOR UPDATE", Long.class, user.getId());
        int updated = jdbc.update("UPDATE product_reviews SET rating=?,comment=?,updated_at=CURRENT_TIMESTAMP WHERE product_id=? AND user_id=?",
                input.rating(),input.comment().trim(),productId,user.getId());
        if (updated == 0) jdbc.update("INSERT INTO product_reviews(product_id,user_id,rating,comment) VALUES (?,?,?,?)", productId,user.getId(),input.rating(),input.comment().trim());
        return new ReviewInput(input.rating(),input.comment().trim());
    }
    private boolean purchased(long productId, long userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM orders o JOIN order_items oi ON oi.order_id=o.id WHERE o.customer_id=? AND oi.product_id=? AND o.status='PAID' AND o.delivery_status='DELIVERED'", Long.class, userId,productId) > 0;
    }
    private void requireProduct(long id) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM products p JOIN categories c ON c.id=p.category_id WHERE p.id=? AND p.is_deleted=FALSE AND c.is_deleted=FALSE", Long.class,id) == 0)
            throw new CatalogNotFoundException("Product was not found");
    }
    public record ReviewInput(@NotNull @Min(1) @Max(5) Integer rating, @NotBlank @Size(max=2000) String comment) {}
    public record Review(long id, String displayName, int rating, String comment, OffsetDateTime updatedAt, boolean verifiedPurchase) {}
    public record Eligibility(boolean canReview, ReviewInput myReview, String reason) {}
}
