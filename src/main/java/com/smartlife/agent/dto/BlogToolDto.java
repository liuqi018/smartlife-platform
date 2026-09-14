package com.smartlife.agent.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BlogToolDto {
    private Long id;
    private Long shopId;
    private String title;
    private String contentExcerpt;
    private Integer liked;
    private Integer comments;
    private LocalDateTime createTime;
}
