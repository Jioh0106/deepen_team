package com.deepen.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import com.deepen.service.InboundService;
import com.deepen.service.OutboundService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Controller
public class OutboundController {
	
    private final OutboundService outboundService;
    
    //출고 관리 
    @GetMapping("/outbound")
    public String outbound() {
        
        return "outbound/outbound";
    }
	
    //출고 등록 팝업
    @GetMapping("/outboundPopup")
    public String outboundPopup() {
        
        return "outbound/outboundPopup";
    }

}