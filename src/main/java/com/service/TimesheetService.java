package com.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.dto.WeeklyLogResponse;
import com.entity.Status;
import com.entity.Task;
import com.entity.User;
import com.entity.WorkLog;
import com.repo.TaskRepository;
import com.repo.UserRepository;
import com.repo.WorkLogRepository;

import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TimesheetService {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private WorkLogRepository workLogRepository;

    @Autowired
    private UserRepository userRepository;

    public ByteArrayInputStream generateTemplate(Long userId) throws IOException {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Timesheet");

            // Header
            Row headerRow = sheet.createRow(0);
            String[] headers = { "Task ID", "Task Title", "Status", "Date (YYYY-MM-DD)", "Hours Worked", "Comment" };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            // Populate with User's Tasks (Active only)
            List<Task> tasks = taskRepository.fetchTasks(userId);
            int rowIdx = 1;
            for (Task task : tasks) {
                if (task.getStatus() != Status.COMPLETED) { // Only show active tasks
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(task.getId());
                    row.createCell(1).setCellValue(task.getTitle());
                    row.createCell(2).setCellValue(task.getStatus().toString());
                    row.createCell(3).setCellValue(LocalDate.now().toString()); // Default to today
                    row.createCell(4).setCellValue(0); // Default 0 hours
                    row.createCell(5).setCellValue("");
                }
            }

            // Auto size columns
            for (int i = 0; i < headers.length; i++)
                sheet.autoSizeColumn(i);

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    @Transactional
    public String processTimesheet(MultipartFile file, Long userId) throws IOException {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        int logsCreated = 0;
        int failedRows = 0;
        StringBuilder errors = new StringBuilder();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            Iterator<Row> rows = sheet.iterator();

            // Skip header
            if (rows.hasNext())
                rows.next();

            int rowNum = 1;
            while (rows.hasNext()) {
                Row row = rows.next();
                rowNum++;

                // Skip empty rows
                if (row.getCell(0) == null)
                    continue;

                try {
                    // 1. Task ID
                    Long taskId = (long) row.getCell(0).getNumericCellValue();

                    // 2. Validate Task
                    Task task = taskRepository.findById(taskId).orElse(null);
                    if (task == null || !task.getUser().getId().equals(userId)) {
                        failedRows++;
                        errors.append("Row ").append(rowNum).append(": Invalid Task ID or not assigned to you. ");
                        continue;
                    }

                    // 3. Hours
                    double hours = 0;
                    Cell hoursCell = row.getCell(4);
                    if (hoursCell != null && hoursCell.getCellType() == CellType.NUMERIC) {
                        hours = hoursCell.getNumericCellValue();
                    }

                    if (hours <= 0)
                        continue; // Skip if no work logged

                    // 4. Date
                    LocalDate date = LocalDate.now(); // Default
                    Cell dateCell = row.getCell(3);
                    try {
                        if (dateCell != null) {
                            if (dateCell.getCellType() == CellType.STRING) {
                                date = LocalDate.parse(dateCell.getStringCellValue());
                            } else if (dateCell.getCellType() == CellType.NUMERIC) {
                                date = dateCell.getLocalDateTimeCellValue().toLocalDate();
                            }
                        }
                    } catch (Exception e) {
                        // ignore date parse error, use today
                    }

                    // 5. Comment
                    String comment = "";
                    Cell commentCell = row.getCell(5);
                    if (commentCell != null) {
                        if (commentCell.getCellType() == CellType.STRING)
                            comment = commentCell.getStringCellValue();
                        else if (commentCell.getCellType() == CellType.NUMERIC)
                            comment = String.valueOf(commentCell.getNumericCellValue());
                    }

                    // Create Log
                    WorkLog log = new WorkLog();
                    log.setTask(task);
                    log.setUser(user);
                    log.setStartTime(date.atStartOfDay()); // Just set to start of day
                    log.setEndTime(date.atStartOfDay().plusMinutes((long) (hours * 60)));
                    log.setDurationMinutes((long) (hours * 60));
                    log.setComment("[Bulk Upload] " + comment);

                    workLogRepository.save(log);

                    // Update Task Total
                    task.setTotalWorkedMinutes((task.getTotalWorkedMinutes() == null ? 0 : task.getTotalWorkedMinutes())
                            + log.getDurationMinutes());
                    taskRepository.save(task);

                    logsCreated++;

                } catch (Exception e) {
                    failedRows++;
                    errors.append("Row ").append(rowNum).append(": Error processing. ");
                }
            }
        }

        return "Processed: " + logsCreated + " entries created. "
                + (failedRows > 0 ? "Failed: " + failedRows + ". Errors: " + errors.toString() : "");
    }

    @Transactional
    public String saveWeeklyLogs(com.dto.WeeklyLogRequest request, Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));
        int logsSaved = 0;

        for (com.dto.WeeklyLogRequest.TaskLogDto taskLog : request.getTaskLogs()) {
            Task task = taskRepository.findById(taskLog.getTaskId()).orElse(null);
            if (task == null || !task.getUser().getId().equals(userId)) {
                continue; // Skip invalid tasks
            }

            for (com.dto.WeeklyLogRequest.DailyEntryDto entry : taskLog.getEntries()) {
                if (entry.getHours() != null && entry.getHours() > 0) {
                    WorkLog log = new WorkLog();
                    log.setTask(task);
                    log.setUser(user);
                    log.setStartTime(entry.getDate().atStartOfDay().plusHours(9)); // Assume 9 AM start
                    long minutes = (long) (entry.getHours() * 60);
                    log.setEndTime(log.getStartTime().plusMinutes(minutes));
                    log.setDurationMinutes(minutes);

                    String comment = entry.getComment();
                    if (comment == null || comment.isEmpty()) {
                        comment = "Timesheet Log";
                    }
                    log.setComment(comment);

                    workLogRepository.save(log);

                    // Update Task Total
                    task.setTotalWorkedMinutes((task.getTotalWorkedMinutes() == null ? 0 : task.getTotalWorkedMinutes())
                            + minutes);
                    taskRepository.save(task);
                    logsSaved++;
                }
            }
        }
        return "Successfully saved " + logsSaved + " entries.";
    }

    public WeeklyLogResponse getWeeklyLogs(Long userId, LocalDate weekStartDate) {
        // 1. Fetch all tasks for the user
        List<Task> allTasks = taskRepository.fetchTasks(userId);

        // 2. Fetch all logs for the week
        LocalDateTime startOfWeek = weekStartDate.atStartOfDay();
        LocalDateTime endOfWeek = weekStartDate.plusDays(7).atStartOfDay();
        List<WorkLog> weeklyLogs = workLogRepository.findByUserIdAndStartTimeBetween(userId, startOfWeek, endOfWeek);

        // 3. Group logs by Task ID
        Map<Long, List<WorkLog>> logsByTask = weeklyLogs.stream()
                .collect(Collectors.groupingBy(log -> log.getTask().getId()));

        List<WeeklyLogResponse.TaskLogResponseDto> taskLogDtos = new ArrayList<>();

        // 4. Iterate over tasks
        for (Task task : allTasks) {
            // Include task if it's not COMPLETED OR if it has logs this week
            boolean hasLogs = logsByTask.containsKey(task.getId());
            if (task.getStatus() == Status.COMPLETED && !hasLogs) {
                continue; // Skip completed tasks with no activity this week
            }

            WeeklyLogResponse.TaskLogResponseDto taskDto = new WeeklyLogResponse.TaskLogResponseDto();
            taskDto.setTaskId(task.getId());
            taskDto.setTaskTitle(task.getTitle());
            taskDto.setStatus(task.getStatus().toString());

            List<WeeklyLogResponse.DailyEntryResponseDto> entries = new ArrayList<>();
            List<WorkLog> taskLogs = logsByTask.getOrDefault(task.getId(), new ArrayList<>());

            // 5. Create entry for each day of the week
            for (int i = 0; i < 7; i++) {
                LocalDate date = weekStartDate.plusDays(i);
                WeeklyLogResponse.DailyEntryResponseDto entry = new WeeklyLogResponse.DailyEntryResponseDto();
                entry.setDate(date);

                // Sum hours for this day
                double hours = taskLogs.stream()
                        .filter(l -> l.getStartTime().toLocalDate().equals(date))
                        .mapToLong(WorkLog::getDurationMinutes)
                        .sum() / 60.0;

                // Collect comments (if multiple, join them)
                String comment = taskLogs.stream()
                        .filter(l -> l.getStartTime().toLocalDate().equals(date))
                        .map(WorkLog::getComment)
                        .filter(c -> c != null && !c.isEmpty())
                        .collect(Collectors.joining("; "));

                entry.setHours(hours > 0 ? hours : null);
                entry.setComment(comment.isEmpty() ? null : comment);
                entries.add(entry);
            }
            taskDto.setEntries(entries);
            taskLogDtos.add(taskDto);
        }

        return new WeeklyLogResponse(weekStartDate, taskLogDtos);
    }
}
