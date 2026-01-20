package com.controller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;

import com.dto.WeeklyLogResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.service.TimesheetService;
import com.util.JwtUtil;

@RestController
@RequestMapping("/user/timesheet")
@CrossOrigin(origins = "*")
public class TimesheetController {

    @Autowired
    private TimesheetService timesheetService;

    @Autowired
    private JwtUtil jwtUtil;

    @GetMapping("/template")
    public ResponseEntity<InputStreamResource> downloadTemplate(@RequestHeader("Authorization") String authHeader)
            throws IOException {
        String token = authHeader.substring(7);
        Long userId = jwtUtil.extractUserId(token);

        ByteArrayInputStream in = timesheetService.generateTemplate(userId);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=timesheet_template.xlsx");

        return ResponseEntity
                .ok()
                .headers(headers)
                .contentType(
                        MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(new InputStreamResource(in));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadTimesheet(@RequestHeader("Authorization") String authHeader,
            @RequestParam("file") MultipartFile file) {
        try {
            String token = authHeader.substring(7);
            Long userId = jwtUtil.extractUserId(token);

            String message = timesheetService.processTimesheet(file, userId);
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Upload failed: " + e.getMessage());
        }
    }

    @PostMapping("/save-weekly")
    public ResponseEntity<?> saveWeeklyLogs(@RequestHeader("Authorization") String authHeader,
            @org.springframework.web.bind.annotation.RequestBody com.dto.WeeklyLogRequest request) {
        try {
            String token = authHeader.substring(7);
            Long userId = jwtUtil.extractUserId(token);

            String message = timesheetService.saveWeeklyLogs(request, userId);
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Save failed: " + e.getMessage());
        }
    }

    @GetMapping("/weekly")
    public ResponseEntity<?> getWeeklyLogs(@RequestHeader("Authorization") String authHeader,
            @RequestParam("startDate") String startDateStr) {
        try {
            String token = authHeader.substring(7);
            Long userId = jwtUtil.extractUserId(token);
            LocalDate startDate = LocalDate.parse(startDateStr);

            WeeklyLogResponse response = timesheetService.getWeeklyLogs(userId, startDate);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error fetching logs: " + e.getMessage());
        }
    }
}
