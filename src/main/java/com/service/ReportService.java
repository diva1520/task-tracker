package com.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.entity.Task;
import com.entity.User;
import com.entity.UserLoginAudit;
import com.entity.WorkLog;
import com.repo.TaskRepository;
import com.repo.UserLoginAuditRepository;
import com.repo.UserRepository;
import com.repo.WorkLogRepository;

@Service
public class ReportService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private WorkLogRepository workLogRepository;

    @Autowired
    private UserLoginAuditRepository auditRepository;

    public ByteArrayInputStream generateUserActivityReport(Long userId) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Styles
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // 1. SUMMARY SHEET
            Sheet summarySheet = workbook.createSheet("Summary");
            createRow(summarySheet, 0, headerStyle, "User Profile");
            createRow(summarySheet, 1, null, "ID", String.valueOf(user.getId()));
            createRow(summarySheet, 2, null, "Username", user.getUsername());
            createRow(summarySheet, 3, null, "Email", user.getEmail());
            createRow(summarySheet, 4, null, "Role", user.getRole());
            createRow(summarySheet, 5, null, "Status",
                    (user.getActive() != null && user.getActive()) ? "Active" : "Inactive");

            // Stats
            List<Task> tasks = taskRepository.findByUserId(userId);
            long totalTasks = tasks.size();
            long completedTasks = tasks.stream().filter(t -> com.entity.Status.COMPLETED.equals(t.getStatus())).count();

            createRow(summarySheet, 7, headerStyle, "Statistics");
            createRow(summarySheet, 8, null, "Total Tasks Assigned", String.valueOf(totalTasks));
            createRow(summarySheet, 9, null, "Tasks Completed", String.valueOf(completedTasks));
            createRow(summarySheet, 10, null, "Pending / In Progress", String.valueOf(totalTasks - completedTasks));
            summarySheet.autoSizeColumn(0);
            summarySheet.autoSizeColumn(1);

            // 2. TASKS SHEET
            Sheet taskSheet = workbook.createSheet("Tasks");
            String[] taskHeaders = { "ID", "Title", "Description", "Status", "Created At", "Due Date", "Total Hours" };
            createHeaderRow(taskSheet, taskHeaders, headerStyle);

            int rowIdx = 1;
            for (Task task : tasks) {
                Row row = taskSheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(task.getId());
                row.createCell(1).setCellValue(task.getTitle());
                row.createCell(2).setCellValue(task.getDescription());
                row.createCell(3).setCellValue(task.getStatus().toString());
                row.createCell(4).setCellValue(task.getCreatedAt() != null ? task.getCreatedAt().toString() : "");
                row.createCell(5).setCellValue(task.getDueDate() != null ? task.getDueDate().toString() : "");
                double totalHours = (task.getTotalWorkedMinutes() != null ? task.getTotalWorkedMinutes() : 0) / 60.0;
                row.createCell(6).setCellValue(totalHours);
            }
            autoSizeColumns(taskSheet, taskHeaders.length);

            // 3. WORK LOGS SHEET
            Sheet workLogSheet = workbook.createSheet("Work Logs");
            String[] logHeaders = { "Log ID", "Task ID", "Task Title", "Date", "Start Time", "End Time",
                    "Duration (Hrs)", "Comment" };
            createHeaderRow(workLogSheet, logHeaders, headerStyle);

            List<WorkLog> logs = workLogRepository.findByUserId(userId);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

            rowIdx = 1;
            for (WorkLog log : logs) {
                Row row = workLogSheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(log.getId());
                row.createCell(1).setCellValue(log.getTask().getId());
                row.createCell(2).setCellValue(log.getTask().getTitle());
                row.createCell(3).setCellValue(log.getStartTime().toLocalDate().toString());
                row.createCell(4).setCellValue(log.getStartTime().format(formatter));
                row.createCell(5).setCellValue(log.getEndTime() != null ? log.getEndTime().format(formatter) : "");
                row.createCell(6)
                        .setCellValue((log.getDurationMinutes() != null ? log.getDurationMinutes() : 0) / 60.0);
                row.createCell(7).setCellValue(log.getComment());
            }
            autoSizeColumns(workLogSheet, logHeaders.length);

            // 4. LOGIN ACTIVITY SHEET
            Sheet auditSheet = workbook.createSheet("Login Activity");
            String[] auditHeaders = { "ID", "Login Time", "Logout Time", "Duration (Mins)", "IP Address", "Status" };
            createHeaderRow(auditSheet, auditHeaders, headerStyle);

            List<UserLoginAudit> audits = auditRepository.findByUserId(userId);

            rowIdx = 1;
            for (UserLoginAudit audit : audits) {
                Row row = auditSheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(audit.getId());
                row.createCell(1)
                        .setCellValue(audit.getLoginTime() != null ? audit.getLoginTime().format(formatter) : "");
                row.createCell(2).setCellValue(audit.getLogoutTime() != null ? audit.getLogoutTime().format(formatter)
                        : "Active/Session Expired");
                row.createCell(3).setCellValue(
                        audit.getSessionDurationMinutes() != null ? audit.getSessionDurationMinutes() : 0);
                row.createCell(4).setCellValue(audit.getIpAddress() != null ? audit.getIpAddress() : "");
                row.createCell(5).setCellValue(audit.getStatus() != null ? audit.getStatus() : "");
            }
            autoSizeColumns(auditSheet, auditHeaders.length);

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private void createRow(Sheet sheet, int rowNum, CellStyle style, String... values) {
        Row row = sheet.createRow(rowNum);
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(values[i]);
            if (style != null)
                cell.setCellStyle(style);
        }
    }

    private void createHeaderRow(Sheet sheet, String[] headers, CellStyle style) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private void autoSizeColumns(Sheet sheet, int count) {
        for (int i = 0; i < count; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
