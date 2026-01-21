package com.ecommerce.pay_service.controller;

import com.ecommerce.pay_service.dto.KakaoApproveResponse;
import com.ecommerce.pay_service.dto.PaymentDto;
import com.ecommerce.pay_service.service.PaymentService;
import com.ecommerce.pay_service.vo.RequestPayment;
import com.ecommerce.pay_service.vo.ResponsePayment;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payment-service")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    private final ModelMapper modelMapper;

    @PostMapping("/payment")
    public ResponseEntity<ResponsePayment> createPayment(@RequestHeader("userId") String userId, @RequestBody RequestPayment request) {
        PaymentDto paymentDto = paymentService.processPayment(request, userId);

        ResponsePayment responsePayment = modelMapper.map(paymentDto, ResponsePayment.class);

        return ResponseEntity.status(HttpStatus.CREATED).body(responsePayment);
    }

    @GetMapping("/payment/success")
    public ResponseEntity<KakaoApproveResponse> successPayment(@RequestParam("pg_token") String pgToken,
                                                                @RequestParam("order_id") String orderId) {
        KakaoApproveResponse approveResponse = paymentService.completePayment(pgToken, orderId);

        return ResponseEntity.status(HttpStatus.OK).body(approveResponse);
    }

    @GetMapping("/payment/cancel")
    public ResponseEntity<String> cancelPayment(@RequestParam("order_id") String orderId) {
        paymentService.updatePaymentToCanceled(orderId);
        return ResponseEntity.status(HttpStatus.OK).body("결제가 취소되었습니다.");
    }

    @GetMapping("/payment/fail")
    public ResponseEntity<String> failPayment(@RequestParam("order_id") String orderId) {
        paymentService.updatePaymentToFailed(orderId);
        return ResponseEntity.status(HttpStatus.OK).body("결제에 실패하였습니다.");
    }
}
