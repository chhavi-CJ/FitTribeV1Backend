// WaitlistController.java
package com.fittribe.api.waitlist;

import com.fittribe.api.dto.ApiResponse;
import com.fittribe.api.exception.ApiException;
import com.fittribe.api.waitlist.dto.WaitlistResponse;
import com.fittribe.api.waitlist.dto.WaitlistSubmitRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/waitlist")
public class WaitlistController {

    private final WaitlistService service;

    public WaitlistController(WaitlistService service) {
        this.service = service;
    }

    // POST /api/waitlist  — submit signup
    @PostMapping
    public ResponseEntity<ApiResponse<WaitlistResponse>> submit(@Valid @RequestBody WaitlistSubmitRequest req) {
        WaitlistResponse resp = service.submit(req);
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    // GET /api/waitlist/{code}  — refresh dashboard data
    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<WaitlistResponse>> getByCode(@PathVariable String code) {
        WaitlistResponse resp = service.findByCode(code)
                .orElseThrow(() -> ApiException.notFound("Referral code"));
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    // GET /api/waitlist/count — public counter (optional, useful for landing page "127 joined" line later)
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<CountResponse>> count() {
        long n = service.count();
        return ResponseEntity.ok(ApiResponse.success(new CountResponse(n)));
    }

    public record CountResponse(long total) {}
}