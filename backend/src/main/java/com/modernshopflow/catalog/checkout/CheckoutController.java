package com.modernshopflow.catalog.checkout;

import com.modernshopflow.catalog.orders.OrderDtos.*;
import com.modernshopflow.catalog.orders.OrderService;
import com.modernshopflow.catalog.service.CatalogService;
import com.shopflow.backend.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class CheckoutController {
    private final AccountService accounts;
    private final AddressService addresses;
    private final VoucherService vouchers;
    private final CatalogService catalog;
    private final OrderService orders;
    public CheckoutController(AccountService accounts,AddressService addresses,VoucherService vouchers,CatalogService catalog,OrderService orders) {
        this.accounts=accounts;this.addresses=addresses;this.vouchers=vouchers;this.catalog=catalog;this.orders=orders;
    }
    public record AddressInput(@NotNull @Valid ShippingAddress shippingAddress,boolean makeDefault) {}
    public record QuoteInput(@NotEmpty @Size(max=100) List<@NotNull @Valid OrderLineRequest> items,@Size(max=50) String voucherCode) {}
    public record CheckoutInput(@NotNull @Valid ShippingAddress shippingAddress,@Email @Size(max=255) String contactEmail,
            @NotBlank String paymentMethod,@NotEmpty @Size(max=100) List<@NotNull @Valid OrderLineRequest> items,
            @Size(max=50) String voucherCode,@NotNull UUID checkoutKey,boolean saveAddress,Long addressId,boolean makeDefault,@NotNull @PositiveOrZero Long expectedTotal) {}

    @GetMapping("/api/account/addresses") public List<AddressService.Address> addresses(Authentication auth) { return addresses.list(accounts.requireUser(auth).getId()); }
    @PostMapping("/api/account/addresses") public AddressService.Address add(Authentication auth,@Valid @RequestBody AddressInput input) { return addresses.save(accounts.requireUser(auth).getId(),null,input.shippingAddress(),input.makeDefault()); }
    @PutMapping("/api/account/addresses/{id}") public AddressService.Address update(Authentication auth,@PathVariable long id,@Valid @RequestBody AddressInput input) { return addresses.save(accounts.requireUser(auth).getId(),id,input.shippingAddress(),input.makeDefault()); }
    @DeleteMapping("/api/account/addresses/{id}") public void delete(Authentication auth,@PathVariable long id) { addresses.delete(accounts.requireUser(auth).getId(),id); }
    @GetMapping("/api/checkout/vouchers") public List<VoucherService.Voucher> vouchers() { return vouchers.available(); }
    @PostMapping("/api/checkout/quote") public VoucherService.Price quote(Authentication auth,@Valid @RequestBody QuoteInput input) {
        long user=accounts.requireUser(auth).getId(), subtotal=0;
        java.util.Set<Long> seen=new java.util.HashSet<>();
        for(var line:input.items()) {
            if(!seen.add(line.productId())) throw new IllegalArgumentException("Sản phẩm bị lặp trong giỏ.");
            var product=catalog.getProduct(line.productId());
            if(product.productVariants().stream().mapToInt(v->v.availableQuantity()).sum()<line.quantity()) throw new IllegalArgumentException("Sản phẩm "+product.name()+" không còn đủ hàng.");
            subtotal=Math.addExact(subtotal,Math.multiplyExact(product.listPrice(),line.quantity()));
        }
        if(subtotal>9999999999L) throw new IllegalArgumentException("Giá trị đơn vượt giới hạn hỗ trợ.");
        return vouchers.price(subtotal,input.voucherCode(),user,false);
    }
    @PostMapping("/api/checkout/orders") @Transactional
    public OrderResponse place(Authentication auth,@Valid @RequestBody CheckoutInput input) {
        var user=accounts.requireUser(auth);
        var order=orders.create(new CreateOrderRequest(new Customer(input.shippingAddress().receiverName(),input.contactEmail()==null?user.getEmail():input.contactEmail(),input.shippingAddress().phone()),input.shippingAddress(),input.paymentMethod(),input.items(),input.voucherCode(),input.checkoutKey()),user.getId());
        if(order.totalAmount()!=input.expectedTotal()) throw new com.modernshopflow.catalog.domain.CatalogConflictException("Giá hoặc ưu đãi đã thay đổi. Kiểm tra lại tổng tiền và đặt lại.");
        if(input.saveAddress()) addresses.save(user.getId(),input.addressId(),input.shippingAddress(),input.makeDefault());
        return order;
    }
}
