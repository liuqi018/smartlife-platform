package com.smartlife.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BlogCommentDTO {
    private Long id;
    private Long blogId;
    private Long userId;
    private String nickName;
    private String icon;
    private String content;
    private Integer liked;
    private LocalDateTime createTime;
}
