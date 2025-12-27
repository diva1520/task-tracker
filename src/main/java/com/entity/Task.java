package com.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String description;
    private Status status;
 
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    private String created_by;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    private User user;
   
    @Column(name = "due_date", updatable = false)
    private LocalDateTime dueDate;
    
    
    private Long totalWorkedMinutes;   
    private Boolean completedOnTime;
    private Long delayMinutes;         
    private LocalDateTime completedAt;

   
}

