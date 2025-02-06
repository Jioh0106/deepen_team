package com.deepen.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class QcMasterDTO {
    private String qcCode;
    private String qcName;
    private Integer process;
    private BigDecimal targetValue;
    private BigDecimal ucl;
    private BigDecimal lcl;
    private String unit;
    private String qcMethod;
    private String useYn;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}