package com.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GetTaskRequest {

	private LocalDate fromDate;
	private LocalDate toDate;
	private List<Long> userIds;
}
