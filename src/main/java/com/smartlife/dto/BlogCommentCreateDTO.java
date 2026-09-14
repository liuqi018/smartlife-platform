package com.smartlife.dto;

import lombok.Data;

/** User identity is intentionally absent and always comes from UserHolder. */
@Data
public class BlogCommentCreateDTO {
    private String content;
}
