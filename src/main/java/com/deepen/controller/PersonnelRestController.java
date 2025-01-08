package com.deepen.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.deepen.domain.CommonDetailDTO;
import com.deepen.service.PersonnelService;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;



@RequiredArgsConstructor
@RestController
@RequestMapping("/restApi")
@Log
public class PersonnelRestController {
	
	
	private final PersonnelService psService;
	
	@GetMapping("/commonDetail")
	public List<CommonDetailDTO> fetchCommonDetailCodeList() {
		
		List<CommonDetailDTO> cdDetailList = psService.fetchCommonDetailCodeList();
		log.info(cdDetailList.toString());
		
		return cdDetailList;
	}
	
	@GetMapping("/empList")
	public List<Map<String, Object>> empList(
			@RequestParam(value = "startDate")String startDate,
			@RequestParam(value = "endDate")String endDate,
			@RequestParam(value = "search", defaultValue = "")String search) {
		
		//log.info("C fitter: "+startDate+", "+endDate+", "+search);
		
		List<Map<String, Object>> empList = psService.getEmpList(startDate,endDate,search);
		
		return empList;
	}
	
	@PostMapping("/empDelete")
	public String postMethodName(@RequestBody List<String> ids) {
		log.info("삭제할 ids - "+ids);
		psService.deleteAllEmpById(ids);
		return "서버 : 삭제 성공";
	}
	
	
	
}
