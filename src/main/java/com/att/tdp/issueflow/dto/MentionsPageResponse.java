package com.att.tdp.issueflow.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MentionsPageResponse {

    private List<CommentResponse> data;
    private long total;
    private int page;
}
