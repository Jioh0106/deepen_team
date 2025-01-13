package com.deepen.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.deepen.calculator.SalaryFormulaCalculator;
import com.deepen.domain.PayInfoDTO;
import com.deepen.domain.SalaryFormulaDTO;
import com.deepen.entity.Employees;
import com.deepen.entity.PayInfo;
import com.deepen.mapper.SalaryFormulaMapper;
import com.deepen.repository.PayInfoRepository;
import com.deepen.repository.PersonnelRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.transaction.Transactional;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollCalculatorService {

    private final PersonnelRepository personnelRepository;
    private final PayInfoRepository payInfoRepository;
    private final SalaryFormulaMapper salaryFormulaMapper;
    private final SalaryFormulaCalculator calculator;
    private final ObjectMapper objectMapper;

    /**
     * 급여 계산 수행
     */
    @Transactional
    public PayInfoDTO calculateSalary(String empId, String paymentDate) {
        log.info("급여 계산 시작 - 사원번호: {}, 지급월: {}", empId, paymentDate);

        try {
            // 1. 직원 정보 조회
            Employees emp = findEmployee(empId);

            // 2. 지급월 중복 체크
            validatePaymentMonth(empId, paymentDate);

            // 3. 급여 계산 공식 조회
            List<SalaryFormulaDTO> formulas = getFormulas(paymentDate);

            // 4. 급여 정보 생성 및 계산
            PayInfo payInfo = calculatePayInfo(emp, paymentDate, formulas);

            // 5. 저장
            PayInfo savedPayInfo = payInfoRepository.save(payInfo);
            
            log.info("급여 계산 완료 - 사원번호: {}, 지급월: {}", empId, paymentDate);
            
            // 6. DTO 변환 후 반환
            return convertToDTO(savedPayInfo);

        } catch (Exception e) {
            log.error("급여 계산 중 오류 발생 - 사원번호: {}, 지급월: {}", empId, paymentDate, e);
            throw new RuntimeException("급여 계산 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 직원 정보 조회
     */
    private Employees findEmployee(String empId) {
        return personnelRepository.findById(empId)
            .orElseThrow(() -> new RuntimeException("직원 정보를 찾을 수 없습니다."));
    }

    /**
     * 지급월 중복 체크
     */
    private void validatePaymentMonth(String empId, String paymentDate) {
        if (payInfoRepository.existsByEmpIdAndPaymentDate(empId, paymentDate)) {
            throw new RuntimeException("해당 월의 급여가 이미 지급되었습니다.");
        }
    }

    /**
     * 급여 계산 공식 조회
     */
    private List<SalaryFormulaDTO> getFormulas(String paymentDate) {
        int year = YearMonth.parse(paymentDate).getYear();
        List<SalaryFormulaDTO> formulas = salaryFormulaMapper.getCurrentFormulas(year);
        
        if (formulas.isEmpty()) {
            throw new RuntimeException(String.format("%d년도 급여 계산 공식이 정의되어 있지 않습니다.", year));
        }
        
        return formulas;
    }

    /**
     * 급여 정보 계산
     */
    private PayInfo calculatePayInfo(
            Employees emp, 
            String paymentDate, 
            List<SalaryFormulaDTO> formulas) {
        
        // 1. 기본 정보 생성
        PayInfo payInfo = createBasicPayInfo(emp, paymentDate);

        try {
            // 2. 수당 계산
            calculateAllowances(emp, payInfo, formulas, YearMonth.parse(paymentDate));

            // 3. 공제 계산
            calculateDeductions(emp, payInfo, formulas);

            // 4. 최종 급여 계산
            calculateNetSalary(payInfo);

            // 5. 생성 정보 설정
            setCreationInfo(payInfo);

            return payInfo;

        } catch (Exception e) {
            log.error("급여 계산 중 오류 발생 - 사원: {}, 지급월: {}", emp.getEmp_id(), paymentDate, e);
            throw new RuntimeException("급여 계산 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 기본 급여 정보 생성
     */
    private PayInfo createBasicPayInfo(Employees emp, String paymentDate) {
        PayInfo payInfo = new PayInfo();
        payInfo.setEmpId(emp.getEmp_id());
        payInfo.setPaymentDate(paymentDate);
        payInfo.setEmpSalary(emp.getEmp_salary());
        return payInfo;
    }

    /**
     * 수당 계산
     */
    private void calculateAllowances(
            Employees emp, 
            PayInfo payInfo,
            List<SalaryFormulaDTO> formulas,
            YearMonth paymentMonth) {
            
        BigDecimal totalAllowance = BigDecimal.ZERO;

        // 수당 공식만 필터링
        List<SalaryFormulaDTO> allowanceFormulas = formulas.stream()
            .filter(f -> f.getFormulaCode().startsWith("RWRD"))
            .toList();

        for (SalaryFormulaDTO formula : allowanceFormulas) {
            try {
                // JSON 파싱
                JsonNode content = objectMapper.readTree(formula.getFormulaContent());
                
                // 계산 수행
                BigDecimal amount = calculator.calculate(
                    content,
                    emp,
                    paymentMonth.getMonthValue(),
                    payInfo,
                    formula.getFormulaCode()
                );

                // 수당 종류별로 설정
                setAllowanceAmount(payInfo, formula.getFormulaCode(), amount);
                
                // 총액에 추가
                totalAllowance = totalAllowance.add(amount);

            } catch (Exception e) {
                log.error("수당 계산 중 오류 발생 - 공식: {}", formula.getFormulaName(), e);
                throw new RuntimeException("수당 계산 중 오류가 발생했습니다: " + e.getMessage());
            }
        }

        payInfo.setAllowAmt(totalAllowance);
    }

    /**
     * 수당 금액 설정
     */
    private void setAllowanceAmount(PayInfo payInfo, String code, BigDecimal amount) {
        switch (code) {
            case "RWRD001" -> payInfo.setTechAllowance(amount);
            case "RWRD002" -> payInfo.setPerformanceBonus(amount);
            case "RWRD003" -> payInfo.setTenureAllowance(amount);
            case "RWRD004" -> payInfo.setHolidayAllowance(amount);
            case "RWRD005" -> payInfo.setLeaveAllowance(amount);
            default -> log.warn("알 수 없는 수당 코드: {}", code);
        }
    }

    /**
     * 공제 계산
     */
    private void calculateDeductions(
            Employees emp,
            PayInfo payInfo,
            List<SalaryFormulaDTO> formulas) {
            
        BigDecimal totalDeduction = BigDecimal.ZERO;
        BigDecimal totalSalary = new BigDecimal(payInfo.getEmpSalary())
            .add(payInfo.getAllowAmt());

        // 공제 공식만 필터링
        List<SalaryFormulaDTO> deductionFormulas = formulas.stream()
            .filter(f -> f.getFormulaCode().startsWith("DDCT"))
            .toList();

        for (SalaryFormulaDTO formula : deductionFormulas) {
            try {
                // JSON 파싱
                JsonNode content = objectMapper.readTree(formula.getFormulaContent());
                
                // 계산 수행
                BigDecimal amount = calculator.calculate(
                    content,
                    emp,
                    0,
                    payInfo,
                    formula.getFormulaCode()
                );

                // 공제액이 총급여를 초과하지 않도록 검증
                if (totalDeduction.add(amount).compareTo(totalSalary) > 0) {
                    throw new RuntimeException("공제 금액이 총급여액을 초과할 수 없습니다.");
                }

                // 공제 종류별로 설정
                setDeductionAmount(payInfo, formula.getFormulaCode(), amount);
                
                // 총액에 추가
                totalDeduction = totalDeduction.add(amount);

            } catch (Exception e) {
                log.error("공제 계산 중 오류 발생 - 공식: {}", formula.getFormulaName(), e);
                throw new RuntimeException("공제 계산 중 오류가 발생했습니다: " + e.getMessage());
            }
        }

        payInfo.setDeducAmt(totalDeduction);
    }

    /**
     * 공제 금액 설정
     */
    private void setDeductionAmount(PayInfo payInfo, String code, BigDecimal amount) {
        switch (code) {
            case "DDCT001" -> payInfo.setNationalPension(amount);
            case "DDCT002" -> payInfo.setHealthInsurance(amount);
            case "DDCT003" -> payInfo.setLongtermCareInsurance(amount);
            case "DDCT004" -> payInfo.setEmploymentInsurance(amount);
            case "DDCT005" -> payInfo.setIncomeTax(amount);
            case "DDCT006" -> payInfo.setResidentTax(amount);
            default -> log.warn("알 수 없는 공제 코드: {}", code);
        }
    }

    /**
     * 최종 급여 계산
     */
    private void calculateNetSalary(PayInfo payInfo) {
        BigDecimal netSalary = new BigDecimal(payInfo.getEmpSalary())
            .add(payInfo.getAllowAmt())
            .subtract(payInfo.getDeducAmt());
            
        if (netSalary.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("최종 급여가 음수가 될 수 없습니다.");
        }
        
        payInfo.setNetSalary(netSalary);
    }

    /**
     * 생성 정보 설정
     */
    private void setCreationInfo(PayInfo payInfo) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = auth != null ? auth.getName() : "SYSTEM";
        
        payInfo.setCreateBy(currentUser);
        payInfo.setCreateAt(LocalDateTime.now());
    }

    /**
     * Entity를 DTO로 변환
     */
    private PayInfoDTO convertToDTO(PayInfo payInfo) {
        return PayInfoDTO.builder()
            .paymentNo(payInfo.getPaymentNo())
            .empId(payInfo.getEmpId())
            .paymentDate(payInfo.getPaymentDate())
            .empSalary(payInfo.getEmpSalary())
            // 수당 정보
            .techAllowance(payInfo.getTechAllowance())
            .performanceBonus(payInfo.getPerformanceBonus())
            .tenureAllowance(payInfo.getTenureAllowance())
            .holidayAllowance(payInfo.getHolidayAllowance())
            .leaveAllowance(payInfo.getLeaveAllowance())
            .allowAmt(payInfo.getAllowAmt())
            // 공제 정보
            .nationalPension(payInfo.getNationalPension())
            .healthInsurance(payInfo.getHealthInsurance())
            .longtermCareInsurance(payInfo.getLongtermCareInsurance())
            .employmentInsurance(payInfo.getEmploymentInsurance())
            .incomeTax(payInfo.getIncomeTax())
            .residentTax(payInfo.getResidentTax())
            .deducAmt(payInfo.getDeducAmt())
            // 최종 급여
            .netSalary(payInfo.getNetSalary())
            // 생성 정보
            .createAt(payInfo.getCreateAt())
            .createBy(payInfo.getCreateBy())
            .build();
    }
    
    /**
     * 급여 계산 테스트
     */
    @Transactional
    public Map<String, Object> testCalculateSalary(String empId, String paymentDate) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. 직원 정보 조회
            Employees emp = personnelRepository.findById(empId)
                .orElseThrow(() -> new RuntimeException("직원 정보를 찾을 수 없습니다."));
            
            // 2. 급여 계산 공식 조회
            int year = YearMonth.parse(paymentDate).getYear();
            List<SalaryFormulaDTO> formulas = salaryFormulaMapper.getCurrentFormulas(year);
            
            if (formulas.isEmpty()) {
                throw new RuntimeException(String.format("%d년도 급여 계산 공식이 정의되어 있지 않습니다.", year));
            }
            
            // 3. 급여 정보 생성
            PayInfo payInfo = new PayInfo();
            payInfo.setEmpId(empId);
            payInfo.setPaymentDate(paymentDate);
            payInfo.setEmpSalary(emp.getEmp_salary());
            
            // 4. 항목별 계산 결과 저장용
            Map<String, BigDecimal> allowanceDetails = new HashMap<>();
            Map<String, BigDecimal> deductionDetails = new HashMap<>();
            
            // 5. 수당 계산
            int currentMonth = YearMonth.parse(paymentDate).getMonthValue();
            BigDecimal totalAllowance = BigDecimal.ZERO;
            
            for (SalaryFormulaDTO formula : formulas.stream()
                    .filter(f -> f.getFormulaType().equals("RWRD"))
                    .sorted(Comparator.comparingInt(SalaryFormulaDTO::getFormulaPriority))
                    .collect(Collectors.toList())) {
                
                try {
                    JsonNode content = objectMapper.readTree(formula.getFormulaContent());
                    BigDecimal amount = calculator.calculate(
                        content,
                        emp,
                        currentMonth,
                        payInfo,
                        formula.getFormulaCode()
                    );
                    
                    // 수당 종류별 금액 저장
                    allowanceDetails.put(formula.getFormulaName(), amount);
                    
                    // PayInfo에 설정
                    switch (formula.getFormulaCode()) {
                        case "RWRD001" -> payInfo.setTechAllowance(amount);
                        case "RWRD002" -> payInfo.setPerformanceBonus(amount);
                        case "RWRD003" -> payInfo.setTenureAllowance(amount);
                        case "RWRD004" -> payInfo.setHolidayAllowance(amount);
                        case "RWRD005" -> payInfo.setLeaveAllowance(amount);
                    }
                    
                    totalAllowance = totalAllowance.add(amount);
                    
                } catch (Exception e) {
                    log.error("수당 계산 중 오류 발생: {}", formula.getFormulaName(), e);
                }
            }
            
            payInfo.setAllowAmt(totalAllowance);
            
            // 6. 공제 계산
            BigDecimal totalDeduction = BigDecimal.ZERO;
            BigDecimal totalSalary = new BigDecimal(payInfo.getEmpSalary())
                .add(payInfo.getAllowAmt());
            
            for (SalaryFormulaDTO formula : formulas.stream()
                    .filter(f -> f.getFormulaType().equals("DDCT"))
                    .sorted(Comparator.comparingInt(SalaryFormulaDTO::getFormulaPriority))
                    .collect(Collectors.toList())) {
                
                try {
                    JsonNode content = objectMapper.readTree(formula.getFormulaContent());
                    BigDecimal amount = calculator.calculate(
                        content,
                        emp,
                        0,
                        payInfo,
                        formula.getFormulaCode()
                    );
                    
                    // 총급여 초과 검증
                    if (totalDeduction.add(amount).compareTo(totalSalary) > 0) {
                        throw new RuntimeException("공제 금액이 총급여액을 초과할 수 없습니다.");
                    }
                    
                    // 공제 종류별 금액 저장
                    deductionDetails.put(formula.getFormulaName(), amount);
                    
                    // PayInfo에 설정
                    switch (formula.getFormulaCode()) {
                        case "DDCT001" -> payInfo.setNationalPension(amount);
                        case "DDCT002" -> payInfo.setHealthInsurance(amount);
                        case "DDCT003" -> payInfo.setLongtermCareInsurance(amount);
                        case "DDCT004" -> payInfo.setEmploymentInsurance(amount);
                        case "DDCT005" -> payInfo.setIncomeTax(amount);
                        case "DDCT006" -> payInfo.setResidentTax(amount);
                    }
                    
                    totalDeduction = totalDeduction.add(amount);
                    
                } catch (Exception e) {
                    log.error("공제 계산 중 오류 발생: {}", formula.getFormulaName(), e);
                }
            }
            
            payInfo.setDeducAmt(totalDeduction);
            
            // 7. 최종 급여 계산
            BigDecimal netSalary = new BigDecimal(payInfo.getEmpSalary())
                .add(payInfo.getAllowAmt())
                .subtract(payInfo.getDeducAmt());
                
            if (netSalary.compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("최종 급여가 음수가 될 수 없습니다.");
            }
            
            payInfo.setNetSalary(netSalary);
            
            // 8. 결과 정리
            result.put("success", true);
            result.put("employeeSalary", emp.getEmp_salary());
            result.put("allowances", allowanceDetails);
            result.put("deductions", deductionDetails);
            result.put("totalAllowance", totalAllowance);
            result.put("totalDeduction", totalDeduction);
            result.put("netSalary", netSalary);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        
        return result;
    }
}