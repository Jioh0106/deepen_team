package com.deepen.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.deepen.domain.LineInfoDTO;
import com.deepen.domain.ProcessInfoDTO;
import com.deepen.service.WorkInstructionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;




@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Log
public class WorkInstructionRestController {
	
	private final WorkInstructionService wiService;

	@GetMapping("/process-info")
	public List<ProcessInfoDTO> getProcessInfo() {
		// selectbox에 넣을 공정 정보
		return wiService.getProcessList();
	}
	
	@GetMapping("/line-info")
	public List<LineInfoDTO> getLineInfo() {
		// selectbox에 넣을 라인 정보
		return wiService.getLineList();
	}
	
	/**
	 * 작업지시 등록 정보 불러오기
	 * @return List<Map<String, Object>>
	 */
	@GetMapping("/reg-work-instruction-info")
	public List<Map<String, Object>> getRegWorkInstructionInfo(){
		List<Map<String, Object>> list = wiService.getRegWorkInstruction();
		return list;
	}
	
	/**
	 * 작업 지시 테이블 insert
	 * @param planIdList
	 */
	@PostMapping("/insert-work-instruction")
	public void insertWorkInstruction(@RequestBody List<Map<String, Object>> planIdList,
									HttpServletRequest request) {
		
		HttpSession session = request.getSession();
		Map<String, Object> empInfo = (Map<String, Object>)session.getAttribute("sEmp");
		String empId = (String) empInfo.get("EMP_ID");
		
		wiService.regWorkInstruction(planIdList,empId);
	}
	
	/**
	 * 작업 지시 테이블 조회
	 * @param
	 */
	@GetMapping("/work-instruction-info")
	public List<Map<String, Object>> getWorkInstruction(){
		
		// 품목 bom에 해당하는 공정 중복값 처리 후 우선순위가 높은 공정 insert
		
		List<Map<String, Object>> list = wiService.getWorkInstruction();
		return list;
	}
	
	/**
	 * 클릭한 로우의 품목을 만드는데 필요한 자재 정보 조회
	 */
	@GetMapping("/material-info-by-row-selection")
	public List<Map<String, Object>> getMaterialsByRowSelection(@RequestParam("productNo") String productNo,
																@RequestParam("vol") String vol){
		log.info("자재 정보 연결 준비 : "+productNo+", "+vol);
		
		List<Map<String, Object>> materialsList  = wiService.getMaterialsByProductNo(productNo,vol);
		return materialsList;
	}
	
	@PostMapping("/insert-material-warehouse")
	public void insertMaterialInWarehouse(@RequestBody List<Map<String, Object>> insertMaterialData) {
		log.info("자재 인서트 준비");
		wiService.insertMaterialInWareHouse(insertMaterialData);
	}
}
