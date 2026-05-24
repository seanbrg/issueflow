package com.att.tdp.issueflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CsvImportResult {
    private int created;
    private int failed;
    private List<String> errors;
}
