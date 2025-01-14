package com.deepen.mapper;

import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.deepen.domain.RequestDTO;


@Mapper
@Repository
public interface RequestMapper {

	Map<String, Object> getAbsenceWithRequest(int request_no);

	void updateStatus(Map<String, Object> updateData);
	
	//요청상태가져오기
	Map<String, Object> getRequest(@Param("request_no") Integer request_no);


	

	
}
	