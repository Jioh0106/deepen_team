package com.deepen.controller;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.deepen.domain.PayInfoDTO;
import com.deepen.service.PayInfoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/payroll")
@Log
public class PayrollRestController {
	
	private final PayInfoService payInfoService;

	 //급여 지급 이력 저장
    @PostMapping("/pay-info/save")
    public ResponseEntity<?> savePayInfo(@RequestBody List<PayInfoDTO> payInfoDTOList) {
        if (payInfoDTOList == null || payInfoDTOList.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "message", "저장할 데이터가 없습니다.",
                    "status", "error"
                ));
        }

        Map<String, Object> result = payInfoService.savePayInfo(payInfoDTOList);
        
        if ((boolean) result.get("success")) {
            return ResponseEntity.ok()
                .body(Map.of(
                    "message", "저장되었습니다.",
                    "status", "success"
                ));
        } else {
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "message", String.join("\n", (List<String>) result.get("errors")),
                    "status", "error"
                ));
        }
    }

    //급여 지급 이력 - 부서 목록
    @GetMapping("/pay-info/departments")
    public ResponseEntity<?> getDepartments() {
        List<Map<String, String>> departments = payInfoService.getDepartments();
        return ResponseEntity.ok(departments);
    }

    @GetMapping("/pay-info/search")
    public ResponseEntity<?> searchPayInfo(
        @RequestParam(value = "department", required = false, defaultValue = "") String department,
        @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
        @RequestParam(value = "searchType", required = false, defaultValue = "main") String searchType) {
        
        try {
            // 현재 로그인한 사용자의 권한 확인
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
            String currentEmpId = auth.getName();
            
            // 권한 체크
            boolean isRegularEmployee = authorities.stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ATHR003"));
            
            List<PayInfoDTO> results;
            
            if (isRegularEmployee) {
                // 일반 사원인 경우 자신의 급여 정보만 조회
                results = payInfoService.getEmployeePayInfo(currentEmpId);
            } else {
                // 관리자인 경우 검색 조건에 따른 조회
                results = "modal".equals(searchType) 
                    ? payInfoService.searchEmployees(department, keyword)
                    : payInfoService.searchPayInfo(department, keyword);
            }
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "message", "검색 중 오류가 발생했습니다: " + e.getMessage(),
                    "status", "error"
                ));
        }
    }
    
    
    
    @GetMapping("/pay-info/missing")
    public ResponseEntity<?> getMissingPaymentEmployees(@RequestParam("paymentDate") String paymentDate) {
        try {
            List<Map<String, Object>> missingEmployees = payInfoService.getMissingPaymentEmployees(paymentDate);
                
            Map<String, Object> response = new HashMap<>();
            response.put("message", missingEmployees.isEmpty() ? 
                "해당 월의 모든 직원에게 급여가 지급되었습니다." : 
                String.format("%d명의 미지급 직원이 있습니다.", missingEmployees.size()));
            response.put("data", missingEmployees);
                
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "조회 중 오류가 발생했습니다."));
        }
    }
////    특정 월의 급여 미지급 직원 목록을 조회합니다.
//   @GetMapping("/pay-info/missing")
//   public ResponseEntity<?> getMissingPaymentEmployees(
//           @RequestParam("paymentDate") String paymentDate) {
//       try {
//           List<Map<String, Object>> missingEmployees = 
//               payInfoService.getMissingPaymentEmployees(paymentDate);
//           
//           if (missingEmployees.isEmpty()) {
//               return ResponseEntity.ok()
//                   .body(Map.of(
//                       "message", "해당 월의 모든 직원에게 급여가 지급되었습니다.",
//                       "data", missingEmployees
//                   ));
//           }
//           
//           return ResponseEntity.ok()
//               .body(Map.of(
//                   "message", String.format("%d명의 미지급 직원이 있습니다.", 
//                       missingEmployees.size()),
//                   "data", missingEmployees
//               ));
//       } catch (IllegalArgumentException e) {
//           return ResponseEntity.badRequest()
//               .body(Map.of("message", e.getMessage()));
//       } catch (Exception e) {
//           return ResponseEntity.internalServerError()
//               .body(Map.of("message", "조회 중 오류가 발생했습니다."));
//       }
//   }
}
    
    
